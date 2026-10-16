package dev.goodwy.rphone.controller

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.telecom.Call
import android.telecom.TelecomManager
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.CallBackgroundStore
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.controller.util.isConference
import dev.goodwy.rphone.liquidglass.LocalLiquidGlassBackdrop
import dev.goodwy.rphone.liquidglass.backdrops.rememberLayerBackdrop
import dev.goodwy.rphone.modal.data.getDisplayName
import dev.goodwy.rphone.modal.`interface`.CallSession
import dev.goodwy.rphone.modal.`interface`.IContactsRepository
import dev.goodwy.rphone.view.screen.ExpressiveCallScreen
import dev.goodwy.rphone.view.theme.Rill4Theme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.android.ext.android.inject
import kotlin.getValue
import kotlin.time.Duration.Companion.milliseconds

private data class CallIdentity(
    val number: String,
    val name: String,
    val photoUri: String?,
    val backgroundUri: String?
)

private data class CachedCallIdentity(
    val identity: CallIdentity,
    val version: Int
)

class CallActivity : FragmentActivity() { //ComponentActivity()

    private val contactsRepo: IContactsRepository by inject()
    private val preferenceManager: PreferenceManager by inject()
    private val callViewModel: CallViewModel by inject()
    private val callStateManager: CallStateManager by inject()
    private var proximityWakeLock: PowerManager.WakeLock? = null
    private val isFinishingCall = java.util.concurrent.atomic.AtomicBoolean(false)
    private var keyguardDismissRequested = false
    private val identityCache = mutableMapOf<String, CachedCallIdentity>()
    private var answeredFromNotificationState by mutableStateOf(false)

