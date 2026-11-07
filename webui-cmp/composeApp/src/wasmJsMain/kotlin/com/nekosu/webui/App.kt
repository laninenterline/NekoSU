package com.nekosu.webui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.twotone.Settings
import androidx.compose.material.icons.automirrored.twotone.Article
import androidx.compose.material.icons.twotone.Adb
import androidx.compose.material.icons.twotone.ChevronRight
import androidx.compose.material.icons.twotone.ElectricalServices
import androidx.compose.material.icons.twotone.FolderDelete
import androidx.compose.material.icons.twotone.FolderOff
import androidx.compose.material.icons.twotone.Info
import androidx.compose.material.icons.twotone.MoreVert
import androidx.compose.material.icons.twotone.Policy
import androidx.compose.material.icons.twotone.RestartAlt
import androidx.compose.material.icons.twotone.Save
import androidx.compose.material.icons.twotone.Science
import androidx.compose.material.icons.twotone.Update
import androidx.compose.material.icons.twotone.SearchOff
import androidx.compose.material.icons.twotone.Android
import androidx.compose.material.icons.twotone.Check
import androidx.compose.material.icons.twotone.Close
import androidx.compose.material.icons.twotone.Cloud
import androidx.compose.material.icons.twotone.DeveloperBoard
import androidx.compose.material.icons.twotone.FilterList
import androidx.compose.material.icons.twotone.Memory
import androidx.compose.material.icons.twotone.MenuBook
import androidx.compose.material.icons.twotone.PowerSettingsNew
import androidx.compose.material.icons.twotone.Security
import androidx.compose.material.icons.twotone.Smartphone
import androidx.compose.material.icons.twotone.Tag
import androidx.compose.material.icons.twotone.TaskAlt
import androidx.compose.material.icons.twotone.Tune
import androidx.compose.material.icons.twotone.VolunteerActivism
import androidx.compose.material.icons.twotone.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.nekosu.webui.resources.NotoSansSC_Regular
import com.nekosu.webui.resources.Res
import org.jetbrains.compose.resources.Font

private val bottomItems = listOf(
    Triple("首页", Icons.Filled.Home, 0),
    Triple("超级用户", Icons.Filled.AdminPanelSettings, 1),
    Triple("模块", Icons.Filled.Extension, 2),
    Triple("设置", Icons.Filled.Settings, 3),
)

private data class ModuleEntry(
    val id: String,
    val name: String,
    val version: String,
    val author: String,
    val description: String,
    val enabled: Boolean,
    val metamodule: Boolean,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    var page by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { AppData.load() }
    val appFont = FontFamily(Font(Res.font.NotoSansSC_Regular))
    val baseTypography = androidx.compose.material3.Typography()
    val typography = baseTypography.copy(
        displayLarge = baseTypography.displayLarge.copy(fontFamily = appFont),
        displayMedium = baseTypography.displayMedium.copy(fontFamily = appFont),
        displaySmall = baseTypography.displaySmall.copy(fontFamily = appFont),
        headlineLarge = baseTypography.headlineLarge.copy(fontFamily = appFont),
        headlineMedium = baseTypography.headlineMedium.copy(fontFamily = appFont),
        headlineSmall = baseTypography.headlineSmall.copy(fontFamily = appFont),
        titleLarge = baseTypography.titleLarge.copy(fontFamily = appFont),
        titleMedium = baseTypography.titleMedium.copy(fontFamily = appFont),
        titleSmall = baseTypography.titleSmall.copy(fontFamily = appFont),
        bodyLarge = baseTypography.bodyLarge.copy(fontFamily = appFont),
        bodyMedium = baseTypography.bodyMedium.copy(fontFamily = appFont),
        bodySmall = baseTypography.bodySmall.copy(fontFamily = appFont),
        labelLarge = baseTypography.labelLarge.copy(fontFamily = appFont),
        labelMedium = baseTypography.labelMedium.copy(fontFamily = appFont),
        labelSmall = baseTypography.labelSmall.copy(fontFamily = appFont),
    )

    MaterialTheme(typography = typography) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Scaffold(
                bottomBar = {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                        bottomItems.forEach { (label, icon, index) ->
                            NavigationBarItem(
                                selected = page == index,
                                onClick = { page = index },
                                icon = { Icon(icon, contentDescription = label) },
                                label = { Text(label) },
                            )
                        }
                    }
                },
            ) { padding ->
                Box(Modifier.padding(padding).fillMaxSize()) {
                    when (page) {
                        0 -> HomeScreen()
                        1 -> SuperUserScreen()
                        2 -> ModuleScreen()
                        else -> SettingsScreen()
                    }
                }
            }
        }
    }
}

