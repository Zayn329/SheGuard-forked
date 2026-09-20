package org.sahara.app.ui

import androidx.compose.animation.AnimatedVisibility
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
            .background(SaharaColors.WarmWhite)
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        // Brand & Woman-Centered Safety Illustration
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 20.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(SaharaColors.VerySoftPink, SaharaColors.VerySoftBlue, SaharaColors.VerySoftYellow)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Soft abstract connected motif
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .clip(CircleShape)
                        .background(SaharaColors.SoftPink),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🌸",
                        fontSize = 48.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Safety, even\nwithout signal.",
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                color = SaharaColors.TextPrimary
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Your phone can recognise when you may need help, reach people you trust, and keep a secure record — even when you're offline.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = SaharaColors.TextSecondary,
                lineHeight = 24.sp
            )
        }

        // Action Buttons
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SaharaPrimaryButton(
                text = "Get started",
                onClick = onGetStarted
            )
            SaharaSecondaryButton(
                text = "Learn how it works",
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
            .background(SaharaColors.WarmWhite)
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        Text(
            text = "← Back",
            style = MaterialTheme.typography.bodyMedium,
            color = SaharaColors.TextSecondary,
            modifier = Modifier
                .clickable { onBack() }
                .padding(vertical = 8.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        SaharaSectionHeader(
            title = "A little access.\nA clear reason.",
            subtitle = "Safety features need access to a few parts of your phone. We'll always explain why before asking."
        )

        Spacer(modifier = Modifier.height(24.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            PermissionCard(
                icon = "🎙️",
                title = "Microphone",
                subtitle = "Listen for signs of distress",
                explanation = "Processed privately on your device. Audio never leaves your phone unless sealed as evidence."
            )
            PermissionCard(
                icon = "📍",
                title = "Location",
                subtitle = "Know where help is needed",
                explanation = "Used only when Safety Watch or Incident mode is active. Never continuously tracked."
            )
            PermissionCard(
                icon = "📡",
                title = "Nearby Devices",
                subtitle = "Find a path when you're offline",
                explanation = "Uses encrypted local mesh relay to send alerts to nearby devices when cellular data is down."
            )
            PermissionCard(
                icon = "💬",
                title = "SMS",
                subtitle = "Keep a backup way to reach people",
                explanation = "Sends direct quiet SMS alerts to your Notify Circle if data connectivity fails."
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
            .shadow(2.dp, RoundedCornerShape(22.dp), spotColor = SaharaColors.ShadowTint),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = SaharaColors.PureWhite)
    ) {
        Row(
            modifier = Modifier
                .padding(18.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(SaharaColors.VerySoftPink),
                contentAlignment = Alignment.Center
            ) {
                Text(text = icon, fontSize = 20.sp)
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = SaharaColors.TextPrimary
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = SaharaColors.PinkPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = explanation,
                    style = MaterialTheme.typography.bodySmall,
                    color = SaharaColors.TextSecondary,
                    lineHeight = 18.sp
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
                Pair("Sara (Best Friend)", "+91 9876543211")
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SaharaColors.WarmWhite)
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        Text(
            text = "← Back",
            style = MaterialTheme.typography.bodyMedium,
            color = SaharaColors.TextSecondary,
            modifier = Modifier
                .clickable { onBack() }
                .padding(vertical = 8.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        SaharaSectionHeader(
            title = "Your people, close\nwhen it matters.",
            subtitle = "Choose up to three people you'd want to know if you need help."
        )

        Spacer(modifier = Modifier.height(20.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Added Contacts List
            contacts.forEach { (name, phone) ->
                SaharaContactCard(
                    name = name,
                    relation = phone,
                    status = "Trusted",
                    avatarColor = SaharaColors.SoftPink,
                    onRemove = { contacts = contacts.filterNot { it.first == name } }
                )
            }

            if (contacts.size < 3) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = SaharaColors.SurfaceSubtle)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "+ Add a trusted person",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = SaharaColors.PinkPrimary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = contactName,
                            onValueChange = { contactName = it },
                            placeholder = { Text("Name (e.g. Maya)") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = SaharaColors.PureWhite,
                                unfocusedContainerColor = SaharaColors.PureWhite,
                                focusedBorderColor = SaharaColors.PinkPrimary,
                                unfocusedBorderColor = SaharaColors.BorderSubtle
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = contactPhone,
                            onValueChange = { contactPhone = it },
                            placeholder = { Text("Phone (+91 ...)") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = SaharaColors.PureWhite,
                                unfocusedContainerColor = SaharaColors.PureWhite,
                                focusedBorderColor = SaharaColors.PinkPrimary,
                                unfocusedBorderColor = SaharaColors.BorderSubtle
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

            // What they'll receive section
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = SaharaColors.VerySoftBlue)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "What they'll receive",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = SaharaColors.BlueMuted
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("• Quiet safety alert if an incident is triggered", style = MaterialTheme.typography.bodySmall)
                    Text("• Approximate incident location (never continuous)", style = MaterialTheme.typography.bodySmall)
                    Text("• Simple status updates & 1-tap acknowledgement", style = MaterialTheme.typography.bodySmall)
                }
            }

            // Privacy Assurance Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SaharaColors.SurfaceSubtle)
                    .padding(14.dp)
            ) {
                Text(
                    text = "🔒 Your location isn't continuously shared with anyone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SaharaColors.TextSecondary,
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
            .background(SaharaColors.WarmWhite)
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        Text(
            text = "← Back",
            style = MaterialTheme.typography.bodyMedium,
            color = SaharaColors.TextSecondary,
            modifier = Modifier
                .clickable { onBack() }
                .padding(vertical = 8.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        SaharaSectionHeader(
            title = "Set up safety\naround your life.",
            subtitle = "Start with what feels right. You can change everything later."
        )

        Spacer(modifier = Modifier.height(20.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SaharaToggleCard(
                title = "Always-on Safety Agent",
                description = "Watch for possible signs of distress using on-device processing.",
                checked = alwaysOnAgent,
                onCheckedChange = { alwaysOnAgent = it }
            )

            SaharaToggleCard(
                title = "Safety Check-ins",
                description = "Get a reminder to let your people know you're okay.",
                checked = safetyCheckIns,
                onCheckedChange = { safetyCheckIns = it }
            )

            SaharaToggleCard(
                title = "Motion Assist",
                description = "Uses unusual movement as an additional safety signal.",
                checked = motionAssist,
                onCheckedChange = { motionAssist = it }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Current setup summary card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = SaharaColors.VerySoftPink)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Your current setup",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = SaharaColors.PinkDeep
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Safety Agent", style = MaterialTheme.typography.bodySmall)
                        Text(if (alwaysOnAgent) "On" else "Off", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Check-ins", style = MaterialTheme.typography.bodySmall)
                        Text(if (safetyCheckIns) "On" else "Off", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Motion Assist", style = MaterialTheme.typography.bodySmall)
                        Text(if (motionAssist) "On" else "Off", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Notify Circle", style = MaterialTheme.typography.bodySmall)
                        Text("2 people", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        SaharaPrimaryButton(
            text = "You're all set",
            onClick = onFinishSetup
        )
    }
}

// =============================================================================
// SCREEN 5 — HOME DASHBOARD (THE STAR SCREEN)
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
            .background(SaharaColors.WarmWhite)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Good evening",
                        style = MaterialTheme.typography.bodyLarge,
                        color = SaharaColors.TextSecondary
                    )
                    Text(
                        text = "You're not alone.",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = SaharaColors.TextPrimary
                    )
                }

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(SaharaColors.SoftPink)
                        .clickable { onOpenCircle() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "🌸", fontSize = 20.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Your safety companion is ready.",
                style = MaterialTheme.typography.bodyMedium,
                color = SaharaColors.TextSecondary,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Central Calming Visual
            BreathingSafetyVisual(statusText = if (isMonitoringActive) "Safety Agent active" else "Safety Agent on standby")

            Box(modifier = Modifier.clickable { onToggleMonitoring(!isMonitoringActive) }) {
                SaharaStatusBadge(
                    text = if (isMonitoringActive) "● Safety Agent active (Tap to pause)" else "○ Safety Agent standby (Tap to start)",
                    style = if (isMonitoringActive) BadgeStyle.SUCCESS else BadgeStyle.NEUTRAL
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Primary & Secondary Actions
            SaharaHoldToActivateButton(
                text = "I need help",
                subtext = "Press & hold for immediate assistance",
                onHoldComplete = onNeedHelp,
                isDanger = false
            )

            Spacer(modifier = Modifier.height(12.dp))

            SaharaSecondaryButton(
                text = "I feel unsafe  ·  Start Safety Watch",
                onClick = onStartSafetyWatch
            )

            Spacer(modifier = Modifier.height(12.dp))

            SaharaPrimaryButton(
                text = "🛡️ SheGuard Safety Micro-Report",
                onClick = onOpenSheGuardReport
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Compact Status Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CompactStatusItem(
                    title = "Your Circle",
                    status = "2 people · Ready",
                    icon = "👥",
                    modifier = Modifier.weight(1f),
                    onClick = onOpenCircle
                )
                CompactStatusItem(
                    title = "Next Check-in",
                    status = "Tomorrow · 9 PM",
                    icon = "⏰",
                    modifier = Modifier.weight(1f),
                    onClick = {}
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CompactStatusItem(
                    title = "Offline Safety",
                    status = "Mesh Relay Ready",
                    icon = "📡",
                    modifier = Modifier.weight(1f),
                    onClick = onOpenSheGuardReport
                )
                CompactStatusItem(
                    title = "Help Directory",
                    status = "Mumbai 100/1090",
                    icon = "📞",
                    modifier = Modifier.weight(1f),
                    onClick = onOpenDirectory
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Recent Activity
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenRecords() },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SaharaColors.PureWhite)
            ) {
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "🕊️", fontSize = 20.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Recent activity",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (recentIncidentsCount > 0) "$recentIncidentsCount recorded incident(s) sealed locally." else "Everything looks quiet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SaharaColors.TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Companion Quick Tools Row
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
                text = "📊 View Detection Log",
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
            .shadow(2.dp, RoundedCornerShape(18.dp), spotColor = SaharaColors.ShadowTint)
            .clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = SaharaColors.PureWhite)
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
                        .background(SaharaColors.SuccessGreen)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = SaharaColors.TextPrimary
            )
            Text(
                text = status,
                style = MaterialTheme.typography.labelSmall,
                color = SaharaColors.TextSecondary
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
            .clip(RoundedCornerShape(14.dp))
            .background(SaharaColors.SurfaceSubtle)
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = SaharaColors.TextPrimary
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
            .background(SaharaColors.WarmWhite)
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(SaharaColors.SoftPink),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "👁️", fontSize = 36.sp)
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Safety Watch is on",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = SaharaColors.TextPrimary
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "We're quietly paying closer attention while you get where you're going.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = SaharaColors.TextSecondary,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(30.dp))

            // Three status sections
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SaharaSafetyStatusCard(
                    title = "Location",
                    subtitle = "Secured locally, updating silently",
                    stateText = "Ready",
                    stateBadgeStyle = BadgeStyle.SUCCESS,
                    iconLetter = "📍"
                )
                SaharaSafetyStatusCard(
                    title = "Safety Agent",
                    subtitle = "Distress & voice trigger sensitivity increased",
                    stateText = "More attentive",
                    stateBadgeStyle = BadgeStyle.ACTIVE_PINK,
                    iconLetter = "✨"
                )
                SaharaSafetyStatusCard(
                    title = "Notify Circle",
                    subtitle = "Alert queued. Contacts not notified yet.",
                    stateText = "Standing by",
                    stateBadgeStyle = BadgeStyle.INFO,
                    iconLetter = "👥"
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "The phone can remain safely in your pocket.",
                style = MaterialTheme.typography.bodySmall,
                color = SaharaColors.TextSecondary
            )
        }

        // Actions
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SaharaPrimaryButton(
                text = "I'm safe",
                onClick = onImSafe
            )
            SaharaSecondaryButton(
                text = "I need help now",
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
            .background(SaharaColors.WarmWhite)
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.verticalScroll(rememberScrollState())
        ) {
            // Calm Incident Header
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(CircleShape)
                    .background(SaharaColors.SoftPink),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "🛡️", fontSize = 40.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Safety mode is active",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = SaharaColors.TextPrimary
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "We're securing a record and reaching your trusted circle.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = SaharaColors.TextSecondary
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Calm Incident Indicator
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = SaharaColors.VerySoftPink)
            ) {
                Row(
                    modifier = Modifier
                        .padding(18.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(SaharaColors.PinkPrimary)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Recording safely",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = SaharaColors.PinkDeep
                        )
                    }
                    Text(
                        text = formattedTime,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = SaharaColors.PinkDeep
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Taking Action Checklist
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = SaharaColors.PureWhite)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Taking action",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = SaharaColors.TextPrimary
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    ActionRowItem(
                        icon = "✓",
                        title = "Evidence capture started",
                        subtitle = "Pre-event audio & sensor data secured locally",
                        isDone = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    ActionRowItem(
                        icon = "⟳",
                        title = "Reaching your circle",
                        subtitle = "Trying the best available connection (Mesh + SMS)",
                        isDone = false
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Contact delivery states
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = SaharaColors.SurfaceSubtle)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Aisha", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                Text("Alert delivered ✓", color = SaharaColors.SuccessGreen, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Sara", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                Text("Connecting... ⟳", color = SaharaColors.BlueMuted, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Location Status
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "📍", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Incident location secured",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Last updated just now",
                                style = MaterialTheme.typography.labelSmall,
                                color = SaharaColors.TextSecondary
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Hold to end
        SaharaHoldToActivateButton(
            text = "I'm safe",
            subtext = "Press and hold to end",
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
                .background(if (isDone) SaharaColors.SuccessGreenBg else SaharaColors.VerySoftBlue),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = icon,
                color = if (isDone) SaharaColors.SuccessGreen else SaharaColors.BlueMuted,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = SaharaColors.TextPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = SaharaColors.TextSecondary
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
            .background(SaharaColors.WarmWhite)
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            // Vault-Sealing Soft Visual
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(SaharaColors.VerySoftPink, SaharaColors.VerySoftBlue)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(SaharaColors.PureWhite)
                        .shadow(4.dp, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "🔒", fontSize = 36.sp)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "You're safe now.",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = SaharaColors.TextPrimary
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Your incident record has been secured on this device.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = SaharaColors.TextSecondary
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Integrity Seal Badge
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = SaharaColors.SuccessGreenBg)
            ) {
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "✓", color = SaharaColors.SuccessGreen, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Record secured",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = SaharaColors.SuccessGreen
                        )
                        Text(
                            text = "Protected against unnoticed changes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SaharaColors.TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Incident Summary
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = SaharaColors.PureWhite)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Incident summary",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = SaharaColors.TextPrimary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    SummaryRow("Time", "Today at 8:41 PM")
                    SummaryRow("Duration", "3m 42s")
                    SummaryRow("Evidence captured", "Encrypted audio + Sensor data")
                    SummaryRow("Location recorded", "Bandra West (Captured)")
                    SummaryRow("Circle notified", "2 contacts alerted")
                }
            }
        }

        // Actions
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SaharaPrimaryButton(
                text = "View incident record",
                onClick = onViewRecord
            )
            SaharaSecondaryButton(
                text = "Share update with my circle",
                onClick = onShareCircle
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onReturnHome() }
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Return to Home Dashboard",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = SaharaColors.TextSecondary
                )
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = SaharaColors.TextSecondary)
        Text(text = value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = SaharaColors.TextPrimary)
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
            .background(SaharaColors.WarmWhite)
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        Text(
            text = "← Back",
            style = MaterialTheme.typography.bodyMedium,
            color = SaharaColors.TextSecondary,
            modifier = Modifier
                .clickable { onBack() }
                .padding(vertical = 8.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        SaharaSectionHeader(
            title = "Incident Record",
            subtitle = "Complete chronological event timeline and integrity details."
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Integrity Verification Status Banner
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SaharaColors.SuccessGreenBg)
        ) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "🛡️", fontSize = 24.sp)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Record integrity verified",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = SaharaColors.SuccessGreen
                    )
                    Text(
                        text = "Sealed with cryptographic Merkle proof on device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SaharaColors.TextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Vertical Timeline
        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = SaharaColors.PureWhite)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                SaharaTimelineItem(time = "8:32 PM", title = "Safety Watch started", subtitle = "Proactive attentive monitoring active")
                SaharaTimelineItem(time = "8:41 PM", title = "Distress signals detected", subtitle = "Voice keyword trigger confirmed on-device")
                SaharaTimelineItem(time = "8:41 PM", title = "Incident mode activated", subtitle = "Autonomous safety escalation engaged")
                SaharaTimelineItem(time = "8:41 PM", title = "Pre-event evidence secured", subtitle = "Rolling buffer sealed into encrypted storage")
                SaharaTimelineItem(time = "8:42 PM", title = "Notify Circle alert sent", subtitle = "SMS delivery confirmation to Aisha & Sara")
                SaharaTimelineItem(time = "8:42 PM", title = "Nearby relay attempted", subtitle = "Offline peer mesh discovery active")
                SaharaTimelineItem(time = "8:45 PM", title = "Incident ended", subtitle = "User confirmed safety via verified pass-hold")
                SaharaTimelineItem(time = "8:45 PM", title = "Record sealed ✓", subtitle = "Merkle root computed & signed in Keystore", isLast = true, isVerified = true)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        SaharaPrimaryButton(
            text = "Export verified record",
            onClick = onExportVerified
        )
    }
}

// =============================================================================
// SCREEN 10 — TRUSTED CONTACT INCIDENT ALERT (RECEIVER PERSPECTIVE)
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
            .background(SaharaColors.WarmWhite)
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        Text(
            text = "← Back",
            style = MaterialTheme.typography.bodyMedium,
            color = SaharaColors.TextSecondary,
            modifier = Modifier
                .clickable { onBack() }
                .padding(vertical = 8.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        SaharaSectionHeader(
            title = "Someone in your circle\nmay need help.",
            subtitle = "You are listed as a trusted emergency contact."
        )

        Spacer(modifier = Modifier.height(16.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Protected Person Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = SaharaColors.PureWhite)
            ) {
                Row(
                    modifier = Modifier
                        .padding(18.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(SaharaColors.SoftPink),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "M", color = SaharaColors.PinkPrimary, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(text = "Maya", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        SaharaStatusBadge(text = "Safety mode activated", style = BadgeStyle.ACTIVE_PINK)
                    }
                }
            }

            // Incident Details Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = SaharaColors.SurfaceSubtle)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Incident time", style = MaterialTheme.typography.bodySmall, color = SaharaColors.TextSecondary)
                        Text("Today at 8:41 PM", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Last location", style = MaterialTheme.typography.bodySmall, color = SaharaColors.TextSecondary)
                        Text("Bandra West (Approx)", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Alert Delivery", style = MaterialTheme.typography.bodySmall, color = SaharaColors.TextSecondary)
                        Text("Direct SMS + Mesh ✓", style = MaterialTheme.typography.bodySmall, color = SaharaColors.SuccessGreen, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Status Step List
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = SaharaColors.PureWhite)
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(text = "Status updates", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("✓ Safety event started", style = MaterialTheme.typography.bodySmall)
                    Text("✓ Alert delivered to you", style = MaterialTheme.typography.bodySmall)
                    Text("✓ Evidence is being secured locally", style = MaterialTheme.typography.bodySmall)
                    Text("✓ Location captured", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Action Buttons for Contact
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SaharaPrimaryButton(
                text = "Check in on Maya (Acknowledge)",
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
                    text = "Get directions",
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
            .background(SaharaColors.WarmWhite)
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        Text(
            text = "← Back",
            style = MaterialTheme.typography.bodyMedium,
            color = SaharaColors.TextSecondary,
            modifier = Modifier
                .clickable { onBack() }
                .padding(vertical = 8.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        SaharaSectionHeader(
            title = "Your Circle",
            subtitle = "Your circle receives updates only during configured safety events."
        )

        Spacer(modifier = Modifier.height(20.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            contacts.forEach { contact ->
                SaharaContactCard(
                    name = contact.displayName,
                    relation = contact.phoneNumber ?: "Direct Contact",
                    status = "Ready",
                    avatarColor = SaharaColors.SoftPink,
                    onRemove = { onRemoveContact(contact) }
                )
            }

            if (contacts.size < 3) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = SaharaColors.SurfaceSubtle)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "+ Add trusted contact (${contacts.size}/3)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = SaharaColors.PinkPrimary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it },
                            placeholder = { Text("Contact Name") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = newPhone,
                            onValueChange = { newPhone = it },
                            placeholder = { Text("Phone Number") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(12.dp))
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

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SaharaColors.VerySoftBlue)
                    .padding(14.dp)
            ) {
                Text(
                    text = "🛡️ Contacts are notified only when an incident is triggered or during proactive Safety Watch.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SaharaColors.BlueMuted
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            SaharaSecondaryButton(
                text = if (isSyncing) "Syncing..." else "Sync Circle to Backend 🔄",
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
                    color = if (syncStatus!!.contains("Successfully")) SaharaColors.SuccessGreen else SaharaColors.DangerCoral,
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
            .background(SaharaColors.WarmWhite)
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        Text(
            text = "← Back",
            style = MaterialTheme.typography.bodyMedium,
            color = SaharaColors.TextSecondary,
            modifier = Modifier
                .clickable { onBack() }
                .padding(vertical = 8.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        SaharaSectionHeader(
            title = "Offline Help Directory",
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
                        .clickable {
                            try {
                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${contact.phone}"))
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = SaharaColors.PureWhite)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = contact.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            SaharaStatusBadge(text = "📞 ${contact.phone}", style = BadgeStyle.INFO)
                        }
                        Text(text = "City: ${contact.city} · Tap to call", style = MaterialTheme.typography.bodySmall, color = SaharaColors.TextSecondary)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = contact.description, style = MaterialTheme.typography.bodySmall)
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
            .background(SaharaColors.WarmWhite)
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        Text(
            text = "← Back",
            style = MaterialTheme.typography.bodyMedium,
            color = SaharaColors.TextSecondary,
            modifier = Modifier
                .clickable { onBack() }
                .padding(vertical = 8.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        SaharaSectionHeader(
            title = "Evidence & Verifier",
            subtitle = "Verify cryptographic integrity proof and export sealed incident packages."
        )

        Spacer(modifier = Modifier.height(20.dp))

        if (exportPackage != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (exportPackage.isIntegrityVerified) SaharaColors.SuccessGreenBg else SaharaColors.DangerCoralBg
                )
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = if (exportPackage.isIntegrityVerified) "INTEGRITY VERIFIED ✓" else "VERIFICATION WARNING",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (exportPackage.isIntegrityVerified) SaharaColors.SuccessGreen else SaharaColors.DangerCoral
                    )
                    exportPackage.warningDisclaimer?.let {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = it, color = SaharaColors.DangerCoral, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = exportPackage.summaryText, style = MaterialTheme.typography.bodySmall, color = SaharaColors.TextPrimary)
                }
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = SaharaColors.PureWhite)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Ready to verify recent incident",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Validates Keystore digital signatures, SHA-256 evidence hashes, and Merkle tree root.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SaharaColors.TextSecondary
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
            .background(SaharaColors.WarmWhite)
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        Text(
            text = "← Back",
            style = MaterialTheme.typography.bodyMedium,
            color = SaharaColors.TextSecondary,
            modifier = Modifier
                .clickable { onBack() }
                .padding(vertical = 8.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        SaharaSectionHeader(
            title = "AI Legal FIR Drafter",
            subtitle = "Prepares structured complaint drafts for human review."
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = SaharaColors.PureWhite)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text("Incident Context:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = incidentSummary,
                    onValueChange = { incidentSummary = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = victimName,
                    onValueChange = { victimName = it },
                    placeholder = { Text("Victim Name") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = locationText,
                    onValueChange = { locationText = it },
                    placeholder = { Text("Location") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(12.dp))
                SaharaPrimaryButton(
                    text = if (isLoading) "Generating AI Draft..." else "Generate FIR Draft",
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

        Spacer(modifier = Modifier.height(16.dp))

        if (generatedDraft != null) {
            Card(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = SaharaColors.VerySoftYellow)
            ) {
                Column(
                    modifier = Modifier
                        .padding(18.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "LEGAL AGENT OUTPUT:",
                        fontWeight = FontWeight.Bold,
                        color = SaharaColors.YellowDark
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = generatedDraft!!, fontSize = 12.sp, lineHeight = 18.sp)
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
            .background(SaharaColors.WarmWhite)
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        Text(
            text = "← Back",
            style = MaterialTheme.typography.bodyMedium,
            color = SaharaColors.TextSecondary,
            modifier = Modifier
                .clickable { onBack() }
                .padding(vertical = 8.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        SaharaSectionHeader(
            title = "Merkle Root Anchoring",
            subtitle = "Optional remote blockchain anchoring for immutable timestamping."
        )

        Spacer(modifier = Modifier.height(20.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = SaharaColors.PureWhite)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text("Sealed Merkle Root:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = merkleRootInput,
                    onValueChange = { merkleRootInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(14.dp))
                SaharaPrimaryButton(
                    text = if (isLoading) "Anchoring..." else "Anchor Merkle Root",
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
                Spacer(modifier = Modifier.height(14.dp))
                Text("Status: $anchorStatus", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                if (txHash != null) {
                    Text("Tx Hash: $txHash", style = MaterialTheme.typography.bodySmall, color = SaharaColors.SuccessGreen)
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
            .background(SaharaColors.WarmWhite)
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        Text(
            text = "← Back",
            style = MaterialTheme.typography.bodyMedium,
            color = SaharaColors.TextSecondary,
            modifier = Modifier
                .clickable { onBack() }
                .padding(vertical = 8.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        SaharaSectionHeader(
            title = "Phone Authentication",
            subtitle = "Sign in to sync your Notify Circle and backup preferences."
        )

        Spacer(modifier = Modifier.height(20.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = SaharaColors.PureWhite)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text("Phone Number:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = { phoneNumber = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true
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
                            color = SaharaColors.PinkPrimary
                        )
                    }
                }

                if (isOtpSent) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Enter 6-Digit OTP:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = otpCode,
                        onValueChange = { otpCode = it },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true
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

                Spacer(modifier = Modifier.height(14.dp))
                Text("Status: $authStatus", style = MaterialTheme.typography.bodySmall, color = SaharaColors.TextSecondary)
            }
        }
    }
}
