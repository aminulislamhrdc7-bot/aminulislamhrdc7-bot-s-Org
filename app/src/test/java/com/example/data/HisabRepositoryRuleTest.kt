package com.example.data

import com.example.base.FirestoreEmulatorTestBase
import com.example.model.AppLanguage
import com.example.model.CurrencyCode
import com.example.model.ExpenseCategory
import com.example.model.IncomeSource
import com.example.model.LoanType
import com.example.model.SavingsFrequency
import com.example.model.UserRole
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class HisabRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun addAndObserveIncome_authenticatedOwner_succeeds() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repo = HisabRepository(firestore, auth)

        val incomeId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.addIncome(
                amount = 45000.0,
                currency = CurrencyCode.BDT.code,
                source = IncomeSource.SALARY.code,
                dateMillis = 1763000000000L,
                description = "November Salary",
                accountId = "rahim_01"
            )
        }
        assertTrue(incomeId.isNotBlank())

        val incomes = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeIncomes().first { list -> list.any { it.id == incomeId } }
        }
        val created = incomes.first { it.id == incomeId }
        assertEquals(45000.0, created.amount, 0.01)
        assertEquals(CurrencyCode.BDT.code, created.currency)
        assertEquals("rahim_01", created.accountId)

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.updateIncome(created.copy(amount = 48000.0))
        }
        val updated = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeIncomes().first { list ->
                list.any { it.id == incomeId && it.amount == 48000.0 }
            }
        }
        assertTrue(updated.any { it.id == incomeId && it.amount == 48000.0 })

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.deleteIncome(incomeId)
        }
    }

    @Test
    fun addExpenseSavingsAndLoan_authenticatedOwner_succeeds() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repo = HisabRepository(firestore, auth)

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.saveUserSettings(
                displayName = "Alice",
                sarToBdtRate = 32.5,
                preferredCurrency = CurrencyCode.BDT,
                language = AppLanguage.BN
            )
        }

        val expenseId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.addExpense(
                amount = 1200.0,
                currency = CurrencyCode.BDT.code,
                category = ExpenseCategory.GROCERIES.code,
                dateMillis = 1763000000000L,
                description = "Weekly bazar",
                accountId = "rahim_01"
            )
        }
        assertTrue(expenseId.isNotBlank())

        val goalId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.addSavingsGoal(
                title = "Hajj Fund",
                targetAmount = 500000.0,
                savedAmount = 50000.0,
                currency = CurrencyCode.BDT.code,
                frequency = SavingsFrequency.MONTHLY.code,
                targetDateMillis = 1790000000000L,
                accountId = "rahim_01"
            )
        }
        assertTrue(goalId.isNotBlank())

        val loanId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.addLoan(
                type = LoanType.LENT.code,
                personName = "Karim",
                personPhone = "01712345678",
                amount = 10000.0,
                paidAmount = 2500.0,
                currency = CurrencyCode.BDT.code,
                dateMillis = 1763000000000L,
                dueDateMillis = 1766000000000L,
                note = "Short term loan",
                accountId = "rahim_01"
            )
        }
        assertTrue(loanId.isNotBlank())

        val loanCreated = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeLoans().first { list -> list.any { it.id == loanId } }
        }.first { it.id == loanId }
        assertEquals("01712345678", loanCreated.personPhone)

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.updateLoan(loanCreated.copy(personPhone = "+966501234567"))
        }
        val loanUpdated = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeLoans().first { list ->
                list.any { it.id == loanId && it.personPhone == "+966501234567" }
            }
        }.first { it.id == loanId }
        assertEquals("+966501234567", loanUpdated.personPhone)

        val repayId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.addLoanRepayment(
                loan = loanCreated,
                amount = 1500.0,
                dateMillis = 1763100000000L,
                note = "bKash installment",
                accountId = "rahim_01"
            )
        }
        assertTrue(repayId.isNotBlank())

        val repayments = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeLoanRepayments().first { list -> list.any { it.id == repayId } }
        }
        val createdRepayment = repayments.first { it.id == repayId }
        assertEquals(1500.0, createdRepayment.amount, 0.01)
        assertEquals(1763100000000L, createdRepayment.dateMillis)

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.deleteLoanRepayment(createdRepayment, loanCreated.copy(paidAmount = 4000.0))
        }
    }

    @Test
    fun managedUsers_crudByOwnerAndDeniedForCrossUser_succeeds() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repo = HisabRepository(firestore, auth)

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.ensureDefaultAdminUser()
            repo.ensureDefaultAdminUser() // Idempotent second call
        }

        val managedId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.createManagedUser(
                loginId = "karim.user1",
                fullName = "Karim Ahmed",
                rawPassword = "Secret123",
                role = UserRole.USER,
                isActive = true
            )
        }
        assertTrue(managedId.isNotBlank())

        val users = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeManagedUsers().first { list ->
                list.any { it.id == managedId } && list.any { it.loginId == "aminulislam" }
            }
        }
        val seededAdmin = users.first { it.loginId == "aminulislam" }
        assertEquals(UserRole.ADMIN.code, seededAdmin.role)
        assertEquals(HisabRepository.defaultAdminPasswordHash(), seededAdmin.passwordHash)
        assertTrue(seededAdmin.isActive)
        val created = users.first { it.id == managedId }
        assertEquals("karim.user1", created.loginId)
        assertEquals("Karim Ahmed", created.fullName)
        assertEquals(64, created.passwordHash.length)
        assertTrue(created.isActive)

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.updateManagedUser(
                user = created.copy(fullName = "Karim Uddin", isActive = false),
                newRawPassword = "NewPassword99"
            )
        }

        val updated = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeManagedUsers().first { list ->
                list.any { it.id == managedId && !it.isActive && it.fullName == "Karim Uddin" }
            }
        }.first { it.id == managedId }
        assertFalse(updated.isActive)
        assertEquals(HisabRepository.hashUserPassword("karim.user1", "NewPassword99"), updated.passwordHash)

        // Cross-user read on managed_users must be denied
        signInTestUser(BOB_EMAIL)
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("users").document(aliceUid)
                    .collection("managed_users").document(managedId)
                    .get()
                    .await()
            }
            fail("Expected PERMISSION_DENIED for cross-user managed_users read")
        } catch (e: Exception) {
            val fsEx = e.unwrapFirestoreException()
            assertNotNull("Expected nested FirebaseFirestoreException, got $e", fsEx)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx?.code)
        }

        // Owner can delete
        signInTestUser(ALICE_EMAIL)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.deleteManagedUser(managedId)
        }
    }

    @Test
    fun crossUserAccess_bobCannotReadAliceIncomes() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = HisabRepository(firestore, auth)
        val incomeId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.addIncome(
                amount = 1500.0,
                currency = CurrencyCode.SAR.code,
                source = IncomeSource.BUSINESS.code,
                dateMillis = 1763000000000L,
                description = "Freelance"
            )
        }

        signInTestUser(BOB_EMAIL)
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("users").document(aliceUid)
                    .collection("incomes").document(incomeId)
                    .get()
                    .await()
            }
            fail("Expected PERMISSION_DENIED for cross-user read")
        } catch (e: Exception) {
            val fsEx = e.unwrapFirestoreException()
            assertNotNull("Expected nested FirebaseFirestoreException, got $e", fsEx)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx?.code)
        }
    }

    @Test
    fun unauthenticatedRead_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        auth.signOut()

        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("users").document(aliceUid)
                    .collection("expenses")
                    .get()
                    .await()
            }
            fail("Expected PERMISSION_DENIED when unauthenticated")
        } catch (e: Exception) {
            val fsEx = e.unwrapFirestoreException()
            assertNotNull("Expected nested FirebaseFirestoreException, got $e", fsEx)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx?.code)
        }
    }

    private companion object {
        const val ALICE_EMAIL = "alice_hisab@test.com"
        const val BOB_EMAIL = "bob_hisab@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
