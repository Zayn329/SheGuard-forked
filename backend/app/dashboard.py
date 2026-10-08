    """SheGuard responder dashboard: report ingestion + clustering + responder API.

Wire-up in app/main.py (2 lines):
    from app.dashboard import make_router
    app.include_router(make_router())

Report upload is OPEN (no login, anonymous) but rate-limited per reporter token and per IP.
"""
import hashlib, hmac, math, os, time
from pathlib import Path
from typing import List, Literal, Optional

from collections import defaultdict, deque
from fastapi import APIRouter, Depends, Header, HTTPException, Request
from fastapi.responses import FileResponse
from pydantic import BaseModel, Field
from sqlalchemy import BigInteger, Column, Float, String
from sqlalchemy.orm import Session

from app.db.database import Base, engine, get_db

# Mirrors PatternEngineConfig in the Android app (SheGuardModels.kt)
SPATIAL_M = 500.0
WINDOW_MS = 2 * 60 * 60 * 1000
MIN_REPORTS = 2
MIN_UNIQUE_REPORTERS = 2          # TrustEvaluationConfig.minUniqueReportersForEmerging
COORD_DECIMALS = int(os.getenv("COORD_DECIMALS", "3"))  # 3 = ~110 m, 2 = ~1 km
CATEGORY_WEIGHT = {"FEELING_FOLLOWED": 3, "HARASSMENT": 3, "SUSPICIOUS_ACTIVITY": 2,
                   "UNSAFE_GATHERING": 2, "POOR_LIGHTING": 1}
Category = Literal["POOR_LIGHTING", "HARASSMENT", "FEELING_FOLLOWED",
                   "UNSAFE_GATHERING", "SUSPICIOUS_ACTIVITY"]
STATUSES = ("NEW", "ACKNOWLEDGED", "DISPATCHED", "RESOLVED")


class DBReport(Base):
    """Deliberately has NO user_id: reports are anonymous on the server too."""
    __tablename__ = "micro_reports"
    report_id = Column(String, primary_key=True)
    reporter_token = Column(String, index=True, nullable=False)
    category = Column(String, nullable=False)
    lat = Column(Float, nullable=False)
    lng = Column(Float, nullable=False)
    area = Column(String, default="")
    ts = Column(BigInteger, index=True, nullable=False)  # epoch ms, from the device
    received_at = Column(BigInteger, index=True, nullable=False, default=0)  # server clock, ms


class DBClusterStatus(Base):
    __tablename__ = "cluster_status"
    cluster_id = Column(String, primary_key=True)
    status = Column(String, nullable=False)
    responder = Column(String, default="")
    updated_at = Column(BigInteger, nullable=False)


try:
    Base.metadata.create_all(bind=engine)
except Exception:
    pass


class ReportIn(BaseModel):
    report_id: str = Field(min_length=8, max_length=64)
    reporter_token: str = Field(min_length=6, max_length=64)
    category: Category
    latitude: float = Field(ge=-90, le=90)
    longitude: float = Field(ge=-180, le=180)
    approximate_area: str = Field(default="", max_length=120)
    timestamp: int  # epoch ms


class ReportBatch(BaseModel):
    reports: List[ReportIn] = Field(max_length=20)


class StatusIn(BaseModel):
    status: Literal["NEW", "ACKNOWLEDGED", "DISPATCHED", "RESOLVED"]
    responder: str = ""


def haversine_m(la1, lo1, la2, lo2):
    p1, p2 = math.radians(la1), math.radians(la2)
    a = (math.sin((p2 - p1) / 2) ** 2
         + math.cos(p1) * math.cos(p2) * math.sin(math.radians(lo2 - lo1) / 2) ** 2)
    return 6371000.0 * 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))


def cluster_reports(reports: List[DBReport]) -> list:
    """Port of SpatioTemporalPatternEngine.detectCandidatePatterns (same greedy logic)."""
    out, by_cat = [], {}
    for r in reports:
        by_cat.setdefault(r.category, []).append(r)
    for cat, rs in by_cat.items():
        rs.sort(key=lambda r: (r.ts, r.report_id))
        seen = set()
        for i, base in enumerate(rs):
            if base.report_id in seen:
                continue
            group = [base]
            for c in rs[i + 1:]:
                if c.report_id in seen or abs(c.ts - base.ts) > WINDOW_MS:
                    continue
                if haversine_m(base.lat, base.lng, c.lat, c.lng) <= SPATIAL_M:
                    group.append(c)
            if len(group) < MIN_REPORTS:
                continue
            seen.update(r.report_id for r in group)
            lat = sum(r.lat for r in group) / len(group)
            lng = sum(r.lng for r in group) / len(group)
            uniq = len({r.reporter_token for r in group})
            score = CATEGORY_WEIGHT[cat] * uniq
            out.append({
                "cluster_id": hashlib.sha1(f"{cat}|{group[0].report_id}".encode()).hexdigest()[:12],
                "category": cat, "lat": lat, "lng": lng,
                "radius_m": max(100.0, max(haversine_m(lat, lng, r.lat, r.lng) for r in group)),
                "report_count": len(group), "unique_reporters": uniq,
                "verified": uniq >= MIN_UNIQUE_REPORTERS,  # single-reporter floods stay "unverified"
                "priority": "HIGH" if score >= 6 else "MEDIUM" if score >= 3 else "LOW",
                "area": next((r.area for r in group if r.area), ""),
                "first_ts": group[0].ts, "last_ts": max(r.ts for r in group),
            })
    return out


