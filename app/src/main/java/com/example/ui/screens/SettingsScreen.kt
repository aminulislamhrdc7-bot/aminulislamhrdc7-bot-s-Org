package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SwitchAccount
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.data.ExportHelper
import com.example.model.AppLanguage
import com.example.model.CurrencyCode
import com.example.model.DEFAULT_ADMIN_ACCOUNT_ID
import com.example.model.ThemeMode
import com.example.ui.HisabUiState
import com.example.ui.components.CurrencySelectorRow
import com.example.ui.components.onGoogleSignInClicked
import com.example.ui.components.signOutUser
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    state: HisabUiState,
    onSetLanguage: (AppLanguage) -> Unit,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetPreferredCurrency: (CurrencyCode) -> Unit,
    onSaveExchangeRate: (Double) -> Unit,
    onSetPin: (String) -> Unit,
    onDisablePin: () -> Unit,
    onBuildBackupJson: () -> String,
    onRestoreBackupJson: suspend (String) -> Result<Int>,
    onOpenAdminPanel: () -> Unit,
    onSwitchToUserLogin: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    val strings = state.strings

    var rateText by remember(state.sarToBdtRate) { mutableStateOf(state.sarToBdtRate.toString()) }
    var showPinSetupDialog by remember { mutableStateOf(false) }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            val json = onBuildBackupJson()
            ExportHelper.writeTextFile(context, uri, json)
                .onSuccess {
                    Toast.makeText(context, strings.exportSuccess, Toast.LENGTH_SHORT).show()
                }
                .onFailure { err ->
                    Toast.makeText(context, err.localizedMessage ?: "Export failed", Toast.LENGTH_SHORT).show()
                }
        }
    }

    val restoreBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            ExportHelper.readTextFile(context, uri)
                .onSuccess { json ->
                    scope.launch {
                        onRestoreBackupJson(json)
                            .onSuccess { count ->
                                Toast.makeText(
                                    context,
                                    "$count${strings.restoreSuccessCount}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            .onFailure { err ->
                                Toast.makeText(
                                    context,
                                    err.localizedMessage ?: "Restore failed",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                    }
                }
                .onFailure { err ->
                    Toast.makeText(context, err.localizedMessage ?: "Read failed", Toast.LENGTH_SHORT).show()
                }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = strings.settingsTitle,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        // User Session & Admin Panel Section
        item {
            SettingsCard(title = strings.currentLoggedInUser) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = if (state.isAdminSession) Icons.Filled.AdminPanelSettings else Icons.Filled.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = state.activeDisplayName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        val idSubtitle = if (state.activeAccountId == DEFAULT_ADMIN_ACCOUNT_ID) {
                            strings.roleAdmin
                        } else {
                            "ID: ${state.activeAccountId}"
                        }
                        Text(
                            text = idSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.isAdminSession) {
                        Button(
                            onClick = onOpenAdminPanel,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("settings_admin_panel_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Filled.AdminPanelSettings, contentDescription = strings.openAdminPanel)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(strings.openAdminPanel)
                        }
                    }
                    OutlinedButton(
                        onClick = onSwitchToUserLogin,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("switch_user_login_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.SwitchAccount, contentDescription = strings.switchToUserLogin)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.switchToUserLogin)
                    }
                }
            }
        }

        // Language Section
        item {
            SettingsCard(title = strings.languageLabel) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilterChip(
                        selected = state.language == AppLanguage.BN,
                        onClick = { onSetLanguage(AppLanguage.BN) },
                        label = { Text(strings.bengaliLabel) },
                        modifier = Modifier.weight(1f).testTag("lang_bn_chip")
                    )
                    FilterChip(
                        selected = state.language == AppLanguage.EN,
                        onClick = { onSetLanguage(AppLanguage.EN) },
                        label = { Text(strings.englishLabel) },
                        modifier = Modifier.weight(1f).testTag("lang_en_chip")
                    )
                }
            }
        }

        // Currency & Exchange Rate Section
        item {
            SettingsCard(title = strings.defaultCurrencyLabel) {
                CurrencySelectorRow(
                    selected = state.preferredCurrency,
                    onSelect = onSetPreferredCurrency
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(strings.exchangeRateLabel, style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = rateText,
                        onValueChange = { rateText = it },
                        placeholder = { Text(strings.exchangeRateHint) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("exchange_rate_input")
                    )
                    Button(
                        onClick = {
                            val parsed = rateText.toDoubleOrNull()
                            if (parsed != null && parsed > 0.0) {
                                onSaveExchangeRate(parsed)
                                Toast.makeText(context, "1 SAR = $parsed BDT", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.testTag("save_rate_button")
                    ) {
                        Text(strings.saveRate)
                    }
                }
            }
        }

        // Dark Mode / Theme Section
        item {
            SettingsCard(title = strings.appearanceLabel) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = state.prefs.themeMode == mode,
                            onClick = { onSetThemeMode(mode) },
                            label = { Text(strings.themeLabel(mode)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // PIN Lock Section
        item {
            SettingsCard(title = strings.securityAppLock) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { showPinSetupDialog = true },
                        modifier = Modifier.weight(1f).testTag("set_pin_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.Lock, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (state.prefs.appLockEnabled) strings.changePin else strings.enablePinLock)
                    }
                    if (state.prefs.appLockEnabled) {
                        OutlinedButton(
                            onClick = onDisablePin,
                            modifier = Modifier.weight(1f).testTag("disable_pin_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(strings.disablePin)
                        }
                    }
                }
            }
        }

        // Backup & Restore Section
        item {
            SettingsCard(title = strings.backupAndRestore) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { exportBackupLauncher.launch("my_hisab_backup.json") },
                        modifier = Modifier.weight(1f).testTag("export_backup_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(strings.exportBackupJson, maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = { restoreBackupLauncher.launch(arrayOf("application/json", "*/*")) },
                        modifier = Modifier.weight(1f).testTag("restore_backup_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.Restore, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(strings.restoreBackupJson, maxLines = 1)
                    }
                }
            }
        }

        // Account & Cloud Sync
        item {
            val user = Firebase.auth.currentUser
            var isConnectingGoogle by remember { mutableStateOf(false) }
            var googleError by remember { mutableStateOf<String?>(null) }

            SettingsCard(title = strings.accountSection) {
                if (user != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CloudDone,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = user.displayName ?: user.email ?: "Google User",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = user.email ?: strings.syncedWithCloud,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { signOutUser(credentialManager, scope) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth().testTag("sign_out_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = strings.signOut)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.signOut)
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.OfflinePin,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = strings.localModeTitle,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = strings.localModeSubtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (googleError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = googleError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            googleError = null
                            isConnectingGoogle = true
                            onGoogleSignInClicked(
                                context = context,
                                credentialManager = credentialManager,
                                onAuthSuccess = { isConnectingGoogle = false },
                                onAuthError = { msg ->
                                    isConnectingGoogle = false
                                    googleError = msg
                                },
                                scope = scope,
                                onAuthCancelled = { isConnectingGoogle = false }
                            )
                        },
                        enabled = !isConnectingGoogle,
                        modifier = Modifier.fillMaxWidth().testTag("google_sign_in_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isConnectingGoogle) {
                            CircularProgressIndicator(
                                modifier = Modifier.width(18.dp).height(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Filled.CloudDone, contentDescription = strings.connectGoogleCloudSync)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(strings.connectGoogleCloudSync)
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }

    if (showPinSetupDialog) {
        var pin1 by remember { mutableStateOf("") }
        var pin2 by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showPinSetupDialog = false },
            title = { Text(strings.setPinTitle, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = pin1,
                        onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) { pin1 = it; error = null } },
                        label = { Text("PIN (4-6)") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("new_pin_input")
                    )
                    OutlinedTextField(
                        value = pin2,
                        onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) { pin2 = it; error = null } },
                        label = { Text("Confirm PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("confirm_pin_input")
                    )
                    if (error != null) {
                        Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        when {
                            pin1.length < 4 -> error = strings.pinTooShort
                            pin1 != pin2 -> error = strings.pinMismatch
                            else -> {
                                onSetPin(pin1)
                                showPinSetupDialog = false
                            }
                        }
                    },
                    modifier = Modifier.testTag("save_pin_button")
                ) { Text(strings.save) }
            },
            dismissButton = {
                TextButton(onClick = { showPinSetupDialog = false }) { Text(strings.cancel) }
            }
        )
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}
