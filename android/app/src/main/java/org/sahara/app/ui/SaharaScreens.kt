package org.sahara.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.sahara.app.export.ExportPackage
import org.sahara.app.help.OfflineHelpDirectory
import org.sahara.core.domain.models.IncidentState
import org.sahara.core.domain.models.NotifyContact

// =============================================================================
// SCREEN 1 — WELCOME
// =============================================================================

@Composable
fun WelcomeScreen(
    onGetStarted: () -> Unit,
    onLearnMore: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SheGuardColors.Background)
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        // Brand & SheGuard Shield Icon
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 20.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .clip(CircleShape)
                    .background(SheGuardColors.PrimaryContainer)
                    .border(2.dp, SheGuardColors.PrimaryLight, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "🛡️",
                    fontSize = 54.sp
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "SheGuard",
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 32.sp),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.ExtraBold,
                color = SheGuardColors.TextPrimary
            )

            Text(
                text = "Offline-First Safety & Community Warning",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.SemiBold,
                color = SheGuardColors.CyanLight
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Anonymous micro-reporting, on-device spatio-temporal risk pattern detection, multi-signal trust verification, and peer mesh early warnings — without cloud dependence.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = SheGuardColors.TextSecondary,
                lineHeight = 22.sp
            )
        }

        // Action Buttons
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SaharaPrimaryButton(
                text = "Get Started",
                onClick = onGetStarted
            )
            SaharaSecondaryButton(
                text = "How SheGuard Works",
                onClick = onLearnMore
            )
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

// =============================================================================
// SCREEN 2 — PERMISSIONS & CONSENT
// =============================================================================

