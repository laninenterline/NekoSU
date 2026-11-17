package com.nekosu.data.system

import kotlinx.serialization.Serializable

@Serializable
data class ManagerAppearance(val displayName: String, val iconEntry: String? = null) {
    init {
        require(displayName == validateName(displayName))
        require(iconEntry == null || (iconEntry.startsWith("res/") &&
            iconEntry.split('/').none { it == ".." || it.isEmpty() } && !iconEntry.contains('\\')))
    }

    companion object {
        private val hiddenIdentityKeywords = listOf("su", "io", "manager")

        fun isHiddenTextAllowed(value: String): Boolean =
            hiddenIdentityKeywords.none { value.contains(it, ignoreCase = true) }

        fun validateHiddenName(value: String): String = validateName(value).also {
            require(isHiddenTextAllowed(it)) { "Hidden app name cannot contain su, io or manager" }
        }

        fun validateName(value: String): String {
            val name = value.trim()
            require(name.isNotEmpty() && name.codePointCount(0, name.length) <= 40)
            require(name.codePoints().allMatch {
                !Character.isISOControl(it) && Character.getType(it) != Character.FORMAT.toInt() &&
                    Character.getType(it) != Character.SURROGATE.toInt()
            })
            return name
        }
    }
}
