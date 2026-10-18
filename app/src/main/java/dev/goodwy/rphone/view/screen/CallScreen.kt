package dev.goodwy.rphone.view.screen

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import dev.goodwy.rphone.controller.util.CallRecorder
import androidx.core.content.ContextCompat
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.automirrored.rounded.CallSplit
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.CallViewModel
import dev.goodwy.rphone.modal.`interface`.IContactsRepository
import dev.goodwy.rphone.cardCornerExtraSmall
import dev.goodwy.rphone.controller.lock.AppLockManager
import dev.goodwy.rphone.controller.sensor.PocketModeManager
import dev.goodwy.rphone.controller.util.NoteManager
import dev.goodwy.rphone.modal.data.getDisplayName
import dev.goodwy.rphone.view.components.RillExpressiveCard
import dev.goodwy.rphone.view.theme.MyColors.bottomBarColor
import dev.goodwy.rphone.view.theme.MyColors.cardColor
import dev.goodwy.rphone.view.theme.MyColors.dialpadKeyColor
import dev.goodwy.rphone.view.theme.color_call_end
import dev.goodwy.rphone.controller.util.formatDuration
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.controller.util.forceLtr
import dev.goodwy.rphone.controller.util.isPhoneNumber
import dev.goodwy.rphone.controller.util.hasCapability
import dev.goodwy.rphone.controller.util.isHD
import dev.goodwy.rphone.controller.util.isWifi
import dev.goodwy.rphone.liquidglass.LocalLiquidGlassBackdrop
import dev.goodwy.rphone.liquidglass.backdrops.LayerBackdrop
import dev.goodwy.rphone.liquidglass.drawBackdrop
import dev.goodwy.rphone.liquidglass.drawPlainBackdrop
import dev.goodwy.rphone.liquidglass.effects.blur
import dev.goodwy.rphone.liquidglass.effects.colorControls
import dev.goodwy.rphone.liquidglass.effects.lens
import dev.goodwy.rphone.liquidglass.highlight.Highlight
import dev.goodwy.rphone.liquidglass.shadow.Shadow
import dev.goodwy.rphone.view.components.RillAvatar
import dev.goodwy.rphone.view.screen.settings.PasswordSetupDialog
import dev.goodwy.rphone.view.screen.settings.PinSetupDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun ExpressiveCallScreen(
    call: Call,
    callState: Int,
    contactName: String,
    phoneNumber: String,
    photoUri: String?,
    audioState: CallAudioState?,
    initialConnectTime: Long = 0L,
    backgroundUri: String? = null,
    skipIncomingScreen: Boolean = false,
    isConference: Boolean,
    liquidGlassBackdrop: LayerBackdrop
) {
    val view = LocalView.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferenceManager = koinInject<PreferenceManager>()
    val contactsRepo = koinInject<IContactsRepository>()
    val callViewModel = koinInject<CallViewModel>()
    // telecomManager removed as it's now handled by ViewModel

    val allCalls by callViewModel.allCalls.collectAsStateWithLifecycle()
    val otherCall = remember(allCalls, call) {
        @Suppress("DEPRECATION")
        allCalls.find { it != call && it.state != Call.STATE_DISCONNECTED }
    }

    val simLabel by callViewModel.simLabel.collectAsStateWithLifecycle()
    val isMuted = audioState?.isMuted ?: false

    val callDuration by callViewModel.callDuration.collectAsStateWithLifecycle()
    var showKeypad by remember { mutableStateOf(false) }
    var showAudioPicker by remember { mutableStateOf(false) }
    var typedDigits by remember { mutableStateOf("") }
    var showMore by remember { mutableStateOf(false) }
    var isEnding by remember { mutableStateOf(false) }
    var showManageSheet by remember { mutableStateOf(false) }

    val isIncoming = remember(call) {
        call.details?.callDirection == Call.Details.DIRECTION_INCOMING || callState == Call.STATE_RINGING
    }
    var hasEverBeenActive by remember(call) {
        mutableStateOf(callState == Call.STATE_ACTIVE)
    }
    var isPendingRecording by remember { mutableStateOf(false) }

    LaunchedEffect(callState) {
        if (callState == Call.STATE_ACTIVE) {
            hasEverBeenActive = true
            if (isPendingRecording) {
                isPendingRecording = false
                val hasPerm = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                if (hasPerm) {
                    CallRecorder.startRecording(context, phoneNumber) { started ->
                        if (started) {
                            Toast.makeText(context, context.getString(R.string.recording_started), Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, context.getString(R.string.recording_error), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        } else if (callState == Call.STATE_DISCONNECTED || callState == Call.STATE_DISCONNECTING) {
            isPendingRecording = false
        }
    }
    val isCallEnded = callState == Call.STATE_DISCONNECTED || callState == Call.STATE_DISCONNECTING

    fun callDisconnect(isIncomingCall: Boolean = false) {
        if (isIncomingCall) isEnding = true
        try { call.disconnect() } catch (_: Exception) { }
    }

    fun answerCallAction() {
        try {
            call.answer(VideoProfile.STATE_AUDIO_ONLY)
        } catch (_: Exception) {}
    }

    val settingsState by preferenceManager.settingsChanged.collectAsStateWithLifecycle()
    val showCallScreenAvatar = remember(settingsState) {
        preferenceManager.getBoolean(PreferenceManager.KEY_SHOW_CALL_SCREEN_AVATAR, true)
    }
    val hideAvatarWithBg = remember(settingsState) {
        preferenceManager.getBoolean(PreferenceManager.KEY_HIDE_AVATAR_WITH_BACKGROUND, false)
    }
    val hasBackground = !backgroundUri.isNullOrEmpty()
    val shouldShowAvatar = showCallScreenAvatar && !(hideAvatarWithBg && hasBackground)
    val callerNameSize = remember(settingsState) {
        preferenceManager.getInt(PreferenceManager.KEY_CALLER_NAME_SIZE, 0)
    }
    val callerNumberSize = remember(settingsState) {
        preferenceManager.getInt(PreferenceManager.KEY_CALLER_NUMBER_SIZE, 0)
    }

    val callScreenStyle = remember(settingsState) {
        preferenceManager.getInt(PreferenceManager.KEY_CALL_SCREEN_STYLE, PreferenceManager.CALL_SCREEN_STYLE_DEFAULT)
    }
    val lockButtonEnabled = remember(settingsState) {
        preferenceManager.getBoolean(PreferenceManager.KEY_CALL_SCREEN_LOCK_BUTTON, true)
    }
    var isScreenLocked by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(callState) {
        if (callState == Call.STATE_DISCONNECTED || callState == Call.STATE_DISCONNECTING) {
            isScreenLocked = false
        }
    }

    val isRecording by CallRecorder.isRecording.collectAsStateWithLifecycle()
    val recordingDuration by CallRecorder.recordingDuration.collectAsStateWithLifecycle()
    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            if (callState == Call.STATE_ACTIVE) {
                CallRecorder.startRecording(context, phoneNumber) { started ->
                    if (started) {
                        Toast.makeText(context, context.getString(R.string.recording_started), Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, context.getString(R.string.recording_error), Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                isPendingRecording = true
                Toast.makeText(context, context.getString(R.string.recording_standby_toast), Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, context.getString(R.string.recording_error), Toast.LENGTH_SHORT).show()
        }
    }

    val globalBackdrop = LocalLiquidGlassBackdrop.current
    val liquidGlass = remember(settingsState) { preferenceManager.getBoolean(PreferenceManager.KEY_LIQUID_GLASS, false) }
    val lgCallScreen = remember(settingsState) { preferenceManager.getBoolean(PreferenceManager.KEY_LG_CALL_SCREEN, true) }
    val blurEffects = remember(settingsState) { preferenceManager.getBoolean(PreferenceManager.KEY_BLUR_EFFECTS, false) }
    val blurCallScreen = remember(settingsState) { preferenceManager.getBoolean(PreferenceManager.KEY_BLUR_CALL_SCREEN, true) }
    val blurIntensity = remember(settingsState) { preferenceManager.getInt(PreferenceManager.KEY_BLUR_INTENSITY, 20).toFloat() }
    val useLgCallScreen = liquidGlass && lgCallScreen && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && globalBackdrop != null
    val useBlurCallScreen = blurEffects && blurCallScreen && !useLgCallScreen

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // LaunchedEffect for duration removed (handled by ViewModel)

    BackHandler(showKeypad) {
        showKeypad = false
    }

    var showQuickResponsesSheet by remember { mutableStateOf(false) }

    val pocketModeEnabled = remember(settingsState) {
        preferenceManager.getBoolean(PreferenceManager.KEY_POCKET_MODE, false)
    }
    var isPocketModeCovered by remember { mutableStateOf(false) }
    var pocketModeDismissedManually by remember { mutableStateOf(false) }

    DisposableEffect(callState, pocketModeEnabled) {
        if (pocketModeEnabled && callState == Call.STATE_RINGING) {
            val manager = PocketModeManager(context)
            manager.startListening { isNear ->
                isPocketModeCovered = isNear
            }
            onDispose {
                manager.stopListening()
            }
        } else {
            isPocketModeCovered = false
            onDispose { }
        }
    }

    val onSendQuickResponse: (String) -> Unit = { message ->
        try {
            call.reject(true, message)
        } catch (e: Exception) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    call.reject(Call.REJECT_REASON_DECLINED)
                } else {
                    call.disconnect()
                }
            } catch (_: Exception) {}
            try {
                val intent = Intent(Intent.ACTION_SENDTO, "smsto:$phoneNumber".toUri()).apply {
                    putExtra("sms_body", message)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {}
        }
        showQuickResponsesSheet = false
    }

    // Call notes --->
    var showNoteWindow by remember { mutableStateOf(false) }
    var noteText by remember { mutableStateOf("") }

    // ── Call-lock biometric ────────────────────────────────────────────────
    val callLockEnabled = remember {
        preferenceManager.shouldGateCallWithBiometric(phoneNumber)
    }
    var callBiometricUnlocked by remember { mutableStateOf(!callLockEnabled || skipIncomingScreen) }

    var showCallBiometricUnlock by remember { mutableStateOf(false) }
    var biometricGatesScreen by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    LaunchedEffect(callState) {
        if (callLockEnabled && !callBiometricUnlocked && !showCallBiometricUnlock) {
            if (callState == Call.STATE_RINGING) {
                biometricGatesScreen = true
                showCallBiometricUnlock = true
            }
        }
    }

    LaunchedEffect(phoneNumber) {
        if (phoneNumber.isNotEmpty() && noteText.isBlank()) {
            val existing = withContext(Dispatchers.IO) {
                NoteManager.readNoteByPhone(context, phoneNumber)
            }
            if (existing.isNotBlank()) noteText = existing
        }
    }

    LaunchedEffect(contactName) {
        if (phoneNumber.isNotEmpty() && noteText.isBlank()) {
            val existing = withContext(Dispatchers.IO) {
                NoteManager.readNote(context, contactName, phoneNumber)
            }
            if (existing.isNotBlank()) noteText = existing
        }
    }

    LaunchedEffect(noteText) {
        if (phoneNumber.isNotEmpty() && noteText.isNotBlank()) {
            delay(1000.milliseconds)
            withContext(Dispatchers.IO) {
                NoteManager.writeNote(context, contactName, phoneNumber, noteText)
            }
        }
    }

    LaunchedEffect(callState) {
        if (callState == Call.STATE_DISCONNECTED || callState == Call.STATE_DISCONNECTING) {
            if (noteText.isNotBlank() && phoneNumber.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    NoteManager.writeNote(context, contactName, phoneNumber, noteText)
                }
            }
            if (CallRecorder.isRecording.value) {
                val saved = CallRecorder.stopRecording()
                if (saved != null) {
                    Toast.makeText(context, context.getString(R.string.recording_saved), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (CallRecorder.isRecording.value) {
                CallRecorder.stopRecording()
            }
        }
    }
    // <--- Call notes

    Box(modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.surface)
    ) {
        ExpressiveBackground(photoUri, backgroundUri, liquidGlassBackdrop, useLgCallScreen || useBlurCallScreen)

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Other Call Card
            AnimatedVisibility(
                visible = otherCall != null && !isConference,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                otherCall?.let { oc ->
                    val cnam = if (oc.details?.callerDisplayNamePresentation == TelecomManager.PRESENTATION_ALLOWED) {
                        oc.details?.callerDisplayName?.takeIf { it.isNotBlank() }
                    } else null
                    var ocName by remember(oc, cnam) { mutableStateOf(cnam ?: oc.details?.handle?.schemeSpecificPart ?: "Unknown") }
                    val displayOrder = preferenceManager.getInt(PreferenceManager.KEY_CONTACT_DISPLAY_ORDER, 0)
                    LaunchedEffect(oc, cnam) {
                        val number = oc.details?.handle?.schemeSpecificPart ?: ""
                        if (number.isNotEmpty() && number.isNotBlank()) {
                            val contact = try { contactsRepo.getContactByNumber(number) } catch (_: Exception) { null }
                            if (contact != null) ocName = getDisplayName(contact, displayOrder)
                            else if (!cnam.isNullOrEmpty()) ocName = cnam
                        }
                    }

                    Surface(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            try {
                                callViewModel.setPreferredCall(oc)
                                if (call.state != Call.STATE_HOLDING) {
                                    call.hold()
                                }
                                oc.unhold()
                            } catch (_: Exception) {
                                try { oc.unhold() } catch (_: Exception) {}
                            }
                        },
                        modifier = Modifier
                            .statusBarsPadding()
                            .fillMaxWidth()
                            .padding(vertical = 16.dp, horizontal = 24.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = bottomBarColor,
                        tonalElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    Icons.Default.PauseCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(
                                        text = ocName,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = stringResource(R.string.call_status_on_hold),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            IconButton(onClick = { oc.disconnect() }) {
                                Icon(Icons.Rounded.CallEnd, contentDescription = stringResource(R.string.end_call), tint = color_call_end)
                            }
                        }
                    }
                }
            }

            // --- HERO SECTION ---
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp)
                    .weight(if (showKeypad || showNoteWindow || showMore || showAudioPicker) 0.7f else 1f)
            ) {
                Spacer(modifier = Modifier.weight(0.4f))
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(tween(1000)) + expandVertically(tween(800))
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val statusText = when (callState) {
                            Call.STATE_DISCONNECTED -> stringResource(R.string.call_status_ended)
                            Call.STATE_HOLDING -> stringResource(R.string.call_status_on_hold)
                            Call.STATE_ACTIVE -> formatDuration(callDuration)
                            Call.STATE_DIALING -> stringResource(R.string.call_status_calling)
                            Call.STATE_RINGING -> stringResource(R.string.call_status_incoming)
                            else -> stringResource(R.string.call_status_connecting)
                        }

                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (callState == Call.STATE_HOLDING) MaterialTheme.colorScheme.tertiary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        val resolvedNameSize = when (callerNameSize) {
                            0 -> 28.sp
                            1 -> 36.sp
                            2 -> 46.sp
                            in 14..72 -> callerNameSize.sp
                            else -> 28.sp
                        }
                        val resolvedNumberSize = when (callerNumberSize) {
                            0 -> 20.sp
                            1 -> 28.sp
                            2 -> 38.sp
                            in 10..64 -> callerNumberSize.sp
                            else -> 20.sp
                        }

                        Text(
                            text = if (contactName.isPhoneNumber()) contactName.forceLtr() else contactName,
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontSize = resolvedNameSize,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (contactName != phoneNumber) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = phoneNumber.forceLtr(),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = resolvedNumberSize,
                                    fontWeight = FontWeight.Normal
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        val currentSimLabel = simLabel
                        val isHD = call.isHD()
                        var isWifi by remember(call) { mutableStateOf(call.isWifi(context)) }
                        LaunchedEffect(call, callState) {
                            while (true) {
                                isWifi = call.isWifi(context)
                                delay(1000.milliseconds)
                            }
                        }
                        if (currentSimLabel != null || isHD || isWifi) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (currentSimLabel != null) Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.padding(top = 8.dp)
                                ) {
                                    Text(
                                        text = currentSimLabel,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (isWifi) Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.padding(top = 8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.Wifi,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "VoWiFi",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                if (isHD) Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.padding(top = 8.dp)
                                ) {
                                    Text(
                                        text = "HD",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        } else {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                Text(
                                    text = "",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        AnimatedVisibility(
                            visible = isRecording || isPendingRecording,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (isRecording) {
                                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f)
                                } else {
                                    MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.9f)
                                },
                                modifier = Modifier.padding(top = 10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val infiniteTransition = rememberInfiniteTransition(label = "recPulse")
                                    val pulseAlpha by infiniteTransition.animateFloat(
                                        initialValue = 1f,
                                        targetValue = 0.25f,
                                        animationSpec = infiniteRepeatable(
                                            animation = tween(600, easing = LinearEasing),
                                            repeatMode = RepeatMode.Reverse
                                        ),
                                        label = "pulseAlpha"
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(9.dp)
                                            .alpha(pulseAlpha)
                                            .background(if (isRecording) Color.Red else Color(0xFFFF9800), CircleShape)
                                    )
                                    Text(
                                        text = if (isRecording) {
                                            "REC ${CallRecorder.formatDuration(recordingDuration)}"
                                        } else {
                                            stringResource(R.string.recording_standby)
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isRecording) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                            }
                        }
                    }
                }

                if (!showKeypad && !showNoteWindow && !showMore && !showAudioPicker) {
                    val showAvatar = shouldShowAvatar && (photoUri != null || isConference)
                    AnimatedVisibility(
                        visible = showAvatar,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(24.dp))
                            if (callState == Call.STATE_RINGING) {
                                PulsingAvatar(photoUri)
                            } else {
                                HeroAvatar(photoUri)
                            }
                        }
                    }
                    if (!showAvatar) Spacer(modifier = Modifier.size(if (callState == Call.STATE_RINGING) 200.dp else 160.dp))
                }
                Spacer(modifier = Modifier.weight(0.6f))
            }

            // --- UI CONTROLS ---
            val showInCallControls = (!isIncoming || hasEverBeenActive) && (callState != Call.STATE_RINGING) && !isEnding && !isCallEnded
            val showIncomingControls = (callState == Call.STATE_RINGING) && !isEnding && !isCallEnded

            if (showInCallControls) {
                val isDark = isSystemInDarkTheme()
                val controlBtnColor = dialpadKeyColor
                val controlBtnActiveColor = if (isDark) Color.White else Color.Black
                val controlBtnActiveFg = if (isDark) Color.Black else Color.White
                val controlBtnFg = MaterialTheme.colorScheme.onSurface

                val controlContent: @Composable () -> Unit = {
                    val isModern = callScreenStyle == PreferenceManager.CALL_SCREEN_STYLE_IOS ||
                            callScreenStyle == PreferenceManager.CALL_SCREEN_STYLE_ONEUI
                    val isSub = showKeypad || showNoteWindow || showMore || showAudioPicker

                    val audioRoute = audioState?.route ?: CallAudioState.ROUTE_EARPIECE
                    val audioIcon = when (audioRoute) {
                        CallAudioState.ROUTE_SPEAKER -> Icons.AutoMirrored.Rounded.VolumeUp
                        CallAudioState.ROUTE_BLUETOOTH -> Icons.Rounded.Bluetooth
                        CallAudioState.ROUTE_WIRED_HEADSET -> Icons.Rounded.Headset
                        else -> Icons.AutoMirrored.Rounded.VolumeDown
                    }
                    val bluetoothLabel = stringResource(R.string.audio_route_bluetooth)
                    val audioLabel = when (audioRoute) {
                        CallAudioState.ROUTE_SPEAKER -> stringResource(R.string.audio_route_speaker)
                        CallAudioState.ROUTE_BLUETOOTH -> try {
                            audioState?.activeBluetoothDevice?.name ?: bluetoothLabel
                        } catch (e: SecurityException) {
                            bluetoothLabel
                        }
                        CallAudioState.ROUTE_WIRED_HEADSET -> stringResource(R.string.audio_route_headset)
                        else -> stringResource(R.string.audio_route_handset)
                    }
                    val hasExternalRoutes = remember(audioState) {
                        val supported = audioState?.supportedRouteMask ?: 0
                        (supported and CallAudioState.ROUTE_BLUETOOTH) != 0 ||
                                (supported and CallAudioState.ROUTE_WIRED_HEADSET) != 0
                    }

                    val messageIcon = ImageVector.vectorResource(id = R.drawable.ic_message_outline)

                    val allInCallActions = remember(
                        isMuted, showKeypad, audioRoute, audioIcon, audioLabel, hasExternalRoutes,
                        isRecording, isPendingRecording, recordingDuration, showNoteWindow, isScreenLocked,
                        otherCall, callState, isConference, showMore
                    ) {
                        mapOf(
                            "mute" to InCallActionState(
                                actionKey = "mute",
                                label = context.getString(R.string.mute),
                                icon = if (isMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                                isActive = isMuted,
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    callViewModel.toggleMute()
                                }
                            ),
                            "keypad" to InCallActionState(
                                actionKey = "keypad",
                                label = context.getString(R.string.keypad),
                                icon = Icons.Rounded.Dialpad,
                                isActive = showKeypad,
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    showKeypad = !showKeypad
                                    showNoteWindow = false
                                    showMore = false
                                    showAudioPicker = false
                                }
                            ),
                            "audio" to InCallActionState(
                                actionKey = "audio",
                                label = audioLabel,
                                icon = audioIcon,
                                isActive = audioRoute == CallAudioState.ROUTE_SPEAKER ||
                                        audioRoute == CallAudioState.ROUTE_BLUETOOTH ||
                                        audioRoute == CallAudioState.ROUTE_WIRED_HEADSET,
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    if (hasExternalRoutes) {
                                        showAudioPicker = !showAudioPicker
                                        showMore = false
                                        showNoteWindow = false
                                        showKeypad = false
                                    } else {
                                        val supported = audioState?.supportedRouteMask ?: 0
                                        if (audioRoute == CallAudioState.ROUTE_SPEAKER) {
                                            if ((supported and CallAudioState.ROUTE_EARPIECE) != 0) {
                                                callViewModel.setAudioRoute(CallAudioState.ROUTE_EARPIECE)
                                            }
                                        } else {
                                            if ((supported and CallAudioState.ROUTE_SPEAKER) != 0) {
                                                callViewModel.setAudioRoute(CallAudioState.ROUTE_SPEAKER)
                                            }
                                        }
                                    }
                                }
                            ),
                            "record" to InCallActionState(
                                actionKey = "record",
                                label = when {
                                    isRecording -> dev.goodwy.rphone.controller.util.CallRecorder.formatDuration(recordingDuration)
                                    isPendingRecording -> context.getString(R.string.recording_standby)
                                    else -> context.getString(R.string.record_call)
                                },
                                icon = when {
                                    isRecording -> Icons.Rounded.StopCircle
                                    isPendingRecording -> Icons.Rounded.Schedule
                                    else -> Icons.Rounded.FiberManualRecord
                                },
                                isActive = isRecording || isPendingRecording,
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    if (isRecording) {
                                        dev.goodwy.rphone.controller.util.CallRecorder.stopRecording()
                                        Toast.makeText(context, context.getString(R.string.recording_saved), Toast.LENGTH_SHORT).show()
                                    } else if (isPendingRecording) {
                                        isPendingRecording = false
                                        Toast.makeText(context, context.getString(R.string.recording_standby_cancelled), Toast.LENGTH_SHORT).show()
                                    } else {
                                        val hasPerm = ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.RECORD_AUDIO
                                        ) == PackageManager.PERMISSION_GRANTED
                                        if (hasPerm) {
                                            if (callState == Call.STATE_ACTIVE) {
                                                dev.goodwy.rphone.controller.util.CallRecorder.startRecording(context, phoneNumber) { started ->
                                                    if (started) {
                                                        Toast.makeText(context, context.getString(R.string.recording_started), Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        Toast.makeText(context, context.getString(R.string.recording_error), Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            } else {
                                                isPendingRecording = true
                                                Toast.makeText(context, context.getString(R.string.recording_standby_toast), Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    }
                                }
                            ),
                            "note" to InCallActionState(
                                actionKey = "note",
                                label = context.getString(R.string.add_note),
                                icon = Icons.AutoMirrored.Outlined.StickyNote2,
                                isActive = showNoteWindow,
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    showNoteWindow = !showNoteWindow
                                    showMore = false
                                    showKeypad = false
                                    showAudioPicker = false
                                }
                            ),
                            "lock" to InCallActionState(
                                actionKey = "lock",
                                label = context.getString(R.string.call_screen_lock),
                                icon = Icons.Rounded.Lock,
                                isActive = isScreenLocked,
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                    isScreenLocked = true
                                    showMore = false
                                    showKeypad = false
                                    showNoteWindow = false
                                    showAudioPicker = false
                                }
                            ),
                            "add_call" to InCallActionState(
                                actionKey = "add_call",
                                label = context.getString(R.string.add_call),
                                icon = Icons.Rounded.Add,
                                isEnabled = otherCall == null && callState != Call.STATE_DIALING,
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    val canMerge = otherCall != null || (try { call.details.can(Call.Details.CAPABILITY_MERGE_CONFERENCE) } catch (_: Exception) { false })
                                    if (!isConference && canMerge) {
                                        callViewModel.mergeCalls()
                                    } else {
                                        if (callState != Call.STATE_HOLDING) {
                                            try { call.hold() } catch (_: Exception) {}
                                        }
                                        val intent = Intent(Intent.ACTION_DIAL)
                                        context.startActivity(intent)
                                    }
                                }
                            ),
                            "hold" to InCallActionState(
                                actionKey = "hold",
                                label = if (otherCall != null && !isConference) context.getString(R.string.swap)
                                else if (callState == Call.STATE_HOLDING) context.getString(R.string.resume)
                                else context.getString(R.string.hold),
                                icon = if (otherCall != null && !isConference) Icons.Rounded.SwapCalls
                                else if (callState == Call.STATE_HOLDING) Icons.Rounded.PlayArrow
                                else Icons.Default.Pause,
                                isActive = callState == Call.STATE_HOLDING,
                                isEnabled = callState != Call.STATE_DIALING,
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    if (callState == Call.STATE_HOLDING) call.unhold() else call.hold()
                                }
                            ),
                            "message" to InCallActionState(
                                actionKey = "message",
                                label = context.getString(R.string.message),
                                icon = messageIcon,
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    showQuickResponsesSheet = true
                                }
                            ),
                            "more" to InCallActionState(
                                actionKey = "more",
                                label = context.getString(R.string.more),
                                icon = Icons.Default.MoreVert,
                                isActive = showMore,
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    showMore = !showMore
                                    showNoteWindow = false
                                    showKeypad = false
                                    showAudioPicker = false
                                }
                            )
                        )
                    }

                    val configuredOrder = preferenceManager.getCallButtonsOrder(callScreenStyle)
                    val orderedActions = configuredOrder
                        .filter { lockButtonEnabled || it != "lock" }
                        .mapNotNull { allInCallActions[it] }

                    val primarySlotsCount = if (callScreenStyle == PreferenceManager.CALL_SCREEN_STYLE_IOS ||
                        callScreenStyle == PreferenceManager.CALL_SCREEN_STYLE_ONEUI) 6 else 4
                    val primaryActions = orderedActions.take(primarySlotsCount)
                    val moreActions = orderedActions.drop(primarySlotsCount).filter { it.actionKey != "more" }

                    val onHangupClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.REJECT)
                        if (noteText.isNotBlank() && phoneNumber.isNotEmpty()) {
                            scope.launch(Dispatchers.IO) {
                                NoteManager.writeNote(context, contactName, phoneNumber, noteText)
                            }
                        }
                        callDisconnect()
                    }

                    Row(
                        modifier = Modifier
                            .padding(
                                horizontal = if (isModern && !isSub) 0.dp else 9.dp,
                                vertical = if (isModern && !isSub) 0.dp else 8.dp
                            ),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (!isModern || isSub) Modifier.navigationBarsPadding() else Modifier)
                                .padding(
                                    start = if (isModern && !isSub) 0.dp else 20.dp,
                                    end = if (isModern && !isSub) 0.dp else 20.dp,
                                    top = if (isSub) 20.dp else (if (isModern) 0.dp else 22.dp),
                                    bottom = if (isModern && !isSub) 4.dp else 20.dp
                                ),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            AnimatedContent(
                                targetState = showKeypad || showNoteWindow || showMore || showAudioPicker,
                                transitionSpec = {
                                    (fadeIn() + expandVertically(
                                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                                    )) togetherWith (fadeOut() + shrinkVertically(
                                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                                    ))
                                },
                                label = "moreContent"
                            ) { visible ->
                                if (visible) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                if (showKeypad) stringResource(R.string.keypad)
                                                else if (showNoteWindow) stringResource(R.string.add_note)
                                                else if (showAudioPicker) stringResource(R.string.audio_output_title)
                                                else stringResource(R.string.more),
                                                style = MaterialTheme.typography.titleMedium,
                                                color = controlBtnFg
                                            )
                                            IconButton(onClick = {
                                                showKeypad = false
                                                showNoteWindow = false
                                                showMore = false
                                                showAudioPicker = false
                                            }) { Icon(Icons.Rounded.Cancel, stringResource(R.string.cancel), tint = controlBtnFg) }
                                        }

                                        if (showAudioPicker) {
                                            val supported = audioState?.supportedRouteMask ?: 0
                                            val handsetLabel = stringResource(R.string.audio_route_handset)
                                            val speakerLabel = stringResource(R.string.audio_route_speaker)
                                            val headsetLabel = stringResource(R.string.audio_route_headset)
                                            val bluetoothLabel = stringResource(R.string.audio_route_bluetooth)
                                            val options = remember(supported, handsetLabel, speakerLabel, headsetLabel, bluetoothLabel) {
                                                mutableListOf<Pair<String, Int>>().apply {
                                                    if ((supported and CallAudioState.ROUTE_EARPIECE) != 0) add(handsetLabel to CallAudioState.ROUTE_EARPIECE)
                                                    if ((supported and CallAudioState.ROUTE_SPEAKER) != 0) add(speakerLabel to CallAudioState.ROUTE_SPEAKER)
                                                    if ((supported and CallAudioState.ROUTE_WIRED_HEADSET) != 0) add(headsetLabel to CallAudioState.ROUTE_WIRED_HEADSET)
                                                    if ((supported and CallAudioState.ROUTE_BLUETOOTH) != 0) {
                                                        val deviceName = try {
                                                            audioState?.activeBluetoothDevice?.name
                                                        } catch (e: SecurityException) {
                                                            null
                                                        }
                                                        add((deviceName ?: bluetoothLabel) to CallAudioState.ROUTE_BLUETOOTH)
                                                    }
                                                }
                                            }

                                            RillExpressiveCard {
                                                options.forEach { item ->
                                                    MoreItem(
                                                        headline = item.first,
                                                        leadingIcon = when (item.second) {
                                                            CallAudioState.ROUTE_SPEAKER -> Icons.AutoMirrored.Rounded.VolumeUp
                                                            CallAudioState.ROUTE_BLUETOOTH -> Icons.Rounded.Bluetooth
                                                            CallAudioState.ROUTE_WIRED_HEADSET -> Icons.Rounded.Headset
                                                            else -> Icons.AutoMirrored.Rounded.VolumeDown
                                                        },
                                                        onClick = {
                                                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                            callViewModel.setAudioRoute(item.second)
                                                        },
                                                        isSelected = item.second == audioState?.route
                                                    )
                                                }
                                            }
                                        }

                                        if (showMore) {
                                            RillExpressiveCard {
                                                moreActions.forEach { action ->
                                                    MoreItem(
                                                        headline = action.label,
                                                        leadingIcon = action.icon,
                                                        isSelected = action.isActive,
                                                        enabled = action.isEnabled,
                                                        onClick = action.onClick
                                                    )
                                                }

                                                val canMerge = otherCall != null || (try { call.details.can(Call.Details.CAPABILITY_MERGE_CONFERENCE) } catch (_: Exception) { false })
                                                if (!isConference && canMerge) MoreItem(
                                                    headline = stringResource(R.string.merge),
                                                    leadingIcon = Icons.Rounded.Merge,
                                                    enabled = canMerge,
                                                    onClick = {
                                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                        callViewModel.mergeCalls()
                                                    }
                                                )
                                                if (isConference) MoreItem(
                                                    headline = stringResource(R.string.manage),
                                                    leadingIcon = Icons.Rounded.PeopleAlt,
                                                    enabled = isConference,
                                                    onClick = {
                                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                        showManageSheet = true
                                                    }
                                                )
                                            }
                                        }

                                        if (showNoteWindow) {
                                            RillExpressiveCard {
                                                MoreItem(
                                                    headline = contactName,
                                                    leadingIcon = Icons.AutoMirrored.Outlined.StickyNote2,
                                                    trailingIcon = Icons.Default.Check,
                                                    enabled = callState != Call.STATE_DIALING,
                                                    onClick = {
                                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                        if (phoneNumber.isNotEmpty()) {
                                                            scope.launch(Dispatchers.IO) {
                                                                NoteManager.writeNote(context, contactName, phoneNumber, noteText)
                                                            }
                                                        }
                                                        showNoteWindow = false
                                                        showKeypad = false
                                                        showMore = true
                                                        showAudioPicker = false
                                                    }
                                                )
                                                Surface(
                                                    color = cardColor,
                                                    shape = RoundedCornerShape(cardCornerExtraSmall),
                                                    modifier = Modifier.fillMaxWidth(),
                                                    shadowElevation = 0.dp
                                                ) {
                                                    OutlinedTextField(
                                                        value = noteText,
                                                        onValueChange = { noteText = it },
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .heightIn(min = 184.dp, max = 184.dp),
                                                        placeholder = { Text(stringResource(R.string.type_your_note)) },
                                                        shape = RoundedCornerShape(12.dp),
                                                        minLines = 3,
                                                        colors = OutlinedTextFieldDefaults.colors(
                                                            focusedBorderColor = Color.Transparent,
                                                            unfocusedBorderColor = Color.Transparent)
                                                    )
                                                }
                                            }
                                        }

                                        if (showKeypad) {
                                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                                InCallKeypad(
                                                    call = call,
                                                    typedDigits = typedDigits,
                                                    onDigitClick = { digit -> typedDigits += digit }
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(24.dp))
                                    }
                                }
                            }

                            val isSubPanelOpen = showKeypad || showNoteWindow || showMore || showAudioPicker

                            if (!isSubPanelOpen && callScreenStyle == PreferenceManager.CALL_SCREEN_STYLE_IOS) {
                                CallControlsIOS(
                                    actions = primaryActions,
                                    onHangupClick = onHangupClick
                                )
                            } else if (!isSubPanelOpen && callScreenStyle == PreferenceManager.CALL_SCREEN_STYLE_ONEUI) {
                                CallControlsOneUI(
                                    actions = primaryActions,
                                    onHangupClick = onHangupClick
                                )
                            } else if (!isSubPanelOpen && callScreenStyle == PreferenceManager.CALL_SCREEN_STYLE_MINIMAL) {
                                CallControlsMinimal(
                                    actions = primaryActions,
                                    onHangupClick = onHangupClick
                                )
                            } else {
                                // Classic Rill controls (and panel hangup button)
                                if (!isSubPanelOpen) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        primaryActions.forEach { action ->
                                            AnimatedCallButton(
                                                modifier = Modifier.weight(1f),
                                                icon = action.icon,
                                                isActive = action.isActive,
                                                label = action.label,
                                                btnColor = controlBtnColor,
                                                activeBtnColor = controlBtnActiveColor,
                                                fgColor = controlBtnFg,
                                                activeFgColor = controlBtnActiveFg
                                            ) {
                                                action.onClick()
                                            }
                                        }
                                    }
                                }

                                val hangupWidthFraction =
                                    preferenceManager.getFloat(PreferenceManager.KEY_HANGUP_WIDTH, 0.5f)
                                val endInteraction = remember { MutableInteractionSource() }
                                val endPressed by endInteraction.collectIsPressedAsState()
                                val endScale by animateFloatAsState(
                                    targetValue = if (endPressed) 0.94f else 1.0f,
                                    animationSpec = spring(stiffness = Spring.StiffnessMedium),
                                    label = "endScale"
                                )

                                Spacer(modifier = Modifier.height(24.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val isCircleHangup = hangupWidthFraction <= 0.1f
                                    Surface(
                                        onClick = onHangupClick,
                                        modifier = (if (isCircleHangup) Modifier.size(76.dp)
                                        else Modifier
                                            .fillMaxWidth(hangupWidthFraction.coerceIn(0.1f, 1.0f))
                                            .height(68.dp))
                                            .scale(endScale),
                                        shape = CircleShape,
                                        color = color_call_end,
                                        interactionSource = endInteraction
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                val showText = hangupWidthFraction > 0.5f
                                                Icon(
                                                    Icons.Rounded.CallEnd,
                                                    stringResource(R.string.end_call),
                                                    tint = Color.White,
                                                    modifier = Modifier.size(if (showText) 26.dp else 32.dp)
                                                )
                                                if (showText) {
                                                    Text(
                                                        stringResource(R.string.end_call),
                                                        color = Color.White,
                                                        style = MaterialTheme.typography.labelLarge,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                val isSubPanelActive = showKeypad || showNoteWindow || showMore || showAudioPicker
                val isModernStyle = callScreenStyle == PreferenceManager.CALL_SCREEN_STYLE_IOS ||
                        callScreenStyle == PreferenceManager.CALL_SCREEN_STYLE_ONEUI

                if (isModernStyle && !isSubPanelActive) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                    ) {
                        controlContent()
                    }
                } else {
                    val controlShape = MaterialTheme.shapes.extraExtraLarge.copy(bottomStart = CornerSize(0.dp), bottomEnd = CornerSize(0.dp))

                    if (useLgCallScreen) {
                        Surface(
                            shape           = controlShape,
                            color           = bottomBarColor.copy(alpha = 0.35f),
                            shadowElevation = 0.dp,
                            tonalElevation  = 0.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .drawBackdrop(
                                    backdrop = globalBackdrop,
                                    shape = { controlShape },
                                    effects = {
                                        val d = density
                                        colorControls(saturation = 1.4f)
                                        blur(blurIntensity * d)
                                        lens(
                                            refractionHeight = 23f * d,
                                            refractionAmount = 64f * d
                                        )
                                    },
                                    highlight = { Highlight.Default }
                                )
                        ) { controlContent() }
                    } else if (useBlurCallScreen && globalBackdrop != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Surface(
                            shape           = controlShape,
                            color           = bottomBarColor.copy(alpha = 0.72f),
                            shadowElevation = 0.dp,
                            tonalElevation  = 0.dp,
                            modifier        = Modifier
                                .fillMaxWidth()
                                .drawPlainBackdrop(
                                    backdrop = globalBackdrop,
                                    shape = { controlShape },
                                    effects = { blur(blurIntensity * density) }
                                )
                        ) { controlContent() }
                    } else {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(controlShape),
                            color = bottomBarColor
                        ) {
                            controlContent()
                        }
                    }
                }
            } else if (showIncomingControls) {
                val useCustomUI = preferenceManager.getInt(PreferenceManager.KEY_INCOMING_CALL_UI_MODE, 10)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(start = 24.dp, end = 24.dp, bottom = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    if ((useCustomUI != 2 && useCustomUI != 3 && useCustomUI != 10) || otherCall != null) {
                        val buttonBgColor =
                            if (useLgCallScreen || useBlurCallScreen) bottomBarColor.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant
                        val interaction = remember { MutableInteractionSource() }
                        val isPressed by interaction.collectIsPressedAsState()
                        val radius by animateDpAsState(
                            if (isPressed) 16.dp else 40.dp,
                            spring(stiffness = Spring.StiffnessMedium),
                            label = "btnMessageRadius"
                        )
                        Surface(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
//                                callDisconnect(true)
//                                val intent = Intent(Intent.ACTION_SENDTO).apply {
//                                    data = "smsto:$phoneNumber".toUri()
//                                }
//                                context.startActivity(intent)
                                showQuickResponsesSheet = true
                            },
                            shape = RoundedCornerShape(radius),
                            color = buttonBgColor,
                            interactionSource = interaction,
//                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                            modifier = Modifier
                                .height(45.dp)
                                .wrapContentWidth()
                                .then(
                                    if (useLgCallScreen) Modifier.drawBackdrop(
                                        backdrop = globalBackdrop,
                                        shape = { RoundedCornerShape(radius) },
                                        shadow = { Shadow(radius = 8.dp) },
                                        effects = {
                                            val d = density
                                            colorControls(saturation = 1.3f)
                                            blur(blurIntensity * d)
                                            lens(
                                                refractionHeight = 18f * d,
                                                refractionAmount = 52f * d
                                            )
                                        },
                                        highlight = { Highlight.Default }
                                    )
                                    else if (useBlurCallScreen && globalBackdrop != null) Modifier.drawPlainBackdrop(
                                        backdrop = globalBackdrop,
                                        shape = { RoundedCornerShape(radius) },
                                        shadow = { Shadow(radius = 8.dp) },
                                        effects = { blur(blurIntensity * density) }
                                    )
                                    else Modifier
                                )
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                                Spacer(modifier = Modifier.width(18.dp))
                                Icon(painter = painterResource(id = R.drawable.ic_message_outline), stringResource(R.string.message), tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(22.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(stringResource(R.string.message), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
                                Spacer(modifier = Modifier.width(18.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(24.dp))
                    }

                    when {
                        useCustomUI == 1 || otherCall != null -> IncomingCallButtons(
                            useLg = useLgCallScreen,
                            useBlur = useBlurCallScreen,
                            blurIntensity = blurIntensity,
                            onAnswer = { answerCallAction() },
                            onDecline = { callDisconnect(true) },
                            onAnswerAndDecline = if (otherCall != null) {
                                {
                                    try {
                                        otherCall.disconnect()
                                        answerCallAction()
                                    } catch (_: Exception) {}
                                }
                            } else null
                        )
                        useCustomUI == 2 -> IPhoneSwipeToAnswer(
                            useLg = useLgCallScreen,
                            useBlur = useBlurCallScreen,
                            blurIntensity = blurIntensity,
                            onAnswer = { answerCallAction() },
                            onDecline = { callDisconnect(true) },
                            onMessage = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
//                                callDisconnect(true)
//                                val intent = Intent(Intent.ACTION_SENDTO).apply {
//                                    data = "smsto:$phoneNumber".toUri()
//                                }
//                                context.startActivity(intent)
                                showQuickResponsesSheet = true
                            }
                        )
                        useCustomUI == 3 -> VerticalSwipeToAnswer(
                            useLg = useLgCallScreen,
                            useBlur = useBlurCallScreen,
                            blurIntensity = blurIntensity,
                            onAnswer = {
                                if (callBiometricUnlocked) {
                                    answerCallAction()
                                } else {
                                    pendingAction = { answerCallAction() }
                                    showCallBiometricUnlock = true
                                }
                            },
                            onDecline = {
                                if (callBiometricUnlocked) {
                                    callDisconnect(true)
                                } else {
                                    pendingAction = { callDisconnect(true) }
                                    showCallBiometricUnlock = true
                                }
                            }
                        )
                        useCustomUI == 0 -> HorizontalSwipeToAnswer(
                            useLg = useLgCallScreen,
                            useBlur = useBlurCallScreen,
                            blurIntensity = blurIntensity,
                            onAnswer = {
                                if (callBiometricUnlocked) {
                                    answerCallAction()
                                } else {
                                    pendingAction = { answerCallAction() }
                                    showCallBiometricUnlock = true
                                }
                            },
                            onDecline = {
                                if (callBiometricUnlocked) {
                                    callDisconnect(true)
                                } else {
                                    pendingAction = { callDisconnect(true) }
                                    showCallBiometricUnlock = true
                                }
                            }
                        )
                        else -> DefaultSwipeToAnswer(
                            useLg = useLgCallScreen,
                            useBlur = useBlurCallScreen,
                            blurIntensity = blurIntensity,
                            onAnswer = {
                                if (callBiometricUnlocked) {
                                    answerCallAction()
                                } else {
                                    pendingAction = { answerCallAction() }
                                    showCallBiometricUnlock = true
                                }
                            },
                            onDecline = {
                                if (callBiometricUnlocked) {
                                    callDisconnect(true)
                                } else {
                                    pendingAction = { callDisconnect(true) }
                                    showCallBiometricUnlock = true
                                }
                            },
                            onMessage = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
//                                callDisconnect(true)
//                                val intent = Intent(Intent.ACTION_SENDTO).apply {
//                                    data = "smsto:$phoneNumber".toUri()
//                                }
//                                context.startActivity(intent)
                                showQuickResponsesSheet = true
                            }
                        )
                    }
                }
            }
        }

        if (isPocketModeCovered && !pocketModeDismissedManually && callState == Call.STATE_RINGING) {
            PocketModeOverlay(
                onDismiss = { pocketModeDismissedManually = true }
            )
        }

        if (showQuickResponsesSheet) {
            QuickResponsesBottomSheet(
                phoneNumber = phoneNumber,
                contactName = contactName,
                onDismiss = { showQuickResponsesSheet = false },
                onSend = onSendQuickResponse,
                onOpenSmsApp = {
                    try {
                        if (call.state == Call.STATE_RINGING) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                call.reject(Call.REJECT_REASON_DECLINED)
                            } else {
                                call.disconnect()
                            }
                        } else {
                            call.disconnect()
                        }
                    } catch (_: Exception) {}
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = "smsto:$phoneNumber".toUri()
                    }
                    context.startActivity(intent)
                    showQuickResponsesSheet = false
                },
//                onScheduleReminder = { delayMinutes, label ->
//                    scope.launch {
//                        reminderManager.scheduleReminder(
//                            phoneNumber = phoneNumber,
//                            contactName = contactName.ifBlank { null },
//                            delayMinutes = delayMinutes,
//                            note = "Callback reminder from incoming call"
//                        )
//                        Toast.makeText(context, "Reminder set for $label", Toast.LENGTH_SHORT).show()
//                    }
//                    try {
//                        if (call.state == Call.STATE_RINGING) {
//                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
//                                call.reject(Call.REJECT_REASON_DECLINED)
//                            } else {
//                                call.disconnect()
//                            }
//                        } else {
//                            call.disconnect()
//                        }
//                    } catch (_: Exception) {}
//                    showQuickResponsesSheet = false
//                }
            )
        }

        if (isScreenLocked) {
            CallScreenLockOverlay(
                contactName = contactName,
                phoneNumber = phoneNumber,
                callDuration = callDuration,
                onUnlock = { isScreenLocked = false }
            )
        }
    }

    if (showCallBiometricUnlock) {
        val biometricType = preferenceManager.getString(PreferenceManager.KEY_BIOMETRICS_TYPE, "") ?: ""
        @SuppressLint("ContextCastToActivity")
        val callActivity = LocalContext.current as? FragmentActivity
        fun onBiometricFail() {
            showCallBiometricUnlock = false
            pendingAction = null
        }
        when (biometricType) {
            "system" -> {
                val activity = callActivity ?: run { onBiometricFail(); return }
                AppLockManager.authenticate(
                    activity = activity,
                    title = stringResource(R.string.verify_your_identity_to_access_call),
                    onSuccess = {
                        callBiometricUnlocked = true
                        biometricGatesScreen = false
                        showCallBiometricUnlock = false
                        pendingAction?.invoke(); pendingAction = null
                    },
                    onError = { _, _ -> onBiometricFail()}
                )
            }
            "pin" -> {
                PinSetupDialog(
                    title = stringResource(R.string.enter_pin), isVerify = true,
                    expectedPin = preferenceManager.getString(PreferenceManager.KEY_BIOMETRICS_PIN, "") ?: "",
                    showCloseButton = !biometricGatesScreen,
                    onConfirm = {
                        callBiometricUnlocked = true; biometricGatesScreen = false
                        showCallBiometricUnlock = false
                        pendingAction?.invoke(); pendingAction = null
                    },
                    onDismiss = { onBiometricFail() }
                )
            }
            "password" -> {
                PasswordSetupDialog(
                    title = stringResource(R.string.enter_password), isVerify = true,
                    expectedPassword = preferenceManager.getString(PreferenceManager.KEY_BIOMETRICS_PASSWORD, "") ?: "",
                    showCloseButton = !biometricGatesScreen,
                    onConfirm = {
                        callBiometricUnlocked = true; biometricGatesScreen = false
                        showCallBiometricUnlock = false
                        pendingAction?.invoke(); pendingAction = null
                    },
                    onDismiss = { onBiometricFail() }
                )
            }
        }
    }

    if (showManageSheet) {
        ModalBottomSheet(
            onDismissRequest = { showManageSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = null,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp)
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

                Spacer(Modifier.height(16.dp))
//                Text(
//                    text = stringResource(R.string.manage),
//                    style = MaterialTheme.typography.headlineSmall,
//                    modifier = Modifier.padding(start = 24.dp, bottom = 16.dp)
//                )

                val children = remember(allCalls, call) {
                    allCalls.filter { it.parent != null && it.parent == call }
                }

                if (children.isEmpty()) {
                    Text(
                        text = "No participants",
                        modifier = Modifier.padding(24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    children.forEach { childCall ->
                        val childDetails = childCall.details
                        val childNumber = childDetails?.handle?.schemeSpecificPart ?: ""
                        var childName by remember(childCall) { mutableStateOf(childNumber) }
                        var childPhotoUri by remember(childCall) { mutableStateOf("") }

                        LaunchedEffect(childCall) {
                            val cnam = if (childDetails?.callerDisplayNamePresentation == TelecomManager.PRESENTATION_ALLOWED) {
                                childDetails.callerDisplayName
                            } else null
                            val contact = if (childNumber.isNotEmpty()) try { contactsRepo.getContactByNumber(childNumber) } catch (_: Exception) { null } else null
                            childName = contact?.let { getDisplayName(it, 0) } ?: cnam ?: childNumber.forceLtr()
                            childPhotoUri = contact?.photoUri ?: ""
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RillAvatar(
                                name = childName,
                                photoUri = childPhotoUri,
                                modifier = Modifier.size(40.dp),
                                shape = CircleShape
                            )
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = childName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (childName != childNumber && childNumber.isNotEmpty()) {
                                    Text(
                                        text = childNumber,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            val canSeparate = childCall.hasCapability(Call.Details.CAPABILITY_SEPARATE_FROM_CONFERENCE)
                            if (canSeparate) IconButton(onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                try { childCall.splitFromConference() } catch (_: Exception) {}
                            }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.CallSplit,
                                    contentDescription = "Split",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            val canDisconnect = childCall.hasCapability(Call.Details.CAPABILITY_DISCONNECT_FROM_CONFERENCE)
                            if (canDisconnect) IconButton(onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.REJECT)
                                try { childCall.disconnect() } catch (_: Exception) {}
                            }) {
                                Icon(
                                    imageVector = Icons.Rounded.CallEnd,
                                    contentDescription = stringResource(R.string.end_call),
                                    tint = color_call_end
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}