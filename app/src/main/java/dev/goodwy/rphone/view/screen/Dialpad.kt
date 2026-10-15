package dev.goodwy.rphone.view.screen

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.provider.CallLog
import android.provider.ContactsContract
import android.telecom.TelecomManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.view.WindowManager
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import dev.goodwy.rphone.controller.ContactsViewModel
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.controller.util.makeCall
import dev.goodwy.rphone.controller.util.normalizeDigits
import dev.goodwy.rphone.controller.util.normalizeNumberDigits
import dev.goodwy.rphone.cardCornerExtraSmall
import dev.goodwy.rphone.controller.util.CallRecorder
import dev.goodwy.rphone.view.components.SimPickerDialog
import dev.goodwy.rphone.view.components.SingleTile
import dev.goodwy.rphone.view.components.SwipeableCallLogContainer
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.ContactDetailsScreenDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinActivityViewModel
import dev.goodwy.rphone.liquidglass.drawBackdrop
import dev.goodwy.rphone.liquidglass.drawPlainBackdrop
import dev.goodwy.rphone.liquidglass.effects.blur
import dev.goodwy.rphone.liquidglass.effects.lens
import dev.goodwy.rphone.liquidglass.effects.colorControls
import dev.goodwy.rphone.liquidglass.highlight.Highlight
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.goodwy.rphone.R
import dev.goodwy.rphone.bottomBarHeight
import dev.goodwy.rphone.controller.CallLogViewModel
import dev.goodwy.rphone.liquidglass.LocalLiquidGlassBackdrop
import dev.goodwy.rphone.view.components.PermissionDeniedView
import dev.goodwy.rphone.view.components.PlaceholderView
import dev.goodwy.rphone.view.theme.MyColors.dialpadColor
import dev.goodwy.rphone.view.theme.MyColors.dialpadKeyColor
import dev.goodwy.rphone.view.theme.TabTransitionStyle
import dev.goodwy.rphone.view.theme.color_call_button
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import com.ramcosta.composedestinations.generated.destinations.ContactEditScreenDestination
import com.ramcosta.composedestinations.generated.destinations.ContactSelectionScreenDestination
import dev.goodwy.rphone.controller.UssdRepository
import dev.goodwy.rphone.controller.util.SocialUtils
import dev.goodwy.rphone.controller.util.SocialUtils.getInstalledMessenger
import dev.goodwy.rphone.controller.util.SocialUtils.messengerPackages
import dev.goodwy.rphone.controller.util.forceLtr
import dev.goodwy.rphone.liquidglass.backdrops.layerBackdrop
import dev.goodwy.rphone.liquidglass.backdrops.rememberLayerBackdrop
import dev.goodwy.rphone.modal.data.CallLogEntry
import dev.goodwy.rphone.modal.data.Contact
import dev.goodwy.rphone.modal.data.getDisplayContactInfo
import dev.goodwy.rphone.modal.data.getDisplayName
import dev.goodwy.rphone.view.components.RillDialog
import dev.goodwy.rphone.view.components.RillExpressiveButton
import dev.goodwy.rphone.view.components.performAppHaptic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.time.Duration.Companion.milliseconds

/**
 * Keeps the in-progress dialed digits alive across the dialpad bottom sheet being dismissed
 * (e.g. by swiping down on the drag handle) and reopened. The sheet's composable is fully torn
 * down on dismiss, so a plain `remember` loses the typed number; this small in-memory holder
 * survives that as long as the process is alive, matching what users expect from a dialer.
 */
