package dev.goodwy.rphone.view.screen

import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.view.HapticFeedbackConstants
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.ui.zIndex
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowRight
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import dev.goodwy.rphone.R
import dev.goodwy.rphone.cardCornerExtraSmall
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.liquidglass.LocalLiquidGlassBackdrop
import dev.goodwy.rphone.liquidglass.drawBackdrop
import dev.goodwy.rphone.liquidglass.drawPlainBackdrop
import dev.goodwy.rphone.liquidglass.effects.blur
import dev.goodwy.rphone.liquidglass.effects.colorControls
import dev.goodwy.rphone.liquidglass.effects.lens
import dev.goodwy.rphone.liquidglass.highlight.Highlight
import dev.goodwy.rphone.liquidglass.shadow.Shadow
import dev.goodwy.rphone.view.components.RillIconBox
import dev.goodwy.rphone.view.screen.onboarding.wavyCircleShape
import dev.goodwy.rphone.view.theme.MyColors.bottomBarColor
import dev.goodwy.rphone.view.theme.MyColors.cardColor
import dev.goodwy.rphone.view.theme.MyColors.dialpadKeyColor
import dev.goodwy.rphone.view.theme.color_call_button
import dev.goodwy.rphone.view.theme.color_call_end
import dev.goodwy.rphone.view.theme.customColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun KeypadButton(
    modifier: Modifier,
    key: Char,
    style: Int = 0,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val cornerRadius by animateDpAsState(
        targetValue = when (style) {
            1 -> 50.dp // Circular
            2 -> 0.dp  // Minimal
            else -> if (isPressed) 16.dp else 32.dp // Modern
        },
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "ButtonShape"
    )

    val containerColor = when (style) {
        2 -> if (isPressed) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else dialpadKeyColor
        else -> dialpadKeyColor
    }

    Surface(
        onClick = onClick,
        modifier = modifier.height(64.dp),
        shape = if (style == 1) CircleShape else RoundedCornerShape(cornerRadius),
        color = containerColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = if (style == 2 && !isPressed) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)) else null,
        interactionSource = interactionSource
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = key.toString(),
                style = if (style == 1) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun PocketModeOverlay(
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.95f))
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            Surface(
                modifier = Modifier.size(80.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.customColors.colorIndigo,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.ScreenLockPortrait,
                        contentDescription = null,
                        modifier = Modifier.size(42.dp),
                        tint = MaterialTheme.colorScheme.customColors.colorDarkIndigo
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.pocket_mode_active),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.pocket_mode_active_description),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(32.dp))
            FilledTonalButton(
                onClick = onDismiss,
                shape = CircleShape
            ) {
                Icon(Icons.Rounded.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.pocket_mode_dismiss))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickResponsesBottomSheet(
    phoneNumber: String,
    contactName: String,
    onDismiss: () -> Unit,
    onSend: (String) -> Unit,
    onOpenSmsApp: () -> Unit,
//    onScheduleReminder: (Long, String) -> Unit
) {
    val prefs = koinInject<PreferenceManager>()
    val responses = remember { prefs.getQuickResponses() }
    var customText by remember { mutableStateOf("") }
    var isCustomVisible by remember { mutableStateOf(false) }

//    val tomorrowMorningMinutes = remember {
//        val now = Calendar.getInstance()
//        val tomorrow = Calendar.getInstance().apply {
//            add(Calendar.DAY_OF_YEAR, 1)
//            set(Calendar.HOUR_OF_DAY, 9)
//            set(Calendar.MINUTE, 0)
//            set(Calendar.SECOND, 0)
//            set(Calendar.MILLISECOND, 0)
//        }
//        val diff = tomorrow.timeInMillis - now.timeInMillis
//        (diff / (1000 * 60)).coerceAtLeast(15)
//    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Drag handle
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(3.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.size(width = 36.dp, height = 4.dp)
                ) {}
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp).padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.quick_response),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.decline_call_and_reply_to, contactName.ifBlank { phoneNumber }),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
//                IconButton(onClick = onOpenSmsApp) {
//                    Icon(
//                        Icons.AutoMirrored.Rounded.OpenInNew,
//                        contentDescription = "Open SMS app",
//                        tint = MaterialTheme.colorScheme.primary
//                    )
//                }
            }

            Spacer(Modifier.height(16.dp))

            responses.forEach { responseText ->
                Surface(
                    onClick = { onSend(responseText) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLowest
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = responseText,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(12.dp))
                        Icon(
                            Icons.AutoMirrored.Rounded.Send,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            if (!isCustomVisible) {
                Button(
                    onClick = { isCustomVisible = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    shape = CircleShape
                ) {
                    Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.write_your_own))
                }
            } else {
                TextField(
                    value = customText,
                    onValueChange = { customText = it },
                    placeholder = { Text(stringResource(R.string.type_message)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = CircleShape,
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent
                    ),
                    trailingIcon = {
                        IconButton(
                            modifier = Modifier.width(72.dp).padding(horizontal = 8.dp),
                            onClick = {
                                if (customText.isNotBlank()) {
                                    onSend(customText.trim())
                                }
                            },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = if (customText.isNotBlank()) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
                            ),
                            shape = CircleShape,
                            enabled = customText.isNotBlank()
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.Send,
                                modifier = Modifier.padding(start = 2.dp),
                                contentDescription = stringResource(R.string.send),
                            )
                        }
                    },
                )
            }

//            Spacer(Modifier.height(16.dp))
//            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
//            Spacer(Modifier.height(12.dp))
//
//            Text(
//                text = "Remind Me to Call Back",
//                style = MaterialTheme.typography.titleMedium,
//                fontWeight = FontWeight.SemiBold
//            )
//            Spacer(Modifier.height(8.dp))
//
//            Row(
//                modifier = Modifier.fillMaxWidth(),
//                horizontalArrangement = Arrangement.spacedBy(8.dp)
//            ) {
//                listOf(
//                    "In 15m" to 15L,
//                    "In 1h" to 60L,
//                    "Tomorrow 9 AM" to tomorrowMorningMinutes
//                ).forEach { (label, minutes) ->
//                    FilledTonalButton(
//                        onClick = { onScheduleReminder(minutes, label) },
//                        modifier = Modifier.weight(1f),
//                        shape = RoundedCornerShape(12.dp),
//                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp)
//                    ) {
//                        Icon(Icons.Rounded.Alarm, contentDescription = null, modifier = Modifier.size(14.dp))
//                        Spacer(Modifier.width(4.dp))
//                        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
//                    }
//                }
//            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PulsingAvatar(photoUri: String?) {
    val prefs = koinInject<PreferenceManager>()
    val settingsState by prefs.settingsChanged.collectAsStateWithLifecycle()
    val avatarShape = remember(settingsState) {
        val shapeVal = prefs.getInt(PreferenceManager.KEY_AVATAR_SHAPE, 1)
        when (shapeVal) {
            0 -> RoundedCornerShape(20.dp)
            1 -> wavyCircleShape(waveAmplitude = 0.024f)
            2 -> RoundedCornerShape(0.dp)
            else -> CircleShape
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "alpha"
    )

    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(230.dp)
                .scale(scale)
                .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = alpha), avatarShape)
        )
        Box(
            modifier = Modifier
                .size(260.dp)
                .scale(scale * 1.1f)
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = alpha * 0.5f),
                    avatarShape
                )
        )

        HeroAvatar(photoUri, avatarSize = 200.dp, wavy = true)
    }
}

