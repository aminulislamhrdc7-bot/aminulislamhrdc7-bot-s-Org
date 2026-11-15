package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.AppLanguage
import com.example.model.CurrencyCode
import com.example.model.ExpenseCategory
import com.example.model.IncomeSource
import com.example.model.LoanStatus
import com.example.model.LoanType
import com.example.model.ManagedUser
import com.example.model.UserRole
import com.example.ui.UserFinancialSummary
import com.example.ui.strings.AppStrings
import com.example.ui.strings.formatCurrency
import com.example.ui.strings.formatDateShort
import com.example.ui.theme.ExpenseCoral
import com.example.ui.theme.IncomeGreen
import com.example.ui.theme.SavingsAmber

@Composable
fun UserDetailsDialog(
    user: ManagedUser,
    summary: UserFinancialSummary,
    strings: AppStrings,
    language: AppLanguage,
    preferredCurrency: CurrencyCode,
    sarToBdtRate: Double,
    isCurrentSession: Boolean,
    onDismiss: () -> Unit,
    onLoginAsUser: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .heightIn(max = 680.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = user.fullName.firstOrNull()?.uppercase() ?: "U",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = user.fullName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "@${user.loginId} · ${
                                if (user.role == UserRole.ADMIN.code) strings.roleAdmin else strings.roleUser
                            } · ${summary.entryCount} ${strings.entriesCountSuffix}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = strings.cancel
                        )
                    }
                }

                // Financial Overview Card (Preferred currency + BDT/SAR split)
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = strings.userFinancialOverview,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DetailMetricBox(
                                label = strings.totalIncome,
                                value = formatCurrency(
                                    summary.combinedIncome(preferredCurrency, sarToBdtRate),
                                    preferredCurrency.code
                                ),
                                color = IncomeGreen,
                                modifier = Modifier.weight(1f)
                            )
                            DetailMetricBox(
                                label = strings.totalExpense,
                                value = formatCurrency(
                                    summary.combinedExpense(preferredCurrency, sarToBdtRate),
                                    preferredCurrency.code
                                ),
                                color = ExpenseCoral,
                                modifier = Modifier.weight(1f)
                            )
                            val net = summary.combinedNetBalance(preferredCurrency, sarToBdtRate)
                            DetailMetricBox(
                                label = strings.netBalance,
                                value = formatCurrency(net, preferredCurrency.code),
                                color = if (net >= 0.0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "BDT: ${formatCurrency(summary.netBalanceBdt, CurrencyCode.BDT.code)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "SAR: ${formatCurrency(summary.netBalanceSar, CurrencyCode.SAR.code)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Segmented Record Tabs
                val tabTitles = listOf(
                    "${strings.incomeTab} (${summary.incomes.size})",
                    "${strings.expenseTab} (${summary.expenses.size})",
                    "${strings.savingsTitle} (${summary.savingsGoals.size})",
                    "${strings.loansTitle} (${summary.loans.size})"
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    tabTitles.forEachIndexed { idx, label ->
                        FilterChip(
                            selected = selectedTab == idx,
                            onClick = { selectedTab = idx },
                            label = {
                                Text(
                                    text = label,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // List of user-specific records
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(min = 140.dp, max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    when (selectedTab) {
                        0 -> {
                            val itemsList = summary.incomes.sortedByDescending { it.dateMillis }
                            if (itemsList.isEmpty()) {
                                item { EmptyUserRecordsNotice(strings.noUserRecordsYet) }
                            } else {
                                items(itemsList, key = { it.id }) { inc ->
                                    UserRecordRow(
                                        title = strings.incomeSourceLabel(IncomeSource.fromCode(inc.source)),
                                        subtitle = buildString {
                                            append(formatDateShort(inc.dateMillis, language))
                                            if (inc.description.isNotBlank()) append(" · ${inc.description}")
                                        },
                                        amountText = "+${formatCurrency(inc.amount, inc.currency)}",
                                        amountColor = IncomeGreen
                                    )
                                }
                            }
                        }
                        1 -> {
                            val itemsList = summary.expenses.sortedByDescending { it.dateMillis }
                            if (itemsList.isEmpty()) {
                                item { EmptyUserRecordsNotice(strings.noUserRecordsYet) }
                            } else {
                                items(itemsList, key = { it.id }) { exp ->
                                    UserRecordRow(
                                        title = strings.expenseCategoryLabel(ExpenseCategory.fromCode(exp.category)),
                                        subtitle = buildString {
                                            append(formatDateShort(exp.dateMillis, language))
                                            if (exp.description.isNotBlank()) append(" · ${exp.description}")
                                        },
                                        amountText = "-${formatCurrency(exp.amount, exp.currency)}",
                                        amountColor = ExpenseCoral
                                    )
                                }
                            }
                        }
                        2 -> {
                            if (summary.savingsGoals.isEmpty()) {
                                item { EmptyUserRecordsNotice(strings.noUserRecordsYet) }
                            } else {
                                items(summary.savingsGoals, key = { it.id }) { goal ->
                                    UserRecordRow(
                                        title = goal.title,
                                        subtitle = "${strings.goalProgress}: ${goal.progressPercent}% · ${
                                            formatCurrency(goal.targetAmount, goal.currency)
                                        }",
                                        amountText = formatCurrency(goal.savedAmount, goal.currency),
                                        amountColor = SavingsAmber
                                    )
                                }
                            }
                        }
                        else -> {
                            val itemsList = summary.loans.sortedByDescending { it.dateMillis }
                            if (itemsList.isEmpty()) {
                                item { EmptyUserRecordsNotice(strings.noUserRecordsYet) }
                            } else {
                                items(itemsList, key = { it.id }) { loan ->
                                    val statusLabel = strings.loanStatusLabel(LoanStatus.fromCode(loan.status))
                                    val typeLabel = strings.loanTypeLabel(LoanType.fromCode(loan.type))
                                    val phonePart = if (loan.personPhone.isNotBlank()) " · ${loan.personPhone}" else ""
                                    UserRecordRow(
                                        title = "${loan.personName}$phonePart ($typeLabel)",
                                        subtitle = "${formatDateShort(loan.dateMillis, language)} · $statusLabel",
                                        amountText = formatCurrency(loan.remainingAmount, loan.currency),
                                        amountColor = if (loan.type == LoanType.LENT.code) IncomeGreen else ExpenseCoral
                                    )
                                }
                            }
                        }
                    }
                }

                // Footer Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text(strings.cancel)
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Button(
                        onClick = {
                            onDismiss()
                            onLoginAsUser()
                        },
                        enabled = user.isActive && !isCurrentSession,
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("details_login_as_${user.loginId}"),
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
                }
            }
        }
    }
}

@Composable
private fun DetailMetricBox(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
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
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun UserRecordRow(
    title: String,
    subtitle: String,
    amountText: String,
    amountColor: Color
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = amountText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = amountColor,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun EmptyUserRecordsNotice(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
