package com.example

import com.example.data.HisabRepository
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
import com.example.model.SavingsGoal
import com.example.model.UserRole
import com.example.ui.HisabUiState
import com.example.ui.HisabViewModel
import com.example.ui.strings.convertCurrency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun dualCurrencyConversion_computesAccurately() {
        val bdt = convertCurrency(100.0, CurrencyCode.SAR.code, CurrencyCode.BDT, 32.5)
        assertEquals(3250.0, bdt, 0.001)

        val sar = convertCurrency(3250.0, CurrencyCode.BDT.code, CurrencyCode.SAR, 32.5)
        assertEquals(100.0, sar, 0.001)
    }

    @Test
    fun hisabUiState_balancesAndCashInHandUpdateAutomatically() {
        val now = System.currentTimeMillis()
        val state = HisabUiState(
            isLoading = false,
            incomes = listOf(
                IncomeEntry(id = "i1", amount = 50000.0, currency = "BDT", dateMillis = now),
                IncomeEntry(id = "i2", amount = 1000.0, currency = "SAR", dateMillis = now)
            ),
            expenses = listOf(
                ExpenseEntry(id = "e1", amount = 12000.0, currency = "BDT", category = ExpenseCategory.RENT.code, dateMillis = now),
                ExpenseEntry(id = "e2", amount = 200.0, currency = "SAR", category = ExpenseCategory.FOOD.code, dateMillis = now)
            ),
            savingsGoals = listOf(
                SavingsGoal(id = "g1", title = "Emergency", targetAmount = 20000.0, savedAmount = 5000.0, currency = "BDT")
            ),
            loans = listOf(
                LoanRecord(id = "l1", type = LoanType.LENT.code, personName = "Karim", amount = 4000.0, paidAmount = 1000.0, currency = "BDT", status = LoanStatus.PARTIAL.code)
            )
        )

        assertEquals(38000.0, state.netBalanceBdt, 0.01)
        assertEquals(800.0, state.netBalanceSar, 0.01)
        // Cash in hand BDT = 38000 (net) - 5000 (saved) - 3000 (lent remaining) = 30000
        assertEquals(30000.0, state.cashInHandBdt, 0.01)
        assertEquals(25, state.savingsGoals.first().progressPercent)
        assertEquals(LoanStatus.PARTIAL, LoanStatus.compute(4000.0, 1000.0))
        assertEquals(LoanStatus.SETTLED, LoanStatus.compute(4000.0, 4000.0))
    }

    @Test
    fun managedUserPasswordHash_andAdminSession_workAsExpected() {
        val hash1 = HisabRepository.hashUserPassword("Rahim_01", "pass1234")
        val hash2 = HisabRepository.hashUserPassword("rahim_01 ", "pass1234")
        val hashWrong = HisabRepository.hashUserPassword("rahim_01", "wrongpass")
        assertEquals(64, hash1.length)
        assertEquals(hash1, hash2)
        assertNotEquals(hash1, hashWrong)

        val adminUserState = HisabUiState(
            activeAccountId = "boss_1",
            activeManagedUser = ManagedUser(
                id = "m1",
                loginId = "boss_1",
                fullName = "Boss",
                role = UserRole.ADMIN.code,
                isActive = true
            )
        )
        assertTrue(adminUserState.isAdminSession)

        val regularUserState = HisabUiState(
            activeAccountId = "staff_1",
            activeManagedUser = ManagedUser(
                id = "m2",
                loginId = "staff_1",
                fullName = "Staff",
                role = UserRole.USER.code,
                isActive = true
            ),
            loanRepayments = listOf(
                LoanRepayment(
                    id = "r1",
                    loanId = "l1",
                    loanType = LoanType.BORROWED.code,
                    personName = "Hasan",
                    amount = 2000.0,
                    currency = "BDT",
                    dateMillis = 1763100000000L,
                    note = "Cash return"
                )
            )
        )
        assertFalse(regularUserState.isAdminSession)
        assertEquals(1, regularUserState.loanRepayments.size)
        assertEquals(2000.0, regularUserState.loanRepayments.first().amount, 0.01)
    }

    @Test
    fun defaultAdminCredentials_andWorkspaceScoping_workAsExpected() {
        assertEquals("aminulislam", DEFAULT_ADMIN_LOGIN_ID)
        assertEquals(
            HisabRepository.defaultAdminPasswordHash(),
            HisabRepository.hashUserPassword("aminulislam", "aminul111312")
        )
        assertNotEquals(
            HisabRepository.defaultAdminPasswordHash(),
            HisabRepository.hashUserPassword("aminulislam", "wrong123")
        )

        val defaultAdminState = HisabUiState(activeAccountId = DEFAULT_ADMIN_LOGIN_ID)
        assertTrue(defaultAdminState.isAdminSession)
        assertFalse(defaultAdminState.canDirectEnterAsAdmin)

        assertTrue(HisabViewModel.belongsToActiveAccount("", "aminulislam"))
        assertTrue(HisabViewModel.belongsToActiveAccount(DEFAULT_ADMIN_ACCOUNT_ID, "aminulislam"))
        assertTrue(HisabViewModel.belongsToActiveAccount("aminulislam", "aminulislam"))
        assertFalse(HisabViewModel.belongsToActiveAccount("staff_1", "aminulislam"))
        assertTrue(HisabViewModel.belongsToActiveAccount("staff_1", "staff_1"))
    }

    @Test
    fun userSummaries_computePerUserBalancesAndEntriesSeparately() {
        val summaries = HisabViewModel.buildUserSummaries(
            incomes = listOf(
                IncomeEntry(id = "i1", accountId = DEFAULT_ADMIN_ACCOUNT_ID, amount = 20000.0, currency = "BDT"),
                IncomeEntry(id = "i2", accountId = "rahim01", amount = 1000.0, currency = "SAR"),
                IncomeEntry(id = "i3", accountId = "rahim01", amount = 5000.0, currency = "BDT")
            ),
            expenses = listOf(
                ExpenseEntry(id = "e1", accountId = "aminulislam", amount = 6000.0, currency = "BDT"),
                ExpenseEntry(id = "e2", accountId = "rahim01", amount = 200.0, currency = "SAR")
            ),
            goals = listOf(
                SavingsGoal(id = "g1", accountId = "karim02", title = "Hajj", targetAmount = 50000.0, savedAmount = 10000.0, currency = "BDT")
            ),
            loans = listOf(
                LoanRecord(id = "l1", accountId = "rahim01", type = LoanType.LENT.code, personName = "Babul", amount = 3000.0, paidAmount = 500.0, currency = "BDT")
            )
        )

        val adminSummary = summaries.getValue(DEFAULT_ADMIN_LOGIN_ID)
        assertEquals(2, adminSummary.entryCount)
        assertEquals(20000.0, adminSummary.totalIncomeBdt, 0.01)
        assertEquals(6000.0, adminSummary.totalExpenseBdt, 0.01)
        assertEquals(14000.0, adminSummary.netBalanceBdt, 0.01)

        val rahimSummary = summaries.getValue("rahim01")
        assertEquals(4, rahimSummary.entryCount)
        assertEquals(5000.0, rahimSummary.totalIncomeBdt, 0.01)
        assertEquals(1000.0, rahimSummary.totalIncomeSar, 0.01)
        assertEquals(200.0, rahimSummary.totalExpenseSar, 0.01)
        assertEquals(800.0, rahimSummary.netBalanceSar, 0.01)
        // Combined in BDT at 32.5 rate: (5000 - 0) + (800 * 32.5) = 31000.0
        assertEquals(31000.0, rahimSummary.combinedNetBalance(CurrencyCode.BDT, 32.5), 0.01)

        val karimSummary = summaries.getValue("karim02")
        assertEquals(1, karimSummary.entryCount)
        assertEquals(10000.0, karimSummary.totalSavedBdt, 0.01)
    }
}