@Composable
fun HeroAvatar(photoUri: String?, avatarSize: Dp = 160.dp, wavy: Boolean = false) {
    val prefs = koinInject<PreferenceManager>()
    val settingsState by prefs.settingsChanged.collectAsStateWithLifecycle()
    val avatarShape = remember(settingsState) {
        if (wavy) wavyCircleShape(waveAmplitude = 0.024f)
        else {
            val shapeVal = prefs.getInt(PreferenceManager.KEY_AVATAR_SHAPE, 1)
            when (shapeVal) {
                0 -> RoundedCornerShape(20.dp)
                1 -> CircleShape
                2 -> RoundedCornerShape(0.dp)
                else -> CircleShape
            }
        }
    }
    val avatarFrame = prefs.getBoolean(PreferenceManager.KEY_AVATAR_FRAME, false)
    val borderColor =  MaterialTheme.colorScheme.onSurface

    if (!photoUri.isNullOrEmpty()) {
        Box(
            modifier = Modifier
                .then(
                    if (avatarFrame) Modifier
                        .drawBehind {
                            val borderWidth = size.width * 0.08f // 8% of the width
                            drawOutline(
                                outline = avatarShape.createOutline(size, layoutDirection, this),
                                color = borderColor,
                                style = Stroke(width = borderWidth)
                            )
                        }
                    else Modifier
                )
                .size(avatarSize)
                .clip(avatarShape)
                .background(bottomBarColor),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = photoUri,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(avatarShape),
                contentScale = ContentScale.Crop
            )
        }
    } else {
        Box(
            modifier = Modifier
                .size(avatarSize)
                .clip(avatarShape)
                .background(bottomBarColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.PeopleAlt,
                contentDescription = null,
                modifier = Modifier.size((avatarSize.value * 0.6).dp),
            )
        }
    }
}

@Composable
fun AnimatedCallButton(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    isActive: Boolean = false,
    btnColor: Color = Color.White.copy(0.12f),
    activeBtnColor: Color = Color.White,
    fgColor: Color = Color.White,
    activeFgColor: Color = Color.Black,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val radius by animateDpAsState(if (isActive || isPressed) 20.dp else 42.dp, spring(stiffness = Spring.StiffnessMedium), label = "btnRadius")
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier
                .height(68.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(radius))
                .combinedClickable(
                    interactionSource = interaction,
                    indication = ripple(),
                    onClick = onClick,
                    onLongClick = onLongClick
                ),
            shape = RoundedCornerShape(radius),
            color = if (isActive) activeBtnColor else btnColor
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (isActive) activeFgColor else fgColor,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = MaterialTheme.typography.labelMedium.fontFamily,
            color = fgColor.copy(0.7f),
            modifier = Modifier.padding(top = 8.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun HorizontalSwipeToAnswer(
    useLg: Boolean,
    useBlur: Boolean,
    blurIntensity: Float,
    onAnswer: () -> Unit,
    onDecline: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val localDensity = LocalDensity.current
    val view = LocalView.current
    val globalBackdrop = LocalLiquidGlassBackdrop.current
    val layoutDirection = LocalLayoutDirection.current
    val isRtl = layoutDirection == LayoutDirection.Rtl

    val trackHeight = 96.dp
    val handleWidth = 110.dp
    val handleHeight = 72.dp
    val handleWidthPx = with(localDensity) { handleWidth.toPx() }
    val paddingHandle = with(localDensity) { (trackHeight - handleHeight).toPx() }
    var trackWidthPx by remember { mutableFloatStateOf(0f) }

    val maxDrag by remember(trackWidthPx, handleWidthPx, paddingHandle) {
        derivedStateOf {
            if (trackWidthPx > 0f) (trackWidthPx / 2f) - (handleWidthPx / 2f) - (paddingHandle) + with(localDensity) { 1.dp.toPx() }
            else 0f
        }
    }
    val triggerThreshold = maxDrag * 0.85f

    val dragProgress = remember { derivedStateOf { if (maxDrag > 0f) offsetX.value / maxDrag else 0f } }
    val dragNormal = remember { derivedStateOf { abs(dragProgress.value) } }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")

    val handlePulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "handlePulse"
    )

    val hintAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "hintAlpha"
    )

    val answerGreen = color_call_button
    val declineRed = color_call_end

    val handleBgColor by animateColorAsState(
        targetValue = when {
            dragProgress.value > 0.1f -> answerGreen
            dragProgress.value < -0.1f -> declineRed
            else -> Color.White
        },
        label = "handleColor"
    )

    val iconTint by animateColorAsState(
        targetValue = if (dragNormal.value > 0.1f) Color.White
        else Color.Black,
        label = "iconTint"
    )

    val iconRotation by remember { derivedStateOf { dragProgress.value * 135f } }
    val buttonBgColor = if (useLg || useBlur) bottomBarColor.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant

    Surface(
        shape           = CircleShape,
        color           = buttonBgColor,
        shadowElevation = 0.dp,
        tonalElevation  = 0.dp,
        modifier = Modifier
            .padding(bottom = 36.dp)
            .fillMaxWidth()
            .height(trackHeight)
            .padding(horizontal = 16.dp)
            .onSizeChanged { trackWidthPx = it.width.toFloat() }
            .then(
                if (useLg && globalBackdrop != null) Modifier.drawBackdrop(
                    backdrop = globalBackdrop,
                    shape = { CircleShape },
                    shadow = { Shadow(radius = 8.dp) },
                    effects = {
                        val d = density
                        colorControls(saturation = 1.3f)
                        blur(blurIntensity * d)
                        lens(refractionHeight = 18f * d, refractionAmount = 52f * d)
                    },
                    highlight = { Highlight.Default }
                )
                else if (useBlur && globalBackdrop != null) Modifier.drawPlainBackdrop(
                    backdrop = globalBackdrop,
                    shape    = { CircleShape },
                    shadow = { Shadow(radius = 8.dp) },
                    effects  = { blur(blurIntensity * density) }
                )
                else Modifier
            )
    ) {
        Box {
            Text(
                stringResource(R.string.decline),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 32.dp)
                    .alpha((1f - (dragProgress.value * -2f).coerceIn(0f, 1f)) * hintAlpha),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = declineRed.copy(alpha = 0.8f)
            )

            Text(
                stringResource(R.string.answer),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 32.dp)
                    .alpha((1f - (dragProgress.value * 2f).coerceIn(0f, 1f)) * hintAlpha),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = answerGreen.copy(alpha = 0.8f)
            )

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset { IntOffset((if (isRtl) -offsetX.value else offsetX.value).roundToInt(), 0) }
                    .graphicsLayer {
                        val idleFactor = (1f - dragNormal.value * 5f).coerceIn(0f, 1f)
                        scaleX = 1f + (handlePulseScale - 1f) * idleFactor
                        scaleY = 1f + (handlePulseScale - 1f) * idleFactor
                    }
                    .width(handleWidth)
                    .height(handleHeight)
                    .clip(CircleShape)
                    .background(handleBgColor)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                coroutineScope.launch {
                                    when {
                                        offsetX.value > triggerThreshold -> {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                            } else {
                                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                            }
                                            onAnswer()
                                        }

                                        offsetX.value < -triggerThreshold -> {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                                view.performHapticFeedback(HapticFeedbackConstants.REJECT)
                                            } else {
                                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                            }
                                            onDecline()
                                        }

                                        else -> offsetX.animateTo(
                                            0f,
                                            spring(
                                                dampingRatio = 0.75f,
                                                stiffness = Spring.StiffnessMedium
                                            )
                                        )
                                    }
                                }
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                coroutineScope.launch {
                                    val adjustedDragAmount = if (isRtl) -dragAmount else dragAmount
                                    val newOffset = (offsetX.value + adjustedDragAmount).coerceIn(
                                        -maxDrag * 1.1f,
                                        maxDrag * 1.1f
                                    )
                                    offsetX.snapTo(newOffset)
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                val icon = Icons.Rounded.Call

                Crossfade(
                    targetState = icon,
                    animationSpec = tween(150),
                    label = "icon"
                ) { targetIcon ->
                    Icon(
                        targetIcon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier
                            .size(32.dp)
                            .graphicsLayer {
                                val adjustedIconRotation = if (isRtl) -iconRotation else iconRotation
                                rotationZ = adjustedIconRotation
                            }
                    )
                }
            }
        }
    }
}