@Composable
fun PermissionsConsentScreen(
    onContinue: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SheGuardColors.Background)
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        Text(
            text = "← Back",
            style = MaterialTheme.typography.bodyMedium,
            color = SheGuardColors.TextSecondary,
            modifier = Modifier
                .clickable { onBack() }
                .padding(vertical = 8.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        SaharaSectionHeader(
            title = "Device Permissions",
            subtitle = "SheGuard processes all safety signals deterministically on-device without continuous surveillance."
        )

        Spacer(modifier = Modifier.height(20.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PermissionCard(
                icon = "🎙️",
                title = "Audio Detection",
                subtitle = "On-Device Distress Signal Processing",
                explanation = "Processed in temporary RAM buffers. Raw audio never leaves your device unless sealed into a cryptographic package."
            )
            PermissionCard(
                icon = "📍",
                title = "Coarse Location",
                subtitle = "Spatio-Temporal Clustering",
                explanation = "Used to detect hazard clusters and early warnings. Location is coarsened to protect reporter anonymity."
            )
            PermissionCard(
                icon = "📡",
                title = "Nearby Devices (Mesh)",
                subtitle = "BLE / Wi-Fi Direct Peer Relay",
                explanation = "Exchanges anonymized micro-reports and early-warning alerts with nearby devices when cellular data is unavailable."
            )
            PermissionCard(
                icon = "💬",
                title = "SMS Backup",
                subtitle = "Offline Emergency Escalation",
                explanation = "Sends direct SMS alerts to your trusted circle if cellular data is unavailable during an active emergency."
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        SaharaPrimaryButton(
            text = "Grant & Continue",
            onClick = onContinue
        )
    }
}

@Composable
private fun PermissionCard(
    icon: String,
    title: String,
    subtitle: String,
    explanation: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, SheGuardColors.BorderSubtle, RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = SheGuardColors.SurfaceCard)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(SheGuardColors.SurfaceElevated)
                    .border(1.dp, SheGuardColors.BorderSubtle, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = icon, fontSize = 18.sp)
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = SheGuardColors.TextPrimary
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = SheGuardColors.CyanLight
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = explanation,
                    style = MaterialTheme.typography.bodySmall,
                    color = SheGuardColors.TextSecondary,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

// =============================================================================
// SCREEN 3 — NOTIFY CIRCLE SETUP
// =============================================================================

@Composable
fun NotifyCircleSetupScreen(
    onContinue: () -> Unit,
    onBack: () -> Unit
) {
    var contactName by remember { mutableStateOf("") }
    var contactPhone by remember { mutableStateOf("") }
    var contacts by remember {
        mutableStateOf(
            listOf(
                Pair("Aisha (Sister)", "+91 9876543210"),
                Pair("Sara (Friend)", "+91 9876543211")
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SheGuardColors.Background)
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        Text(
            text = "← Back",
            style = MaterialTheme.typography.bodyMedium,
            color = SheGuardColors.TextSecondary,
            modifier = Modifier
                .clickable { onBack() }
                .padding(vertical = 8.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        SaharaSectionHeader(
            title = "Trusted Circle Setup",
            subtitle = "Select up to 3 trusted contacts to receive verified emergency escalations."
        )

        Spacer(modifier = Modifier.height(20.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            contacts.forEach { (name, phone) ->
                SaharaContactCard(
                    name = name,
                    relation = phone,
                    status = "Trusted",
                    avatarColor = SheGuardColors.PrimaryContainer,
                    onRemove = { contacts = contacts.filterNot { it.first == name } }
                )
            }

            if (contacts.size < 3) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, SheGuardColors.BorderSubtle, RoundedCornerShape(18.dp)),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = SheGuardColors.SurfaceCard)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "+ Add a trusted contact",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = SheGuardColors.CyanLight
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = contactName,
                            onValueChange = { contactName = it },
                            placeholder = { Text("Contact Name", color = SheGuardColors.TextMuted) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = SheGuardColors.SurfaceElevated,
                                unfocusedContainerColor = SheGuardColors.SurfaceElevated,
                                focusedBorderColor = SheGuardColors.Primary,
                                unfocusedBorderColor = SheGuardColors.BorderSubtle,
                                focusedTextColor = SheGuardColors.TextPrimary,
                                unfocusedTextColor = SheGuardColors.TextPrimary
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = contactPhone,
                            onValueChange = { contactPhone = it },
                            placeholder = { Text("Phone (+91 ...)", color = SheGuardColors.TextMuted) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = SheGuardColors.SurfaceElevated,
                                unfocusedContainerColor = SheGuardColors.SurfaceElevated,
                                focusedBorderColor = SheGuardColors.Primary,
                                unfocusedBorderColor = SheGuardColors.BorderSubtle,
                                focusedTextColor = SheGuardColors.TextPrimary,
                                unfocusedTextColor = SheGuardColors.TextPrimary
                            )
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        SaharaSecondaryButton(
                            text = "Add to Circle",
                            onClick = {
                                if (contactName.isNotBlank() && contactPhone.isNotBlank()) {
                                    contacts = contacts + Pair(contactName, contactPhone)
                                    contactName = ""
                                    contactPhone = ""
                                }
                            }
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(SheGuardColors.SurfaceElevated)
                    .border(1.dp, SheGuardColors.BorderSubtle, RoundedCornerShape(14.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = "🔒 Privacy: Contacts are notified only during active emergencies or Safety Watch.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SheGuardColors.TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        SaharaPrimaryButton(
            text = "Continue",
            onClick = onContinue
        )
    }
}

// =============================================================================
// SCREEN 4 — QUICK PREFERENCES
// =============================================================================

@Composable
fun QuickPreferencesScreen(
    onFinishSetup: () -> Unit,
    onBack: () -> Unit
) {
    var alwaysOnAgent by remember { mutableStateOf(true) }
    var safetyCheckIns by remember { mutableStateOf(false) }
    var motionAssist by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SheGuardColors.Background)
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        Text(
            text = "← Back",
            style = MaterialTheme.typography.bodyMedium,
            color = SheGuardColors.TextSecondary,
            modifier = Modifier
                .clickable { onBack() }
                .padding(vertical = 8.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        SaharaSectionHeader(
            title = "Safety Preferences",
            subtitle = "Customize on-device sensors and distress detection parameters."
        )

        Spacer(modifier = Modifier.height(20.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SaharaToggleCard(
                title = "On-Device Distress Classifier",
                description = "Runs local TFLite audio classification (screams, voice triggers) entirely offline.",
                checked = alwaysOnAgent,
                onCheckedChange = { alwaysOnAgent = it }
            )

            SaharaToggleCard(
                title = "Motion Anomaly Assist",
                description = "Monitors sudden impact / free-fall acceleration signals as secondary distress indicators.",
                checked = motionAssist,
                onCheckedChange = { motionAssist = it }
            )

            SaharaToggleCard(
                title = "Periodic Safety Check-ins",
                description = "Prompts for a quick 1-tap confirmation when navigating unfamiliar areas.",
                checked = safetyCheckIns,
                onCheckedChange = { safetyCheckIns = it }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        SaharaPrimaryButton(
            text = "Save & Go to Dashboard",
            onClick = onFinishSetup
        )
    }
}

// =============================================================================
// SCREEN 5 — HOME DASHBOARD (SHEGUARD COMMAND CENTER)
// =============================================================================

@Composable
fun HomeDashboardScreen(
    isMonitoringActive: Boolean,
    recentIncidentsCount: Int = 0,
    onToggleMonitoring: (Boolean) -> Unit,
    onStartSafetyWatch: () -> Unit,
    onNeedHelp: () -> Unit,
    onOpenCircle: () -> Unit,
    onOpenRecords: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDirectory: () -> Unit,
    onOpenVerifier: () -> Unit,
    onOpenLegalDraft: () -> Unit,
    onOpenAnchoring: () -> Unit,
    onOpenDetectionLog: () -> Unit = {},
    onOpenSheGuardReport: () -> Unit = {}
) {
    var selectedNavTab by remember { mutableStateOf("Home") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SheGuardColors.Background)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "SheGuard",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = SheGuardColors.TextPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(SheGuardColors.EmeraldBg)
                                .border(1.dp, SheGuardColors.EmeraldSuccess.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "OFFLINE FIRST",
                                color = SheGuardColors.EmeraldText,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Text(
                        text = "Intelligent Community Safety System",
                        style = MaterialTheme.typography.bodySmall,
                        color = SheGuardColors.TextSecondary
                    )
                }

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(SheGuardColors.SurfaceElevated)
                        .border(1.dp, SheGuardColors.BorderSubtle, CircleShape)
                        .clickable { onOpenSettings() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "⚙️", fontSize = 18.sp)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Central Animated Radar & Monitoring Toggle
            BreathingSafetyVisual(
                statusText = if (isMonitoringActive) "SheGuard Active" else "SheGuard Standby"
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onToggleMonitoring(!isMonitoringActive) }
            ) {
                SaharaStatusBadge(
                    text = if (isMonitoringActive) "● Safety Monitoring: ACTIVE (Tap to pause)" else "○ Safety Monitoring: STANDBY (Tap to start)",
                    style = if (isMonitoringActive) BadgeStyle.SUCCESS else BadgeStyle.NEUTRAL
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // HERO CARD: SheGuard 5-Phase Pipeline Entry Point
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, SheGuardColors.PrimaryLight.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                    .clickable { onOpenSheGuardReport() },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SheGuardColors.SurfaceCard)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "🚨", fontSize = 22.sp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "SheGuard Safety Pipeline",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = SheGuardColors.TextPrimary
                                )
                                Text(
                                    text = "Report → Detect → Trust → Alert → Mesh",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SheGuardColors.CyanLight,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(SheGuardColors.Primary)
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "Open →",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Submit low-friction micro-reports, evaluate deterministic spatio-temporal clusters, verify multi-signal anti-gaming trust, and view rising-pattern early warnings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SheGuardColors.TextSecondary,
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Emergency & Proactive Safety Actions
            SaharaHoldToActivateButton(
                text = "Emergency Help",
                subtext = "Press & hold for immediate distress escalation",
                onHoldComplete = onNeedHelp,
                isDanger = false
            )

            Spacer(modifier = Modifier.height(10.dp))

            SaharaSecondaryButton(
                text = "🛡️ Start Attentive Safety Watch",
                onClick = onStartSafetyWatch
            )

            Spacer(modifier = Modifier.height(18.dp))

            // 4-Card Live System Status Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CompactStatusItem(
                    title = "Trusted Circle",
                    status = "2 Contacts · Ready",
                    icon = "👥",
                    modifier = Modifier.weight(1f),
                    onClick = onOpenCircle
                )
                CompactStatusItem(
                    title = "Mesh Relay",
                    status = "BLE / P2P Ready",
                    icon = "📡",
                    modifier = Modifier.weight(1f),
                    onClick = onOpenSheGuardReport
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CompactStatusItem(
                    title = "Incident Records",
                    status = if (recentIncidentsCount > 0) "$recentIncidentsCount Sealed Record(s)" else "Quiet · 0 Active",
                    icon = "📋",
                    modifier = Modifier.weight(1f),
                    onClick = onOpenRecords
                )
                CompactStatusItem(
                    title = "Help Directory",
                    status = "Mumbai 100 / 1090",
                    icon = "📞",
                    modifier = Modifier.weight(1f),
                    onClick = onOpenDirectory
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Supporting Tools Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MiniToolButton("Export & Verify", onClick = onOpenVerifier, modifier = Modifier.weight(1f))
                MiniToolButton("AI FIR Drafter", onClick = onOpenLegalDraft, modifier = Modifier.weight(1f))
                MiniToolButton("Anchoring", onClick = onOpenAnchoring, modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(10.dp))

            SaharaSecondaryButton(
                text = "📊 View Real-Time Detection Log",
                onClick = onOpenDetectionLog
            )
        }

        // Bottom Navigation Bar
        SaharaBottomNav(
            selectedTab = selectedNavTab,
            onSelectTab = { tab ->
                selectedNavTab = tab
                when (tab) {
                    "Circle" -> onOpenCircle()
                    "Records" -> onOpenRecords()
                    "Settings" -> onOpenSettings()
                }
            }
        )
    }
}

@Composable
private fun CompactStatusItem(
    title: String,
    status: String,
    icon: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .border(1.dp, SheGuardColors.BorderSubtle, RoundedCornerShape(16.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SheGuardColors.SurfaceCard)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = icon, fontSize = 16.sp)
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(SheGuardColors.EmeraldSuccess)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = SheGuardColors.TextPrimary
            )
            Text(
                text = status,
                style = MaterialTheme.typography.labelSmall,
                color = SheGuardColors.TextSecondary
            )
        }
    }
}

@Composable
private fun MiniToolButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(SheGuardColors.SurfaceElevated)
            .border(1.dp, SheGuardColors.BorderSubtle, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = SheGuardColors.CyanLight
        )
    }
}

// =============================================================================
// SCREEN 6 — SAFETY WATCH
// =============================================================================

@Composable
fun SafetyWatchScreen(
    onImSafe: () -> Unit,
    onNeedHelpNow: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SheGuardColors.Background)
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(SheGuardColors.PrimaryContainer)
                    .border(2.dp, SheGuardColors.CyanLight, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "👁️", fontSize = 34.sp)
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "Safety Watch Active",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = SheGuardColors.TextPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "SheGuard is proactively monitoring on-device audio, motion, and location signals while you transit.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = SheGuardColors.TextSecondary,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SaharaSafetyStatusCard(
                    title = "Location",
                    subtitle = "Locally cached coordinates (19.0760, 72.8777)",
                    stateText = "Ready",
                    stateBadgeStyle = BadgeStyle.SUCCESS,
                    iconLetter = "📍"
                )
                SaharaSafetyStatusCard(
                    title = "Distress Audio Engine",
                    subtitle = "TFLite Scream & Keyword classifier high sensitivity",
                    stateText = "Attentive",
                    stateBadgeStyle = BadgeStyle.ACTIVE_PINK,
                    iconLetter = "🎙️"
                )
                SaharaSafetyStatusCard(
                    title = "Notify Circle",
                    subtitle = "Standing by. Will escalate only on confirmed distress",
                    stateText = "Ready",
                    stateBadgeStyle = BadgeStyle.INFO,
                    iconLetter = "👥"
                )
            }
        }

        // Actions
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SaharaPrimaryButton(
                text = "I'm Safe (End Watch)",
                onClick = onImSafe
            )
            SaharaSecondaryButton(
                text = "I Need Help Now",
                onClick = onNeedHelpNow
            )
        }
    }
}

