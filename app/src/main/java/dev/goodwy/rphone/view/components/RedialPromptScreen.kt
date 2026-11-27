package dev.goodwy.rphone.view.components

import android.os.Build
import android.telecom.PhoneAccountHandle
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.automirrored.rounded.PhoneCallback
import androidx.compose.material.icons.automirrored.rounded.PhoneMissed
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.forceLtr
import dev.goodwy.rphone.view.theme.customColors
import kotlinx.coroutines.delay
import kotlin.math.ceil

data class RedialTarget(
    val name: String,
    val number: String,
    val dialNumber: String = number,
    val photoUri: String? = null,
    val backgroundUri: String? = null,
    val phoneAccountHandle: PhoneAccountHandle? = null,
    val reason: String = ""
)

@Composable
fun RedialPromptScreen(
    target: RedialTarget,
    autoCallEnabled: Boolean = false,
    totalDurationSeconds: Int = 10,
    onRedial: (RedialTarget) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    fun triggerHaptic(feedback: Int = HapticFeedbackConstants.KEYBOARD_TAP) {
        try {
            view.performHapticFeedback(feedback)
        } catch (_: Exception) {}
    }

    var isClosing by remember { mutableStateOf(false) }
    var isTriggered by remember(target) { mutableStateOf(false) }

    fun safeDismiss() {
        if (!isTriggered) {
            isTriggered = true
            isClosing = true
            triggerHaptic()
            onDismiss()
        }
    }

    fun safeRedial() {
        if (!isTriggered) {
            isTriggered = true
            isClosing = true
            triggerHaptic(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM
                else HapticFeedbackConstants.LONG_PRESS
            )
            onRedial(target)
        }
    }

    BackHandler { safeDismiss() }

    val totalMillis = totalDurationSeconds * 1000L
    var remainingMillis by remember(target) { mutableLongStateOf(totalMillis) }
    var continueAutomatically by remember(target) { mutableStateOf(autoCallEnabled) }

    LaunchedEffect(continueAutomatically, target) {
        remainingMillis = totalMillis
        if (isTriggered) return@LaunchedEffect
        val interval = 50L
        while (remainingMillis > 0 && !isTriggered) {
            delay(interval)
            remainingMillis = (remainingMillis - interval).coerceAtLeast(0L)
        }
        if (!isTriggered) {
            if (continueAutomatically) safeRedial() else safeDismiss()
        }
    }

    val remainingSeconds = ceil(remainingMillis / 1000f).toInt().coerceAtLeast(0)
    val progressFraction = (remainingMillis.toFloat() / totalMillis.toFloat()).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = progressFraction,
        animationSpec = tween(durationMillis = 60, easing = FastOutSlowInEasing),
        label = "redialProgress"
    )

    val isBusyOrRejected = remember(target.reason) {
        target.reason.contains("مشغول", ignoreCase = true) ||
                target.reason.contains("busy", ignoreCase = true) ||
                target.reason.contains("رفض", ignoreCase = true) ||
                target.reason.contains("rejected", ignoreCase = true)
    }

    val isMaskedNumber = remember(target.number) {
        target.number.contains("•")
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .padding(horizontal = 24.dp, vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = !isClosing,
            enter = fadeIn(tween(260)) + scaleIn(
                initialScale = 0.88f,
                animationSpec = spring(dampingRatio = 0.75f, stiffness = 380f)
            ),
            exit = fadeOut(tween(180)) + scaleOut(targetScale = 0.92f, animationSpec = tween(180))
        ) {
            Surface(
                shape = RoundedCornerShape(32.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                tonalElevation = 6.dp,
                shadowElevation = 20.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 400.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Reason Pill Badge
                    val badgeBg = if (isBusyOrRejected) {
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f)
                    } else {
                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.9f)
                    }
                    val badgeContentColor = if (isBusyOrRejected) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    }
                    val badgeIcon = if (isBusyOrRejected) Icons.AutoMirrored.Rounded.PhoneMissed else Icons.AutoMirrored.Rounded.PhoneCallback

                    Surface(
                        shape = CircleShape,
                        color = badgeBg
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = badgeIcon,
                                contentDescription = null,
                                tint = badgeContentColor,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = target.reason.ifEmpty { stringResource(R.string.call_status_unanswered) },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = badgeContentColor
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Contact Avatar
                    RillAvatar(
                        name = target.name,
                        photoUri = target.photoUri,
                        modifier = Modifier.size(86.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Contact Name
                    Text(
                        text = target.name.ifEmpty { target.number.forceLtr() },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Phone Number / Masked Number
                    if (target.name.isNotEmpty() && target.name != target.number) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            if (isMaskedNumber) {
                                Icon(
                                    imageVector = Icons.Rounded.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(
                                text = target.number.forceLtr(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(22.dp))

                    // ── Auto-Redial Interactive Control Card ───────────────────────
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .clickable {
                                continueAutomatically = !continueAutomatically
                                triggerHaptic()
                            }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (continueAutomatically) MaterialTheme.colorScheme.primaryContainer
                                            else MaterialTheme.colorScheme.surfaceContainerHighest,
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = if (continueAutomatically) Icons.Rounded.Autorenew else Icons.Rounded.Timer,
                                            contentDescription = null,
                                            tint = if (continueAutomatically) MaterialTheme.colorScheme.primary
                                                   else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                Spacer(Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.redial_auto_call),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = stringResource(
                                            if (continueAutomatically) R.string.redialing_in
                                            else R.string.redial_prompt_closes_in,
                                            remainingSeconds
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (continueAutomatically) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Switch(
                                    checked = continueAutomatically,
                                    onCheckedChange = {
                                        continueAutomatically = it
                                        triggerHaptic()
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                                        checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                                        uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Smooth Animated Progress Bar
                            val progressTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                            val progressColor by animateColorAsState(
                                targetValue = if (continueAutomatically) MaterialTheme.colorScheme.primary
                                              else MaterialTheme.colorScheme.outlineVariant,
                                label = "progressColor"
                            )

                            LinearProgressIndicator(
                                progress = { animatedProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = progressColor,
                                trackColor = progressTrackColor
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(26.dp))

                    // ── Action Buttons ──────────────────────────────────────────────
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Cancel button
                        FilledTonalButton(
                            onClick = { safeDismiss() },
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.cancel),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Call Now button
                        Button(
                            onClick = { safeRedial() },
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.customColors.colorDarkGreen,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Call,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.call_now),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