@Composable
fun VerticalSwipeToAnswer(
    useLg: Boolean,
    useBlur: Boolean,
    blurIntensity: Float,
    onAnswer: () -> Unit,
    onDecline: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val offsetY = remember { Animatable(0f) }
    val localDensity = LocalDensity.current
    val view = LocalView.current
    val globalBackdrop = LocalLiquidGlassBackdrop.current

    val handleSize = 74.dp
    val trackWidth = 86.dp
    val trackHeight = 300.dp
    val maxDrag = with(localDensity) { 124.dp.toPx() }
    val triggerThreshold = maxDrag * 0.65f

    var hasCrossedThreshold by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseAlpha"
    )

    val arrowBounceUp by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "arrowBounceUp"
    )

    val arrowBounceDown by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "arrowBounceDown"
    )

    val currentOffset = offsetY.value
    val currentDragNormal = remember(currentOffset) {
        (abs(currentOffset) / maxDrag).coerceIn(0f, 1f)
    }

    val isDraggingUp = currentOffset < -10f
    val isDraggingDown = currentOffset > 10f

    val topChevronsAlpha = remember(currentOffset) {
        if (currentOffset <= 0f) {
            (0.4f + currentDragNormal * 0.6f).coerceIn(0f, 1f)
        } else {
            (0.4f - currentDragNormal * 1.5f).coerceIn(0f, 1f)
        }
    }

    val bottomChevronsAlpha = remember(currentOffset) {
        if (currentOffset >= 0f) {
            (0.4f + currentDragNormal * 0.6f).coerceIn(0f, 1f)
        } else {
            (0.4f - currentDragNormal * 1.5f).coerceIn(0f, 1f)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(330.dp)
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        // --- Visual Guide / Runway Track ---
        val trackBgColor = if (useLg || useBlur) {
            bottomBarColor.copy(alpha = 0.22f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
        }

        Surface(
            shape = RoundedCornerShape(44.dp),
            color = trackBgColor,
            shadowElevation = 0.dp,
            tonalElevation = 0.dp,
            modifier = Modifier
                .width(trackWidth)
                .height(trackHeight)
                .then(
                    if (useLg && globalBackdrop != null) Modifier.drawBackdrop(
                        backdrop = globalBackdrop,
                        shape = { RoundedCornerShape(44.dp) },
                        shadow = { Shadow(radius = 6.dp) },
                        effects = {
                            val d = density
                            colorControls(saturation = 1.2f)
                            blur(blurIntensity * d)
                            lens(refractionHeight = 12f * d, refractionAmount = 36f * d)
                        },
                        highlight = { Highlight.Default }
                    )
                    else if (useBlur && globalBackdrop != null) Modifier.drawPlainBackdrop(
                        backdrop = globalBackdrop,
                        shape = { RoundedCornerShape(44.dp) },
                        shadow = { Shadow(radius = 6.dp) },
                        effects = { blur(blurIntensity * density) }
                    )
                    else Modifier
                )
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                // Directional subtle glow inside track runway
                val glowBrush = remember(currentOffset) {
                    when {
                        currentOffset < -15f -> Brush.verticalGradient(
                            colors = listOf(
                                color_call_button.copy(alpha = (currentDragNormal * 0.32f).coerceIn(0f, 0.35f)),
                                Color.Transparent
                            )
                        )
                        currentOffset > 15f -> Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                color_call_end.copy(alpha = (currentDragNormal * 0.32f).coerceIn(0f, 0.35f))
                            )
                        )
                        else -> null
                    }
                }

                if (glowBrush != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(glowBrush)
                    )
                }

                // Upward chevrons in top part of track (Answer)
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 18.dp)
                        .graphicsLayer {
                            alpha = topChevronsAlpha
                            translationY = if (!isDraggingUp) arrowBounceUp else 0f
                            val scale = if (isDraggingUp) 1f + currentDragNormal * 0.12f else 1f
                            scaleX = scale
                            scaleY = scale
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy((-7).dp)
                ) {
                    Icon(
                        Icons.Rounded.KeyboardArrowUp,
                        contentDescription = null,
                        tint = color_call_button,
                        modifier = Modifier.size(26.dp)
                    )
                    Icon(
                        Icons.Rounded.KeyboardArrowUp,
                        contentDescription = null,
                        tint = color_call_button.copy(alpha = 0.65f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Downward chevrons in bottom part of track (Decline)
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 18.dp)
                        .graphicsLayer {
                            alpha = bottomChevronsAlpha
                            translationY = if (!isDraggingDown) arrowBounceDown else 0f
                            val scale = if (isDraggingDown) 1f + currentDragNormal * 0.12f else 1f
                            scaleX = scale
                            scaleY = scale
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy((-7).dp)
                ) {
                    Icon(
                        Icons.Rounded.KeyboardArrowDown,
                        contentDescription = null,
                        tint = color_call_end.copy(alpha = 0.65f),
                        modifier = Modifier.size(20.dp)
                    )
                    Icon(
                        Icons.Rounded.KeyboardArrowDown,
                        contentDescription = null,
                        tint = color_call_end,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }

        // --- Draggable Handle ---
        Box(contentAlignment = Alignment.Center) {
            val pulseFade = (1f - (abs(currentOffset) / with(localDensity) { 24.dp.toPx() })).coerceIn(0f, 1f)
            if (pulseFade > 0f) {
                Box(
                    modifier = Modifier
                        .size(handleSize)
                        .scale(pulseScale)
                        .background(color_call_button.copy(alpha = pulseAlpha * 0.25f * pulseFade), CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size(handleSize)
                        .scale(pulseScale * 1.35f)
                        .border(1.dp, color_call_button.copy(alpha = pulseAlpha * 0.15f * pulseFade), CircleShape)
                )
            }

            val handleBgColor by animateColorAsState(
                targetValue = when {
                    currentOffset < -15f -> color_call_button
                    currentOffset > 15f -> color_call_end
                    else -> Color.White
                },
                animationSpec = tween(220, easing = FastOutSlowInEasing),
                label = "bgColor"
            )

            val iconTint by animateColorAsState(
                targetValue = if (abs(currentOffset) > 15f) Color.White else color_call_button,
                animationSpec = tween(220, easing = FastOutSlowInEasing),
                label = "iconTint"
            )

            val handleScale = 1f + (currentDragNormal * 0.08f)
            val shadowDp = (5f + currentDragNormal * 10f).dp

            Surface(
                shape           = CircleShape,
                color           = if (useLg || useBlur) handleBgColor.copy(0.85f) else handleBgColor,
                shadowElevation = 0.dp,
                tonalElevation  = 0.dp,
                modifier = Modifier
                    .offset { IntOffset(0, currentOffset.roundToInt()) }
                    .size(handleSize)
                    .scale(handleScale)
                    .shadow(shadowDp, CircleShape)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                coroutineScope.launch {
                                    when {
                                        offsetY.value < -triggerThreshold -> {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                            } else {
                                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                            }
                                            onAnswer()
                                        }

                                        offsetY.value > triggerThreshold -> {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                                view.performHapticFeedback(HapticFeedbackConstants.REJECT)
                                            } else {
                                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                            }
                                            onDecline()
                                        }

                                        else -> {
                                            hasCrossedThreshold = false
                                            offsetY.animateTo(
                                                0f,
                                                spring(
                                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                                    stiffness = Spring.StiffnessMediumLow
                                                )
                                            )
                                        }
                                    }
                                }
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                coroutineScope.launch {
                                    val newOffset = (offsetY.value + dragAmount).coerceIn(-maxDrag, maxDrag)
                                    val crossed = abs(newOffset) >= triggerThreshold
                                    if (crossed && !hasCrossedThreshold) {
                                        hasCrossedThreshold = true
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    } else if (!crossed && hasCrossedThreshold) {
                                        hasCrossedThreshold = false
                                    }
                                    offsetY.snapTo(newOffset)
                                }
                            }
                        )
                    }
                    .then(
                        if (useLg && globalBackdrop != null) Modifier.drawBackdrop(
                            backdrop = globalBackdrop,
                            shape = { CircleShape },
                            shadow = { Shadow(radius = 8.dp) },
                            effects = {
                                val d = density
                                colorControls(saturation = 1.3f)
                                blur(blurIntensity * d)
                                lens(refractionHeight = 18f * d, refractionAmount = 52f * d)
                            },
                            highlight = { Highlight.Default }
                        )
                        else if (useBlur && globalBackdrop != null) Modifier.drawPlainBackdrop(
                            backdrop = globalBackdrop,
                            shape    = { CircleShape },
                            shadow = { Shadow(radius = 8.dp) },
                            effects  = { blur(blurIntensity * density) }
                        )
                        else Modifier
                    )
            ) {
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    val icon = if (currentOffset > 15f) Icons.Rounded.CallEnd else Icons.Rounded.Call

                    Crossfade(targetState = icon, label = "icon") { targetIcon ->
                        Icon(
                            targetIcon,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun IPhoneSwipeToAnswer(
    useLg: Boolean,
    useBlur: Boolean,
    blurIntensity: Float,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    onMessage: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val localDensity = LocalDensity.current
    val view = LocalView.current
    val isDark = isSystemInDarkTheme()
    val globalBackdrop = LocalLiquidGlassBackdrop.current
    val layoutDirection = LocalLayoutDirection.current
    val isRtl = layoutDirection == LayoutDirection.Rtl

    var isDragging by remember { mutableStateOf(false) }
    var hasCrossedThreshold by remember { mutableStateOf(false) }

    val trackWidth = 310.dp
    val trackHeight = 72.dp
    val handleSize = 60.dp
    val handlePadding = 6.dp

    val trackWidthPx = with(localDensity) { trackWidth.toPx() }
    val handleSizePx = with(localDensity) { handleSize.toPx() }
    val handlePaddingPx = with(localDensity) { handlePadding.toPx() }

    val maxDrag = trackWidthPx - handleSizePx - (handlePaddingPx * 2)

    val trackBgColor =
        if (useLg || useBlur) bottomBarColor else Color.Black.copy(alpha = if (isDark) 0.38f else 0.28f)
    val buttonContentColor = Color.White

    // Authentic continuous iOS shimmer wave across text and chevrons
    val infiniteTransition = rememberInfiniteTransition(label = "iosShimmer")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -0.2f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerOffset"
    )

    val dragProgress = remember { derivedStateOf { if (maxDrag > 0f) (offsetX.value / maxDrag).coerceIn(0f, 1f) else 0f } }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(32.dp),
        modifier = Modifier.padding(bottom = 32.dp)
    ) {
        // iOS Action Buttons (Decline & Message) with Spring Press Feedback
        Row(
            modifier = Modifier.fillMaxWidth(0.72f),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Decline (Red Button)
            val declineInteraction = remember { MutableInteractionSource() }
            val isDeclinePressed by declineInteraction.collectIsPressedAsState()
            val declineScale by animateFloatAsState(
                targetValue = if (isDeclinePressed) 0.90f else 1f,
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                label = "declineScale"
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.REJECT)
                        onDecline()
                    },
                    shape = CircleShape,
                    color = color_call_end,
                    interactionSource = declineInteraction,
                    modifier = Modifier
                        .size(58.dp)
                        .scale(declineScale)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.CallEnd,
                            contentDescription = stringResource(R.string.decline),
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
                Text(
                    stringResource(R.string.decline),
                    style = MaterialTheme.typography.labelMedium,
                    color = buttonContentColor,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            // Message Button (Translucent Frosted Circle)
            val messageInteraction = remember { MutableInteractionSource() }
            val isMessagePressed by messageInteraction.collectIsPressedAsState()
            val messageScale by animateFloatAsState(
                targetValue = if (isMessagePressed) 0.90f else 1f,
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                label = "messageScale"
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    onClick = onMessage,
                    shape = CircleShape,
                    color = trackBgColor,
                    interactionSource = messageInteraction,
                    modifier = Modifier
                        .size(58.dp)
                        .scale(messageScale)
                        .then(
                            if (useLg && globalBackdrop != null) Modifier.drawBackdrop(
                                backdrop = globalBackdrop,
                                shape = { CircleShape },
                                shadow = { Shadow(radius = 8.dp) },
                                effects = {
                                    val d = density
                                    colorControls(saturation = 1.3f)
                                    blur(blurIntensity * d)
                                    lens(refractionHeight = 18f * d, refractionAmount = 52f * d)
                                },
                                highlight = { Highlight.Default }
                            )
                            else if (useBlur && globalBackdrop != null) Modifier.drawPlainBackdrop(
                                backdrop = globalBackdrop,
                                shape = { CircleShape },
                                shadow = { Shadow(radius = 8.dp) },
                                effects = { blur(blurIntensity * density) }
                            )
                            else Modifier
                        )
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_message_outline),
                            contentDescription = stringResource(R.string.message),
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
                Text(
                    stringResource(R.string.message),
                    style = MaterialTheme.typography.labelMedium,
                    color = buttonContentColor,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        // iOS Slide to Answer Capsule Track (Pure Authentic Clean Style)
        Box(
            modifier = Modifier
                .width(trackWidth)
                .height(trackHeight)
                .clip(CircleShape)
                .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape),
            contentAlignment = Alignment.CenterStart
        ) {
            // Track base background surface
            Surface(
                shape = CircleShape,
                color = trackBgColor,
                shadowElevation = 0.dp,
                tonalElevation = 0.dp,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (useLg && globalBackdrop != null) Modifier.drawBackdrop(
                            backdrop = globalBackdrop,
                            shape = { CircleShape },
                            shadow = { Shadow(radius = 8.dp) },
                            effects = {
                                val d = density
                                colorControls(saturation = 1.3f)
                                blur(blurIntensity * d)
                                lens(refractionHeight = 18f * d, refractionAmount = 52f * d)
                            },
                            highlight = { Highlight.Default }
                        )
                        else if (useBlur && globalBackdrop != null) Modifier.drawPlainBackdrop(
                            backdrop = globalBackdrop,
                            shape = { CircleShape },
                            shadow = { Shadow(radius = 8.dp) },
                            effects = { blur(blurIntensity * density) }
                        )
                        else Modifier
                    )
            ) {}

            // Shimmering Text & Animated Chevrons
            val shimmerBrush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.32f),
                    Color.White.copy(alpha = 0.98f),
                    Color.White.copy(alpha = 0.32f)
                ),
                start = if (isRtl) {
                    val rtlOffset = 1.1f - shimmerOffset
                    Offset(trackWidthPx * (rtlOffset + 0.28f), 0f)
                } else {
                    Offset(trackWidthPx * (shimmerOffset - 0.28f), 0f)
                },
                end = if (isRtl) {
                    val rtlOffset = 1.1f - shimmerOffset
                    Offset(trackWidthPx * (rtlOffset - 0.28f), 0f)
                } else {
                    Offset(trackWidthPx * (shimmerOffset + 0.28f), 0f)
                }
            )

            // Text fades out smoothly as handle advances (completely gone by 45% of drag)
            val textAlpha = (1f - (dragProgress.value / 0.45f)).coerceIn(0f, 1f)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = handleSize + 12.dp, end = 20.dp)
                    .graphicsLayer {
                        alpha = textAlpha
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(R.string.slide_to_answer),
                    style = MaterialTheme.typography.titleMedium.copy(
                        brush = shimmerBrush,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp
                    ),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (isRtl) "‹ ‹ ‹" else "› › ›",
                    style = MaterialTheme.typography.titleMedium.copy(
                        brush = shimmerBrush,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                )
            }

            // Slider Thumb Handle with Strict Boundaries (Never Exceeds Track or Screen)
            val visualOffsetX = if (isRtl) -offsetX.value else offsetX.value
            Box(
                modifier = Modifier
                    .padding(start = handlePadding)
                    .offset { IntOffset(visualOffsetX.roundToInt(), 0) }
                    .size(handleSize)
                    .shadow(3.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Color.White)
                    .pointerInput(maxDrag, isRtl) {
                        detectHorizontalDragGestures(
                            onDragStart = {
                                isDragging = true
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            },
                            onDragEnd = {
                                isDragging = false
                                coroutineScope.launch {
                                    if (offsetX.value >= maxDrag * 0.80f) {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                        } else {
                                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                        }
                                        offsetX.animateTo(
                                            maxDrag,
                                            spring(
                                                stiffness = Spring.StiffnessMediumLow,
                                                dampingRatio = Spring.DampingRatioNoBouncy
                                            )
                                        )
                                        onAnswer()
                                    } else {
                                        hasCrossedThreshold = false
                                        offsetX.animateTo(
                                            0f,
                                            spring(
                                                dampingRatio = Spring.DampingRatioLowBouncy,
                                                stiffness = Spring.StiffnessMediumLow
                                            )
                                        )
                                    }
                                }
                            },
                            onDragCancel = {
                                isDragging = false
                                hasCrossedThreshold = false
                                coroutineScope.launch {
                                    offsetX.animateTo(
                                        0f,
                                        spring(
                                            dampingRatio = Spring.DampingRatioLowBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    )
                                }
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                val delta = if (isRtl) -dragAmount else dragAmount
                                // Strictly clamp offset inside the track [0f .. maxDrag]
                                val newOffset = (offsetX.value + delta).coerceIn(0f, maxDrag)
                                coroutineScope.launch {
                                    offsetX.snapTo(newOffset)
                                    val crossed = newOffset >= maxDrag * 0.80f
                                    if (crossed && !hasCrossedThreshold) {
                                        hasCrossedThreshold = true
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    } else if (!crossed && hasCrossedThreshold) {
                                        hasCrossedThreshold = false
                                    }
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Call,
                    contentDescription = null,
                    tint = Color(0xFF34C759),
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
fun IncomingCallButtons(
    useLg: Boolean,
    useBlur: Boolean,
    blurIntensity: Float,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    onAnswerAndDecline: (() -> Unit)?
) {
    val declineColor = if (useLg || useBlur) color_call_end.copy(alpha = 0.65f) else color_call_end
    val answerColor = if (useLg || useBlur) color_call_button.copy(alpha = 0.65f) else color_call_button
    val globalBackdrop = LocalLiquidGlassBackdrop.current

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
            .padding(bottom = 32.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                val interaction = remember { MutableInteractionSource() }
                val isPressed by interaction.collectIsPressedAsState()
                val radius by animateDpAsState(
                    if (isPressed) 24.dp else 42.dp,
                    spring(stiffness = Spring.StiffnessMedium),
                    label = "btnDeclineRadius"
                )
                Box(
                    modifier = Modifier
                        .size(height = 68.dp, width = 80.dp)
                        .scale(scale * 1.06f)
                        .background(declineColor.copy(alpha = 0.2f), RoundedCornerShape(radius))
                )
                Surface(
                    onClick = onDecline,
                    modifier = Modifier.size(height = 68.dp, width = 82.dp)
                        .then(
                            if (useLg && globalBackdrop != null) Modifier.drawBackdrop(
                                backdrop = globalBackdrop,
                                shape = { RoundedCornerShape(radius) },
                                shadow = { Shadow(radius = 8.dp) },
                                effects = {
                                    val d = density
                                    colorControls(saturation = 1.3f)
                                    blur(blurIntensity * d)
                                    lens(refractionHeight = 18f * d, refractionAmount = 52f * d)
                                },
                                highlight = { Highlight.Default }
                            )
                            else if (useBlur && globalBackdrop != null) Modifier.drawPlainBackdrop(
                                backdrop = globalBackdrop,
                                shape    = { RoundedCornerShape(radius) },
                                shadow = { Shadow(radius = 8.dp) },
                                effects  = { blur(blurIntensity * density) }
                            )
                            else Modifier
                        ),
                    shape = RoundedCornerShape(radius),
                    color = declineColor,
                    interactionSource = interaction
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.CallEnd,
                            contentDescription = stringResource(R.string.decline),
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.decline),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 96.dp)
            )
        }

        if (onAnswerAndDecline != null) Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                val interaction = remember { MutableInteractionSource() }
                val isPressed by interaction.collectIsPressedAsState()
                val radius by animateDpAsState(
                    if (isPressed) 24.dp else 42.dp,
                    spring(stiffness = Spring.StiffnessMedium),
                    label = "btnRadius"
                )
                Box(
                    modifier = Modifier
                        .size(height = 68.dp, width = 80.dp)
                        .scale(scale * 1.06f)
                        .background(declineColor.copy(alpha = 0.1f), RoundedCornerShape(radius))
                )
                Surface(onClick = onAnswerAndDecline,
                    modifier = Modifier.size(height = 68.dp, width = 82.dp)
                        .then(
                            if (useLg && globalBackdrop != null) Modifier.drawBackdrop(
                                backdrop = globalBackdrop,
                                shape = { RoundedCornerShape(radius) },
                                shadow = { Shadow(radius = 8.dp) },
                                effects = {
                                    val d = density
                                    colorControls(saturation = 1.3f)
                                    blur(blurIntensity * d)
                                    lens(refractionHeight = 18f * d, refractionAmount = 52f * d)
                                },
                                highlight = { Highlight.Default }
                            )
                            else if (useBlur && globalBackdrop != null) Modifier.drawPlainBackdrop(
                                backdrop = globalBackdrop,
                                shape    = { RoundedCornerShape(radius) },
                                shadow = { Shadow(radius = 8.dp) },
                                effects  = { blur(blurIntensity * density) }
                            )
                            else Modifier
                        ),
                    shape = RoundedCornerShape(radius),
                    color = answerColor,
                    interactionSource = interaction
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.CallEnd,
                            contentDescription = stringResource(R.string.answer_and_decline),
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.answer_and_decline),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelLarge,
                lineHeight = MaterialTheme.typography.labelMedium.lineHeight,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 96.dp)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                val interaction = remember { MutableInteractionSource() }
                val isPressed by interaction.collectIsPressedAsState()
                val radius by animateDpAsState(
                    if (isPressed) 24.dp else 42.dp,
                    spring(stiffness = Spring.StiffnessMedium),
                    label = "btnAnswerRadius"
                )
                Box(
                    modifier = Modifier
                        .size(height = 68.dp, width = 80.dp)
                        .scale(scale * 1.06f)
                        .background(answerColor.copy(alpha = 0.2f), RoundedCornerShape(radius))
                )
                Surface(onClick = onAnswer,
                    modifier = Modifier.size(height = 68.dp, width = 82.dp)
                        .then(
                            if (useLg && globalBackdrop != null) Modifier.drawBackdrop(
                                backdrop = globalBackdrop,
                                shape = { RoundedCornerShape(radius) },
                                shadow = { Shadow(radius = 8.dp) },
                                effects = {
                                    val d = density
                                    colorControls(saturation = 1.3f)
                                    blur(blurIntensity * d)
                                    lens(refractionHeight = 18f * d, refractionAmount = 52f * d)
                                },
                                highlight = { Highlight.Default }
                            )
                            else if (useBlur && globalBackdrop != null) Modifier.drawPlainBackdrop(
                                backdrop = globalBackdrop,
                                shape    = { RoundedCornerShape(radius) },
                                shadow = { Shadow(radius = 8.dp) },
                                effects  = { blur(blurIntensity * density) }
                            )
                            else Modifier
                        ),
                    shape = RoundedCornerShape(radius),
                    color = answerColor,
                    interactionSource = interaction
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Call,
                            contentDescription = stringResource(R.string.answer),
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.answer),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 96.dp)
            )
        }
    }
}

