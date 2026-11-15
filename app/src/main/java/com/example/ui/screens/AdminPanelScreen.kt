package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwitchAccount
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.CurrencyCode
import com.example.model.DEFAULT_ADMIN_LOGIN_ID
import com.example.model.ManagedUser
import com.example.model.UserRole
import com.example.ui.HisabUiState
import com.example.ui.UserFinancialSummary
import com.example.ui.components.UserDetailsDialog
import com.example.ui.strings.AppStrings
import com.example.ui.strings.formatCurrency
import com.example.ui.theme.ExpenseCoral
import com.example.ui.theme.IncomeGreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPanelScreen(
    state: HisabUiState,
    onBack: () -> Unit,
    onCreateUser: suspend (loginId: String, fullName: String, rawPassword: String, role: UserRole, isActive: Boolean) -> String?,
    onUpdateUser: suspend (user: ManagedUser, newFullName: String, newRawPassword: String?, newRole: UserRole, newActive: Boolean) -> String?,
    onToggleActive: (ManagedUser) -> Unit,
    onDeleteUser: (String) -> Unit,
    onLoginAsUser: (ManagedUser) -> Unit,
    onSwitchToUserLogin: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = state.strings
    val users = state.managedUsers
    val activeCount = users.count { it.isActive }
    val inactiveCount = users.size - activeCount

    var showCreateDialog by remember { mutableStateOf(false) }
    var editingUser by remember { mutableStateOf<ManagedUser?>(null) }
    var deletingUser by remember { mutableStateOf<ManagedUser?>(null) }
    var inspectingUser by remember { mutableStateOf<ManagedUser?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var statusFilter by remember { mutableIntStateOf(0) } // 0=All, 1=Active, 2=Inactive

    val filteredUsers = remember(users, searchQuery, statusFilter) {
        val q = searchQuery.trim().lowercase()
        users.filter { user ->
            val matchesQuery = q.isEmpty() ||
                user.fullName.lowercase().contains(q) ||
                user.loginId.lowercase().contains(q)
            val matchesStatus = when (statusFilter) {
                1 -> user.isActive
                2 -> !user.isActive
                else -> true
            }
            matchesQuery && matchesStatus
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(strings.adminPanelTitle, fontWeight = FontWeight.Bold)
                        Text(
                            text = strings.adminPanelSubtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("admin_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = strings.cancel
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onSwitchToUserLogin,
                        modifier = Modifier.testTag("open_user_login_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SwitchAccount,
                            contentDescription = strings.switchToUserLogin
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Summary Cards
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AdminStatTile(
                        label = strings.totalUsersLabel,
                        value = users.size.toString(),
                        modifier = Modifier.weight(1f)
                    )
                    AdminStatTile(
                        label = strings.activeUsersLabel,
                        value = activeCount.toString(),
                        highlight = true,
                        modifier = Modifier.weight(1f)
                    )
                    AdminStatTile(
                        label = strings.inactiveUsersLabel,
                        value = inactiveCount.toString(),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Action Buttons
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { showCreateDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("create_managed_user_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Filled.PersonAdd, contentDescription = strings.createUserId)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.createUserId, style = MaterialTheme.typography.titleSmall)
                    }

                    OutlinedButton(
                        onClick = onSwitchToUserLogin,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("admin_switch_to_login_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Login, contentDescription = strings.switchToUserLogin)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.switchToUserLogin)
                    }
                }
            }

            // Search & Filter Bar
            if (users.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text(strings.searchUsersHint) },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("admin_user_search_input")
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = statusFilter == 0,
                                onClick = { statusFilter = 0 },
                                label = { Text("${strings.allUsersFilter} (${users.size})") }
                            )
                            FilterChip(
                                selected = statusFilter == 1,
                                onClick = { statusFilter = 1 },
                                label = { Text("${strings.activeUsersLabel} ($activeCount)") }
                            )
                            FilterChip(
                                selected = statusFilter == 2,
                                onClick = { statusFilter = 2 },
                                label = { Text("${strings.inactiveUsersLabel} ($inactiveCount)") }
                            )
                        }
                    }
                }
            }

            if (filteredUsers.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AdminPanelSettings,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = strings.noManagedUsersYet,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(filteredUsers, key = { it.id }) { user ->
                    val normalizedKey = user.loginId.lowercase()
                    val summary = state.userSummariesByAccount[normalizedKey]
                        ?: UserFinancialSummary(accountKey = normalizedKey)
                    val isCurrentSession = state.activeAccountId.equals(user.loginId, ignoreCase = true)
                    ManagedUserCard(
                        user = user,
                        summary = summary,
                        preferredCurrency = state.preferredCurrency,
                        sarToBdtRate = state.sarToBdtRate,
                        isCurrentSession = isCurrentSession,
                        strings = strings,
                        onToggleActive = { onToggleActive(user) },
                        onViewDetails = { inspectingUser = user },
                        onEdit = { editingUser = user },
                        onDelete = { deletingUser = user },
                        onLoginAs = { onLoginAsUser(user) }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    val userToInspect = inspectingUser
    if (userToInspect != null) {
        val key = userToInspect.loginId.lowercase()
        val summary = state.userSummariesByAccount[key] ?: UserFinancialSummary(accountKey = key)
        UserDetailsDialog(
            user = userToInspect,
            summary = summary,
            strings = strings,
            language = state.language,
            preferredCurrency = state.preferredCurrency,
            sarToBdtRate = state.sarToBdtRate,
            isCurrentSession = state.activeAccountId.equals(userToInspect.loginId, ignoreCase = true),
            onDismiss = { inspectingUser = null },
            onLoginAsUser = { onLoginAsUser(userToInspect) }
        )
    }

    if (showCreateDialog) {
        ManagedUserFormDialog(
            strings = strings,
            existingUser = null,
            onDismiss = { showCreateDialog = false },
            onSave = { loginId, fullName, rawPassword, role, isActive ->
                onCreateUser(loginId, fullName, rawPassword, role, isActive)
            }
        )
    }

    val userToEdit = editingUser
    if (userToEdit != null) {
        ManagedUserFormDialog(
            strings = strings,
            existingUser = userToEdit,
            onDismiss = { editingUser = null },
            onSave = { _, fullName, rawPassword, role, isActive ->
                onUpdateUser(userToEdit, fullName, rawPassword.ifBlank { null }, role, isActive)
            }
        )
    }

    val userToDelete = deletingUser
    if (userToDelete != null) {
        AlertDialog(
            onDismissRequest = { deletingUser = null },
            title = { Text(strings.confirmDelete, fontWeight = FontWeight.Bold) },
            text = {
                Text("${strings.confirmDeleteUserMessage}\n\n${userToDelete.fullName} (@${userToDelete.loginId})")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteUser(userToDelete.id)
                        deletingUser = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_user_button")
                ) {
                    Text(strings.delete)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingUser = null }) {
                    Text(strings.cancel)
                }
            }
        )
    }
}

@Composable
private fun AdminStatTile(
    label: String,
    value: String,
    highlight: Boolean = false,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (highlight) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ManagedUserCard(
    user: ManagedUser,
    summary: UserFinancialSummary,
    preferredCurrency: CurrencyCode,
    sarToBdtRate: Double,
    isCurrentSession: Boolean,
    strings: AppStrings,
    onToggleActive: () -> Unit,
    onViewDetails: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onLoginAs: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (user.isActive) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = user.fullName.firstOrNull()?.uppercase() ?: "U",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (user.isActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = user.fullName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "ID: ${user.loginId} · ${summary.entryCount} ${strings.entriesCountSuffix}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val isRoleAdmin = user.role == UserRole.ADMIN.code
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isRoleAdmin) MaterialTheme.colorScheme.tertiaryContainer
                            else MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = if (isRoleAdmin) strings.roleAdmin else strings.roleUser,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (user.isActive) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.errorContainer
                        ) {
                            Text(
                                text = if (user.isActive) strings.activeStatus else strings.inactiveStatus,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                color = if (user.isActive) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                val isPrimaryAdmin = user.loginId.equals(DEFAULT_ADMIN_LOGIN_ID, ignoreCase = true)
                Switch(
                    checked = user.isActive,
                    onCheckedChange = { if (!isPrimaryAdmin) onToggleActive() },
                    enabled = !isPrimaryAdmin,
                    modifier = Modifier.testTag("toggle_user_active_${user.loginId}")
                )
            }

            // Per-User Financial Summary Strip
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    UserMetricColumn(
                        label = strings.totalIncome,
                        value = formatCurrency(
                            summary.combinedIncome(preferredCurrency, sarToBdtRate),
                            preferredCurrency.code
                        ),
                        valueColor = IncomeGreen,
                        modifier = Modifier.weight(1f)
                    )
                    UserMetricColumn(
                        label = strings.totalExpense,
                        value = formatCurrency(
                            summary.combinedExpense(preferredCurrency, sarToBdtRate),
                            preferredCurrency.code
                        ),
                        valueColor = ExpenseCoral,
                        modifier = Modifier.weight(1f)
                    )
                    val net = summary.combinedNetBalance(preferredCurrency, sarToBdtRate)
                    UserMetricColumn(
                        label = strings.netBalance,
                        value = formatCurrency(net, preferredCurrency.code),
                        valueColor = if (net >= 0.0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onViewDetails,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("view_user_details_${user.loginId}"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Visibility,
                        contentDescription = strings.viewUserDetails,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = strings.viewUserDetails,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Button(
                    onClick = onLoginAs,
                    enabled = user.isActive && !isCurrentSession,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("login_as_user_${user.loginId}"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Login,
                        contentDescription = strings.loginAsThisUser,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isCurrentSession) strings.activeStatus else strings.loginAsThisUser,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onEdit,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("edit_user_${user.loginId}")
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = strings.edit)
                }

                if (!user.loginId.equals(DEFAULT_ADMIN_LOGIN_ID, ignoreCase = true)) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("delete_user_${user.loginId}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = strings.delete,
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UserMetricColumn(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ManagedUserFormDialog(
    strings: AppStrings,
    existingUser: ManagedUser?,
    onDismiss: () -> Unit,
    onSave: suspend (loginId: String, fullName: String, rawPassword: String, role: UserRole, isActive: Boolean) -> String?
) {
    val scope = rememberCoroutineScope()
    val isEditing = existingUser != null

    var loginId by remember(existingUser) { mutableStateOf(existingUser?.loginId ?: "") }
    var fullName by remember(existingUser) { mutableStateOf(existingUser?.fullName ?: "") }
    var rawPassword by remember(existingUser) { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var selectedRole by remember(existingUser) {
        mutableStateOf(UserRole.fromCode(existingUser?.role))
    }
    var isActive by remember(existingUser) { mutableStateOf(existingUser?.isActive ?: true) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isEditing) strings.editUser else strings.createUserId,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = loginId,
                    onValueChange = {
                        loginId = it.trim()
                        errorText = null
                    },
                    enabled = !isEditing,
                    label = { Text(strings.userIdLabel) },
                    placeholder = { Text(strings.userIdHint) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_user_id_input")
                )

                OutlinedTextField(
                    value = fullName,
                    onValueChange = {
                        fullName = it
                        errorText = null
                    },
                    label = { Text(strings.fullNameLabel) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_user_fullname_input")
                )

                OutlinedTextField(
                    value = rawPassword,
                    onValueChange = {
                        rawPassword = it
                        errorText = null
                    },
                    label = {
                        Text(
                            if (isEditing) strings.newPasswordOptionalLabel else strings.passwordLabel
                        )
                    },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = strings.passwordLabel
                            )
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_user_password_input")
                )

                Text(strings.userRoleLabel, style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedRole == UserRole.USER,
                        onClick = { selectedRole = UserRole.USER },
                        label = { Text(strings.roleUser) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("dialog_role_user_chip")
                    )
                    FilterChip(
                        selected = selectedRole == UserRole.ADMIN,
                        onClick = { selectedRole = UserRole.ADMIN },
                        label = { Text(strings.roleAdmin) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("dialog_role_admin_chip")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(strings.activeStatus, style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = isActive,
                        onCheckedChange = { isActive = it },
                        modifier = Modifier.testTag("dialog_user_active_switch")
                    )
                }

                if (errorText != null) {
                    Text(
                        text = errorText!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    isSaving = true
                    scope.launch {
                        val err = onSave(loginId, fullName, rawPassword, selectedRole, isActive)
                        isSaving = false
                        if (err != null) {
                            errorText = err
                        } else {
                            onDismiss()
                        }
                    }
                },
                enabled = !isSaving,
                modifier = Modifier.testTag("dialog_save_user_button")
            ) {
                Text(strings.save)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel)
            }
        }
    )
}
