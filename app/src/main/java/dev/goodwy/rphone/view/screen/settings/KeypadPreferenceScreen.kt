package dev.goodwy.rphone.view.screen.settings

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.view.Surface
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.view.components.*
import dev.goodwy.rphone.view.screen.DialPadKey
import dev.goodwy.rphone.view.screen.getDialpadSubKeys
import dev.goodwy.rphone.view.theme.MyColors.cardColor
import dev.goodwy.rphone.view.theme.customColors
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun KeypadPreferenceScreen(navigator: DestinationsNavigator) {
    val prefs = koinInject<PreferenceManager>()
    val settingsState by prefs.settingsChanged.collectAsStateWithLifecycle()

    var keypadShape by remember(settingsState) {
        mutableIntStateOf(prefs.getKeypadShape())
    }
    var buttonSizeScale by remember(settingsState) {
        mutableFloatStateOf(prefs.getKeypadButtonSizeScale())
    }
    var digitSizeScale by remember(settingsState) {
        mutableFloatStateOf(prefs.getKeypadDigitSizeScale())
    }
    var numberDisplaySize by remember(settingsState) {
        mutableIntStateOf(prefs.getKeypadNumberSize())
    }
    var showLetters by remember(settingsState) {
        mutableStateOf(prefs.isKeypadShowLetters())
    }
    var dialpadAnimation by remember(settingsState) {
        mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_DIALPAD_ANIMATION, true))
    }
    var dialpadTones by remember(settingsState) {
        mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_DTMF_TONE, false))
    }
    var appHaptics by remember(settingsState) {
        mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_APP_HAPTICS, true))
    }

    var previewNumber by remember { mutableStateOf("12345") }

    val context = LocalContext.current
    val rotation =
        (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val isRotation90 = rotation == if (isLtr) Surface.ROTATION_90 else Surface.ROTATION_270

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets.systemBars.only(
                    if (isRotation90) WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                    else WindowInsetsSides.Top
                ),
                title = { Title(stringResource(R.string.keypad_settings)) },
                navigationIcon = {
                    NavigationIcon(onClick = { navigator.navigateUp() })
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(top = padding.calculateTopPadding())
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Live Keypad Interactive Preview ──────────────────────────────
            item {
                RillAnimatedSection(delayMs = 50L) {
                    Column {
                        SettingsSectionLabel(stringResource(R.string.keypad_preview))
                        RillExpressiveCard {
                            Column(
                                modifier = Modifier
                                    .background(color = cardColor)
                                    .padding(vertical = 16.dp, horizontal = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Typed Number Display
                                Text(
                                    text = previewNumber,
                                    fontSize = numberDisplaySize.sp,
                                    fontWeight = FontWeight.Normal,
                                    letterSpacing = 1.sp,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 14.dp)
                                )

                                val currentLocale = LocalConfiguration.current.locales[0] ?: java.util.Locale.getDefault()
                                val subKeys = remember(currentLocale) { getDialpadSubKeys(currentLocale.language) }
                                val previewRows = listOf(
                                    listOf("1", "2", "3"),
                                    listOf("4", "5", "6")
                                )

                                val previewKeySize = (64 * buttonSizeScale).coerceIn(44f, 80f).dp
                                val previewSpacing = (10 * buttonSizeScale.coerceAtMost(1.1f)).coerceIn(6f, 16f).dp

                                Column(verticalArrangement = Arrangement.spacedBy(previewSpacing)) {
                                    previewRows.forEach { row ->
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(previewSpacing, Alignment.CenterHorizontally),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            row.forEach { digit ->
                                                DialPadKey(
                                                    number = digit,
                                                    letters = if (showLetters) (subKeys[digit] ?: "") else "",
                                                    hapticsEnabled = appHaptics,
                                                    dtmfEnabled = dialpadTones,
                                                    overrideWidth = previewKeySize,
                                                    overrideHeight = previewKeySize,
                                                    scaleFactor = 1f,
                                                    digitScale = digitSizeScale,
                                                    keypadShape = keypadShape,
                                                    showLetters = showLetters,
                                                    onClick = {
                                                        if (previewNumber.length < 10) {
                                                            previewNumber += it
                                                        } else {
                                                            previewNumber = it
                                                        }
                                                    },
                                                    onLongClick = {
                                                        previewNumber = ""
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.examples_of_changes),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            // ── Keypad Shape Selection ───────────────────────────────────────
            item {
                RillAnimatedSection(delayMs = 70L) {
                    Column {
                        SettingsSectionLabel(stringResource(R.string.keypad_shape))
                        RillExpressiveCard {
                            RillSelectListItem(
                                headline = stringResource(R.string.keypad_shape),
                                supporting = stringResource(R.string.keypad_shape_subtitle),
                                leadingIcon = Icons.Rounded.Category,
                                iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkBlue,
                                iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorBlue,
                                options = listOf(
                                    stringResource(R.string.keypad_shape_circle) to PreferenceManager.KEYPAD_SHAPE_CIRCLE,
                                    stringResource(R.string.keypad_shape_squircle) to PreferenceManager.KEYPAD_SHAPE_SQUIRCLE,
                                    stringResource(R.string.keypad_shape_rounded_rect) to PreferenceManager.KEYPAD_SHAPE_ROUNDED_RECT,
                                    stringResource(R.string.keypad_shape_capsule) to PreferenceManager.KEYPAD_SHAPE_CAPSULE,
                                    stringResource(R.string.keypad_shape_minimal) to PreferenceManager.KEYPAD_SHAPE_MINIMAL,
                                    stringResource(R.string.keypad_shape_cut_corner) to PreferenceManager.KEYPAD_SHAPE_CUT_CORNER
                                ),
                                selectedValue = keypadShape,
                                onValueChange = {
                                    keypadShape = it
                                    prefs.setKeypadShape(it)
                                }
                            )

                            RillSwitchListItem(
                                headline = stringResource(R.string.keypad_show_letters),
                                supporting = stringResource(R.string.keypad_show_letters_subtitle),
                                leadingIcon = Icons.Rounded.Abc,
                                iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkGreen,
                                iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorGreen,
                                checked = showLetters,
                                onCheckedChange = {
                                    showLetters = it
                                    prefs.setKeypadShowLetters(it)
                                }
                            )
                        }
                    }
                }
            }

            // ── Sizing Customization ─────────────────────────────────────────
            item {
                RillAnimatedSection(delayMs = 90L) {
                    Column {
                        SettingsSectionLabel(stringResource(R.string.customize_width))
                        RillExpressiveCard {
                            // Button Size Scale Slider
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
                                        color = MaterialTheme.colorScheme.customColors.colorDarkPurple.copy(alpha = 0.15f),
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Rounded.AspectRatio,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.customColors.colorPurple,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Column {
                                        Text(
                                            stringResource(R.string.keypad_button_size),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            stringResource(R.string.keypad_button_size_subtitle),
                                            style = MaterialTheme.typography.bodySmall,
                                            lineHeight = MaterialTheme.typography.bodyMedium.fontSize,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Spacer(Modifier.height(16.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Slider(
                                        value = buttonSizeScale,
                                        onValueChange = { buttonSizeScale = it },
                                        onValueChangeFinished = {
                                            prefs.setKeypadButtonSizeScale(buttonSizeScale)
                                        },
                                        valueRange = 0.75f..1.25f,
                                        steps = 10,
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "75%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        "${(buttonSizeScale * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.clickable {
                                            buttonSizeScale = 1.0f
                                            prefs.setKeypadButtonSizeScale(1.0f)
                                        }
                                    )
                                    Text(
                                        "125%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            )

                            // Button Digits Font Size Slider (حجم الأرقام داخل الأزرار)
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
                                        color = MaterialTheme.colorScheme.customColors.colorDarkCyan.copy(alpha = 0.15f),
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Rounded.Pin,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.customColors.colorCyan,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Column {
                                        Text(
                                            stringResource(R.string.keypad_digit_size),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            stringResource(R.string.keypad_digit_size_subtitle),
                                            style = MaterialTheme.typography.bodySmall,
                                            lineHeight = MaterialTheme.typography.bodyMedium.fontSize,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Spacer(Modifier.height(16.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Slider(
                                        value = digitSizeScale,
                                        onValueChange = { digitSizeScale = it },
                                        onValueChangeFinished = {
                                            prefs.setKeypadDigitSizeScale(digitSizeScale)
                                        },
                                        valueRange = 0.70f..1.40f,
                                        steps = 13,
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "70%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        "${(digitSizeScale * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.clickable {
                                            digitSizeScale = 1.0f
                                            prefs.setKeypadDigitSizeScale(1.0f)
                                        }
                                    )
                                    Text(
                                        "140%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            )

                            // Number Display Font Size Slider
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
                                        color = MaterialTheme.colorScheme.customColors.colorDarkAmber.copy(alpha = 0.15f),
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Rounded.FormatSize,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.customColors.colorAmber,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Column {
                                        Text(
                                            stringResource(R.string.keypad_number_display_size),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            stringResource(R.string.keypad_number_display_size_subtitle),
                                            style = MaterialTheme.typography.bodySmall,
                                            lineHeight = MaterialTheme.typography.bodyMedium.fontSize,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Spacer(Modifier.height(16.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Slider(
                                        value = numberDisplaySize.toFloat(),
                                        onValueChange = { numberDisplaySize = it.toInt() },
                                        onValueChangeFinished = {
                                            prefs.setKeypadNumberSize(numberDisplaySize)
                                        },
                                        valueRange = 24f..48f,
                                        steps = 12,
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "24 sp",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        "$numberDisplaySize sp",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.clickable {
                                            numberDisplaySize = 36
                                            prefs.setKeypadNumberSize(36)
                                        }
                                    )
                                    Text(
                                        "48 sp",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── Animations & Feedback ────────────────────────────────────────
            item {
                RillAnimatedSection(delayMs = 110L) {
                    Column {
                        SettingsSectionLabel(stringResource(R.string.sound_and_vibration))
                        RillExpressiveCard {
                            RillSwitchListItem(
                                headline = stringResource(R.string.dialpad_animations),
                                supporting = stringResource(R.string.dialpad_animations_subtitle),
                                leadingIcon = Icons.Rounded._123,
                                iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkAmber,
                                iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorAmber,
                                checked = dialpadAnimation,
                                onCheckedChange = {
                                    dialpadAnimation = it
                                    prefs.setBoolean(PreferenceManager.KEY_DIALPAD_ANIMATION, it)
                                }
                            )

                            RillSwitchListItem(
                                headline = stringResource(R.string.dialpad_tones),
                                supporting = stringResource(R.string.dialpad_tones_subtitle),
                                leadingIcon = Icons.AutoMirrored.Rounded.VolumeUp,
                                iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkGreen,
                                iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorGreen,
                                checked = dialpadTones,
                                onCheckedChange = {
                                    dialpadTones = it
                                    prefs.setBoolean(PreferenceManager.KEY_DTMF_TONE, it)
                                }
                            )

                            RillSwitchListItem(
                                headline = stringResource(R.string.enable_tap_haptics),
                                supporting = stringResource(R.string.haptics_across_app),
                                leadingIcon = Icons.Rounded.Vibration,
                                iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkPurple,
                                iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorPurple,
                                checked = appHaptics,
                                onCheckedChange = {
                                    appHaptics = it
                                    prefs.setBoolean(PreferenceManager.KEY_APP_HAPTICS, it)
                                }
                            )
                        }
                    }
                }
            }

            item { SettingsBottomPadding() }
        }
    }
}
