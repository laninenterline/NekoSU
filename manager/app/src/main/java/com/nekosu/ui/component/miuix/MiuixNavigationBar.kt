package com.nekosu.ui.component.miuix

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nekosu.ui.screen.BottomBarDestination
import com.nekosu.ui.util.LocalHandlePageChange
import com.nekosu.ui.util.LocalSelectedPage
import com.nekosu.ui.viewmodel.HomeViewModel
import org.koin.compose.viewmodel.koinViewModel
import top.yukonga.miuix.kmp.basic.Badge
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationRail
import top.yukonga.miuix.kmp.basic.NavigationRailItem
import top.yukonga.miuix.kmp.basic.NavigationRailValue
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.rememberNavigationRailState
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Miuix 风格的底部导航 / 横屏侧边 Rail。
 *
 * 与 Material 版 NavigationBar 使用同一套状态（LocalSelectedPage /
 * LocalHandlePageChange）与徽标逻辑（超级用户 / 模块计数），只替换视觉组件。
 *
 * @param isBottomBar true 为底部导航，false 为横屏侧边 Rail。
 */
@Composable
fun MiuixNavigationBar(
    destinations: List<BottomBarDestination>,
    isBottomBar: Boolean,
    modifier: Modifier = Modifier,
) {
    val homeViewModel = koinViewModel<HomeViewModel>()
    val uiState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val selectedPage = LocalSelectedPage.current
    val handlePageChange = LocalHandlePageChange.current
    val colorScheme = MiuixTheme.colorScheme

    if (isBottomBar) {
        NavigationBar(
            modifier = modifier,
            color = colorScheme.surface,
        ) {
            destinations.forEachIndexed { index, destination ->
                NavigationBarItem(
                    selected = index == selectedPage,
                    onClick = { handlePageChange(index) },
                    icon = if (index == selectedPage) {
                        destination.iconSelected
                    } else {
                        destination.iconNotSelected
                    },
                    label = stringResource(destination.label),
                    badge = destinationBadge(
                        destination = destination,
                        superuserCount = uiState.systemInfo.superuserCount,
                        moduleCount = uiState.systemInfo.moduleCount,
                        show = uiState.showNavigationBarBadge,
                    ),
                )
            }
        }
    } else {
        val railState = rememberNavigationRailState(
            initialValue = NavigationRailValue.Collapsed,
        )
        NavigationRail(
            state = railState,
            modifier = modifier,
            color = colorScheme.surface,
        ) {
            destinations.forEachIndexed { index, destination ->
                NavigationRailItem(
                    selected = index == selectedPage,
                    onClick = { handlePageChange(index) },
                    icon = if (index == selectedPage) {
                        destination.iconSelected
                    } else {
                        destination.iconNotSelected
                    },
                    label = stringResource(destination.label),
                    badge = destinationBadge(
                        destination = destination,
                        superuserCount = uiState.systemInfo.superuserCount,
                        moduleCount = uiState.systemInfo.moduleCount,
                        show = uiState.showNavigationBarBadge,
                    ),
                )
            }
        }
    }
}

@Composable
private fun destinationBadge(
    destination: BottomBarDestination,
    superuserCount: Int,
    moduleCount: Int,
    show: Boolean,
): (@Composable () -> Unit)? {
    val count = when (destination) {
        BottomBarDestination.SuperUser -> superuserCount
        BottomBarDestination.Module -> moduleCount
        else -> 0
    }
    if (!show || count <= 0) return null
    return {
        Badge(
            containerColor = MiuixTheme.colorScheme.primary,
        ) {
            Text(text = count.toString())
        }
    }
}
