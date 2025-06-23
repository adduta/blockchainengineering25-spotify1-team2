package nl.tudelft.trustchain.musicdao.core.model

enum class AccountType {
    BASIC,
    PRO,
    ULTIMATE
}

data class UserAccount(
    val userId: String,
    val accountType: AccountType,
    val createdAt: Long = System.currentTimeMillis()
)
