package com.example.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

const val DEFAULT_ADMIN_ACCOUNT_ID = "admin_main"
const val DEFAULT_ADMIN_LOGIN_ID = "aminulislam"

enum class CurrencyCode(val code: String, val symbol: String) {
    BDT("BDT", "৳"),
    SAR("SAR", "﷼");

    companion object {
        fun fromCode(code: String?): CurrencyCode =
            entries.firstOrNull { it.code == code } ?: BDT
    }
}

enum class AppLanguage(val code: String) {
    BN("BN"),
    EN("EN");

    companion object {
        fun fromCode(code: String?): AppLanguage =
            entries.firstOrNull { it.code == code } ?: BN
    }
}

enum class ThemeMode(val code: String) {
    SYSTEM("SYSTEM"),
    LIGHT("LIGHT"),
    DARK("DARK");

    companion object {
        fun fromCode(code: String?): ThemeMode =
            entries.firstOrNull { it.code == code } ?: SYSTEM
    }
}

enum class UserRole(val code: String) {
    ADMIN("ADMIN"),
    USER("USER");

    companion object {
        fun fromCode(code: String?): UserRole =
            entries.firstOrNull { it.code == code } ?: USER
    }
}

enum class IncomeSource(val code: String) {
    SALARY("SALARY"),
    BUSINESS("BUSINESS"),
    OTHER("OTHER");

    companion object {
        fun fromCode(code: String?): IncomeSource =
            entries.firstOrNull { it.code == code } ?: OTHER
    }
}

enum class ExpenseCategory(val code: String) {
    FOOD("FOOD"),
    RENT("RENT"),
    ELECTRICITY("ELECTRICITY"),
    GAS("GAS"),
    GROCERIES("GROCERIES"),
    TRANSPORT("TRANSPORT"),
    MEDICAL("MEDICAL"),
    FAMILY("FAMILY"),
    MOBILE_INTERNET("MOBILE_INTERNET"),
    OTHER("OTHER");

    companion object {
        fun fromCode(code: String?): ExpenseCategory =
            entries.firstOrNull { it.code == code } ?: OTHER
    }
}

enum class SavingsFrequency(val code: String) {
    DAILY("DAILY"),
    MONTHLY("MONTHLY");

    companion object {
        fun fromCode(code: String?): SavingsFrequency =
            entries.firstOrNull { it.code == code } ?: MONTHLY
    }
}

enum class LoanType(val code: String) {
    LENT("LENT"),
    BORROWED("BORROWED");

    companion object {
        fun fromCode(code: String?): LoanType =
            entries.firstOrNull { it.code == code } ?: LENT
    }
}

enum class LoanStatus(val code: String) {
    PENDING("PENDING"),
    PARTIAL("PARTIAL"),
    SETTLED("SETTLED");

    companion object {
        fun fromCode(code: String?): LoanStatus =
            entries.firstOrNull { it.code == code } ?: PENDING

        fun compute(amount: Double, paidAmount: Double): LoanStatus = when {
            paidAmount <= 0.0 -> PENDING
            paidAmount >= amount -> SETTLED
            else -> PARTIAL
        }
    }
}

@IgnoreExtraProperties
data class UserSettings(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val sarToBdtRate: Double = 32.5,
    val preferredCurrency: String = CurrencyCode.BDT.code,
    val language: String = AppLanguage.BN.code,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ManagedUser(
    @DocumentId val id: String = "",
    val userId: String = "",
    val loginId: String = "",
    val fullName: String = "",
    val passwordHash: String = "",
    val role: String = UserRole.USER.code,
    @field:PropertyName("isActive")
    @get:PropertyName("isActive")
    val isActive: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class IncomeEntry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val accountId: String = DEFAULT_ADMIN_ACCOUNT_ID,
    val amount: Double = 0.0,
    val currency: String = CurrencyCode.BDT.code,
    val source: String = IncomeSource.SALARY.code,
    val dateMillis: Long = 0L,
    val description: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ExpenseEntry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val accountId: String = DEFAULT_ADMIN_ACCOUNT_ID,
    val amount: Double = 0.0,
    val currency: String = CurrencyCode.BDT.code,
    val category: String = ExpenseCategory.FOOD.code,
    val dateMillis: Long = 0L,
    val description: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class SavingsGoal(
    @DocumentId val id: String = "",
    val userId: String = "",
    val accountId: String = DEFAULT_ADMIN_ACCOUNT_ID,
    val title: String = "",
    val targetAmount: Double = 0.0,
    val savedAmount: Double = 0.0,
    val currency: String = CurrencyCode.BDT.code,
    val frequency: String = SavingsFrequency.MONTHLY.code,
    val targetDateMillis: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val progressRatio: Float
        get() = if (targetAmount > 0.0) (savedAmount / targetAmount).toFloat().coerceIn(0f, 1f) else 0f

    val progressPercent: Int
        get() = if (targetAmount > 0.0) ((savedAmount / targetAmount) * 100.0).toInt().coerceIn(0, 100) else 0
}

@IgnoreExtraProperties
data class SavingsEntry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val accountId: String = DEFAULT_ADMIN_ACCOUNT_ID,
    val goalId: String = "",
    val goalTitle: String = "",
    val amount: Double = 0.0,
    val currency: String = CurrencyCode.BDT.code,
    val frequency: String = SavingsFrequency.DAILY.code,
    val dateMillis: Long = 0L,
    val note: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class LoanRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val accountId: String = DEFAULT_ADMIN_ACCOUNT_ID,
    val type: String = LoanType.LENT.code,
    val personName: String = "",
    val personPhone: String = "",
    val amount: Double = 0.0,
    val paidAmount: Double = 0.0,
    val currency: String = CurrencyCode.BDT.code,
    val dateMillis: Long = 0L,
    val dueDateMillis: Long = 0L,
    val note: String = "",
    val status: String = LoanStatus.PENDING.code,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val remainingAmount: Double
        get() = (amount - paidAmount).coerceAtLeast(0.0)
}

@IgnoreExtraProperties
data class LoanRepayment(
    @DocumentId val id: String = "",
    val userId: String = "",
    val accountId: String = DEFAULT_ADMIN_ACCOUNT_ID,
    val loanId: String = "",
    val loanType: String = LoanType.LENT.code,
    val personName: String = "",
    val amount: Double = 0.0,
    val currency: String = CurrencyCode.BDT.code,
    val dateMillis: Long = 0L,
    val note: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