// =============================================================================
// SCREEN 7 — ACTIVE INCIDENT
// =============================================================================

@Composable
fun ActiveIncidentScreen(
    elapsedSeconds: Int = 18,
    onEndIncident: () -> Unit
) {
    val formattedTime = String.format("%02d:%02d", elapsedSeconds / 60, elapsedSeconds % 60)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SheGuardColors.Background)
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.verticalScroll(rememberScrollState())
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(SheGuardColors.RoseBg)
                    .border(2.dp, SheGuardColors.RoseDanger, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "🚨", fontSize = 36.sp)
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Emergency Mode Active",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = SheGuardColors.TextPrimary
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Securing local evidence and dispatching escalation signals.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = SheGuardColors.TextSecondary
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Timer Banner
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, SheGuardColors.RoseDanger, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SheGuardColors.RoseBg)
            ) {
                Row(
                    modifier = Modifier
                        .padding(14.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(SheGuardColors.RoseDanger)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Securing Evidence",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Text(
                        text = formattedTime,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Status Checklist
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, SheGuardColors.BorderSubtle, RoundedCornerShape(18.dp)),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = SheGuardColors.SurfaceCard)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Escalation Status",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = SheGuardColors.TextPrimary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    ActionRowItem(
                        icon = "✓",
                        title = "Pre-roll audio evidence captured",
                        subtitle = "AES-256-GCM encrypted in device Keystore",
                        isDone = true
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    ActionRowItem(
                        icon = "⟳",
                        title = "Dispatching trusted circle alerts",
                        subtitle = "Attempting Nearby Mesh relay + direct SMS",
                        isDone = false
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Contact delivery states
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(SheGuardColors.SurfaceElevated)
                            .padding(12.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Aisha (Sister)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, color = SheGuardColors.TextPrimary)
                                Text("Delivered ✓", color = SheGuardColors.EmeraldText, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Sara (Friend)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, color = SheGuardColors.TextPrimary)
                                Text("Dispatching... ⟳", color = SheGuardColors.CyanLight, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        SaharaHoldToActivateButton(
            text = "I'm Safe",
            subtext = "Press and hold to seal incident & end",
            onHoldComplete = onEndIncident,
            isDanger = false
        )
    }
}

@Composable
private fun ActionRowItem(
    icon: String,
    title: String,
    subtitle: String,
    isDone: Boolean
) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(if (isDone) SheGuardColors.EmeraldBg else SheGuardColors.PrimaryContainer)
                .border(1.dp, if (isDone) SheGuardColors.EmeraldSuccess else SheGuardColors.PrimaryLight, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = icon,
                color = if (isDone) SheGuardColors.EmeraldText else SheGuardColors.CyanLight,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = SheGuardColors.TextPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = SheGuardColors.TextSecondary
            )
        }
    }
}