@Composable
fun DefaultSwipeToAnswer(
    useLg: Boolean,
    useBlur: Boolean,
    blurIntensity: Float,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    onMessage: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val localDensity = LocalDensity.current
    val view = LocalView.current
    val isDark = isSystemInDarkTheme()
    val globalBackdrop = LocalLiquidGlassBackdrop.current
    val layoutDirection = LocalLayoutDirection.current
    val isRtl = layoutDirection == LayoutDirection.Rtl

    val trackWidth = 320.dp
    val trackHeight = 80.dp
    val handleHeight = 68.dp
    val handleWidth = 96.dp
    val handlePadding = 6.dp

    val trackWidthPx = with(localDensity) { trackWidth.toPx() }
    val handleWidthPx = with(localDensity) { handleWidth.toPx() }
    val handlePaddingPx = with(localDensity) { handlePadding.toPx() }

    val maxDrag = trackWidthPx - handleWidthPx - (handlePaddingPx * 2)

    val buttonBgColor =
        if (useLg || useBlur) bottomBarColor else MaterialTheme.colorScheme.onSurface.copy(alpha = if (isDark) 0.15f else 0.1f)
    val buttonContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
    val buttonIconColor = MaterialTheme.colorScheme.onSurface
    val handleBgColor = Color.White

    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "offset"
    )

    val dragProgress = remember { derivedStateOf { if (maxDrag > 0f) offsetX.value / maxDrag else 0f } }
    val iconRotation by remember { derivedStateOf { dragProgress.value * 135f } }

    val appearanceStretch = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(500.milliseconds)
        while (true) {
            appearanceStretch.animateTo(12f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
            appearanceStretch.animateTo(0f, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium))
            delay(4000.milliseconds)
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(42.dp),
        modifier = Modifier.padding(bottom = 42.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(0.75f),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = onDecline,
                    colors = IconButtonDefaults.iconButtonColors(containerColor = buttonBgColor.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .size(60.dp)
                        .then(
                            if (useLg && globalBackdrop != null) Modifier.drawBackdrop(
                                backdrop = globalBackdrop,
                                shape = { CircleShape },
                                shadow = { Shadow(radius = 8.dp) },
                                effects = {
                                    val d = density
                                    colorControls(saturation = 1.3f)
                                    blur(blurIntensity * d)
                                    lens(refractionHeight = 18f * d, refractionAmount = 52f * d)
                                },
                                highlight = { Highlight.Default }
                            )
                            else if (useBlur && globalBackdrop != null) Modifier.drawPlainBackdrop(
                                backdrop = globalBackdrop,
                                shape    = { CircleShape },
                                shadow = { Shadow(radius = 8.dp) },
                                effects  = { blur(blurIntensity * density) }
                            )
                            else Modifier.background(
                                buttonBgColor,
                                CircleShape
                            )
                        )
                ) {
                    Icon(
                        Icons.Rounded.CallEnd,
                        contentDescription = stringResource(R.string.decline),
                        tint = buttonIconColor
                    )
                }
                Text(
                    stringResource(R.string.decline),
                    style = MaterialTheme.typography.labelMedium,
                    color = buttonContentColor,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = onMessage,
                    colors = IconButtonDefaults.iconButtonColors(containerColor = buttonBgColor.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .size(60.dp)
                        .then(
                            if (useLg && globalBackdrop != null) Modifier.drawBackdrop(
                                backdrop = globalBackdrop,
                                shape = { CircleShape },
                                shadow = { Shadow(radius = 8.dp) },
                                effects = {
                                    val d = density
                                    colorControls(saturation = 1.3f)
                                    blur(blurIntensity * d)
                                    lens(refractionHeight = 18f * d, refractionAmount = 52f * d)
                                },
                                highlight = { Highlight.Default }
                            )
                            else if (useBlur && globalBackdrop != null) Modifier.drawPlainBackdrop(
                                backdrop = globalBackdrop,
                                shape    = { CircleShape },
                                shadow = { Shadow(radius = 8.dp) },
                                effects  = { blur(blurIntensity * density) }
                            )
                            else Modifier.background(
                                buttonBgColor,
                                CircleShape
                            )
                        )
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_message_outline),
                        contentDescription = stringResource(R.string.message),
                        tint = buttonIconColor
                    )
                }
                Text(
                    stringResource(R.string.message),
                    style = MaterialTheme.typography.labelMedium,
                    color = buttonContentColor,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .width(trackWidth + 16.dp)
                .height(trackHeight + 16.dp)
                .clip(CircleShape)
                .padding(8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Surface(
                shape           = CircleShape,
                color           = buttonBgColor.copy(alpha = 0.35f),
                shadowElevation = 0.dp,
                tonalElevation  = 0.dp,
                modifier = Modifier
                    .height(trackHeight)
                    .align(Alignment.CenterStart)
                    .width(
                        with(localDensity) {
                            val width = trackWidthPx// - offsetX.value
                            width.coerceAtLeast(0f).toDp()
                        }
                    )
                    .then(
                        if (useLg && globalBackdrop != null) Modifier.drawBackdrop(
                            backdrop = globalBackdrop,
                            shape = { CircleShape },
                            shadow = { Shadow(radius = 8.dp) },
                            effects = {
                                val d = density
                                colorControls(saturation = 1.3f)
                                blur(blurIntensity * d)
                                lens(refractionHeight = 18f * d, refractionAmount = 52f * d)
                            },
                            highlight = { Highlight.Default }
                        )
                        else if (useBlur && globalBackdrop != null) Modifier.drawPlainBackdrop(
                            backdrop = globalBackdrop,
                            shape    = { CircleShape },
                            shadow = { Shadow(radius = 8.dp) },
                            effects  = { blur(blurIntensity * density) }
                        )
                        else Modifier
                    )
            ) {}

            val shimmerColor = handleBgColor.copy(alpha = 0.4f)

            val brush = Brush.linearGradient(
                colors = listOf(shimmerColor, handleBgColor, shimmerColor),
                start = Offset(trackWidthPx * shimmerOffset - 150f, 0f),
                end = Offset(trackWidthPx * shimmerOffset + 150f, 0f)
            )

            Text(
                text = stringResource(R.string.slide_to_answer),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = handleWidth, end = 56.dp)
                    .graphicsLayer {
                        alpha = (1f - (offsetX.value / maxDrag) * 2f).coerceIn(0f, 1f)
                    },
                style = MaterialTheme.typography.titleMedium.copy(
                    brush = brush,
                    fontWeight = FontWeight.Medium
                ),
                textAlign = TextAlign.Center
            )
            Icon(
                Icons.AutoMirrored.Rounded.ArrowRight,
                contentDescription = null,
                tint = handleBgColor.copy(0.3f),
                modifier = Modifier.padding(end = 28.dp).size(46.dp).align(Alignment.CenterEnd)
            )
            Icon(
                Icons.AutoMirrored.Rounded.ArrowRight,
                contentDescription = null,
                tint = handleBgColor.copy(0.6f),
                modifier = Modifier.padding(end = 8.dp).size(46.dp).align(Alignment.CenterEnd)
            )

            Box(
                modifier = Modifier
                    .padding(start = handlePadding)
                    .width(with(localDensity) {
                        val currentStretch = if (offsetX.value > 0f) 0f else appearanceStretch.value
                        (handleWidthPx + offsetX.value + currentStretch).toDp()
                    })
                    .height(handleHeight)
                    .clip(CircleShape)
                    .background(handleBgColor)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                coroutineScope.launch {
                                    if (offsetX.value > maxDrag * 0.85f) {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                        } else {
                                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                        }
                                        onAnswer()
                                    } else {
                                        offsetX.animateTo(0f, spring(dampingRatio = 0.8f))
                                    }
                                }
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                coroutineScope.launch {
                                    val adjustedDragAmount = if (isRtl) -dragAmount else dragAmount
                                    offsetX.snapTo(
                                        (offsetX.value + adjustedDragAmount).coerceIn(
                                            0f,
                                            maxDrag
                                        )
                                    )
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    Icons.Rounded.DensityMedium,
                    contentDescription = null,
                    tint = Color.Black.copy(alpha = 0.6f),
                    modifier = Modifier
                        .padding(end = 35.dp)
                        .rotate(90f)
                        .size(26.dp)
//                        .graphicsLayer { rotationZ = iconRotation }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MoreItem(
    headline: String,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    enabled: Boolean = true,
    isSelected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "ListItemScale"
    )

    Surface(
        color = cardColor,
        shape = RoundedCornerShape(cardCornerExtraSmall),
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale),
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (enabled) {
                        Modifier
                            .alpha(1f)
                            .combinedClickable(
                                interactionSource = interactionSource,
                                indication = ripple(),
                                onClick = onClick,
                                onLongClick = onLongClick
                            )
                    } else {
                        Modifier.alpha(0.5f)
                    }
                )
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingIcon != null) {
                if (isSelected) RillIconBox(
                    icon = leadingIcon,
                    iconContainerColor = MaterialTheme.colorScheme.surface,
                    iconBgContainerColor = MaterialTheme.colorScheme.onSurface,
                ) else RillIconBox(
                    icon = leadingIcon,
                    iconContainerColor = MaterialTheme.colorScheme.onSurface,
                    iconBgContainerColor = MaterialTheme.colorScheme.surface,
                )
                Spacer(modifier = Modifier.width(16.dp))
            }
            Text(
                modifier = Modifier.weight(1f),
                text = headline,
                style = MaterialTheme.typography.bodyLarge,
                lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (trailingIcon != null) {
                Spacer(modifier = Modifier.width(12.dp))
                Icon(
                    trailingIcon, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.width(2.dp))
            }
        }
    }
}

