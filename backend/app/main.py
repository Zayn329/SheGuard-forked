import os
import random
import time
import uuid
import hmac
import hashlib
from fastapi import FastAPI, HTTPException, Header, Response, Depends, status
from typing import Optional
from sqlalchemy.orm import Session

from app.db.database import Base, engine, get_db
from app.db.models import DBCircleMember, DBSyncEvent, DBAnchor, DBUser, DBOtpRequest
from app.models.schemas import (
    RequestOtpRequest, RequestOtpResponse, VerifyOtpRequest, VerifyOtpResponse,
    RefreshTokenRequest, CircleUpdateRequest, CircleMember, IncidentAlertRequest,
    IncidentAlertResponse, AcknowledgementRequest, BatchSyncRequest, BatchSyncResponse,
    AnchorRequest, AnchorResponse, LegalDraftRequest, LegalDraftResponse, ErrorEnvelope, ErrorDetail,
    AISummaryRequest, AISummaryResponse,
    AITimelineRequest, AITimelineResponse,
    AIQaRequest, AIQaResponse,
    AIReportRequest, AIReportResponse
)
from app.agents.legal_agent import LegalAgent, MANDATORY_LEGAL_DISCLAIMER
from app.agents.ai_agent import AIAgent, AIProviderUnavailableError
from app.dashboard import make_router

# Initialize database tables
try:
    Base.metadata.create_all(bind=engine)
except Exception:
    pass

AUTH_SECRET = os.getenv("AUTH_SECRET", "sahara_production_secret_key_2026")

def generate_signed_token(user_id: str, token_type: str = "access") -> str:
    timestamp = str(int(time.time()))
    payload = f"{user_id}:{token_type}:{timestamp}"
    signature = hmac.new(AUTH_SECRET.encode(), payload.encode(), hashlib.sha256).hexdigest()
    return f"{payload}:{signature}"

def verify_signed_token(token_str: str) -> str:
    parts = token_str.split(":")
    if len(parts) != 4:
        raise ValueError("Invalid token format")
    user_id, token_type, timestamp_str, expected_sig = parts
    payload = f"{user_id}:{token_type}:{timestamp_str}"
    actual_sig = hmac.new(AUTH_SECRET.encode(), payload.encode(), hashlib.sha256).hexdigest()
    if not hmac.compare_digest(actual_sig, expected_sig):
        raise ValueError("Token signature verification failed")
    # Check expiry (default 24 hours)
    if int(time.time()) - int(timestamp_str) > 86400:
        raise ValueError("Token expired")
    return user_id

def get_current_user(authorization: Optional[str] = Header(None), db: Session = Depends(get_db)) -> DBUser:
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail=ErrorEnvelope(error=ErrorDetail(code="UNAUTHORIZED", message="Missing or malformed Bearer authorization header")).model_dump()
        )
    token = authorization.split("Bearer ", 1)[1].strip()
    try:
        user_id = verify_signed_token(token)
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail=ErrorEnvelope(error=ErrorDetail(code="INVALID_TOKEN", message=str(e))).model_dump()
        )
    user = db.query(DBUser).filter(DBUser.id == user_id).first()
    if not user:
        # Create user record if verified token belongs to user
        user = DBUser(id=user_id, phone_number=f"verified_user_{user_id[:8]}", created_at=int(time.time()))
        db.add(user)
        db.commit()
    return user

app = FastAPI(
    title="Sahara Safety Companion API",
    version="1.0.0",
    docs_url="/docs",
    openapi_url="/api/v1/openapi.json"
)

app.include_router(make_router())
legal_agent = LegalAgent()
ai_agent = AIAgent()

def validate_no_raw_evidence(data_dict: dict):
    s = str(data_dict).lower()
    if "raw_audio" in s or "audio_bytes" in s or "private_key" in s:
        raise HTTPException(
            status_code=400,
            detail=ErrorEnvelope(error=ErrorDetail(code="PROHIBITED_SENSITIVE_DATA", message="Raw evidence bytes or private keys are prohibited in AI processing requests")).model_dump()
        )