TOKEN_LIMIT_PER_HOUR = int(os.getenv("TOKEN_LIMIT_PER_HOUR", "10"))
IP_LIMIT_PER_HOUR = int(os.getenv("IP_LIMIT_PER_HOUR", "60"))
_ip_hits = defaultdict(deque)  # in-memory; fine for one-instance MVP


def _ip_allowed(ip: str, n: int) -> bool:
    now, q = time.time(), _ip_hits[ip]
    while q and q[0] < now - 3600:
        q.popleft()
    if len(q) + n > IP_LIMIT_PER_HOUR:
        return False
    q.extend([now] * n)
    return True


def make_router() -> APIRouter:
    router = APIRouter()

    def responder_auth(x_responder_key: Optional[str] = Header(None)):
        expected = os.getenv("RESPONDER_API_KEY")
        if not expected:
            raise HTTPException(503, "Dashboard disabled: set RESPONDER_API_KEY")
        if not x_responder_key or not hmac.compare_digest(x_responder_key, expected):
            raise HTTPException(401, "Invalid responder key")

    @router.post("/api/v1/reports/batch")
    def upload_reports(req: ReportBatch, request: Request, db: Session = Depends(get_db)):
        """Phone -> server. Open + anonymous: no login, no user id stored. Rate-limited instead."""
        ip = (request.headers.get("x-forwarded-for") or (request.client.host if request.client else "?")).split(",")[0].strip()
        if not _ip_allowed(ip, len(req.reports)):
            raise HTTPException(429, "Too many reports from this network. Try later.")
        now = int(time.time() * 1000)
        hour_ago = now - 3600 * 1000
        sent, accepted = {}, []
        for r in req.reports:
            if r.timestamp > now + 5 * 60 * 1000 or r.timestamp < now - 7 * 24 * 3600 * 1000:
                continue  # clock-skewed or stale; skip silently (device will mark it synced)
            if db.get(DBReport, r.report_id):
                accepted.append(r.report_id)  # idempotent retry
                continue
            if sent.get(r.reporter_token) is None:
                sent[r.reporter_token] = db.query(DBReport).filter(
                    DBReport.reporter_token == r.reporter_token, DBReport.received_at >= hour_ago).count()
            if sent[r.reporter_token] >= TOKEN_LIMIT_PER_HOUR:
                continue  # over the per-token cap: drop, not accepted
            sent[r.reporter_token] += 1
            db.add(DBReport(report_id=r.report_id, reporter_token=r.reporter_token, category=r.category,
                            area=r.approximate_area, ts=r.timestamp, received_at=now,
                            lat=round(r.latitude, COORD_DECIMALS), lng=round(r.longitude, COORD_DECIMALS)))
            accepted.append(r.report_id)
        db.commit()
        return {"accepted_report_ids": accepted}

    @router.get("/api/v1/dashboard/clusters", dependencies=[Depends(responder_auth)])
    def clusters(hours: int = 24, db: Session = Depends(get_db)):
        since = int(time.time() * 1000) - hours * 3600 * 1000
        rows = db.query(DBReport).filter(DBReport.ts >= since).all()
        statuses = {s.cluster_id: s for s in db.query(DBClusterStatus).all()}
        result = cluster_reports(rows)
        for c in result:
            s = statuses.get(c["cluster_id"])
            c["status"], c["responder"] = (s.status, s.responder) if s else ("NEW", "")
        rank = {"HIGH": 0, "MEDIUM": 1, "LOW": 2}
        result.sort(key=lambda c: (c["status"] == "RESOLVED", rank[c["priority"]], -c["last_ts"]))
        return {"generated_at": int(time.time() * 1000), "clusters": result}

    @router.post("/api/v1/dashboard/clusters/{cluster_id}/status", dependencies=[Depends(responder_auth)])
    def set_status(cluster_id: str, req: StatusIn, db: Session = Depends(get_db)):
        s = db.get(DBClusterStatus, cluster_id) or DBClusterStatus(cluster_id=cluster_id)
        s.status, s.responder, s.updated_at = req.status, req.responder[:60], int(time.time() * 1000)
        db.merge(s)
        db.commit()
        return {"cluster_id": cluster_id, "status": s.status}

    @router.post("/api/v1/dashboard/demo-seed", dependencies=[Depends(responder_auth)])
    def demo_seed(db: Session = Depends(get_db)):
        """Hackathon demo data around Mumbai. Enable with DASHBOARD_DEMO=true."""
        if os.getenv("DASHBOARD_DEMO", "false").lower() != "true":
            raise HTTPException(403, "Set DASHBOARD_DEMO=true to enable")
        now, n = int(time.time() * 1000), 0
        spots = [(19.1197, 72.8464, "HARASSMENT", 4), (19.0178, 72.8478, "FEELING_FOLLOWED", 3),
                 (19.0728, 72.8826, "POOR_LIGHTING", 3), (19.2183, 72.9781, "UNSAFE_GATHERING", 2)]
        for lat, lng, cat, k in spots:
            for i in range(k):
                db.merge(DBReport(report_id=f"demo-{cat}-{i}", reporter_token=f"demo-tok-{cat}-{i}",
                                  category=cat, lat=round(lat + i * 0.0006, COORD_DECIMALS),
                                  lng=round(lng + i * 0.0004, COORD_DECIMALS), area="Demo area",
                                  ts=now - (i + 1) * 12 * 60 * 1000, received_at=now))
                n += 1
        db.commit()
        return {"seeded_reports": n}

    @router.get("/dashboard", include_in_schema=False)
    def page():
        return FileResponse(Path(__file__).parent / "static" / "dashboard.html")

    return router
