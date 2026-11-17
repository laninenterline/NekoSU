package com.nekosu.data.logging

import android.app.Application
import java.io.File
import com.nekosu.data.shell.KsuCliRepository

class BugreportRepository(
    private val application: Application,
    private val ksuCliRepository: KsuCliRepository,
) {
    fun create(): File = getBugreportFile(application, ksuCliRepository)
}
