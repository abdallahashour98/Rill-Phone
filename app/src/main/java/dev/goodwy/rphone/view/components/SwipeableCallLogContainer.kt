package dev.goodwy.rphone.view.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.view.theme.customColors
import kotlinx.coroutines.launch

/**
 * Swipeable container for call log items.
 * Supports:
 * - [PreferenceManager.SWIPE_STYLE_SAMSUNG]: Full-width reveal, Samsung One UI animations.
 * - [PreferenceManager.SWIPE_STYLE_CLASSIC]: Original floating pill buttons (Call right, SMS & Delete left).
 * - [PreferenceManager.SWIPE_STYLE_DISABLED]: Plain content without swipe gestures.
 */
@Composable
fun SwipeableCallLogContainer(
    enabled: Boolean,
    style: Int = PreferenceManager.SWIPE_STYLE_SAMSUNG,
    shape: Shape = RoundedCornerShape(16.dp),
    haptics: Boolean,
    hapticsStrength: String,
    hapticsIntensity: Float,
    onSwipeRight: () -> Unit, // Call
    onSwipeLeft: () -> Unit,  // Message
    onDelete: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    if (!enabled || style == PreferenceManager.SWIPE_STYLE_DISABLED) {
        Box(modifier = modifier) {
            content()
        }
        return
    }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }

    // Thresholds
    val singleThreshold = with(LocalDensity.current) { 76.dp.toPx() }
    val maxDrag = with(LocalDensity.current) { 150.dp.toPx() }

    var triggeredRight by remember { mutableStateOf(false) }
    var triggeredLeft by remember { mutableStateOf(false) }
    var triggeredDelete by remember { mutableStateOf(false) }

    // Classic style dimensions
    val width = 48.dp
    val height = width - 12.dp
    val iconSpacing = 16.dp

    // Spring animations
    val callScale by animateFloatAsState(
        targetValue = if (triggeredRight) 1.18f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "callScale"
    )
    val messageScale by animateFloatAsState(
        targetValue = if (triggeredLeft) 1.18f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "messageScale"
    )
    val delTriggerScale by animateFloatAsState(
        targetValue = if (triggeredDelete) 1.2f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "delTriggerScale"
    )

    // Colors
    val callBgColor = if (triggeredRight) Color(0xFF2E7D32) else Color(0xFF388E3C)
    val msgBgColor = if (triggeredLeft) Color(0xFF1565C0) else Color(0xFF1E88E5)

    Box(
        modifier = modifier
            .clip(shape)
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta ->
                    coroutineScope.launch {
                        val newOffset = (offsetX.value + delta * 0.72f).coerceIn(-maxDrag, maxDrag)
                        offsetX.snapTo(newOffset)

                        // Physical right = Call
                        if (newOffset >= singleThreshold && !triggeredRight) {
                            triggeredRight = true
                            if (haptics) {
                                performAppHaptic(context, hapticsStrength, hapticsIntensity)
                            }
                        } else if (newOffset < singleThreshold && triggeredRight) {
                            triggeredRight = false
                        }

                        // Physical left: Delete (if dragged deep in classic mode) or Message
                        val isDeepDelete = style == PreferenceManager.SWIPE_STYLE_CLASSIC && onDelete != null && newOffset <= -singleThreshold * 1.5f
                        if (isDeepDelete && !triggeredDelete) {
                            triggeredDelete = true
                            if (haptics) {
                                performAppHaptic(context, hapticsStrength, hapticsIntensity)
                            }
                        } else if (!isDeepDelete && triggeredDelete) {
                            triggeredDelete = false
                        }

                        if (newOffset <= -singleThreshold && !triggeredLeft) {
                            triggeredLeft = true
                            if (haptics && !isDeepDelete) {
                                performAppHaptic(context, hapticsStrength, hapticsIntensity)
                            }
                        } else if (newOffset > -singleThreshold && triggeredLeft) {
                            triggeredLeft = false
                        }
                    }
                },
                onDragStopped = {
                    coroutineScope.launch {
                        val finalOffset = offsetX.value
                        if (finalOffset >= singleThreshold) {
                            onSwipeRight()
                        } else if (style == PreferenceManager.SWIPE_STYLE_CLASSIC && onDelete != null && finalOffset <= -singleThreshold * 1.5f) {
                            onDelete()
                        } else if (finalOffset <= -singleThreshold) {
                            onSwipeLeft()
                        }

                        offsetX.animateTo(
                            targetValue = 0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        )
                        triggeredRight = false
                        triggeredLeft = false
                        triggeredDelete = false
                    }
                }
            )
    ) {
        val currentOffset = offsetX.value

        if (style == PreferenceManager.SWIPE_STYLE_SAMSUNG) {
            // --- SAMSUNG ONE UI STYLE ---
            if (currentOffset > 0f) {
                val progress = (currentOffset / singleThreshold).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(callBgColor),
                    contentAlignment = Alignment.CenterStart
                ) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Row(
                            modifier = Modifier
                                .padding(start = 20.dp)
                                .graphicsLayer {
                                    scaleX = callScale
                                    scaleY = callScale
                                    alpha = (0.5f + progress * 0.5f).coerceIn(0f, 1f)
                                },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = Color.White.copy(alpha = 0.22f),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.Call,
                                        contentDescription = stringResource(R.string.call),
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Text(
                                text = stringResource(R.string.call),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
            } else if (currentOffset < 0f) {
                val progress = (-currentOffset / singleThreshold).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(msgBgColor),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Row(
                            modifier = Modifier
                                .padding(end = 20.dp)
                                .graphicsLayer {
                                    scaleX = messageScale
                                    scaleY = messageScale
                                    alpha = (0.5f + progress * 0.5f).coerceIn(0f, 1f)
                                },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.message),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = Color.White.copy(alpha = 0.22f),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = ImageVector.vectorResource(id = R.drawable.ic_message_filled),
                                        contentDescription = stringResource(R.string.message),
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // --- CLASSIC DEFAULT STYLE (Floating Action Pills) ---
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(
                    modifier = Modifier.matchParentSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentOffset > 0) {
                        val rightProgress = (currentOffset / singleThreshold).coerceIn(0f, 1f)
                        Surface(
                            modifier = Modifier
                                .padding(start = 16.dp)
                                .size(width, height)
                                .graphicsLayer {
                                    val s = (0.5f + rightProgress * 0.5f) * callScale
                                    scaleX = s
                                    scaleY = s
                                    alpha = rightProgress
                                },
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.customColors.colorGreen
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.Call,
                                    contentDescription = stringResource(R.string.call),
                                    tint = MaterialTheme.colorScheme.customColors.colorDarkGreen,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    if (currentOffset < 0) {
                        val leftProgress = (-currentOffset / singleThreshold).coerceIn(0f, 1f)
                        if (onDelete != null) {
                            Surface(
                                modifier = Modifier
                                    .padding(end = iconSpacing)
                                    .size(width, height)
                                    .graphicsLayer {
                                        val s = (0.5f + leftProgress * 0.5f) * delTriggerScale
                                        scaleX = s
                                        scaleY = s
                                        alpha = ((-currentOffset - singleThreshold) / (singleThreshold * 0.5f)).coerceIn(0f, 1f)
                                    },
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.customColors.colorRed
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = ImageVector.vectorResource(id = R.drawable.ic_delete),
                                        contentDescription = stringResource(R.string.delete),
                                        tint = MaterialTheme.colorScheme.customColors.colorDarkRed,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .padding(end = 16.dp)
                                .size(width, height)
                                .graphicsLayer {
                                    val s = (0.5f + leftProgress * 0.5f) * messageScale
                                    scaleX = s
                                    scaleY = s
                                    alpha = leftProgress
                                },
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.customColors.colorBlue
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = ImageVector.vectorResource(id = R.drawable.ic_message_outline),
                                    contentDescription = stringResource(R.string.message),
                                    tint = MaterialTheme.colorScheme.customColors.colorDarkBlue,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Foreground Card Content (hardware-accelerated translation)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    translationX = currentOffset
                }
        ) {
            content()
        }
    }
}