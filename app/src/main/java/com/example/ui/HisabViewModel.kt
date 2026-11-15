package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.FinanceDataSource
import com.example.data.HisabRepository
import com.example.data.LocalAppPreferences
import com.example.data.LocalHisabStore
import com.example.data.PreferencesManager
import com.example.model.AppLanguage
import com.example.model.CurrencyCode
import com.example.model.DEFAULT_ADMIN_ACCOUNT_ID
import com.example.model.DEFAULT_ADMIN_LOGIN_ID
import com.example.model.ExpenseCategory
import com.example.model.ExpenseEntry
import com.example.model.IncomeEntry
import com.example.model.LoanRecord
import com.example.model.LoanRepayment
import com.example.model.LoanStatus
import com.example.model.LoanType
import com.example.model.ManagedUser
import com.example.model.SavingsEntry
import com.example.model.SavingsGoal
import com.example.model.ThemeMode
import com.example.model.UserRole
import com.example.model.UserSettings
import com.example.ui.strings.AppStrings
import com.example.ui.strings.convertCurrency
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class MonthlyChartBar(
    val label: String,
    val income: Double,
    val expense: Double
)

data class CategoryStat(
    val category: ExpenseCategory,
    val amountInPreferred: Double,
    val amountBdt: Double,
    val amountSar: Double,
    val percentage: Float
)

data class UserFinancialSummary(
    val accountKey: String,
    val entryCount: Int = 0,
    val totalIncomeBdt: Double = 0.0,
    val totalIncomeSar: Double = 0.0,
    val totalExpenseBdt: Double = 0.0,
    val totalExpenseSar: Double = 0.0,
    val totalSavedBdt: Double = 0.0,
    val totalSavedSar: Double = 0.0,
    val incomes: List<IncomeEntry> = emptyList(),
    val expenses: List<ExpenseEntry> = emptyList(),
    val savingsGoals: List<SavingsGoal> = emptyList(),
    val loans: List<LoanRecord> = emptyList()
) {
    val netBalanceBdt: Double get() = totalIncomeBdt - totalExpenseBdt
    val netBalanceSar: Double get() = totalIncomeSar - totalExpenseSar

    fun combinedIncome(preferredCurrency: CurrencyCode, sarToBdtRate: Double): Double =
        convertCurrency(totalIncomeBdt, CurrencyCode.BDT.code, preferredCurrency, sarToBdtRate) +
            convertCurrency(totalIncomeSar, CurrencyCode.SAR.code, preferredCurrency, sarToBdtRate)

    fun combinedExpense(preferredCurrency: CurrencyCode, sarToBdtRate: Double): Double =
        convertCurrency(totalExpenseBdt, CurrencyCode.BDT.code, preferredCurrency, sarToBdtRate) +
            convertCurrency(totalExpenseSar, CurrencyCode.SAR.code, preferredCurrency, sarToBdtRate)

    fun combinedNetBalance(preferredCurrency: CurrencyCode, sarToBdtRate: Double): Double =
        convertCurrency(netBalanceBdt, CurrencyCode.BDT.code, preferredCurrency, sarToBdtRate) +
            convertCurrency(netBalanceSar, CurrencyCode.SAR.code, preferredCurrency, sarToBdtRate)
}

