package com.nekosu.domain.model

data class HiddenManagerState(
    val supported: Boolean = false,
    val hidden: Boolean = false,
    val busy: Boolean = false,
    val currentPackage: String = "",
    val displayName: String? = null,
    val error: String? = null,
    val pendingTransfer: Boolean = false,
    val hasOfficialCopy: Boolean = false,
    /** Android user id (uid / 100000) the Manager currently runs as. */
    val currentUserId: Int = 0,
    /** True when the device has more than one Android user. */
    val multiUserDevice: Boolean = false,
    /** A newer official APK was prepared for a hidden update but not installed yet. */
    val pendingUpdate: Boolean = false,
)
