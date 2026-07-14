package com.renovation.ledger.ui.navigation

import android.widget.Toast
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.renovation.ledger.ui.detail.ItemDetailScreen
import com.renovation.ledger.ui.entry.ConfirmEntryScreen
import com.renovation.ledger.ui.entry.ManualEntryScreen
import com.renovation.ledger.ui.importbatch.BatchImportConfirmScreen
import com.renovation.ledger.ui.list.BudgetListScreen
import com.renovation.ledger.ui.mine.MineScreen
import com.renovation.ledger.ui.overview.OverviewScreen
import com.renovation.ledger.ui.paidgap.PaidGapDetailScreen
import com.renovation.ledger.ui.pending.PendingSpendScreen
import com.renovation.ledger.ui.settings.SettingsScreen
import com.renovation.ledger.ui.stats.StatsScreen
import com.renovation.ledger.ui.taxonomy.TaxonomyManageScreen
import com.renovation.ledger.ui.theme.RenovationLedgerTheme
import com.renovation.ledger.ui.trash.TrashScreen

sealed class Route(val path: String) {
    data object Overview : Route("overview")
    data object List : Route("list")
    data object Stats : Route("stats")
    data object Mine : Route("mine")
    data object BatchImport : Route("import/batch")
    data object TaxonomyManage : Route("taxonomy")
    data object Trash : Route("trash")
    data object Settings : Route("settings")

    companion object {
        const val PendingSpendPattern = "pending?tab={tab}"
        const val PaidGapPattern = "paidgap?tab={tab}"
        const val ItemDetailPattern = "item/{id}"
        const val ManualEntryPattern = "entry/manual?itemId={itemId}&editItemId={editItemId}"
        const val ConfirmEntryPattern = "entry/confirm?source={source}&itemId={itemId}"

        fun pendingSpend(tab: String = "unpaid") = "pending?tab=$tab"

        fun paidGap(tab: String = "overspend") = "paidgap?tab=$tab"

        fun itemDetail(id: String) = "item/$id"

        fun manualEntry(itemId: String = "", editItemId: String = "") =
            "entry/manual?itemId=$itemId&editItemId=$editItemId"

        fun confirmEntry(source: String, itemId: String = "") =
            "entry/confirm?source=$source&itemId=$itemId"
    }
}