// =============================================================================
// SCREEN 8 — INCIDENT SEALED
// =============================================================================

@Composable
fun IncidentSealedScreen(
    onViewRecord: () -> Unit,
    onShareCircle: () -> Unit,
    onReturnHome: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SheGuardColors.Background)
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(CircleShape)
                    .background(SheGuardColors.EmeraldBg)
                    .border(2.dp, SheGuardColors.EmeraldSuccess, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "🔒", fontSize = 40.sp)
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "Incident Sealed & Protected",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = SheGuardColors.TextPrimary
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "All evidence has been cryptographically sealed with a SHA-256 Merkle root in local storage.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = SheGuardColors.TextSecondary
            )

            Spacer(modifier = Modifier.height(18.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, SheGuardColors.EmeraldSuccess, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SheGuardColors.EmeraldBg.copy(alpha = 0.6f))
            ) {
                Row(
                    modifier = Modifier
                        .padding(14.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "🛡️", fontSize = 22.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Merkle Integrity Verified",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = SheGuardColors.EmeraldText
                        )
                        Text(
                            text = "Tamper-evident manifest signed with Android Keystore key.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SheGuardColors.TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, SheGuardColors.BorderSubtle, RoundedCornerShape(18.dp)),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = SheGuardColors.SurfaceCard)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Incident Summary",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = SheGuardColors.TextPrimary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    SummaryRow("Time", "Today at 8:41 PM")
                    SummaryRow("Duration", "3m 42s")
                    SummaryRow("Evidence captured", "AES-256-GCM Encrypted Audio")
                    SummaryRow("Location recorded", "Bandra West / Mumbai Central")
                    SummaryRow("Circle notified", "2 contacts alerted via Mesh/SMS")
                }
            }
        }

        // Actions
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SaharaPrimaryButton(
                text = "View Incident Record",
                onClick = onViewRecord
            )
            SaharaSecondaryButton(
                text = "Return to Dashboard",
                onClick = onReturnHome
            )
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = SheGuardColors.TextSecondary)
        Text(text = value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = SheGuardColors.TextPrimary)
    }
}

// =============================================================================
// SCREEN 9 — INCIDENT RECORD / TIMELINE
// =============================================================================

@Composable
fun IncidentTimelineScreen(
    onExportVerified: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SheGuardColors.Background)
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        Text(
            text = "← Back",
            style = MaterialTheme.typography.bodyMedium,
            color = SheGuardColors.TextSecondary,
            modifier = Modifier
                .clickable { onBack() }
                .padding(vertical = 8.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        SaharaSectionHeader(
            title = "Incident Timeline & Audit",
            subtitle = "Chronological event log sealed with Android Keystore cryptographic proof."
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .border(1.dp, SheGuardColors.BorderSubtle, RoundedCornerShape(18.dp)),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = SheGuardColors.SurfaceCard)
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                SaharaTimelineItem(time = "8:32 PM", title = "Safety Watch activated", subtitle = "Proactive sensor monitoring active")
                SaharaTimelineItem(time = "8:41 PM", title = "Distress signals classified", subtitle = "TFLite audio classifier confirmed high confidence")
                SaharaTimelineItem(time = "8:41 PM", title = "Emergency mode engaged", subtitle = "Autonomous safety state transition")
                SaharaTimelineItem(time = "8:41 PM", title = "Pre-roll evidence sealed", subtitle = "Rolling buffer encrypted into AES-GCM storage")
                SaharaTimelineItem(time = "8:42 PM", title = "Notify Circle dispatched", subtitle = "Mesh relay & SMS delivery sent")
                SaharaTimelineItem(time = "8:45 PM", title = "Incident ended by user", subtitle = "Safety confirmed via pass-hold")
                SaharaTimelineItem(time = "8:45 PM", title = "Record sealed with Merkle Root ✓", subtitle = "SHA-256 tree computed & signed in Keystore", isLast = true, isVerified = true)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        SaharaPrimaryButton(
            text = "Export Verified Package",
            onClick = onExportVerified
        )
    }
}

// =============================================================================
// SCREEN 10 — TRUSTED CONTACT ALERT
// =============================================================================