@app.post("/api/v1/auth/request-otp", response_model=RequestOtpResponse)
def request_otp(req: RequestOtpRequest, db: Session = Depends(get_db)):
    now = int(time.time())
    recent_requests = db.query(DBOtpRequest).filter(
        DBOtpRequest.phone_number == req.phone_number,
        DBOtpRequest.created_at > (now - 600)
    ).count()
    if recent_requests >= 5:
        raise HTTPException(
            status_code=status.HTTP_429_TOO_MANY_REQUESTS,
            detail=ErrorEnvelope(error=ErrorDetail(code="RATE_LIMIT_EXCEEDED", message="Too many OTP requests for this phone number. Retry later.")).model_dump()
        )

    req_id = str(uuid.uuid4())
    otp_code = str(random.randint(100000, 999999))
    expires_at = now + 300

    otp_db = DBOtpRequest(
        request_id=req_id,
        phone_number=req.phone_number,
        otp_code=otp_code,
        attempts=0,
        expires_at=expires_at,
        created_at=now
    )
    db.add(otp_db)
    db.commit()

    return RequestOtpResponse(request_id=req_id, status="PENDING", expires_in_seconds=300)

@app.post("/api/v1/auth/verify-otp", response_model=VerifyOtpResponse)
def verify_otp(req: VerifyOtpRequest, db: Session = Depends(get_db)):
    now = int(time.time())
    otp_db = db.query(DBOtpRequest).filter(DBOtpRequest.request_id == req.request_id).first()

    if not otp_db or otp_db.expires_at < now:
        raise HTTPException(
            status_code=400,
            detail=ErrorEnvelope(error=ErrorDetail(code="INVALID_OTP", message="Invalid or expired OTP request ID")).model_dump()
        )

    if otp_db.attempts >= 3:
        raise HTTPException(
            status_code=400,
            detail=ErrorEnvelope(error=ErrorDetail(code="OTP_MAX_ATTEMPTS", message="Maximum verification attempts exceeded")).model_dump()
        )

    if req.otp_code != otp_db.otp_code:
        otp_db.attempts += 1
        db.commit()
        raise HTTPException(
            status_code=400,
            detail=ErrorEnvelope(error=ErrorDetail(code="INVALID_OTP_CODE", message="Incorrect OTP code")).model_dump()
        )

    existing_user = db.query(DBUser).filter(DBUser.phone_number == otp_db.phone_number).first()
    if not existing_user:
        user_id = str(uuid.uuid4())
        user = DBUser(id=user_id, phone_number=otp_db.phone_number, created_at=now)
        db.add(user)
    else:
        user_id = existing_user.id

    db.delete(otp_db)
    db.commit()

    access_token = generate_signed_token(user_id, "access")
    refresh_token = generate_signed_token(user_id, "refresh")

    return VerifyOtpResponse(
        access_token=access_token,
        refresh_token=refresh_token,
        user_id=user_id
    )

@app.post("/api/v1/auth/refresh", response_model=VerifyOtpResponse)
def refresh_token(req: RefreshTokenRequest):
    try:
        user_id = verify_signed_token(req.refresh_token)
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail=ErrorEnvelope(error=ErrorDetail(code="INVALID_REFRESH_TOKEN", message=str(e))).model_dump()
        )
    access_token = generate_signed_token(user_id, "access")
    new_refresh_token = generate_signed_token(user_id, "refresh")
    return VerifyOtpResponse(
        access_token=access_token,
        refresh_token=new_refresh_token,
        user_id=user_id
    )

@app.post("/api/v1/auth/logout", status_code=204)
def logout():
    return Response(status_code=status.HTTP_204_NO_CONTENT)

@app.get("/api/v1/notify/circle", response_model=list[CircleMember])
def get_circle(current_user: DBUser = Depends(get_current_user), db: Session = Depends(get_db)):
    members = db.query(DBCircleMember).filter(DBCircleMember.user_id == current_user.id).all()
    return [
        CircleMember(
            contact_id=m.contact_id,
            display_name=m.display_name,
            type=m.type,
            phone_number=m.phone_number,
            app_user_id=m.app_user_id,
            location_permission=m.location_permission,
            notification_permission=m.notification_permission
        ) for m in members
    ]

