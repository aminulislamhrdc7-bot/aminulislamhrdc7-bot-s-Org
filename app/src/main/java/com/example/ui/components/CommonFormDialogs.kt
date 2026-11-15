package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.ShoppingBasket
import androidx.compose.material.icons.filled.Train
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.model.AppLanguage
import com.example.model.CurrencyCode
import com.example.model.ExpenseCategory
import com.example.model.ExpenseEntry
import com.example.model.IncomeEntry
import com.example.model.IncomeSource
import com.example.ui.strings.AppStrings
import com.example.ui.strings.formatDateShort

fun expenseCategoryIcon(category: ExpenseCategory): ImageVector = when (category) {
    ExpenseCategory.FOOD -> Icons.Filled.Fastfood
    ExpenseCategory.RENT -> Icons.Filled.Home
    ExpenseCategory.ELECTRICITY -> Icons.Filled.ElectricBolt
    ExpenseCategory.GAS -> Icons.Filled.LocalGasStation
    ExpenseCategory.GROCERIES -> Icons.Filled.ShoppingBasket
    ExpenseCategory.TRANSPORT -> Icons.Filled.Train
    ExpenseCategory.MEDICAL -> Icons.Filled.LocalHospital
    ExpenseCategory.FAMILY -> Icons.Filled.FamilyRestroom
    ExpenseCategory.MOBILE_INTERNET -> Icons.Filled.PhoneAndroid
    ExpenseCategory.OTHER -> Icons.Filled.Category
}

@Composable
fun CurrencySelectorRow(
    selected: CurrencyCode,
    onSelect: (CurrencyCode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CurrencyCode.entries.forEach { code ->
            val isSelected = selected == code
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(code) },
                label = {
                    Text(
                        text = if (code == CurrencyCode.BDT) "৳ BDT" else "﷼ SAR",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                leadingIcon = if (isSelected) {
                    { Icon(Icons.Filled.Check, contentDescription = null) }
                } else null,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HisabDatePickerField(
    label: String,
    dateMillis: Long,
    language: AppLanguage,
    onDateSelected: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var showPicker by remember { mutableStateOf(false) }

    OutlinedButton(
        onClick = { showPicker = true },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Icon(Icons.Filled.CalendarToday, contentDescription = label)
        Spacer(modifier = Modifier.width(8.dp))
        Text("$label: ${formatDateShort(dateMillis, language)}")
    }

    if (showPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = dateMillis)
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let(onDateSelected)
                        showPicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = state)
        }
    }
}

@Composable
fun ConfirmDeleteDialog(
    strings: AppStrings,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.confirmDelete, fontWeight = FontWeight.Bold) },
        text = { Text(strings.confirmDeleteMessage) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.testTag("confirm_delete_button")
            ) {
                Text(strings.delete)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel)
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IncomeFormDialog(
    strings: AppStrings,
    language: AppLanguage,
    defaultCurrency: CurrencyCode,
    existing: IncomeEntry?,
    onDismiss: () -> Unit,
    onSave: (amount: Double, currency: String, source: String, dateMillis: Long, description: String) -> Unit
) {
    var amountText by remember { mutableStateOf(existing?.amount?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: "") }
    var currency by remember { mutableStateOf(existing?.let { CurrencyCode.fromCode(it.currency) } ?: defaultCurrency) }
    var source by remember { mutableStateOf(existing?.let { IncomeSource.fromCode(it.source) } ?: IncomeSource.SALARY) }
    var dateMillis by remember { mutableLongStateOf(existing?.dateMillis ?: System.currentTimeMillis()) }
    var description by remember { mutableStateOf(existing?.description ?: "") }
    var amountError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (existing == null) strings.addIncome else strings.editIncome,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it
                        amountError = null
                    },
                    label = { Text(strings.amount) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    isError = amountError != null,
                    supportingText = amountError?.let { err -> { Text(err) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("income_amount_input")
                )

                Text(strings.currency, style = MaterialTheme.typography.labelLarge)
                CurrencySelectorRow(selected = currency, onSelect = { currency = it })

                Text(strings.source, style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IncomeSource.entries.forEach { src ->
                        FilterChip(
                            selected = source == src,
                            onClick = { source = src },
                            label = { Text(strings.incomeSourceLabel(src)) }
                        )
                    }
                }

                HisabDatePickerField(
                    label = strings.date,
                    dateMillis = dateMillis,
                    language = language,
                    onDateSelected = { dateMillis = it }
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it.take(500) },
                    label = { Text(strings.description) },
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("income_description_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = amountText.trim().toDoubleOrNull()
                    if (amount == null || amount <= 0.0) {
                        amountError = strings.invalidAmountError
                    } else {
                        onSave(amount, currency.code, source.code, dateMillis, description.trim())
                    }
                },
                modifier = Modifier.testTag("save_income_button")
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExpenseFormDialog(
    strings: AppStrings,
    language: AppLanguage,
    defaultCurrency: CurrencyCode,
    existing: ExpenseEntry?,
    onDismiss: () -> Unit,
    onSave: (amount: Double, currency: String, category: String, dateMillis: Long, description: String) -> Unit
) {
    var amountText by remember { mutableStateOf(existing?.amount?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: "") }
    var currency by remember { mutableStateOf(existing?.let { CurrencyCode.fromCode(it.currency) } ?: defaultCurrency) }
    var category by remember { mutableStateOf(existing?.let { ExpenseCategory.fromCode(it.category) } ?: ExpenseCategory.FOOD) }
    var dateMillis by remember { mutableLongStateOf(existing?.dateMillis ?: System.currentTimeMillis()) }
    var description by remember { mutableStateOf(existing?.description ?: "") }
    var amountError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (existing == null) strings.addExpense else strings.editExpense,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it
                        amountError = null
                    },
                    label = { Text(strings.amount) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    isError = amountError != null,
                    supportingText = amountError?.let { err -> { Text(err) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("expense_amount_input")
                )

                Text(strings.currency, style = MaterialTheme.typography.labelLarge)
                CurrencySelectorRow(selected = currency, onSelect = { currency = it })

                Text(strings.category, style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ExpenseCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            leadingIcon = {
                                Icon(
                                    imageVector = expenseCategoryIcon(cat),
                                    contentDescription = null
                                )
                            },
                            label = { Text(strings.expenseCategoryLabel(cat)) }
                        )
                    }
                }

                HisabDatePickerField(
                    label = strings.date,
                    dateMillis = dateMillis,
                    language = language,
                    onDateSelected = { dateMillis = it }
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it.take(500) },
                    label = { Text(strings.description) },
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("expense_description_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = amountText.trim().toDoubleOrNull()
                    if (amount == null || amount <= 0.0) {
                        amountError = strings.invalidAmountError
                    } else {
                        onSave(amount, currency.code, category.code, dateMillis, description.trim())
                    }
                },
                modifier = Modifier.testTag("save_expense_button")
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