object DialpadDraftHolder {
    var pendingNumber: String = ""
}

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Destination<RootGraph>(style = TabTransitionStyle::class)
@Composable
fun DialPadScreen(
    navController: NavController,
    navigator: DestinationsNavigator,
    initialNumber: String? = null
) {
//    val sheetState = rememberModalBottomSheetState(
//        skipPartiallyExpanded = true,
//        confirmValueChange = { true }
//    )
//    val prefs = koinInject<PreferenceManager>()

    // Lock the window so the keyboard never pushes the bottom sheet up.
    // WindowCompat.setDecorFitsSystemWindows(false) in MainActivity normally causes
    // the sheet to resize with the IME; overriding softInputMode here prevents that.
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = (view.context as? android.app.Activity)?.window
        val prevMode = window?.attributes?.softInputMode ?: 0
        window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        onDispose {
            window?.setSoftInputMode(prevMode)
        }
    }

    var number by remember(initialNumber) {
        mutableStateOf(
            if (!initialNumber.isNullOrBlank()) initialNumber
            else DialpadDraftHolder.pendingNumber
        )
    }

    LaunchedEffect(initialNumber) {
        if (!initialNumber.isNullOrBlank()) {
            number = initialNumber
            DialpadDraftHolder.pendingNumber = initialNumber
        }
    }

    // Keep the draft holder in sync so dismissing the sheet (including swipe-down-to-dismiss)
    // and reopening it restores whatever digits were typed, instead of clearing them.
    LaunchedEffect(number) { DialpadDraftHolder.pendingNumber = number }

    Scaffold(
        modifier = Modifier
            .fillMaxSize(),
//            .pointerInput(Unit) {
//                awaitPointerEventScope {
//                    while (true) {
//                        val down = awaitPointerEvent(PointerEventPass.Final).changes.firstOrNull()
//                            ?: continue
//                        if (!down.pressed) continue
//                        val startX = down.position.x
//                        val startY = down.position.y
//                        val startTime = System.currentTimeMillis()
//                        var triggered = false
//                        while (true) {
//                            val event = awaitPointerEvent(PointerEventPass.Final)
//                            val change = event.changes.firstOrNull() ?: break
//                            val dx = change.position.x - startX
//                            val dy = change.position.y - startY
//                            val elapsed = System.currentTimeMillis() - startTime
//                            if (!triggered && elapsed >= 150L &&
//                                abs(dx) > 700f &&
//                                abs(dx) > abs(dy) * 5.5f
//                            ) {
//                                triggered = true
//                                if (dx < 0) {
//                                    val route = when {
//                                        notesEnabled -> NotesScreenDestination.route
//                                        favouritesEnabled -> FavoritesScreenDestination.route
//                                        else -> RecentScreenDestination.route
//                                    }
//                                    scope.launch {
//                                        navController.navigate(route) {
//                                            popUpTo(navController.graph.findStartDestination().id) {
//                                                saveState = true
//                                            }
//                                            launchSingleTop = true; restoreState = true
//                                        }
//                                    }
//                                } else {
//                                    val route = when {
//                                        contactsEnabled -> ContactScreenDestination.route
//                                        else -> RecentScreenDestination.route
//                                    }
//                                    scope.launch {
//                                        navController.navigate(route) {
//                                            popUpTo(navController.graph.findStartDestination().id) {
//                                                saveState = true
//                                            }
//                                            launchSingleTop = true; restoreState = true
//                                        }
//                                    }
//                                }
//                            }
//                            if (!change.pressed) break
//                        }
//                    }
//                }
//            },
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0)
    ) { innerPadding ->
        Box(modifier = Modifier
            .padding(innerPadding)
            .fillMaxSize()
        ) {
            DialPadContent(
                initialNumber = number,
                navigator = navigator,
                onDismiss = { navigator.navigateUp() },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalPermissionsApi::class)
@Composable
fun DialPadContent(
    initialNumber: String? = null,
    navigator: DestinationsNavigator? = null,
    onDismiss: (() -> Unit)? = null,
    isBottomSheet: Boolean = false
) {
    val permStateLogs = rememberPermissionState(Manifest.permission.READ_CALL_LOG)
    val isGrantedLogs = permStateLogs.status == PermissionStatus.Granted
    val permStateContacts = rememberPermissionState(Manifest.permission.READ_CONTACTS)
    val isGrantedContacts = permStateContacts.status == PermissionStatus.Granted

    if (isGrantedLogs && isGrantedContacts) {
        val context = LocalContext.current
        val haptic = LocalHapticFeedback.current
        val clipboard = LocalClipboardManager.current
        val focusManager = LocalFocusManager.current
        val contactsVM: ContactsViewModel = koinActivityViewModel()
        val logsViewModel: CallLogViewModel = koinActivityViewModel()
        val prefs = koinInject<PreferenceManager>()
        val settingsState by prefs.settingsChanged.collectAsStateWithLifecycle()
        val displayOrder by remember(settingsState) {
            mutableIntStateOf(prefs.getInt(PreferenceManager.KEY_CONTACT_DISPLAY_ORDER, 0))
        }
        val enableAnimations by remember(settingsState) {
            mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_DIALPAD_ANIMATION, true))
        }

        val allContacts by contactsVM.allContacts.collectAsStateWithLifecycle()
        val logs by logsViewModel.allCallLogs.collectAsStateWithLifecycle()
        var number by remember { mutableStateOf(initialNumber ?: DialpadDraftHolder.pendingNumber) }
        // Where new digits get inserted / backspace deletes from. Defaults to the end of the number
        // (normal typing behaviour), but the user can tap anywhere in the number to move it, so they
        // can fill in a missing digit in the middle without having to delete and retype everything.
        var cursorPosition by remember { mutableIntStateOf(number.length) }

        // Route every edit through these so the cursor position stays correct and consistent no
        // matter where the edit originates from (dialpad keys, backspace, paste, clipboard banner,
        // clearing on secret-code detection, etc.)
        fun insertAtCursor(text: String) {
            val at = cursorPosition.coerceIn(0, number.length)
            number = number.substring(0, at) + text + number.substring(at)
            cursorPosition = at + text.length
        }
        fun backspaceAtCursor() {
            val at = cursorPosition.coerceIn(0, number.length)
            if (at > 0) {
                number = number.removeRange(at - 1, at)
                cursorPosition = at - 1
            }
        }
        fun replaceNumber(text: String) {
            number = text
            cursorPosition = text.length
        }

        LaunchedEffect(initialNumber) {
            if (!initialNumber.isNullOrBlank()) {
                replaceNumber(initialNumber)
                DialpadDraftHolder.pendingNumber = initialNumber
            }
        }

        // Keep the draft holder in sync so dismissing the sheet (including swipe-down-to-dismiss)
        // and reopening it restores whatever digits were typed, instead of clearing them.
        LaunchedEffect(number) { DialpadDraftHolder.pendingNumber = number }

        // Collect USSD / MMI responses from CallService and show inline dialog
        val ussdResult by UssdRepository.response.collectAsStateWithLifecycle()
        DisposableEffect(Unit) { onDispose { UssdRepository.clear() } }

        ussdResult?.let { (request, response) ->
            AlertDialog(
                onDismissRequest = { UssdRepository.clear() },
                title = {
                    Text(
                        request,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = { Text(response, style = MaterialTheme.typography.bodyMedium) },
                confirmButton = {
                    TextButton(onClick = { UssdRepository.clear() }) {
                        Text(stringResource(R.string.ok))
                    }
                },
                shape = MaterialTheme.shapes.extraLarge
            )
        }

        val appHapticsEnabled = remember(settingsState) {
            prefs.getBoolean(PreferenceManager.KEY_APP_HAPTICS, true)
        }
        val dtmfEnabled = remember(settingsState) {
            prefs.getBoolean(PreferenceManager.KEY_DTMF_TONE, false)
        }

        var showSocialDialog by remember { mutableStateOf(false) }
        val installedWhatsApp = messengerPackages["WhatsApp"]?.let { context.getInstalledMessenger(it) }
        val installedTelegram = messengerPackages["Telegram"]?.let { context.getInstalledMessenger(it) }
        val installedSignal = messengerPackages["Signal"]?.let { context.getInstalledMessenger(it) }
        val initiateMessage = { number: String ->
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    "sms:$number".toUri()
                )
            )
        }

        val t9Enabled = prefs.getBoolean(PreferenceManager.KEY_T9_DIALING, true)
        val swipeToCallEnabled = remember(settingsState) {
            prefs.getBoolean(PreferenceManager.KEY_SWIPE_TO_CALL, true)
        }
        val swipeCallStyle = remember(settingsState) {
            prefs.getInt(PreferenceManager.KEY_SWIPE_TO_CALL_STYLE, PreferenceManager.SWIPE_STYLE_SAMSUNG)
        }
        val swipeHaptics = remember(settingsState) {
            prefs.getBoolean(PreferenceManager.KEY_APP_HAPTICS, true)
        }
        val swipeHapticsStrength = remember(settingsState) {
            prefs.getString(PreferenceManager.KEY_APP_HAPTICS_STRENGTH, "light") ?: "light"
        }
        val swipeHapticsIntensity = remember(settingsState) {
            prefs.getFloat(PreferenceManager.KEY_HAPTICS_CUSTOM_INTENSITY, 0.5f)
        }
        var showSimPicker by remember { mutableStateOf(false) }
        val telecomManager =
            remember { context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager }
        var pendingSearchCallNumber by remember { mutableStateOf<String?>(null) }

        // Helper: place a call respecting the default SIM preference
        fun placeCallWithSimPreference(num: String) {
            val accounts = try { telecomManager.callCapablePhoneAccounts } catch (_: SecurityException) { emptyList() }
            if (accounts.size > 1) {
                val simPref = prefs.getInt(PreferenceManager.KEY_DEFAULT_SIM, prefs.getDefaultSimIndexDefault())
                when {
                    simPref == 1 && accounts.isNotEmpty() -> {
                        replaceNumber("")
                        makeCall(context, num, accounts[0])
                    }

                    simPref == 2 && accounts.size >= 2 -> {
                        replaceNumber("")
                        makeCall(context, num, accounts[1])
                    }

                    else -> {
                        pendingSearchCallNumber = num
                        showSimPicker = true
                    }
                }
            } else {
                replaceNumber("")
                makeCall(context, num)
            }
        }

        val clipText = remember {
            clipboard.getText()?.text?.filter { it.isDigit() || it == '+' } ?: ""
        }
        var showClipboardBanner by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            val clipText = clipboard.getText()?.text?.filter { it.isDigit() || it == '+' } ?: ""
            if (clipText.length in 7..15) {
                showClipboardBanner = true
            }
        }

        var showOverflowMenu by remember { mutableStateOf(false) }

        var openDialpadDefault by remember {
            mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_OPEN_DIALPAD_DEFAULT, true))
        }

        val take = 30
        // Search contacts results
        val searchResults by produceState(initialValue = emptyList<Contact>(), number, allContacts, t9Enabled) {
            val cleanQuery = number.normalizeDigits().replace(" ", "").replace("-", "")
            if (cleanQuery.isEmpty()) {
                value = emptyList()
                return@produceState
            }
            delay(100.milliseconds)
            val cleanQueryDigits = cleanQuery.filter { it.isDigit() }
            value = withContext(Dispatchers.Default) {
                allContacts.asSequence()
                    .filter { contact ->
                        val matchesNumber = contact.phoneNumbers.any {
                            val cleanNum = normalizeNumberDigits(it)
                            cleanNum.contains(cleanQuery) || (cleanQueryDigits.length >= 3 && cleanNum.filter { c -> c.isDigit() }.contains(cleanQueryDigits))
                        }
                        val matchesName = t9Enabled && T9Matcher.isMatch(contact.displayName, cleanQuery)
                        val matchesNickname = t9Enabled && contact.nickname.isNotBlank() && T9Matcher.isMatch(contact.nickname, cleanQuery)
                        matchesNumber || matchesName || matchesNickname
                    }
                    .take(take)
                    .toList()
            }
        }

        // Search logs results with unique numbers (latest call per number)
        val searchLogsResults by produceState(initialValue = emptyList<CallLogEntry>(), number, logs, t9Enabled) {
            val cleanQuery = number.normalizeDigits().replace(" ", "").replace("-", "")
            if (cleanQuery.isEmpty()) {
                value = emptyList()
                return@produceState
            }
            delay(100.milliseconds)
            val cleanQueryDigits = cleanQuery.filter { it.isDigit() }
            value = withContext(Dispatchers.Default) {
                logs.asSequence()
                    .filter { log ->
                        val cleanNum = normalizeNumberDigits(log.number)
                        val matchesNumber = cleanNum.contains(cleanQuery) || (cleanQueryDigits.length >= 3 && cleanNum.filter { c -> c.isDigit() }.contains(cleanQueryDigits))
                        val matchesName = t9Enabled && T9Matcher.isMatch(log.name ?: "", cleanQuery)
                        matchesNumber || matchesName
                    }
                    .take(take)
                    .toList()
                    .groupBy { it.number.replace(" ", "").replace("-", "") }
                    .mapNotNull { (_, logsGroup) -> logsGroup.maxByOrNull { it.date } }
                    .sortedByDescending { it.date }
            }
        }

        // Create a set of all phone numbers and names from contacts for quick searching
        val contactNumbers = remember(allContacts) {
            allContacts.flatMap { contact ->
                contact.phoneNumbers.map { it.replace(" ", "").replace("-", "") }
            }.toSet()
        }

        val contactNames = remember(allContacts) {
            allContacts.map { it.displayName.lowercase() }.toSet()
        }

        // Filtering logs
        val filteredSearchLogsResults = remember(searchLogsResults, contactNumbers, contactNames) {
            searchLogsResults.filter { log ->
                val logNumber = log.number.replace(" ", "").replace("-", "")
                val logName = log.name?.lowercase() ?: ""

                // Exclude if the number is in contacts OR the name is in contacts
                logNumber !in contactNumbers && logName !in contactNames
            }
        }