    companion object {
        /** FloatingCallService observes this to hide the bubble when CallActivity is visible. */
        val isInForeground = kotlinx.coroutines.flow.MutableStateFlow(false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        isFinishingCall.set(false)
        turnScreenOnAndShowWhileLocked()
        super.onCreate(savedInstanceState)
        handleCallIntent(intent)

        CallBackgroundStore.attach(preferenceManager)

        if (preferenceManager.getBoolean(PreferenceManager.KEY_KEEP_SCREEN_ON, true)) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        setupProximitySensor()
        val themeMode = preferenceManager.getString(PreferenceManager.KEY_THEME_MODE, "auto") ?: "auto"
        applySystemBarStyle(
            themeMode == "dark" || themeMode == "black" ||
                    (themeMode == "auto" && isNightMode()) || (themeMode == "auto_bw" && isNightMode())
        )

        setContent {
            Rill4Theme {
                val session by callViewModel.currentCallSession.collectAsStateWithLifecycle()
                val audioState by callViewModel.audioState.collectAsStateWithLifecycle()
                val settingsState by preferenceManager.settingsChanged.collectAsStateWithLifecycle()
//                val callerMetadata by callViewModel.callerMetadata.collectAsStateWithLifecycle()

                var retainedSession by remember { mutableStateOf<CallSession?>(null) }
                var hasHadCall by remember { mutableStateOf(false) }
                var hasEverBeenActive by remember { mutableStateOf(false) }
                var redialTarget by remember { mutableStateOf<dev.goodwy.rphone.view.components.RedialTarget?>(null) }

                LaunchedEffect(session) {
                    if (session != null) {
                        hasHadCall = true
                        retainedSession = session
                        if (session!!.state != Call.STATE_DISCONNECTED) {
                            redialTarget = null
                        }
                    }
                }

                LaunchedEffect(callViewModel.currentCallSession.value?.state) {
                    val s = callViewModel.currentCallSession.value?.state
                    if (s == Call.STATE_ACTIVE) {
                        hasEverBeenActive = true
                    }
                }

                val displaySession = session ?: retainedSession
                val displayCall = displaySession?.call
                val callState = session?.state

                val identity = if (displayCall != null) {
                    rememberCallIdentity(displayCall, settingsState)
                } else {
                    null
                }

                val darkSystemTheme = isSystemInDarkTheme()
                val darkTheme = themeMode == "dark" || themeMode == "black" ||
                        (themeMode == "auto" && darkSystemTheme) || (themeMode == "auto_bw" && darkSystemTheme)
                val lightSystemBarIcons = darkTheme ||
                        identity?.backgroundUri != null ||
                        identity?.photoUri != null

                DisposableEffect(lightSystemBarIcons) {
                    applySystemBarStyle(lightSystemBarIcons)
                    onDispose { }
                }

                LaunchedEffect(callState, settingsState, audioState) {
                    val keepScreenOn =
                        preferenceManager.getBoolean(PreferenceManager.KEY_KEEP_SCREEN_ON, true)
                    if (keepScreenOn) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                    when (callState) {
                        Call.STATE_ACTIVE -> {
                            if (preferenceManager.getBoolean(PreferenceManager.KEY_VIBRATE_ON_ANSWER, true)) {
                                this@CallActivity.window?.decorView?.performHapticFeedback(
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                        HapticFeedbackConstants.CONFIRM
                                    } else {
                                        HapticFeedbackConstants.VIRTUAL_KEY
                                    }
                                )
                            }
                            if (preferenceManager.getBoolean(PreferenceManager.KEY_PROXIMITY_SENSOR, true)) {
                                acquireProximityLock()
                            } else {
                                releaseProximityLock()
                            }
                        }

                        Call.STATE_DIALING -> {
                            if (preferenceManager.getBoolean(PreferenceManager.KEY_PROXIMITY_SENSOR, true)) {
                                acquireProximityLock()
                            } else {
                                releaseProximityLock()
                            }
                        }

                        Call.STATE_DISCONNECTED -> {
                            if (preferenceManager.getBoolean(PreferenceManager.KEY_VIBRATE_ON_HANGUP, false)) {
                                this@CallActivity.window?.decorView?.performHapticFeedback(
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                        HapticFeedbackConstants.REJECT
                                    } else {
                                        HapticFeedbackConstants.LONG_PRESS
                                    }
                                )
                            }
                            releaseProximityLock()
                            val isUnansweredIncoming = session?.call?.let { c ->
                                (c.details?.callDirection == Call.Details.DIRECTION_INCOMING || c.state == Call.STATE_RINGING) &&
                                        callViewModel.allCalls.value.none { it.state == Call.STATE_ACTIVE }
                            } ?: false

                            val isOutgoing = displayCall?.details?.callDirection == Call.Details.DIRECTION_OUTGOING
                            val isUnansweredOutgoing = isOutgoing && !hasEverBeenActive && (displayCall?.details?.connectTimeMillis ?: 0L) == 0L
                            val hasNoOtherCalls = callViewModel.allCalls.value.none { it != displayCall && it.state != Call.STATE_DISCONNECTED }
                            val redialEnabled = preferenceManager.getBoolean(PreferenceManager.KEY_REDIAL_UNANSWERED, true)

                            if (isUnansweredOutgoing && hasNoOtherCalls && redialEnabled && identity != null && identity.number.isNotBlank()) {
                                val disconnectCauseCode = displayCall?.details?.disconnectCause?.code
                                val reasonText = when (disconnectCauseCode) {
                                    android.telecom.DisconnectCause.BUSY -> getString(R.string.call_status_busy)
                                    android.telecom.DisconnectCause.REJECTED -> getString(R.string.call_status_rejected)
                                    else -> getString(R.string.call_status_unanswered)
                                }
                                redialTarget = dev.goodwy.rphone.view.components.RedialTarget(
                                    name = identity.name,
                                    number = identity.number,
                                    photoUri = identity.photoUri,
                                    backgroundUri = identity.backgroundUri,
                                    phoneAccountHandle = displayCall?.details?.accountHandle,
                                    reason = reasonText
                                )
                            } else {
                                if (isUnansweredIncoming) {
                                    dismissCallScreen()
                                } else {
                                    delay(350.milliseconds)
                                    dismissCallScreen()
                                }
                            }
                        }

                        else -> releaseProximityLock()
                    }

                    if (session == null && redialTarget == null) {
                        if (hasHadCall) {
                            delay(400.milliseconds)
                            if (callViewModel.allCalls.value.none { it.state != Call.STATE_DISCONNECTED }) {
                                dismissCallScreen()
                            }
                        } else {
                            // Waiting for Telecom to deliver onCallAdded. Don't immediately exit and drop back to previous app!
                            delay(5000.milliseconds)
                            if (callViewModel.allCalls.value.none { it.state != Call.STATE_DISCONNECTED }) {
                                dismissCallScreen()
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    AnimatedContent(
                        targetState = displayCall,
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(400)) + scaleIn(initialScale = 0.92f, animationSpec = tween(400)))
                                .togetherWith(fadeOut(animationSpec = tween(300)) + scaleOut(targetScale = 0.95f, animationSpec = tween(300)))
                        },
                        label = "CallSwitch"
                    ) { targetCall ->
                        val answeredFromNotification = answeredFromNotificationState || (intent?.getBooleanExtra("ANSWERED_FROM_NOTIFICATION", false) ?: false)

                        if (targetCall == null) {
                            Box(modifier = Modifier.fillMaxSize())
                        } else {
                            val isDisplayed = targetCall === displayCall
                            val targetIdentity =
                                if (isDisplayed && identity != null) {
                                    identity
                                } else {
                                    rememberCallIdentity(targetCall, settingsState)
                                }
                            val targetState = if (isDisplayed) {
                                session?.state ?: targetCall.state
                            } else {
                                targetCall.state
                            }
                            val connectTime = if (isDisplayed) {
                                displaySession?.connectTimeMillis ?: 0L
                            } else {
                                targetCall.details?.connectTimeMillis ?: 0L
                            }

                            val liquidGlassBackdrop = rememberLayerBackdrop()
                            CompositionLocalProvider(LocalLiquidGlassBackdrop provides liquidGlassBackdrop) {
                                val isConference = targetCall.isConference()
                                ExpressiveCallScreen(
                                    call = targetCall,
                                    callState = targetState,
                                    contactName = targetIdentity.name,
                                    phoneNumber = targetIdentity.number,
                                    photoUri = targetIdentity.photoUri,
                                    audioState = audioState,
                                    initialConnectTime = connectTime,
                                    backgroundUri = targetIdentity.backgroundUri,
                                    skipIncomingScreen = answeredFromNotification,
                                    isConference = isConference,
                                    liquidGlassBackdrop = liquidGlassBackdrop
                                )
                            }
                        }
                    }

                    val targetForRedial = redialTarget
                    if (targetForRedial != null) {
                        val autoCall = preferenceManager.getBoolean(PreferenceManager.KEY_REDIAL_AUTO_CALL, true)
                        dev.goodwy.rphone.view.components.RedialPromptScreen(
                            target = targetForRedial,
                            autoCallEnabled = autoCall,
                            onRedial = { rTarget ->
                                val num = rTarget.number
                                val account = rTarget.phoneAccountHandle
                                redialTarget = null
                                hasEverBeenActive = false
                                dev.goodwy.rphone.controller.util.makeCall(this@CallActivity, num, account)
                            },
                            onDismiss = {
                                redialTarget = null
                                dismissCallScreen()
                            }
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun rememberCallIdentity(call: Call, settingsState: Int): CallIdentity {
        val context = LocalContext.current
        val unknownLabel = stringResource(R.string.label_unknown)
        val details = call.details
        val isConference = call.isConference()

        val number = remember(call, details) {
            if (isConference) "" else details?.handle?.schemeSpecificPart.orEmpty()
        }

        val cnam = remember(call.details?.callerDisplayName, call.details?.callerDisplayNamePresentation) {
            try {
                if (call.details?.callerDisplayNamePresentation == TelecomManager.PRESENTATION_ALLOWED) {
                    call.details?.callerDisplayName?.takeIf { it.isNotBlank() }
                } else null
            } catch (e: Exception) {
                null
            }
        }

        val conference = stringResource(R.string.conference)
        var identity by remember(number, cnam, unknownLabel) {
            val base = if (isConference) {
                CallIdentity("", conference, null, null)
            } else {
                val cached = cachedIdentity(number, settingsState)
                if (cached != null) {
                    cached
                } else {
                    val metadata = callStateManager.getMetadata(number)
                    if (metadata != null) {
                        CallIdentity(
                            number = number,
                            name = metadata.name.ifBlank { cnam ?: number.ifEmpty { unknownLabel } },
                            photoUri = metadata.photoUri,
                            backgroundUri = null
                        )
                    } else {
                        CallIdentity(number, cnam ?: number.ifEmpty { unknownLabel }, null, null)
                    }
                }
            }
            mutableStateOf(
                if (base.backgroundUri == null) {
                    base.copy(backgroundUri = CallBackgroundStore.defaultModel(context))
                } else {
                    base
                }
            )
        }

        if (isConference) {
            return identity
        }

        LaunchedEffect(number) {
            if (number.isNotEmpty()) {
                callStateManager.callerMetadataMap.collect { map ->
                    val meta = map[number] ?: return@collect
                    if (meta.name.isNotBlank() && meta.name != number) {
                        identity = identity.copy(
                            name = meta.name,
                            photoUri = meta.photoUri ?: identity.photoUri
                        )
                    }
                }
            }
        }

        val displayOrder = preferenceManager.getInt(PreferenceManager.KEY_CONTACT_DISPLAY_ORDER, 0)

        LaunchedEffect(number, cnam, settingsState, displayOrder) {
            val handle = number.ifEmpty { null }

            val handleResult = CallBackgroundStore.resolveResult(context, handle, null)
            val handleBackground = handleResult.uri
            if (handleBackground != null && handleBackground != identity.backgroundUri) {
                identity = identity.copy(backgroundUri = handleBackground)
            }

            val lookup = if (handle == null) {
                null
            } else {
                withContext(Dispatchers.IO) {
                    runCatching { contactsRepo.getContactByNumber(number) }
                }
            }
            val contact = lookup?.getOrNull()
            val contactFailed = lookup?.isFailure == true

            val contactId = contact?.id?.takeIf { it.isNotBlank() }
            val idResult = if (handleBackground == null && contactId != null) {
                CallBackgroundStore.resolveResult(context, handle, contactId)
            } else {
                null
            }

            val resolvedBackground = handleBackground ?: idResult?.uri
            val resolveFailed =
                handleResult.failed || idResult?.failed == true || contactFailed
            val defaultBackground = CallBackgroundStore.defaultModelAsync(context)
            val background = resolvedBackground
                ?: identity.backgroundUri.takeIf { resolveFailed }
                ?: defaultBackground

            val resolved = CallIdentity(
                number = number,
                name = contact?.let { getDisplayName(it, displayOrder) }
                    ?.takeIf { it.isNotBlank() }
                    ?: cnam
                    ?: identity.name.takeIf { contactFailed && it.isNotBlank() }
                    ?: number.ifEmpty { unknownLabel },
                photoUri = contact?.photoUri ?: identity.photoUri.takeIf { contactFailed },
                backgroundUri = background
            )
            cacheIdentity(number, resolved, settingsState)
            identity = resolved
        }

        return identity
    }

    private fun cachedIdentity(number: String, version: Int): CallIdentity? {
        if (number.isEmpty()) return null
        val cached = identityCache[number] ?: return null
        if (cached.version != version) return null
        return cached.identity
    }

    private fun cacheIdentity(number: String, identity: CallIdentity, version: Int) {
        identityCache.entries.removeAll { it.value.version != version }
        if (number.isEmpty()) return
        identityCache[number] = CachedCallIdentity(identity, version)
    }

    private fun isNightMode(): Boolean {
        return (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
    }

    private fun applySystemBarStyle(lightIcons: Boolean) {
        val style = if (lightIcons) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    override fun onResume() {
        super.onResume()
        isInForeground.value = true
        isFinishingCall.set(false)
        if (callViewModel.allCalls.value.any { it.state != Call.STATE_DISCONNECTED }) {
            turnScreenOnAndShowWhileLocked()
        }
        val state = callViewModel.currentCallSession.value?.state
        if (state == Call.STATE_ACTIVE || state == Call.STATE_DIALING) {
            if (preferenceManager.getBoolean(PreferenceManager.KEY_PROXIMITY_SENSOR, true)) {
                acquireProximityLock()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        isInForeground.value = false
    }

    private fun setupProximitySensor() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        if (powerManager.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
            proximityWakeLock = powerManager.newWakeLock(
                PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                "RillPhoneApp::ProximityWakeLock"
            )
        }
    }

    override fun onDestroy() {
        releaseProximityLock()
        proximityWakeLock = null
        isFinishingCall.set(false)
        callViewModel.setIsActivityVisible(false)
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        isFinishingCall.set(false)
        handleCallIntent(intent)
        turnScreenOnAndShowWhileLocked()
    }

    private fun dismissCallScreen() {
        if (isFinishingCall.getAndSet(true)) return

        if (callViewModel.allCalls.value.any { it.state != Call.STATE_DISCONNECTED }) {
            isFinishingCall.set(false)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(false)
            setTurnScreenOn(false)
        }
        @Suppress("DEPRECATION")
        window.clearFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
        )

        finishAndRemoveTask()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        turnScreenOnAndShowWhileLocked()
    }

    private fun turnScreenOnAndShowWhileLocked() {
        isFinishingCall.set(false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
        )

        try {
            val powerManager = getSystemService(POWER_SERVICE) as? PowerManager
            if (powerManager != null) {
                @Suppress("DEPRECATION")
                val wakeLock = powerManager.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                    "RillPhone:IncomingCallWakeLock"
                )
                wakeLock.acquire(5000L)
            }
        } catch (e: Exception) {
            Log.w("CallActivity", "Failed to acquire screen wake lock", e)
        }
    }

    private fun handleCallIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        val autoAnswer = intent.getBooleanExtra("AUTO_ANSWER_CALL", false) ||
                intent.getBooleanExtra("ANSWERED_FROM_NOTIFICATION", false) ||
                action == "ACTION_ANSWER"
        if (autoAnswer) {
            answeredFromNotificationState = true
            lifecycleScope.launch {
                withTimeoutOrNull(2000L) {
                    callViewModel.allCalls.first { list ->
                        list.any { it.state == Call.STATE_RINGING || it.state == Call.STATE_ACTIVE }
                    }
                }
                callViewModel.answerCall()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        isInForeground.value = true
        callViewModel.setIsActivityVisible(true)
        turnScreenOnAndShowWhileLocked()
    }

    override fun onStop() {
        super.onStop()
        isInForeground.value = false
        if (proximityWakeLock?.isHeld != true) {
            callViewModel.setIsActivityVisible(false)
        }
    }

    private fun acquireProximityLock() {
        val route = callViewModel.audioState.value?.route
        val isHandsFree = route == android.telecom.CallAudioState.ROUTE_SPEAKER ||
                route == android.telecom.CallAudioState.ROUTE_BLUETOOTH ||
                route == android.telecom.CallAudioState.ROUTE_WIRED_HEADSET

        if (!isHandsFree && preferenceManager.getBoolean(PreferenceManager.KEY_PROXIMITY_SENSOR, true)) {
            try {
                proximityWakeLock?.let { if (!it.isHeld) it.acquire(60*60*1000L /* 1 hour */) }
            } catch (e: Exception) {
                Log.e("CallActivity", "Failed to acquire proximity lock", e)
            }
        } else {
            releaseProximityLock()
        }
    }

    private fun releaseProximityLock() {
        try {
            proximityWakeLock?.let { if (it.isHeld) it.release() }
        } catch (e: Exception) {
            Log.e("CallActivity", "Failed to release proximity lock", e)
        }
    }
}