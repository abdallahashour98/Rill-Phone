package dev.goodwy.rphone.view.screen.settings

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.view.Surface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.alias.CallerAlias
import dev.goodwy.rphone.controller.util.alias.CallerAliasManager
import dev.goodwy.rphone.view.components.NavigationIcon
import dev.goodwy.rphone.view.components.RillAnimatedSection
import dev.goodwy.rphone.view.components.RillDialog
import dev.goodwy.rphone.view.components.RillExpressiveCard
import dev.goodwy.rphone.view.components.RillSwitchListItem
import dev.goodwy.rphone.view.components.Title
import dev.goodwy.rphone.view.theme.customColors

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun CallerAliasScreen(
    navigator: DestinationsNavigator
) {
    val context = LocalContext.current
    val isFeatureEnabled by CallerAliasManager.isFeatureEnabled.collectAsStateWithLifecycle()
    val aliasList by CallerAliasManager.aliases.collectAsStateWithLifecycle()

    var showAddEditDialog by remember { mutableStateOf(false) }
    var editingAlias by remember { mutableStateOf<CallerAlias?>(null) }
    var aliasToDelete by remember { mutableStateOf<CallerAlias?>(null) }

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
                title = { Title(stringResource(R.string.caller_alias_title)) },
                navigationIcon = {
                    NavigationIcon(onClick = { navigator.navigateUp() })
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingAlias = null
                    showAddEditDialog = true
                },
                shape = RoundedCornerShape(17.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_masked_number))
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(
                    top = padding.calculateTopPadding(),
                    start = 0.dp,
                    end = 0.dp,
                    bottom = 0.dp
                )
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Master toggle section
            item {
                RillAnimatedSection(delayMs = 0L) {
                    RillExpressiveCard {
                        RillSwitchListItem(
                            headline = stringResource(R.string.caller_alias_enable),
                            supporting = stringResource(R.string.caller_alias_enable_desc),
                            leadingIcon = Icons.Rounded.Shield,
                            iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkGreen,
                            iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorGreen,
                            checked = isFeatureEnabled,
                            onCheckedChange = { CallerAliasManager.setFeatureEnabled(context, it) }
                        )
                    }
                }
            }

            // Info card
            item {
                RillAnimatedSection(delayMs = 30L) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Rounded.VisibilityOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(26.dp)
                            )
                            Text(
                                text = stringResource(R.string.caller_alias_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Masked Numbers List
            if (aliasList.isEmpty()) {
                item {
                    RillAnimatedSection(delayMs = 60L) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Outlined.PersonOff,
                                    contentDescription = null,
                                    modifier = Modifier.size(36.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = stringResource(R.string.no_masked_numbers),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.no_masked_numbers_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                        }
                    }
                }
            } else {
                item {
                    Text(
                        text = "${stringResource(R.string.caller_alias_title)} (${aliasList.size})",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }

                items(aliasList, key = { it.id }) { alias ->
                    RillAnimatedSection(delayMs = 60L) {
                        RillExpressiveCard {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        editingAlias = alias
                                        showAddEditDialog = true
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        if (alias.hideNumber) Icons.Outlined.Lock else Icons.Rounded.VisibilityOff,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = alias.aliasName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = alias.rawNumber,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (alias.hideNumber) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                                            ) {
                                                Text(
                                                    text = "••••",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                Switch(
                                    checked = alias.isEnabled,
                                    onCheckedChange = { checked ->
                                        CallerAliasManager.toggleAliasEnabled(context, alias.id, checked)
                                    }
                                )

                                IconButton(
                                    onClick = { aliasToDelete = alias }
                                ) {
                                    Icon(
                                        Icons.Default.DeleteOutline,
                                        contentDescription = stringResource(R.string.delete),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(60.dp))
            }
        }
    }

    // Add or Edit Dialog
    if (showAddEditDialog) {
        AddEditAliasDialog(
            initialAlias = editingAlias,
            onDismiss = {
                showAddEditDialog = false
                editingAlias = null
            },
            onSave = { savedAlias ->
                CallerAliasManager.addOrUpdateAlias(context, savedAlias)
                showAddEditDialog = false
                editingAlias = null
            }
        )
    }

    // Delete Confirmation Dialog
    aliasToDelete?.let { alias ->
        AlertDialog(
            onDismissRequest = { aliasToDelete = null },
            icon = {
                Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            },
            title = {
                Text(
                    text = stringResource(R.string.delete_alias),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.delete_alias_confirm, alias.aliasName)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        CallerAliasManager.deleteAlias(context, alias.id)
                        aliasToDelete = null
                    }
                ) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { aliasToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun AddEditAliasDialog(
    initialAlias: CallerAlias?,
    onDismiss: () -> Unit,
    onSave: (CallerAlias) -> Unit
) {
    val context = LocalContext.current
    var rawNumber by remember { mutableStateOf(initialAlias?.rawNumber ?: "") }
    var aliasName by remember { mutableStateOf(initialAlias?.aliasName ?: "") }
    var hideNumber by remember { mutableStateOf(initialAlias?.hideNumber ?: false) }

    // Preset suggestions
    val suggestions = listOf(
        stringResource(R.string.preset_private_number),
        stringResource(R.string.preset_unknown),
        stringResource(R.string.preset_customer_service),
        stringResource(R.string.preset_work),
        stringResource(R.string.preset_delivery),
        stringResource(R.string.preset_important)
    )

    // Contact picker launcher
    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickContact()
    ) { contactUri: Uri? ->
        if (contactUri != null) {
            try {
                val projection = arrayOf(
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                )
                context.contentResolver.query(contactUri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                        if (numIdx >= 0) {
                            val pickedNum = cursor.getString(numIdx) ?: ""
                            if (pickedNum.isNotBlank()) rawNumber = pickedNum
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    RillDialog(
        onDismissRequest = onDismiss,
        title = stringResource(if (initialAlias == null) R.string.add_masked_number else R.string.edit_masked_number),
        confirmButton = {
            Button(
                onClick = {
                    val finalAlias = initialAlias?.copy(
                        rawNumber = rawNumber.trim(),
                        cleanNumber = CallerAlias.cleanPhoneNumber(rawNumber),
                        last7Digits = CallerAlias.extractLast7(CallerAlias.cleanPhoneNumber(rawNumber)),
                        aliasName = aliasName.trim().ifEmpty { context.getString(R.string.preset_private_number) },
                        hideNumber = hideNumber
                    ) ?: CallerAlias(
                        rawNumber = rawNumber.trim(),
                        aliasName = aliasName.trim().ifEmpty { context.getString(R.string.preset_private_number) },
                        hideNumber = hideNumber
                    )
                    onSave(finalAlias)
                },
                enabled = rawNumber.isNotBlank()
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Phone number field with Pick Contact button
            OutlinedTextField(
                value = rawNumber,
                onValueChange = { rawNumber = it },
                label = { Text(stringResource(R.string.phone_number_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(onClick = { contactPickerLauncher.launch(null) }) {
                        Icon(
                            Icons.Outlined.Contacts,
                            contentDescription = stringResource(R.string.pick_contact),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )

            // Preset suggestions horizontal chips
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.quick_suggestions),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp)
                ) {
                    items(suggestions) { suggestion ->
                        val isSelected = aliasName == suggestion
                        FilterChip(
                            selected = isSelected,
                            onClick = { aliasName = suggestion },
                            label = { Text(suggestion) }
                        )
                    }
                }
            }

            // Custom alias name input
            OutlinedTextField(
                value = aliasName,
                onValueChange = { aliasName = it },
                label = { Text(stringResource(R.string.alias_name_label)) },
                placeholder = { Text(stringResource(R.string.alias_name_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Hide number toggle
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.hide_number_toggle),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(R.string.hide_number_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = hideNumber,
                        onCheckedChange = { hideNumber = it }
                    )
                }
            }
        }
    }
}
