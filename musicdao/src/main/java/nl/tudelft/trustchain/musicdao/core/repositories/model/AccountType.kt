package nl.tudelft.trustchain.musicdao.core.repositories.model

enum class AccountType(val displayName: String, val downloadDelayHours: Int, val requiredDonations: Double) {
    BASIC("Basic", 24, 0.0),
    PRO("Pro", 0, 0.1); // 0.1 BTC required for Pro

    companion object {
        fun fromString(type: String): AccountType {
            return when (type.uppercase()) {
                "PRO" -> PRO
                else -> BASIC
            }
        }
    }
}
