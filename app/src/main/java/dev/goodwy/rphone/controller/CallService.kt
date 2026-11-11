package dev.goodwy.rphone.controller

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Bundle
import android.telecom.Connection
import android.telecom.PhoneAccountHandle
import android.telephony.ServiceState
import android.telephony.TelephonyManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.net.Uri
import android.os.Build
import android.provider.BlockedNumberContract
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.DisconnectCause
import android.telecom.InCallService
import android.telecom.TelecomManager
import android.widget.Toast
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.controller.util.bluetooth.BluetoothCallAudioHelper
import dev.goodwy.rphone.controller.util.toast
import dev.goodwy.rphone.modal.`interface`.CallSession
import dev.goodwy.rphone.modal.`interface`.ICallRepository
import dev.goodwy.rphone.modal.repository.CallRepositoryImpl
import dev.goodwy.rphone.view.screen.BiometricCallActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.isActive
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.android.ext.android.inject
import kotlin.getValue
import kotlin.time.Duration.Companion.milliseconds

class CallService : InCallService() {

    private val preferenceManager: PreferenceManager by inject()
    private val callStateManager: CallStateManager by inject()
    private val notificationManager: CallNotificationManager by inject()
    private val callRepository: ICallRepository by inject()

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var redialCount = 0
    // Number currently being auto-redialed. Used so a redial-initiated call doesn't reset the counter.
    private var redialNumber: String? = null
    private var redialJob: Job? = null
    private val callStartTimes = mutableMapOf<Call, Long>()
    private var lastFloatingCallMetadata: Triple<String, String, String?>? = null
    private var notificationUpdateJob: Job? = null

    // Incoming calls still being processed by a call-screening app (STATE_NEW / STATE_AUDIO_PROCESSING)
    private val awaitingRingCalls = mutableSetOf<Call>()
    // Incoming calls whose block status is still being checked
    private val blockCheckCalls = mutableSetOf<Call>()
    // Blocked calls handled with the "silent" method: they keep ringing silently but must stay hidden
    private val silentBlockedCalls = mutableSetOf<Call>()
    // Last known Wi-Fi calling state per call, to detect Wi-Fi <-> mobile handovers
    private val wifiCallState = mutableMapOf<Call, Boolean>()

    private fun isSuppressed(call: Call) =
        call in awaitingRingCalls || call in blockCheckCalls || call in silentBlockedCalls

    private fun isRingingState(state: Int) =
        state == Call.STATE_RINGING || state == Call.STATE_SIMULATED_RINGING

    private fun isIncomingCall(call: Call) =
        call.details.callDirection == Call.Details.DIRECTION_INCOMING || isRingingState(call.state)

    private fun numberOf(call: Call): String =
        call.details?.handle?.schemeSpecificPart?.let { Uri.decode(it) } ?: ""

    // BroadcastReceiver for monitoring when the screen is locked or turned off
    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF,
                Intent.ACTION_SCREEN_ON,
                Intent.ACTION_USER_PRESENT -> {
                    callRepository.currentCallSession.value?.call?.let { currentCall ->
                        updateNotification(currentCall)
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        BluetoothCallAudioHelper.init(this)
        (callRepository as? CallRepositoryImpl)?.bindService(this)

        // Register the receiver to track screen locks
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT) // Screen Unlocked
        }
        registerReceiver(screenStateReceiver, filter)

