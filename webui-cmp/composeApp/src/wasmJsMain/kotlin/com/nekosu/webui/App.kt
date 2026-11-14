package com.nekosu.webui

import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.twotone.Download
import androidx.compose.material.icons.twotone.Refresh
import androidx.compose.material.icons.twotone.Search
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
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.nekosu.webui.resources.NotoSansSC_Regular
import com.nekosu.webui.resources.Res
import org.jetbrains.compose.resources.Font
import kotlinx.coroutines.launch

private val LocalSettingsSearch = androidx.compose.runtime.compositionLocalOf { "" }

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
    var subScreen by remember { mutableStateOf<String?>(null) }
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

    var darkMode by remember { mutableStateOf(getPref("theme.darkMode", "system")) }
    var seedIndex by remember { mutableStateOf(getPref("theme.seed", "0").toIntOrNull() ?: 0) }
    val systemDark = isSystemInDarkTheme()
    val colorScheme = buildColorScheme(darkMode, seedIndex, systemDark)
    fun changeDarkMode(mode: String) { darkMode = mode; setPref("theme.darkMode", mode) }
    fun changeSeed(index: Int) { seedIndex = index; setPref("theme.seed", index.toString()) }
    MaterialTheme(typography = typography, colorScheme = colorScheme) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Scaffold(
                bottomBar = {
                    if (subScreen == null) {
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
                    }
                },
            ) { padding ->
                Box(Modifier.padding(padding).fillMaxSize()) {
                    when {
                        subScreen == "logs" -> LogCenterScreen(onBack = { subScreen = null })
                        subScreen == "kmi" -> KmiScreen(onBack = { subScreen = null })
                        subScreen == "theme" -> ThemeSettingsScreen(
                            darkMode = darkMode,
                            seedIndex = seedIndex,
                            onDarkModeChange = ::changeDarkMode,
                            onSeedChange = ::changeSeed,
                            onBack = { subScreen = null },
                        )
                        subScreen == "about" -> AboutScreen(onBack = { subScreen = null })
                        subScreen?.startsWith("app:") == true -> AppProfileScreen(
                            uid = subScreen!!.removePrefix("app:").toIntOrNull() ?: 0,
                            onBack = { subScreen = null },
                        )
                        else -> when (page) {
                            0 -> HomeScreen()
                            1 -> SuperUserScreen(onOpenProfile = { subScreen = "app:" + it })
                            2 -> ModuleScreen()
                            else -> SettingsScreen(
                                onOpenLogs = { subScreen = "logs" },
                                onOpenKmi = { subScreen = "kmi" },
                                onOpenTheme = { subScreen = "theme" },
                                onOpenAbout = { subScreen = "about" },
                            )
                        }
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
    val status = home.status
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val model = com.nekosu.shared.SharedHomeModel(
        working = status.isRootAvailable,
        lkmMode = status.lkmMode == true,
        lateLoad = status.isLateLoadMode,
        superuserCount = home.superuserCount,
        moduleCount = home.moduleCount,
        device = home.deviceModel,
        kernelRelease = home.kernelRelease,
        androidVersion = home.androidVersion,
        kernelDriverVersion = status.ksuFullVersion ?: "—",
        managerVersion = home.version + " (" + home.versionCode + ")",
        susfsVersion = home.susfsVersion,
        selinux = home.selinuxStatus,
        seccomp = seccompText(home.seccompStatus),
        kmi = formatKmi(status.kernelVersion),
        hook = status.hookType.ifBlank { "—" },
    )
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
        com.nekosu.shared.SharedHomeContent(model, Modifier.padding(padding))
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
            if (home.status.lkmMode == true) LabelPill("LKM")
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
    val moduleScope = rememberCoroutineScope()
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
                com.nekosu.shared.SharedModuleCard(
                    model = com.nekosu.shared.SharedModuleModel(
                        id = module.id,
                        name = module.name,
                        version = module.version,
                        author = module.author,
                        description = module.description,
                        enabled = enabledStates[module.id] ?: module.enabled,
                        metamodule = module.metamodule,
                    ),
                    onCheckedChange = { enabled ->
                        enabledStates[module.id] = enabled
                        moduleScope.launch { AppData.setModuleEnabled(module.id, enabled) }
                    },
                    onAction = {},
                    onRemove = {
                        moduleScope.launch { AppData.setModuleRemoved(module.id, true) }
                    },
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
private fun ModuleCard(
    module: ModuleEntry,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onRemove: () -> Unit,
) {
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
                TextButton(onClick = onRemove) {
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
private fun SuperUserScreen(onOpenProfile: (Int) -> Unit) {
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
    val routeScope = rememberCoroutineScope()
    val groupStates = remember { mutableStateMapOf<Int, Boolean>().apply { groups.forEach { put(it.uid, it.allowSu) } } }

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
                items(filtered, key = { it.uid }) { group ->
                    com.nekosu.shared.SharedAppGroupCard(
                        model = com.nekosu.shared.SharedAppGroupModel(
                            label = group.label,
                            packageName = group.packageName,
                            uid = group.uid,
                            allowSu = group.allowSu,
                            shouldUmount = group.shouldUmount,
                            customProfile = group.custom,
                            isManager = group.manager,
                            appsCount = group.appsCount,
                            userName = group.userName,
                        ),
                        onClick = { onOpenProfile(group.uid) },
                        onRootChange = { allowed ->
                            groupStates[group.uid] = allowed
                            routeScope.launch { AppData.setSuperuserRoot(group.uid, allowed) }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AppGroupCard(
    group: AppGroupMock,
    onRootChange: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable { onClick() }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
            Switch(
                checked = group.allowSu,
                onCheckedChange = onRootChange,
                thumbContent = {
                    Icon(
                        if (group.allowSu) Icons.TwoTone.Check else Icons.TwoTone.Close,
                        contentDescription = null,
                        modifier = Modifier.size(SwitchDefaults.IconSize),
                    )
                },
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
private fun SettingsScreen(
    onOpenLogs: () -> Unit,
    onOpenKmi: () -> Unit,
    onOpenTheme: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    var search by remember { mutableStateOf("") }
    var suLog by remember { mutableStateOf(true) }
    var selinuxHide by remember { mutableStateOf(true) }
    var umountDefault by remember { mutableStateOf(false) }
    var checkManagerUpdate by remember { mutableStateOf(true) }
    var checkBetaUpdate by remember { mutableStateOf(true) }
    var checkModuleUpdate by remember { mutableStateOf(true) }
    val liveSettings = AppData.settings ?: fallbackSettings()
    var webUiEnabled by remember(liveSettings) { mutableStateOf(liveSettings.enabled) }
    var webUiRandomPort by remember(liveSettings) { mutableStateOf(liveSettings.randomPort) }
    val scope = rememberCoroutineScope()
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
        androidx.compose.runtime.CompositionLocalProvider(LocalSettingsSearch provides search) {
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("搜索设置") },
                leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                shape = RoundedCornerShape(28.dp),
                singleLine = true,
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                    focusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
            )
            Spacer(Modifier.height(8.dp))
            SettingGroup("应用设置") {
                SettingSwitchRow(Icons.TwoTone.Update, "检查管理器更新", "自动检查管理器更新", checkManagerUpdate) { checkManagerUpdate = it }
                SettingSwitchRow(Icons.TwoTone.Science, "检查测试版更新", "自动检查 main 分支的测试版构建", checkBetaUpdate) { checkBetaUpdate = it }
                SettingSwitchRow(Icons.Filled.Extension, "检查模块更新", "自动检查已安装模块的更新", checkModuleUpdate) { checkModuleUpdate = it }
                SettingChevronRow(Icons.TwoTone.Settings, "主题设置", "主题设置", onClick = onOpenTheme)
            }
            SettingGroup("内核功能") {
                SettingSwitchRow(Icons.TwoTone.Science, "Su 兼容模式", "仅由 adbd 或 KernelSU Root 进程启动的 shell 可获取 Root", suLog) { suLog = it }
                SettingSwitchRow(Icons.TwoTone.Policy, "隐藏 SELinux 修改", "阻止应用检测 SELinux 修改", selinuxHide) { selinuxHide = it }
                SettingSwitchRow(Icons.TwoTone.FolderDelete, "默认卸载模块", "所有应用默认不挂载模块", umountDefault) { umountDefault = it }
                SettingChevronRow(Icons.TwoTone.FolderOff, "Umount 路径管理", "管理不挂载模块的路径")
            }
            SettingGroup("工具") {
                SettingChevronRow(Icons.TwoTone.Science, "KMI / 加载诊断", "设备内核、内置 KMI 匹配与加载状态", onClick = onOpenKmi)
                SettingChevronRow(Icons.AutoMirrored.TwoTone.Article, "日志中心", "SU / Magica / Crash / Metamodule / Problem 日志", onClick = onOpenLogs)
                SettingChevronRow(Icons.TwoTone.Save, "备份设置", "导出当前设置")
                SettingChevronRow(Icons.TwoTone.RestartAlt, "恢复设置", "从备份导入设置")
            }
            SettingGroup("WebUI") {
                SettingSwitchRow(Icons.TwoTone.Adb, "启用 WebUI 服务", "在 127.0.0.1 启动浏览器版管理器", webUiEnabled) {
                    webUiEnabled = it
                    scope.launch { AppData.updateWebUi(enabled = it) }
                }
                SettingSwitchRow(Icons.TwoTone.ElectricalServices, "随机端口", "由系统分配空闲端口；关闭时使用 8787", webUiRandomPort) {
                    webUiRandomPort = it
                    scope.launch { AppData.updateWebUi(randomPort = it) }
                }
                SettingChevronRow(Icons.TwoTone.Info, "打开 WebUI 页面", "查看地址 / Token / 在浏览器中打开")
            }
            SettingGroup("关于") {
                SettingChevronRow(Icons.TwoTone.Info, "关于", "版本、开源许可、致谢", onClick = onOpenAbout)
            }
            Spacer(Modifier.height(16.dp))
        }
        }
    }
}

@Composable
private fun SettingGroup(title: String, content: @Composable () -> Unit) {
    com.nekosu.shared.SharedSettingGroup(title, content)
}

@Composable
private fun SettingRowSurface(content: @Composable () -> Unit) {
    com.nekosu.shared.SharedSettingRowSurface(content)
}

@Composable
private fun SettingSwitchRow(icon: ImageVector, title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val query = LocalSettingsSearch.current
    if (query.isNotBlank() && !title.contains(query, true) && !description.contains(query, true)) return
    com.nekosu.shared.SharedSettingSwitchRow(icon, title, description, checked, onChange)
}

@Composable
private fun SettingChevronRow(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: (() -> Unit)? = null,
) {
    val query = LocalSettingsSearch.current
    if (query.isNotBlank() && !title.contains(query, true) && !description.contains(query, true)) return
    SettingRowSurface {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(enabled = onClick != null) { onClick?.invoke() }
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.TwoTone.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        }
    }
}


/* ============================== 日志中心（对照 LogCenterScreen.kt） ============================== */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogCenterScreen(onBack: () -> Unit) {
    val sources = listOf(
        "ALL" to "全部",
        "SULOG" to "SU",
        "MAGICA" to "Magica",
        "CRASH" to "Crash",
        "METAMODULE" to "Metamodule",
        "PROBLEM" to "Problem",
    )
    var source by remember { mutableStateOf("ALL") }
    var query by remember { mutableStateOf("") }
    var logsText by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun refresh() {
        loading = true
        scope.launch {
            logsText = AppData.loadLogs(source) ?: "（预览模式：无法连接 /api/logs，装入 APK 后显示真机日志）"
            loading = false
        }
    }

    LaunchedEffect(source) { refresh() }

    val filtered = remember(logsText, query) {
        if (query.isBlank()) logsText
        else logsText.split('\n').filter { it.contains(query, ignoreCase = true) }.joinToString("\n")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("日志中心", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.TwoTone.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { refresh() }) {
                        Icon(Icons.TwoTone.Refresh, contentDescription = "刷新")
                    }
                    IconButton(onClick = { downloadLog(filtered, "nekosu-" + source.lowercase() + ".log") }) {
                        Icon(Icons.TwoTone.Download, contentDescription = "导出")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                sources.forEach { (value, label) ->
                    FilterChip(
                        selected = source == value,
                        onClick = { source = value },
                        label = { Text(label) },
                        shape = RoundedCornerShape(999.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                    )
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("搜索日志") },
                leadingIcon = { Icon(Icons.TwoTone.Search, contentDescription = null) },
                shape = RoundedCornerShape(28.dp),
                singleLine = true,
            )
            if (loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) {
                Text(
                    text = filtered.ifEmpty { "（无日志）" },
                    modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

/* ============================== 主题设置（对照 ThemeSettings） ============================== */

private val seedColors = listOf(
    Color(0xFF6750A4),
    Color(0xFF0061A4),
    Color(0xFF2E6B3F),
    Color(0xFF8B5000),
)

private fun buildColorScheme(mode: String, seedIndex: Int, systemDark: Boolean): ColorScheme {
    val dark = when (mode) {
        "dark" -> true
        "light" -> false
        else -> systemDark
    }
    val base = if (dark) darkColorScheme() else lightColorScheme()
    val seed = seedColors.getOrElse(seedIndex) { seedColors[0] }
    val primaryContainer = lerp(if (dark) Color.Black else Color.White, seed, if (dark) 0.55f else 0.16f)
    val onPrimaryContainer = lerp(seed, if (dark) Color.White else Color.Black, if (dark) 0.75f else 0.65f)
    return base.copy(
        primary = if (dark) lerp(seed, Color.White, 0.35f) else seed,
        onPrimary = if (dark) Color.Black else Color.White,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = if (dark) lerp(seed, Color.White, 0.55f) else lerp(seed, Color.Black, 0.25f),
        tertiary = if (dark) lerp(seed, Color.White, 0.7f) else lerp(seed, Color.Black, 0.4f),
        surfaceTint = seed,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeSettingsScreen(
    darkMode: String,
    seedIndex: Int,
    onDarkModeChange: (String) -> Unit,
    onSeedChange: (Int) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("主题设置", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.TwoTone.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            SettingGroup("深色模式") {
                SettingRowSurface {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色").forEach { (value, label) ->
                            FilterChip(
                                selected = darkMode == value,
                                onClick = { onDarkModeChange(value) },
                                label = { Text(label) },
                                shape = RoundedCornerShape(999.dp),
                            )
                        }
                    }
                }
            }
            SettingGroup("主题色") {
                SettingRowSurface {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        seedColors.forEachIndexed { index, color ->
                            Box(
                                Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .then(
                                        if (seedIndex == index) {
                                            Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                        } else {
                                            Modifier
                                        },
                                    )
                                    .clickable { onSeedChange(index) },
                            )
                        }
                    }
                }
            }
            SettingGroup("预览") {
                SettingRowSurface {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                            Text("主色容器", Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primary) {
                            Text("主色", Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = MaterialTheme.colorScheme.onPrimary)
                        }
                        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                            Text("第三色", Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = MaterialTheme.colorScheme.onTertiaryContainer)
                        }
                    }
                }
            }
            SettingGroup("说明") {
                SettingRowSurface {
                    Text(
                        "Web 版使用预设主题色。动态取色（Monet）依赖 Android 壁纸，仅在 App 内生效；深色模式与主题色保存在浏览器本地。",
                        Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/* ============================== KMI / 加载诊断 ============================== */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KmiScreen(onBack: () -> Unit) {
    val home = AppData.home ?: fallbackHome()
    val status = home.status
    val kernel = status.kernelVersion
    val kernelText = kernel?.let { "${it.major}.${it.patchLevel}.${it.subLevel}" } ?: "—"
    val modeText = when (status.lkmMode) {
        true -> "LKM（可加载内核模块）"
        false -> "Built-in（内置）"
        null -> "未知"
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("KMI / 加载诊断", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.TwoTone.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            if (!status.isRootAvailable) {
                WarningCard("Root 不可用：加载状态可能不准确", Icons.TwoTone.Warning)
            }
            SegmentedSection("内核信息") {
                InfoRow(Icons.TwoTone.Memory, "内核版本", kernelText)
                InfoRow(Icons.TwoTone.DeveloperBoard, "内核 release", home.kernelRelease)
                InfoRow(Icons.TwoTone.Tag, "KMI 推断", formatKmi(kernel))
                InfoRow(Icons.TwoTone.Android, "Android 版本", home.androidVersion)
            }
            SegmentedSection("加载状态") {
                InfoRow(Icons.TwoTone.TaskAlt, "Root 状态", if (status.isRootAvailable) "可用" else "不可用")
                InfoRow(Icons.TwoTone.ElectricalServices, "加载模式", modeText)
                InfoRow(Icons.TwoTone.Science, "加载方式", if (status.isLateLoadMode) "越狱 / late-load" else "开机加载")
                InfoRow(Icons.TwoTone.Policy, "Hook 类型", status.hookType.ifBlank { "—" })
            }
            SegmentedSection("内核模块") {
                InfoRow(Icons.TwoTone.Tag, "KSU 版本", status.ksuFullVersion ?: "—")
                InfoRow(Icons.TwoTone.Security, "SELinux", home.selinuxStatus)
                InfoRow(Icons.TwoTone.FilterList, "Seccomp", seccompText(home.seccompStatus))
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/* ============================== 关于 ============================== */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutScreen(onBack: () -> Unit) {
    val home = AppData.home ?: fallbackHome()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("关于", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.TwoTone.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(8.dp))
            com.nekosu.shared.SharedAboutContent(
                com.nekosu.shared.SharedAboutModel(
                    appName = "NekoSU",
                    version = home.version,
                    versionCode = home.versionCode,
                    driverVersion = home.status.ksuFullVersion ?: "—",
                    onOpenUrl = { openUrl(it) },
                ),
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}


/* ============================== App Profile 详情 ============================== */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppProfileScreen(uid: Int, onBack: () -> Unit) {
    val groups = AppData.groups.ifEmpty { fallbackGroups() }
    val group = groups.firstOrNull { it.uid == uid }
    val primary = group?.apps?.firstOrNull { it.packageName == group.primaryPackageName } ?: group?.apps?.firstOrNull()
    var allowSu by remember(group) {
        mutableStateOf(group?.profile?.rootAllowed ?: group?.profile?.allowRoot ?: false)
    }
    var umount by remember(group) { mutableStateOf(group?.profile?.umountModules ?: group?.shouldUmount ?: false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("应用详情", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.TwoTone.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            SettingGroup("应用") {
                SettingRowSurface {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            primary?.label ?: group?.primaryPackageName ?: "未知应用",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            group?.primaryPackageName ?: "—",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "UID $uid" + if ((group?.apps?.size ?: 0) > 1) " · 包含 ${group?.apps?.size} 个应用" else "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (allowSu) LabelPill("ROOT")
                            if (umount) LabelPillTonal("UMOUNT", MaterialTheme.colorScheme.secondaryContainer)
                            LabelPillTonal(if (allowSu || umount) "CUSTOM" else "DEFAULT", MaterialTheme.colorScheme.tertiaryContainer)
                            if (group?.primaryPackageName == "com.nekosu") {
                                LabelPillTonal("MANAGER", MaterialTheme.colorScheme.errorContainer)
                            }
                        }
                    }
                }
            }
            SettingGroup("权限配置") {
                SettingSwitchRow(
                    Icons.TwoTone.Security,
                    "允许 ROOT",
                    "授予该应用超级用户权限",
                    allowSu,
                ) { checked ->
                    allowSu = checked
                    scope.launch { AppData.setSuperuserRoot(uid, checked) }
                }
                SettingSwitchRow(
                    Icons.TwoTone.FolderOff,
                    "不挂载模块",
                    "该应用运行时默认不挂载任何模块",
                    umount,
                ) { checked ->
                    umount = checked
                    scope.launch { AppData.setSuperuserUmount(uid, checked) }
                }
            }
            SettingGroup("操作") {
                SettingRowSurface {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            allowSu = false
                            umount = false
                            scope.launch {
                                AppData.setSuperuserRoot(uid, false)
                                AppData.setSuperuserUmount(uid, false)
                            }
                        }) { Text("恢复默认") }
                        OutlinedButton(onClick = onBack) { Text("返回列表") }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
