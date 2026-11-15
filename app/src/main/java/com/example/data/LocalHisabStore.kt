package com.example.data

import android.content.Context
import com.example.model.AppLanguage
import com.example.model.CurrencyCode
import com.example.model.DEFAULT_ADMIN_ACCOUNT_ID
import com.example.model.DEFAULT_ADMIN_LOGIN_ID
import com.example.model.ExpenseEntry
import com.example.model.IncomeEntry
import com.example.model.LoanRecord
import com.example.model.LoanRepayment
import com.example.model.LoanStatus
import com.example.model.ManagedUser
import com.example.model.SavingsEntry
import com.example.model.SavingsGoal
import com.example.model.UserRole
import com.example.model.UserSettings
import com.google.firebase.Timestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class LocalHisabStore(context: Context) : FinanceDataSource {

    private val storageFile = File(context.applicationContext.filesDir, FILE_NAME)
    private val mutex = Mutex()

    data class LocalSnapshot(
        val settings: UserSettings? = null,
        val managedUsers: List<ManagedUser> = emptyList(),
        val incomes: List<IncomeEntry> = emptyList(),
        val expenses: List<ExpenseEntry> = emptyList(),
        val savingsGoals: List<SavingsGoal> = emptyList(),
        val savingsEntries: List<SavingsEntry> = emptyList(),
        val loans: List<LoanRecord> = emptyList(),
        val loanRepayments: List<LoanRepayment> = emptyList()
    )

    private val state = MutableStateFlow(readFromDisk())

    override fun observeUserSettings(): Flow<UserSettings?> = state.map { it.settings }

    override fun observeManagedUsers(): Flow<List<ManagedUser>> =
        state.map { s -> s.managedUsers.sortedBy { it.loginId.lowercase() } }

    override fun observeIncomes(): Flow<List<IncomeEntry>> =
        state.map { s -> s.incomes.sortedByDescending { it.dateMillis } }

    override fun observeExpenses(): Flow<List<ExpenseEntry>> =
        state.map { s -> s.expenses.sortedByDescending { it.dateMillis } }

    override fun observeSavingsGoals(): Flow<List<SavingsGoal>> =
        state.map { s -> s.savingsGoals.sortedByDescending { it.createdAt?.seconds ?: 0L } }

    override fun observeSavingsEntries(): Flow<List<SavingsEntry>> =
        state.map { s -> s.savingsEntries.sortedByDescending { it.dateMillis } }

    override fun observeLoans(): Flow<List<LoanRecord>> =
        state.map { s -> s.loans.sortedByDescending { it.dateMillis } }

    override fun observeLoanRepayments(): Flow<List<LoanRepayment>> =
        state.map { s -> s.loanRepayments.sortedByDescending { it.dateMillis } }

    override suspend fun ensureDefaultAdminUser(
        loginId: String,
        fullName: String,
        passwordHash: String
    ) {
        val normalizedId = loginId.trim().lowercase()
        updateSnapshot { cur ->
            if (cur.managedUsers.any { it.loginId.equals(normalizedId, ignoreCase = true) }) {
                cur
            } else {
                val now = Timestamp.now()
                val adminUser = ManagedUser(
                    id = "admin_$normalizedId",
                    userId = LOCAL_UID,
                    loginId = normalizedId,
                    fullName = fullName.trim().take(100),
                    passwordHash = passwordHash,
                    role = UserRole.ADMIN.code,
                    isActive = true,
                    createdAt = now,
                    updatedAt = now
                )
                cur.copy(managedUsers = cur.managedUsers + adminUser)
            }
        }
    }

    override suspend fun createManagedUser(
        loginId: String,
        fullName: String,
        rawPassword: String,
        role: UserRole,
        isActive: Boolean
    ): String {
        val normalizedId = loginId.trim().lowercase()
        val id = newId()
        val now = Timestamp.now()
        val user = ManagedUser(
            id = id,
            userId = LOCAL_UID,
            loginId = normalizedId,
            fullName = fullName.trim().take(100),
            passwordHash = HisabRepository.hashUserPassword(normalizedId, rawPassword),
            role = role.code,
            isActive = isActive,
            createdAt = now,
            updatedAt = now
        )
        updateSnapshot { cur -> cur.copy(managedUsers = cur.managedUsers + user) }
        return id
    }

    override suspend fun updateManagedUser(user: ManagedUser, newRawPassword: String?) {
        require(user.id.isNotBlank()) { "Managed user ID must not be blank" }
        val normalizedId = user.loginId.trim().lowercase()
        val passwordHash = if (!newRawPassword.isNullOrBlank()) {
            HisabRepository.hashUserPassword(normalizedId, newRawPassword)
        } else {
            user.passwordHash
        }
        val updated = user.copy(
            loginId = normalizedId,
            fullName = user.fullName.trim().take(100),
            passwordHash = passwordHash,
            updatedAt = Timestamp.now()
        )
        updateSnapshot { cur ->
            cur.copy(managedUsers = cur.managedUsers.map { if (it.id == user.id) updated else it })
        }
    }

    override suspend fun deleteManagedUser(managedUserId: String) {
        updateSnapshot { cur ->
            cur.copy(managedUsers = cur.managedUsers.filterNot { it.id == managedUserId })
        }
    }

    override suspend fun saveUserSettings(
        displayName: String,
        sarToBdtRate: Double,
        preferredCurrency: CurrencyCode,
        language: AppLanguage,
        existingCreatedAt: Timestamp?
    ) {
        val now = Timestamp.now()
        updateSnapshot { cur ->
            cur.copy(
                settings = UserSettings(
                    id = LOCAL_UID,
                    userId = LOCAL_UID,
                    displayName = displayName.take(120),
                    sarToBdtRate = sarToBdtRate.coerceIn(0.01, 10000.0),
                    preferredCurrency = preferredCurrency.code,
                    language = language.code,
                    createdAt = existingCreatedAt ?: cur.settings?.createdAt ?: now,
                    updatedAt = now
                )
            )
        }
    }

    override suspend fun addIncome(
        amount: Double,
        currency: String,
        source: String,
        dateMillis: Long,
        description: String,
        accountId: String
    ): String {
        val id = newId()
        val now = Timestamp.now()
        val entry = IncomeEntry(
            id = id,
            userId = LOCAL_UID,
            accountId = accountId.ifBlank { DEFAULT_ADMIN_ACCOUNT_ID },
            amount = amount,
            currency = currency,
            source = source,
            dateMillis = dateMillis,
            description = description.take(500),
            createdAt = now,
            updatedAt = now
        )
        updateSnapshot { cur -> cur.copy(incomes = cur.incomes + entry) }
        return id
    }

    override suspend fun updateIncome(income: IncomeEntry) {
        val updated = income.copy(
            accountId = income.accountId.ifBlank { DEFAULT_ADMIN_ACCOUNT_ID },
            description = income.description.take(500),
            updatedAt = Timestamp.now()
        )
        updateSnapshot { cur ->
            cur.copy(incomes = cur.incomes.map { if (it.id == income.id) updated else it })
        }
    }

    override suspend fun deleteIncome(incomeId: String) {
        updateSnapshot { cur ->
            cur.copy(incomes = cur.incomes.filterNot { it.id == incomeId })
        }
    }

    override suspend fun addExpense(
        amount: Double,
        currency: String,
        category: String,
        dateMillis: Long,
        description: String,
        accountId: String
    ): String {
        val id = newId()
        val now = Timestamp.now()
        val entry = ExpenseEntry(
            id = id,
            userId = LOCAL_UID,
            accountId = accountId.ifBlank { DEFAULT_ADMIN_ACCOUNT_ID },
            amount = amount,
            currency = currency,
            category = category,
            dateMillis = dateMillis,
            description = description.take(500),
            createdAt = now,
            updatedAt = now
        )
        updateSnapshot { cur -> cur.copy(expenses = cur.expenses + entry) }
        return id
    }

    override suspend fun updateExpense(expense: ExpenseEntry) {
        val updated = expense.copy(
            accountId = expense.accountId.ifBlank { DEFAULT_ADMIN_ACCOUNT_ID },
            description = expense.description.take(500),
            updatedAt = Timestamp.now()
        )
        updateSnapshot { cur ->
            cur.copy(expenses = cur.expenses.map { if (it.id == expense.id) updated else it })
        }
    }

    override suspend fun deleteExpense(expenseId: String) {
        updateSnapshot { cur ->
            cur.copy(expenses = cur.expenses.filterNot { it.id == expenseId })
        }
    }

    override suspend fun addSavingsGoal(
        title: String,
        targetAmount: Double,
        savedAmount: Double,
        currency: String,
        frequency: String,
        targetDateMillis: Long,
        accountId: String
    ): String {
        val id = newId()
        val now = Timestamp.now()
        val goal = SavingsGoal(
            id = id,
            userId = LOCAL_UID,
            accountId = accountId.ifBlank { DEFAULT_ADMIN_ACCOUNT_ID },
            title = title.take(100),
            targetAmount = targetAmount,
            savedAmount = savedAmount,
            currency = currency,
            frequency = frequency,
            targetDateMillis = targetDateMillis,
            createdAt = now,
            updatedAt = now
        )
        updateSnapshot { cur -> cur.copy(savingsGoals = cur.savingsGoals + goal) }
        return id
    }

    override suspend fun updateSavingsGoal(goal: SavingsGoal) {
        val updated = goal.copy(
            accountId = goal.accountId.ifBlank { DEFAULT_ADMIN_ACCOUNT_ID },
            title = goal.title.take(100),
            updatedAt = Timestamp.now()
        )
        updateSnapshot { cur ->
            cur.copy(savingsGoals = cur.savingsGoals.map { if (it.id == goal.id) updated else it })
        }
    }

    override suspend fun deleteSavingsGoal(goalId: String) {
        updateSnapshot { cur ->
            cur.copy(savingsGoals = cur.savingsGoals.filterNot { it.id == goalId })
        }
    }

    override suspend fun addSavingsEntry(
        goal: SavingsGoal?,
        amount: Double,
        currency: String,
        frequency: String,
        dateMillis: Long,
        note: String,
        accountId: String
    ): String {
        val id = newId()
        val now = Timestamp.now()
        val entry = SavingsEntry(
            id = id,
            userId = LOCAL_UID,
            accountId = accountId.ifBlank { DEFAULT_ADMIN_ACCOUNT_ID },
            goalId = goal?.id ?: "",
            goalTitle = (goal?.title ?: "").take(100),
            amount = amount,
            currency = currency,
            frequency = frequency,
            dateMillis = dateMillis,
            note = note.take(300),
            createdAt = now,
            updatedAt = now
        )
        updateSnapshot { cur ->
            val updatedGoals = if (goal != null && goal.id.isNotBlank()) {
                cur.savingsGoals.map { g ->
                    if (g.id == goal.id) g.copy(savedAmount = g.savedAmount + amount, updatedAt = now) else g
                }
            } else {
                cur.savingsGoals
            }
            cur.copy(savingsEntries = cur.savingsEntries + entry, savingsGoals = updatedGoals)
        }
        return id
    }

    override suspend fun deleteSavingsEntry(entry: SavingsEntry, linkedGoal: SavingsGoal?) {
        val now = Timestamp.now()
        updateSnapshot { cur ->
            val updatedGoals = if (linkedGoal != null && linkedGoal.id.isNotBlank()) {
                cur.savingsGoals.map { g ->
                    if (g.id == linkedGoal.id) {
                        g.copy(savedAmount = (g.savedAmount - entry.amount).coerceAtLeast(0.0), updatedAt = now)
                    } else g
                }
            } else {
                cur.savingsGoals
            }
            cur.copy(
                savingsEntries = cur.savingsEntries.filterNot { it.id == entry.id },
                savingsGoals = updatedGoals
            )
        }
    }

    override suspend fun addLoan(
        type: String,
        personName: String,
        personPhone: String,
        amount: Double,
        paidAmount: Double,
        currency: String,
        dateMillis: Long,
        dueDateMillis: Long,
        note: String,
        accountId: String
    ): String {
        val effectiveAccountId = accountId.ifBlank { DEFAULT_ADMIN_ACCOUNT_ID }
        val id = newId()
        val now = Timestamp.now()
        val clampedPaid = paidAmount.coerceIn(0.0, amount)
        val status = LoanStatus.compute(amount, clampedPaid).code
        val loan = LoanRecord(
            id = id,
            userId = LOCAL_UID,
            accountId = effectiveAccountId,
            type = type,
            personName = personName.trim().take(100),
            personPhone = personPhone.trim().take(40),
            amount = amount,
            paidAmount = clampedPaid,
            currency = currency,
            dateMillis = dateMillis,
            dueDateMillis = dueDateMillis,
            note = note.take(300),
            status = status,
            createdAt = now,
            updatedAt = now
        )
        val initialRepay = if (clampedPaid > 0.0) {
            LoanRepayment(
                id = newId(),
                userId = LOCAL_UID,
                accountId = effectiveAccountId,
                loanId = id,
                loanType = type,
                personName = personName.trim().take(100),
                amount = clampedPaid,
                currency = currency,
                dateMillis = dateMillis,
                note = note.take(300),
                createdAt = now,
                updatedAt = now
            )
        } else null
        updateSnapshot { cur ->
            cur.copy(
                loans = cur.loans + loan,
                loanRepayments = if (initialRepay != null) cur.loanRepayments + initialRepay else cur.loanRepayments
            )
        }
        return id
    }

    override suspend fun addLoanRepayment(
        loan: LoanRecord,
        amount: Double,
        dateMillis: Long,
        note: String,
        accountId: String
    ): String {
        val effectiveAccountId = loan.accountId.ifBlank { accountId.ifBlank { DEFAULT_ADMIN_ACCOUNT_ID } }
        val id = newId()
        val now = Timestamp.now()
        val repay = LoanRepayment(
            id = id,
            userId = LOCAL_UID,
            accountId = effectiveAccountId,
            loanId = loan.id,
            loanType = loan.type,
            personName = loan.personName.trim().take(100),
            amount = amount,
            currency = loan.currency,
            dateMillis = dateMillis,
            note = note.trim().take(300),
            createdAt = now,
            updatedAt = now
        )
        updateSnapshot { cur ->
            val updatedLoans = cur.loans.map { l ->
                if (l.id == loan.id) {
                    val newPaid = (l.paidAmount + amount).coerceIn(0.0, l.amount)
                    l.copy(
                        paidAmount = newPaid,
                        status = LoanStatus.compute(l.amount, newPaid).code,
                        updatedAt = now
                    )
                } else l
            }
            cur.copy(loans = updatedLoans, loanRepayments = cur.loanRepayments + repay)
        }
        return id
    }

    override suspend fun deleteLoanRepayment(repayment: LoanRepayment, linkedLoan: LoanRecord?) {
        val now = Timestamp.now()
        updateSnapshot { cur ->
            val updatedLoans = if (linkedLoan != null && linkedLoan.id.isNotBlank()) {
                cur.loans.map { l ->
                    if (l.id == linkedLoan.id) {
                        val newPaid = (l.paidAmount - repayment.amount).coerceIn(0.0, l.amount)
                        l.copy(
                            paidAmount = newPaid,
                            status = LoanStatus.compute(l.amount, newPaid).code,
                            updatedAt = now
                        )
                    } else l
                }
            } else cur.loans
            cur.copy(
                loans = updatedLoans,
                loanRepayments = cur.loanRepayments.filterNot { it.id == repayment.id }
            )
        }
    }

    override suspend fun updateLoan(loan: LoanRecord) {
        val clampedPaid = loan.paidAmount.coerceIn(0.0, loan.amount)
        val status = LoanStatus.compute(loan.amount, clampedPaid).code
        val updated = loan.copy(
            accountId = loan.accountId.ifBlank { DEFAULT_ADMIN_ACCOUNT_ID },
            personName = loan.personName.trim().take(100),
            personPhone = loan.personPhone.trim().take(40),
            paidAmount = clampedPaid,
            note = loan.note.take(300),
            status = status,
            updatedAt = Timestamp.now()
        )
        updateSnapshot { cur ->
            cur.copy(loans = cur.loans.map { if (it.id == loan.id) updated else it })
        }
    }

    override suspend fun deleteLoan(loanId: String) {
        updateSnapshot { cur ->
            cur.copy(loans = cur.loans.filterNot { it.id == loanId })
        }
    }

    override fun exportToJsonString(
        incomes: List<IncomeEntry>,
        expenses: List<ExpenseEntry>,
        goals: List<SavingsGoal>,
        savingsEntries: List<SavingsEntry>,
        loans: List<LoanRecord>,
        sarToBdtRate: Double,
        loanRepayments: List<LoanRepayment>
    ): String = buildBackupJsonString(incomes, expenses, goals, savingsEntries, loans, sarToBdtRate, loanRepayments)

    override suspend fun restoreFromJsonString(json: String, fallbackAccountId: String): Int {
        val root = JSONObject(json)
        val now = Timestamp.now()
        var count = 0
        val newIncomes = mutableListOf<IncomeEntry>()
        val newExpenses = mutableListOf<ExpenseEntry>()
        val newGoals = mutableListOf<SavingsGoal>()
        val newEntries = mutableListOf<SavingsEntry>()
        val newLoans = mutableListOf<LoanRecord>()
        val newRepayments = mutableListOf<LoanRepayment>()

        val incomes = root.optJSONArray("incomes") ?: JSONArray()
        for (i in 0 until incomes.length()) {
            val obj = incomes.getJSONObject(i)
            val amount = obj.optDouble("amount", 0.0)
            if (amount <= 0.0) continue
            newIncomes += IncomeEntry(
                id = newId(),
                userId = LOCAL_UID,
                accountId = obj.optString("accountId", fallbackAccountId).ifBlank { fallbackAccountId },
                amount = amount,
                currency = obj.optString("currency", CurrencyCode.BDT.code),
                source = obj.optString("source", "OTHER"),
                dateMillis = obj.optLong("dateMillis", System.currentTimeMillis()),
                description = obj.optString("description", "").take(500),
                createdAt = now,
                updatedAt = now
            )
            count++
        }

        val expenses = root.optJSONArray("expenses") ?: JSONArray()
        for (i in 0 until expenses.length()) {
            val obj = expenses.getJSONObject(i)
            val amount = obj.optDouble("amount", 0.0)
            if (amount <= 0.0) continue
            newExpenses += ExpenseEntry(
                id = newId(),
                userId = LOCAL_UID,
                accountId = obj.optString("accountId", fallbackAccountId).ifBlank { fallbackAccountId },
                amount = amount,
                currency = obj.optString("currency", CurrencyCode.BDT.code),
                category = obj.optString("category", "OTHER"),
                dateMillis = obj.optLong("dateMillis", System.currentTimeMillis()),
                description = obj.optString("description", "").take(500),
                createdAt = now,
                updatedAt = now
            )
            count++
        }

        val goals = root.optJSONArray("savingsGoals") ?: JSONArray()
        for (i in 0 until goals.length()) {
            val obj = goals.getJSONObject(i)
            val title = obj.optString("title", "").take(100)
            val target = obj.optDouble("targetAmount", 0.0)
            if (title.isBlank() || target <= 0.0) continue
            newGoals += SavingsGoal(
                id = newId(),
                userId = LOCAL_UID,
                accountId = obj.optString("accountId", fallbackAccountId).ifBlank { fallbackAccountId },
                title = title,
                targetAmount = target,
                savedAmount = obj.optDouble("savedAmount", 0.0).coerceAtLeast(0.0),
                currency = obj.optString("currency", CurrencyCode.BDT.code),
                frequency = obj.optString("frequency", "MONTHLY"),
                targetDateMillis = obj.optLong("targetDateMillis", 0L),
                createdAt = now,
                updatedAt = now
            )
            count++
        }

        val entries = root.optJSONArray("savingsEntries") ?: JSONArray()
        for (i in 0 until entries.length()) {
            val obj = entries.getJSONObject(i)
            val amount = obj.optDouble("amount", 0.0)
            if (amount <= 0.0) continue
            newEntries += SavingsEntry(
                id = newId(),
                userId = LOCAL_UID,
                accountId = obj.optString("accountId", fallbackAccountId).ifBlank { fallbackAccountId },
                goalId = obj.optString("goalId", "").take(128),
                goalTitle = obj.optString("goalTitle", "").take(100),
                amount = amount,
                currency = obj.optString("currency", CurrencyCode.BDT.code),
                frequency = obj.optString("frequency", "DAILY"),
                dateMillis = obj.optLong("dateMillis", System.currentTimeMillis()),
                note = obj.optString("note", "").take(300),
                createdAt = now,
                updatedAt = now
            )
            count++
        }

        val loans = root.optJSONArray("loans") ?: JSONArray()
        for (i in 0 until loans.length()) {
            val obj = loans.getJSONObject(i)
            val personName = obj.optString("personName", "").take(100)
            val amount = obj.optDouble("amount", 0.0)
            if (personName.isBlank() || amount <= 0.0) continue
            val paid = obj.optDouble("paidAmount", 0.0).coerceIn(0.0, amount)
            newLoans += LoanRecord(
                id = newId(),
                userId = LOCAL_UID,
                accountId = obj.optString("accountId", fallbackAccountId).ifBlank { fallbackAccountId },
                type = obj.optString("type", "LENT"),
                personName = personName,
                personPhone = obj.optString("personPhone", "").trim().take(40),
                amount = amount,
                paidAmount = paid,
                currency = obj.optString("currency", CurrencyCode.BDT.code),
                dateMillis = obj.optLong("dateMillis", System.currentTimeMillis()),
                dueDateMillis = obj.optLong("dueDateMillis", 0L),
                note = obj.optString("note", "").take(300),
                status = LoanStatus.compute(amount, paid).code,
                createdAt = now,
                updatedAt = now
            )
            count++
        }

        val repayments = root.optJSONArray("loanRepayments") ?: JSONArray()
        for (i in 0 until repayments.length()) {
            val obj = repayments.getJSONObject(i)
            val personName = obj.optString("personName", "").take(100)
            val amount = obj.optDouble("amount", 0.0)
            if (personName.isBlank() || amount <= 0.0) continue
            newRepayments += LoanRepayment(
                id = newId(),
                userId = LOCAL_UID,
                accountId = obj.optString("accountId", fallbackAccountId).ifBlank { fallbackAccountId },
                loanId = obj.optString("loanId", "").take(128),
                loanType = obj.optString("loanType", "LENT"),
                personName = personName,
                amount = amount,
                currency = obj.optString("currency", CurrencyCode.BDT.code),
                dateMillis = obj.optLong("dateMillis", System.currentTimeMillis()),
                note = obj.optString("note", "").take(300),
                createdAt = now,
                updatedAt = now
            )
            count++
        }

        if (count > 0) {
            updateSnapshot { cur ->
                cur.copy(
                    incomes = cur.incomes + newIncomes,
                    expenses = cur.expenses + newExpenses,
                    savingsGoals = cur.savingsGoals + newGoals,
                    savingsEntries = cur.savingsEntries + newEntries,
                    loans = cur.loans + newLoans,
                    loanRepayments = cur.loanRepayments + newRepayments
                )
            }
        }
        return count
    }

    suspend fun syncPendingToFirestore(repo: HisabRepository) {
        val snap = mutex.withLock { state.value }
        val hasFinancialRecords = snap.incomes.isNotEmpty() ||
            snap.expenses.isNotEmpty() ||
            snap.savingsGoals.isNotEmpty() ||
            snap.savingsEntries.isNotEmpty() ||
            snap.loans.isNotEmpty() ||
            snap.loanRepayments.isNotEmpty()
        val hasCustomUsers = snap.managedUsers.any { !it.loginId.equals(DEFAULT_ADMIN_LOGIN_ID, ignoreCase = true) }
        val editedDefaultAdmin = snap.managedUsers.firstOrNull {
            it.loginId.equals(DEFAULT_ADMIN_LOGIN_ID, ignoreCase = true) &&
                (it.passwordHash != HisabRepository.defaultAdminPasswordHash() || it.fullName != "Aminul Islam")
        }

        if (!hasFinancialRecords && !hasCustomUsers && editedDefaultAdmin == null && snap.settings == null) {
            return
        }

        repo.importLocalSnapshot(
            settings = snap.settings,
            managedUsers = snap.managedUsers,
            incomes = snap.incomes,
            expenses = snap.expenses,
            goals = snap.savingsGoals,
            entries = snap.savingsEntries,
            loans = snap.loans,
            repayments = snap.loanRepayments
        )

        mutex.withLock {
            withContext(Dispatchers.IO) {
                if (storageFile.exists()) storageFile.delete()
            }
            state.value = LocalSnapshot()
        }
    }

    private suspend fun updateSnapshot(transform: (LocalSnapshot) -> LocalSnapshot) {
        mutex.withLock {
            val next = transform(state.value)
            state.value = next
            withContext(Dispatchers.IO) {
                writeToDisk(next)
            }
        }
    }

    private fun readFromDisk(): LocalSnapshot {
        if (!storageFile.exists()) return LocalSnapshot()
        return try {
            val root = JSONObject(storageFile.readText(Charsets.UTF_8))
            val now = Timestamp.now()
            val settingsObj = root.optJSONObject("settings")
            val settings = settingsObj?.let { s ->
                UserSettings(
                    id = LOCAL_UID,
                    userId = LOCAL_UID,
                    displayName = s.optString("displayName", ""),
                    sarToBdtRate = s.optDouble("sarToBdtRate", 32.5),
                    preferredCurrency = s.optString("preferredCurrency", CurrencyCode.BDT.code),
                    language = s.optString("language", AppLanguage.BN.code),
                    createdAt = now,
                    updatedAt = now
                )
            }

            val users = buildList {
                val arr = root.optJSONArray("managedUsers") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        ManagedUser(
                            id = o.optString("id", newId()),
                            userId = LOCAL_UID,
                            loginId = o.optString("loginId", ""),
                            fullName = o.optString("fullName", ""),
                            passwordHash = o.optString("passwordHash", ""),
                            role = o.optString("role", UserRole.USER.code),
                            isActive = o.optBoolean("isActive", true),
                            createdAt = now,
                            updatedAt = now
                        )
                    )
                }
            }

            val incomes = buildList {
                val arr = root.optJSONArray("incomes") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        IncomeEntry(
                            id = o.optString("id", newId()),
                            userId = LOCAL_UID,
                            accountId = o.optString("accountId", DEFAULT_ADMIN_ACCOUNT_ID),
                            amount = o.optDouble("amount", 0.0),
                            currency = o.optString("currency", CurrencyCode.BDT.code),
                            source = o.optString("source", "OTHER"),
                            dateMillis = o.optLong("dateMillis", 0L),
                            description = o.optString("description", ""),
                            createdAt = now,
                            updatedAt = now
                        )
                    )
                }
            }

            val expenses = buildList {
                val arr = root.optJSONArray("expenses") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        ExpenseEntry(
                            id = o.optString("id", newId()),
                            userId = LOCAL_UID,
                            accountId = o.optString("accountId", DEFAULT_ADMIN_ACCOUNT_ID),
                            amount = o.optDouble("amount", 0.0),
                            currency = o.optString("currency", CurrencyCode.BDT.code),
                            category = o.optString("category", "OTHER"),
                            dateMillis = o.optLong("dateMillis", 0L),
                            description = o.optString("description", ""),
                            createdAt = now,
                            updatedAt = now
                        )
                    )
                }
            }

            val goals = buildList {
                val arr = root.optJSONArray("savingsGoals") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        SavingsGoal(
                            id = o.optString("id", newId()),
                            userId = LOCAL_UID,
                            accountId = o.optString("accountId", DEFAULT_ADMIN_ACCOUNT_ID),
                            title = o.optString("title", ""),
                            targetAmount = o.optDouble("targetAmount", 0.0),
                            savedAmount = o.optDouble("savedAmount", 0.0),
                            currency = o.optString("currency", CurrencyCode.BDT.code),
                            frequency = o.optString("frequency", "MONTHLY"),
                            targetDateMillis = o.optLong("targetDateMillis", 0L),
                            createdAt = now,
                            updatedAt = now
                        )
                    )
                }
            }

            val entries = buildList {
                val arr = root.optJSONArray("savingsEntries") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        SavingsEntry(
                            id = o.optString("id", newId()),
                            userId = LOCAL_UID,
                            accountId = o.optString("accountId", DEFAULT_ADMIN_ACCOUNT_ID),
                            goalId = o.optString("goalId", ""),
                            goalTitle = o.optString("goalTitle", ""),
                            amount = o.optDouble("amount", 0.0),
                            currency = o.optString("currency", CurrencyCode.BDT.code),
                            frequency = o.optString("frequency", "DAILY"),
                            dateMillis = o.optLong("dateMillis", 0L),
                            note = o.optString("note", ""),
                            createdAt = now,
                            updatedAt = now
                        )
                    )
                }
            }

            val loans = buildList {
                val arr = root.optJSONArray("loans") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        LoanRecord(
                            id = o.optString("id", newId()),
                            userId = LOCAL_UID,
                            accountId = o.optString("accountId", DEFAULT_ADMIN_ACCOUNT_ID),
                            type = o.optString("type", "LENT"),
                            personName = o.optString("personName", ""),
                            personPhone = o.optString("personPhone", ""),
                            amount = o.optDouble("amount", 0.0),
                            paidAmount = o.optDouble("paidAmount", 0.0),
                            currency = o.optString("currency", CurrencyCode.BDT.code),
                            dateMillis = o.optLong("dateMillis", 0L),
                            dueDateMillis = o.optLong("dueDateMillis", 0L),
                            note = o.optString("note", ""),
                            status = o.optString("status", LoanStatus.PENDING.code),
                            createdAt = now,
                            updatedAt = now
                        )
                    )
                }
            }

            val repayments = buildList {
                val arr = root.optJSONArray("loanRepayments") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        LoanRepayment(
                            id = o.optString("id", newId()),
                            userId = LOCAL_UID,
                            accountId = o.optString("accountId", DEFAULT_ADMIN_ACCOUNT_ID),
                            loanId = o.optString("loanId", ""),
                            loanType = o.optString("loanType", "LENT"),
                            personName = o.optString("personName", ""),
                            amount = o.optDouble("amount", 0.0),
                            currency = o.optString("currency", CurrencyCode.BDT.code),
                            dateMillis = o.optLong("dateMillis", 0L),
                            note = o.optString("note", ""),
                            createdAt = now,
                            updatedAt = now
                        )
                    )
                }
            }

            LocalSnapshot(
                settings = settings,
                managedUsers = users,
                incomes = incomes,
                expenses = expenses,
                savingsGoals = goals,
                savingsEntries = entries,
                loans = loans,
                loanRepayments = repayments
            )
        } catch (_: Exception) {
            LocalSnapshot()
        }
    }

    private fun writeToDisk(snap: LocalSnapshot) {
        val root = JSONObject()
        snap.settings?.let { s ->
            root.put("settings", JSONObject().apply {
                put("displayName", s.displayName)
                put("sarToBdtRate", s.sarToBdtRate)
                put("preferredCurrency", s.preferredCurrency)
                put("language", s.language)
            })
        }
        root.put("managedUsers", JSONArray().apply {
            snap.managedUsers.forEach { u ->
                put(JSONObject().apply {
                    put("id", u.id)
                    put("loginId", u.loginId)
                    put("fullName", u.fullName)
                    put("passwordHash", u.passwordHash)
                    put("role", u.role)
                    put("isActive", u.isActive)
                })
            }
        })
        root.put("incomes", JSONArray().apply {
            snap.incomes.forEach { i ->
                put(JSONObject().apply {
                    put("id", i.id)
                    put("accountId", i.accountId)
                    put("amount", i.amount)
                    put("currency", i.currency)
                    put("source", i.source)
                    put("dateMillis", i.dateMillis)
                    put("description", i.description)
                })
            }
        })
        root.put("expenses", JSONArray().apply {
            snap.expenses.forEach { e ->
                put(JSONObject().apply {
                    put("id", e.id)
                    put("accountId", e.accountId)
                    put("amount", e.amount)
                    put("currency", e.currency)
                    put("category", e.category)
                    put("dateMillis", e.dateMillis)
                    put("description", e.description)
                })
            }
        })
        root.put("savingsGoals", JSONArray().apply {
            snap.savingsGoals.forEach { g ->
                put(JSONObject().apply {
                    put("id", g.id)
                    put("accountId", g.accountId)
                    put("title", g.title)
                    put("targetAmount", g.targetAmount)
                    put("savedAmount", g.savedAmount)
                    put("currency", g.currency)
                    put("frequency", g.frequency)
                    put("targetDateMillis", g.targetDateMillis)
                })
            }
        })
        root.put("savingsEntries", JSONArray().apply {
            snap.savingsEntries.forEach { e ->
                put(JSONObject().apply {
                    put("id", e.id)
                    put("accountId", e.accountId)
                    put("goalId", e.goalId)
                    put("goalTitle", e.goalTitle)
                    put("amount", e.amount)
                    put("currency", e.currency)
                    put("frequency", e.frequency)
                    put("dateMillis", e.dateMillis)
                    put("note", e.note)
                })
            }
        })
        root.put("loans", JSONArray().apply {
            snap.loans.forEach { l ->
                put(JSONObject().apply {
                    put("id", l.id)
                    put("accountId", l.accountId)
                    put("type", l.type)
                    put("personName", l.personName)
                    put("personPhone", l.personPhone)
                    put("amount", l.amount)
                    put("paidAmount", l.paidAmount)
                    put("currency", l.currency)
                    put("dateMillis", l.dateMillis)
                    put("dueDateMillis", l.dueDateMillis)
                    put("note", l.note)
                    put("status", l.status)
                })
            }
        })
        root.put("loanRepayments", JSONArray().apply {
            snap.loanRepayments.forEach { r ->
                put(JSONObject().apply {
                    put("id", r.id)
                    put("accountId", r.accountId)
                    put("loanId", r.loanId)
                    put("loanType", r.loanType)
                    put("personName", r.personName)
                    put("amount", r.amount)
                    put("currency", r.currency)
                    put("dateMillis", r.dateMillis)
                    put("note", r.note)
                })
            }
        })
        val temp = File(storageFile.parentFile, "$FILE_NAME.tmp")
        temp.writeText(root.toString(), Charsets.UTF_8)
        if (!temp.renameTo(storageFile)) {
            storageFile.writeText(root.toString(), Charsets.UTF_8)
            temp.delete()
        }
    }

    private fun newId(): String = UUID.randomUUID().toString().replace("-", "")

    companion object {
        private const val FILE_NAME = "hisab_local_store.json"
        private const val LOCAL_UID = "local_device"
    }
}
