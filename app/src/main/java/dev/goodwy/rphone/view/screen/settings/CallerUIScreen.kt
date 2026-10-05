package dev.goodwy.rphone.view.screen.settings

import android.content.Context
import android.content.Intent
import android.view.Surface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.Numbers
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.view.components.NavigationIcon
import dev.goodwy.rphone.view.components.RillAnimatedSection
import dev.goodwy.rphone.view.components.RillExpressiveCard
import dev.goodwy.rphone.view.theme.MyColors.cardColor
import dev.goodwy.rphone.view.theme.color_call_end
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.DonateScreenDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.goodwy.rphone.controller.PurchaseHelper
import dev.goodwy.rphone.controller.util.CallBackgroundStore
import dev.goodwy.rphone.view.components.SupportProjectItem
import dev.goodwy.rphone.view.components.Title
import dev.goodwy.rphone.view.components.RillSelectListItem
import dev.goodwy.rphone.view.components.shake
import dev.goodwy.rphone.view.theme.customColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun CallerUIScreen(navigator: DestinationsNavigator) {
    val context = LocalContext.current
    val prefs = koinInject<PreferenceManager>()
    val scope = rememberCoroutineScope()
    val settingsState by prefs.settingsChanged.collectAsStateWithLifecycle()


    val purchaseHelper: PurchaseHelper = koinInject()
    val isPro by purchaseHelper.isPro.collectAsStateWithLifecycle()
    val proCheckDone by purchaseHelper.proCheckDone.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        val savedIsProIap = prefs.getBoolean(PreferenceManager.KEY_IS_PRO_IAP, false)
        val savedIsProSub = prefs.getBoolean(PreferenceManager.KEY_IS_PRO_SUB, false)
        val savedIsProFoss = prefs.getBoolean(PreferenceManager.KEY_IS_PRO_FOSS, false)
        if (savedIsProIap || savedIsProSub || savedIsProFoss) {
            purchaseHelper.setProStatusImmediate(true)
            purchaseHelper.checkProStatus()
        } else {
            purchaseHelper.checkProStatus()
        }
    }
    var enabledShake by remember { mutableStateOf(false) }
    var showSnackbar   by remember(settingsState) { mutableStateOf(false) }

    var themeMode   by remember(settingsState) { mutableStateOf(prefs.getString(PreferenceManager.KEY_THEME_MODE, "auto") ?: "auto") }
    var hangupWidth by remember(settingsState) { mutableFloatStateOf(prefs.getFloat(PreferenceManager.KEY_HANGUP_WIDTH, 0.5f).coerceIn(0.1f, 1.0f)) }
    val rawCallerNameSize = prefs.getInt(PreferenceManager.KEY_CALLER_NAME_SIZE, 28)
    val initialCallerNameSize = when (rawCallerNameSize) {
        0 -> 28
        1 -> 36
        2 -> 46
        in 16..72 -> rawCallerNameSize
        else -> 28
    }
    var callerNameSize by remember(settingsState) { mutableIntStateOf(initialCallerNameSize) }

    val rawCallerNumberSize = prefs.getInt(PreferenceManager.KEY_CALLER_NUMBER_SIZE, 20)
    val initialCallerNumberSize = when (rawCallerNumberSize) {
        0 -> 20
        1 -> 28
        2 -> 38
        in 12..64 -> rawCallerNumberSize
        else -> 20
    }
    var callerNumberSize by remember(settingsState) { mutableIntStateOf(initialCallerNumberSize) }

    var defaultCallBg by remember { mutableStateOf<String?>(null) }
    var loadingBg by remember { mutableStateOf(true) }
    LaunchedEffect(settingsState) {
        loadingBg = true
        defaultCallBg = CallBackgroundStore.defaultModelAsync(context)
        loadingBg = false
    }
    var savingCallBg by remember { mutableStateOf(false) }
    val defaultCallBgLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            scope.launch {
                savingCallBg = true
                val ok = CallBackgroundStore.saveDefault(context, uri)
                savingCallBg = false
                if (ok) defaultCallBg = CallBackgroundStore.defaultModelAsync(context)
            }
        }
    }

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
                title = { Title(stringResource(R.string.сaller_ui)) },
                navigationIcon = {
                    NavigationIcon(onClick = { navigator.navigateUp() })
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        Box(modifier = Modifier
            .padding(
                top = padding.calculateTopPadding(),
                start = 0.dp,
                end = 0.dp,
                bottom = 0.dp
            )
            .fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                item {
                    RillExpressiveCard {
                        Column(
                            modifier = Modifier
                                .background(color = cardColor)
                                .padding(16.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.customColors.colorBlue,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Rounded.Wallpaper,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.customColors.colorDarkBlue,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Column {
                                    Text(
                                        stringResource(R.string.settings_call_default_background),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        if (defaultCallBg != null) {
                                            stringResource(R.string.settings_call_default_background_set)
                                        } else {
                                            stringResource(R.string.settings_call_default_background_none)
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        lineHeight = MaterialTheme.typography.bodyMedium.fontSize, //14.sp
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 2.dp)
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(20.dp))
                                    .aspectRatio(2f)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                contentAlignment = Alignment.Center
                            ) {
                                if (loadingBg) {
                                    CircularProgressIndicator()
                                } else if (defaultCallBg != null) {
                                    AsyncImage(
                                        model = defaultCallBg,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.Wallpaper,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(40.dp)
                                    )
                                }
                                if (savingCallBg) {
                                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = 2.dp)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val isDark = isSystemInDarkTheme()
                                val borderColor =
                                    if (!isPro) {
                                        if (themeMode == "dark" || (themeMode == "auto" && isDark)) BorderStroke(
                                            3.dp,
                                            MaterialTheme.colorScheme.customColors.colorPurple
                                        )
                                        else BorderStroke(
                                            3.dp,
                                            MaterialTheme.colorScheme.customColors.colorDarkPurple.copy(
                                                0.4f
                                            )
                                        )
                                    } else null
                                FilledTonalButton(
                                    onClick = {
                                        if (isPro) {
                                            defaultCallBgLauncher.launch(arrayOf("image/*"))
                                        } else {
                                            enabledShake = true
                                            showSnackbar = true
                                            scope.launch {
                                                delay(3000.milliseconds)
                                                showSnackbar = false
                                            }
                                        }
                                    },
                                    border = borderColor,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        Icons.Outlined.Edit,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        if (defaultCallBg != null) {
                                            stringResource(R.string.contact_call_background_change)
                                        } else {
                                            stringResource(R.string.contact_call_background_choose)
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (defaultCallBg != null) {
                                    OutlinedButton(
                                        onClick = {
                                            scope.launch {
                                                CallBackgroundStore.clearDefault(context)
                                                defaultCallBg = null
                                            }
                                        }
                                    ) {
                                        Icon(
                                            ImageVector.vectorResource(id = R.drawable.ic_delete),
                                            contentDescription = stringResource(R.string.contact_call_background_remove),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            stringResource(R.string.contact_call_background_remove),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Caller Font Sizes & Live Preview ──────────────────────────────────────
                item {
                    RillAnimatedSection(delayMs = 45L) {
                        Column {
                            SettingsSectionLabel(stringResource(R.string.caller_font_size_section))
                            RillExpressiveCard {
                                Column(
                                    modifier = Modifier
                                        .background(color = cardColor)
                                        .padding(16.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = MaterialTheme.colorScheme.customColors.colorPurple.copy(alpha = 0.15f),
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    Icons.Rounded.FormatSize,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.customColors.colorPurple,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                        Column {
                                            Text(
                                                stringResource(R.string.caller_font_size_section),
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Spacer(Modifier.height(2.dp))
                                            Text(
                                                stringResource(R.string.caller_name_size_subtitle),
                                                style = MaterialTheme.typography.bodySmall,
                                                lineHeight = MaterialTheme.typography.bodyMedium.fontSize,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Spacer(Modifier.height(20.dp))

                                    // Live preview card mimicking CallScreen
                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 18.dp, horizontal = 12.dp)
                                        ) {
                                            Text(
                                                text = stringResource(R.string.sample_calling),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(Modifier.height(8.dp))
                                            Text(
                                                text = stringResource(R.string.sample_caller_name),
                                                fontSize = callerNameSize.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                textAlign = TextAlign.Center,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                text = stringResource(R.string.sample_caller_number),
                                                fontSize = callerNumberSize.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = TextAlign.Center,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(Modifier.height(8.dp))
                                            Surface(
                                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text(
                                                    text = "etisalat by e&",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }

                                    Spacer(Modifier.height(20.dp))

                                    // Caller Name Size Slider
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            stringResource(R.string.caller_name_size),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            "${callerNameSize} sp",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.clickable {
                                                callerNameSize = 26
                                                prefs.setInt(PreferenceManager.KEY_CALLER_NAME_SIZE, 26)
                                            }
                                        )
                                    }
                                    Slider(
                                        value = callerNameSize.toFloat(),
                                        onValueChange = { callerNameSize = it.toInt() },
                                        onValueChangeFinished = {
                                            prefs.setInt(PreferenceManager.KEY_CALLER_NAME_SIZE, callerNameSize)
                                        },
                                        valueRange = 18f..60f,
                                        steps = 20,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("18 sp", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("38 sp", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("60 sp", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }

                                    Spacer(Modifier.height(16.dp))

                                    // Caller Number Size Slider
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            stringResource(R.string.caller_number_size),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            "${callerNumberSize} sp",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.clickable {
                                                callerNumberSize = 20
                                                prefs.setInt(PreferenceManager.KEY_CALLER_NUMBER_SIZE, 20)
                                            }
                                        )
                                    }
                                    Slider(
                                        value = callerNumberSize.toFloat(),
                                        onValueChange = { callerNumberSize = it.toInt() },
                                        onValueChangeFinished = {
                                            prefs.setInt(PreferenceManager.KEY_CALLER_NUMBER_SIZE, callerNumberSize)
                                        },
                                        valueRange = 14f..52f,
                                        steps = 18,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("14 sp", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("32 sp", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("52 sp", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Hang Up Button ────────────────────────────────────────
                item {
                    RillAnimatedSection(delayMs = 60L) {
                        Column {
                            SettingsSectionLabel(stringResource(R.string.end_call_button))
                            RillExpressiveCard {
                                Column(
                                    modifier = Modifier
                                        .background(color = cardColor)
                                        .padding(16.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = color_call_end.copy(alpha = 0.15f),
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    Icons.Rounded.CallEnd,
                                                    contentDescription = null,
                                                    tint = color_call_end,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                        Column {
                                            Text(
                                                stringResource(R.string.customize_width),
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Spacer(Modifier.height(2.dp))
                                            Text(
                                                stringResource(R.string.customize_width_subtitle),
                                                style = MaterialTheme.typography.bodySmall,
                                                lineHeight = MaterialTheme.typography.bodyMedium.fontSize, //14.sp
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Spacer(Modifier.height(24.dp))

                                    // Live preview
                                    Box(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        val isCircle = hangupWidth <= 0.1f
                                        Surface(
                                            shape = if (isCircle) CircleShape else RoundedCornerShape(
                                                42.dp
                                            ),
                                            color = color_call_end,
                                            modifier = if (isCircle) Modifier.size(64.dp)
                                            else Modifier.fillMaxWidth(
                                                hangupWidth.coerceIn(
                                                    0.1f,
                                                    1.0f
                                                )
                                            ).height(64.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    val showText = hangupWidth > 0.5f
                                                    Icon(
                                                        Icons.Rounded.CallEnd,
                                                        null,
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

                                    Spacer(Modifier.height(16.dp))

                                    // Slider
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Slider(
                                            value = hangupWidth,
                                            onValueChange = { hangupWidth = it },
                                            onValueChangeFinished = {
                                                prefs.setFloat(
                                                    PreferenceManager.KEY_HANGUP_WIDTH,
                                                    hangupWidth
                                                )
                                            },
                                            valueRange = 0.1f..1.0f,
                                            steps = 8,
                                            modifier = Modifier.weight(1f),
                                            colors = SliderDefaults.colors(
                                                thumbColor = color_call_end,
                                                activeTrackColor = color_call_end,
                                                inactiveTrackColor = color_call_end.copy(alpha = 0.3f)
                                            )
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            "10%",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            "${(hangupWidth * 100).toInt()}%",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.clickable(
                                                indication = ripple(
                                                    bounded = false,
                                                    radius = 24.dp
                                                ),
                                                interactionSource = null,
                                            ) {
                                                hangupWidth = 0.5f
                                            }
                                        )
                                        Text(
                                            "100%",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item { SettingsBottomPadding() }
            }

            AnimatedVisibility(
                modifier = Modifier
                    .align(Alignment.BottomCenter),
                visible = showSnackbar,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                Snackbar(
                    modifier = Modifier.navigationBarsPadding().padding(24.dp),
                    shape = MaterialTheme.shapes.large,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    action = {
                        TextButton(
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                            onClick = {
                                showSnackbar = false
                                navigator.navigate(DonateScreenDestination)
                            }
                        ) {
                            Text(stringResource(R.string.continue_support), color = MaterialTheme.colorScheme.primary)
                        }
                    },
                ) {
                    Text(
                        stringResource(R.string.support_project_to_unlock),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
