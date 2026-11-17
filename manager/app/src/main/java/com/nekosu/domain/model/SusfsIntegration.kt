package com.nekosu.domain.model

/**
 * SUSFS integration state reported by `ksud susfs status`.
 *
 * The integration switch loads the SUSFS guard LKM on demand and persists an
 * auto-load choice for the next boot.
 */
data class SusfsIntegrationStatus(
    val supported: Boolean = false,
    val loaded: Boolean = false,
    val builtin: Boolean = false,
    /** The guard LKM was loaded by the SUSFS integration switch itself. */
    val owned: Boolean = false,
    val autoload: Boolean = false,
    /** "builtin", "lkm" or "none". */
    val backend: String = "",
    val version: String = "",
    val variant: String = "",
    val kmi: String = "",
    val reason: String = "",
    val rebootRequired: Boolean = false,
)

/**
 * Result of `susfs enable` / `susfs disable`.
 *
 * [status] is the state read back after the command (may be null when the CLI
 * could not answer at all) and [error] is a user-displayable failure reason.
 */
data class SusfsIntegrationResult(
    val status: SusfsIntegrationStatus?,
    val error: String?,
)
