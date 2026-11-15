package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.HisabRepository
import com.example.data.LocalHisabStore
import com.example.model.DEFAULT_ADMIN_LOGIN_ID
import com.example.model.ExpenseCategory
import com.example.model.IncomeSource
import com.example.model.LoanStatus
import com.example.model.LoanType
import com.example.model.UserRole
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("আমার হিসাব", appName)
  }

  @Test
  fun `local hisab store persists admin user, managed users, transactions and repayments without Google Sign-In`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val store = LocalHisabStore(context)

    store.ensureDefaultAdminUser(
      loginId = DEFAULT_ADMIN_LOGIN_ID,
      fullName = "Aminul Islam",
      passwordHash = HisabRepository.defaultAdminPasswordHash()
    )
    store.createManagedUser(
      loginId = "rahim01",
      fullName = "Rahim",
      rawPassword = "secret123",
      role = UserRole.USER
    )

    val incomeId = store.addIncome(
      amount = 15000.0,
      currency = "BDT",
      source = IncomeSource.SALARY.code,
      dateMillis = 1763100000000L,
      description = "Monthly salary",
      accountId = DEFAULT_ADMIN_LOGIN_ID
    )
    val expenseId = store.addExpense(
      amount = 3500.0,
      currency = "BDT",
      category = ExpenseCategory.GROCERIES.code,
      dateMillis = 1763100000000L,
      description = "Weekly market",
      accountId = DEFAULT_ADMIN_LOGIN_ID
    )
    val loanId = store.addLoan(
      type = LoanType.LENT.code,
      personName = "Karim",
      personPhone = "01711002233",
      amount = 5000.0,
      paidAmount = 0.0,
      currency = "BDT",
      dateMillis = 1763100000000L,
      dueDateMillis = 0L,
      note = "Initial loan",
      accountId = DEFAULT_ADMIN_LOGIN_ID
    )
    val borrowedLoanId = store.addLoan(
      type = LoanType.BORROWED.code,
      personName = "Salam",
      personPhone = "+966551234567",
      amount = 3000.0,
      paidAmount = 0.0,
      currency = "SAR",
      dateMillis = 1763100000000L,
      dueDateMillis = 0L,
      note = "Borrowed for rent",
      accountId = DEFAULT_ADMIN_LOGIN_ID
    )
    val savedLoan = store.observeLoans().first().first { it.id == loanId }
    store.addLoanRepayment(
      loan = savedLoan,
      amount = 2000.0,
      dateMillis = 1763200000000L,
      note = "Part payment",
      accountId = DEFAULT_ADMIN_LOGIN_ID
    )

    // Re-create store from same filesDir to verify disk persistence
    val reloadedStore = LocalHisabStore(context)
    val users = reloadedStore.observeManagedUsers().first()
    assertTrue(users.any { it.loginId == "aminulislam" && it.role == UserRole.ADMIN.code })
    assertTrue(users.any { it.loginId == "rahim01" && it.role == UserRole.USER.code })

    val incomes = reloadedStore.observeIncomes().first()
    val expenses = reloadedStore.observeExpenses().first()
    val loans = reloadedStore.observeLoans().first()
    val repayments = reloadedStore.observeLoanRepayments().first()

    assertTrue(incomes.any { it.id == incomeId && it.amount == 15000.0 })
    assertTrue(expenses.any { it.id == expenseId && it.amount == 3500.0 })
    assertEquals("01711002233", loans.first { it.id == loanId }.personPhone)
    assertEquals("+966551234567", loans.first { it.id == borrowedLoanId }.personPhone)
    assertEquals(LoanStatus.PARTIAL.code, loans.first { it.id == loanId }.status)
    assertEquals(2000.0, loans.first { it.id == loanId }.paidAmount, 0.01)
    assertEquals(1, repayments.size)
    assertEquals(1763200000000L, repayments.first().dateMillis)
  }
}
