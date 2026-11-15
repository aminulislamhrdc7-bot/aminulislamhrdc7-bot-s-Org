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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.ExportHelper
import com.example.model.CurrencyCode
import com.example.ui.HisabUiState
import com.example.ui.components.CategoryDonutChartCard
import com.example.ui.components.HisabDatePickerField
import com.example.ui.strings.convertCurrency
import com.example.ui.strings.formatCurrency
import com.example.ui.theme.ChartPalette
import com.example.ui.theme.ExpenseCoral
import com.example.ui.theme.IncomeGreen
import java.util.Calendar

enum class ReportPeriod { DAILY, WEEKLY, MONTHLY, YEARLY }

@Composable
fun ReportsScreen(
    state: HisabUiState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val strings = state.strings
    val prefCode = state.preferredCurrency.code
    var period by remember { mutableStateOf(ReportPeriod.MONTHLY) }
    var anchorDateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val (startMillis, endMillis) = remember(period, anchorDateMillis) {
        computePeriodBounds(period, anchorDateMillis)
    }

    val periodIncomes = remember(state.incomes, startMillis, endMillis) {
        state.incomes.filter { it.dateMillis in startMillis..endMillis }
    }
    val periodExpenses = remember(state.expenses, startMillis, endMillis) {
        state.expenses.filter { it.dateMillis in startMillis..endMillis }
    }

    val totalIncomePref = periodIncomes.sumOf {
        convertCurrency(it.amount, it.currency, state.preferredCurrency, state.sarToBdtRate)
    }
    val totalExpensePref = periodExpenses.sumOf {
        convertCurrency(it.amount, it.currency, state.preferredCurrency, state.sarToBdtRate)
    }
    val totalIncBdt = periodIncomes.filter { it.currency == CurrencyCode.BDT.code }.sumOf { it.amount }
    val totalIncSar = periodIncomes.filter { it.currency == CurrencyCode.SAR.code }.sumOf { it.amount }
    val totalExpBdt = periodExpenses.filter { it.currency == CurrencyCode.BDT.code }.sumOf { it.amount }
    val totalExpSar = periodExpenses.filter { it.currency == CurrencyCode.SAR.code }.sumOf { it.amount }

    val categoryStats = remember(periodExpenses, state.preferredCurrency, state.sarToBdtRate) {
        state.categoryStatsFor(periodExpenses)
    }

    val periodLabel = when (period) {
        ReportPeriod.DAILY -> strings.dailyReport
        ReportPeriod.WEEKLY -> strings.weeklyReport
        ReportPeriod.MONTHLY -> strings.monthlyReport
        ReportPeriod.YEARLY -> strings.yearlyReport
    }

    val pdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            ExportHelper.writePdfReport(
                context = context,
                uri = uri,
                language = state.language,
                periodTitle = periodLabel,
                incomes = periodIncomes,
                expenses = periodExpenses,
                sarToBdtRate = state.sarToBdtRate
            ).onSuccess {
                Toast.makeText(context, strings.exportSuccess, Toast.LENGTH_SHORT).show()
            }.onFailure { err ->
                Toast.makeText(context, err.localizedMessage ?: "PDF export failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val csvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            ExportHelper.writeCsvExcelReport(
                context = context,
                uri = uri,
                language = state.language,
                incomes = periodIncomes,
                expenses = periodExpenses
            ).onSuccess {
                Toast.makeText(context, strings.exportSuccess, Toast.LENGTH_SHORT).show()
            }.onFailure { err ->
                Toast.makeText(context, err.localizedMessage ?: "CSV export failed", Toast.LENGTH_SHORT).show()
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
                text = strings.reportsTitle,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        // Period Selector
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ReportPeriod.entries.forEach { p ->
                    val label = when (p) {
                        ReportPeriod.DAILY -> strings.dailyReport
                        ReportPeriod.WEEKLY -> strings.weeklyReport
                        ReportPeriod.MONTHLY -> strings.monthlyReport
                        ReportPeriod.YEARLY -> strings.yearlyReport
                    }
                    FilterChip(
                        selected = period == p,
                        onClick = { period = p },
                        label = { Text(label) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        item {
            HisabDatePickerField(
                label = strings.date,
                dateMillis = anchorDateMillis,
                language = state.language,
                onDateSelected = { anchorDateMillis = it }
            )
        }

        // Summary Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "$periodLabel ${strings.summarySection}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(strings.totalIncome, style = MaterialTheme.typography.labelMedium)
                            Text(
                                text = formatCurrency(totalIncomePref, prefCode),
                                style = MaterialTheme.typography.titleLarge,
                                color = IncomeGreen,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "৳ ${totalIncBdt.toLong()} · SAR ${totalIncSar.toLong()}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(strings.totalExpense, style = MaterialTheme.typography.labelMedium)
                            Text(
                                text = formatCurrency(totalExpensePref, prefCode),
                                style = MaterialTheme.typography.titleLarge,
                                color = ExpenseCoral,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "৳ ${totalExpBdt.toLong()} · SAR ${totalExpSar.toLong()}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    val net = totalIncomePref - totalExpensePref
                    Text(
                        text = "${strings.netBalance}: ${formatCurrency(net, prefCode)}",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (net >= 0) IncomeGreen else ExpenseCoral,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Export Buttons: PDF & Excel (CSV)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { pdfLauncher.launch("my_hisab_report_${period.name.lowercase()}.pdf") },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("download_pdf_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Filled.PictureAsPdf, contentDescription = strings.downloadPdf)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(strings.downloadPdf, maxLines = 1)
                }
                OutlinedButton(
                    onClick = { csvLauncher.launch("my_hisab_report_${period.name.lowercase()}.csv") },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("download_excel_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Filled.TableChart, contentDescription = strings.downloadExcel)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(strings.downloadExcel, maxLines = 1)
                }
            }
        }

        // Donut Chart
        item {
            CategoryDonutChartCard(
                title = strings.categoryBreakdown,
                stats = categoryStats,
                strings = strings,
                currencyCode = prefCode,
                emptyText = strings.noExpensesYet
            )
        }

        // Detailed Category Bars
        if (categoryStats.isNotEmpty()) {
            items(categoryStats.indices.toList()) { idx ->
                val stat = categoryStats[idx]
                val color = ChartPalette[idx % ChartPalette.size]
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = strings.expenseCategoryLabel(stat.category),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${(stat.percentage * 100).toInt()}% · ${formatCurrency(stat.amountInPreferred, prefCode)}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = color
                            )
                        }
                        LinearProgressIndicator(
                            progress = { stat.percentage },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = color
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

private fun computePeriodBounds(period: ReportPeriod, anchorMillis: Long): Pair<Long, Long> {
    val start = Calendar.getInstance().apply { timeInMillis = anchorMillis }
    val end = Calendar.getInstance().apply { timeInMillis = anchorMillis }

    when (period) {
        ReportPeriod.DAILY -> Unit
        ReportPeriod.WEEKLY -> {
            start.set(Calendar.DAY_OF_WEEK, start.firstDayOfWeek)
            end.timeInMillis = start.timeInMillis
            end.add(Calendar.DAY_OF_YEAR, 6)
        }
        ReportPeriod.MONTHLY -> {
            start.set(Calendar.DAY_OF_MONTH, 1)
            end.set(Calendar.DAY_OF_MONTH, end.getActualMaximum(Calendar.DAY_OF_MONTH))
        }
        ReportPeriod.YEARLY -> {
            start.set(Calendar.DAY_OF_YEAR, 1)
            end.set(Calendar.MONTH, Calendar.DECEMBER)
            end.set(Calendar.DAY_OF_MONTH, 31)
        }
    }

    start.set(Calendar.HOUR_OF_DAY, 0)
    start.set(Calendar.MINUTE, 0)
    start.set(Calendar.SECOND, 0)
    start.set(Calendar.MILLISECOND, 0)

    end.set(Calendar.HOUR_OF_DAY, 23)
    end.set(Calendar.MINUTE, 59)
    end.set(Calendar.SECOND, 59)
    end.set(Calendar.MILLISECOND, 999)

    return start.timeInMillis to end.timeInMillis
}