@Composable
fun TrustedContactAlertScreen(
    onCheckIn: () -> Unit,
    onCall: () -> Unit,
    onGetDirections: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SheGuardColors.Background)
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        Text(
            text = "← Back",
            style = MaterialTheme.typography.bodyMedium,
            color = SheGuardColors.TextSecondary,
            modifier = Modifier
                .clickable { onBack() }
                .padding(vertical = 8.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        SaharaSectionHeader(
            title = "Emergency Circle Alert",
            subtitle = "You received a verified distress alert from your circle."
        )

        Spacer(modifier = Modifier.height(16.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, SheGuardColors.RoseDanger, RoundedCornerShape(18.dp)),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = SheGuardColors.SurfaceCard)
            ) {
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(SheGuardColors.RoseBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "M", color = SheGuardColors.RoseDanger, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(text = "Maya", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = SheGuardColors.TextPrimary)
                        SaharaStatusBadge(text = "Emergency Mode Activated", style = BadgeStyle.WARNING)
                    }
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, SheGuardColors.BorderSubtle, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SheGuardColors.SurfaceElevated)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SummaryRow("Time", "Today at 8:41 PM")
                    SummaryRow("Approximate Location", "Bandra West / Mumbai Central")
                    SummaryRow("Transport", "Direct SMS + Mesh Relay ✓")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SaharaPrimaryButton(
                text = "Acknowledge Alert",
                onClick = onCheckIn
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SaharaSecondaryButton(
                    text = "Call",
                    onClick = onCall,
                    modifier = Modifier.weight(1f)
                )
                SaharaSecondaryButton(
                    text = "Directions",
                    onClick = onGetDirections,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

// =============================================================================
// SCREEN 11 — NOTIFY CIRCLE MANAGEMENT
// =============================================================================

@Composable
fun NotifyCircleManagementScreen(
    contacts: List<NotifyContact>,
    onAddContact: (String, String) -> Unit,
    onRemoveContact: (NotifyContact) -> Unit,
    onBack: () -> Unit
) {
    var newName by remember { mutableStateOf("") }
    var newPhone by remember { mutableStateOf("") }
    var syncStatus by remember { mutableStateOf<String?>(null) }
    var isSyncing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SheGuardColors.Background)
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        Text(
            text = "← Back",
            style = MaterialTheme.typography.bodyMedium,
            color = SheGuardColors.TextSecondary,
            modifier = Modifier
                .clickable { onBack() }
                .padding(vertical = 8.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        SaharaSectionHeader(
            title = "Manage Trusted Circle",
            subtitle = "Contacts receive alerts only during emergency events or Safety Watch."
        )

        Spacer(modifier = Modifier.height(18.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            contacts.forEach { contact ->
                SaharaContactCard(
                    name = contact.displayName,
                    relation = contact.phoneNumber ?: "Direct Contact",
                    status = "Ready",
                    avatarColor = SheGuardColors.PrimaryContainer,
                    onRemove = { onRemoveContact(contact) }
                )
            }

            if (contacts.size < 3) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, SheGuardColors.BorderSubtle, RoundedCornerShape(18.dp)),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = SheGuardColors.SurfaceCard)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "+ Add Contact (${contacts.size}/3)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = SheGuardColors.CyanLight
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it },
                            placeholder = { Text("Contact Name", color = SheGuardColors.TextMuted) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = SheGuardColors.SurfaceElevated,
                                unfocusedContainerColor = SheGuardColors.SurfaceElevated,
                                focusedBorderColor = SheGuardColors.Primary,
                                unfocusedBorderColor = SheGuardColors.BorderSubtle,
                                focusedTextColor = SheGuardColors.TextPrimary,
                                unfocusedTextColor = SheGuardColors.TextPrimary
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = newPhone,
                            onValueChange = { newPhone = it },
                            placeholder = { Text("Phone Number", color = SheGuardColors.TextMuted) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = SheGuardColors.SurfaceElevated,
                                unfocusedContainerColor = SheGuardColors.SurfaceElevated,
                                focusedBorderColor = SheGuardColors.Primary,
                                unfocusedBorderColor = SheGuardColors.BorderSubtle,
                                focusedTextColor = SheGuardColors.TextPrimary,
                                unfocusedTextColor = SheGuardColors.TextPrimary
                            )
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        SaharaPrimaryButton(
                            text = "Add Contact",
                            onClick = {
                                if (newName.isNotBlank() && newPhone.isNotBlank()) {
                                    onAddContact(newName, newPhone)
                                    newName = ""
                                    newPhone = ""
                                }
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            SaharaSecondaryButton(
                text = if (isSyncing) "Syncing Circle..." else "Sync Circle to Backend 🔄",
                onClick = {
                    scope.launch {
                        isSyncing = true
                        try {
                            val memberJsons = contacts.map { contact ->
                                val contactId = contact.contactId.toString()
                                "{\"contact_id\":\"$contactId\",\"display_name\":\"${contact.displayName}\",\"type\":\"${contact.type}\",\"phone_number\":${if (contact.phoneNumber != null) "\"${contact.phoneNumber}\"" else "null"},\"app_user_id\":null,\"location_permission\":${contact.locationPermission},\"notification_permission\":${contact.notificationPermission}}"
                            }.joinToString(",")
                            val body = "{\"members\":[$memberJsons]}"
                            val token = SaharaApiClient.savedAccessToken ?: "bearer_demo_token"
                            SaharaApiClient.putJson("/api/v1/notify/circle", body, bearerToken = token)
                            syncStatus = "Circle Synced Successfully ✓"
                        } catch (e: Exception) {
                            syncStatus = "Sync Failed: ${e.message}"
                        } finally {
                            isSyncing = false
                        }
                    }
                }
            )

            if (syncStatus != null) {
                Text(
                    text = syncStatus!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (syncStatus!!.contains("Successfully")) SheGuardColors.EmeraldText else SheGuardColors.RoseDanger,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

// =============================================================================
// SUPPORTING SCREENS (HELP DIRECTORY, VERIFIER, AUTH, LEGAL, ANCHORING)
// =============================================================================

@Composable
fun HelpDirectoryScreen(onBack: () -> Unit) {
    val contacts = OfflineHelpDirectory.getAllContacts()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SheGuardColors.Background)
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        Text(
            text = "← Back",
            style = MaterialTheme.typography.bodyMedium,
            color = SheGuardColors.TextSecondary,
            modifier = Modifier
                .clickable { onBack() }
                .padding(vertical = 8.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        SaharaSectionHeader(
            title = "Offline Helplines Directory",
            subtitle = "Direct emergency hotlines and local women's helplines in Mumbai & Maharashtra."
        )

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(contacts) { contact ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, SheGuardColors.BorderSubtle, RoundedCornerShape(16.dp))
                        .clickable {
                            try {
                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${contact.phone}"))
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SheGuardColors.SurfaceCard)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = contact.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = SheGuardColors.TextPrimary)
                            SaharaStatusBadge(text = "📞 ${contact.phone}", style = BadgeStyle.INFO)
                        }
                        Text(text = "City: ${contact.city} · Tap to call", style = MaterialTheme.typography.bodySmall, color = SheGuardColors.CyanLight)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = contact.description, style = MaterialTheme.typography.bodySmall, color = SheGuardColors.TextSecondary)
                    }
                }
            }
        }
    }
}

@Composable
fun ExportVerifierScreen(
    exportPackage: ExportPackage?,
    onVerifyPackage: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SheGuardColors.Background)
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        Text(
            text = "← Back",
            style = MaterialTheme.typography.bodyMedium,
            color = SheGuardColors.TextSecondary,
            modifier = Modifier
                .clickable { onBack() }
                .padding(vertical = 8.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        SaharaSectionHeader(
            title = "Evidence & Integrity Verifier",
            subtitle = "Verify cryptographic Merkle tree integrity and export signed evidence packages."
        )

        Spacer(modifier = Modifier.height(20.dp))

        if (exportPackage != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        if (exportPackage.isIntegrityVerified) SheGuardColors.EmeraldSuccess else SheGuardColors.RoseDanger,
                        RoundedCornerShape(18.dp)
                    ),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (exportPackage.isIntegrityVerified) SheGuardColors.EmeraldBg else SheGuardColors.RoseBg
                )
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = if (exportPackage.isIntegrityVerified) "INTEGRITY VERIFIED ✓" else "VERIFICATION WARNING",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (exportPackage.isIntegrityVerified) SheGuardColors.EmeraldText else SheGuardColors.RoseText
                    )
                    exportPackage.warningDisclaimer?.let {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = it, color = SheGuardColors.RoseText, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = exportPackage.summaryText, style = MaterialTheme.typography.bodySmall, color = Color.White)
                }
            }
        } else {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, SheGuardColors.BorderSubtle, RoundedCornerShape(18.dp)),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = SheGuardColors.SurfaceCard)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Ready to Verify Incident Package",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = SheGuardColors.TextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Validates Keystore digital signatures, SHA-256 evidence hashes, and Merkle tree root on-device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SheGuardColors.TextSecondary
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    SaharaPrimaryButton(
                        text = "Export & Verify Incident",
                        onClick = onVerifyPackage
                    )
                }
            }
        }
    }
}

