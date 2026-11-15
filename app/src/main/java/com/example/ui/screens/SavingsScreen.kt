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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.model.CurrencyCode
import com.example.model.SavingsEntry
import com.example.model.SavingsFrequency
import com.example.model.SavingsGoal
import com.example.ui.HisabUiState
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.components.CurrencySelectorRow
import com.example.ui.components.HisabDatePickerField
import com.example.ui.strings.formatCurrency
import com.example.ui.strings.formatDateShort
import com.example.ui.theme.IncomeGreen
import com.example.ui.theme.SavingsAmber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavingsScreen(
    state: HisabUiState,
    onBack: () -> Unit,
    onAddGoal: (String, Double, Double, String, String, Long) -> Unit,
    onUpdateGoal: (SavingsGoal) -> Unit,
    onDeleteGoal: (String) -> Unit,
    onAddDeposit: (SavingsGoal?, Double, String, String, Long, String) -> Unit,
    onDeleteEntry: (SavingsEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = state.strings
    var showGoalDialog by remember { mutableStateOf(false) }
    var editingGoal by remember { mutableStateOf<SavingsGoal?>(null) }
    var deletingGoal by remember { mutableStateOf<SavingsGoal?>(null) }
    var depositTargetGoal by remember { mutableStateOf<SavingsGoal?>(null) }
    var showQuickDepositDialog by remember { mutableStateOf(false) }
    var deletingEntry by remember { mutableStateOf<SavingsEntry?>(null) }

    val dailyTotalBdt = state.savingsEntries
        .filter { it.frequency == SavingsFrequency.DAILY.code && it.currency == CurrencyCode.BDT.code }
        .sumOf { it.amount }
    val monthlyTotalBdt = state.savingsEntries
        .filter { it.frequency == SavingsFrequency.MONTHLY.code && it.currency == CurrencyCode.BDT.code }
        .sumOf { it.amount }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(strings.savingsTitle, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("savings_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Daily & Monthly Summary Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = SavingsAmber.copy(alpha = 0.12f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(strings.dailySavings, style = MaterialTheme.typography.labelMedium)
                            Text(
                                text = formatCurrency(dailyTotalBdt, "BDT"),
                                style = MaterialTheme.typography.titleLarge,
                                color = SavingsAmber,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(strings.monthlySavings, style = MaterialTheme.typography.labelMedium)
                            Text(
                                text = formatCurrency(monthlyTotalBdt, "BDT"),
                                style = MaterialTheme.typography.titleLarge,
                                color = SavingsAmber,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Action Buttons
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { showGoalDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("add_savings_goal_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(strings.addSavingsGoal, maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = {
                            depositTargetGoal = state.savingsGoals.firstOrNull()
                            showQuickDepositDialog = true
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("add_savings_deposit_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Filled.Savings, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(strings.depositNow, maxLines = 1)
                    }
                }
            }

            item {
                Text(
                    text = strings.savingsGoals,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (state.savingsGoals.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Text(
                            text = strings.noSavingsGoalsYet,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp)
                        )
                    }
                }
            } else {
                items(state.savingsGoals, key = { it.id }) { goal ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = goal.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = strings.savingsFrequencyLabel(SavingsFrequency.fromCode(goal.frequency)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row {
                                    IconButton(onClick = { editingGoal = goal }) {
                                        Icon(Icons.Filled.Edit, contentDescription = strings.edit)
                                    }
                                    IconButton(onClick = { deletingGoal = goal }) {
                                        Icon(
                                            Icons.Filled.Delete,
                                            contentDescription = strings.delete,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }

                            LinearProgressIndicator(
                                progress = { goal.progressRatio },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(5.dp)),
                                color = if (goal.progressPercent >= 100) IncomeGreen else SavingsAmber
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${strings.savedSoFar}: ${formatCurrency(goal.savedAmount, goal.currency)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "${goal.progressPercent}% / ${formatCurrency(goal.targetAmount, goal.currency)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    depositTargetGoal = goal
                                    showQuickDepositDialog = true
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(strings.depositNow)
                            }
                        }
                    }
                }
            }

            if (state.savingsEntries.isNotEmpty()) {
                item {
                    Text(
                        text = strings.savingsHistory,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                items(state.savingsEntries, key = { it.id }) { entry ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(SavingsAmber.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Savings, contentDescription = null, tint = SavingsAmber)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entry.goalTitle.ifBlank {
                                        strings.savingsFrequencyLabel(SavingsFrequency.fromCode(entry.frequency))
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "${formatDateShort(entry.dateMillis, state.language)}${if (entry.note.isNotBlank()) " · ${entry.note}" else ""}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "+${formatCurrency(entry.amount, entry.currency)}",
                                style = MaterialTheme.typography.titleSmall,
                                color = IncomeGreen,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(onClick = { deletingEntry = entry }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = strings.delete,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    if (showGoalDialog || editingGoal != null) {
        val current = editingGoal
        var title by remember(current) { mutableStateOf(current?.title ?: "") }
        var targetText by remember(current) { mutableStateOf(current?.targetAmount?.toLong()?.toString() ?: "") }
        var savedText by remember(current) { mutableStateOf(current?.savedAmount?.toLong()?.toString() ?: "0") }
        var currency by remember(current) { mutableStateOf(current?.let { CurrencyCode.fromCode(it.currency) } ?: state.preferredCurrency) }
        var frequency by remember(current) { mutableStateOf(current?.let { SavingsFrequency.fromCode(it.frequency) } ?: SavingsFrequency.MONTHLY) }
        var error by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = {
                showGoalDialog = false
                editingGoal = null
            },
            title = { Text(strings.addSavingsGoal, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it.take(100); error = null },
                        label = { Text(strings.goalTitle) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("savings_goal_title_input")
                    )
                    OutlinedTextField(
                        value = targetText,
                        onValueChange = { targetText = it; error = null },
                        label = { Text(strings.targetAmount) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("savings_goal_target_input")
                    )
                    OutlinedTextField(
                        value = savedText,
                        onValueChange = { savedText = it; error = null },
                        label = { Text(strings.savedSoFar) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    CurrencySelectorRow(selected = currency, onSelect = { currency = it })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SavingsFrequency.entries.forEach { freq ->
                            FilterChip(
                                selected = frequency == freq,
                                onClick = { frequency = freq },
                                label = { Text(strings.savingsFrequencyLabel(freq)) }
                            )
                        }
                    }
                    if (error != null) {
                        Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = targetText.toDoubleOrNull()
                        val saved = savedText.toDoubleOrNull() ?: 0.0
                        if (title.isBlank()) {
                            error = strings.requiredFieldError
                        } else if (target == null || target <= 0.0) {
                            error = strings.invalidAmountError
                        } else {
                            if (current == null) {
                                onAddGoal(title.trim(), target, saved.coerceAtLeast(0.0), currency.code, frequency.code, 0L)
                            } else {
                                onUpdateGoal(
                                    current.copy(
                                        title = title.trim(),
                                        targetAmount = target,
                                        savedAmount = saved.coerceAtLeast(0.0),
                                        currency = currency.code,
                                        frequency = frequency.code
                                    )
                                )
                            }
                            showGoalDialog = false
                            editingGoal = null
                        }
                    },
                    modifier = Modifier.testTag("save_savings_goal_button")
                ) {
                    Text(strings.save)
                }
            },
            dismissButton = {
                TextButton(onClick = { showGoalDialog = false; editingGoal = null }) {
                    Text(strings.cancel)
                }
            }
        )
    }

    if (showQuickDepositDialog) {
        val targetGoal = depositTargetGoal
        var amountText by remember { mutableStateOf("") }
        var currency by remember { mutableStateOf(targetGoal?.let { CurrencyCode.fromCode(it.currency) } ?: state.preferredCurrency) }
        var frequency by remember { mutableStateOf(targetGoal?.let { SavingsFrequency.fromCode(it.frequency) } ?: SavingsFrequency.DAILY) }
        var dateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
        var note by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showQuickDepositDialog = false },
            title = { Text(strings.addSavingsEntry, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (targetGoal != null) {
                        Text(
                            text = "${strings.goalTitle}: ${targetGoal.title}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it; error = null },
                        label = { Text(strings.amount) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("savings_deposit_amount_input")
                    )
                    CurrencySelectorRow(selected = currency, onSelect = { currency = it })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SavingsFrequency.entries.forEach { freq ->
                            FilterChip(
                                selected = frequency == freq,
                                onClick = { frequency = freq },
                                label = { Text(strings.savingsFrequencyLabel(freq)) }
                            )
                        }
                    }
                    HisabDatePickerField(
                        label = strings.date,
                        dateMillis = dateMillis,
                        language = state.language,
                        onDateSelected = { dateMillis = it }
                    )
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it.take(300) },
                        label = { Text(strings.description) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (error != null) {
                        Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = amountText.toDoubleOrNull()
                        if (amt == null || amt <= 0.0) {
                            error = strings.invalidAmountError
                        } else {
                            onAddDeposit(targetGoal, amt, currency.code, frequency.code, dateMillis, note.trim())
                            showQuickDepositDialog = false
                        }
                    },
                    modifier = Modifier.testTag("save_savings_deposit_button")
                ) {
                    Text(strings.save)
                }
            },
            dismissButton = {
                TextButton(onClick = { showQuickDepositDialog = false }) { Text(strings.cancel) }
            }
        )
    }

    deletingGoal?.let { g ->
        ConfirmDeleteDialog(
            strings = strings,
            onConfirm = { onDeleteGoal(g.id); deletingGoal = null },
            onDismiss = { deletingGoal = null }
        )
    }

    deletingEntry?.let { e ->
        ConfirmDeleteDialog(
            strings = strings,
            onConfirm = { onDeleteEntry(e); deletingEntry = null },
            onDismiss = { deletingEntry = null }
        )
    }
}
