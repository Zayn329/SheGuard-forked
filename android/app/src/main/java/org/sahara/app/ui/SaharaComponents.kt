package org.sahara.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// =============================================================================
// PRIMARY & SECONDARY BUTTONS (Pink & White)
// =============================================================================

@Composable
fun SaharaPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isDanger: Boolean = false
) {
    val haptic = LocalHapticFeedback.current
    val containerGradient = if (isDanger) {
        Brush.horizontalGradient(listOf(Color(0xFFF43F5E), Color(0xFFE11D48)))
    } else {
        Brush.horizontalGradient(listOf(Color(0xFFFB7185), Color(0xFFE11D48)))
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .shadow(
                elevation = if (enabled) 5.dp else 0.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor = if (isDanger) SheGuardColors.RoseDanger else SheGuardColors.Primary,
                ambientColor = SheGuardColors.ShadowTint
            )
            .clip(RoundedCornerShape(16.dp))
            .background(if (enabled) containerGradient else Brush.linearGradient(listOf(SheGuardColors.BorderSubtle, SheGuardColors.BorderSubtle)))
            .clickable(enabled = enabled) {
                try { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) } catch (_: Throwable) {}
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = if (enabled) Color.White else SheGuardColors.TextMuted
        )
    }
}

@Composable
fun SaharaSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(16.dp),
        border = ButtonDefaults.outlinedButtonBorder.copy(
            brush = Brush.horizontalGradient(
                listOf(SheGuardColors.BorderSubtle, SheGuardColors.BorderSubtle)
            )
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = SheGuardColors.SurfaceCard,
            contentColor = SheGuardColors.TextPrimary
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp
        )
    }
}

// =============================================================================
// STATUS BADGES & PILLS
// =============================================================================

enum class BadgeStyle {
    SUCCESS,
    INFO,
    WARNING,
    NEUTRAL,
    ACTIVE_PINK
}

@Composable
fun SaharaStatusBadge(
    text: String,
    style: BadgeStyle = BadgeStyle.NEUTRAL
) {
    val (bgColor, textColor, borderColor) = when (style) {
        BadgeStyle.SUCCESS -> Triple(SheGuardColors.EmeraldBg, SheGuardColors.EmeraldText, SheGuardColors.EmeraldBorder)
        BadgeStyle.INFO -> Triple(SheGuardColors.CyanContainer, SheGuardColors.CyanAccent, SheGuardColors.CyanAccent.copy(alpha = 0.3f))
        BadgeStyle.WARNING -> Triple(SheGuardColors.AmberBg, SheGuardColors.AmberText, SheGuardColors.AmberBorder)
        BadgeStyle.ACTIVE_PINK -> Triple(SheGuardColors.PrimaryContainer, SheGuardColors.Primary, SheGuardColors.BorderHighlight)
        BadgeStyle.NEUTRAL -> Triple(SheGuardColors.SurfaceElevated, SheGuardColors.TextSecondary, SheGuardColors.BorderSubtle)
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            color = textColor
        )
    }
}

// =============================================================================
// TOGGLE CARD (Preference Items)
// =============================================================================

@Composable
fun SaharaToggleCard(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = SheGuardColors.Primary
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, SheGuardColors.BorderSubtle, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SheGuardColors.SurfaceCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(18.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = SheGuardColors.TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = SheGuardColors.TextSecondary
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = accentColor,
                    uncheckedThumbColor = SheGuardColors.TextMuted,
                    uncheckedTrackColor = SheGuardColors.SurfaceElevated,
                    uncheckedBorderColor = SheGuardColors.BorderSubtle
                )
            )
        }
    }
}

// =============================================================================
// SAFETY STATUS CARD (Dashboard & Status)
// =============================================================================

@Composable
fun SaharaSafetyStatusCard(
    title: String,
    subtitle: String,
    stateText: String,
    modifier: Modifier = Modifier,
    stateBadgeStyle: BadgeStyle = BadgeStyle.SUCCESS,
    containerColor: Color = SheGuardColors.SurfaceCard,
    iconLetter: String = "✓"
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, SheGuardColors.BorderSubtle, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(SheGuardColors.PrimaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = iconLetter,
                        color = SheGuardColors.Primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = SheGuardColors.TextPrimary
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = SheGuardColors.TextSecondary
                    )
                }
            }
            SaharaStatusBadge(text = stateText, style = stateBadgeStyle)
        }
    }
}

// =============================================================================
// CONTACT CARD
// =============================================================================

@Composable
fun SaharaContactCard(
    name: String,
    relation: String,
    status: String,
    modifier: Modifier = Modifier,
    avatarColor: Color = SheGuardColors.PrimaryContainer,
    onRemove: (() -> Unit)? = null
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, SheGuardColors.BorderSubtle, RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = SheGuardColors.SurfaceCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(avatarColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = name.take(1).uppercase(),
                        color = SheGuardColors.Primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = SheGuardColors.TextPrimary
                    )
                    Text(
                        text = relation,
                        style = MaterialTheme.typography.bodySmall,
                        color = SheGuardColors.TextSecondary
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                SaharaStatusBadge(text = status, style = BadgeStyle.SUCCESS)
                if (onRemove != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "✕",
                        color = SheGuardColors.TextMuted,
                        fontSize = 15.sp,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { onRemove() }
                            .padding(6.dp)
                    )
                }
            }
        }
    }
}

