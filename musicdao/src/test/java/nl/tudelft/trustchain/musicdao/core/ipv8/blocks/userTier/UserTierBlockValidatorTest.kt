package nl.tudelft.trustchain.musicdao.core.ipv8.blocks.userTier

import io.mockk.*
import nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock
import nl.tudelft.ipv8.attestation.trustchain.store.TrustChainStore
import nl.tudelft.ipv8.attestation.trustchain.validation.ValidationResult
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.*

class UserTierBlockValidatorTest {
    private lateinit var validator: UserTierBlockValidator
    private lateinit var database: TrustChainStore

    @BeforeEach
    fun setup() {
        validator = UserTierBlockValidator()
        database = mockk()
    }

    @Test
    fun `test validate returns valid for correct PRO tier block`() {
        val block = mockk<TrustChainBlock>()
        val transaction = mapOf(
            "userId" to "test_user",
            "tier" to "PRO",
            "validFrom" to 1000L,
            "validUntil" to 2000L
        )

        every { block.transaction } returns transaction

        val result = validator.validate(block, database)

        assertEquals(ValidationResult.Valid, result)
    }

    @Test
    fun `test validate returns valid for correct ULTIMATE tier block`() {
        val block = mockk<TrustChainBlock>()
        val transaction = mapOf(
            "userId" to "test_user",
            "tier" to "ULTIMATE",
            "validFrom" to 1000L,
            "validUntil" to null
        )

        every { block.transaction } returns transaction

        val result = validator.validate(block, database)

        assertEquals(ValidationResult.Valid, result)
    }

    @Test
    fun `test validate returns invalid for missing required fields`() {
        val block = mockk<TrustChainBlock>()
        val transaction = mapOf(
            "userId" to "test_user",
            "tier" to "PRO"
            // missing validFrom
        )

        every { block.transaction } returns transaction

        val result = validator.validate(block, database)

        assertTrue(result is ValidationResult.Invalid)
        assertTrue((result as ValidationResult.Invalid).errors.any { it.contains("Missing required fields") })
    }

    @Test
    fun `test validate returns invalid for invalid tier`() {
        val block = mockk<TrustChainBlock>()
        val transaction = mapOf(
            "userId" to "test_user",
            "tier" to "INVALID_TIER",
            "validFrom" to 1000L
        )

        every { block.transaction } returns transaction

        val result = validator.validate(block, database)

        assertTrue(result is ValidationResult.Invalid)
        assertTrue((result as ValidationResult.Invalid).errors.any { it.contains("Invalid tier value") })
    }

    @Test
    fun `test validate returns invalid for invalid validFrom timestamp`() {
        val block = mockk<TrustChainBlock>()
        val transaction = mapOf(
            "userId" to "test_user",
            "tier" to "PRO",
            "validFrom" to -1L
        )

        every { block.transaction } returns transaction

        val result = validator.validate(block, database)

        assertTrue(result is ValidationResult.Invalid)
        assertTrue((result as ValidationResult.Invalid).errors.any { it.contains("Invalid validFrom timestamp") })
    }

    @Test
    fun `test validate returns invalid when validUntil is before validFrom`() {
        val block = mockk<TrustChainBlock>()
        val transaction = mapOf(
            "userId" to "test_user",
            "tier" to "PRO",
            "validFrom" to 2000L,
            "validUntil" to 1000L
        )

        every { block.transaction } returns transaction

        val result = validator.validate(block, database)

        assertTrue(result is ValidationResult.Invalid)
        assertTrue((result as ValidationResult.Invalid).errors.any { it.contains("validUntil must be greater than validFrom") })
    }
}