@Composable
fun InCallKeypad(
    call: Call,
    typedDigits: String,
    onDigitClick: (Char) -> Unit
) {
    val prefs = koinInject<PreferenceManager>()
    val settingsState by prefs.settingsChanged.collectAsStateWithLifecycle()
    val toneGenerator = remember { ToneGenerator(AudioManager.STREAM_DTMF, 80) }
    val dialpadStyle by remember(settingsState) {
        mutableIntStateOf(prefs.getInt(PreferenceManager.KEY_DIALPAD_STYLE, 3))
    }

    DisposableEffect(Unit) {
        onDispose {
            toneGenerator.release()
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val typedDigitsLength = typedDigits.length
        val fontSize = (
                (when {
                    typedDigitsLength > 42 -> 12
                    typedDigitsLength > 38 -> 14
                    typedDigitsLength > 34 -> 16
                    typedDigitsLength > 30 -> 18
                    typedDigitsLength > 25 -> 20
                    typedDigitsLength > 20 -> 24
                    typedDigitsLength > 16 -> 28
                    else -> 36
                }))
        val textStyle = MaterialTheme.typography.displaySmall.copy(
            fontSize = fontSize.sp,
            fontWeight = FontWeight.Light
        )
        Text(
            text = typedDigits,
            style = textStyle,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .animateContentSize()
        )

        val keys = listOf(
            listOf('1', '2', '3'),
            listOf('4', '5', '6'),
            listOf('7', '8', '9'),
            listOf('*', '0', '#')
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            keys.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    row.forEach { key ->
                        KeypadButton(
                            modifier = Modifier.weight(1f),
                            key = key,
                            style = dialpadStyle,
                            onClick = {
                                if (prefs.getBoolean(PreferenceManager.KEY_DTMF_TONE, true)) {
                                    val toneType = when (key) {
                                        '1' -> ToneGenerator.TONE_DTMF_1
                                        '2' -> ToneGenerator.TONE_DTMF_2
                                        '3' -> ToneGenerator.TONE_DTMF_3
                                        '4' -> ToneGenerator.TONE_DTMF_4
                                        '5' -> ToneGenerator.TONE_DTMF_5
                                        '6' -> ToneGenerator.TONE_DTMF_6
                                        '7' -> ToneGenerator.TONE_DTMF_7
                                        '8' -> ToneGenerator.TONE_DTMF_8
                                        '9' -> ToneGenerator.TONE_DTMF_9
                                        '0' -> ToneGenerator.TONE_DTMF_0
                                        '*' -> ToneGenerator.TONE_DTMF_S
                                        '#' -> ToneGenerator.TONE_DTMF_P
                                        else -> -1
                                    }
                                    if (toneType != -1) {
                                        toneGenerator.startTone(toneType, 120)
                                    }
                                }
                                call.playDtmfTone(key)
                                call.stopDtmfTone()
                                onDigitClick(key)
                            }
                        )
                    }
                }
            }
        }
    }
}