@Composable
fun LegalDraftingScreen(onBack: () -> Unit) {
    var incidentSummary by remember { mutableStateOf("Distress signal triggered near Bandra West. High pitch scream detected, panic button activated.") }
    var victimName by remember { mutableStateOf("Maya Sharma") }
    var locationText by remember { mutableStateOf("Bandra West, Mumbai") }
    var generatedDraft by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SheGuardColors.Background)
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        Text(
            text = "← Back",
            style = MaterialTheme.typography.bodyMedium,
            color = SheGuardColors.TextSecondary,
            modifier = Modifier
                .clickable { onBack() }
                .padding(vertical = 8.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        SaharaSectionHeader(
            title = "AI Legal Complaint Drafter",
            subtitle = "Prepares structured complaint drafts for human and legal review."
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, SheGuardColors.BorderSubtle, RoundedCornerShape(18.dp)),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = SheGuardColors.SurfaceCard)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Incident Summary:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, color = SheGuardColors.TextPrimary)
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = incidentSummary,
                    onValueChange = { incidentSummary = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SheGuardColors.SurfaceElevated,
                        unfocusedContainerColor = SheGuardColors.SurfaceElevated,
                        focusedBorderColor = SheGuardColors.Primary,
                        unfocusedBorderColor = SheGuardColors.BorderSubtle,
                        focusedTextColor = SheGuardColors.TextPrimary,
                        unfocusedTextColor = SheGuardColors.TextPrimary
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = victimName,
                    onValueChange = { victimName = it },
                    placeholder = { Text("Complainant Name", color = SheGuardColors.TextMuted) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SheGuardColors.SurfaceElevated,
                        unfocusedContainerColor = SheGuardColors.SurfaceElevated,
                        focusedBorderColor = SheGuardColors.Primary,
                        unfocusedBorderColor = SheGuardColors.BorderSubtle,
                        focusedTextColor = SheGuardColors.TextPrimary,
                        unfocusedTextColor = SheGuardColors.TextPrimary
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = locationText,
                    onValueChange = { locationText = it },
                    placeholder = { Text("Location", color = SheGuardColors.TextMuted) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SheGuardColors.SurfaceElevated,
                        unfocusedContainerColor = SheGuardColors.SurfaceElevated,
                        focusedBorderColor = SheGuardColors.Primary,
                        unfocusedBorderColor = SheGuardColors.BorderSubtle,
                        focusedTextColor = SheGuardColors.TextPrimary,
                        unfocusedTextColor = SheGuardColors.TextPrimary
                    )
                )
                Spacer(modifier = Modifier.height(12.dp))
                SaharaPrimaryButton(
                    text = if (isLoading) "Generating AI Draft..." else "Generate Structured FIR Draft",
                    onClick = {
                        scope.launch {
                            isLoading = true
                            try {
                                val token = SaharaApiClient.savedAccessToken ?: "bearer_demo_token"
                                val targetIncId = "inc_${System.currentTimeMillis()}"
                                val responseJson = SaharaApiClient.generateLegalDraft(
                                    incidentId = targetIncId,
                                    summaryText = incidentSummary,
                                    victimName = victimName,
                                    locationText = locationText,
                                    bearerToken = token
                                )
                                val contentMatch = Regex("\"content\"\\s*:\\s*\"([^\"]+)\"").find(responseJson)
                                val disclaimerMatch = Regex("\"disclaimer\"\\s*:\\s*\"([^\"]+)\"").find(responseJson)
                                if (contentMatch != null) {
                                    val contentStr = contentMatch.groupValues[1].replace("\\n", "\n").replace("\\\"", "\"")
                                    val disclaimerStr = disclaimerMatch?.groupValues?.get(1)?.replace("\\n", "\n")
                                        ?: "DRAFT FOR HUMAN AND LEGAL REVIEW. THIS DOCUMENT HAS NOT BEEN FILED WITH ANY AUTHORITY."
                                    generatedDraft = "$disclaimerStr\n\n$contentStr"
                                } else {
                                    generatedDraft = responseJson
                                }
                            } catch (e: Exception) {
                                generatedDraft = "DRAFT FOR HUMAN AND LEGAL REVIEW. THIS DOCUMENT HAS NOT BEEN FILED WITH ANY AUTHORITY.\n\n" +
                                    "[OFFLINE FALLBACK DRAFT]\n" +
                                    "FIRST INFORMATION REPORT (DRAFT)\n\n" +
                                    "Incident Context: $incidentSummary\n" +
                                    "Complainant/Victim: $victimName\n" +
                                    "Location: $locationText\n\n" +
                                    "Statement: The complainant reported a distress situation requiring emergency assistance. Structured facts preserved locally."
                            } finally {
                                isLoading = false
                            }
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (generatedDraft != null) {
            Card(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .border(1.dp, SheGuardColors.AmberWarning, RoundedCornerShape(18.dp)),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = SheGuardColors.AmberBg.copy(alpha = 0.6f))
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "LEGAL AGENT OUTPUT:",
                        fontWeight = FontWeight.Bold,
                        color = SheGuardColors.AmberText
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = generatedDraft!!, fontSize = 12.sp, lineHeight = 18.sp, color = Color.White)
                }
            }
        }
    }
}

