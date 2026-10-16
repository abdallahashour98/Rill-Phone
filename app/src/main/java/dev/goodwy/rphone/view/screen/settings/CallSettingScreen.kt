package dev.goodwy.rphone.view.screen.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.telecom.TelecomManager
import android.telephony.SubscriptionManager
import android.view.Surface
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material.icons.rounded.PictureInPicture
import androidx.compose.material.icons.rounded.ScreenLockPortrait
import androidx.compose.material.icons.rounded.SettingsPhone
import androidx.compose.material.icons.rounded.SimCard
import androidx.compose.material.icons.rounded.SpatialTracking
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.goodwy.rphone.controller.util.ShizukuManager
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.view.components.NavigationIcon
import dev.goodwy.rphone.view.components.RillAnimatedSection
import dev.goodwy.rphone.view.components.RillExpressiveCard
import dev.goodwy.rphone.view.components.RillListItem
import dev.goodwy.rphone.view.components.RillSwitchListItem
import dev.goodwy.rphone.view.theme.customColors
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import org.koin.compose.koinInject
import androidx.core.net.toUri
import com.ramcosta.composedestinations.generated.destinations.QuickResponsesScreenDestination
import dev.goodwy.rphone.view.components.RillSelectListItem
import dev.goodwy.rphone.view.components.Title

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun CallSettingScreen(navigator: DestinationsNavigator) {
    val prefs = koinInject<PreferenceManager>()
    val context = LocalContext.current

    var proximityBg by remember { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_PROXIMITY_SENSOR, true)) }
    var pocketModePrevention by remember { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_POCKET_MODE, false)) }
    var floatingCall by remember { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_FLOATING_CALL, false)) }
    var directCallOnTap by remember { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_DIRECT_CALL_ON_TAP, false)) }
    var autoSpeaker by remember { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_AUTO_SPEAKER, false)) }
    var defaultSim by remember { mutableStateOf(prefs.getInt(PreferenceManager.KEY_DEFAULT_SIM, prefs.getDefaultSimIndexDefault())) }
    var fullscreenCalls by remember { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_ALWAYS_FULLSCREEN_CALLS, false)) }
    var showWifiCallingDialog by remember { mutableStateOf(false) }

    val subscriptionManager = remember {
        context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
    }
    val activeSubscriptions = remember {
        try {
            subscriptionManager?.activeSubscriptionInfoList ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    val isShizukuAvailable by ShizukuManager.isAvailable.collectAsStateWithLifecycle()
    val isShizukuGranted by ShizukuManager.isPermissionGranted.collectAsStateWithLifecycle()

    var showAudioSourceDialog by remember { mutableStateOf(false) }
    var currentAudioSource by remember {
        mutableStateOf(prefs.getString("call_recording_audio_source", "mic-voice-communication"))
    }

    LaunchedEffect(Unit) {
        ShizukuManager.ensureInitialized()
        ShizukuManager.checkStatus()
    }

    LaunchedEffect(isShizukuAvailable, isShizukuGranted) {
        if (isShizukuAvailable && isShizukuGranted) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val serverPath = dev.goodwy.rphone.controller.util.shizuku.ScrcpyConfig.getServerPath(context)
                    dev.goodwy.rphone.controller.util.shizuku.ServerExtractor.ensureServerFile(context, serverPath)
                } catch (_: Exception) {}
            }
        }
    }

    var visible by remember { mutableStateOf(false) }
    val screenAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(350),
        label = "callSettingsAlpha"
    )
    LaunchedEffect(Unit) { visible = true }

    val rotation =
        (context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay.rotation
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val isRotation90 = rotation == if (isLtr) Surface.ROTATION_90 else Surface.ROTATION_270
    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets.systemBars.only(
                    if (isRotation90) WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                    else WindowInsetsSides.Top
                ),
                title = { Title(stringResource(R.string.call_settings)) },
                navigationIcon = {
                    NavigationIcon(onClick = { navigator.navigateUp() })
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = padding.calculateTopPadding(),
                    start = 0.dp,
                    end = 0.dp,
                    bottom = 0.dp
                )
                .alpha(screenAlpha),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Caller Accounts ───────────────────────────────────────────────
            item {
                RillAnimatedSection(delayMs = 0L) {
                    Column {
                        SettingsSectionLabel("SIM")
                        RillExpressiveCard {
                            RillSelectListItem(
                                headline = stringResource(R.string.default_sim),
                                leadingIcon = Icons.Rounded.SimCard,
                                iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkGreen,
                                iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorGreen,
                                options = listOf(
                                    stringResource(R.string.ask_first) to 0,
                                    "SIM 1" to 1,
                                    "SIM 2" to 2
                                ),
                                selectedValue = defaultSim,
                                onValueChange = { newValue: Int ->
                                    defaultSim = newValue
                                    prefs.setInt(PreferenceManager.KEY_DEFAULT_SIM, newValue)
                                }
                            )
                            RillListItem(
                                headline = stringResource(R.string.calling_accounts),
                                supporting = stringResource(R.string.system_settings),
                                leadingIcon = Icons.Rounded.SettingsPhone,
                                iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkGreen,
                                iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorGreen,
                                trailingIcon = Icons.Default.ChevronRight,
                                onClick = {
                                    try {
                                        val intent = Intent(TelecomManager.ACTION_CHANGE_PHONE_ACCOUNTS).apply {
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                        context.startActivity(intent)
                                    } catch (_: Exception) {
                                        try {
                                            val intent = Intent("android.telecom.action.SHOW_CALL_SETTINGS").apply {
                                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                            }
                                            context.startActivity(intent)
                                        } catch (_: Exception) {
                                            try {
                                                val intent = Intent(android.provider.Settings.ACTION_SETTINGS).apply {
                                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                                }
                                                context.startActivity(intent)
                                            } catch (_: Exception) {}
                                        }
                                    }
                                }
                            )
                            RillListItem(
                                headline = stringResource(R.string.wifi_calling),
                                supporting = stringResource(R.string.wifi_calling_subtitle),
                                leadingIcon = Icons.Rounded.Wifi,
                                iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkBlue,
                                iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorBlue,
                                trailingIcon = Icons.Default.ChevronRight,
                                onClick = { showWifiCallingDialog = true }
                            )
                        }
                    }
                }
            }

            // ── Call Behavior ─────────────────────────────────────────────────
            item {
                RillAnimatedSection(delayMs = 60L) {
                    Column {
                        SettingsSectionLabel(stringResource(R.string.call_behavior))
                        RillExpressiveCard {
                            RillListItem(
                                headline = stringResource(R.string.quick_responses),
                                supporting = stringResource(R.string.quick_responses_subtitle),
                                leadingIcon = ImageVector.vectorResource(id = R.drawable.ic_message_filled),
                                iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkAmber,
                                iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorAmber,
                                trailingIcon = Icons.Default.ChevronRight,
                                onClick = { navigator.navigate(QuickResponsesScreenDestination) }
                            )
                            RillSwitchListItem(
                                headline   = stringResource(R.string.proximity_sensor),
                                supporting = stringResource(R.string.proximity_sensor_subtitle),
                                leadingIcon = Icons.Rounded.SpatialTracking,
                                iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkOrange,
                                iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorOrange,
                                checked = proximityBg,
                                onCheckedChange = {
                                    proximityBg = it
                                    prefs.setBoolean(PreferenceManager.KEY_PROXIMITY_SENSOR, it)
                                }
                            )
                            RillSwitchListItem(
                                headline   = stringResource(R.string.pocket_mode_prevention),
                                supporting = stringResource(R.string.pocket_mode_prevention_subtitle),
                                leadingIcon = Icons.Rounded.ScreenLockPortrait,
                                iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkOrange,
                                iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorOrange,
                                checked = pocketModePrevention,
                                onCheckedChange = {
                                    pocketModePrevention = it
                                    prefs.setBoolean(PreferenceManager.KEY_POCKET_MODE, it)
                                }
                            )
                            RillSwitchListItem(
                                headline   = stringResource(R.string.floating_ongoing_call),
                                supporting = stringResource(R.string.floating_ongoing_call_subtitle),
                                leadingIcon = Icons.Rounded.PictureInPicture,
                                iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkGreen,
                                iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorGreen,
                                checked = floatingCall,
                                onCheckedChange = { newValue ->
                                    if (newValue && !Settings.canDrawOverlays(context)) {
                                        context.startActivity(
                                            Intent(
                                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                "package:${context.packageName}".toUri()
                                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                    } else {
                                        floatingCall = newValue
                                        prefs.setBoolean(PreferenceManager.KEY_FLOATING_CALL, newValue)
                                    }
                                }
                            )
                            RillSwitchListItem(
                                headline   = stringResource(R.string.fullscreen_calls),
                                supporting = stringResource(R.string.fullscreen_calls_subtitle),
                                leadingIcon = Icons.Rounded.CropFree,
                                iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkGreen,
                                iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorGreen,
                                checked = fullscreenCalls,
                                onCheckedChange = {
                                    fullscreenCalls = it
                                    prefs.setBoolean(PreferenceManager.KEY_ALWAYS_FULLSCREEN_CALLS, it)
                                }
                            )
                            RillSwitchListItem(
                                headline   = stringResource(R.string.direct_call_on_tap),
                                supporting = stringResource(R.string.direct_call_on_tap_subtitle),
                                leadingIcon = Icons.Rounded.TouchApp,
                                iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkGreen,
                                iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorGreen,
                                checked = directCallOnTap,
                                onCheckedChange = {
                                    directCallOnTap = it
                                    prefs.setBoolean(PreferenceManager.KEY_DIRECT_CALL_ON_TAP, it)
                                }
                            )
//                            RillSwitchListItem(
//                                headline   = stringResource(R.string.auto_speaker),
//                                supporting = stringResource(R.string.auto_speaker_subtitle),
//                                leadingIcon = Icons.AutoMirrored.Rounded.VolumeUp,
//                                iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkAmber,
//                                iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorAmber,
//                                checked = autoSpeaker,
//                                onCheckedChange = {
//                                    autoSpeaker = it
//                                    prefs.setBoolean(PreferenceManager.KEY_AUTO_SPEAKER, it)
//                                }
//                            )
                        }
                    }
                }
            }

            // ── Call Recording ────────────────────────────────────────────────
            item {
                RillAnimatedSection(delayMs = 120L) {
                    Column {
                        SettingsSectionLabel(stringResource(R.string.call_recording))
                        RillExpressiveCard {
                            val statusHeadline = stringResource(R.string.shizuku_call_recording)
                            val sourceName = when (currentAudioSource) {
                                "voice-call" -> stringResource(R.string.audio_source_voice_call)
                                "mic" -> stringResource(R.string.audio_source_mic)
                                else -> stringResource(R.string.audio_source_voice_comm)
                            }
                            val statusSupporting = when {
                                isShizukuAvailable && isShizukuGranted -> "${stringResource(R.string.shizuku_status_connected)} • $sourceName"
                                isShizukuAvailable -> stringResource(R.string.shizuku_status_permission_needed)
                                else -> stringResource(R.string.shizuku_status_disconnected)
                            }
                            val iconColor = when {
                                isShizukuAvailable && isShizukuGranted -> MaterialTheme.colorScheme.customColors.colorDarkGreen
                                isShizukuAvailable -> MaterialTheme.colorScheme.customColors.colorDarkAmber
                                else -> MaterialTheme.colorScheme.customColors.colorDarkRed
                            }
                            val iconBgColor = when {
                                isShizukuAvailable && isShizukuGranted -> MaterialTheme.colorScheme.customColors.colorGreen
                                isShizukuAvailable -> MaterialTheme.colorScheme.customColors.colorAmber
                                else -> MaterialTheme.colorScheme.customColors.colorRed
                            }

                            RillListItem(
                                headline = statusHeadline,
                                supporting = statusSupporting,
                                leadingIcon = Icons.Rounded.Mic,
                                iconContainerColor = iconColor,
                                iconBgContainerColor = iconBgColor,
                                trailingIcon = Icons.Default.ChevronRight,
                                onClick = {
                                    if (isShizukuAvailable && !isShizukuGranted) {
                                        ShizukuManager.requestPermission()
                                    } else if (!isShizukuAvailable) {
                                        ShizukuManager.launchShizukuApp(context)
                                    } else {
                                        showAudioSourceDialog = true
                                    }
                                }
                            )
                        }
                    }
                }
            }

            item { SettingsBottomPadding() }
        }

        if (showWifiCallingDialog) {
            AlertDialog(
                onDismissRequest = { showWifiCallingDialog = false },
                icon = {
                    Icon(
                        Icons.Rounded.Wifi,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                },
                title = {
                    Text(
                        text = stringResource(R.string.wifi_calling),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (activeSubscriptions.isNotEmpty()) {
                            activeSubscriptions.forEach { sub ->
                                val simName = sub.displayName?.toString()?.ifBlank { null }
                                    ?: sub.carrierName?.toString()?.ifBlank { null }
                                    ?: "SIM ${sub.simSlotIndex + 1}"
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    onClick = {
                                        showWifiCallingDialog = false
                                        openWifiCalling(context, sub.subscriptionId)
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.Wifi,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = stringResource(R.string.wifi_calling_direct, simName),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = sub.carrierName?.toString()?.ifBlank { null } ?: "Slot ${sub.simSlotIndex + 1}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                onClick = {
                                    showWifiCallingDialog = false
                                    openWifiCalling(context)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.Wifi,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(R.string.wifi_calling),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }

                        // SIM & Mobile Network option (reliable on all OEMs)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            onClick = {
                                showWifiCallingDialog = false
                                openNetworkOperatorSettings(context, activeSubscriptions.firstOrNull()?.subscriptionId)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.SimCard,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.wifi_calling_sim_settings),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = stringResource(R.string.wifi_calling_subtitle),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Xiaomi carrier check hint with dial shortcut
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.wifi_calling_xiaomi_hint),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                FilledTonalButton(
                                    onClick = {
                                        showWifiCallingDialog = false
                                        try {
                                            val dialIntent = Intent(Intent.ACTION_DIAL, "tel:${Uri.encode("*#*#869434#*#*")}".toUri()).apply {
                                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                            }
                                            context.startActivity(dialIntent)
                                        } catch (_: Exception) {}
                                    },
                                    modifier = Modifier.align(Alignment.End),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.dial_code, "*#*#869434#*#*"),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showWifiCallingDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }

        if (showAudioSourceDialog) {
            AlertDialog(
                onDismissRequest = { showAudioSourceDialog = false },
                title = {
                    Text(
                        text = stringResource(R.string.call_recording_audio_source),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "mic-voice-communication" to stringResource(R.string.audio_source_voice_comm),
                            "voice-call" to stringResource(R.string.audio_source_voice_call),
                            "mic" to stringResource(R.string.audio_source_mic)
                        ).forEach { (key, label) ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (currentAudioSource == key)
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                else
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                onClick = {
                                    currentAudioSource = key
                                    prefs.setString("call_recording_audio_source", key)
                                    showAudioSourceDialog = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = currentAudioSource == key,
                                        onClick = {
                                            currentAudioSource = key
                                            prefs.setString("call_recording_audio_source", key)
                                            showAudioSourceDialog = false
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (currentAudioSource == key) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showAudioSourceDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }
    }
}

private fun openWifiCalling(context: Context, subId: Int? = null) {
    val targetSubId = subId
        ?: SubscriptionManager.getDefaultVoiceSubscriptionId().takeIf { it != SubscriptionManager.INVALID_SUBSCRIPTION_ID }
        ?: SubscriptionManager.getDefaultSubscriptionId().takeIf { it != SubscriptionManager.INVALID_SUBSCRIPTION_ID }
        ?: try {
            val subManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            subManager?.activeSubscriptionInfoList?.firstOrNull()?.subscriptionId
        } catch (_: Exception) { null }

    val intent = Intent("android.settings.WIFI_CALLING_SETTINGS").apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
        if (targetSubId != null && targetSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            putExtra(Settings.EXTRA_SUB_ID, targetSubId)
            putExtra("android.telephony.extra.SUBSCRIPTION_INDEX", targetSubId)
            putExtra("subscription", targetSubId)
            putExtra("subscription_id", targetSubId)
            putExtra("sub_id", targetSubId)
            putExtra("simId", targetSubId)
            putExtra("slot", 0)
        }
    }
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        openNetworkOperatorSettings(context, targetSubId)
    }
}

private fun openNetworkOperatorSettings(context: Context, subId: Int? = null) {
    try {
        val intent = Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            if (subId != null && subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                putExtra(Settings.EXTRA_SUB_ID, subId)
                putExtra("android.telephony.extra.SUBSCRIPTION_INDEX", subId)
                putExtra("subscription", subId)
                putExtra("sub_id", subId)
            }
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        try {
            context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
        } catch (_: Exception) {
            try {
                context.startActivity(Intent(Settings.ACTION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                })
            } catch (_: Exception) {}
        }
    }
}