data class HisabUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val incomes: List<IncomeEntry> = emptyList(),
    val expenses: List<ExpenseEntry> = emptyList(),
    val savingsGoals: List<SavingsGoal> = emptyList(),
    val savingsEntries: List<SavingsEntry> = emptyList(),
    val loans: List<LoanRecord> = emptyList(),
    val loanRepayments: List<LoanRepayment> = emptyList(),
    val managedUsers: List<ManagedUser> = emptyList(),
    val transactionCountsByAccount: Map<String, Int> = emptyMap(),
    val userSummariesByAccount: Map<String, UserFinancialSummary> = emptyMap(),
    val activeAccountId: String = DEFAULT_ADMIN_ACCOUNT_ID,
    val activeManagedUser: ManagedUser? = null,
    val userLoginRequired: Boolean = false,
    val isGoogleAdmin: Boolean = false,
    val userSettings: UserSettings? = null,
    val prefs: LocalAppPreferences = LocalAppPreferences(),
    val isAppUnlocked: Boolean = true,
    val snackbarMessage: String? = null
) {
    val language: AppLanguage get() = prefs.language
    val strings: AppStrings get() = AppStrings.forLanguage(language)
    val isAdminSession: Boolean
        get() = activeAccountId.equals(DEFAULT_ADMIN_ACCOUNT_ID, ignoreCase = true) ||
            activeAccountId.equals(DEFAULT_ADMIN_LOGIN_ID, ignoreCase = true) ||
            (activeManagedUser?.role == UserRole.ADMIN.code && activeManagedUser.isActive)
    val activeDisplayName: String
        get() = activeManagedUser?.fullName?.takeIf { it.isNotBlank() }
            ?: if (activeAccountId.equals(DEFAULT_ADMIN_LOGIN_ID, ignoreCase = true)) {
                "Aminul Islam"
            } else {
                strings.adminMasterAccount
            }
    val canDirectEnterAsAdmin: Boolean
        get() = false
    val preferredCurrency: CurrencyCode get() = prefs.preferredCurrency
    val sarToBdtRate: Double get() = userSettings?.sarToBdtRate ?: prefs.sarToBdtRate

    val totalIncomeBdt: Double get() = incomes.filter { it.currency == CurrencyCode.BDT.code }.sumOf { it.amount }
    val totalIncomeSar: Double get() = incomes.filter { it.currency == CurrencyCode.SAR.code }.sumOf { it.amount }

    val totalExpenseBdt: Double get() = expenses.filter { it.currency == CurrencyCode.BDT.code }.sumOf { it.amount }
    val totalExpenseSar: Double get() = expenses.filter { it.currency == CurrencyCode.SAR.code }.sumOf { it.amount }

    val netBalanceBdt: Double get() = totalIncomeBdt - totalExpenseBdt
    val netBalanceSar: Double get() = totalIncomeSar - totalExpenseSar

    val totalSavedBdt: Double get() = savingsGoals.filter { it.currency == CurrencyCode.BDT.code }.sumOf { it.savedAmount }
    val totalSavedSar: Double get() = savingsGoals.filter { it.currency == CurrencyCode.SAR.code }.sumOf { it.savedAmount }

    private fun netLoanCashImpact(currencyCode: String): Double {
        var impact = 0.0
        loans.filter { it.currency == currencyCode }.forEach { loan ->
            val remaining = loan.remainingAmount
            if (loan.type == LoanType.BORROWED.code) {
                impact += remaining
            } else {
                impact -= remaining
            }
        }
        return impact
    }

    val cashInHandBdt: Double get() = netBalanceBdt - totalSavedBdt + netLoanCashImpact(CurrencyCode.BDT.code)
    val cashInHandSar: Double get() = netBalanceSar - totalSavedSar + netLoanCashImpact(CurrencyCode.SAR.code)

    val combinedBalanceInPreferred: Double
        get() = convertCurrency(netBalanceBdt, CurrencyCode.BDT.code, preferredCurrency, sarToBdtRate) +
            convertCurrency(netBalanceSar, CurrencyCode.SAR.code, preferredCurrency, sarToBdtRate)

    val combinedCashInHandInPreferred: Double
        get() = convertCurrency(cashInHandBdt, CurrencyCode.BDT.code, preferredCurrency, sarToBdtRate) +
            convertCurrency(cashInHandSar, CurrencyCode.SAR.code, preferredCurrency, sarToBdtRate)

    val combinedIncomeInPreferred: Double
        get() = convertCurrency(totalIncomeBdt, CurrencyCode.BDT.code, preferredCurrency, sarToBdtRate) +
            convertCurrency(totalIncomeSar, CurrencyCode.SAR.code, preferredCurrency, sarToBdtRate)

    val combinedExpenseInPreferred: Double
        get() = convertCurrency(totalExpenseBdt, CurrencyCode.BDT.code, preferredCurrency, sarToBdtRate) +
            convertCurrency(totalExpenseSar, CurrencyCode.SAR.code, preferredCurrency, sarToBdtRate)

    val thisMonthIncomeInPreferred: Double
        get() {
            val (start, end) = currentMonthBounds()
            return incomes.filter { it.dateMillis in start..end }.sumOf {
                convertCurrency(it.amount, it.currency, preferredCurrency, sarToBdtRate)
            }
        }

    val thisMonthExpenseInPreferred: Double
        get() {
            val (start, end) = currentMonthBounds()
            return expenses.filter { it.dateMillis in start..end }.sumOf {
                convertCurrency(it.amount, it.currency, preferredCurrency, sarToBdtRate)
            }
        }

    val thisMonthIncomeBdt: Double
        get() {
            val (start, end) = currentMonthBounds()
            return incomes.filter { it.currency == CurrencyCode.BDT.code && it.dateMillis in start..end }.sumOf { it.amount }
        }

    val thisMonthIncomeSar: Double
        get() {
            val (start, end) = currentMonthBounds()
            return incomes.filter { it.currency == CurrencyCode.SAR.code && it.dateMillis in start..end }.sumOf { it.amount }
        }

    val thisMonthExpenseBdt: Double
        get() {
            val (start, end) = currentMonthBounds()
            return expenses.filter { it.currency == CurrencyCode.BDT.code && it.dateMillis in start..end }.sumOf { it.amount }
        }

    val thisMonthExpenseSar: Double
        get() {
            val (start, end) = currentMonthBounds()
            return expenses.filter { it.currency == CurrencyCode.SAR.code && it.dateMillis in start..end }.sumOf { it.amount }
        }

    fun monthlyBars(monthsCount: Int = 6): List<MonthlyChartBar> {
        val locale = if (language == AppLanguage.BN) Locale("bn", "BD") else Locale.ENGLISH
        val monthFormat = SimpleDateFormat("MMM", locale)
        val cal = Calendar.getInstance()
        val result = mutableListOf<MonthlyChartBar>()
        for (i in (monthsCount - 1) downTo 0) {
            val c = (cal.clone() as Calendar).apply {
                add(Calendar.MONTH, -i)
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val start = c.timeInMillis
            c.set(Calendar.DAY_OF_MONTH, c.getActualMaximum(Calendar.DAY_OF_MONTH))
            c.set(Calendar.HOUR_OF_DAY, 23)
            c.set(Calendar.MINUTE, 59)
            c.set(Calendar.SECOND, 59)
            c.set(Calendar.MILLISECOND, 999)
            val end = c.timeInMillis

            val inc = incomes.filter { it.dateMillis in start..end }.sumOf {
                convertCurrency(it.amount, it.currency, preferredCurrency, sarToBdtRate)
            }
            val exp = expenses.filter { it.dateMillis in start..end }.sumOf {
                convertCurrency(it.amount, it.currency, preferredCurrency, sarToBdtRate)
            }
            result.add(MonthlyChartBar(monthFormat.format(c.time), inc, exp))
        }
        return result
    }

    fun categoryStatsFor(filteredExpenses: List<ExpenseEntry> = expenses): List<CategoryStat> {
        val total = filteredExpenses.sumOf {
            convertCurrency(it.amount, it.currency, preferredCurrency, sarToBdtRate)
        }
        if (total <= 0.0) return emptyList()
        return ExpenseCategory.entries.mapNotNull { cat ->
            val catExpenses = filteredExpenses.filter { it.category == cat.code }
            if (catExpenses.isEmpty()) return@mapNotNull null
            val bdt = catExpenses.filter { it.currency == CurrencyCode.BDT.code }.sumOf { it.amount }
            val sar = catExpenses.filter { it.currency == CurrencyCode.SAR.code }.sumOf { it.amount }
            val inPref = catExpenses.sumOf { convertCurrency(it.amount, it.currency, preferredCurrency, sarToBdtRate) }
            CategoryStat(
                category = cat,
                amountInPreferred = inPref,
                amountBdt = bdt,
                amountSar = sar,
                percentage = (inPref / total).toFloat().coerceIn(0f, 1f)
            )
        }.sortedByDescending { it.amountInPreferred }
    }

    private fun currentMonthBounds(): Pair<Long, Long> {
        val c = Calendar.getInstance()
        c.set(Calendar.DAY_OF_MONTH, 1)
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        val start = c.timeInMillis
        c.set(Calendar.DAY_OF_MONTH, c.getActualMaximum(Calendar.DAY_OF_MONTH))
        c.set(Calendar.HOUR_OF_DAY, 23)
        c.set(Calendar.MINUTE, 59)
        c.set(Calendar.SECOND, 59)
        c.set(Calendar.MILLISECOND, 999)
        return start to c.timeInMillis
    }
}

class HisabViewModel(
    private val repository: FinanceDataSource,
    private val preferencesManager: PreferencesManager,
    private val currentUserId: String?,
    private val currentUserEmail: String = "",
    private val localStoreToSync: LocalHisabStore? = null
) : ViewModel() {

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    private val _streamError = MutableStateFlow<String?>(null)
    private val _unlocked = MutableStateFlow<Boolean?>(null)

    init {
        viewModelScope.launch {
            try {
                withTimeoutOrNull(6000L) {
                    if (repository is HisabRepository && localStoreToSync != null) {
                        localStoreToSync.syncPendingToFirestore(repository)
                    }
                    repository.ensureDefaultAdminUser()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Default admin seed/sync skipped: ${e.message}")
            }
        }
    }

    private data class FinanceStreams(
        val incomes: List<IncomeEntry>,
        val expenses: List<ExpenseEntry>,
        val goals: List<SavingsGoal>,
        val entries: List<SavingsEntry>,
        val loans: List<LoanRecord>
    )

    private data class CoreStreams(
        val finance: FinanceStreams,
        val loanRepayments: List<LoanRepayment>,
        val managedUsers: List<ManagedUser>
    )

    private val financeStreamsFlow = combine(
        repository.observeIncomes().catchAndReport("incomes", emptyList()),
        repository.observeExpenses().catchAndReport("expenses", emptyList()),
        repository.observeSavingsGoals().catchAndReport("savings_goals", emptyList()),
        repository.observeSavingsEntries().catchAndReport("savings_entries", emptyList()),
        repository.observeLoans().catchAndReport("loans", emptyList())
    ) { incomes, expenses, goals, entries, loans ->
        FinanceStreams(incomes, expenses, goals, entries, loans)
    }

    private val coreStreamsFlow = combine(
        financeStreamsFlow,
        repository.observeLoanRepayments().catchAndReport("loan_repayments", emptyList()),
        repository.observeManagedUsers().catchAndReport("managed_users", emptyList())
    ) { finance, repayments, managedUsers ->
        CoreStreams(finance, repayments, managedUsers)
    }

    val uiState: StateFlow<HisabUiState> = combine(
        coreStreamsFlow,
        repository.observeUserSettings().catchAndReport("user_settings", null),
        preferencesManager.preferencesFlow,
        _unlocked,
        _snackbarMessage,
        _streamError
    ) { args ->
        val core = args[0] as CoreStreams
        val settings = args[1] as UserSettings?
        val prefs = args[2] as LocalAppPreferences
        val unlockedOverride = args[3] as Boolean?
        val snackbar = args[4] as String?
        val streamError = args[5] as String?

        val unlocked = unlockedOverride ?: !prefs.appLockEnabled
        val activeAccountId = prefs.activeAccountId.ifBlank { DEFAULT_ADMIN_LOGIN_ID }
        val activeManagedUser = core.managedUsers.firstOrNull {
            it.loginId.equals(activeAccountId, ignoreCase = true)
        } ?: if (activeAccountId.equals(DEFAULT_ADMIN_ACCOUNT_ID, ignoreCase = true)) {
            core.managedUsers.firstOrNull { it.loginId.equals(DEFAULT_ADMIN_LOGIN_ID, ignoreCase = true) }
        } else {
            null
        }
        val isPrimaryAdminAccount = activeAccountId.equals(DEFAULT_ADMIN_ACCOUNT_ID, ignoreCase = true) ||
            activeAccountId.equals(DEFAULT_ADMIN_LOGIN_ID, ignoreCase = true)
        val sessionInvalid = !isPrimaryAdminAccount &&
            (activeManagedUser == null || !activeManagedUser.isActive)
        val userLoginRequired = prefs.userLoginRequired || sessionInvalid
        val isGoogleAdmin = currentUserEmail.equals(ADMIN_EMAIL, ignoreCase = true)

        val scopedIncomes = core.finance.incomes.filter {
            belongsToActiveAccount(it.accountId, activeAccountId)
        }
        val scopedExpenses = core.finance.expenses.filter {
            belongsToActiveAccount(it.accountId, activeAccountId)
        }
        val scopedGoals = core.finance.goals.filter {
            belongsToActiveAccount(it.accountId, activeAccountId)
        }
        val scopedEntries = core.finance.entries.filter {
            belongsToActiveAccount(it.accountId, activeAccountId)
        }
        val scopedLoans = core.finance.loans.filter {
            belongsToActiveAccount(it.accountId, activeAccountId)
        }
        val scopedRepayments = core.loanRepayments.filter {
            belongsToActiveAccount(it.accountId, activeAccountId)
        }

        val isAdminRole = isPrimaryAdminAccount ||
            (activeManagedUser?.role == UserRole.ADMIN.code && activeManagedUser.isActive)
        val userSummaries = if (isAdminRole) {
            buildUserSummaries(
                incomes = core.finance.incomes,
                expenses = core.finance.expenses,
                goals = core.finance.goals,
                loans = core.finance.loans
            )
        } else {
            emptyMap()
        }
        val counts = userSummaries.mapValues { it.value.entryCount }

        HisabUiState(
            isLoading = false,
            errorMessage = streamError,
            incomes = scopedIncomes,
            expenses = scopedExpenses,
            savingsGoals = scopedGoals,
            savingsEntries = scopedEntries,
            loans = scopedLoans,
            loanRepayments = scopedRepayments,
            managedUsers = core.managedUsers,
            transactionCountsByAccount = counts,
            userSummariesByAccount = userSummaries,
            activeAccountId = activeAccountId,
            activeManagedUser = activeManagedUser,
            userLoginRequired = userLoginRequired,
            isGoogleAdmin = isGoogleAdmin,
            userSettings = settings,
            prefs = prefs,
            isAppUnlocked = unlocked,
            snackbarMessage = snackbar
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = HisabUiState(isLoading = true)
    )

    private fun <T> kotlinx.coroutines.flow.Flow<T>.catchAndReport(
        label: String,
        fallback: T
    ): kotlinx.coroutines.flow.Flow<T> = catch { error ->
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val root = firestoreError ?: error
        Log.w(TAG, "Error observing $label", root)
        _streamError.value = root.localizedMessage ?: "Failed to sync $label"
        emit(fallback)
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    fun showMessage(message: String) {
        _snackbarMessage.value = message
    }

    fun unlockWithPin(pin: String): Boolean {
        val prefs = uiState.value.prefs
        val valid = preferencesManager.verifyPin(pin, prefs.pinHash)
        if (valid) {
            _unlocked.value = true
        }
        return valid
    }

    fun setPinLock(pin: String) {
        viewModelScope.launch {
            preferencesManager.setAppLockPin(pin)
            _unlocked.value = true
        }
    }

    fun disablePinLock() {
        viewModelScope.launch {
            preferencesManager.disableAppLock()
            _unlocked.value = true
        }
    }

    suspend fun loginWithUserIdPassword(loginId: String, password: String): String? {
        val s = uiState.value
        val normalizedId = loginId.trim().lowercase()
        if (normalizedId.isBlank() || password.isEmpty()) {
            return s.strings.invalidUserCredentials
        }
        val matched = s.managedUsers.firstOrNull { it.loginId.equals(normalizedId, ignoreCase = true) }
        val expectedHash = HisabRepository.hashUserPassword(normalizedId, password)
        if (matched != null) {
            if (matched.passwordHash != expectedHash) {
                return s.strings.invalidUserCredentials
            }
            if (!matched.isActive) {
                return s.strings.inactiveUserError
            }
            preferencesManager.setActiveAccountSession(matched.loginId, requireLogin = false)
            return null
        }
        if (normalizedId == DEFAULT_ADMIN_LOGIN_ID &&
            expectedHash == HisabRepository.defaultAdminPasswordHash()
        ) {
            preferencesManager.setActiveAccountSession(DEFAULT_ADMIN_LOGIN_ID, requireLogin = false)
            return null
        }
        return s.strings.invalidUserCredentials
    }

    fun loginAsManagedUserDirectly(user: ManagedUser) {
        if (!user.isActive) {
            _snackbarMessage.value = uiState.value.strings.inactiveUserError
            return
        }
        viewModelScope.launch {
            preferencesManager.setActiveAccountSession(user.loginId, requireLogin = false)
        }
    }

    fun switchToUserLoginScreen() {
        viewModelScope.launch {
            preferencesManager.setUserLoginRequired(true)
        }
    }

    fun enterAsAdmin() {
        viewModelScope.launch {
            preferencesManager.setActiveAccountSession(DEFAULT_ADMIN_LOGIN_ID, requireLogin = false)
        }
    }

    suspend fun createManagedUser(
        loginId: String,
        fullName: String,
        rawPassword: String,
        role: UserRole,
        isActive: Boolean
    ): String? {
        val s = uiState.value
        val normalizedId = loginId.trim().lowercase()
        val trimmedName = fullName.trim()
        if (!LOGIN_ID_REGEX.matches(normalizedId)) {
            return s.strings.invalidUserIdFormatError
        }
        if (trimmedName.isEmpty()) {
            return s.strings.requiredFieldError
        }
        if (rawPassword.length < 4) {
            return s.strings.passwordTooShortError
        }
        if (s.managedUsers.any { it.loginId.equals(normalizedId, ignoreCase = true) }) {
            return s.strings.duplicateUserIdError
        }

        return try {
            withTimeoutOrNull(2500L) {
                repository.createManagedUser(
                    loginId = normalizedId,
                    fullName = trimmedName,
                    rawPassword = rawPassword,
                    role = role,
                    isActive = isActive
                )
            }
            _snackbarMessage.value = s.strings.userCreatedSuccess
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.localizedMessage ?: "Failed to create user"
        }
    }

    suspend fun updateManagedUser(
        user: ManagedUser,
        newFullName: String,
        newRawPassword: String?,
        newRole: UserRole,
        newActive: Boolean
    ): String? {
        val s = uiState.value
        val trimmedName = newFullName.trim()
        if (trimmedName.isEmpty()) {
            return s.strings.requiredFieldError
        }
        if (!newRawPassword.isNullOrEmpty() && newRawPassword.length < 4) {
            return s.strings.passwordTooShortError
        }

        return try {
            withTimeoutOrNull(2500L) {
                repository.updateManagedUser(
                    user = user.copy(
                        fullName = trimmedName,
                        role = newRole.code,
                        isActive = newActive
                    ),
                    newRawPassword = newRawPassword?.takeIf { it.isNotBlank() }
                )
            }
            _snackbarMessage.value = s.strings.userUpdatedSuccess
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.localizedMessage ?: "Failed to update user"
        }
    }

    fun toggleManagedUserActive(user: ManagedUser) {
        if (user.loginId.equals(DEFAULT_ADMIN_LOGIN_ID, ignoreCase = true)) return
        viewModelScope.launch {
            runOfflineFriendly {
                repository.updateManagedUser(
                    user = user.copy(isActive = !user.isActive),
                    newRawPassword = null
                )
            }
        }
    }

    fun deleteManagedUser(managedUserId: String) {
        viewModelScope.launch {
            val s = uiState.value
            val deleted = s.managedUsers.firstOrNull { it.id == managedUserId }
            if (deleted?.loginId?.equals(DEFAULT_ADMIN_LOGIN_ID, ignoreCase = true) == true) return@launch
            runOfflineFriendly {
                repository.deleteManagedUser(managedUserId)
                _snackbarMessage.value = s.strings.userDeletedSuccess
            }
            if (deleted != null && s.activeAccountId.equals(deleted.loginId, ignoreCase = true)) {
                preferencesManager.setActiveAccountSession(DEFAULT_ADMIN_LOGIN_ID, requireLogin = true)
            }
        }
    }

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch {
            preferencesManager.setLanguage(language)
            val s = uiState.value
            runOfflineFriendly {
                repository.saveUserSettings(
                    displayName = s.userSettings?.displayName ?: "",
                    sarToBdtRate = s.sarToBdtRate,
                    preferredCurrency = s.preferredCurrency,
                    language = language,
                    existingCreatedAt = s.userSettings?.createdAt
                )
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            preferencesManager.setThemeMode(mode)
        }
    }

    fun setPreferredCurrency(currency: CurrencyCode) {
        viewModelScope.launch {
            preferencesManager.setPreferredCurrency(currency)
            val s = uiState.value
            runOfflineFriendly {
                repository.saveUserSettings(
                    displayName = s.userSettings?.displayName ?: "",
                    sarToBdtRate = s.sarToBdtRate,
                    preferredCurrency = currency,
                    language = s.language,
                    existingCreatedAt = s.userSettings?.createdAt
                )
            }
        }
    }

    fun updateExchangeRate(rate: Double) {
        viewModelScope.launch {
            preferencesManager.setSarToBdtRate(rate)
            val s = uiState.value
            runOfflineFriendly {
                repository.saveUserSettings(
                    displayName = s.userSettings?.displayName ?: "",
                    sarToBdtRate = rate,
                    preferredCurrency = s.preferredCurrency,
                    language = s.language,
                    existingCreatedAt = s.userSettings?.createdAt
                )
            }
        }
    }

    fun addIncome(
        amount: Double,
        currency: String,
        source: String,
        dateMillis: Long,
        description: String
    ) {
        val accountId = uiState.value.activeAccountId
        viewModelScope.launch {
            runOfflineFriendly {
                repository.addIncome(amount, currency, source, dateMillis, description, accountId)
            }
        }
    }

    fun updateIncome(income: IncomeEntry) {
        viewModelScope.launch {
            runOfflineFriendly { repository.updateIncome(income) }
        }
    }

    fun deleteIncome(incomeId: String) {
        viewModelScope.launch {
            runOfflineFriendly { repository.deleteIncome(incomeId) }
        }
    }

    fun addExpense(
        amount: Double,
        currency: String,
        category: String,
        dateMillis: Long,
        description: String
    ) {
        val accountId = uiState.value.activeAccountId
        viewModelScope.launch {
            runOfflineFriendly {
                repository.addExpense(amount, currency, category, dateMillis, description, accountId)
            }
        }
    }

    fun updateExpense(expense: ExpenseEntry) {
        viewModelScope.launch {
            runOfflineFriendly { repository.updateExpense(expense) }
        }
    }

    fun deleteExpense(expenseId: String) {
        viewModelScope.launch {
            runOfflineFriendly { repository.deleteExpense(expenseId) }
        }
    }

    fun addSavingsGoal(
        title: String,
        targetAmount: Double,
        initialSavedAmount: Double,
        currency: String,
        frequency: String,
        targetDateMillis: Long
    ) {
        val accountId = uiState.value.activeAccountId
        viewModelScope.launch {
            runOfflineFriendly {
                repository.addSavingsGoal(
                    title = title,
                    targetAmount = targetAmount,
                    savedAmount = initialSavedAmount,
                    currency = currency,
                    frequency = frequency,
                    targetDateMillis = targetDateMillis,
                    accountId = accountId
                )
            }
        }
    }

    fun updateSavingsGoal(goal: SavingsGoal) {
        viewModelScope.launch {
            runOfflineFriendly { repository.updateSavingsGoal(goal) }
        }
    }

    fun deleteSavingsGoal(goalId: String) {
        viewModelScope.launch {
            runOfflineFriendly { repository.deleteSavingsGoal(goalId) }
        }
    }

    fun addSavingsDeposit(
        goal: SavingsGoal?,
        amount: Double,
        currency: String,
        frequency: String,
        dateMillis: Long,
        note: String
    ) {
        val accountId = uiState.value.activeAccountId
        viewModelScope.launch {
            runOfflineFriendly {
                repository.addSavingsEntry(goal, amount, currency, frequency, dateMillis, note, accountId)
            }
        }
    }

    fun deleteSavingsEntry(entry: SavingsEntry) {
        viewModelScope.launch {
            val linkedGoal = uiState.value.savingsGoals.firstOrNull { it.id == entry.goalId }
            runOfflineFriendly { repository.deleteSavingsEntry(entry, linkedGoal) }
        }
    }

    fun addLoan(
        type: String,
        personName: String,
        personPhone: String,
        amount: Double,
        paidAmount: Double,
        currency: String,
        dateMillis: Long,
        dueDateMillis: Long,
        note: String
    ) {
        val accountId = uiState.value.activeAccountId
        viewModelScope.launch {
            runOfflineFriendly {
                repository.addLoan(type, personName, personPhone, amount, paidAmount, currency, dateMillis, dueDateMillis, note, accountId)
            }
        }
    }

    fun updateLoan(loan: LoanRecord) {
        viewModelScope.launch {
            runOfflineFriendly { repository.updateLoan(loan) }
        }
    }

    fun recordLoanRepayment(
        loan: LoanRecord,
        additionalPayment: Double,
        dateMillis: Long = System.currentTimeMillis(),
        note: String = ""
    ) {
        val accountId = uiState.value.activeAccountId
        viewModelScope.launch {
            runOfflineFriendly {
                repository.addLoanRepayment(
                    loan = loan,
                    amount = additionalPayment,
                    dateMillis = dateMillis,
                    note = note,
                    accountId = accountId
                )
            }
        }
    }

    fun deleteLoanRepayment(repayment: LoanRepayment) {
        val linkedLoan = uiState.value.loans.firstOrNull { it.id == repayment.loanId }
        viewModelScope.launch {
            runOfflineFriendly {
                repository.deleteLoanRepayment(repayment, linkedLoan)
            }
        }
    }

    fun deleteLoan(loanId: String) {
        viewModelScope.launch {
            runOfflineFriendly { repository.deleteLoan(loanId) }
        }
    }

    fun buildBackupJson(): String {
        val s = uiState.value
        return repository.exportToJsonString(
            incomes = s.incomes,
            expenses = s.expenses,
            goals = s.savingsGoals,
            savingsEntries = s.savingsEntries,
            loans = s.loans,
            sarToBdtRate = s.sarToBdtRate,
            loanRepayments = s.loanRepayments
        )
    }

    suspend fun restoreFromBackupJson(json: String): Result<Int> = runCatching {
        val accountId = uiState.value.activeAccountId
        withTimeoutOrNull(5000L) {
            repository.restoreFromJsonString(json, fallbackAccountId = accountId)
        } ?: 0
    }

    private suspend fun runOfflineFriendly(block: suspend () -> Unit) {
        try {
            // If offline, Firestore queues the mutation to disk immediately and updates snapshot listeners,
            // while Task.await() waits for server ack. A short timeout keeps UI snappy offline.
            withTimeoutOrNull(2000L) {
                block()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Operation failed for $currentUserId", e)
            _snackbarMessage.value = e.localizedMessage ?: "Operation failed"
        }
    }

    companion object {
        private const val TAG = "HisabVM"
        private const val ADMIN_EMAIL = "aminulislam.hrdc7@gmail.com"
        private val LOGIN_ID_REGEX = Regex("^[a-zA-Z0-9_.\\-]{3,30}$")

        fun normalizeAccountKey(rawAccountId: String): String {
            val normalized = rawAccountId.ifBlank { DEFAULT_ADMIN_ACCOUNT_ID }.trim().lowercase()
            return if (normalized == DEFAULT_ADMIN_ACCOUNT_ID) DEFAULT_ADMIN_LOGIN_ID else normalized
        }

        fun belongsToActiveAccount(recordAccountId: String, activeAccountId: String): Boolean {
            return normalizeAccountKey(recordAccountId) == normalizeAccountKey(activeAccountId)
        }

        fun buildUserSummaries(
            incomes: List<IncomeEntry>,
            expenses: List<ExpenseEntry>,
            goals: List<SavingsGoal>,
            loans: List<LoanRecord>
        ): Map<String, UserFinancialSummary> {
            val incomesByKey = incomes.groupBy { normalizeAccountKey(it.accountId) }
            val expensesByKey = expenses.groupBy { normalizeAccountKey(it.accountId) }
            val goalsByKey = goals.groupBy { normalizeAccountKey(it.accountId) }
            val loansByKey = loans.groupBy { normalizeAccountKey(it.accountId) }

            val allKeys = (incomesByKey.keys + expensesByKey.keys + goalsByKey.keys + loansByKey.keys)
            return allKeys.associateWith { key ->
                val userIncomes = incomesByKey[key].orEmpty()
                val userExpenses = expensesByKey[key].orEmpty()
                val userGoals = goalsByKey[key].orEmpty()
                val userLoans = loansByKey[key].orEmpty()
                UserFinancialSummary(
                    accountKey = key,
                    entryCount = userIncomes.size + userExpenses.size + userGoals.size + userLoans.size,
                    totalIncomeBdt = userIncomes.filter { it.currency == CurrencyCode.BDT.code }.sumOf { it.amount },
                    totalIncomeSar = userIncomes.filter { it.currency == CurrencyCode.SAR.code }.sumOf { it.amount },
                    totalExpenseBdt = userExpenses.filter { it.currency == CurrencyCode.BDT.code }.sumOf { it.amount },
                    totalExpenseSar = userExpenses.filter { it.currency == CurrencyCode.SAR.code }.sumOf { it.amount },
                    totalSavedBdt = userGoals.filter { it.currency == CurrencyCode.BDT.code }.sumOf { it.savedAmount },
                    totalSavedSar = userGoals.filter { it.currency == CurrencyCode.SAR.code }.sumOf { it.savedAmount },
                    incomes = userIncomes,
                    expenses = userExpenses,
                    savingsGoals = userGoals,
                    loans = userLoans
                )
            }
        }
    }
}