@Composable
fun AnchoringScreen(onBack: () -> Unit) {
    var merkleRootInput by remember { mutableStateOf("0x3f7a8b9c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a") }
    var anchorStatus by remember { mutableStateOf("NOT_ANCHORED") }
    var txHash by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SheGuardColors.Background)
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        Text(
            text = "← Back",
            style = MaterialTheme.typography.bodyMedium,
            color = SheGuardColors.TextSecondary,
            modifier = Modifier
                .clickable { onBack() }
                .padding(vertical = 8.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        SaharaSectionHeader(
            title = "Blockchain Merkle Anchoring",
            subtitle = "Optional remote timestamp anchoring of sealed Merkle roots."
        )

        Spacer(modifier = Modifier.height(20.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, SheGuardColors.BorderSubtle, RoundedCornerShape(18.dp)),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = SheGuardColors.SurfaceCard)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Sealed Merkle Root:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, color = SheGuardColors.TextPrimary)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = merkleRootInput,
                    onValueChange = { merkleRootInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SheGuardColors.SurfaceElevated,
                        unfocusedContainerColor = SheGuardColors.SurfaceElevated,
                        focusedBorderColor = SheGuardColors.Primary,
                        unfocusedBorderColor = SheGuardColors.BorderSubtle,
                        focusedTextColor = SheGuardColors.TextPrimary,
                        unfocusedTextColor = SheGuardColors.TextPrimary
                    )
                )
                Spacer(modifier = Modifier.height(14.dp))
                SaharaPrimaryButton(
                    text = if (isLoading) "Anchoring..." else "Anchor Root to Blockchain",
                    onClick = {
                        scope.launch {
                            isLoading = true
                            try {
                                val nowSeconds = System.currentTimeMillis() / 1000
                                val body = "{\"merkle_root\":\"${merkleRootInput.trim()}\",\"device_signature\":\"sig_device_dummy\",\"timestamp\":$nowSeconds}"
                                val token = SaharaApiClient.savedAccessToken ?: "bearer_demo_token"
                                val responseJson = SaharaApiClient.postJson("/api/v1/anchors", body, bearerToken = token)
                                val statusMatch = Regex("\"status\"\\s*:\\s*\"([^\"]+)\"").find(responseJson)
                                val txMatch = Regex("\"transaction_hash\"\\s*:\\s*\"([^\"]+)\"").find(responseJson)
                                if (statusMatch != null) {
                                    val statusStr = statusMatch.groupValues[1]
                                    anchorStatus = "ANCHORED ($statusStr)"
                                    txHash = txMatch?.groupValues?.get(1)
                                } else {
                                    anchorStatus = "ANCHORED: $responseJson"
                                }
                            } catch (e: Exception) {
                                anchorStatus = "Anchoring Failed: ${e.message}"
                            } finally {
                                isLoading = false
                            }
                        }
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text("Status: $anchorStatus", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = SheGuardColors.CyanLight)
                if (txHash != null) {
                    Text("Tx Hash: $txHash", style = MaterialTheme.typography.bodySmall, color = SheGuardColors.EmeraldText)
                }
            }
        }
    }
}

object SaharaApiClient {
    var baseUrl = "http://10.0.2.2:8000"
    var savedAccessToken: String? = null

    suspend fun generateLegalDraft(
        incidentId: String,
        summaryText: String,
        victimName: String,
        locationText: String,
        bearerToken: String? = savedAccessToken
    ): String = withContext(Dispatchers.IO) {
        val escSummary = summaryText.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
        val escName = victimName.replace("\\", "\\\\").replace("\"", "\\\"")
        val escLoc = locationText.replace("\\", "\\\\").replace("\"", "\\\"")
        val body = """
            {
              "incident_id": "$incidentId",
              "draft_type": "FIR_COMPLAINT",
              "authorized_summary": {
                "incident_summary": "$escSummary",
                "victim_name": "$escName",
                "location_text": "$escLoc"
              },
              "user_authorized": true
            }
        """.trimIndent()
        postJson("/api/v1/legal/drafts", body, bearerToken)
    }