private data class TabItem(
    val route: Route,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

private val tabs = listOf(
    TabItem(Route.Overview, "总览", Icons.Filled.Home, Icons.Outlined.Home),
    TabItem(
        Route.List,
        "清单",
        Icons.AutoMirrored.Filled.FormatListBulleted,
        Icons.AutoMirrored.Outlined.FormatListBulleted,
    ),
    TabItem(Route.Stats, "统计", Icons.Filled.BarChart, Icons.Outlined.BarChart),
    TabItem(Route.Mine, "我的", Icons.Filled.Person, Icons.Outlined.Person),
)

private val tabRoutes = tabs.map { it.route.path }.toSet()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RenovationAppScaffold(
    viewModel: AppShellViewModel = hiltViewModel(),
) {
    val shellState by viewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = currentRoute in tabRoutes
    val context = LocalContext.current

    LaunchedEffect(shellState.restoreMessage) {
        val message = shellState.restoreMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        viewModel.clearRestoreMessage()
    }

    RenovationLedgerTheme(
        healthLevel = shellState.healthLevel,
        healthColorEnabled = shellState.healthColorEnabled,
    ) {
        shellState.pendingAutosaveRestore?.let { summary ->
            AlertDialog(
                onDismissRequest = viewModel::dismissAutosaveRestore,
                title = { Text("发现账本数据为空") },
                text = {
                    Text(
                        "检测到自动备份（约 ${summary.itemCount} 项 / ${summary.paymentCount} 笔付款）。" +
                            "是否恢复？恢复将写入当前账本。",
                    )
                },
                confirmButton = {
                    TextButton(onClick = viewModel::confirmAutosaveRestore) {
                        Text("恢复")
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismissAutosaveRestore) {
                        Text("暂不")
                    }
                },
            )
        }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            // 子页面自带 TopAppBar 会消费状态栏 insets；外层再叠一层会导致部分机型标题栏上方空白
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (showBottomBar) {
                    // 部分国产机点击/选中时把 M3 指示器或默认涟漪渲成白底；关掉胶囊指示器，用水波纹主色 + 文字色表达选中
                    CompositionLocalProvider(
                        LocalRippleConfiguration provides RippleConfiguration(
                            color = MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            tonalElevation = 0.dp,
                        ) {
                            tabs.forEach { tab ->
                                val selected = currentRoute == tab.route.path
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = {
                                        navController.navigate(tab.route.path) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) {
                                                tab.selectedIcon
                                            } else {
                                                tab.unselectedIcon
                                            },
                                            contentDescription = tab.label,
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = tab.label,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = if (selected) {
                                                FontWeight.SemiBold
                                            } else {
                                                FontWeight.Normal
                                            },
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        indicatorColor = Color.Transparent,
                                    ),
                                )
                            }
                        }
                    }
                }
            },
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Route.Overview.path,
                // 状态栏 inset 只在此消费一次，避免与各页 TopAppBar 叠加成空白
                modifier = Modifier
                    .padding(innerPadding)
                    .statusBarsPadding(),
            ) {
                composable(Route.Overview.path) {
                    OverviewScreen(
                        onOpenPending = { tab ->
                            navController.navigate(Route.pendingSpend(tab))
                        },
                        onOpenPaidGap = { tab ->
                            navController.navigate(Route.paidGap(tab))
                        },
                        onOpenManualEntry = { navController.navigate(Route.manualEntry()) },
                        onOpenConfirmEntry = { source ->
                            navController.navigate(Route.confirmEntry(source))
                        },
                        onOpenItem = { id -> navController.navigate(Route.itemDetail(id)) },
                    )
                }
                composable(Route.List.path) {
                    BudgetListScreen(
                        onOpenItem = { id -> navController.navigate(Route.itemDetail(id)) },
                        onOpenManualEntry = { navController.navigate(Route.manualEntry()) },
                    )
                }
                composable(Route.Stats.path) {
                    StatsScreen(
                        onOpenItem = { id -> navController.navigate(Route.itemDetail(id)) },
                    )
                }
                composable(Route.Mine.path) {
                    MineScreen(
                        onOpenBatchImport = {
                            navController.navigate(Route.BatchImport.path)
                        },
                        onOpenTaxonomyManage = {
                            navController.navigate(Route.TaxonomyManage.path)
                        },
                        onOpenTrash = {
                            navController.navigate(Route.Trash.path)
                        },
                        onOpenSettings = {
                            navController.navigate(Route.Settings.path)
                        },
                    )
                }
                composable(Route.TaxonomyManage.path) {
                    TaxonomyManageScreen(
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(Route.Trash.path) {
                    TrashScreen(
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(Route.Settings.path) {
                    SettingsScreen(
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(
                    route = Route.PendingSpendPattern,
                    arguments = listOf(
                        navArgument("tab") {
                            type = NavType.StringType
                            defaultValue = "unpaid"
                        },
                    ),
                ) { entry ->
                    val tab = entry.arguments?.getString("tab").orEmpty()
                    PendingSpendScreen(
                        initialTab = tab,
                        onBack = { navController.popBackStack() },
                        onOpenItem = { id -> navController.navigate(Route.itemDetail(id)) },
                    )
                }
                composable(
                    route = Route.PaidGapPattern,
                    arguments = listOf(
                        navArgument("tab") {
                            type = NavType.StringType
                            defaultValue = "overspend"
                        },
                    ),
                ) { entry ->
                    val tab = entry.arguments?.getString("tab").orEmpty()
                    PaidGapDetailScreen(
                        initialTab = tab,
                        onBack = { navController.popBackStack() },
                        onOpenItem = { id -> navController.navigate(Route.itemDetail(id)) },
                    )
                }
                composable(Route.BatchImport.path) {
                    BatchImportConfirmScreen(
                        onBack = { navController.popBackStack() },
                        onImported = {
                            navController.popBackStack()
                            navController.navigate(Route.List.path) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
                composable(
                    route = Route.ConfirmEntryPattern,
                    arguments = listOf(
                        navArgument("source") {
                            type = NavType.StringType
                            defaultValue = "manual"
                        },
                        navArgument("itemId") {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                    ),
                ) {
                    ConfirmEntryScreen(
                        onBack = { navController.popBackStack() },
                        onSaved = { navController.popBackStack() },
                    )
                }
                composable(
                    route = Route.ManualEntryPattern,
                    arguments = listOf(
                        navArgument("itemId") {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                        navArgument("editItemId") {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                    ),
                ) {
                    ManualEntryScreen(
                        onBack = { navController.popBackStack() },
                        onSaved = { navController.popBackStack() },
                    )
                }
                composable(Route.ItemDetailPattern) { backStackEntry ->
                    val id = backStackEntry.arguments?.getString("id").orEmpty()
                    ItemDetailScreen(
                        onBack = { navController.popBackStack() },
                        onAddPayment = { itemId ->
                            navController.navigate(Route.manualEntry(itemId = itemId))
                        },
                        onDeleted = {
                            navController.popBackStack()
                        },
                    )
                }
            }
        }
    }
}