// =============================================================================
// TIMELINE ITEM
// =============================================================================

@Composable
fun SaharaTimelineItem(
    time: String,
    title: String,
    subtitle: String? = null,
    isLast: Boolean = false,
    isVerified: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
    ) {
        // Time Column
        Text(
            text = time,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = SheGuardColors.Primary,
            modifier = Modifier.width(60.dp)
        )

        // Indicator Line Column
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(if (isVerified) SheGuardColors.EmeraldSuccess else SheGuardColors.Primary),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                )
            }
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(34.dp)
                        .background(SheGuardColors.BorderSubtle)
                )
            }
        }

        // Event Details
        Column(modifier = Modifier.weight(1f).padding(bottom = if (isLast) 0.dp else 14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = SheGuardColors.TextPrimary
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = SheGuardColors.TextSecondary
                )
            }
        }
    }
}

// =============================================================================
// SECTION HEADER
// =============================================================================

@Composable
fun SaharaSectionHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = SheGuardColors.TextPrimary
        )
        if (subtitle != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = SheGuardColors.TextSecondary
            )
        }
    }
}

// =============================================================================
// PRESS AND HOLD INTERACTION BUTTON
// =============================================================================

@Composable
fun SaharaHoldToActivateButton(
    text: String,
    subtext: String = "Press & hold to activate",
    onHoldComplete: () -> Unit,
    modifier: Modifier = Modifier,
    isDanger: Boolean = false,
    holdDurationMs: Long = 1800L
) {
    var progress by remember { mutableFloatStateOf(0f) }
    var isHolding by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(isHolding) {
        if (isHolding) {
            try { haptic.performHapticFeedback(HapticFeedbackType.LongPress) } catch (_: Throwable) {}
            val stepTime = 30L
            val totalSteps = holdDurationMs / stepTime
            for (i in 1..totalSteps) {
                delay(stepTime)
                if (!isHolding) {
                    progress = 0f
                    break
                }
                progress = i / totalSteps.toFloat()
            }
            if (progress >= 1f) {
                try { haptic.performHapticFeedback(HapticFeedbackType.LongPress) } catch (_: Throwable) {}
                onHoldComplete()
                progress = 0f
                isHolding = false
            }
        } else {
            progress = 0f
        }
    }

    val baseGradient = if (isDanger) {
        Brush.horizontalGradient(listOf(Color(0xFFEF4444), Color(0xFFDC2626)))
    } else {
        Brush.horizontalGradient(listOf(Color(0xFFFB7185), Color(0xFFE11D48)))
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp)
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(18.dp),
                spotColor = if (isDanger) SheGuardColors.RoseDanger else SheGuardColors.Primary,
                ambientColor = SheGuardColors.ShadowTint
            )
            .clip(RoundedCornerShape(18.dp))
            .background(baseGradient)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isHolding = true
                        tryAwaitRelease()
                        isHolding = false
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Progress Fill
        if (progress > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(60.dp)
                    .align(Alignment.CenterStart)
                    .background(Color(0xFF881337).copy(alpha = 0.35f))
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = if (isHolding) "Keep holding..." else subtext,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.9f)
            )
        }
    }
}

// =============================================================================
// SHEGUARD MODERN PINK & WHITE RADAR / SAFETY VISUAL
// =============================================================================

@Composable
fun BreathingSafetyVisual(
    statusText: String = "SheGuard Active",
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "breathing")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    val alphaPulse by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.40f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Box(
        modifier = modifier
            .size(190.dp)
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        // Outer pulsing pink wave
        Box(
            modifier = Modifier
                .size(165.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(SheGuardColors.PrimaryLight.copy(alpha = alphaPulse))
        )

        // Middle soft blush wave
        Box(
            modifier = Modifier
                .size(125.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(Color(0xFFFFF1F2), Color(0xFFFFE4E8))
                    )
                )
                .border(1.dp, Color(0xFFFDA4AF), CircleShape)
        )

        // Core Shield Center (Pure White Card)
        Box(
            modifier = Modifier
                .size(85.dp)
                .shadow(8.dp, CircleShape, spotColor = SheGuardColors.Primary)
                .clip(CircleShape)
                .background(SheGuardColors.SurfaceCard)
                .border(2.dp, SheGuardColors.PrimaryLight, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "🛡️",
                    fontSize = 24.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "ACTIVE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                    color = SheGuardColors.EmeraldSuccess,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

// =============================================================================
// BOTTOM NAVIGATION (Pink & White)
// =============================================================================

@Composable
fun SaharaBottomNav(
    selectedTab: String,
    onSelectTab: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = listOf(
        Pair("Home", "🏠"),
        Pair("Circle", "👥"),
        Pair("Records", "📋"),
        Pair("Settings", "⚙️")
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, SheGuardColors.BorderSubtle, RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)),
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        colors = CardDefaults.cardColors(containerColor = SheGuardColors.SurfaceCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { (tabName, iconEmoji) ->
                val isSelected = selectedTab == tabName
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onSelectTab(tabName) }
                        .background(if (isSelected) SheGuardColors.PrimaryContainer else Color.Transparent)
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = iconEmoji,
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = tabName,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) SheGuardColors.Primary else SheGuardColors.TextSecondary
                    )
                }
            }
        }
    }
}