/* ============================== 首页（对照 HomePage.kt） ============================== */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen() {
    val home = AppData.home ?: fallbackHome()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("NekoSU", fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = {}) { Icon(Icons.TwoTone.Tune, contentDescription = "SUSFS 配置") }
                    IconButton(onClick = {}) { Icon(Icons.TwoTone.PowerSettingsNew, contentDescription = "重启") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Spacer(Modifier.height(6.dp))
            StatusCard(home)
            Spacer(Modifier.height(2.dp))
            SegmentedSection("版本信息") {
                InfoRow(Icons.TwoTone.Smartphone, "设备型号", home.deviceModel)
                InfoRow(Icons.TwoTone.DeveloperBoard, "内核版本（release）", home.kernelRelease)
                InfoRow(Icons.TwoTone.Android, "Android 版本", home.androidVersion)
                InfoRow(Icons.TwoTone.Memory, "内核版本", home.status.ksuFullVersion ?: "—")
                InfoRow(Icons.TwoTone.Tag, "管理器版本", "${home.version} (${home.versionCode})")
            }
            SegmentedSection("状态信息") {
                InfoRow(Icons.TwoTone.Security, "SELinux 状态", home.selinuxStatus)
                InfoRow(Icons.TwoTone.FilterList, "Seccomp 状态", seccompText(home.seccompStatus))
                InfoRow(Icons.TwoTone.Tag, "KMI", formatKmi(home.status.kernelVersion))
            }
            SegmentedSection("支持") {
                InfoRow(Icons.TwoTone.VolunteerActivism, "支持 NekoSU", "支持上游开发者")
                InfoRow(Icons.TwoTone.MenuBook, "了解更多", "了解 KernelSU")
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun StatusCard(home: HomeInfo) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.TwoTone.TaskAlt, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(if (home.status.isRootAvailable) "工作中" else "未安装", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.height(2.dp))
                Text(
                    "超级用户 ${home.superuserCount} · 模块 ${home.moduleCount}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
            }
            if (home.status.lkmMode != false) LabelPill("LKM")
            if (home.status.isLateLoadMode) {
                Spacer(Modifier.width(6.dp))
                LabelPill("越狱模式")
            }
        }
    }
}

@Composable
private fun LabelPill(text: String) {
    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primary) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

