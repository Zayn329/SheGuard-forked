package org.sahara.app.ui

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.content.ContextCompat
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
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.97f else 1f,
        animationSpec = tween(durationMillis = 110),
        label = "primaryButtonScale"
    )
    val containerGradient = if (isDanger) {
        Brush.horizontalGradient(listOf(Color(0xFFF43F5E), Color(0xFFE11D48)))
    } else {
        Brush.horizontalGradient(listOf(Color(0xFFFB7185), Color(0xFFE11D48)))
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .shadow(
                elevation = if (enabled) 5.dp else 0.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor = if (isDanger) SheGuardColors.RoseDanger else SheGuardColors.Primary,
                ambientColor = SheGuardColors.ShadowTint
            )
            .clip(RoundedCornerShape(16.dp))
            .background(if (enabled) containerGradient else Brush.linearGradient(listOf(SheGuardColors.BorderSubtle, SheGuardColors.BorderSubtle)))
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Button
            ) {
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
            textAlign = TextAlign.Center,
            color = if (enabled) Color.White else SheGuardColors.TextMuted,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
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
            .heightIn(min = 52.dp),
        shape = RoundedCornerShape(16.dp),
        border = ButtonDefaults.outlinedButtonBorder.copy(
            brush = Brush.horizontalGradient(
                listOf(SheGuardColors.BorderSubtle, SheGuardColors.BorderSubtle)
            )
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = SheGuardColors.SurfaceCard,
            contentColor = SheGuardColors.TextPrimary
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
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
    style: BadgeStyle = BadgeStyle.NEUTRAL,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor, borderColor) = when (style) {
        BadgeStyle.SUCCESS -> Triple(SheGuardColors.EmeraldBg, SheGuardColors.EmeraldText, SheGuardColors.EmeraldBorder)
        BadgeStyle.INFO -> Triple(SheGuardColors.CyanContainer, SheGuardColors.CyanAccent, SheGuardColors.CyanAccent.copy(alpha = 0.3f))
        BadgeStyle.WARNING -> Triple(SheGuardColors.AmberBg, SheGuardColors.AmberText, SheGuardColors.AmberBorder)
        BadgeStyle.ACTIVE_PINK -> Triple(SheGuardColors.PrimaryContainer, SheGuardColors.Primary, SheGuardColors.BorderHighlight)
        BadgeStyle.NEUTRAL -> Triple(SheGuardColors.SurfaceElevated, SheGuardColors.TextSecondary, SheGuardColors.BorderSubtle)
    }

    Box(
        modifier = modifier
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
            textAlign = TextAlign.Center,
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
            verticalAlignment = Alignment.CenterVertically
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
            Column(modifier = Modifier.weight(1f)) {
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
            Spacer(modifier = Modifier.width(10.dp))
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
    onRemove: (() -> Unit)? = null,
    onCall: (() -> Unit)? = null
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
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(avatarColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = name.take(1).uppercase(),
                    color = SheGuardColors.Primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            }
            Spacer(modifier = Modifier.width(12.dp))

            // Text block takes all remaining width, so long names / numbers truncate instead of overlapping the buttons.
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = SheGuardColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = relation,
                    style = MaterialTheme.typography.bodySmall,
                    color = SheGuardColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(SheGuardColors.EmeraldSuccess)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = status,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = SheGuardColors.EmeraldText,
                        maxLines = 1
                    )
                }
            }

            if (onCall != null) {
                Spacer(modifier = Modifier.width(10.dp))
                SaharaCallButton(onClick = onCall, contentDescription = "Call $name")
            }

            if (onRemove != null) {
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClickLabel = "Remove $name") { onRemove() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "✕",
                        color = SheGuardColors.TextMuted,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}

// =============================================================================
// CALL BUTTON + DIRECT CALL INTENT
// =============================================================================

