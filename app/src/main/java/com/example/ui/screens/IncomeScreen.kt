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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.model.CurrencyCode
import com.example.model.IncomeEntry
import com.example.model.IncomeSource
import com.example.ui.HisabUiState
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.components.IncomeFormDialog
import com.example.ui.strings.formatCurrency
import com.example.ui.strings.formatDateShort
import com.example.ui.theme.IncomeGreen
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncomeScreen(
    state: HisabUiState,
    openAddImmediately: Boolean,
    onConsumedOpenAdd: () -> Unit,
    onAddIncome: (Double, String, String, Long, String) -> Unit,
    onUpdateIncome: (IncomeEntry) -> Unit,
    onDeleteIncome: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = state.strings
    var searchQuery by remember { mutableStateOf("") }
    var selectedDateMillis by remember { mutableStateOf<Long?>(null) }
    var showDateFilterPicker by remember { mutableStateOf(false) }

    var showAddDialog by remember(openAddImmediately) { mutableStateOf(openAddImmediately) }
    var editingIncome by remember { mutableStateOf<IncomeEntry?>(null) }
    var deletingIncome by remember { mutableStateOf<IncomeEntry?>(null) }

    val filtered = remember(state.incomes, searchQuery, selectedDateMillis) {
        state.incomes.filter { item ->
            val matchesQuery = searchQuery.isBlank() ||
                item.description.contains(searchQuery, ignoreCase = true) ||
                strings.incomeSourceLabel(IncomeSource.fromCode(item.source))
                    .contains(searchQuery, ignoreCase = true) ||
                formatDateShort(item.dateMillis, state.language)
                    .contains(searchQuery, ignoreCase = true)

            val matchesDate = selectedDateMillis == null || isSameDay(item.dateMillis, selectedDateMillis!!)
            matchesQuery && matchesDate
        }
    }

    val totalBdt = filtered.filter { it.currency == CurrencyCode.BDT.code }.sumOf { it.amount }
    val totalSar = filtered.filter { it.currency == CurrencyCode.SAR.code }.sumOf { it.amount }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = strings.addIncome) },
                text = { Text(strings.addIncome) },
                containerColor = IncomeGreen,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("add_income_fab")
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
            item {
                Text(
                    text = strings.incomeHistory,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = IncomeGreen.copy(alpha = 0.12f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(strings.bdtBalanceLabel, style = MaterialTheme.typography.labelMedium)
                            Text(
                                text = formatCurrency(totalBdt, CurrencyCode.BDT.code),
                                style = MaterialTheme.typography.titleLarge,
                                color = IncomeGreen,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(strings.sarBalanceLabel, style = MaterialTheme.typography.labelMedium)
                            Text(
                                text = formatCurrency(totalSar, CurrencyCode.SAR.code),
                                style = MaterialTheme.typography.titleLarge,
                                color = IncomeGreen,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text(strings.searchByDateOrNote) },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("income_search_input")
                    )
                    FilterChip(
                        selected = selectedDateMillis != null,
                        onClick = {
                            if (selectedDateMillis != null) selectedDateMillis = null
                            else showDateFilterPicker = true
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (selectedDateMillis != null) Icons.Filled.Close else Icons.Filled.CalendarMonth,
                                contentDescription = strings.filterByDate
                            )
                        },
                        label = {
                            Text(
                                selectedDateMillis?.let { formatDateShort(it, state.language) }
                                    ?: strings.filterByDate
                            )
                        }
                    )
                }
            }

            if (filtered.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Text(
                            text = strings.noIncomeYet,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp)
                        )
                    }
                }
            } else {
                items(filtered, key = { it.id }) { income ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("income_item_card_${income.id}"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(IncomeGreen.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ArrowDownward,
                                    contentDescription = null,
                                    tint = IncomeGreen
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = strings.incomeSourceLabel(IncomeSource.fromCode(income.source)),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (income.description.isNotBlank()) {
                                    Text(
                                        text = income.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = formatDateShort(income.dateMillis, state.language),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "+${formatCurrency(income.amount, income.currency)}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = IncomeGreen,
                                    fontWeight = FontWeight.Bold
                                )
                                Row {
                                    IconButton(
                                        onClick = { editingIncome = income },
                                        modifier = Modifier.testTag("edit_income_${income.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Edit,
                                            contentDescription = strings.edit,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    IconButton(
                                        onClick = { deletingIncome = income },
                                        modifier = Modifier.testTag("delete_income_${income.id}")
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
            }

            item { Spacer(modifier = Modifier.height(72.dp)) }
        }
    }

    if (showDateFilterPicker) {
        val dateState = rememberDatePickerState(initialSelectedDateMillis = selectedDateMillis ?: System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { showDateFilterPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedDateMillis = dateState.selectedDateMillis
                        showDateFilterPicker = false
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDateFilterPicker = false }) { Text(strings.cancel) }
            }
        ) {
            DatePicker(state = dateState)
        }
    }

    if (showAddDialog) {
        IncomeFormDialog(
            strings = strings,
            language = state.language,
            defaultCurrency = state.preferredCurrency,
            existing = null,
            onDismiss = {
                showAddDialog = false
                onConsumedOpenAdd()
            },
            onSave = { amount, curr, src, date, desc ->
                onAddIncome(amount, curr, src, date, desc)
                showAddDialog = false
                onConsumedOpenAdd()
            }
        )
    }

    editingIncome?.let { inc ->
        IncomeFormDialog(
            strings = strings,
            language = state.language,
            defaultCurrency = state.preferredCurrency,
            existing = inc,
            onDismiss = { editingIncome = null },
            onSave = { amount, curr, src, date, desc ->
                onUpdateIncome(
                    inc.copy(
                        amount = amount,
                        currency = curr,
                        source = src,
                        dateMillis = date,
                        description = desc
                    )
                )
                editingIncome = null
            }
        )
    }

    deletingIncome?.let { inc ->
        ConfirmDeleteDialog(
            strings = strings,
            onConfirm = {
                onDeleteIncome(inc.id)
                deletingIncome = null
            },
            onDismiss = { deletingIncome = null }
        )
    }
}

internal fun isSameDay(aMillis: Long, bMillis: Long): Boolean {
    val calA = Calendar.getInstance().apply { timeInMillis = aMillis }
    val calB = Calendar.getInstance().apply { timeInMillis = bMillis }
    return calA.get(Calendar.YEAR) == calB.get(Calendar.YEAR) &&
        calA.get(Calendar.DAY_OF_YEAR) == calB.get(Calendar.DAY_OF_YEAR)
}
