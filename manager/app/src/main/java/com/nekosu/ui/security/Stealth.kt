package com.nekosu.ui.security

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.lifecycle.ViewModelStoreOwner
import com.nekosu.Natives

/**
 * Stealth mode hides the Manager UI (and the kernel MANAGER flag reported by
 * GET_INFO) without changing the kernel's real root permission.
 *
 * The switch is stored by the kernel in [STEALTH_FLAG_PATH]; while it is on,
 * GET_INFO stops reporting the manager role, so NekoSU's `KernelRepository`
 * sees "not installed" and most root-only UI disappears automatically.
 */
object Stealth {
    /** Must stay identical to `KSU_STEALTH_PATH` in `kernel/manager/stealth.c`. */
    const val STEALTH_FLAG_PATH = "/data/adb/ksu/stealth"

    enum class State {
        /** The kernel explicitly reports stealth on. */
        ON,

        /** The kernel explicitly reports stealth off. */
        OFF,

        /** The kernel has no stealth ioctl (old kernel): stealth cannot be on. */
        UNSUPPORTED,

        /** ioctl failed for another reason (e.g. Manager not recognized). */
        UNKNOWN,
    }

    enum class SetResult {
        SUCCESS,
        UNSUPPORTED,
        FAILED,
    }

    /**
     * 1 = on, 0 = off, -2 = kernel has no stealth ioctl, -1 = other ioctl
     * failure (unsupported/permission/not recognized).
     */
    fun kernelState(): Int = runCatching { Natives.stealthState() }.getOrDefault(-1)

    fun state(): State = when (kernelState()) {
        1 -> State.ON
        0 -> State.OFF
        -2 -> State.UNSUPPORTED
        else -> State.UNKNOWN
    }

    /**
     * Whether root features must be hidden. Only an explicit "0" from the
     * kernel is trusted; an unknown/failed state is treated as possibly hidden
     * so a transient ioctl failure never leaks root UI.
     */
    fun looksStealthy(): Boolean = when (state()) {
        State.ON, State.UNKNOWN -> true
        State.OFF, State.UNSUPPORTED -> false
    }

    /** True only when the kernel explicitly reports stealth on. */
    fun isEnabled(): Boolean = kernelState() == 1

    /** False only when the kernel explicitly reports that stealth is unavailable. */
    fun kernelHasStealthFeature(): Boolean = state() != State.UNSUPPORTED

    fun setEnabled(enabled: Boolean): Boolean =
        runCatching { Natives.setStealth(enabled) == 0 }.getOrDefault(false)

    fun setEnabledReporting(enabled: Boolean): SetResult = when {
        setEnabled(enabled) -> SetResult.SUCCESS
        !kernelHasStealthFeature() -> SetResult.UNSUPPORTED
        else -> SetResult.FAILED
    }
}

/**
 * Recreate the UI after a stealth toggle so ViewModels built from the old
 * manager state are discarded (they survive Activity.recreate()).
 */
fun restartUiFresh(context: Context) {
    val activity = context.findActivity()
    (activity as? ViewModelStoreOwner)?.let {
        runCatching { it.viewModelStore.clear() }
    }
    activity?.recreate()
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