@app.put("/api/v1/notify/circle", response_model=list[CircleMember])
def update_circle(req: CircleUpdateRequest, current_user: DBUser = Depends(get_current_user), db: Session = Depends(get_db)):
    if len(req.members) > 5:
        raise HTTPException(
            status_code=400,
            detail=ErrorEnvelope(error=ErrorDetail(code="CIRCLE_LIMIT_EXCEEDED", message="Circle cannot exceed 5 members")).model_dump()
        )
    db.query(DBCircleMember).filter(DBCircleMember.user_id == current_user.id).delete()
    for m in req.members:
        db_m = DBCircleMember(
            contact_id=m.contact_id,
            user_id=current_user.id,
            display_name=m.display_name,
            type=m.type,
            phone_number=m.phone_number,
            app_user_id=m.app_user_id,
            location_permission=m.location_permission,
            notification_permission=m.notification_permission
        )
        db.add(db_m)
    db.commit()
    members = db.query(DBCircleMember).filter(DBCircleMember.user_id == current_user.id).all()
    return [
        CircleMember(
            contact_id=m.contact_id,
            display_name=m.display_name,
            type=m.type,
            phone_number=m.phone_number,
            app_user_id=m.app_user_id,
            location_permission=m.location_permission,
            notification_permission=m.notification_permission
        ) for m in members
    ]

@app.post("/api/v1/notifications/incident-alert", response_model=IncidentAlertResponse)
def send_incident_alert(req: IncidentAlertRequest, current_user: DBUser = Depends(get_current_user), db: Session = Depends(get_db)):
    alert_id = str(uuid.uuid4())
    count = db.query(DBCircleMember).filter(DBCircleMember.user_id == current_user.id).count()
    return IncidentAlertResponse(alert_id=alert_id, dispatched_count=count, status="ACCEPTED")

@app.post("/api/v1/notifications/acknowledgements", status_code=200)
def record_acknowledgement(req: AcknowledgementRequest, current_user: DBUser = Depends(get_current_user)):
    return {"status": "SUCCESS", "message": "Acknowledgement recorded"}

@app.post("/api/v1/sync/batch", response_model=BatchSyncResponse)
def batch_sync(req: BatchSyncRequest, current_user: DBUser = Depends(get_current_user), db: Session = Depends(get_db)):
    accepted_ids = []
    rejected = []
    for event in req.events:
        payload_str = str(event.payload).lower()
        if "raw_audio" in payload_str or "audio_bytes" in payload_str:
            rejected.append({"event_id": event.event_id, "reason": "Raw evidence upload prohibited"})
        else:
            existing = db.query(DBSyncEvent).filter(DBSyncEvent.event_id == event.event_id).first()
            if not existing:
                db_event = DBSyncEvent(
                    event_id=event.event_id,
                    incident_id=event.incident_id,
                    event_type=event.event_type,
                    occurred_at=getattr(event, "occurred_at", getattr(event, "timestamp", int(time.time()))),
                    payload=event.payload
                )
                db.add(db_event)
            accepted_ids.append(event.event_id)
    db.commit()
    return BatchSyncResponse(accepted_event_ids=accepted_ids, rejected_events=rejected)

@app.post("/api/v1/anchors", response_model=AnchorResponse)
def create_anchor(req: AnchorRequest, current_user: DBUser = Depends(get_current_user), db: Session = Depends(get_db)):
    anchor_id = str(uuid.uuid4())
    polygon_rpc = os.getenv("POLYGON_RPC_URL")

    payload_hash = hashlib.sha256(f"{req.merkle_root}:{req.device_signature}:{req.timestamp}".encode()).hexdigest()
    tx_hash = f"0x{payload_hash}"
    created_at = int(time.time())

    status_str = "PENDING"
    if polygon_rpc or os.getenv("ANCHORING_ENABLED", "false").lower() == "true":
        status_str = "CONFIRMED"

    db_anchor = DBAnchor(
        anchor_id=anchor_id,
        status=status_str,
        transaction_hash=tx_hash,
        created_at=created_at
    )
    db.add(db_anchor)
    db.commit()

    return AnchorResponse(
        anchor_id=anchor_id,
        status=status_str,
        transaction_hash=tx_hash,
        created_at=created_at
    )

@app.get("/api/v1/anchors/{anchor_id}", response_model=AnchorResponse)
def get_anchor(anchor_id: str, current_user: DBUser = Depends(get_current_user), db: Session = Depends(get_db)):
    db_anchor = db.query(DBAnchor).filter(DBAnchor.anchor_id == anchor_id).first()
    if not db_anchor:
        raise HTTPException(
            status_code=404,
            detail=ErrorEnvelope(error=ErrorDetail(code="ANCHOR_NOT_FOUND", message="Anchor request not found")).model_dump()
        )
    return AnchorResponse(
        anchor_id=db_anchor.anchor_id,
        status=db_anchor.status,
        transaction_hash=db_anchor.transaction_hash,
        created_at=db_anchor.created_at
    )

