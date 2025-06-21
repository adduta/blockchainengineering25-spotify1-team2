package nl.tudelft.trustchain.musicdao.core.ipv8.blocks.userTier

import nl.tudelft.ipv8.attestation.trustchain.validation.TransactionValidator
import nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock
import nl.tudelft.ipv8.attestation.trustchain.store.TrustChainStore
import nl.tudelft.ipv8.attestation.trustchain.validation.ValidationResult
import javax.inject.Inject

class UserTierBlockValidator
    @Inject
    constructor() : TransactionValidator {
        override fun validate(
            block: TrustChainBlock,
            database: TrustChainStore
        ): ValidationResult {
            val transaction = block.transaction

            // Check if all required fields are present
            val requiredFields = listOf("userId", "tier", "validFrom")
            val missingFields = requiredFields.filter { transaction[it] == null }

            if (missingFields.isNotEmpty()) {
                return ValidationResult.Invalid(
                    listOf("Missing required fields: ${missingFields.joinToString(", ")}")
                )
            }

            // Validate tier value
            val tier = transaction["tier"] as? String
            if (tier == null || !listOf("BASIC", "PRO", "ULTIMATE").contains(tier)) {
                return ValidationResult.Invalid(
                    listOf("Invalid tier value: $tier. Must be one of: BASIC, PRO, ULTIMATE")
                )
            }

            // Validate validFrom timestamp
            val validFrom = transaction["validFrom"] as? Number
            if (validFrom == null || validFrom.toLong() <= 0) {
                return ValidationResult.Invalid(
                    listOf("Invalid validFrom timestamp: $validFrom")
                )
            }

            // Validate validUntil timestamp if present
            val validUntil = transaction["validUntil"] as? Number
            if (validUntil != null) {
                if (validUntil.toLong() <= 0) {
                    return ValidationResult.Invalid(
                        listOf("Invalid validUntil timestamp: $validUntil")
                    )
                }
                if (validUntil.toLong() <= validFrom.toLong()) {
                    return ValidationResult.Invalid(
                        listOf("validUntil must be greater than validFrom")
                    )
                }
            }

            return ValidationResult.Valid
        }
    }
