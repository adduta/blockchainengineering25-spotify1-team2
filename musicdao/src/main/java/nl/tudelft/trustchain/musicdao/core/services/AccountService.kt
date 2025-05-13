package nl.tudelft.trustchain.musicdao.core.services

import nl.tudelft.trustchain.musicdao.core.repositories.model.AccountType
import nl.tudelft.trustchain.musicdao.core.repositories.model.Artist
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountService @Inject constructor() {
    fun calculateAccountType(artist: Artist): AccountType {
        return AccountType.fromDonationAmount(artist.totalDonations)
    }

    fun canUpgradeAccount(artist: Artist): Boolean {
        val currentType = artist.accountType
        val nextType = when (currentType) {
            AccountType.BASIC -> AccountType.BRONZE
            AccountType.BRONZE -> AccountType.SILVER
            AccountType.SILVER -> AccountType.GOLD
            AccountType.GOLD -> AccountType.PLATINUM
            AccountType.PLATINUM -> return false
        }
        return artist.totalDonations >= nextType.requiredDonations
    }

    fun getNextUpgradeRequirements(artist: Artist): Pair<AccountType, Double>? {
        val currentType = artist.accountType
        val nextType = when (currentType) {
            AccountType.BASIC -> AccountType.BRONZE
            AccountType.BRONZE -> AccountType.SILVER
            AccountType.SILVER -> AccountType.GOLD
            AccountType.GOLD -> AccountType.PLATINUM
            AccountType.PLATINUM -> return null
        }
        return Pair(nextType, nextType.requiredDonations - artist.totalDonations)
    }

    fun updateDonations(artist: Artist, newDonationAmount: Double): Artist {
        val updatedTotalDonations = artist.totalDonations + newDonationAmount
        return artist.copy(totalDonations = updatedTotalDonations)
    }

    fun upgradeToPro(artist: Artist): Artist {
        return artist.copy(accountType = AccountType.PRO)
    }

    fun downgradeToBasic(artist: Artist): Artist {
        return artist.copy(accountType = AccountType.BASIC)
    }

    fun getDownloadDelay(artist: Artist): Int {
        return artist.accountType.downloadDelayHours
    }

    fun canUpgradeToPro(artist: Artist): Boolean {
        return artist.accountType == AccountType.BASIC && 
               artist.totalDonations >= AccountType.PRO.requiredDonations
    }

    fun getMissingDonationsForPro(artist: Artist): Double {
        return if (artist.accountType == AccountType.BASIC) {
            AccountType.PRO.requiredDonations - artist.totalDonations
        } else {
            0.0
        }
    }
} 