        serviceScope.launch {
            combine(
                callRepository.isActivityVisible,
                CallActivity.isInForeground
            ) { isVisible, inForeground ->
                isVisible || inForeground
            }.distinctUntilChanged().collect { isUiVisible ->
                val currentCall = callRepository.currentCallSession.value?.call
                val isRinging = currentCall?.let { isRingingState(it.state) }
                    ?: callRepository.allCalls.value.any { isRingingState(it.state) }

                if (isRinging) {
                    if (isUiVisible) {
                        notificationUpdateJob?.cancel()
                        removeForeground()
                        cancelNotification()
                    } else if (currentCall != null) {
                        updateNotification(currentCall)
                    }
                } else if (currentCall != null) {
                    updateNotification(currentCall)
                }
            }
        }
        serviceScope.launch {
            var lastMetadata: dev.goodwy.rphone.modal.data.CallerMetadata? = null
            callStateManager.callerMetadataMap.collect { map ->
                val currentCall = callRepository.currentCallSession.value?.call ?: return@collect
                val number = currentCall.details?.handle?.schemeSpecificPart?.let { Uri.decode(it) } ?: ""
                val currentMeta = map[number]
                if (currentMeta != null && currentMeta != lastMetadata) {
                    lastMetadata = currentMeta
                    updateNotification(currentCall)
                }
            }
        }
    }

    private val callCallback = object : Call.Callback() {
        override fun onConnectionEvent(call: Call, event: String, extras: android.os.Bundle?) {
            super.onConnectionEvent(call, event, extras)
            val number = call.details?.handle?.schemeSpecificPart?.let { android.net.Uri.decode(it) } ?: ""
            if (isUssdNumber(number)) {
                val resp = extras?.let { b ->
                    b.getString("ussdResult") ?: b.getString("android.telecom.extra.ussd_message")
                    ?: b.getString("android.telephony.extra.USSD_RESPONSE")
                    ?: b.getString("response") ?: b.getString("result") ?: b.getString("data") ?: b.getString("message")
                }
                if (!resp.isNullOrBlank()) UssdRepository.post(number, resp)
                return
            }

            val messageRes = when (event) {
                Connection.EVENT_CALL_HOLD_FAILED -> R.string.call_event_hold_failed
                Connection.EVENT_CALL_MERGE_FAILED -> R.string.call_event_merge_failed
                Connection.EVENT_CALL_REMOTELY_HELD -> R.string.call_event_remotely_held
                Connection.EVENT_CALL_REMOTELY_UNHELD -> R.string.call_event_remotely_unheld
                EVENT_CALL_SWITCH_FAILED -> R.string.call_event_switch_failed
                else -> null
            }
            if (messageRes != null && !isSuppressed(call)) {
                toast(getString(messageRes))
            }
        }

        override fun onStateChanged(call: Call, state: Int) {
            super.onStateChanged(call, state)

            // A screened incoming call has finished audio processing
            if (call in awaitingRingCalls && state != Call.STATE_NEW && state != Call.STATE_AUDIO_PROCESSING) {
                awaitingRingCalls.remove(call)
                if (isRingingState(state)) {
                    handleIncomingCall(call, numberOf(call))
                    return
                }
            }

            updateCallState()

            if (state == Call.STATE_ACTIVE) {
                redialCount = 0
                redialNumber = null
                BluetoothCallAudioHelper.onCallActive(this@CallService)
            } else if (state == Call.STATE_DIALING || state == Call.STATE_CONNECTING) {
                BluetoothCallAudioHelper.onCallActive(this@CallService)
            }

            if (state == Call.STATE_DISCONNECTED) {
                val cause = call.details.disconnectCause
                handleDisconnect(call, cause)

                val remaining = calls?.filter { it.state != Call.STATE_DISCONNECTED && !isSuppressed(it) } ?: emptyList()
                if (remaining.isEmpty()) {
                    BluetoothCallAudioHelper.onCallEnded()
                    serviceScope.launch {
                        delay(150.milliseconds)
                        removeForeground()
                        cancelNotification()
                    }
                }
            } else {
                updateNotification(call)
            }
        }

        override fun onDetailsChanged(call: Call, details: Call.Details) {
            super.onDetailsChanged(call, details)
            val number = details.handle?.schemeSpecificPart?.let { android.net.Uri.decode(it) } ?: ""
            val cnam = if (details.callerDisplayNamePresentation == TelecomManager.PRESENTATION_ALLOWED) {
                details.callerDisplayName
            } else null
            callStateManager.onNewCallReceived(number, cnam)

            // Notify on Wi-Fi calling <-> mobile network handover during an active call
            val isWifi = details.hasProperty(Call.Details.PROPERTY_WIFI)
            val wasWifi = wifiCallState.put(call, isWifi)
            if (wasWifi != null && wasWifi != isWifi && call.state == Call.STATE_ACTIVE && !isSuppressed(call)) {
                toast(getString(if (isWifi) R.string.call_switched_to_wifi else R.string.call_switched_to_mobile))
            }

            updateCallState()
            if (call !in awaitingRingCalls) updateNotification(call)
        }
    }

    private fun handleDisconnect(call: Call, cause: DisconnectCause?) {
        val number = numberOf(call)

        // Need to create a Receiver (android.telecom.action.SHOW_MISSED_CALLS_NOTIFICATION) to prevent the system notification from being duplicated
        val wasNeverConnected = call.details.connectTimeMillis == 0L
        val isIncoming = call.details.callDirection == Call.Details.DIRECTION_INCOMING
        val isOutgoing = call.details.callDirection == Call.Details.DIRECTION_OUTGOING

        // Auto Redial on Busy
        if (isOutgoing && cause?.code == DisconnectCause.BUSY &&
            preferenceManager.getBoolean(PreferenceManager.KEY_AUTO_REDIAL_BUSY, false)) {

            val maxAttempts = preferenceManager.getInt(PreferenceManager.KEY_REDIAL_ATTEMPTS, 3)
            val delayMs = preferenceManager.getInt(PreferenceManager.KEY_REDIAL_DELAY, 3000).toLong()

            if (redialNumber != number) {
                redialNumber = number
                redialCount = 0
            }

            if (redialCount < maxAttempts) {
                redialCount++
                val accountHandle = call.details.accountHandle
                redialJob?.cancel()
                redialJob = serviceScope.launch {
                    delay(delayMs.milliseconds)
                    placeRedialCall(number, accountHandle)
                }
            } else {
                redialNumber = null
                redialCount = 0
            }
        }

        if (isOutgoing && wasNeverConnected) {
            val failMessage = when {
                dev.goodwy.rphone.controller.util.isAirplaneModeOn(this) ->
                    getString(R.string.call_failed_airplane_mode)
                cause == null -> null
                // Normal endings: user/remote hung up, busy, etc. — nothing to report
                cause.code in NORMAL_DISCONNECT_CODES -> null
                cause.code == DisconnectCause.RESTRICTED ->
                    getString(R.string.call_failed_restricted)
                isCellularOutOfService(call.details.accountHandle) ->
                    getString(R.string.call_failed_no_network)
                else ->
                    cause.description?.toString()?.takeIf { it.isNotBlank() }
                        ?: cause.label?.toString()?.takeIf { it.isNotBlank() }
                        ?: if (cause.code == DisconnectCause.ERROR || cause.code == DisconnectCause.OTHER) {
                            getString(R.string.call_failed_generic)
                        } else null
            }
            if (failMessage != null) {
                serviceScope.launch(Dispatchers.Main) {
                    toast(failMessage, Toast.LENGTH_LONG)
                }
            }
        }

        // REJECTED is excluded on purpose: the user declined the call, so it isn't "missed"
        if (isIncoming && wasNeverConnected && (cause?.code == DisconnectCause.MISSED || cause?.code == DisconnectCause.REMOTE)) {
            serviceScope.launch {
                val isBlocked = isNumberBlocked(number)
                val showBlocked = preferenceManager.getInt(PreferenceManager.KEY_BLOCK_LOG_VISIBILITY, 0) == 1
                if (!isBlocked || showBlocked) {
//                    val contactName = getContactNameFromCache(number)
//                    val photoUri = getContactPhotoFromCache(number)
//                    notificationManager.showMissedCallNotification(call, contactName, photoUri)

                    val cachedMeta = callStateManager.getMetadata(number)
                    val metadata = cachedMeta ?: withTimeoutOrNull(1000L.milliseconds) {
                        callStateManager.onNewCallReceived(number, null)
                        callStateManager.callerMetadataMap.first { map ->
                            map.containsKey(number)
                        }[number]
                    }
                    val contactName = metadata?.name?.takeIf { it.isNotEmpty() } ?: number
                    val photoUri = metadata?.photoUri
                    notificationManager.showMissedCallNotification(call, contactName, photoUri)
                }
            }
        }
    }

    private suspend fun isNumberBlocked(number: String): Boolean = withContext(Dispatchers.IO) {
        if (number.isBlank()) return@withContext false
        if (!dev.goodwy.rphone.controller.util.BlockedNumbersManager.canBlockNumbers(this@CallService)) return@withContext false
        return@withContext try {
            BlockedNumberContract.isBlocked(this@CallService, number)
        } catch (_: Exception) {
            false
        }
    }

    @SuppressLint("MissingPermission")
    private fun placeRedialCall(number: String, accountHandle: PhoneAccountHandle?) {
        if (number.isBlank()) return
        if (checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) return
        val telecomManager = getSystemService(TelecomManager::class.java) ?: return
        val extras = Bundle().apply {
            // Redial through the same SIM that got the busy signal
            accountHandle?.let { putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, it) }
        }
        try {
            telecomManager.placeCall(Uri.fromParts("tel", number, null), extras)
        } catch (_: Exception) {
            redialNumber = null
            redialCount = 0
        }
    }

    /** True when the cellular radio for [accountHandle] has no service and there's no Wi-Fi to fall back on. */
    @SuppressLint("MissingPermission")
    private fun isCellularOutOfService(accountHandle: PhoneAccountHandle?): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) return false
        return try {
            val baseTm = getSystemService(TelephonyManager::class.java) ?: return false
            val tm = accountHandle?.let { baseTm.createForPhoneAccountHandle(it) } ?: baseTm
            val state = tm.serviceState?.state ?: return false
            (state == ServiceState.STATE_OUT_OF_SERVICE || state == ServiceState.STATE_POWER_OFF) &&
                !dev.goodwy.rphone.controller.util.isWifiConnected(this)
        } catch (_: Exception) {
            false
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleBlockedCall(call: Call, number: String) {
        val method = preferenceManager.getInt(PreferenceManager.KEY_BLOCK_METHOD, 0) // 0: Decline, 1: Silent

        if (method == 0) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                call.reject(Call.REJECT_REASON_DECLINED)
            } else {
                call.disconnect()
            }
        } else {
            // Keep the call hidden and stop the ringtone that Telecom is playing
            silentBlockedCalls.add(call)
            try {
                getSystemService(TelecomManager::class.java)?.silenceRinger()
            } catch (_: Exception) {}
        }

        if (preferenceManager.getBoolean(PreferenceManager.KEY_BLOCK_NOTIFICATION, true)) {
            notificationManager.showBlockedNotification(number)
        }
    }

    private fun getContactNameFromCache(number: String): String {
        if (number.isEmpty()) return getString(R.string.label_unknown_number)
        val metadata = callStateManager.callerMetadataMap.value[number]
        return metadata?.name?.takeIf { it.isNotEmpty() } ?: number
    }

    private fun getContactPhotoFromCache(number: String): String? {
        val metadata = callStateManager.callerMetadataMap.value[number]
        return metadata?.photoUri
    }

    private fun updateCallState() {
        // Hidden calls (being screened, block-checked, or silently blocked) are not exposed to the UI
        val callsList = (calls ?: emptyList()).filter { !isSuppressed(it) }
        callRepository.updateAllCalls(callsList)

        callsList.forEach { c ->
            if (c.state == Call.STATE_ACTIVE) {
                val detailsTime = c.details.connectTimeMillis
                if (detailsTime > 0) {
                    callStartTimes[c] = detailsTime
                } else if (!callStartTimes.containsKey(c)) {
                    callStartTimes[c] = System.currentTimeMillis()
                }
            }
        }
        callStartTimes.keys.retainAll(callsList.toSet())

        val preferred = callRepository.getPreferredCall()
        if (preferred != null && (preferred !in callsList || preferred.state == Call.STATE_DISCONNECTED)) {
            callRepository.setPreferredCall(null)
        }

        val currentPreferred = callRepository.getPreferredCall()
        val activePreferred = if (currentPreferred != null && currentPreferred.state != Call.STATE_DISCONNECTED && currentPreferred.state != Call.STATE_HOLDING) currentPreferred else null

        val priorityCall = callsList.find { isRingingState(it.state) }
            ?: activePreferred
            ?: callsList.find { it.details.hasProperty(Call.Details.PROPERTY_CONFERENCE) && it.state != Call.STATE_DISCONNECTED }
            ?: callsList.find { it.state == Call.STATE_DIALING || it.state == Call.STATE_CONNECTING }
            ?: callsList.find { it.state == Call.STATE_ACTIVE }
            ?: callsList.find { it == preferred }
            ?: callsList.find { it.state == Call.STATE_HOLDING }
            ?: callsList.firstOrNull { it.state != Call.STATE_DISCONNECTED }

        if (priorityCall != null) {
            val connectTime = callStartTimes[priorityCall] ?: 0L
            callRepository.updateCurrentCallSession(CallSession(priorityCall, priorityCall.state, connectTimeMillis = connectTime))
        } else {
            val current = callRepository.currentCallSession.value
            if (current != null && current.state != Call.STATE_DISCONNECTED) {
                callRepository.updateCurrentCallSession(CallSession(current.call, Call.STATE_DISCONNECTED, connectTimeMillis = current.connectTimeMillis))
            } else if (current == null) {
                callRepository.updateCurrentCallSession(null)
            }
        }
    }

    private fun isDeviceLocked(): Boolean {
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isLocked = keyguardManager?.isKeyguardLocked == true || keyguardManager?.isDeviceLocked == true
        val isScreenOff = powerManager?.isInteractive == false
        return isLocked || isScreenOff
    }

    private fun updateNotification(requestedCall: Call, forcedHigh: Boolean? = null) {
        // Never show a notification for a hidden call; fall back to the visible current call if any
        val call = if (!isSuppressed(requestedCall)) requestedCall else {
            callRepository.currentCallSession.value?.call
                ?.takeIf { !isSuppressed(it) && it.state != Call.STATE_DISCONNECTED }
                ?: return
        }

        val isRinging = isRingingState(call.state)
        val isUiVisible = callRepository.isActivityVisible.value || CallActivity.isInForeground.value

        if (isRinging && isUiVisible) {
            notificationUpdateJob?.cancel()
            removeForeground()
            cancelNotification()
            return
        }

        notificationUpdateJob?.cancel()
        notificationUpdateJob = serviceScope.launch {
            val handle = call.details.handle
            val number = handle?.schemeSpecificPart ?: ""
            val contactName = getContactNameFromCache(number)
            val photoUri = getContactPhotoFromCache(number)

            // Don't hold the (ringing) notification back for a slow photo decode:
            // wait briefly, then post without the photo and update once it's ready.
            val photoDeferred = if (photoUri.isNullOrBlank()) null else serviceScope.async {
                notificationManager.getContactBitmap(photoUri)
            }
            val quickPhoto = photoDeferred?.let { withTimeoutOrNull(PHOTO_LOAD_TIMEOUT_MS.milliseconds) { it.await() } }

            if (!isActive) return@launch
            if (isRinging && (callRepository.isActivityVisible.value || CallActivity.isInForeground.value)) {
                removeForeground()
                cancelNotification()
                return@launch
            }
            postCallNotification(call, contactName, quickPhoto, forcedHigh)

            // Start/stop floating bubble based on preference
            if (call.state != Call.STATE_DISCONNECTED && call.state != Call.STATE_DISCONNECTING) {
                maybeStartFloatingCall(contactName, number, photoUri)
            }

            if (quickPhoto == null && photoDeferred != null) {
                val photo = photoDeferred.await()
                if (photo != null && isActive && call.state != Call.STATE_DISCONNECTED) {
                    if (isRinging && (callRepository.isActivityVisible.value || CallActivity.isInForeground.value)) {
                        removeForeground()
                        cancelNotification()
                        return@launch
                    }
                    postCallNotification(call, contactName, photo, forcedHigh)
                }
            }
        }
    }

    private fun postCallNotification(call: Call, contactName: String, contactPhoto: android.graphics.Bitmap?, forcedHigh: Boolean?) {
        val isHigh = forcedHigh ?: isDeviceLocked()
        val notification = notificationManager.buildCallNotification(
            call,
            contactName,
            contactPhoto,
            callRepository.audioState.value,
            isHigh
        )
        startForeground(
            CallNotificationManager.NOTIFICATION_ID,
            notification,
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
        )
    }

    private fun removeForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun launchBiometricCallActivity(action: String) {
        val intent = Intent(this, BiometricCallActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("NOTIFICATION_PENDING_ACTION", action)
        }
        val options = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ActivityOptions.makeBasic().apply {
                setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
            }.toBundle()
        } else null
        try {
            startActivity(intent, options)
        } catch (_: Exception) {
            try { startActivity(intent) } catch (_: Exception) {}
        }
    }

    /**
     * Telecom calls this when it needs the default dialer's in-call UI to be brought
     * forward. This is especially important while the device is locked, where a
     * regular background activity launch may otherwise be deferred by the system.
     */
    override fun onBringToForeground(showDialpad: Boolean) {
        super.onBringToForeground(showDialpad)
        if (callRepository.allCalls.value.any { it.state != Call.STATE_DISCONNECTED }) {
            wakeUpScreen()
            showCallActivity()
        }
    }

    private fun showCallActivity() {
        val intent = Intent(this, CallActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val options = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ActivityOptions.makeBasic().apply {
                setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
            }.toBundle()
        } else null
        try {
            startActivity(intent, options)
        } catch (_: Exception) {
            try { startActivity(intent) } catch (_: Exception) {}
        }
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallActivity.userDismissedCallScreen = false
        call.registerCallback(callCallback)
        callAudioState?.let { callRepository.updateAudioState(it) }
        try { setMuted(false) } catch (_: Exception) {}
        BluetoothCallAudioHelper.onCallAdded(this, call)

        val number = numberOf(call)
        val cnam = if (call.details.callerDisplayNamePresentation == TelecomManager.PRESENTATION_ALLOWED) {
            call.details.callerDisplayName
        } else null

        callStateManager.onNewCallReceived(number, cnam)
        wifiCallState[call] = call.details.hasProperty(Call.Details.PROPERTY_WIFI)

        // Use the call direction, not only STATE_RINGING: screened calls arrive as
        // STATE_NEW / STATE_AUDIO_PROCESSING / STATE_SIMULATED_RINGING.
        val isIncoming = isIncomingCall(call)

        // ── USSD / MMI outgoing calls ────────────────────────────────────────
        if (!isIncoming && isUssdNumber(number)) return
        // ────────────────────────────────────────────────────────────────────

        if (isIncoming) {
            if (isRingingState(call.state)) {
                handleIncomingCall(call, number)
            } else {
                // Still being processed by a call-screening app: wait until it actually rings
                awaitingRingCalls.add(call)
                updateCallState()
            }
            return
        }

        // A new outgoing call that isn't our own auto-redial cancels any pending redial
        if (number != redialNumber) {
            redialJob?.cancel()
            redialNumber = null
            redialCount = 0
        }

        // Update call state synchronously so CallActivity sees active calls immediately
        updateCallState()

        // Outgoing call: display CallActivity immediately with zero blocking or delay!
        updateNotification(call)
        val intent = Intent(this@CallService, CallActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val options = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ActivityOptions.makeBasic().apply {
                setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
            }.toBundle()
        } else null
        try {
            startActivity(intent, options)
        } catch (_: Exception) {
            try { startActivity(intent) } catch (_: Exception) {}
        }
    }

    private fun wakeUpScreen() {
        try {
            val powerManager = getSystemService(POWER_SERVICE) as? PowerManager
            if (powerManager != null) {
                @Suppress("DEPRECATION")
                val wakeLock = powerManager.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                    "RillPhone:IncomingCallServiceWakeLock"
                )
                wakeLock.acquire(10000L)
            }
        } catch (e: Exception) {
            android.util.Log.w("CallService", "Failed to acquire wake lock in CallService", e)
        }
    }

    private fun handleIncomingCall(call: Call, number: String) {
        val wasLocked = isDeviceLocked()
        wakeUpScreen()
        // Keep the call hidden until we know it isn't blocked, so no notification flashes for it
        blockCheckCalls.add(call)
        updateCallState()

        serviceScope.launch {
            val blocked = isNumberBlocked(number)
            blockCheckCalls.remove(call)

            if (blocked) {
                handleBlockedCall(call, number)
                updateCallState()
                return@launch
            }

            updateCallState()
            if (call.state == Call.STATE_DISCONNECTED) return@launch

            val fullscreenCalls = preferenceManager.getBoolean(PreferenceManager.KEY_ALWAYS_FULLSCREEN_CALLS, false)
            val isLockedNow = isDeviceLocked()
            val canUseFullScreenIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                nm?.canUseFullScreenIntent() ?: true
            } else true

            val canAlert = notificationManager.canShowIncomingCallNotification() && canUseFullScreenIntent
            val shouldShowActivity = !CallActivity.userDismissedCallScreen && (fullscreenCalls || wasLocked || isLockedNow || !canAlert)

            if (shouldShowActivity) {
                showCallActivity()
            } else {
                updateNotification(call)
            }
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        CallActivity.userDismissedCallScreen = false
        call.unregisterCallback(callCallback)
        awaitingRingCalls.remove(call)
        blockCheckCalls.remove(call)
        silentBlockedCalls.remove(call)
        wifiCallState.remove(call)

        // If the call being deleted is the same one for which a floating window was launched,
        // we must clear the cache to allow it to be launched for the next call.
        val number = call.details?.handle?.schemeSpecificPart ?: ""
        val currentMetadata = lastFloatingCallMetadata
        if (currentMetadata != null && currentMetadata.second == number) {
            lastFloatingCallMetadata = null
        }

        callStateManager.onCallEnded(number)
        updateCallState()

        val callsList = callRepository.allCalls.value
        if (callsList.isEmpty()) {
            serviceScope.launch {
                delay(100.milliseconds)
                callRepository.updateCurrentCallSession(null)
                removeForeground()
                cancelNotification()
            }
        } else {
            callRepository.currentCallSession.value?.call?.let { updateNotification(it) }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        super.onCallAudioStateChanged(audioState)
        callRepository.updateAudioState(audioState)
        BluetoothCallAudioHelper.onCallAudioStateChanged(this, audioState)
        callRepository.currentCallSession.value?.call?.let { updateNotification(it) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "ANSWER_CALL" -> {
                val phoneNumber = callRepository.currentCallSession.value?.call?.details?.handle?.schemeSpecificPart
                if (preferenceManager.shouldGateCallWithBiometric(phoneNumber)) {
                    launchBiometricCallActivity("ANSWER")
                } else {
                    callRepository.answerCall()
                    val intent = Intent(this, CallActivity::class.java).apply {
                        action = "ACTION_ANSWER"
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        putExtra("ANSWERED_FROM_NOTIFICATION", true)
                        putExtra("AUTO_ANSWER_CALL", true)
                    }
                    val options = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        ActivityOptions.makeBasic().apply {
                            setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                        }.toBundle()
                    } else null
                    try {
                        startActivity(intent, options)
                    } catch (_: Exception) {
                        try { startActivity(intent) } catch (_: Exception) {}
                    }
                }
            }
            "DECLINE_CALL" -> {
                val phoneNumber = callRepository.currentCallSession.value?.call?.details?.handle?.schemeSpecificPart
                if (preferenceManager.shouldGateCallWithBiometric(phoneNumber)) {
                    launchBiometricCallActivity("DECLINE")
                } else {
                    callRepository.declineCall()
                }
            }
            "TOGGLE_MUTE" -> callRepository.toggleMute()
            "TOGGLE_SPEAKER" -> callRepository.cycleAudioRoute()
            "NOTES_CALL"   -> {
                val name   = intent.getStringExtra("contact_name") ?: "Unknown"
                val number = intent.getStringExtra("phone_number") ?: ""
                if (android.provider.Settings.canDrawOverlays(this)) {
                    FloatingNotesService.start(this, name, number)
                }
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun cancelNotification() {
        notificationManager.cancelNotification()
    }

    override fun onDestroy() {
        super.onDestroy()
        BluetoothCallAudioHelper.onCallEnded()

        try {
            unregisterReceiver(screenStateReceiver)
        } catch (_: Exception) {}

        (callRepository as? CallRepositoryImpl)?.unbindService()
        serviceScope.cancel()
    }

    private fun maybeStartFloatingCall(contactName: String, number: String, photoUri: String?) {
        if (!preferenceManager.getBoolean(PreferenceManager.KEY_FLOATING_CALL, false)) return
        if (!android.provider.Settings.canDrawOverlays(this)) return

        val metadata = Triple(contactName, number, photoUri)
        if (lastFloatingCallMetadata == metadata) return
        lastFloatingCallMetadata = metadata

        FloatingCallService.start(this, contactName, number, photoUri)
    }

    private companion object {
        const val PHOTO_LOAD_TIMEOUT_MS = 300L

        // Connection.EVENT_CALL_SWITCH_FAILED (API 30); literal so it's safe on API 29
        const val EVENT_CALL_SWITCH_FAILED = "android.telecom.event.CALL_SWITCH_FAILED"

        val NORMAL_DISCONNECT_CODES = setOf(
            DisconnectCause.LOCAL,
            DisconnectCause.REMOTE,
            DisconnectCause.CANCELED,
            DisconnectCause.BUSY,
            DisconnectCause.MISSED,
            DisconnectCause.REJECTED,
            DisconnectCause.ANSWERED_ELSEWHERE,
            DisconnectCause.CALL_PULLED
        )
    }

    /** Returns true for any MMI / USSD code like *124# *#06# ##002# *21*N# */
    private fun isUssdNumber(number: String): Boolean {
        if (number.isBlank()) return false
        val n = android.net.Uri.decode(number).trim()
        return (n.startsWith("*") || n.startsWith("#")) && n.endsWith("#")
    }
}
