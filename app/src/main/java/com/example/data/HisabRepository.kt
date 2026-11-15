package com.example.data

import android.content.Context
import com.example.R
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
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

class HisabRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) : FinanceDataSource {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        Firebase.auth
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private fun userDoc(uid: String) = db.collection("users").document(uid)

    private fun <T> observeUserSubcollection(
        collectionName: String,
        clazz: Class<T>,
        sorter: (List<T>) -> List<T>
    ): Flow<List<T>> = flow {
        val uid = requireUserId()
        val colRef = userDoc(uid).collection(collectionName)
        emitAll(
            colRef.snapshots(MetadataChanges.INCLUDE)
                .map { snapshot ->
                    val items = snapshot.toObjects(
                        clazz,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                    sorter(items)
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val ex = firestoreError ?: (error as? Exception)
                    if (ex != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(ex, OperationType.LIST, colRef.path, auth)
                    }
                    throw error
                }
        )
    }

    override fun observeUserSettings(): Flow<UserSettings?> = flow {
        val uid = requireUserId()
        val docRef = userDoc(uid)
        emitAll(
            docRef.snapshots(MetadataChanges.INCLUDE)
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            UserSettings::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else {
                        null
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val ex = firestoreError ?: (error as? Exception)
                    if (ex != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(ex, OperationType.GET, docRef.path, auth)
                    }
                    throw error
                }
        )
    }

    override fun observeManagedUsers(): Flow<List<ManagedUser>> =
        observeUserSubcollection("managed_users", ManagedUser::class.java) { list ->
            list.sortedBy { it.loginId.lowercase() }
        }

    override fun observeIncomes(): Flow<List<IncomeEntry>> =
        observeUserSubcollection("incomes", IncomeEntry::class.java) { list ->
            list.sortedByDescending { it.dateMillis }
        }

    override fun observeExpenses(): Flow<List<ExpenseEntry>> =
        observeUserSubcollection("expenses", ExpenseEntry::class.java) { list ->
            list.sortedByDescending { it.dateMillis }
        }

    override fun observeSavingsGoals(): Flow<List<SavingsGoal>> =
        observeUserSubcollection("savings_goals", SavingsGoal::class.java) { list ->
            list.sortedByDescending { it.createdAt?.seconds ?: 0L }
        }

    override fun observeSavingsEntries(): Flow<List<SavingsEntry>> =
        observeUserSubcollection("savings_entries", SavingsEntry::class.java) { list ->
            list.sortedByDescending { it.dateMillis }
        }

    override fun observeLoans(): Flow<List<LoanRecord>> =
        observeUserSubcollection("loans", LoanRecord::class.java) { list ->
            list.sortedByDescending { it.dateMillis }
        }

    override fun observeLoanRepayments(): Flow<List<LoanRepayment>> =
        observeUserSubcollection("loan_repayments", LoanRepayment::class.java) { list ->
            list.sortedByDescending { it.dateMillis }
        }

    override suspend fun createManagedUser(
        loginId: String,
        fullName: String,
        rawPassword: String,
        role: UserRole,
        isActive: Boolean
    ): String {
        val uid = requireUserId()
        val normalizedId = loginId.trim().lowercase()
        val docRef = userDoc(uid).collection("managed_users").document()
        val payload = mapOf(
            "userId" to uid,
            "loginId" to normalizedId,
            "fullName" to fullName.trim().take(100),
            "passwordHash" to hashUserPassword(normalizedId, rawPassword),
            "role" to role.code,
            "isActive" to isActive,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            return docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
            throw e
        }
    }

    override suspend fun ensureDefaultAdminUser(
        loginId: String,
        fullName: String,
        passwordHash: String
    ) {
        val uid = requireUserId()
        val normalizedId = loginId.trim().lowercase()
        val docRef = userDoc(uid).collection("managed_users").document("admin_$normalizedId")
        try {
            val existing = docRef.get().await()
            if (existing.exists()) return
            val payload = mapOf(
                "userId" to uid,
                "loginId" to normalizedId,
                "fullName" to fullName.trim().take(100),
                "passwordHash" to passwordHash,
                "role" to UserRole.ADMIN.code,
                "isActive" to true,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path, auth)
            throw e
        }
    }

    override suspend fun updateManagedUser(
        user: ManagedUser,
        newRawPassword: String?
    ) {
        val uid = requireUserId()
        require(user.id.isNotBlank()) { "Managed user ID must not be blank" }
        val normalizedId = user.loginId.trim().lowercase()
        val passwordHash = if (!newRawPassword.isNullOrBlank()) {
            hashUserPassword(normalizedId, newRawPassword)
        } else {
            user.passwordHash
        }
        val docRef = userDoc(uid).collection("managed_users").document(user.id)
        val payload = mapOf(
            "loginId" to normalizedId,
            "fullName" to user.fullName.trim().take(100),
            "passwordHash" to passwordHash,
            "role" to user.role,
            "isActive" to user.isActive,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.update(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
            throw e
        }
    }

    override suspend fun deleteManagedUser(managedUserId: String) {
        val uid = requireUserId()
        val docRef = userDoc(uid).collection("managed_users").document(managedUserId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path, auth)
            throw e
        }
    }

    override suspend fun saveUserSettings(
        displayName: String,
        sarToBdtRate: Double,
        preferredCurrency: CurrencyCode,
        language: AppLanguage,
        existingCreatedAt: com.google.firebase.Timestamp?
    ) {
        val uid = requireUserId()
        val docRef = userDoc(uid)
        try {
            val snapshot = docRef.get().await()
            if (snapshot.exists()) {
                val updates = mapOf(
                    "displayName" to displayName.take(120),
                    "sarToBdtRate" to sarToBdtRate.coerceIn(0.01, 10000.0),
                    "preferredCurrency" to preferredCurrency.code,
                    "language" to language.code,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updates).await()
            } else {
                val payload = mapOf(
                    "userId" to uid,
                    "displayName" to displayName.take(120),
                    "sarToBdtRate" to sarToBdtRate.coerceIn(0.01, 10000.0),
                    "preferredCurrency" to preferredCurrency.code,
                    "language" to language.code,
                    "createdAt" to (existingCreatedAt ?: FieldValue.serverTimestamp()),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path, auth)
            throw e
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
        val uid = requireUserId()
        val docRef = userDoc(uid).collection("incomes").document()
        val payload = mapOf(
            "userId" to uid,
            "accountId" to accountId.ifBlank { DEFAULT_ADMIN_ACCOUNT_ID },
            "amount" to amount,
            "currency" to currency,
            "source" to source,
            "dateMillis" to dateMillis,
            "description" to description.take(500),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            return docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
            throw e
        }
    }

    override suspend fun updateIncome(income: IncomeEntry) {
        val uid = requireUserId()
        require(income.id.isNotBlank()) { "Income ID must not be blank" }
        val docRef = userDoc(uid).collection("incomes").document(income.id)
        val payload = mapOf(
            "accountId" to income.accountId.ifBlank { DEFAULT_ADMIN_ACCOUNT_ID },
            "amount" to income.amount,
            "currency" to income.currency,
            "source" to income.source,
            "dateMillis" to income.dateMillis,
            "description" to income.description.take(500),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.update(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
            throw e
        }
    }

    override suspend fun deleteIncome(incomeId: String) {
        val uid = requireUserId()
        val docRef = userDoc(uid).collection("incomes").document(incomeId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path, auth)
            throw e
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
        val uid = requireUserId()
        val docRef = userDoc(uid).collection("expenses").document()
        val payload = mapOf(
            "userId" to uid,
            "accountId" to accountId.ifBlank { DEFAULT_ADMIN_ACCOUNT_ID },
            "amount" to amount,
            "currency" to currency,
            "category" to category,
            "dateMillis" to dateMillis,
            "description" to description.take(500),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            return docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
            throw e
        }
    }

    override suspend fun updateExpense(expense: ExpenseEntry) {
        val uid = requireUserId()
        require(expense.id.isNotBlank()) { "Expense ID must not be blank" }
        val docRef = userDoc(uid).collection("expenses").document(expense.id)
        val payload = mapOf(
            "accountId" to expense.accountId.ifBlank { DEFAULT_ADMIN_ACCOUNT_ID },
            "amount" to expense.amount,
            "currency" to expense.currency,
            "category" to expense.category,
            "dateMillis" to expense.dateMillis,
            "description" to expense.description.take(500),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.update(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
            throw e
        }
    }

    override suspend fun deleteExpense(expenseId: String) {
        val uid = requireUserId()
        val docRef = userDoc(uid).collection("expenses").document(expenseId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path, auth)
            throw e
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
        val uid = requireUserId()
        val docRef = userDoc(uid).collection("savings_goals").document()
        val payload = mapOf(
            "userId" to uid,
            "accountId" to accountId.ifBlank { DEFAULT_ADMIN_ACCOUNT_ID },
            "title" to title.take(100),
            "targetAmount" to targetAmount,
            "savedAmount" to savedAmount,
            "currency" to currency,
            "frequency" to frequency,
            "targetDateMillis" to targetDateMillis,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            return docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
            throw e
        }
    }

    override suspend fun updateSavingsGoal(goal: SavingsGoal) {
        val uid = requireUserId()
        require(goal.id.isNotBlank()) { "Goal ID must not be blank" }
        val docRef = userDoc(uid).collection("savings_goals").document(goal.id)
        val payload = mapOf(
            "accountId" to goal.accountId.ifBlank { DEFAULT_ADMIN_ACCOUNT_ID },
            "title" to goal.title.take(100),
            "targetAmount" to goal.targetAmount,
            "savedAmount" to goal.savedAmount,
            "currency" to goal.currency,
            "frequency" to goal.frequency,
            "targetDateMillis" to goal.targetDateMillis,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.update(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
            throw e
        }
    }

    override suspend fun deleteSavingsGoal(goalId: String) {
        val uid = requireUserId()
        val docRef = userDoc(uid).collection("savings_goals").document(goalId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path, auth)
            throw e
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
        val uid = requireUserId()
        val entryRef = userDoc(uid).collection("savings_entries").document()
        val batch = db.batch()
        val entryPayload = mapOf(
            "userId" to uid,
            "accountId" to accountId.ifBlank { DEFAULT_ADMIN_ACCOUNT_ID },
            "goalId" to (goal?.id ?: ""),
            "goalTitle" to (goal?.title ?: "").take(100),
            "amount" to amount,
            "currency" to currency,
            "frequency" to frequency,
            "dateMillis" to dateMillis,
            "note" to note.take(300),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        batch.set(entryRef, entryPayload)
        if (goal != null && goal.id.isNotBlank()) {
            val goalRef = userDoc(uid).collection("savings_goals").document(goal.id)
            batch.update(
                goalRef,
                mapOf(
                    "savedAmount" to (goal.savedAmount + amount),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }
        try {
            batch.commit().await()
            return entryRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, entryRef.path, auth)
            throw e
        }
    }

    override suspend fun deleteSavingsEntry(entry: SavingsEntry, linkedGoal: SavingsGoal?) {
        val uid = requireUserId()
        val entryRef = userDoc(uid).collection("savings_entries").document(entry.id)
        val batch = db.batch()
        batch.delete(entryRef)
        if (linkedGoal != null && linkedGoal.id.isNotBlank()) {
            val goalRef = userDoc(uid).collection("savings_goals").document(linkedGoal.id)
            batch.update(
                goalRef,
                mapOf(
                    "savedAmount" to (linkedGoal.savedAmount - entry.amount).coerceAtLeast(0.0),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }
        try {
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, entryRef.path, auth)
            throw e
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
        val uid = requireUserId()
        val effectiveAccountId = accountId.ifBlank { DEFAULT_ADMIN_ACCOUNT_ID }
        val docRef = userDoc(uid).collection("loans").document()
        val clampedPaid = paidAmount.coerceIn(0.0, amount)
        val status = LoanStatus.compute(amount, clampedPaid).code
        val batch = db.batch()
        val payload = mapOf(
            "userId" to uid,
            "accountId" to effectiveAccountId,
            "type" to type,
            "personName" to personName.trim().take(100),
            "personPhone" to personPhone.trim().take(40),
            "amount" to amount,
            "paidAmount" to clampedPaid,
            "currency" to currency,
            "dateMillis" to dateMillis,
            "dueDateMillis" to dueDateMillis,
            "note" to note.take(300),
            "status" to status,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        batch.set(docRef, payload)
        if (clampedPaid > 0.0) {
            val repayRef = userDoc(uid).collection("loan_repayments").document()
            batch.set(
                repayRef,
                mapOf(
                    "userId" to uid,
                    "accountId" to effectiveAccountId,
                    "loanId" to docRef.id,
                    "loanType" to type,
                    "personName" to personName.trim().take(100),
                    "amount" to clampedPaid,
                    "currency" to currency,
                    "dateMillis" to dateMillis,
                    "note" to note.take(300),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }
        try {
            batch.commit().await()
            return docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
            throw e
        }
    }

    override suspend fun addLoanRepayment(
        loan: LoanRecord,
        amount: Double,
        dateMillis: Long,
        note: String,
        accountId: String
    ): String {
        val uid = requireUserId()
        val effectiveAccountId = loan.accountId.ifBlank { accountId.ifBlank { DEFAULT_ADMIN_ACCOUNT_ID } }
        val repayRef = userDoc(uid).collection("loan_repayments").document()
        val batch = db.batch()
        batch.set(
            repayRef,
            mapOf(
                "userId" to uid,
                "accountId" to effectiveAccountId,
                "loanId" to loan.id,
                "loanType" to loan.type,
                "personName" to loan.personName.trim().take(100),
                "amount" to amount,
                "currency" to loan.currency,
                "dateMillis" to dateMillis,
                "note" to note.trim().take(300),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        if (loan.id.isNotBlank()) {
            val newPaid = (loan.paidAmount + amount).coerceIn(0.0, loan.amount)
            val newStatus = LoanStatus.compute(loan.amount, newPaid).code
            val loanRef = userDoc(uid).collection("loans").document(loan.id)
            batch.update(
                loanRef,
                mapOf(
                    "paidAmount" to newPaid,
                    "status" to newStatus,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }
        try {
            batch.commit().await()
            return repayRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, repayRef.path, auth)
            throw e
        }
    }

    override suspend fun deleteLoanRepayment(repayment: LoanRepayment, linkedLoan: LoanRecord?) {
        val uid = requireUserId()
        val repayRef = userDoc(uid).collection("loan_repayments").document(repayment.id)
        val batch = db.batch()
        batch.delete(repayRef)
        if (linkedLoan != null && linkedLoan.id.isNotBlank()) {
            val newPaid = (linkedLoan.paidAmount - repayment.amount).coerceIn(0.0, linkedLoan.amount)
            val newStatus = LoanStatus.compute(linkedLoan.amount, newPaid).code
            val loanRef = userDoc(uid).collection("loans").document(linkedLoan.id)
            batch.update(
                loanRef,
                mapOf(
                    "paidAmount" to newPaid,
                    "status" to newStatus,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }
        try {
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, repayRef.path, auth)
            throw e
        }
    }

    override suspend fun updateLoan(loan: LoanRecord) {
        val uid = requireUserId()
        require(loan.id.isNotBlank()) { "Loan ID must not be blank" }
        val docRef = userDoc(uid).collection("loans").document(loan.id)
        val clampedPaid = loan.paidAmount.coerceIn(0.0, loan.amount)
        val status = LoanStatus.compute(loan.amount, clampedPaid).code
        val payload = mapOf(
            "accountId" to loan.accountId.ifBlank { DEFAULT_ADMIN_ACCOUNT_ID },
            "type" to loan.type,
            "personName" to loan.personName.trim().take(100),
            "personPhone" to loan.personPhone.trim().take(40),
            "amount" to loan.amount,
            "paidAmount" to clampedPaid,
            "currency" to loan.currency,
            "dateMillis" to loan.dateMillis,
            "dueDateMillis" to loan.dueDateMillis,
            "note" to loan.note.take(300),
            "status" to status,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.update(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
            throw e
        }
    }

    override suspend fun deleteLoan(loanId: String) {
        val uid = requireUserId()
        val docRef = userDoc(uid).collection("loans").document(loanId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path, auth)
            throw e
        }
    }

    suspend fun importLocalSnapshot(
        settings: UserSettings?,
        managedUsers: List<ManagedUser>,
        incomes: List<IncomeEntry>,
        expenses: List<ExpenseEntry>,
        goals: List<SavingsGoal>,
        entries: List<SavingsEntry>,
        loans: List<LoanRecord>,
        repayments: List<LoanRepayment>
    ) {
        val uid = requireUserId()
        if (settings != null) {
            saveUserSettings(
                displayName = settings.displayName,
                sarToBdtRate = settings.sarToBdtRate,
                preferredCurrency = CurrencyCode.fromCode(settings.preferredCurrency),
                language = AppLanguage.fromCode(settings.language)
            )
        }
        val batch = db.batch()
        var ops = 0

        managedUsers.forEach { u ->
            val docId = u.id.ifBlank { "user_${u.loginId.lowercase()}" }
            val ref = userDoc(uid).collection("managed_users").document(docId)
            batch.set(
                ref,
                mapOf(
                    "userId" to uid,
                    "loginId" to u.loginId.trim().lowercase(),
                    "fullName" to u.fullName.trim().take(100),
                    "passwordHash" to u.passwordHash,
                    "role" to u.role,
                    "isActive" to u.isActive,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            ops++
        }

        incomes.forEach { inc ->
            val ref = userDoc(uid).collection("incomes").document(inc.id)
            batch.set(
                ref,
                mapOf(
                    "userId" to uid,
                    "accountId" to inc.accountId.ifBlank { DEFAULT_ADMIN_ACCOUNT_ID },
                    "amount" to inc.amount,
                    "currency" to inc.currency,
                    "source" to inc.source,
                    "dateMillis" to inc.dateMillis,
                    "description" to inc.description.take(500),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            ops++
        }

        expenses.forEach { exp ->
            val ref = userDoc(uid).collection("expenses").document(exp.id)
            batch.set(
                ref,
                mapOf(
                    "userId" to uid,
                    "accountId" to exp.accountId.ifBlank { DEFAULT_ADMIN_ACCOUNT_ID },
                    "amount" to exp.amount,
                    "currency" to exp.currency,
                    "category" to exp.category,
                    "dateMillis" to exp.dateMillis,
                    "description" to exp.description.take(500),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            ops++
        }

        goals.forEach { g ->
            val ref = userDoc(uid).collection("savings_goals").document(g.id)
            batch.set(
                ref,
                mapOf(
                    "userId" to uid,
                    "accountId" to g.accountId.ifBlank { DEFAULT_ADMIN_ACCOUNT_ID },
                    "title" to g.title.take(100),
                    "targetAmount" to g.targetAmount,
                    "savedAmount" to g.savedAmount,
                    "currency" to g.currency,
                    "frequency" to g.frequency,
                    "targetDateMillis" to g.targetDateMillis,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            ops++
        }

        entries.forEach { e ->
            val ref = userDoc(uid).collection("savings_entries").document(e.id)
            batch.set(
                ref,
                mapOf(
                    "userId" to uid,
                    "accountId" to e.accountId.ifBlank { DEFAULT_ADMIN_ACCOUNT_ID },
                    "goalId" to e.goalId.take(128),
                    "goalTitle" to e.goalTitle.take(100),
                    "amount" to e.amount,
                    "currency" to e.currency,
                    "frequency" to e.frequency,
                    "dateMillis" to e.dateMillis,
                    "note" to e.note.take(300),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            ops++
        }

        loans.forEach { l ->
            val ref = userDoc(uid).collection("loans").document(l.id)
            batch.set(
                ref,
                mapOf(
                    "userId" to uid,
                    "accountId" to l.accountId.ifBlank { DEFAULT_ADMIN_ACCOUNT_ID },
                    "type" to l.type,
                    "personName" to l.personName.trim().take(100),
                    "personPhone" to l.personPhone.trim().take(40),
                    "amount" to l.amount,
                    "paidAmount" to l.paidAmount,
                    "currency" to l.currency,
                    "dateMillis" to l.dateMillis,
                    "dueDateMillis" to l.dueDateMillis,
                    "note" to l.note.take(300),
                    "status" to l.status,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            ops++
        }

        repayments.forEach { r ->
            val ref = userDoc(uid).collection("loan_repayments").document(r.id)
            batch.set(
                ref,
                mapOf(
                    "userId" to uid,
                    "accountId" to r.accountId.ifBlank { DEFAULT_ADMIN_ACCOUNT_ID },
                    "loanId" to r.loanId.take(128),
                    "loanType" to r.loanType,
                    "personName" to r.personName.trim().take(100),
                    "amount" to r.amount,
                    "currency" to r.currency,
                    "dateMillis" to r.dateMillis,
                    "note" to r.note.take(300),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            ops++
        }

        if (ops > 0) {
            try {
                batch.commit().await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.WRITE, userDoc(uid).path, auth)
                throw e
            }
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

    override suspend fun restoreFromJsonString(
        json: String,
        fallbackAccountId: String
    ): Int {
        val uid = requireUserId()
        val root = JSONObject(json)
        var count = 0
        val batch = db.batch()

        val incomes = root.optJSONArray("incomes") ?: JSONArray()
        for (i in 0 until incomes.length()) {
            val obj = incomes.getJSONObject(i)
            val amount = obj.optDouble("amount", 0.0)
            if (amount <= 0.0) continue
            val docRef = userDoc(uid).collection("incomes").document()
            batch.set(docRef, mapOf(
                "userId" to uid,
                "accountId" to obj.optString("accountId", fallbackAccountId).ifBlank { fallbackAccountId },
                "amount" to amount,
                "currency" to obj.optString("currency", CurrencyCode.BDT.code),
                "source" to obj.optString("source", "OTHER"),
                "dateMillis" to obj.optLong("dateMillis", System.currentTimeMillis()),
                "description" to obj.optString("description", "").take(500),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ))
            count++
        }

        val expenses = root.optJSONArray("expenses") ?: JSONArray()
        for (i in 0 until expenses.length()) {
            val obj = expenses.getJSONObject(i)
            val amount = obj.optDouble("amount", 0.0)
            if (amount <= 0.0) continue
            val docRef = userDoc(uid).collection("expenses").document()
            batch.set(docRef, mapOf(
                "userId" to uid,
                "accountId" to obj.optString("accountId", fallbackAccountId).ifBlank { fallbackAccountId },
                "amount" to amount,
                "currency" to obj.optString("currency", CurrencyCode.BDT.code),
                "category" to obj.optString("category", "OTHER"),
                "dateMillis" to obj.optLong("dateMillis", System.currentTimeMillis()),
                "description" to obj.optString("description", "").take(500),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ))
            count++
        }

        val goals = root.optJSONArray("savingsGoals") ?: JSONArray()
        for (i in 0 until goals.length()) {
            val obj = goals.getJSONObject(i)
            val title = obj.optString("title", "").take(100)
            val target = obj.optDouble("targetAmount", 0.0)
            if (title.isBlank() || target <= 0.0) continue
            val docRef = userDoc(uid).collection("savings_goals").document()
            batch.set(docRef, mapOf(
                "userId" to uid,
                "accountId" to obj.optString("accountId", fallbackAccountId).ifBlank { fallbackAccountId },
                "title" to title,
                "targetAmount" to target,
                "savedAmount" to obj.optDouble("savedAmount", 0.0).coerceAtLeast(0.0),
                "currency" to obj.optString("currency", CurrencyCode.BDT.code),
                "frequency" to obj.optString("frequency", "MONTHLY"),
                "targetDateMillis" to obj.optLong("targetDateMillis", 0L),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ))
            count++
        }

        val savingsEntries = root.optJSONArray("savingsEntries") ?: JSONArray()
        for (i in 0 until savingsEntries.length()) {
            val obj = savingsEntries.getJSONObject(i)
            val amount = obj.optDouble("amount", 0.0)
            if (amount <= 0.0) continue
            val docRef = userDoc(uid).collection("savings_entries").document()
            batch.set(docRef, mapOf(
                "userId" to uid,
                "accountId" to obj.optString("accountId", fallbackAccountId).ifBlank { fallbackAccountId },
                "goalId" to obj.optString("goalId", "").take(128),
                "goalTitle" to obj.optString("goalTitle", "").take(100),
                "amount" to amount,
                "currency" to obj.optString("currency", CurrencyCode.BDT.code),
                "frequency" to obj.optString("frequency", "DAILY"),
                "dateMillis" to obj.optLong("dateMillis", System.currentTimeMillis()),
                "note" to obj.optString("note", "").take(300),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ))
            count++
        }

        val loans = root.optJSONArray("loans") ?: JSONArray()
        for (i in 0 until loans.length()) {
            val obj = loans.getJSONObject(i)
            val personName = obj.optString("personName", "").take(100)
            val amount = obj.optDouble("amount", 0.0)
            if (personName.isBlank() || amount <= 0.0) continue
            val paid = obj.optDouble("paidAmount", 0.0).coerceIn(0.0, amount)
            val docRef = userDoc(uid).collection("loans").document()
            batch.set(docRef, mapOf(
                "userId" to uid,
                "accountId" to obj.optString("accountId", fallbackAccountId).ifBlank { fallbackAccountId },
                "type" to obj.optString("type", "LENT"),
                "personName" to personName,
                "personPhone" to obj.optString("personPhone", "").trim().take(40),
                "amount" to amount,
                "paidAmount" to paid,
                "currency" to obj.optString("currency", CurrencyCode.BDT.code),
                "dateMillis" to obj.optLong("dateMillis", System.currentTimeMillis()),
                "dueDateMillis" to obj.optLong("dueDateMillis", 0L),
                "note" to obj.optString("note", "").take(300),
                "status" to LoanStatus.compute(amount, paid).code,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ))
            count++
        }

        val loanRepayments = root.optJSONArray("loanRepayments") ?: JSONArray()
        for (i in 0 until loanRepayments.length()) {
            val obj = loanRepayments.getJSONObject(i)
            val personName = obj.optString("personName", "").take(100)
            val amount = obj.optDouble("amount", 0.0)
            if (personName.isBlank() || amount <= 0.0) continue
            val docRef = userDoc(uid).collection("loan_repayments").document()
            batch.set(docRef, mapOf(
                "userId" to uid,
                "accountId" to obj.optString("accountId", fallbackAccountId).ifBlank { fallbackAccountId },
                "loanId" to obj.optString("loanId", "").take(128),
                "loanType" to obj.optString("loanType", "LENT"),
                "personName" to personName,
                "amount" to amount,
                "currency" to obj.optString("currency", CurrencyCode.BDT.code),
                "dateMillis" to obj.optLong("dateMillis", System.currentTimeMillis()),
                "note" to obj.optString("note", "").take(300),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ))
            count++
        }

        if (count > 0) {
            try {
                batch.commit().await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.WRITE, userDoc(uid).path, auth)
                throw e
            }
        }
        return count
    }

    companion object {
        fun hashUserPassword(loginId: String, rawPassword: String): String {
            val normalizedId = loginId.trim().lowercase()
            val input = "my_hisab_user_pwd_v1:$normalizedId:$rawPassword"
            val bytes = MessageDigest.getInstance("SHA-256")
                .digest(input.toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }
        }

        fun defaultAdminPasswordHash(): String =
            hashUserPassword(DEFAULT_ADMIN_LOGIN_ID, "aminul111312")
    }
}
