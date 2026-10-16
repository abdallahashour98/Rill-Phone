package dev.goodwy.rphone.view.components

import android.net.Uri
import android.telecom.PhoneAccountHandle
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PhoneCallback
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.R
import dev.goodwy.rphone.view.theme.customColors
import kotlinx.coroutines.delay
import kotlin.math.ceil

data class RedialTarget(
    val name: String,
    val number: String,
    val photoUri: String? = null,
    val backgroundUri: String? = null,
    val phoneAccountHandle: PhoneAccountHandle? = null,
    val reason: String = ""
)

@Composable
fun RedialPromptScreen(
    target: RedialTarget,
    autoCallEnabled: Boolean = true,
    totalDurationSeconds: Int = 5,
    onRedial: (RedialTarget) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onDismiss() }

    val totalMillis = totalDurationSeconds * 1000L
    var remainingMillis by remember { mutableLongStateOf(totalMillis) }
    var isTriggered by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val interval = 50L
        while (remainingMillis > 0 && !isTriggered) {
            delay(interval)
            remainingMillis = (remainingMillis - interval).coerceAtLeast(0L)
        }
        if (!isTriggered) {
            isTriggered = true
            if (autoCallEnabled) {
                onRedial(target)
            } else {
                onDismiss()
            }
        }
    }

    val remainingSeconds = ceil(remainingMillis / 1000f).toInt().coerceAtLeast(0)
    val progressFraction = (remainingMillis.toFloat() / totalMillis.toFloat()).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = progressFraction,
        animationSpec = tween(durationMillis = 60, easing = FastOutSlowInEasing),
        label = "redialProgress"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(tween(280)) + scaleIn(initialScale = 0.92f, animationSpec = tween(280)),
            exit = fadeOut(tween(200)) + scaleOut(targetScale = 0.95f, animationSpec = tween(200))
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                shadowElevation = 12.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Reason pill chip
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PhoneCallback,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = target.reason.ifEmpty { stringResource(R.string.call_status_unanswered) },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Contact Avatar
                    RillAvatar(
                        name = target.name,
                        photoUri = target.photoUri,
                        modifier = Modifier.size(84.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Contact Name
                    Text(
                        text = target.name.ifEmpty { target.number },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Phone Number (if name differs)
                    if (target.name.isNotEmpty() && target.name != target.number) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = target.number,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Linear countdown progress bar
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Countdown text
                    Text(
                        text = stringResource(R.string.redialing_in, remainingSeconds),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Cancel / Decline button
                        FilledTonalButton(
                            onClick = {
                                if (!isTriggered) {
                                    isTriggered = true
                                    onDismiss()
                                }
                            },
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.cancel), fontWeight = FontWeight.SemiBold)
                        }

                        // Call Now button
                        Button(
                            onClick = {
                                if (!isTriggered) {
                                    isTriggered = true
                                    onRedial(target)
                                }
                            },
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.customColors.colorDarkGreen,
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .weight(1.3f)
                                .height(52.dp)
                        ) {
                            Icon(Icons.Rounded.Call, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.call_now), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