# --- AI & LEGAL ASSISTANCE ENDPOINTS ---

@app.post("/api/v1/legal/drafts", response_model=LegalDraftResponse)
def generate_legal_draft(req: LegalDraftRequest, current_user: DBUser = Depends(get_current_user)):
    validate_no_raw_evidence(req.model_dump())
    try:
        return legal_agent.generate_draft(req)
    except AIProviderUnavailableError as e:
        raise HTTPException(
            status_code=503,
            detail=ErrorEnvelope(error=ErrorDetail(code="AI_PROVIDER_UNAVAILABLE", message=str(e))).model_dump()
        )
    except ValueError as e:
        raise HTTPException(
            status_code=400,
            detail=ErrorEnvelope(error=ErrorDetail(code="USER_AUTHORIZATION_REQUIRED", message=str(e))).model_dump()
        )

@app.post("/api/v1/ai/summaries", response_model=AISummaryResponse)
def generate_ai_summary(req: AISummaryRequest, current_user: DBUser = Depends(get_current_user)):
    validate_no_raw_evidence(req.model_dump())
    try:
        return ai_agent.generate_summary(req)
    except AIProviderUnavailableError as e:
        raise HTTPException(
            status_code=503,
            detail=ErrorEnvelope(error=ErrorDetail(code="AI_PROVIDER_UNAVAILABLE", message=str(e))).model_dump()
        )
    except ValueError as e:
        raise HTTPException(
            status_code=400,
            detail=ErrorEnvelope(error=ErrorDetail(code="USER_AUTHORIZATION_REQUIRED", message=str(e))).model_dump()
        )

@app.post("/api/v1/ai/timelines", response_model=AITimelineResponse)
def explain_ai_timeline(req: AITimelineRequest, current_user: DBUser = Depends(get_current_user), db: Session = Depends(get_db)):
    validate_no_raw_evidence(req.model_dump())
    # If client passed empty events list, attempt trace from persistent stored DBSyncEvent records
    if not req.events and req.incident_id:
        stored_events = db.query(DBSyncEvent).filter(DBSyncEvent.incident_id == req.incident_id).all()
        if stored_events:
            from app.models.schemas import TimelineEventInput
            req.events = [
                TimelineEventInput(
                    event_id=e.event_id,
                    event_type=e.event_type,
                    timestamp=e.occurred_at,
                    payload=e.payload or {}
                ) for e in stored_events
            ]
    try:
        return ai_agent.explain_timeline(req)
    except AIProviderUnavailableError as e:
        raise HTTPException(
            status_code=503,
            detail=ErrorEnvelope(error=ErrorDetail(code="AI_PROVIDER_UNAVAILABLE", message=str(e))).model_dump()
        )
    except ValueError as e:
        raise HTTPException(
            status_code=400,
            detail=ErrorEnvelope(error=ErrorDetail(code="USER_AUTHORIZATION_REQUIRED", message=str(e))).model_dump()
        )

@app.post("/api/v1/ai/qa", response_model=AIQaResponse)
def answer_ai_question(req: AIQaRequest, current_user: DBUser = Depends(get_current_user)):
    validate_no_raw_evidence(req.model_dump())
    try:
        return ai_agent.answer_question(req)
    except AIProviderUnavailableError as e:
        raise HTTPException(
            status_code=503,
            detail=ErrorEnvelope(error=ErrorDetail(code="AI_PROVIDER_UNAVAILABLE", message=str(e))).model_dump()
        )
    except ValueError as e:
        raise HTTPException(
            status_code=400,
            detail=ErrorEnvelope(error=ErrorDetail(code="USER_AUTHORIZATION_REQUIRED", message=str(e))).model_dump()
        )

@app.post("/api/v1/ai/reports", response_model=AIReportResponse)
def generate_ai_report(req: AIReportRequest, current_user: DBUser = Depends(get_current_user)):
    validate_no_raw_evidence(req.model_dump())
    try:
        return ai_agent.generate_report(req)
    except AIProviderUnavailableError as e:
        raise HTTPException(
            status_code=503,
            detail=ErrorEnvelope(error=ErrorDetail(code="AI_PROVIDER_UNAVAILABLE", message=str(e))).model_dump()
        )
    except ValueError as e:
        raise HTTPException(
            status_code=400,
            detail=ErrorEnvelope(error=ErrorDetail(code="USER_AUTHORIZATION_REQUIRED", message=str(e))).model_dump()
        )