    suspend fun postJson(endpoint: String, jsonBody: String, bearerToken: String? = savedAccessToken): String = withContext(Dispatchers.IO) {
        val url = java.net.URL("$baseUrl$endpoint")
        val conn = url.openConnection() as java.net.HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Accept", "application/json")
        if (!bearerToken.isNullOrBlank()) {
            conn.setRequestProperty("Authorization", "Bearer $bearerToken")
        }
        conn.doOutput = true
        conn.connectTimeout = 5000
        conn.readTimeout = 5000

        conn.outputStream.use { os ->
            os.write(jsonBody.toByteArray(Charsets.UTF_8))
        }

        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val response = stream?.bufferedReader()?.use { it.readText() } ?: ""
        if (code !in 200..299) {
            throw java.io.IOException("HTTP $code: $response")
        }
        response
    }

    suspend fun putJson(endpoint: String, jsonBody: String, bearerToken: String? = savedAccessToken): String = withContext(Dispatchers.IO) {
        val url = java.net.URL("$baseUrl$endpoint")
        val conn = url.openConnection() as java.net.HttpURLConnection
        conn.requestMethod = "PUT"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Accept", "application/json")
        if (!bearerToken.isNullOrBlank()) {
            conn.setRequestProperty("Authorization", "Bearer $bearerToken")
        }
        conn.doOutput = true
        conn.connectTimeout = 5000
        conn.readTimeout = 5000

        conn.outputStream.use { os ->
            os.write(jsonBody.toByteArray(Charsets.UTF_8))
        }

        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val response = stream?.bufferedReader()?.use { it.readText() } ?: ""
        if (code !in 200..299) {
            throw java.io.IOException("HTTP $code: $response")
        }
        response
    }
}

@Composable
fun AuthScreen(onBack: () -> Unit) {
    var phoneNumber by remember { mutableStateOf("+91 9876543210") }
    var otpCode by remember { mutableStateOf("") }
    var isOtpSent by remember { mutableStateOf(false) }
    var authStatus by remember { mutableStateOf("NOT_AUTHENTICATED") }
    var requestId by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SheGuardColors.Background)
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        Text(
            text = "← Back",
            style = MaterialTheme.typography.bodyMedium,
            color = SheGuardColors.TextSecondary,
            modifier = Modifier
                .clickable { onBack() }
                .padding(vertical = 8.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        SaharaSectionHeader(
            title = "Phone Authentication",
            subtitle = "Optional cloud sync for Notify Circle & remote backups."
        )

        Spacer(modifier = Modifier.height(20.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, SheGuardColors.BorderSubtle, RoundedCornerShape(18.dp)),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = SheGuardColors.SurfaceCard)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Phone Number:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, color = SheGuardColors.TextPrimary)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = { phoneNumber = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SheGuardColors.SurfaceElevated,
                        unfocusedContainerColor = SheGuardColors.SurfaceElevated,
                        focusedBorderColor = SheGuardColors.Primary,
                        unfocusedBorderColor = SheGuardColors.BorderSubtle,
                        focusedTextColor = SheGuardColors.TextPrimary,
                        unfocusedTextColor = SheGuardColors.TextPrimary
                    )
                )
                Spacer(modifier = Modifier.height(12.dp))
                SaharaPrimaryButton(
                    text = if (isLoading && !isOtpSent) "Requesting OTP..." else "Request OTP Code",
                    enabled = phoneNumber.trim().length >= 8,
                    onClick = {
                        scope.launch {
                            isLoading = true
                            try {
                                val body = "{\"phone_number\":\"${phoneNumber.trim()}\"}"
                                val responseJson = SaharaApiClient.postJson("/api/v1/auth/request-otp", body)
                                val reqIdMatch = Regex("\"request_id\"\\s*:\\s*\"([^\"]+)\"").find(responseJson)
                                if (reqIdMatch != null) {
                                    requestId = reqIdMatch.groupValues[1]
                                    isOtpSent = true
                                    authStatus = "OTP_SENT (ReqID: ${requestId.take(8)}...)"
                                } else {
                                    authStatus = "OTP Request Sent: $responseJson"
                                    isOtpSent = true
                                }
                            } catch (e: Exception) {
                                authStatus = "OTP Request Failed: ${e.message}"
                            } finally {
                                isLoading = false
                            }
                        }
                    }
                )

                if (isLoading) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = SheGuardColors.Primary
                        )
                    }
                }

                if (isOtpSent) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Enter 6-Digit OTP:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, color = SheGuardColors.TextPrimary)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = otpCode,
                        onValueChange = { otpCode = it },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = SheGuardColors.SurfaceElevated,
                            unfocusedContainerColor = SheGuardColors.SurfaceElevated,
                            focusedBorderColor = SheGuardColors.Primary,
                            unfocusedBorderColor = SheGuardColors.BorderSubtle,
                            focusedTextColor = SheGuardColors.TextPrimary,
                            unfocusedTextColor = SheGuardColors.TextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    SaharaSecondaryButton(
                        text = if (isLoading) "Verifying..." else "Verify Code",
                        onClick = {
                            scope.launch {
                                isLoading = true
                                try {
                                    val body = "{\"request_id\":\"$requestId\",\"otp_code\":\"${otpCode.trim()}\"}"
                                    val responseJson = SaharaApiClient.postJson("/api/v1/auth/verify-otp", body)
                                    val tokenMatch = Regex("\"access_token\"\\s*:\\s*\"([^\"]+)\"").find(responseJson)
                                    val userMatch = Regex("\"user_id\"\\s*:\\s*\"([^\"]+)\"").find(responseJson)
                                    if (tokenMatch != null) {
                                        val token = tokenMatch.groupValues[1]
                                        val userId = userMatch?.groupValues?.get(1) ?: "unknown"
                                        SaharaApiClient.savedAccessToken = token
                                        authStatus = "AUTHENTICATED (User: $userId)"
                                    } else {
                                        authStatus = "VERIFIED: $responseJson"
                                    }
                                } catch (e: Exception) {
                                    authStatus = "Verification Failed: ${e.message}"
                                } finally {
                                    isLoading = false
                                }
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text("Status: $authStatus", style = MaterialTheme.typography.bodySmall, color = SheGuardColors.TextSecondary)
            }
        }
    }
}
