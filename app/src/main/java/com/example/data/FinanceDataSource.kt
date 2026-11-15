package com.example.data

import com.example.model.AppLanguage
import com.example.model.CurrencyCode
import com.example.model.DEFAULT_ADMIN_ACCOUNT_ID
import com.example.model.DEFAULT_ADMIN_LOGIN_ID
import com.example.model.ExpenseEntry
import com.example.model.IncomeEntry
import com.example.model.LoanRecord
import com.example.model.LoanRepayment
import com.example.model.ManagedUser
import com.example.model.SavingsEntry
import com.example.model.SavingsGoal
import com.example.model.UserRole
import com.example.model.UserSettings
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

interface FinanceDataSource {
    fun observeUserSettings(): Flow<UserSettings?>
    fun observeManagedUsers(): Flow<List<ManagedUser>>
    fun observeIncomes(): Flow<List<IncomeEntry>>
    fun observeExpenses(): Flow<List<ExpenseEntry>>
    fun observeSavingsGoals(): Flow<List<SavingsGoal>>
    fun observeSavingsEntries(): Flow<List<SavingsEntry>>
    fun observeLoans(): Flow<List<LoanRecord>>
    fun observeLoanRepayments(): Flow<List<LoanRepayment>>

    suspend fun ensureDefaultAdminUser(
        loginId: String = DEFAULT_ADMIN_LOGIN_ID,
        fullName: String = "Aminul Islam",
        passwordHash: String = HisabRepository.defaultAdminPasswordHash()
    )

    suspend fun createManagedUser(
        loginId: String,
        fullName: String,
        rawPassword: String,
        role: UserRole = UserRole.USER,
        isActive: Boolean = true
    ): String

    suspend fun updateManagedUser(
        user: ManagedUser,
        newRawPassword: String? = null
    )

    suspend fun deleteManagedUser(managedUserId: String)

    suspend fun saveUserSettings(
        displayName: String,
        sarToBdtRate: Double,
        preferredCurrency: CurrencyCode,
        language: AppLanguage,
        existingCreatedAt: Timestamp? = null
    )

    suspend fun addIncome(
        amount: Double,
        currency: String,
        source: String,
        dateMillis: Long,
        description: String,
        accountId: String = DEFAULT_ADMIN_ACCOUNT_ID
    ): String

    suspend fun updateIncome(income: IncomeEntry)
    suspend fun deleteIncome(incomeId: String)

    suspend fun addExpense(
        amount: Double,
        currency: String,
        category: String,
        dateMillis: Long,
        description: String,
        accountId: String = DEFAULT_ADMIN_ACCOUNT_ID
    ): String

    suspend fun updateExpense(expense: ExpenseEntry)
    suspend fun deleteExpense(expenseId: String)

    suspend fun addSavingsGoal(
        title: String,
        targetAmount: Double,
        savedAmount: Double,
        currency: String,
        frequency: String,
        targetDateMillis: Long,
        accountId: String = DEFAULT_ADMIN_ACCOUNT_ID
    ): String

    suspend fun updateSavingsGoal(goal: SavingsGoal)
    suspend fun deleteSavingsGoal(goalId: String)

    suspend fun addSavingsEntry(
        goal: SavingsGoal?,
        amount: Double,
        currency: String,
        frequency: String,
        dateMillis: Long,
        note: String,
        accountId: String = DEFAULT_ADMIN_ACCOUNT_ID
    ): String

    suspend fun deleteSavingsEntry(entry: SavingsEntry, linkedGoal: SavingsGoal?)

    suspend fun addLoan(
        type: String,
        personName: String,
        personPhone: String = "",
        amount: Double,
        paidAmount: Double,
        currency: String,
        dateMillis: Long,
        dueDateMillis: Long,
        note: String,
        accountId: String = DEFAULT_ADMIN_ACCOUNT_ID
    ): String

    suspend fun addLoanRepayment(
        loan: LoanRecord,
        amount: Double,
        dateMillis: Long,
        note: String,
        accountId: String = DEFAULT_ADMIN_ACCOUNT_ID
    ): String

    suspend fun deleteLoanRepayment(repayment: LoanRepayment, linkedLoan: LoanRecord?)
    suspend fun updateLoan(loan: LoanRecord)
    suspend fun deleteLoan(loanId: String)

    fun exportToJsonString(
        incomes: List<IncomeEntry>,
        expenses: List<ExpenseEntry>,
        goals: List<SavingsGoal>,
        savingsEntries: List<SavingsEntry>,
        loans: List<LoanRecord>,
        sarToBdtRate: Double,
        loanRepayments: List<LoanRepayment> = emptyList()
    ): String

    suspend fun restoreFromJsonString(
        json: String,
        fallbackAccountId: String = DEFAULT_ADMIN_ACCOUNT_ID
    ): Int
}

internal fun buildBackupJsonString(
    incomes: List<IncomeEntry>,
    expenses: List<ExpenseEntry>,
    goals: List<SavingsGoal>,
    savingsEntries: List<SavingsEntry>,
    loans: List<LoanRecord>,
    sarToBdtRate: Double,
    loanRepayments: List<LoanRepayment> = emptyList()
): String {
    val root = JSONObject()
    root.put("version", 2)
    root.put("exportedAt", System.currentTimeMillis())
    root.put("sarToBdtRate", sarToBdtRate)

    root.put("incomes", JSONArray().apply {
        incomes.forEach { inc ->
            put(JSONObject().apply {
                put("accountId", inc.accountId.ifBlank { DEFAULT_ADMIN_ACCOUNT_ID })
                put("amount", inc.amount)
                put("currency", inc.currency)
                put("source", inc.source)
                put("dateMillis", inc.dateMillis)
                put("description", inc.description)
            })
        }
    })

    root.put("expenses", JSONArray().apply {
        expenses.forEach { exp ->
            put(JSONObject().apply {
                put("accountId", exp.accountId.ifBlank { DEFAULT_ADMIN_ACCOUNT_ID })
                put("amount", exp.amount)
                put("currency", exp.currency)
                put("category", exp.category)
                put("dateMillis", exp.dateMillis)
                put("description", exp.description)
            })
        }
    })

    root.put("savingsGoals", JSONArray().apply {
        goals.forEach { g ->
            put(JSONObject().apply {
                put("accountId", g.accountId.ifBlank { DEFAULT_ADMIN_ACCOUNT_ID })
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
        savingsEntries.forEach { e ->
            put(JSONObject().apply {
                put("accountId", e.accountId.ifBlank { DEFAULT_ADMIN_ACCOUNT_ID })
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
        loans.forEach { l ->
            put(JSONObject().apply {
                put("accountId", l.accountId.ifBlank { DEFAULT_ADMIN_ACCOUNT_ID })
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
        loanRepayments.forEach { r ->
            put(JSONObject().apply {
                put("accountId", r.accountId.ifBlank { DEFAULT_ADMIN_ACCOUNT_ID })
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

    return root.toString(2)
}
