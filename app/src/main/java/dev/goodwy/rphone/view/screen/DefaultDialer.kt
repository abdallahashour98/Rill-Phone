package dev.goodwy.rphone.view.screen

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.controller.util.getDefaultDialerIntent
import dev.goodwy.rphone.controller.util.isAlreadyDefaultDialer
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.ContactScreenDestination
import com.ramcosta.composedestinations.generated.destinations.DefaultDialerScreenDestination
import com.ramcosta.composedestinations.generated.destinations.RecentScreenDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import org.koin.compose.koinInject

@Destination<RootGraph>()
@Composable
fun DefaultDialerScreen(navController: NavController, navigator: DestinationsNavigator) {
    val context = LocalContext.current
    var isDenied by remember { mutableStateOf(false) }

    val prefs = koinInject<PreferenceManager>()
    val defBar = prefs.getInt(PreferenceManager.KEY_DEFAULT_BOTTOM_NAV, 0)

    val initialRoute = if (defBar == 0) {
        RecentScreenDestination
    } else {
        ContactScreenDestination
    }

    // Auto-detect when app becomes default dialer (initially or upon returning from system settings)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (isAlreadyDefaultDialer(context)) {
                    isDenied = false
                    navigator.navigate(initialRoute) {
                        popUpTo(DefaultDialerScreenDestination) { inclusive = true }
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        if (isAlreadyDefaultDialer(context)) {
            navigator.navigate(initialRoute) {
                popUpTo(DefaultDialerScreenDestination) { inclusive = true }
            }
        }
    }

    val roleRequestLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK || isAlreadyDefaultDialer(context)) {
            isDenied = false
            navigator.navigate(initialRoute) {
                popUpTo(DefaultDialerScreenDestination) { inclusive = true }
            }
        } else {
            isDenied = true
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val configuration = LocalConfiguration.current
            val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (!isLandscape) Box(
                    modifier = Modifier
                        .size(140.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Call,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = stringResource(R.string.default_dialer_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.default_dialer_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                AnimatedVisibility(visible = isDenied) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = stringResource(R.string.default_dialer_warning),
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.restricted_settings_hint),
                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f),
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        // Direct shortcut to System Default Apps
                        OutlinedButton(
                            onClick = {
                                try {
                                    val settingsIntent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                                    context.startActivity(settingsIntent)
                                } catch (_: Exception) {
                                    try {
                                        val appSettingsIntent = Intent(Settings.ACTION_SETTINGS)
                                        context.startActivity(appSettingsIntent)
                                    } catch (_: Exception) {}
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.open_default_apps_settings))
                        }

                        // Direct shortcut to App Info (to allow restricted settings)
                        TextButton(
                            onClick = {
                                try {
                                    val detailsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.fromParts("package", context.packageName, null)
                                    }
                                    context.startActivity(detailsIntent)
                                } catch (_: Exception) {}
                            }
                        ) {
                            Text(
                                text = stringResource(R.string.open_app_info_restricted),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = {
                        if (isAlreadyDefaultDialer(context)) {
                            navigator.navigate(initialRoute) {
                                popUpTo(DefaultDialerScreenDestination) { inclusive = true }
                            }
                            return@Button
                        }
                        try {
                            val intent = getDefaultDialerIntent(context)
                            roleRequestLauncher.launch(intent)
                        } catch (_: Exception) {
                            try {
                                context.startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
                            } catch (_: Exception) {
                                isDenied = true
                            }
                        }
                    },
                    modifier = Modifier.wrapContentSize(),
                    shape = MaterialTheme.shapes.extraLarge
                ) {
                    Text(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        text = stringResource(R.string.set_as_default),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                TextButton(
                    onClick = {
                        navigator.navigate(initialRoute) {
                            popUpTo(DefaultDialerScreenDestination) { inclusive = true }
                        }
                    }
                ) {
                    Text(
                        text = stringResource(R.string.continue_without_default),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}