/** Round green phone-icon button. */
@Composable
fun SaharaCallButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    Box(
        modifier = modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(SheGuardColors.EmeraldBg)
            .border(1.dp, SheGuardColors.EmeraldBorder, CircleShape)
            .clickable(role = Role.Button, onClickLabel = contentDescription) {
                try { haptic.performHapticFeedback(HapticFeedbackType.LongPress) } catch (_: Throwable) {}
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Call,
            contentDescription = contentDescription,
            tint = SheGuardColors.EmeraldSuccess,
            modifier = Modifier.size(22.dp)
        )
    }
}

/** Keeps digits and a leading '+', so "+91 98765-43210" becomes "+919876543210". */
fun sanitizePhoneNumber(raw: String): String {
    val trimmed = raw.trim()
    val digits = trimmed.filter { it.isDigit() }
    return if (trimmed.startsWith("+")) "+$digits" else digits
}

private fun placeCall(context: Context, number: String, direct: Boolean) {
    fun start(action: String): Boolean {
        val intent = Intent(action, Uri.fromParts("tel", number, null))
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (_: SecurityException) {
            false
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    // ACTION_CALL dials immediately. If it is refused (or CALL_PHONE was denied) we fall back to
    // ACTION_DIAL, which opens the dialer with the number already filled in.
    val started = (direct && start(Intent.ACTION_CALL)) || start(Intent.ACTION_DIAL)
    if (!started) {
        Toast.makeText(context, "No phone app available to place the call", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Returns a function that calls a phone number directly.
 * - CALL_PHONE granted  -> ACTION_CALL, the call starts immediately.
 * - Not granted yet     -> asks once, then calls (or opens the dialer if the user declines).
 */
@Composable
fun rememberDirectCaller(): (String) -> Unit {
    val context = LocalContext.current
    var pendingNumber by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val number = pendingNumber
        pendingNumber = null
        if (number != null) placeCall(context, number, direct = granted)
    }

    return remember(context, permissionLauncher) {
        { rawNumber: String ->
            val number = sanitizePhoneNumber(rawNumber)
            if (number.isNotEmpty()) {
                val hasPermission = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.CALL_PHONE
                ) == PackageManager.PERMISSION_GRANTED
                if (hasPermission) {
                    placeCall(context, number, direct = true)
                } else {
                    pendingNumber = number
                    permissionLauncher.launch(Manifest.permission.CALL_PHONE)
                }
            }
        }
    }
}

// =============================================================================
// BACK LINK (comfortable touch target) & TEXT FIELD
// =============================================================================

@Composable
fun SaharaBackLink(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClickLabel = "Go back") { onBack() }
            .padding(horizontal = 6.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "←",
            color = SheGuardColors.Primary,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "Back",
            color = SheGuardColors.Primary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp
        )
    }
}

@Composable
fun SaharaTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onImeDone: (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = SheGuardColors.TextMuted) },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onImeDone?.invoke() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = SheGuardColors.SurfaceElevated,
            unfocusedContainerColor = SheGuardColors.SurfaceElevated,
            focusedBorderColor = SheGuardColors.Primary,
            unfocusedBorderColor = SheGuardColors.BorderSubtle,
            focusedTextColor = SheGuardColors.TextPrimary,
            unfocusedTextColor = SheGuardColors.TextPrimary
        )
    )
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
    // IntrinsicSize.Min lets the connector line stretch to the real text height, so wrapped
    // titles / subtitles never run past the line or collide with the next entry.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(horizontal = 4.dp)
    ) {
        Text(
            text = time,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = SheGuardColors.Primary,
            modifier = Modifier.width(64.dp)
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 8.dp)
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
                        .weight(1f)
                        .background(SheGuardColors.BorderSubtle)
                )
            }
        }

        Column(modifier = Modifier.weight(1f).padding(start = 4.dp, bottom = if (isLast) 0.dp else 16.dp)) {
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
    val latestOnHoldComplete by rememberUpdatedState(onHoldComplete)

    // Frame-driven progress: the fill advances every frame (60/90/120 fps) instead of
    // jumping every 30 ms, so the hold animation looks smooth.
    LaunchedEffect(isHolding) {
        if (isHolding) {
            try { haptic.performHapticFeedback(HapticFeedbackType.LongPress) } catch (_: Throwable) {}
            val totalNanos = holdDurationMs * 1_000_000L
            val startNanos = withFrameNanos { it }
            while (progress < 1f) {
                val now = withFrameNanos { it }
                progress = ((now - startNanos).toFloat() / totalNanos.toFloat()).coerceIn(0f, 1f)
            }
            try { haptic.performHapticFeedback(HapticFeedbackType.LongPress) } catch (_: Throwable) {}
            latestOnHoldComplete()
            progress = 0f
            isHolding = false
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
            .height(64.dp)
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
        if (progress > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .fillMaxHeight()
                    .align(Alignment.CenterStart)
                    .background(Color(0xFF881337).copy(alpha = 0.35f))
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = Color.White
            )
            Text(
                text = if (isHolding) "Keep holding..." else subtext,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
    modifier: Modifier = Modifier,
    isActive: Boolean = true
) {
    // The pulse animation only exists while monitoring is active; in standby it is not running at all.
    val scale: Float
    val alphaPulse: Float
    if (isActive) {
        val infiniteTransition = rememberInfiniteTransition(label = "breathing")
        scale = infiniteTransition.animateFloat(
            initialValue = 0.92f,
            targetValue = 1.08f,
            animationSpec = infiniteRepeatable(
                animation = tween(2400, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "scale"
        ).value
        alphaPulse = infiniteTransition.animateFloat(
            initialValue = 0.15f,
            targetValue = 0.40f,
            animationSpec = infiniteRepeatable(
                animation = tween(2400, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "alpha"
        ).value
    } else {
        scale = 1f
        alphaPulse = 0.10f
    }
    val waveColor = if (isActive) SheGuardColors.PrimaryLight else SheGuardColors.TextSecondary
    val midColors = if (isActive) listOf(Color(0xFFFFF1F2), Color(0xFFFFE4E8)) else listOf(Color(0xFFF3F4F6), Color(0xFFE5E7EB))
    val midBorder = if (isActive) Color(0xFFFDA4AF) else Color(0xFFD1D5DB)
    val coreBorder = if (isActive) SheGuardColors.PrimaryLight else Color(0xFFD1D5DB)
    val coreShadow = if (isActive) SheGuardColors.Primary else Color.Gray

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
                .background(waveColor.copy(alpha = alphaPulse))
        )

        // Middle soft blush wave
        Box(
            modifier = Modifier
                .size(125.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(midColors)
                )
                .border(1.dp, midBorder, CircleShape)
        )

        // Core Shield Center (Pure White Card)
        Box(
            modifier = Modifier
                .size(85.dp)
                .shadow(if (isActive) 8.dp else 2.dp, CircleShape, spotColor = coreShadow)
                .clip(CircleShape)
                .background(SheGuardColors.SurfaceCard)
                .border(2.dp, coreBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "🛡️",
                    fontSize = 24.sp,
                    modifier = Modifier.alpha(if (isActive) 1f else 0.4f)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (isActive) "ACTIVE" else "STANDBY",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                    color = if (isActive) SheGuardColors.EmeraldSuccess else SheGuardColors.TextSecondary,
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
                .padding(vertical = 8.dp, horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { (tabName, iconEmoji) ->
                val isSelected = selectedTab == tabName
                val tabBackground by animateColorAsState(
                    targetValue = if (isSelected) SheGuardColors.PrimaryContainer else Color.Transparent,
                    animationSpec = tween(durationMillis = 200),
                    label = "navTabBackground"
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(tabBackground)
                        .clickable(role = Role.Tab) { onSelectTab(tabName) }
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
                        color = if (isSelected) SheGuardColors.Primary else SheGuardColors.TextSecondary,
                        maxLines = 1
                    )
                }
            }
        }
    }
}