//    val scale by animateFloatAsState(
//        targetValue = if (number.isNotEmpty()) 1f else 0.95f,
//        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
//        label = "numberScale"
//    )

        val callPermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            if (permissions[Manifest.permission.CALL_PHONE] == true) {
                val numToCall = pendingSearchCallNumber ?: number
                pendingSearchCallNumber = null
                val hasPhoneState = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_PHONE_STATE
                ) == PackageManager.PERMISSION_GRANTED
                if (hasPhoneState) {
                    placeCallWithSimPreference(numToCall)
                } else {
                    replaceNumber("")
                    makeCall(context, numToCall)
                }
            } else {
                pendingSearchCallNumber = null
            }
        }

        // Auto-process Android hidden/secret codes and MMI codes as the user types
        fun processSecretCodeIfNeeded(input: String): Boolean {
            val code = input.trim()
            if (code.length < 3) return false

            // ── Pattern 0: *#06# (IMEI) / *#07# (SAR info)  ─────────────────────────────
            // These look like MMI/USSD codes (they even used to be handled that way in this
            // app), but they are NOT network requests at all — dialing them out via
            // TelecomManager.placeCall() sends them to the SIM/carrier as if they were a real
            // number, which is exactly what was causing the SIM-picker prompt / failed "call"
            // instead of the expected system info screen. Stock dialers intercept these two
            // locally, before ever touching Telecom, and this app now does the same.
            //
            // Note: reading the real IMEI via TelephonyManager.getImei() requires
            // READ_PRIVILEGED_PHONE_STATE on Android 10+, which only privileged system apps
            // can hold — being the default dialer does not grant it. So instead of showing a
            // (permission-blocked) in-app dialog, we open Android's own "About phone → IMEI
            // information" settings screen, which is what actually has that privilege and is
            // guaranteed to exist on every device.
            if (code == "*#06#") {
                try {
                    context.startActivity(
                        Intent(android.provider.Settings.ACTION_DEVICE_INFO_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (_: Exception) {}
                return true
            }
            if (code == "*#07#") {
                // "SAR information" doesn't have one stable intent action across all OEMs/OS
                // versions the way IMEI does, so try the couple of known ones first and fall
                // back to the general device-info settings screen (still a real system menu,
                // not a failed call) if none of them resolve on this device.
                val sarActions = listOf(
                    "android.settings.SAR_INFORMATION",
                    "android.settings.RF_EXPOSURE_SETTINGS",
                    android.provider.Settings.ACTION_DEVICE_INFO_SETTINGS
                )
                for (action in sarActions) {
                    try {
                        context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        break
                    } catch (_: Exception) { /* try next action */ }
                }
                return true
            }

            // ── Pattern 1: *#*#DIGITS#*#*  (Android secret activity codes, e.g. testing menu) ──
            // These end with #*#* so a plain endsWith("#") check misses them entirely
            val secretMatch = Regex("^\\*#\\*#(\\d+)#\\*#\\*$").find(code)
            if (secretMatch != null) {
                val digits = secretMatch.groupValues[1]
                // Fire every known delivery mechanism unconditionally rather than only
                // falling back to the classic broadcasts when sendDialerSpecialCode() throws.
                // sendDialerSpecialCode() is a fire-and-forget AIDL call — it reports no
                // success/failure back to us, so "didn't throw" is not proof the code was
                // actually delivered anywhere. Different codes are ultimately owned by
                // different apps (Settings' Testing menu for 4636, Calendar Storage for 225,
                // Play Services for 426, an OEM diagnostics app for the hardware-test codes,
                // etc.) and some only listen on one of these two channels, so sending both
                // maximizes the chance whichever app owns this particular code receives it.
                try {
                    val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? android.telephony.TelephonyManager
                    telephonyManager?.sendDialerSpecialCode(digits)
                } catch (_: Exception) {}
                val uri = android.net.Uri.parse("android_secret_code://$digits")
                try {
                    context.sendBroadcast(
                        Intent("android.provider.Telephony.SECRET_CODE", uri).apply {
                            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                        }
                    )
                } catch (_: Exception) {}
                try {
                    context.sendBroadcast(
                        Intent("android.telephony.action.SECRET_CODE", uri).apply {
                            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                        }
                    )
                } catch (_: Exception) {}
                return true
            }

            // ── Pattern 2: USSD / MMI codes  ──────────────────────────────────────────
            // *124#  *123#  *199#  ##002#  *21*N#  *#21#  *#62#
            // (*#06# and *#07# are intercepted above and never reach this branch.)
            val decoded = try { android.net.Uri.decode(code) } catch (_: Exception) { code }
            if (!((decoded.startsWith("*") || decoded.startsWith("#")) &&
                        decoded.endsWith("#"))) return false

            // Dial USSD/MMI codes exactly like a normal call via TelecomManager.placeCall()
            // (same approach RivoPhoneApp uses). The carrier's telephony stack recognises the
            // MMI/USSD prefix itself and drives the whole USSD session — including any
            // interactive multi-step menu — through Android's own native USSD dialog, and
            // placing a real call also lets CallService's connection-event listener (see
            // isUssdNumber() below) pick up and surface the response inline when the
            // carrier/OEM supplies one. This is far more reliable than
            // TelephonyManager.sendUssdRequest(), which only supports a single
            // non-interactive request/response and fails outright on many devices, carriers,
            // and dual-SIM setups.
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                placeCallWithSimPreference(decoded)
            } else {
                pendingSearchCallNumber = decoded
                callPermissionLauncher.launch(arrayOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE))
            }
            return true
        }

        fun initiateCall(num: String) {
            val cleanNum = num.trim()
            if (cleanNum.isEmpty() || cleanNum == "Unknown") return
            // MMI/USSD codes (*#06#, *#*#4636#*#*, *21*1234#, *124#, ##002#, etc.) are handled
            // by processSecretCodeIfNeeded which uses telecomManager.placeCall() with the raw URI
            // for proper carrier-stack routing without looping back to this app.
            if (processSecretCodeIfNeeded(cleanNum)) {
                replaceNumber("")
                return
            }
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                val hasPhoneState = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
                if (hasPhoneState) {
                    placeCallWithSimPreference(cleanNum)
                } else {
                    replaceNumber("")
                    makeCall(context, cleanNum)
                }
            } else {
                pendingSearchCallNumber = cleanNum
                callPermissionLauncher.launch(arrayOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE))
            }
        }

        if (showSimPicker) {
            SimPickerDialog(
                onDismissRequest = { showSimPicker = false },
                onSimSelected = { handle ->
                    replaceNumber("")
                    makeCall(context, pendingSearchCallNumber ?: number, handle)
                    pendingSearchCallNumber = null
                    showSimPicker = false
                }
            )
        }

        if (showSocialDialog) {
            RillDialog(
                onDismissRequest = { showSocialDialog = false },
                title = stringResource(R.string.connect_via_social),
                icon = ImageVector.vectorResource(id = R.drawable.ic_message_outline),
                confirmButton = {
                    TextButton(onClick = { showSocialDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (installedWhatsApp != null) {
                        RillExpressiveButton(
                            modifier = Modifier.weight(1f),
                            icon = ImageVector.vectorResource(id = R.drawable.ic_whatsapp),
                            label = "WhatsApp",
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            iconSize = 32.dp,
                            onClick = {
                                SocialUtils.openWhatsApp(context, number)
                                showSocialDialog = false
                            }
                        )
                    }
                    if (installedTelegram != null) {
                        RillExpressiveButton(
                            modifier = Modifier.weight(1f),
                            icon = ImageVector.vectorResource(id = R.drawable.ic_telegram),
                            label = "Telegram",
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            iconSize = 32.dp,
                            onClick = {
                                SocialUtils.openTelegram(context, number)
                                showSocialDialog = false
                            }
                        )
                    }
                    if (installedSignal != null) {
                        RillExpressiveButton(
                            modifier = Modifier.weight(1f),
                            icon = ImageVector.vectorResource(id = R.drawable.ic_signal),
                            label = "Signal",
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            iconSize = 32.dp,
                            onClick = {
                                SocialUtils.openSignal(context, number)
                                showSocialDialog = false
                            }
                        )
                    }
                    RillExpressiveButton(
                        modifier = Modifier.weight(1f),
                        icon = ImageVector.vectorResource(id = R.drawable.ic_message_outline),
                        label = "SMS",
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        iconSize = 32.dp,
                        onClick = {
                            initiateMessage(number)
                            showSocialDialog = false
                        }
                    )
                }
            }
        }

        val configuration = LocalConfiguration.current
        val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        if (isLandscape) {
            // Landscape: side-by-side layout — left=search+search results, right=dialpad keys+number+actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left panel: search bar + results only
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .background(if (isBottomSheet) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surface),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        val searchLogsNotEmpty = filteredSearchLogsResults.isNotEmpty()
                        val searchResultsNotEmpty = searchResults.isNotEmpty()
                        val searchResultsPadding = if (searchLogsNotEmpty) 16.dp else 4.dp
                        // Search contacts results
                        AnimatedVisibility(
                            visible = searchResultsNotEmpty,
                            enter = fadeIn(tween(380, easing = FastOutSlowInEasing)) +
                                    expandVertically(tween(420, easing = FastOutSlowInEasing)),
                            exit = fadeOut(tween(280, easing = FastOutLinearInEasing)) +
                                    shrinkVertically(tween(320, easing = FastOutLinearInEasing))
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(top = 4.dp, bottom = searchResultsPadding)
                                    .then(
                                        if (searchLogsNotEmpty) Modifier
                                        else Modifier.navigationBarsPadding()
                                    )
                                    .then(if (!isBottomSheet) Modifier.statusBarsPadding() else Modifier)
                            ) {
                                Text(
                                    text = stringResource(R.string.contacts),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp)
                                )
                                Surface(
                                    shape = MaterialTheme.shapes.extraLarge,
                                    color = if (isBottomSheet) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .background(if (isBottomSheet) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surface),
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        searchResults.forEach { contact ->
                                            val defaultOrFirstPhone = contact.phoneDetails.firstOrNull { it.isPrimary }?.number ?: contact.phoneNumbers.firstOrNull()
                                            SwipeableCallLogContainer(
                                                enabled = swipeToCallEnabled && defaultOrFirstPhone != null && (swipeCallStyle != PreferenceManager.SWIPE_STYLE_DISABLED),
                                                style = swipeCallStyle,
                                                shape = RoundedCornerShape(cardCornerExtraSmall),
                                                haptics = swipeHaptics,
                                                hapticsStrength = swipeHapticsStrength,
                                                hapticsIntensity = swipeHapticsIntensity,
                                                onSwipeRight = {
                                                    if (defaultOrFirstPhone != null) initiateCall(defaultOrFirstPhone)
                                                },
                                                onSwipeLeft = {
                                                    if (defaultOrFirstPhone != null) initiateMessage(defaultOrFirstPhone)
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                SingleTile(
                                                    title = getDisplayName(contact, displayOrder), //contact.displayName,
                                                    subtitle = getDisplayContactInfo(contact),
                                                    photoUri = contact.photoUri,
                                                    phoneNumber = defaultOrFirstPhone,
                                                    trailingContent = if (!swipeToCallEnabled || swipeCallStyle != PreferenceManager.SWIPE_STYLE_SAMSUNG) {
                                                        {
                                                            if (defaultOrFirstPhone != null) {
                                                                IconButton(onClick = { initiateCall(defaultOrFirstPhone) }) {
                                                                    Icon(Icons.Outlined.Call, contentDescription = stringResource(R.string.call), tint = MaterialTheme.colorScheme.primary)
                                                                }
                                                            }
                                                        }
                                                    } else null,
                                                    onCall = { if (defaultOrFirstPhone != null) initiateCall(defaultOrFirstPhone) },
                                                    onClick = {
                                                        navigator?.navigate(
                                                            ContactDetailsScreenDestination(
                                                                contactId = contact.id
                                                            )
                                                        )
                                                    },
                                                    menuOffset = DpOffset(56.dp, 0.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Search logs results
                        AnimatedVisibility(
                            visible = searchLogsNotEmpty,
                            enter = fadeIn(tween(380, easing = FastOutSlowInEasing)) +
                                    expandVertically(tween(420, easing = FastOutSlowInEasing)),
                            exit = fadeOut(tween(280, easing = FastOutLinearInEasing)) +
                                    shrinkVertically(tween(320, easing = FastOutLinearInEasing))
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(top = 4.dp, bottom = 4.dp)
                                    .navigationBarsPadding()
                                    .then(if (!isBottomSheet && !searchResultsNotEmpty) Modifier.statusBarsPadding() else Modifier)
                            ) {
                                Text(
                                    text = stringResource(R.string.recents),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp)
                                )
                                Surface(
                                    shape = MaterialTheme.shapes.extraLarge,
                                    color = if (isBottomSheet) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier
//                                        .padding(vertical = 8.dp)
                                            .background(if (isBottomSheet) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surface),
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        filteredSearchLogsResults.forEach { log ->
                                            val displayName = if (log.name == log.number) log.number.forceLtr() else log.name?.ifEmpty { log.number } ?: log.number.ifEmpty { "Unknown" }
                                            val recFile = remember(log.id, log.number, log.date) {
                                                CallRecorder.findRecordingForCall(context, log.number, log.date, log.duration)
                                            }
                                            SwipeableCallLogContainer(
                                                enabled = swipeToCallEnabled && log.number.isNotBlank() && (swipeCallStyle != PreferenceManager.SWIPE_STYLE_DISABLED),
                                                style = swipeCallStyle,
                                                shape = RoundedCornerShape(cardCornerExtraSmall),
                                                haptics = swipeHaptics,
                                                hapticsStrength = swipeHapticsStrength,
                                                hapticsIntensity = swipeHapticsIntensity,
                                                onSwipeRight = {
                                                    initiateCall(log.number)
                                                },
                                                onSwipeLeft = {
                                                    initiateMessage(log.number)
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                SingleTile(
                                                    title = displayName,
                                                    subtitle = if (log.name == log.number) null else log.number,
                                                    photoUri = log.photoUri,
                                                    phoneNumber = log.number,
                                                    hasRecording = recFile != null,
                                                    trailingContent = if (!swipeToCallEnabled || swipeCallStyle != PreferenceManager.SWIPE_STYLE_SAMSUNG) {
                                                        {
                                                            IconButton(onClick = { initiateCall(log.number) }) {
                                                                Icon(Icons.Outlined.Call, contentDescription = stringResource(R.string.call), tint = MaterialTheme.colorScheme.primary)
                                                            }
                                                        }
                                                    } else null,
                                                    onCall = { initiateCall(log.number) },
                                                    onClick = {
                                                        if (prefs.getBoolean(PreferenceManager.KEY_APP_HAPTICS, true)) {
                                                            performAppHaptic(
                                                                context,
                                                                prefs.getString(PreferenceManager.KEY_APP_HAPTICS_STRENGTH, "light") ?: "light",
                                                                prefs.getFloat(PreferenceManager.KEY_HAPTICS_CUSTOM_INTENSITY, 0.5f)
                                                            )
                                                        }
                                                        navigator?.navigate(ContactDetailsScreenDestination(phoneNumber = log.number))
                                                    },
                                                    showCreateContact = log.contactId == null,
                                                    menuOffset = DpOffset(56.dp, 0.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.weight(1f))
                    }
                }

                // Right panel: dialpad keys + action buttons below
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .navigationBarsPadding()
                            .padding(horizontal = 6.dp, vertical = 12.dp)
                            .then(if (!isBottomSheet) Modifier.statusBarsPadding() else Modifier),
                        shape = MaterialTheme.shapes.extraLarge,
//                shadowElevation = 2.dp,
                        color = dialpadColor //MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 8.dp, vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)//.SpaceEvenly
                        ) {
                            // Number display
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1.2f)
                                    .clip(MaterialTheme.shapes.large)
                                    .padding(vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                val numberLength = number.length
                                val dynamicFontSize = (28f - (numberLength * 0.4f)).coerceIn(12f, 28f).toInt()
                                DialpadNumberDisplay(
                                    number = number,
                                    fontSize = dynamicFontSize,
                                    cursorPosition = cursorPosition,
                                    onCursorPositionChange = { cursorPosition = it },
                                    onLongPress = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        showOverflowMenu = true
                                    }
                                )
                            }
                            val keys = listOf(
                                listOf("1", "2", "3"),
                                listOf("4", "5", "6"),
                                listOf("7", "8", "9"),
                                listOf("*", "0", "#")
                            )
                            val currentLocale = LocalConfiguration.current.locales[0] ?: java.util.Locale.getDefault()
                            val subKeys = remember(currentLocale) { getDialpadSubKeys(currentLocale.language) }
                            keys.forEach { row ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    row.forEach { key ->
                                        DialPadKey(
                                            number = key,
                                            letters = subKeys[key] ?: "",
                                            onClick = { digit -> insertAtCursor(digit) },
                                            onLongClick = { digit -> insertAtCursor(digit) },
                                            hapticsEnabled = appHapticsEnabled,
                                            dtmfEnabled = dtmfEnabled,
                                            compact = true,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .weight(1f)
                                        )
                                    }
                                }
                            }
                            // Action row — below the keys
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1.2f)
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                FadeScaleBox(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                    visible = number.isNotEmpty()
                                ) {
                                    DialerActionExpressive(
                                        onClick = {
                                            navigator?.navigate(
                                                ContactEditScreenDestination(
                                                    initialPhone = number
                                                )
                                            )
                                        },
                                        icon = Icons.Default.PersonAdd,
                                        contentDescription = stringResource(R.string.create_contact),
                                        containerColor = Color.Transparent //MaterialTheme.colorScheme.surfaceContainerLow
                                    )
                                }
                                DialerActionExpressive(
                                    onClick = {
                                        if (number.isNotEmpty()) {
                                            initiateCall(number)
                                        } else {
                                            val lastNum = logs.firstOrNull { it.type == CallLog.Calls.OUTGOING_TYPE }?.number
                                                ?: logs.firstOrNull()?.number
                                            if (!lastNum.isNullOrBlank()) {
                                                replaceNumber(lastNum)
                                            }
                                        }
                                    },
                                    onLongClick = {
                                        val pasteText = clipboard.getText()?.text
                                            ?.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
                                            ?: ""
                                        if (number.isEmpty() && pasteText.isNotEmpty()) {
                                            replaceNumber(pasteText)
                                        } else {
                                            clipboard.setText(AnnotatedString(number))
                                        }
                                    },
                                    icon = Icons.Default.Call,
                                    contentDescription = stringResource(R.string.call),
                                    containerColor = color_call_button,
                                    contentColor = Color.White,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                    isLarge = true
                                )
                                FadeScaleBox(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                    visible = number.isNotEmpty()
                                ) {
                                    DialerActionExpressive(
                                        onLongClick = {
                                            replaceNumber("")
                                        },
                                        onClick = {
                                            backspaceAtCursor()
                                        },
                                        icon = Icons.AutoMirrored.Outlined.Backspace,
                                        contentDescription = stringResource(R.string.backspace),
                                        containerColor = Color.Transparent //MaterialTheme.colorScheme.surfaceContainerLow
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Prevent keyboard from auto-opening on composition
            LaunchedEffect(Unit) { focusManager.clearFocus() }

            val dialpadBackdrop = rememberLayerBackdrop()

            CompositionLocalProvider(LocalLiquidGlassBackdrop provides dialpadBackdrop) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (!isBottomSheet) Modifier.statusBarsPadding() else Modifier)
//            .padding(top = 16.dp)
                ) {
                    val screenHeight = maxHeight
                    val screenWidth = maxWidth

                    // Layout: search bar fixed at top, scrollable middle (results/pills/clipboard),
                    // dialpad card fixed at bottom. Nothing moves when results appear.
                    Box(
                        modifier = Modifier.fillMaxSize(),
//                verticalArrangement = Arrangement.Top
                        contentAlignment = Alignment.BottomEnd
                    ) {
                        // ── Middle section: results / pills / clipboard (scrollable, fills space) ──
                        var isDialpadVisible by remember { mutableStateOf(true) }
                        val listScrollState = rememberScrollState()
                        var lastScrollPosition by remember { mutableStateOf(0) }

                        LaunchedEffect(listScrollState.isScrollInProgress) {
                            snapshotFlow { listScrollState.isScrollInProgress }
                                .collect { isScrolling ->
                                    if (isScrolling && isDialpadVisible && listScrollState.value > 0) {
                                        isDialpadVisible = false
                                    }
                                }
                        }
                        Surface(
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .layerBackdrop(dialpadBackdrop),
                            shape = MaterialTheme.shapes.large,
                            color = Color.Transparent
                        ) {
                            Column(
                                modifier = Modifier
//                        .weight(1f)
                                    .fillMaxSize()
//                                .navigationBarsPadding()
                                    .verticalScroll(listScrollState),
                                verticalArrangement = Arrangement.Top
                            ) {

                                AnimatedVisibility(
                                    visible = number.isNotEmpty(),// && searchResults.isEmpty() && searchQuery.isEmpty(),
                                    enter = fadeIn() + expandVertically(),
                                    exit = fadeOut() + shrinkVertically()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 8.dp, start = 2.dp, end = 2.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Surface(
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                navigator?.navigate(
                                                    ContactEditScreenDestination(
                                                        initialPhone = number
                                                    )
                                                )
                                            },
                                            shape = RoundedCornerShape(50.dp),
                                            color = MaterialTheme.colorScheme.secondaryContainer,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(
                                                    horizontal = 12.dp,
                                                    vertical = 10.dp
                                                ),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center
                                            ) {
                                                val create = stringResource(R.string.create_contact)
                                                Icon(
                                                    Icons.Default.PersonAdd,
                                                    create,
                                                    modifier = Modifier.size(16.dp),
                                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    create,
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        Surface(
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
//                                                val intent =
//                                                    Intent(Intent.ACTION_INSERT_OR_EDIT).apply {
//                                                        type =
//                                                            ContactsContract.Contacts.CONTENT_ITEM_TYPE
//                                                        putExtra(
//                                                            ContactsContract.Intents.Insert.PHONE,
//                                                            number
//                                                        )
//                                                    }
//                                                context.startActivity(intent)
                                                navigator?.navigate(
                                                    ContactSelectionScreenDestination(
                                                        title = "Add to Existing Contact",
                                                        initialPhoneToAssign = number
                                                    )
                                                )
                                            },
                                            shape = RoundedCornerShape(50.dp),
                                            color = MaterialTheme.colorScheme.secondaryContainer,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(
                                                    horizontal = 12.dp,
                                                    vertical = 10.dp
                                                ),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center
                                            ) {
                                                val add = stringResource(R.string.add_to_contact)
                                                Icon(
                                                    Icons.Default.Person,
                                                    add,
                                                    modifier = Modifier.size(16.dp),
                                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    add,
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }

                                        Surface(
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                if (
                                                    installedWhatsApp != null ||
                                                    installedTelegram != null ||
                                                    installedSignal != null
                                                ) showSocialDialog = true
                                                else initiateMessage(number)
                                            },
                                            shape = RoundedCornerShape(50.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(
                                                    horizontal = 12.dp,
                                                    vertical = 10.dp
                                                ),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center
                                            ) {
                                                val message = stringResource(R.string.message)
                                                Icon(
                                                    painter = painterResource(id = R.drawable.ic_message_outline),
                                                    message,
                                                    modifier = Modifier.size(16.dp),
                                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    message,
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }

                                val configuration = LocalConfiguration.current
                                val screenHeightDp = configuration.screenHeightDp.dp
                                val searchResultsBottomPadding = screenHeightDp * 0.50f
                                val searchResultsPadding =
                                    if (filteredSearchLogsResults.isNotEmpty()) 16.dp else searchResultsBottomPadding
                                // Search contacts results
                                AnimatedVisibility(
                                    visible = searchResults.isNotEmpty(),
                                    enter = fadeIn(tween(380, easing = FastOutSlowInEasing)) +
                                            expandVertically(
                                                tween(
                                                    420,
                                                    easing = FastOutSlowInEasing
                                                )
                                            ),
                                    exit = fadeOut(tween(280, easing = FastOutLinearInEasing)) +
                                            shrinkVertically(
                                                tween(
                                                    320,
                                                    easing = FastOutLinearInEasing
                                                )
                                            )
                                ) {
                                    Column(
                                        modifier = Modifier//.padding(horizontal = 16.dp, vertical = 4.dp)
                                            .padding(
//                                        start = 16.dp,
//                                        end = 16.dp,
                                                top = 4.dp,
                                                bottom = searchResultsPadding
                                            )
                                    ) {
                                        Text(
                                            text = stringResource(R.string.contacts),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                        )
                                        Surface(
                                            shape = MaterialTheme.shapes.extraLarge,
                                            color = if (isBottomSheet) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surface,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(
                                                modifier = Modifier
//                                        .padding(vertical = 8.dp)
                                                    .background(if (isBottomSheet) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surface),
                                                verticalArrangement = Arrangement.spacedBy(2.dp),
                                            ) {
                                                searchResults.forEach { contact ->
                                                    val defaultOrFirstPhone =
                                                        contact.phoneDetails.firstOrNull { it.isPrimary }?.number
                                                            ?: contact.phoneNumbers.firstOrNull()
                                                    SwipeableCallLogContainer(
                                                        enabled = swipeToCallEnabled && defaultOrFirstPhone != null && (swipeCallStyle != PreferenceManager.SWIPE_STYLE_DISABLED),
                                                        style = swipeCallStyle,
                                                        shape = RoundedCornerShape(cardCornerExtraSmall),
                                                        haptics = swipeHaptics,
                                                        hapticsStrength = swipeHapticsStrength,
                                                        hapticsIntensity = swipeHapticsIntensity,
                                                        onSwipeRight = {
                                                            if (defaultOrFirstPhone != null) initiateCall(defaultOrFirstPhone)
                                                        },
                                                        onSwipeLeft = {
                                                            if (defaultOrFirstPhone != null) initiateMessage(defaultOrFirstPhone)
                                                        },
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        SingleTile(
                                                            title = getDisplayName(
                                                                contact,
                                                                displayOrder
                                                            ), //contact.displayName,
                                                            subtitle = getDisplayContactInfo(contact),
                                                            photoUri = contact.photoUri,
                                                            phoneNumber = defaultOrFirstPhone,
                                                            trailingContent = if (!swipeToCallEnabled || swipeCallStyle != PreferenceManager.SWIPE_STYLE_SAMSUNG) {
                                                                {
                                                                    if (defaultOrFirstPhone != null) {
                                                                        IconButton(onClick = {
                                                                            initiateCall(
                                                                                defaultOrFirstPhone
                                                                            )
                                                                        }) {
                                                                            Icon(
                                                                                Icons.Outlined.Call,
                                                                                contentDescription = stringResource(
                                                                                    R.string.call
                                                                                ),
                                                                                tint = MaterialTheme.colorScheme.primary
                                                                            )
                                                                        }
                                                                    }
                                                                }
                                                            } else null,
                                                            onCall = {
                                                                if (defaultOrFirstPhone != null) initiateCall(
                                                                    defaultOrFirstPhone
                                                                )
                                                            },
                                                            onClick = {
                                                                navigator?.navigate(
                                                                    ContactDetailsScreenDestination(
                                                                        contactId = contact.id
                                                                    )
                                                                )
                                                            },
                                                            menuOffset = DpOffset(56.dp, 64.dp),
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // Search logs and recents results
                                val logsToDisplay = remember(number, filteredSearchLogsResults, logs) {
                                    if (number.isEmpty()) {
                                        logs.take(50)
                                    } else {
                                        filteredSearchLogsResults
                                    }
                                }
                                AnimatedVisibility(
                                    visible = logsToDisplay.isNotEmpty(),
                                    enter = fadeIn(tween(380, easing = FastOutSlowInEasing)) +
                                            expandVertically(
                                                tween(
                                                    420,
                                                    easing = FastOutSlowInEasing
                                                )
                                            ),
                                    exit = fadeOut(tween(280, easing = FastOutLinearInEasing)) +
                                            shrinkVertically(
                                                tween(
                                                    320,
                                                    easing = FastOutLinearInEasing
                                                )
                                            )
                                ) {
                                    Column(
                                        modifier = Modifier//.padding(horizontal = 16.dp, vertical = 4.dp)
                                            .padding(
//                                        start = 16.dp,
//                                        end = 16.dp,
                                                top = 4.dp,
                                                bottom = searchResultsBottomPadding
                                            )
                                    ) {
                                        Text(
                                            text = stringResource(R.string.recents),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                        )
                                        Surface(
                                            shape = MaterialTheme.shapes.extraLarge,
                                            color = if (isBottomSheet) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surface,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(
                                                modifier = Modifier
//                                        .padding(vertical = 8.dp)
                                                    .background(if (isBottomSheet) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surface),
                                                verticalArrangement = Arrangement.spacedBy(2.dp),
                                            ) {
                                                logsToDisplay.forEach { log ->
                                                    val displayName =
                                                        if (log.name == log.number) log.number.forceLtr() else log.name?.ifEmpty { log.number }
                                                            ?: log.number.ifEmpty { "Unknown" }
                                                    val recFile = remember(log.id, log.number, log.date) {
                                                        CallRecorder.findRecordingForCall(context, log.number, log.date, log.duration)
                                                    }
                                                    SwipeableCallLogContainer(
                                                        enabled = swipeToCallEnabled && log.number.isNotBlank() && (swipeCallStyle != PreferenceManager.SWIPE_STYLE_DISABLED),
                                                        style = swipeCallStyle,
                                                        shape = RoundedCornerShape(cardCornerExtraSmall),
                                                        haptics = swipeHaptics,
                                                        hapticsStrength = swipeHapticsStrength,
                                                        hapticsIntensity = swipeHapticsIntensity,
                                                        onSwipeRight = {
                                                            initiateCall(log.number)
                                                        },
                                                        onSwipeLeft = {
                                                            initiateMessage(log.number)
                                                        },
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        SingleTile(
                                                            title = displayName,
                                                            subtitle = if (log.name == log.number) null else log.number,
                                                            photoUri = log.photoUri,
                                                            phoneNumber = log.number,
                                                            hasRecording = recFile != null,
                                                            trailingContent = if (!swipeToCallEnabled || swipeCallStyle != PreferenceManager.SWIPE_STYLE_SAMSUNG) {
                                                                {
                                                                    IconButton(onClick = { initiateCall(log.number) }) {
                                                                        Icon(
                                                                            Icons.Outlined.Call,
                                                                            contentDescription = stringResource(
                                                                                R.string.call
                                                                            ),
                                                                            tint = MaterialTheme.colorScheme.primary
                                                                        )
                                                                    }
                                                                }
                                                            } else null,
                                                            onCall = { initiateCall(log.number) },
                                                            onClick = {
                                                                if (prefs.getBoolean(
                                                                        PreferenceManager.KEY_APP_HAPTICS,
                                                                        true
                                                                    )
                                                                ) {
                                                                    performAppHaptic(
                                                                        context,
                                                                        prefs.getString(
                                                                            PreferenceManager.KEY_APP_HAPTICS_STRENGTH,
                                                                            "light"
                                                                        ) ?: "light",
                                                                        prefs.getFloat(
                                                                            PreferenceManager.KEY_HAPTICS_CUSTOM_INTENSITY,
                                                                            0.5f
                                                                        )
                                                                    )
                                                                }
                                                                navigator?.navigate(
                                                                    ContactDetailsScreenDestination(
                                                                        phoneNumber = log.number
                                                                    )
                                                                )
                                                            },
                                                            showCreateContact = log.contactId == null,
                                                            menuOffset = DpOffset(56.dp, 64.dp),
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // Clipboard banner
                                AnimatedVisibility(
                                    visible = showClipboardBanner && number.isEmpty(),
                                    enter = fadeIn() + expandVertically(),
                                    exit = fadeOut() + shrinkVertically()
                                ) {
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(
//                                        horizontal = 16.dp,
                                                vertical = 4.dp
                                            ),
                                        shape = MaterialTheme.shapes.extraLarge,
                                        color = MaterialTheme.colorScheme.secondaryContainer
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(
                                                start = 24.dp, end = 8.dp, top = 8.dp, bottom = 8.dp
                                            ),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(
                                                Icons.Default.ContentPaste,
                                                null,
                                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Text(
                                                text = clipText,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .padding(horizontal = 20.dp)
                                            )
                                            TextButton(onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); replaceNumber(
                                                clipText
                                            ); showClipboardBanner = false
                                            }) {
                                                Text(
                                                    stringResource(R.string.use),
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            IconButton(
                                                onClick = { showClipboardBanner = false },
//                                        modifier = Modifier.size(cardCornerBig)
                                            ) {
                                                Icon(
                                                    Icons.Default.Close,
                                                    stringResource(R.string.close)
                                                )
                                            }
                                        }
                                    }
                                }

                                AnimatedVisibility(
                                    visible = searchResults.isEmpty() && filteredSearchLogsResults.isEmpty() && number.isNotEmpty(),
                                    enter = fadeIn(tween(380, easing = FastOutSlowInEasing)) +
                                            expandVertically(
                                                tween(
                                                    420,
                                                    easing = FastOutSlowInEasing
                                                )
                                            ),
                                    exit = fadeOut(tween(280, easing = FastOutLinearInEasing)) +
                                            shrinkVertically(
                                                tween(
                                                    320,
                                                    easing = FastOutLinearInEasing
                                                )
                                            )
                                ) {
                                    PlaceholderView(
                                        icon = Icons.Rounded.SearchOff,
                                        title = stringResource(R.string.no_results),
                                    )
                                }
                            } // end scrollable middle Column
                        }


                        // Show dialpad button
                        val pillNav = remember { prefs.getBoolean(PreferenceManager.KEY_PILL_NAV, false) }
                        AnimatedVisibility(
                            visible = !isDialpadVisible,
                            enter = scaleIn() + fadeIn(),
                            exit = scaleOut() + fadeOut()
                        ) {
                            val dialpadTab =
                                prefs.getBoolean(PreferenceManager.KEY_TAB_SHOW_DIALPAD, true)
                            val fabShape = RoundedCornerShape(17.dp)
                            FloatingActionButton(
                                modifier = Modifier
                                    .padding(bottom = if (isBottomSheet) 0.dp else if (pillNav) 88.dp else bottomBarHeight + 12.dp)
                                    .navigationBarsPadding()
                                    .then(
                                        if (dialpadTab) Modifier
                                            .padding(horizontal = 16.dp, vertical = 4.dp)
                                        else Modifier
                                            .padding(horizontal = 16.dp, vertical = 16.dp)
                                    ),
                                onClick = { isDialpadVisible = true },
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                shape = fabShape,
                                elevation = FloatingActionButtonDefaults.elevation(4.dp),
                            ) { Icon(Icons.Default.Dialpad, stringResource(R.string.keypad)) }
                        }

                        // ── Dialpad card — hides with animation when search is active ──
                        AnimatedVisibility(
                            visible = isDialpadVisible,
                            enter = slideInVertically(
                                initialOffsetY = { it },
                                animationSpec = tween(
                                    durationMillis = 320,
                                    easing = FastOutSlowInEasing
                                )
                            ) + fadeIn(
                                animationSpec = tween(
                                    durationMillis = 280,
                                    easing = FastOutSlowInEasing
                                )
                            ),
                            exit = slideOutVertically(
                                targetOffsetY = { it },
                                animationSpec = tween(
                                    durationMillis = 260,
                                    easing = FastOutLinearInEasing
                                )
                            ) + fadeOut(
                                animationSpec = tween(
                                    durationMillis = 200,
                                    easing = FastOutLinearInEasing
                                )
                            )
                        ) {
                            // ── Dialpad card — always at bottom, never moves ───────────────
                            BoxWithConstraints(
                                contentAlignment = Alignment.BottomCenter,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp)
                            ) {
                                // Scale based on the SMALLER of width-derived and height-derived factors
                                // so the dialpad always fits on screen regardless of device size.
                                val refWidth = 360f
                                val availableWidth = maxWidth.value
                                val widthScale = (availableWidth / refWidth).coerceIn(0.6f, 1.4f)

                                // Height budget: total screen height minus search bar (~64dp) minus spacing (~24dp)
                                // The dialpad card needs: header(~56dp) + 4 key rows + action row(~72dp) + padding(~40dp)
                                // Reference key height = 68dp, so 4 rows = 272dp + overhead ~168dp = ~440dp total card
                                val cardHeightBudget =
                                    (screenHeight.value - 64f - 24f).coerceAtLeast(200f)
                                val refCardHeight = 440f
                                val heightScale =
                                    (cardHeightBudget / refCardHeight).coerceIn(0.55f, 1.4f)

                                val scaleFactor = minOf(widthScale, heightScale)

                                val keySize: Dp = (72 * scaleFactor).coerceIn(54f, 78f).dp
                                val keyWidth: Dp = keySize
                                val keyHeight: Dp = keySize
                                val spacing = if (isBottomSheet) 14 else 12
                                val keySpacing: Dp = (spacing * scaleFactor).coerceIn(8f, 20f).dp

                                val liquidGlass = remember(settingsState) { prefs.getBoolean(PreferenceManager.KEY_LIQUID_GLASS, false) }
                                val lgDialpad = remember(settingsState) { prefs.getBoolean(PreferenceManager.KEY_LG_DIALPAD, false) }
                                val blurEffects = remember(settingsState) { prefs.getBoolean(PreferenceManager.KEY_BLUR_EFFECTS, false) }
                                val blurDialpad = remember(settingsState) { prefs.getBoolean(PreferenceManager.KEY_BLUR_DIALPAD, false) }
                                val blurIntensity = remember(settingsState) { prefs.getInt(PreferenceManager.KEY_BLUR_INTENSITY, 20).toFloat() }
                                val globalBackdrop = LocalLiquidGlassBackdrop.current
                                val useLgDialpad = liquidGlass && lgDialpad && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && globalBackdrop != null
                                val useBlurDialpad = blurEffects && blurDialpad && !useLgDialpad

                                if (!useLgDialpad && !useBlurDialpad) Box( // Hides the bottom part of the list
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(if (isBottomSheet) 100.dp else 200.dp)
                                        .background(if (isBottomSheet) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surface)
                                )
                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                    val dialpadContent: @Composable () -> Unit = {
                                        val topBottom = if (isBottomSheet) 12 else 8
                                        Column(
                                            modifier = Modifier.padding(
                                                horizontal = 8.dp,
                                                vertical = (topBottom * scaleFactor).coerceIn(4f, 14f).dp
                                            ),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(keySpacing)
                                        ) {
                                            // Drag / collapse handle to hide dialpad and view recents
                                            if (!isBottomSheet) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable(
                                                            interactionSource = remember { MutableInteractionSource() },
                                                            indication = null
                                                        ) {
                                                            isDialpadVisible = false
                                                        }
                                                        .padding(bottom = 4.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .width(36.dp)
                                                            .height(4.dp)
                                                            .background(
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                                                                shape = CircleShape
                                                            )
                                                    )
                                                }
                                            }

                                            // Centered Number & "Create Contact" Display (iOS style)
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .heightIn(min = 68.dp)
                                                    .padding(horizontal = 16.dp, vertical = 2.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    val numberLength = number.length
                                                    val dynamicFontSize = (36f - (numberLength - 7).coerceAtLeast(0) * 1.5f).coerceIn(20f, 36f).toInt()
                                                    DialpadNumberDisplay(
                                                        number = number,
                                                        fontSize = dynamicFontSize,
                                                        cursorPosition = cursorPosition,
                                                        onCursorPositionChange = { cursorPosition = it },
                                                        onLongPress = {
                                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            showOverflowMenu = true
                                                        }
                                                    )

                                                    // Dropdown menu on long press
                                                    DropdownMenu(
                                                        shape = MaterialTheme.shapes.large,
                                                        expanded = showOverflowMenu,
                                                        onDismissRequest = { showOverflowMenu = false },
                                                        offset = DpOffset(0.dp, 8.dp)
                                                    ) {
                                                        val pasteText = clipboard.getText()?.text
                                                            ?.filter { it.isDigit() || it == '+' || it == '*' || it == '#' } ?: ""
                                                        if (number.isNotEmpty()) {
                                                            DropdownMenuItem(
                                                                contentPadding = PaddingValues(horizontal = 20.dp),
                                                                text = { Text(stringResource(R.string.copy)) },
                                                                onClick = {
                                                                    showOverflowMenu = false
                                                                    clipboard.setText(AnnotatedString(number))
                                                                }
                                                            )
                                                            DropdownMenuItem(
                                                                contentPadding = PaddingValues(horizontal = 20.dp),
                                                                text = { Text(stringResource(R.string.add_pause)) },
                                                                onClick = {
                                                                    showOverflowMenu = false
                                                                    insertAtCursor(",")
                                                                }
                                                            )
                                                            DropdownMenuItem(
                                                                contentPadding = PaddingValues(horizontal = 20.dp),
                                                                text = { Text(stringResource(R.string.add_wait)) },
                                                                onClick = {
                                                                    showOverflowMenu = false
                                                                    insertAtCursor(";")
                                                                }
                                                            )
                                                        }
                                                        if (pasteText.isNotEmpty()) {
                                                            DropdownMenuItem(
                                                                contentPadding = PaddingValues(horizontal = 20.dp),
                                                                text = { Text(stringResource(R.string.paste)) },
                                                                onClick = {
                                                                    showOverflowMenu = false
                                                                    replaceNumber(pasteText)
                                                                }
                                                            )
                                                        }
                                                    }
                                                }

                                                if (number.isNotEmpty()) {
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(
                                                        text = stringResource(R.string.create_contact),
                                                        style = MaterialTheme.typography.bodyMedium.copy(
                                                            fontWeight = FontWeight.Medium,
                                                            color = MaterialTheme.colorScheme.primary
                                                        ),
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .clickable {
                                                                val intent = Intent(Intent.ACTION_INSERT).apply {
                                                                    type = ContactsContract.RawContacts.CONTENT_TYPE
                                                                    putExtra(ContactsContract.Intents.Insert.PHONE, number)
                                                                }
                                                                try {
                                                                    context.startActivity(intent)
                                                                } catch (_: Exception) {}
                                                            }
                                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }

                                            // Dialpad keys
                                            val keys = listOf(
                                                listOf("1", "2", "3"),
                                                listOf("4", "5", "6"),
                                                listOf("7", "8", "9"),
                                                listOf("*", "0", "#")
                                            )
                                            val currentLocale = LocalConfiguration.current.locales[0] ?: java.util.Locale.getDefault()
                                            val subKeys = remember(currentLocale) { getDialpadSubKeys(currentLocale.language) }

                                            keys.forEach { row ->
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(keySpacing, Alignment.CenterHorizontally)
                                                ) {
                                                    row.forEach { key ->
                                                        DialPadKey(
                                                            number = key,
                                                            letters = subKeys[key] ?: "",
                                                            hapticsEnabled = appHapticsEnabled,
                                                            dtmfEnabled = dtmfEnabled,
                                                            onClick = { digit ->
                                                                insertAtCursor(digit)
                                                            },
                                                            onLongClick = { digit ->
                                                                insertAtCursor(digit)
                                                            },
                                                            overrideWidth = keySize,
                                                            overrideHeight = keySize,
                                                            scaleFactor = scaleFactor
                                                        )
                                                    }
                                                }
                                            }

                                            // Action row — iPhone style (Column 1 empty, Column 2 green circular Call, Column 3 Backspace)
                                            val top = if (isBottomSheet) 4.dp else 2.dp
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = (top * scaleFactor).coerceIn(2.dp, 8.dp)),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(keySpacing, Alignment.CenterHorizontally)
                                            ) {
                                                val lgBackdrop = LocalLiquidGlassBackdrop.current
                                                val lgDialpadEnabled = remember(settingsState) {
                                                    prefs.getBoolean(PreferenceManager.KEY_LIQUID_GLASS, false) &&
                                                            prefs.getBoolean(PreferenceManager.KEY_LG_DIALPAD_CALL_BUTTON, false)
                                                }
                                                val blurDialpadEnabled = remember(settingsState) {
                                                    prefs.getBoolean(PreferenceManager.KEY_BLUR_EFFECTS, false) &&
                                                            prefs.getBoolean(PreferenceManager.KEY_BLUR_DIALPAD_CALL_BUTTON, false) &&
                                                            !lgDialpadEnabled
                                                }

                                                // Column 1: Placeholder matching keySize to keep Call button perfectly centered
                                                Box(
                                                    modifier = Modifier.size(keySize),
                                                    contentAlignment = Alignment.Center
                                                ) {}

                                                // Column 2: Circular green Call button
                                                DialerActionExpressive(
                                                    onClick = {
                                                        if (number.isNotEmpty()) {
                                                            initiateCall(number)
                                                        } else {
                                                            val lastNum = logs.firstOrNull { it.type == CallLog.Calls.OUTGOING_TYPE }?.number
                                                                ?: logs.firstOrNull()?.number
                                                            if (!lastNum.isNullOrBlank()) {
                                                                replaceNumber(lastNum)
                                                            }
                                                        }
                                                    },
                                                    onLongClick = {
                                                        val pasteText = clipboard.getText()?.text
                                                            ?.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
                                                            ?: ""
                                                        if (number.isEmpty() && pasteText.isNotEmpty()) {
                                                            replaceNumber(pasteText)
                                                        } else {
                                                            clipboard.setText(AnnotatedString(number))
                                                        }
                                                    },
                                                    icon = Icons.Default.Call,
                                                    contentDescription = stringResource(R.string.call),
                                                    containerColor = color_call_button,
                                                    contentColor = Color.White,
                                                    modifier = Modifier.size(keySize),
                                                    isLarge = true,
                                                    liquidGlassBackdrop = lgBackdrop,
                                                    liquidGlassEnabled = lgDialpadEnabled,
                                                    blurEnabled = blurDialpadEnabled
                                                )

                                                // Column 3: Backspace button under '#'
                                                Box(
                                                    modifier = Modifier.size(keySize),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    val backspaceSource = remember { MutableInteractionSource() }
                                                    androidx.compose.animation.AnimatedVisibility(
                                                        visible = number.isNotEmpty(),
                                                        enter = fadeIn(tween(140)) + scaleIn(tween(140)),
                                                        exit = fadeOut(tween(120)) + scaleOut(tween(120))
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(keySize)
                                                                .clip(CircleShape)
                                                                .combinedClickable(
                                                                    onClick = { backspaceAtCursor() },
                                                                    onLongClick = { replaceNumber("") },
                                                                    interactionSource = backspaceSource,
                                                                    indication = ripple(bounded = true, radius = keySize / 2)
                                                                ),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.AutoMirrored.Outlined.Backspace,
                                                                contentDescription = stringResource(R.string.backspace),
                                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                modifier = Modifier.size((28 * scaleFactor).coerceIn(22f, 32f).dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Dialpad Card
                                    val dialpadCardShape = MaterialTheme.shapes.extraLargeIncreased
                                    val baseModifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = if (isBottomSheet) 12.dp else if (pillNav) 88.dp else bottomBarHeight + 12.dp)
                                        .navigationBarsPadding()
                                    if (useLgDialpad) {
                                        Surface(
                                            shape           = dialpadCardShape,
                                            color           = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.35f),
                                            shadowElevation = 0.dp,
                                            tonalElevation  = 0.dp,
                                            modifier = baseModifier.drawBackdrop(
                                                backdrop = globalBackdrop,
                                                shape = { dialpadCardShape },
                                                effects = {
                                                    val d = density
                                                    colorControls(saturation = 1.4f)
                                                    blur(blurIntensity * d)
                                                    lens(
                                                        refractionHeight = 18f * d,
                                                        refractionAmount = 52f * d
                                                    )
                                                },
                                                highlight = { Highlight.Default }
                                            )
                                        ) { dialpadContent() }
                                    } else if (useBlurDialpad && globalBackdrop != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                        Surface(
                                            shape           = dialpadCardShape,
                                            color           = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f),
                                            shadowElevation = 0.dp,
                                            tonalElevation  = 0.dp,
                                            modifier        = baseModifier.drawPlainBackdrop(
                                                    backdrop = globalBackdrop,
                                                    shape = { dialpadCardShape },
                                                    effects = { blur(blurIntensity * density) }
                                                )
                                        ) { dialpadContent() }
                                    } else {
                                        Surface(
                                            modifier = baseModifier,
                                            shape    = dialpadCardShape,
                                            color    = dialpadColor,
                                        ) { dialpadContent() }
                                    }
                                }
                            } // end BoxWithConstraints (dialpad card)
                        } // end AnimatedVisibility (dialpad card)

                        Spacer(modifier = Modifier.height(8.dp))
                    } // end outer Column
                } // end BoxWithConstraints (screen)
            }
        }
    } else {
        if (isGrantedLogs) {
            PermissionDeniedView(
                icon = Icons.Rounded.People,
                title = stringResource(R.string.contacts_permission),
                description = stringResource(R.string.contacts_permission_description),
                onGrantClick = { permStateContacts.launchPermissionRequest() }
            )
        } else {
            PermissionDeniedView(
                icon = Icons.Default.Call,
                title = stringResource(R.string.call_history),
                description = stringResource(R.string.call_history_permission),
                onGrantClick = { permStateLogs.launchPermissionRequest() }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DialerActionExpressive(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    containerColor: Color,
    modifier: Modifier = Modifier.size(64.dp),
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    isLarge: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    liquidGlassBackdrop: dev.goodwy.rphone.liquidglass.Backdrop? = null,
    liquidGlassEnabled: Boolean = false,
    blurEnabled: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val prefs = koinInject<PreferenceManager>()
    val haptic = LocalHapticFeedback.current
    val wrappedOnClick: () -> Unit = {
        if (prefs.getBoolean(PreferenceManager.KEY_APP_HAPTICS, true)) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        onClick()
    }
    val wrappedOnLongClick: (() -> Unit)? = if (onLongClick != null) ({
        if (prefs.getBoolean(PreferenceManager.KEY_APP_HAPTICS, true)) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        onLongClick()
    }) else null
    val cornerRadius by animateDpAsState(
        targetValue = if (isPressed) (if (isLarge) 42.dp else 14.dp) else (if (isLarge) 42.dp else 24.dp),
        animationSpec = tween(80, easing = FastOutSlowInEasing), label = "ButtonShape"
    )
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = tween(80, easing = FastOutSlowInEasing), label = "ButtonScale"
    )
    val buttonShape = if (isLarge) CircleShape else RoundedCornerShape(cornerRadius)
    val useLiquidGlass =
        liquidGlassEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && liquidGlassBackdrop != null
    val useBackdropBlur =
        blurEnabled && !useLiquidGlass && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    if (useLiquidGlass) {
        Box(
            modifier = modifier
                .scale(scale)
                .drawBackdrop(
                    backdrop = liquidGlassBackdrop,
                    shape = { buttonShape },
                    effects = {
                        val d = density
                        colorControls(saturation = 1.3f)
                        blur(2f * d)
                        lens(refractionHeight = 18f * d, refractionAmount = 52f * d)
                    },
                    highlight = { Highlight.Default }
                )
                .combinedClickable(
                    onClick = wrappedOnClick,
                    onLongClick = wrappedOnLongClick,
                    interactionSource = interactionSource,
                    indication = null
                )
        ) {
            Surface(
                shape = buttonShape,
                color = containerColor.copy(alpha = 0.5f),
                contentColor = contentColor,
                modifier = Modifier.matchParentSize()
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription,
                        modifier = Modifier.size(if (isLarge) 32.dp else 24.dp)
                    )
                }
            }
        }
    } else if (useBackdropBlur && liquidGlassBackdrop != null) {
        Surface(
            modifier = modifier
                .scale(scale)
                .drawPlainBackdrop(
                    backdrop = liquidGlassBackdrop,
                    shape = { buttonShape },
                    effects = { blur(30f * density) }
                )
                .combinedClickable(
                    onClick = wrappedOnClick,
                    onLongClick = wrappedOnLongClick,
                    interactionSource = interactionSource,
                    indication = null
                ),
            shape = buttonShape,
            color = containerColor.copy(alpha = 0.72f),
            contentColor = contentColor
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription,
                    modifier = Modifier.size(if (isLarge) 32.dp else 24.dp)
                )
            }
        }
    } else {
        Surface(
            modifier = modifier
                .scale(scale)
                .combinedClickable(
                    onClick = wrappedOnClick,
                    onLongClick = wrappedOnLongClick,
                    interactionSource = interactionSource,
                    indication = null
                ),
            shape = buttonShape,
            color = containerColor,
            contentColor = contentColor
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription,
                    modifier = Modifier.size(if (isLarge) 32.dp else 24.dp)
                )
            }
        }
    }
}

@Composable
fun DialPadKey(
    number: String,
    letters: String,
    onClick: (String) -> Unit,
    onLongClick: (String) -> Unit,
    hapticsEnabled: Boolean = true,
    dtmfEnabled: Boolean = false,
    compact: Boolean = false,
    overrideWidth: Dp? = null,
    overrideHeight: Dp? = null,
    scaleFactor: Float = 1f,
    modifier: Modifier? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current
    val isDark = isSystemInDarkTheme()

    val keyNormalColor = if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)
    val keyPressedColor = if (isDark) Color(0xFF48484A) else Color(0xFFD1D1D6)
    val numberColor = if (isDark) Color.White else Color.Black
    val lettersColor = if (isDark) Color(0xFF8E8E93) else Color(0xFF6C6C70)

    val keySize = overrideWidth ?: if (compact) 60.dp else (72 * scaleFactor).coerceIn(54f, 78f).dp
    val keyWidth = keySize
    val keyHeight = overrideHeight ?: keySize

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = tween(70, easing = FastOutSlowInEasing),
        label = "DialKeyScale"
    )

    val bgColor by animateColorAsState(
        targetValue = if (isPressed) keyPressedColor else keyNormalColor,
        animationSpec = tween(70, easing = FastOutSlowInEasing),
        label = "DialKeyColor"
    )

    val isCircular = modifier == null || !compact

    Surface(
        shape = if (isCircular) CircleShape else RoundedCornerShape(18.dp),
        color = bgColor,
        modifier = Modifier
            .then(
                modifier ?: Modifier.size(width = keyWidth, height = keyHeight)
            )
            .scale(scale)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .combinedClickable(
                    onClick = {
                        if (hapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (dtmfEnabled) DtmfAudioPlayer.play(number)
                        onClick(number)
                    },
                    onLongClick = {
                        if (hapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (dtmfEnabled) DtmfAudioPlayer.play(number)
                        val newNumber = when (number) {
                            "0" -> "+"
                            "*" -> ","
                            "#" -> ";"
                            else -> number
                        }
                        onLongClick(newNumber)
                    },
                    interactionSource = interactionSource,
                    indication = ripple(bounded = true)
                ),
            contentAlignment = Alignment.Center
        ) {
            val hasLetters = letters.isNotBlank()
            if (!hasLetters) {
                // Centered single symbol for "1", "*", "#"
                val singleFontSize = (when (number) {
                    "*" -> if (compact) 26f else 38f
                    "#" -> if (compact) 20f else 28f
                    else -> if (compact) 22f else 32f
                } * scaleFactor.coerceIn(0.65f, 1.3f)).sp

                Text(
                    text = number,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = singleFontSize,
                        fontWeight = FontWeight.Normal,
                        letterSpacing = 0.sp
                    ),
                    color = numberColor,
                    textAlign = TextAlign.Center
                )
            } else {
                // Column for digits with sub-letters (2-9 and 0)
                val mainFontSize = (if (compact) 20f else 32f) * scaleFactor.coerceIn(0.65f, 1.3f)
                val subFontSize = 10f * scaleFactor.coerceIn(0.65f, 1.3f)
                val isPlus = letters.trim() == "+"

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = number,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontSize = mainFontSize.sp,
                            fontWeight = FontWeight.Normal,
                            letterSpacing = 0.sp
                        ),
                        color = numberColor,
                        lineHeight = (mainFontSize * 1.05f).sp
                    )
                    Text(
                        text = letters.trim().uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = (if (isPlus) 13f else subFontSize).sp,
                            fontWeight = if (isPlus) FontWeight.Normal else FontWeight.Bold,
                            letterSpacing = if (isPlus) 0.sp else 2.sp
                        ),
                        color = lettersColor,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun DialpadNumberDisplay(
    number: String,
    fontSize: Int,
    cursorPosition: Int = number.length,
    onCursorPositionChange: (Int) -> Unit = {},
    onLongPress: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val textColor = if (isDark) Color.White else Color.Black

    Box(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(number) {
                detectTapGestures(
                    onLongPress = { onLongPress() },
                    onTap = { onCursorPositionChange(number.length) }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        if (number.isNotEmpty()) {
            Text(
                text = number,
                style = MaterialTheme.typography.displaySmall.copy(
                    fontSize = fontSize.sp,
                    fontWeight = FontWeight.Normal,
                    letterSpacing = 0.5.sp,
                    textAlign = TextAlign.Center
                ),
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

fun getDialpadSubKeys(language: String): Map<String, String> {
    return when (language) {
        "ru", "be", "uk" -> mapOf(
            "1" to "",
            "2" to "АБВГ",
            "3" to "ДЕЖЗ",
            "4" to "ИЙКЛ",
            "5" to "МНОП",
            "6" to "РСТУ",
            "7" to "ФХЦЧ",
            "8" to "ШЩЪЫ",
            "9" to "ЬЭЮЯ",
            "0" to "+"
        )
        else -> mapOf(
            "1" to "",
            "2" to "ABC",
            "3" to "DEF",
            "4" to "GHI",
            "5" to "JKL",
            "6" to "MNO",
            "7" to "PQRS",
            "8" to "TUV",
            "9" to "WXYZ",
            "0" to "+"
        )
    }
}

object T9Matcher {
    fun isMatch(contactName: String, query: String): Boolean {
        val normQuery = query.normalizeDigits()
        if (normQuery.isEmpty()) return false

        val startIndices = mutableListOf(0)
        for (i in 0 until contactName.length - 1) {
            val c = contactName[i]
            if (isWordSeparator(c)) {
                startIndices.add(i + 1)
            }
        }

        for (startIndex in startIndices) {
            var qIdx = 0
            var nIdx = startIndex

            while (qIdx < normQuery.length && nIdx < contactName.length) {
                val nC = contactName[nIdx]
                if (isWordSeparator(nC) || isIgnoredChar(nC)) {
                    nIdx++
                    continue
                }

                if (charToT9(nC) != normQuery[qIdx]) {
                    break
                }
                qIdx++
                nIdx++
            }

            if (qIdx == normQuery.length) return true
        }

        return false
    }

    private fun isWordSeparator(c: Char): Boolean =
        c == ' ' || c == '-' || c == '.' || c == '_' || c == '/' || c == '(' || c == ')'

    private fun isIgnoredChar(c: Char): Boolean =
        (c in '\u064B'..'\u0652') || c == '\u0640' || c == '\u200E' || c == '\u200F'

    private fun charToT9(c: Char): Char = when (c.uppercaseChar()) {
        // Latin
        'A', 'B', 'C' -> '2'
        'D', 'E', 'F' -> '3'
        'G', 'H', 'I' -> '4'
        'J', 'K', 'L' -> '5'
        'M', 'N', 'O' -> '6'
        'P', 'Q', 'R', 'S' -> '7'
        'T', 'U', 'V' -> '8'
        'W', 'X', 'Y', 'Z' -> '9'

        // Arabic
        'ا', 'أ', 'إ', 'آ', 'ٱ', 'ء', 'ب', 'ت', 'ث' -> '2'
        'ج', 'ح', 'خ' -> '3'
        'د', 'ذ', 'ر', 'ز' -> '4'
        'س', 'ش', 'ص', 'ض' -> '5'
        'ط', 'ظ', 'ع', 'غ' -> '6'
        'ف', 'ق', 'ك', 'ل', 'ک' -> '7'
        'م', 'ن', 'ه', 'ة' -> '8'
        'و', 'ي', 'ى', 'ئ', 'ؤ', 'ی' -> '9'

        // Cyrillic
        'А', 'Б', 'В', 'Г', 'Ґ' -> '2'
        'Д', 'Е', 'Ё', 'Є', 'Ж', 'З' -> '3'
        'И', 'І', 'Ї', 'Й', 'К', 'Л' -> '4'
        'М', 'Н', 'О', 'П' -> '5'
        'Р', 'С', 'Т', 'У' -> '6'
        'Ф', 'Х', 'Ц', 'Ч' -> '7'
        'Ш', 'Щ', 'Ъ', 'Ы' -> '8'
        'Ь', 'Э', 'Ю', 'Я' -> '9'

        // Digits
        '0' -> '0'
        '1' -> '1'
        '2' -> '2'
        '3' -> '3'
        '4' -> '4'
        '5' -> '5'
        '6' -> '6'
        '7' -> '7'
        '8' -> '8'
        '9' -> '9'
        else -> ' '
    }
}

object DtmfAudioPlayer {
    private val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
    private var toneGenerator: android.media.ToneGenerator? = null

    fun play(key: String) {
        val toneType = when (key) {
            "0" -> android.media.ToneGenerator.TONE_DTMF_0
            "1" -> android.media.ToneGenerator.TONE_DTMF_1
            "2" -> android.media.ToneGenerator.TONE_DTMF_2
            "3" -> android.media.ToneGenerator.TONE_DTMF_3
            "4" -> android.media.ToneGenerator.TONE_DTMF_4
            "5" -> android.media.ToneGenerator.TONE_DTMF_5
            "6" -> android.media.ToneGenerator.TONE_DTMF_6
            "7" -> android.media.ToneGenerator.TONE_DTMF_7
            "8" -> android.media.ToneGenerator.TONE_DTMF_8
            "9" -> android.media.ToneGenerator.TONE_DTMF_9
            "*" -> android.media.ToneGenerator.TONE_DTMF_S
            "#" -> android.media.ToneGenerator.TONE_DTMF_P
            else -> return
        }
        executor.execute {
            try {
                if (toneGenerator == null) {
                    toneGenerator = android.media.ToneGenerator(android.media.AudioManager.STREAM_DTMF, 80)
                }
                toneGenerator?.startTone(toneType, 100)
            } catch (_: Exception) {}
        }
    }
}

@Composable
private fun FadeScaleBox(modifier: Modifier, visible: Boolean, content: @Composable () -> Unit) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut()
        ) {
            content()
        }
    }
}
