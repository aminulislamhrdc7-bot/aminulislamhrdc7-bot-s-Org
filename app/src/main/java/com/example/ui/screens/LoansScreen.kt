package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.CurrencyCode
import com.example.model.LoanRecord
import com.example.model.LoanRepayment
import com.example.model.LoanStatus
import com.example.model.LoanType
import com.example.ui.HisabUiState
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.components.CurrencySelectorRow
import com.example.ui.components.HisabDatePickerField
import com.example.ui.strings.formatCurrency
import com.example.ui.strings.formatDateShort
import com.example.ui.theme.ExpenseCoral
import com.example.ui.theme.IncomeGreen
import com.example.ui.theme.SavingsAmber

private data class DatedLoanEvent(
    val key: String,
    val isRepayment: Boolean,
    val loanType: LoanType,
    val personName: String,
    val personPhone: String = "",
    val amount: Double,
    val currency: String,
    val dateMillis: Long,
    val note: String,
    val repayment: LoanRepayment? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoansScreen(
    state: HisabUiState,
    onBack: () -> Unit,
    onAddLoan: (String, String, String, Double, Double, String, Long, Long, String) -> Unit,
    onUpdateLoan: (LoanRecord) -> Unit,
    onRecordRepayment: (LoanRecord, Double, Long, String) -> Unit,
    onDeleteRepayment: (LoanRepayment) -> Unit,
    onDeleteLoan: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val strings = state.strings
    var selectedTab by remember { mutableIntStateOf(0) }
    val activeType = if (selectedTab == 1) LoanType.BORROWED else LoanType.LENT

    var searchQuery by remember { mutableStateOf("") }
    var selectedDateMillis by remember { mutableStateOf<Long?>(null) }
    var showDateFilterPicker by remember { mutableStateOf(false) }

    var showAddDialog by remember { mutableStateOf(false) }
    var editingLoan by remember { mutableStateOf<LoanRecord?>(null) }
    var repayLoan by remember { mutableStateOf<LoanRecord?>(null) }
    var deletingLoan by remember { mutableStateOf<LoanRecord?>(null) }
    var deletingRepayment by remember { mutableStateOf<LoanRepayment?>(null) }

    val repaymentsByLoan = remember(state.loanRepayments) {
        state.loanRepayments.groupBy { it.loanId }
    }

    val filteredLoans = remember(state.loans, repaymentsByLoan, activeType, searchQuery, selectedDateMillis) {
        state.loans.filter { loan ->
            if (loan.type != activeType.code) return@filter false
            val reps = repaymentsByLoan[loan.id].orEmpty()
            val matchesQuery = searchQuery.isBlank() ||
                loan.personName.contains(searchQuery, ignoreCase = true) ||
                loan.personPhone.contains(searchQuery, ignoreCase = true) ||
                loan.note.contains(searchQuery, ignoreCase = true) ||
                formatDateShort(loan.dateMillis, state.language).contains(searchQuery, ignoreCase = true) ||
                reps.any {
                    it.note.contains(searchQuery, ignoreCase = true) ||
                        formatDateShort(it.dateMillis, state.language).contains(searchQuery, ignoreCase = true)
                }
            val matchesDate = selectedDateMillis == null ||
                isSameDay(loan.dateMillis, selectedDateMillis!!) ||
                (loan.dueDateMillis > 0L && isSameDay(loan.dueDateMillis, selectedDateMillis!!)) ||
                reps.any { isSameDay(it.dateMillis, selectedDateMillis!!) }
            matchesQuery && matchesDate
        }
    }

    val datedEvents = remember(state.loans, state.loanRepayments, searchQuery, selectedDateMillis, state.language) {
        val loanById = state.loans.associateBy { it.id }
        val loanEvents = state.loans.map { loan ->
            DatedLoanEvent(
                key = "loan_${loan.id}",
                isRepayment = false,
                loanType = LoanType.fromCode(loan.type),
                personName = loan.personName,
                personPhone = loan.personPhone,
                amount = loan.amount,
                currency = loan.currency,
                dateMillis = loan.dateMillis,
                note = loan.note
            )
        }
        val repayEvents = state.loanRepayments.map { rep ->
            DatedLoanEvent(
                key = "rep_${rep.id}",
                isRepayment = true,
                loanType = LoanType.fromCode(rep.loanType),
                personName = rep.personName,
                personPhone = loanById[rep.loanId]?.personPhone.orEmpty(),
                amount = rep.amount,
                currency = rep.currency,
                dateMillis = rep.dateMillis,
                note = rep.note,
                repayment = rep
            )
        }
        (loanEvents + repayEvents)
            .filter { ev ->
                val matchesQuery = searchQuery.isBlank() ||
                    ev.personName.contains(searchQuery, ignoreCase = true) ||
                    ev.personPhone.contains(searchQuery, ignoreCase = true) ||
                    ev.note.contains(searchQuery, ignoreCase = true) ||
                    formatDateShort(ev.dateMillis, state.language).contains(searchQuery, ignoreCase = true)
                val matchesDate = selectedDateMillis == null || isSameDay(ev.dateMillis, selectedDateMillis!!)
                matchesQuery && matchesDate
            }
            .sortedByDescending { it.dateMillis }
    }

    val totalAmountBdt = filteredLoans.filter { it.currency == CurrencyCode.BDT.code }.sumOf { it.amount }
    val remainingBdt = filteredLoans.filter { it.currency == CurrencyCode.BDT.code }.sumOf { it.remainingAmount }
    val remainingSar = filteredLoans.filter { it.currency == CurrencyCode.SAR.code }.sumOf { it.remainingAmount }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(strings.loansTitle, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("loans_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = strings.addLoan) },
                text = { Text(strings.addLoan) },
                modifier = Modifier.testTag("add_loan_fab")
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(strings.lentTab, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.testTag("loans_lent_tab")
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(strings.borrowedTab, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.testTag("loans_borrowed_tab")
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text(strings.loanDateHistoryTab, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.testTag("loans_date_history_tab")
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Search & Date Filter Bar
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
                                .testTag("loans_search_input")
                        )
                        FilterChip(
                            selected = selectedDateMillis != null,
                            onClick = { showDateFilterPicker = true },
                            leadingIcon = { Icon(Icons.Filled.CalendarMonth, contentDescription = strings.filterByDate) },
                            label = {
                                Text(
                                    if (selectedDateMillis != null) {
                                        formatDateShort(selectedDateMillis!!, state.language)
                                    } else {
                                        strings.filterByDate
                                    },
                                    maxLines = 1
                                )
                            },
                            modifier = Modifier.testTag("loans_date_filter_chip")
                        )
                        if (selectedDateMillis != null) {
                            IconButton(
                                onClick = { selectedDateMillis = null },
                                modifier = Modifier.testTag("loans_clear_date_filter")
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = strings.clearFilter)
                            }
                        }
                    }
                }

                if (selectedTab != 2) {
                    item {
                        val accent = if (activeType == LoanType.LENT) IncomeGreen else ExpenseCoral
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.12f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(strings.totalAmount, style = MaterialTheme.typography.labelMedium)
                                    Text(
                                        text = formatCurrency(totalAmountBdt, "BDT"),
                                        style = MaterialTheme.typography.titleLarge,
                                        color = accent,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(strings.remainingAmount, style = MaterialTheme.typography.labelMedium)
                                    Text(
                                        text = "${formatCurrency(remainingBdt, "BDT")} · ${formatCurrency(remainingSar, "SAR")}",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = accent,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    if (filteredLoans.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Text(
                                    text = strings.noLoansYet,
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
                        items(filteredLoans, key = { it.id }) { loan ->
                            val status = LoanStatus.fromCode(loan.status)
                            val statusColor = when (status) {
                                LoanStatus.SETTLED -> IncomeGreen
                                LoanStatus.PARTIAL -> SavingsAmber
                                LoanStatus.PENDING -> ExpenseCoral
                            }
                            val progress = if (loan.amount > 0.0) (loan.paidAmount / loan.amount).toFloat().coerceIn(0f, 1f) else 0f
                            val loanReps = repaymentsByLoan[loan.id].orEmpty().sortedByDescending { it.dateMillis }
                            var showHistory by remember(loan.id) { mutableStateOf(loanReps.isNotEmpty()) }

                            val originDateLabel = if (loan.type == LoanType.LENT.code) {
                                strings.loanGivenDateLabel
                            } else {
                                strings.loanBorrowedDateLabel
                            }

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
                                                text = loan.personName,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            if (loan.personPhone.isNotBlank()) {
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                                    modifier = Modifier
                                                        .clickable {
                                                            val intent = Intent(
                                                                Intent.ACTION_DIAL,
                                                                Uri.parse("tel:${loan.personPhone.trim()}")
                                                            )
                                                            runCatching { context.startActivity(intent) }
                                                        }
                                                        .testTag("call_loan_person_${loan.id}")
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Filled.Phone,
                                                            contentDescription = strings.callPerson,
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                        Text(
                                                            text = loan.personPhone,
                                                            style = MaterialTheme.typography.labelMedium,
                                                            color = MaterialTheme.colorScheme.primary,
                                                            fontWeight = FontWeight.SemiBold
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(2.dp))
                                            }
                                            Text(
                                                text = "$originDateLabel: ${formatDateShort(loan.dateMillis, state.language)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            if (loan.dueDateMillis > 0L) {
                                                Text(
                                                    text = "${strings.dueDateOptionalLabel}: ${formatDateShort(loan.dueDateMillis, state.language)}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = SavingsAmber
                                                )
                                            }
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = statusColor.copy(alpha = 0.14f)
                                        ) {
                                            Text(
                                                text = strings.loanStatusLabel(status),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = statusColor,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                            )
                                        }
                                    }

                                    LinearProgressIndicator(
                                        progress = { progress },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp)),
                                        color = statusColor
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "${strings.totalAmount}: ${formatCurrency(loan.amount, loan.currency)}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Text(
                                            text = "${strings.paidAmount}: ${formatCurrency(loan.paidAmount, loan.currency)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = IncomeGreen
                                        )
                                        Text(
                                            text = "${strings.remainingAmount}: ${formatCurrency(loan.remainingAmount, loan.currency)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = statusColor,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    if (loan.note.isNotBlank()) {
                                        Text(
                                            text = loan.note,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    // Dated Repayment History Section
                                    if (loanReps.isNotEmpty()) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { showHistory = !showHistory }
                                                .padding(vertical = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.History,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    text = "${strings.repaymentHistoryTitle} (${loanReps.size})",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                            Icon(
                                                imageVector = if (showHistory) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        AnimatedVisibility(visible = showHistory) {
                                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                loanReps.forEach { rep ->
                                                    Surface(
                                                        shape = RoundedCornerShape(10.dp),
                                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                                    ) {
                                                        Row(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .padding(horizontal = 10.dp, vertical = 6.dp),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.SpaceBetween
                                                        ) {
                                                            Column(modifier = Modifier.weight(1f)) {
                                                                Text(
                                                                    text = formatDateShort(rep.dateMillis, state.language),
                                                                    style = MaterialTheme.typography.labelMedium,
                                                                    fontWeight = FontWeight.SemiBold
                                                                )
                                                                if (rep.note.isNotBlank()) {
                                                                    Text(
                                                                        text = rep.note,
                                                                        style = MaterialTheme.typography.labelSmall,
                                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                        maxLines = 1,
                                                                        overflow = TextOverflow.Ellipsis
                                                                    )
                                                                }
                                                            }
                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                Text(
                                                                    text = "+ ${formatCurrency(rep.amount, rep.currency)}",
                                                                    style = MaterialTheme.typography.labelLarge,
                                                                    color = IncomeGreen,
                                                                    fontWeight = FontWeight.Bold
                                                                )
                                                                IconButton(
                                                                    onClick = { deletingRepayment = rep },
                                                                    modifier = Modifier
                                                                        .size(36.dp)
                                                                        .testTag("delete_repayment_${rep.id}")
                                                                ) {
                                                                    Icon(
                                                                        imageVector = Icons.Filled.Delete,
                                                                        contentDescription = strings.delete,
                                                                        tint = MaterialTheme.colorScheme.error,
                                                                        modifier = Modifier.size(18.dp)
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (loan.remainingAmount > 0.0) {
                                            OutlinedButton(
                                                onClick = { repayLoan = loan },
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier.testTag("record_repayment_${loan.id}")
                                            ) {
                                                Icon(Icons.Filled.Payments, contentDescription = null)
                                                Text("  ${strings.recordRepayment}")
                                            }
                                        } else {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                        Row {
                                            IconButton(onClick = { editingLoan = loan }) {
                                                Icon(Icons.Filled.Edit, contentDescription = strings.edit)
                                            }
                                            IconButton(onClick = { deletingLoan = loan }) {
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
                        }
                    }
                } else {
                    // Date-wise Timeline Tab (Tab 2)
                    if (datedEvents.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Text(
                                    text = strings.noLoanHistoryYet,
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
                        items(datedEvents, key = { it.key }) { event ->
                            val badgeText = when {
                                !event.isRepayment && event.loanType == LoanType.LENT -> strings.lentEventBadge
                                !event.isRepayment && event.loanType == LoanType.BORROWED -> strings.borrowedEventBadge
                                event.isRepayment && event.loanType == LoanType.LENT -> strings.collectedEventBadge
                                else -> strings.repaidEventBadge
                            }
                            val accentColor: Color = when {
                                event.isRepayment && event.loanType == LoanType.LENT -> IncomeGreen
                                event.isRepayment && event.loanType == LoanType.BORROWED -> MaterialTheme.colorScheme.primary
                                event.loanType == LoanType.LENT -> SavingsAmber
                                else -> ExpenseCoral
                            }
                            val icon = if (event.isRepayment) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward

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
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .background(accentColor.copy(alpha = 0.14f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(icon, contentDescription = badgeText, tint = accentColor)
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = event.personName,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = accentColor.copy(alpha = 0.14f)
                                            ) {
                                                Text(
                                                    text = badgeText,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = accentColor,
                                                    fontWeight = FontWeight.SemiBold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = formatDateShort(event.dateMillis, state.language),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Medium
                                        )
                                        if (event.personPhone.isNotBlank()) {
                                            Row(
                                                modifier = Modifier
                                                    .padding(top = 2.dp)
                                                    .clickable {
                                                        val intent = Intent(
                                                            Intent.ACTION_DIAL,
                                                            Uri.parse("tel:${event.personPhone.trim()}")
                                                        )
                                                        runCatching { context.startActivity(intent) }
                                                    },
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Phone,
                                                    contentDescription = strings.callPerson,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                                Text(
                                                    text = event.personPhone,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                        if (event.note.isNotBlank()) {
                                            Text(
                                                text = event.note,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = formatCurrency(event.amount, event.currency),
                                            style = MaterialTheme.typography.titleSmall,
                                            color = accentColor,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (event.repayment != null) {
                                            IconButton(
                                                onClick = { deletingRepayment = event.repayment },
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .testTag("timeline_delete_repayment_${event.repayment.id}")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Delete,
                                                    contentDescription = strings.delete,
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
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
    }

    if (showDateFilterPicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDateMillis ?: System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showDateFilterPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedDateMillis = pickerState.selectedDateMillis
                        showDateFilterPicker = false
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDateFilterPicker = false }) { Text(strings.cancel) }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (showAddDialog || editingLoan != null) {
        val current = editingLoan
        var loanType by remember(current, activeType) { mutableStateOf(current?.let { LoanType.fromCode(it.type) } ?: activeType) }
        var personName by remember(current) { mutableStateOf(current?.personName ?: "") }
        var personPhone by remember(current) { mutableStateOf(current?.personPhone ?: "") }
        var amountText by remember(current) { mutableStateOf(current?.amount?.toLong()?.toString() ?: "") }
        var paidText by remember(current) { mutableStateOf(current?.paidAmount?.toLong()?.toString() ?: "0") }
        var currency by remember(current) { mutableStateOf(current?.let { CurrencyCode.fromCode(it.currency) } ?: state.preferredCurrency) }
        var dateMillis by remember(current) { mutableLongStateOf(current?.dateMillis ?: System.currentTimeMillis()) }
        var hasDueDate by remember(current) { mutableStateOf((current?.dueDateMillis ?: 0L) > 0L) }
        var dueDateMillis by remember(current) {
            mutableLongStateOf(
                if ((current?.dueDateMillis ?: 0L) > 0L) current!!.dueDateMillis else System.currentTimeMillis()
            )
        }
        var note by remember(current) { mutableStateOf(current?.note ?: "") }
        var error by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showAddDialog = false; editingLoan = null },
            title = { Text(if (current == null) strings.addLoan else strings.editLoan, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LoanType.entries.forEach { t ->
                            FilterChip(
                                selected = loanType == t,
                                onClick = { loanType = t },
                                label = { Text(strings.loanTypeLabel(t)) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = personName,
                        onValueChange = { personName = it.take(100); error = null },
                        label = {
                            Text(
                                if (loanType == LoanType.LENT) strings.borrowerNameLabel
                                else strings.lenderNameLabel
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("loan_person_input")
                    )
                    OutlinedTextField(
                        value = personPhone,
                        onValueChange = { personPhone = it.take(40); error = null },
                        label = {
                            Text(
                                if (loanType == LoanType.LENT) strings.borrowerPhoneLabel
                                else strings.lenderPhoneLabel
                            )
                        },
                        placeholder = { Text(strings.personPhoneHint) },
                        leadingIcon = { Icon(Icons.Filled.Phone, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("loan_phone_input")
                    )
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it; error = null },
                        label = { Text(strings.totalAmount) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("loan_amount_input")
                    )
                    OutlinedTextField(
                        value = paidText,
                        onValueChange = { paidText = it; error = null },
                        label = { Text(strings.paidAmount) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    CurrencySelectorRow(selected = currency, onSelect = { currency = it })
                    HisabDatePickerField(
                        label = if (loanType == LoanType.LENT) strings.loanGivenDateLabel else strings.loanBorrowedDateLabel,
                        dateMillis = dateMillis,
                        language = state.language,
                        onDateSelected = { dateMillis = it },
                        modifier = Modifier.testTag("loan_date_picker")
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(strings.setDueDateLabel, style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = hasDueDate,
                            onCheckedChange = { hasDueDate = it },
                            modifier = Modifier.testTag("loan_due_date_switch")
                        )
                    }
                    if (hasDueDate) {
                        HisabDatePickerField(
                            label = strings.dueDateOptionalLabel,
                            dateMillis = dueDateMillis,
                            language = state.language,
                            onDateSelected = { dueDateMillis = it },
                            modifier = Modifier.testTag("loan_due_date_picker")
                        )
                    }
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
                        val paid = (paidText.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0)
                        val finalDueMillis = if (hasDueDate) dueDateMillis else 0L
                        if (personName.isBlank()) {
                            error = strings.requiredFieldError
                        } else if (amt == null || amt <= 0.0) {
                            error = strings.invalidAmountError
                        } else {
                            if (current == null) {
                                onAddLoan(
                                    loanType.code,
                                    personName.trim(),
                                    personPhone.trim(),
                                    amt,
                                    paid.coerceAtMost(amt),
                                    currency.code,
                                    dateMillis,
                                    finalDueMillis,
                                    note.trim()
                                )
                            } else {
                                val clampedPaid = paid.coerceAtMost(amt)
                                onUpdateLoan(
                                    current.copy(
                                        type = loanType.code,
                                        personName = personName.trim(),
                                        personPhone = personPhone.trim(),
                                        amount = amt,
                                        paidAmount = clampedPaid,
                                        currency = currency.code,
                                        dateMillis = dateMillis,
                                        dueDateMillis = finalDueMillis,
                                        note = note.trim(),
                                        status = LoanStatus.compute(amt, clampedPaid).code
                                    )
                                )
                            }
                            showAddDialog = false
                            editingLoan = null
                        }
                    },
                    modifier = Modifier.testTag("save_loan_button")
                ) {
                    Text(strings.save)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false; editingLoan = null }) { Text(strings.cancel) }
            }
        )
    }

    repayLoan?.let { loan ->
        var repayText by remember(loan.id) { mutableStateOf("") }
        var repayDateMillis by remember(loan.id) { mutableLongStateOf(System.currentTimeMillis()) }
        var repayNote by remember(loan.id) { mutableStateOf("") }
        var error by remember(loan.id) { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { repayLoan = null },
            title = { Text("${strings.recordRepayment} - ${loan.personName}", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "${strings.remainingAmount}: ${formatCurrency(loan.remainingAmount, loan.currency)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    OutlinedTextField(
                        value = repayText,
                        onValueChange = { repayText = it; error = null },
                        label = { Text(strings.repaymentAmount) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("repayment_amount_input")
                    )
                    HisabDatePickerField(
                        label = strings.repaymentDateLabel,
                        dateMillis = repayDateMillis,
                        language = state.language,
                        onDateSelected = { repayDateMillis = it },
                        modifier = Modifier.testTag("repayment_date_picker")
                    )
                    OutlinedTextField(
                        value = repayNote,
                        onValueChange = { repayNote = it.take(300) },
                        label = { Text(strings.repaymentNoteLabel) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("repayment_note_input")
                    )
                    if (error != null) {
                        Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = repayText.toDoubleOrNull()
                        if (amt == null || amt <= 0.0) {
                            error = strings.invalidAmountError
                        } else {
                            onRecordRepayment(loan, amt, repayDateMillis, repayNote.trim())
                            repayLoan = null
                        }
                    },
                    modifier = Modifier.testTag("save_repayment_button")
                ) { Text(strings.save) }
            },
            dismissButton = {
                TextButton(onClick = { repayLoan = null }) { Text(strings.cancel) }
            }
        )
    }

    deletingLoan?.let { loan ->
        ConfirmDeleteDialog(
            strings = strings,
            onConfirm = { onDeleteLoan(loan.id); deletingLoan = null },
            onDismiss = { deletingLoan = null }
        )
    }

    deletingRepayment?.let { rep ->
        ConfirmDeleteDialog(
            strings = strings,
            onConfirm = { onDeleteRepayment(rep); deletingRepayment = null },
            onDismiss = { deletingRepayment = null }
        )
    }
}