@Composable
private fun SegmentedSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            title,
            modifier = Modifier.padding(start = 16.dp),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
            Column { content() }
        }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, title: String, description: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider(modifier = Modifier.padding(start = 46.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
}

private fun seccompText(status: Int): String = when (status) {
    -1 -> "不支持"
    0 -> "已禁用"
    1 -> "严格模式"
    2 -> "过滤模式"
    else -> "未知"
}

private fun formatKmi(version: KernelVersionInfo?): String {
    if (version == null) return "—"
    return when ("${version.major}.${version.patchLevel}") {
        "4.19" -> "android10-4.19"
        "4.14" -> "android9-4.14"
        "4.9" -> "android8-4.9"
        "4.4" -> "android7-4.4"
        "5.4" -> "android11-5.4"
        "5.10" -> "android12-5.10"
        "5.15" -> "android13-5.15"
        "6.1" -> "android14-6.1"
        "6.6" -> "android15-6.6"
        "6.12" -> "android16-6.12"
        "6.18" -> "android17-6.18"
        else -> "${version.major}.${version.patchLevel}.x"
    }
}

/* ============================== 模块（对照 ModulePage.kt） ============================== */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModuleScreen() {
    val modules = remember(AppData.modules) {
        AppData.modules.ifEmpty { fallbackModules() }.map {
            ModuleEntry(it.id, it.name, it.version, it.author, it.description, it.enabled, it.metamodule)
        }
    }
    var search by remember { mutableStateOf("") }
    val enabledStates = remember { mutableStateMapOf<String, Boolean>().apply { modules.forEach { put(it.id, it.enabled) } } }
    val filtered = modules.filter { it.name.contains(search, ignoreCase = true) || it.id.contains(search, ignoreCase = true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("模块", fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = {}) { Icon(Icons.TwoTone.Cloud, contentDescription = "模块仓库") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                onClick = {},
            ) {
                Icon(Icons.Filled.Extension, contentDescription = "安装模块")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("搜索模块") },
                    shape = RoundedCornerShape(28.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                )
            }
            if (modules.any { it.metamodule }) {
                item {
                    WarningCard(
                        message = "检测到 metamodule：卸载/更换 metamodule 会影响所有模块的挂载行为。",
                        icon = Icons.TwoTone.Warning,
                    )
                }
            }
            items(filtered, key = { it.id }) { module ->
                ModuleCard(
                    module = module,
                    enabled = enabledStates[module.id] ?: module.enabled,
                    onEnabledChange = { enabledStates[module.id] = it },
                )
            }
            if (filtered.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("未找到模块", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun WarningCard(message: String, icon: ImageVector) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.width(10.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}

@Composable
private fun ModuleCard(module: ModuleEntry, enabled: Boolean, onEnabledChange: (Boolean) -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        module.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text("Version: ${module.version}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Author: ${module.author}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                    thumbContent = {
                        Icon(
                            if (enabled) Icons.TwoTone.Check else Icons.TwoTone.Close,
                            contentDescription = null,
                            modifier = Modifier.size(SwitchDefaults.IconSize),
                        )
                    },
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(module.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = {}) { Text("操作") }
                TextButton(onClick = {}) {
                    Text("卸载", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

/* ============================== 超级用户（对照 SuperUserPage.kt） ============================== */

private data class AppGroupMock(
    val label: String,
    val packageName: String,
    val uid: Int,
    val allowSu: Boolean,
    val shouldUmount: Boolean = false,
    val custom: Boolean = false,
    val manager: Boolean = false,
    val appsCount: Int = 1,
    val userName: String? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SuperUserScreen() {
    var search by remember { mutableStateOf("") }
    val groups = remember(AppData.groups) {
        AppData.groups.ifEmpty { fallbackGroups() }.map { group ->
            val primary = group.apps.firstOrNull { it.packageName == group.primaryPackageName } ?: group.apps.firstOrNull()
            AppGroupMock(
                label = primary?.label ?: group.primaryPackageName,
                packageName = group.primaryPackageName,
                uid = group.uid,
                allowSu = group.profile?.rootAllowed ?: group.profile?.allowRoot ?: false,
                shouldUmount = group.shouldUmount,
                custom = group.profile?.umountModules == true,
                manager = group.primaryPackageName == "com.nekosu",
                appsCount = group.apps.size,
            )
        }
    }
    val filtered = groups.filter {
        it.label.contains(search, ignoreCase = true) || it.packageName.contains(search, ignoreCase = true)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("超级用户", fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = {}) { Icon(Icons.TwoTone.MoreVert, contentDescription = "更多") }
                    IconButton(onClick = {}) { Icon(Icons.AutoMirrored.TwoTone.Article, contentDescription = "SU 日志") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("搜索应用") },
                    shape = RoundedCornerShape(28.dp),
                    singleLine = true,
                )
            }
            if (filtered.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.TwoTone.SearchOff, contentDescription = null, modifier = Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(12.dp))
                            Text("未找到应用", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                items(filtered, key = { it.uid }) { group -> AppGroupCard(group) }
            }
        }
    }
}

@Composable
private fun AppGroupCard(group: AppGroupMock) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        group.label.take(1),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(group.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    if (group.appsCount > 1) "包含 ${group.appsCount} 个应用" else group.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (group.allowSu) LabelPill("ROOT")
                    else if (group.shouldUmount) LabelPillTonal("UMOUNT", MaterialTheme.colorScheme.secondaryContainer)
                    if (group.custom) LabelPillTonal("CUSTOM", MaterialTheme.colorScheme.tertiaryContainer)
                    else if (!group.allowSu) LabelPillTonal("DEFAULT", MaterialTheme.colorScheme.primaryContainer)
                    if (group.manager) LabelPillTonal("MANAGER", MaterialTheme.colorScheme.errorContainer)
                    group.userName?.let { LabelPillTonal(it, MaterialTheme.colorScheme.secondaryContainer) }
                }
            }
            Icon(
                Icons.TwoTone.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LabelPillTonal(text: String, color: androidx.compose.ui.graphics.Color) {
    Surface(shape = RoundedCornerShape(8.dp), color = color) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/* ============================== 设置（对照 SettingsPage.kt） ============================== */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen() {
    var suLog by remember { mutableStateOf(true) }
    var selinuxHide by remember { mutableStateOf(true) }
    var umountDefault by remember { mutableStateOf(false) }
    var checkManagerUpdate by remember { mutableStateOf(true) }
    var checkBetaUpdate by remember { mutableStateOf(false) }
    var checkModuleUpdate by remember { mutableStateOf(true) }
    val liveSettings = AppData.settings ?: fallbackSettings()
    var webUiEnabled by remember(liveSettings) { mutableStateOf(liveSettings.enabled) }
    var webUiRandomPort by remember(liveSettings) { mutableStateOf(liveSettings.randomPort) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("设置", fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            SegmentedSection("内核功能") {
                SwitchInfoRow(Icons.TwoTone.Science, "Su 兼容模式", "仅由 adbd 或 KernelSU Root 进程启动的 shell 可获取 Root", suLog) { suLog = it }
                SwitchInfoRow(Icons.TwoTone.Policy, "隐藏 SELinux 修改", "阻止应用检测 SELinux 修改", selinuxHide) { selinuxHide = it }
                SwitchInfoRow(Icons.TwoTone.FolderDelete, "默认卸载模块", "所有应用默认不挂载模块", umountDefault) { umountDefault = it }
                ChevronInfoRow(Icons.TwoTone.FolderOff, "Umount 路径管理", "管理不挂载模块的路径")
            }
            SegmentedSection("应用设置") {
                SwitchInfoRow(Icons.TwoTone.Update, "检查管理器更新", "启动时检查稳定版更新", checkManagerUpdate) { checkManagerUpdate = it }
                SwitchInfoRow(Icons.TwoTone.Science, "检查测试版更新", "检查 beta 更新", checkBetaUpdate) { checkBetaUpdate = it }
                SwitchInfoRow(Icons.Filled.Extension, "检查模块更新", "启动时检查已安装模块更新", checkModuleUpdate) { checkModuleUpdate = it }
                ChevronInfoRow(Icons.TwoTone.Settings, "主题设置", "自定义配色、模糊、卡片行为")
            }
            SegmentedSection("工具") {
                ChevronInfoRow(Icons.TwoTone.Science, "KMI 诊断", "查看内核与 KMI 匹配信息")
                ChevronInfoRow(Icons.AutoMirrored.TwoTone.Article, "日志中心", "SU / Magica / Crash / Metamodule / Problem 日志")
                ChevronInfoRow(Icons.TwoTone.Save, "备份设置", "导出当前设置")
                ChevronInfoRow(Icons.TwoTone.RestartAlt, "恢复设置", "从备份导入设置")
            }
            SegmentedSection("WebUI") {
                SwitchInfoRow(Icons.TwoTone.Adb, "启用 WebUI 服务", "在 127.0.0.1 启动浏览器版管理器", webUiEnabled) { webUiEnabled = it }
                SwitchInfoRow(Icons.TwoTone.ElectricalServices, "随机端口", "由系统分配空闲端口；关闭时使用 8787", webUiRandomPort) { webUiRandomPort = it }
                ChevronInfoRow(Icons.TwoTone.Info, "打开 WebUI 页面", "查看地址 / Token / 在浏览器中打开")
            }
            SegmentedSection("关于") {
                ChevronInfoRow(Icons.TwoTone.Info, "关于", "版本、开源许可、致谢")
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SwitchInfoRow(icon: ImageVector, title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            thumbContent = {
                Icon(
                    if (checked) Icons.TwoTone.Check else Icons.TwoTone.Close,
                    contentDescription = null,
                    modifier = Modifier.size(SwitchDefaults.IconSize),
                )
            },
        )
    }
    HorizontalDivider(modifier = Modifier.padding(start = 48.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
}

@Composable
private fun ChevronInfoRow(icon: ImageVector, title: String, description: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.TwoTone.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
    }
    HorizontalDivider(modifier = Modifier.padding(start = 48.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
}
