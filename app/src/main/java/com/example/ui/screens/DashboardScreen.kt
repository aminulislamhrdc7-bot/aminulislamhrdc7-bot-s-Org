package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.AppLanguage
import com.example.model.CurrencyCode
import com.example.model.ExpenseCategory
import com.example.model.IncomeSource
import com.example.model.LoanType
import com.example.ui.HisabUiState
import com.example.ui.components.CategoryDonutChartCard
import com.example.ui.components.IncomeExpenseBarChartCard
import com.example.ui.components.expenseCategoryIcon
import com.example.ui.strings.formatCurrency
import com.example.ui.strings.formatDateShort
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.ExpenseCoral
import com.example.ui.theme.IncomeGreen
import com.example.ui.theme.SavingsAmber
import com.example.ui.theme.TealTertiary

private data class RecentTx(
    val id: String,
    val isIncome: Boolean,
    val title: String,
    val subtitle: String,
    val amount: Double,
    val currency: String,
    val dateMillis: Long,
    val icon: ImageVector
)

@Composable
fun DashboardScreen(
    state: HisabUiState,
    onToggleCurrency: (CurrencyCode) -> Unit,
    onToggleLanguage: (AppLanguage) -> Unit,
    onOpenSavings: () -> Unit,
    onOpenLoans: () -> Unit,
    onOpenAdminPanel: () -> Unit,
    onQuickAddIncome: () -> Unit,
    onQuickAddExpense: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = state.strings
    val prefCode = state.preferredCurrency.code

    val recentItems = (
        state.incomes.map { inc ->
            RecentTx(
                id = "inc_${inc.id}",
                isIncome = true,
                title = strings.incomeSourceLabel(IncomeSource.fromCode(inc.source)),
                subtitle = inc.description.ifBlank { formatDateShort(inc.dateMillis, state.language) },
                amount = inc.amount,
                currency = inc.currency,
                dateMillis = inc.dateMillis,
                icon = Icons.Filled.ArrowDownward
            )
        } + state.expenses.map { exp ->
            val cat = ExpenseCategory.fromCode(exp.category)
            RecentTx(
                id = "exp_${exp.id}",
                isIncome = false,
                title = strings.expenseCategoryLabel(cat),
                subtitle = exp.description.ifBlank { formatDateShort(exp.dateMillis, state.language) },
                amount = exp.amount,
                currency = exp.currency,
                dateMillis = exp.dateMillis,
                icon = expenseCategoryIcon(cat)
            )
        }
    ).sortedByDescending { it.dateMillis }.take(6)

    val lentRemainingBdt = state.loans.filter { it.type == LoanType.LENT.code && it.currency == CurrencyCode.BDT.code }.sumOf { it.remainingAmount }
    val borrowedRemainingBdt = state.loans.filter { it.type == LoanType.BORROWED.code && it.currency == CurrencyCode.BDT.code }.sumOf { it.remainingAmount }

    val totalGoalTarget = state.savingsGoals.sumOf { it.targetAmount }
    val totalGoalSaved = state.savingsGoals.sumOf { it.savedAmount }
    val overallGoalProgress = if (totalGoalTarget > 0.0) (totalGoalSaved / totalGoalTarget).toFloat().coerceIn(0f, 1f) else 0f

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header Row with Language & Currency quick chips
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = strings.appName,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${state.activeDisplayName} · ${strings.dashboardGreeting}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = state.preferredCurrency == CurrencyCode.BDT,
                        onClick = {
                            onToggleCurrency(
                                if (state.preferredCurrency == CurrencyCode.BDT) CurrencyCode.SAR else CurrencyCode.BDT
                            )
                        },
                        label = {
                            Text(
                                if (state.preferredCurrency == CurrencyCode.BDT) "৳ BDT" else "﷼ SAR",
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.testTag("currency_toggle_chip")
                    )
                    FilterChip(
                        selected = true,
                        onClick = {
                            onToggleLanguage(
                                if (state.language == AppLanguage.BN) AppLanguage.EN else AppLanguage.BN
                            )
                        },
                        label = {
                            Text(if (state.language == AppLanguage.BN) "বাংলা" else "EN")
                        },
                        modifier = Modifier.testTag("language_toggle_chip")
                    )
                }
            }
        }

        // Hero Balance Card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(EmeraldPrimary, TealTertiary, Color(0xFF0A4736))
                        )
                    )
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "${strings.totalBalance} (${strings.equivalentIn})",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = formatCurrency(state.combinedBalanceInPreferred, prefCode),
                                style = MaterialTheme.typography.displaySmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AccountBalanceWallet,
                                contentDescription = strings.totalBalance,
                                tint = Color.White
                            )
                        }
                    }

                    // Dual currency breakdown row (BDT & SAR separately)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            color = Color.White.copy(alpha = 0.14f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "BDT ${strings.netBalance}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                                Text(
                                    text = formatCurrency(state.netBalanceBdt, CurrencyCode.BDT.code),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            color = Color.White.copy(alpha = 0.14f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "SAR ${strings.netBalance}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                                Text(
                                    text = formatCurrency(state.netBalanceSar, CurrencyCode.SAR.code),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Cash in hand bar
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = Color.Black.copy(alpha = 0.22f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Wallet,
                                    contentDescription = strings.cashInHand,
                                    tint = Color(0xFFFDE68A),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = strings.cashInHand,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color(0xFFFDE68A)
                                )
                            }
                            Text(
                                text = "${formatCurrency(state.cashInHandBdt, "BDT")} | ${formatCurrency(state.cashInHandSar, "SAR")}",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Total Income & Total Expense Cards (clickable quick add)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricStatCard(
                    title = strings.totalIncome,
                    mainValue = formatCurrency(state.combinedIncomeInPreferred, prefCode),
                    subValue = "৳ ${state.totalIncomeBdt.toLong()} · SAR ${state.totalIncomeSar.toLong()}",
                    accentColor = IncomeGreen,
                    icon = Icons.Filled.ArrowDownward,
                    onClick = onQuickAddIncome,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("dashboard_income_card")
                )
                MetricStatCard(
                    title = strings.totalExpense,
                    mainValue = formatCurrency(state.combinedExpenseInPreferred, prefCode),
                    subValue = "৳ ${state.totalExpenseBdt.toLong()} · SAR ${state.totalExpenseSar.toLong()}",
                    accentColor = ExpenseCoral,
                    icon = Icons.Filled.ArrowUpward,
                    onClick = onQuickAddExpense,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("dashboard_expense_card")
                )
            }
        }

        // This Month Summary Card
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
                        text = strings.thisMonthSummary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = strings.totalIncome,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = formatCurrency(state.thisMonthIncomeInPreferred, prefCode),
                                style = MaterialTheme.typography.titleMedium,
                                color = IncomeGreen,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = strings.totalExpense,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = formatCurrency(state.thisMonthExpenseInPreferred, prefCode),
                                style = MaterialTheme.typography.titleMedium,
                                color = ExpenseCoral,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    val monthNet = state.thisMonthIncomeInPreferred - state.thisMonthExpenseInPreferred
                    Text(
                        text = "${strings.netBalance}: ${formatCurrency(monthNet, prefCode)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (monthNet >= 0) IncomeGreen else ExpenseCoral
                    )
                }
            }
        }

        // Quick Access Modules: Savings & Loans
        item {
            Text(
                text = strings.quickModules,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onOpenSavings)
                        .testTag("open_savings_card"),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(SavingsAmber.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Savings,
                                    contentDescription = strings.savingsTitle,
                                    tint = SavingsAmber
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(
                            text = strings.savingsTitle,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = formatCurrency(state.totalSavedBdt, CurrencyCode.BDT.code),
                            style = MaterialTheme.typography.titleMedium,
                            color = SavingsAmber,
                            fontWeight = FontWeight.Bold
                        )
                        LinearProgressIndicator(
                            progress = { overallGoalProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = SavingsAmber
                        )
                        Text(
                            text = "${(overallGoalProgress * 100).toInt()}% ${strings.goalProgress}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onOpenLoans)
                        .testTag("open_loans_card"),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Handshake,
                                    contentDescription = strings.loansTitle,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(
                            text = strings.loansTitle,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${strings.lentTab.substringBefore(" ")}: ${formatCurrency(lentRemainingBdt, "BDT")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = IncomeGreen,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${strings.borrowedTab.substringBefore(" ")}: ${formatCurrency(borrowedRemainingBdt, "BDT")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = ExpenseCoral,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Admin Panel Quick Access Card (visible to Admin)
        if (state.isAdminSession) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenAdminPanel)
                        .testTag("dashboard_admin_panel_button"),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AdminPanelSettings,
                                contentDescription = strings.adminPanelTitle,
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = strings.adminPanelTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${strings.adminPanelSubtitle} (${state.managedUsers.size})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = strings.openAdminPanel,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // Charts
        item {
            IncomeExpenseBarChartCard(
                title = strings.incomeVsExpenseChart,
                bars = state.monthlyBars(6),
                incomeLabel = strings.incomeTab,
                expenseLabel = strings.expenseTab
            )
        }

        item {
            CategoryDonutChartCard(
                title = strings.expenseBreakdownChart,
                stats = state.categoryStatsFor(),
                strings = strings,
                currencyCode = prefCode,
                emptyText = strings.noExpensesYet
            )
        }

        // Recent Transactions
        if (recentItems.isNotEmpty()) {
            item {
                Text(
                    text = strings.recentTransactions,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            items(recentItems, key = { it.id }) { tx ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
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
                        val color = if (tx.isIncome) IncomeGreen else ExpenseCoral
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(color.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(tx.icon, contentDescription = tx.title, tint = color)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tx.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = tx.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "${if (tx.isIncome) "+" else "-"}${formatCurrency(tx.amount, tx.currency)}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = color
                            )
                            Text(
                                text = formatDateShort(tx.dateMillis, state.language),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }
    }
}

@Composable
private fun MetricStatCard(
    title: String,
    mainValue: String,
    subValue: String,
    accentColor: Color,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = mainValue,
                style = MaterialTheme.typography.titleLarge,
                color = accentColor,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subValue,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