// ── In-Call Screen Lock Overlay (Samsung Duos / Anti-Touch Protection) ────────
@Composable
fun CallScreenLockOverlay(
    contactName: String,
    phoneNumber: String,
    callDuration: Long,
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(enabled = true) {
        // Prevent accidental system back gestures from closing call screen while locked
    }

    val view = LocalView.current
    val density = LocalDensity.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    var trackWidthPx by remember { mutableFloatStateOf(0f) }
    val thumbSizeDp = 48.dp
    val thumbPaddingDp = 5.dp
    val thumbSizePx = with(density) { thumbSizeDp.toPx() }
    val thumbPaddingPx = with(density) { thumbPaddingDp.toPx() }
    val maxOffsetPx = (trackWidthPx - thumbSizePx - (thumbPaddingPx * 2)).coerceAtLeast(1f)

    var dragOffsetPx by remember { mutableFloatStateOf(0f) }

    val infiniteTransition = rememberInfiniteTransition(label = "lockPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val textAlpha by infiniteTransition.animateFloat(
        initialValue = 0.50f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "textAlpha"
    )

    // Semi-transparent overlay preserving call screen visibility underneath while intercepting touches
    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(100f)
            .background(Color.Black.copy(alpha = 0.58f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                // Intercept any tap on background safely - does NOT unlock!
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top live call duration capsule
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.55f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
                modifier = Modifier.padding(top = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(Color(0xFF4CAF50), CircleShape)
                    )
                    Text(
                        text = dev.goodwy.rphone.controller.util.formatDuration(callDuration),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }

            // Center Floating Shield Card with Elevated Integrated Slider (Elevated safely away from End Call & controls!)
            Surface(
                shape = RoundedCornerShape(32.dp),
                color = Color(0xFF1B1E24).copy(alpha = 0.92f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
                shadowElevation = 16.dp,
                modifier = Modifier
                    .fillMaxWidth(0.90f)
                    .padding(vertical = 16.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 26.dp)
                ) {
                    // Lock icon inside pulsing circle
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.20f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .size(68.dp)
                            .scale(pulseScale)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    Text(
                        text = stringResource(R.string.call_screen_locked_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Spacer(Modifier.height(4.dp))

                    Text(
                        text = stringResource(R.string.call_screen_slide_to_unlock),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.70f),
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(24.dp))

                    // Elevated Slider (Safely in the center card, far above the in-call buttons and red End Call button!)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.10f))
                            .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                            .onSizeChanged { trackWidthPx = it.width.toFloat() },
                        contentAlignment = Alignment.CenterStart
                    ) {
                        // Slider Track Label
                        Text(
                            text = stringResource(R.string.call_screen_slide_to_unlock),
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.5.sp),
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = textAlpha),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp),
                            textAlign = TextAlign.Center
                        )

                        // Drag Thumb
                        val animatedOffset by animateFloatAsState(
                            targetValue = dragOffsetPx,
                            animationSpec = spring(stiffness = Spring.StiffnessMedium),
                            label = "thumbOffset"
                        )
                        val visualOffsetX = if (isRtl) -animatedOffset else animatedOffset

                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            shadowElevation = 4.dp,
                            modifier = Modifier
                                .padding(start = thumbPaddingDp)
                                .offset { IntOffset(x = visualOffsetX.toInt(), y = 0) }
                                .size(thumbSizeDp)
                                .pointerInput(maxOffsetPx, isRtl) {
                                    detectHorizontalDragGestures(
                                        onDragEnd = {
                                            if (dragOffsetPx >= maxOffsetPx * 0.45f) {
                                                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                                onUnlock()
                                            }
                                            dragOffsetPx = 0f
                                        },
                                        onDragCancel = { dragOffsetPx = 0f },
                                        onHorizontalDrag = { _, dragAmount ->
                                            val delta = if (isRtl) -dragAmount else dragAmount
                                            val newOffset = (dragOffsetPx + delta).coerceIn(0f, maxOffsetPx)
                                            dragOffsetPx = newOffset
                                            if (newOffset >= maxOffsetPx * 0.70f) {
                                                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                                onUnlock()
                                            }
                                        }
                                    )
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (dragOffsetPx >= maxOffsetPx * 0.45f) Icons.Rounded.LockOpen else Icons.Rounded.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Bottom empty spacer: leaves the lower screen area (controls & End Call button) clean and clear
            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

// ── Dynamic In-Call Action State ─────────────────────────────────────────────
data class InCallActionState(
    val actionKey: String,
    val label: String,
    val icon: ImageVector,
    val isActive: Boolean = false,
    val isEnabled: Boolean = true,
    val onClick: () -> Unit,
    val onLongClick: (() -> Unit)? = null
)

// ── iOS Style Button ─────────────────────────────────────────────────────────
@Composable
fun IOSCallButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean = false,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        spring(stiffness = Spring.StiffnessMedium),
        label = "iosBtnScale"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = CircleShape,
            color = if (isActive) Color.White else Color.White.copy(alpha = 0.20f),
            border = if (!isActive) BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f)) else null,
            modifier = Modifier
                .size(75.dp)
                .scale(scale)
                .clip(CircleShape)
                .combinedClickable(
                    interactionSource = interaction,
                    indication = ripple(),
                    enabled = enabled,
                    onClick = onClick,
                    onLongClick = onLongClick
                )
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (isActive) Color.Black else Color.White.copy(alpha = if (enabled) 1f else 0.4f),
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Normal),
            color = Color.White.copy(alpha = if (enabled) 0.92f else 0.4f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

// ── iOS Call Controls (Authentic 3x2 Circular Frosted Grid + Red Hangup) ─────
@Composable
fun CallControlsIOS(
    actions: List<InCallActionState>,
    onHangupClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 3x2 Grid
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            // Row 1: Slots 0, 1, 2
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                for (i in 0..2) {
                    val action = actions.getOrNull(i)
                    if (action != null) {
                        IOSCallButton(
                            icon = action.icon,
                            label = action.label,
                            isActive = action.isActive,
                            enabled = action.isEnabled,
                            onClick = action.onClick,
                            onLongClick = action.onLongClick,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

            // Row 2: Slots 3, 4, 5
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                for (i in 3..5) {
                    val action = actions.getOrNull(i)
                    if (action != null) {
                        IOSCallButton(
                            icon = action.icon,
                            label = action.label,
                            isActive = action.isActive,
                            enabled = action.isEnabled,
                            onClick = action.onClick,
                            onLongClick = action.onLongClick,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(34.dp))

        // Large circular red End Call button (75dp, iOS signature #EB5545)
        Surface(
            onClick = onHangupClick,
            shape = CircleShape,
            color = Color(0xFFEB5545),
            modifier = Modifier.size(75.dp),
            shadowElevation = 4.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.CallEnd,
                    contentDescription = stringResource(R.string.end_call),
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }
}

// ── One UI Style Button (Samsung Signature Rounded Squircle) ───────────────
@Composable
fun OneUICallButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean = false,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        spring(stiffness = Spring.StiffnessMedium),
        label = "oneUIBtnScale"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = if (isActive) Color.White else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            modifier = Modifier
                .size(68.dp)
                .scale(scale)
                .clip(RoundedCornerShape(24.dp))
                .combinedClickable(
                    interactionSource = interaction,
                    indication = ripple(),
                    enabled = enabled,
                    onClick = onClick,
                    onLongClick = onLongClick
                )
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (isActive) Color.Black else MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.4f),
                    modifier = Modifier.size(30.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.Normal),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 0.88f else 0.4f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

// ── One UI Call Controls (Samsung Squircle Grid + Iconic Wide Pill Hangup) ───
@Composable
fun CallControlsOneUI(
    actions: List<InCallActionState>,
    onHangupClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 3x2 Squircle Grid
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Row 1: Slots 0, 1, 2
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                for (i in 0..2) {
                    val action = actions.getOrNull(i)
                    if (action != null) {
                        OneUICallButton(
                            icon = action.icon,
                            label = action.label,
                            isActive = action.isActive,
                            enabled = action.isEnabled,
                            onClick = action.onClick,
                            onLongClick = action.onLongClick,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

            // Row 2: Slots 3, 4, 5
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                for (i in 3..5) {
                    val action = actions.getOrNull(i)
                    if (action != null) {
                        OneUICallButton(
                            icon = action.icon,
                            label = action.label,
                            isActive = action.isActive,
                            enabled = action.isEnabled,
                            onClick = action.onClick,
                            onLongClick = action.onLongClick,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Samsung Iconic Wide Pill End Call button (One UI signature)
        Surface(
            onClick = onHangupClick,
            shape = CircleShape,
            color = Color(0xFFE53935),
            shadowElevation = 4.dp,
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .height(64.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CallEnd,
                        contentDescription = stringResource(R.string.end_call),
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                    Text(
                        text = stringResource(R.string.end_call),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ── Minimal Floating Call Controls ───────────────────────────────────────────
@Composable
fun CallControlsMinimal(
    actions: List<InCallActionState>,
    onHangupClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(36.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.88f),
        tonalElevation = 6.dp,
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            actions.forEach { action ->
                Surface(
                    shape = CircleShape,
                    color = if (action.isActive) Color.White else Color.Transparent,
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .combinedClickable(
                            enabled = action.isEnabled,
                            onClick = action.onClick,
                            onLongClick = action.onLongClick
                        )
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = action.icon,
                            contentDescription = action.label,
                            tint = if (action.isActive) Color.Black else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
            Surface(
                onClick = onHangupClick,
                shape = CircleShape,
                color = color_call_end,
                modifier = Modifier.size(50.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.CallEnd,
                        contentDescription = stringResource(R.string.end_call),
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    }
}