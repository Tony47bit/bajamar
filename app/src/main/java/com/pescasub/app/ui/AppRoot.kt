package com.pescasub.app.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pescasub.app.AppContainer
import com.pescasub.app.ui.conditions.ConditionsViewModel
import com.pescasub.app.ui.conditions.ForecastScreen
import com.pescasub.app.ui.home.HomeScreen
import com.pescasub.app.ui.logbook.EditSessionScreen
import com.pescasub.app.ui.logbook.EditSessionViewModel
import com.pescasub.app.ui.logbook.LogbookScreen
import com.pescasub.app.ui.logbook.LogbookViewModel
import com.pescasub.app.ui.logbook.SessionDetailScreen
import com.pescasub.app.ui.logbook.SessionDetailViewModel
import com.pescasub.app.ui.logbook.StatsScreen
import com.pescasub.app.ui.map.MapScreen
import com.pescasub.app.ui.map.MapViewModel
import com.pescasub.app.ui.profile.ProfileScreen
import com.pescasub.app.ui.profile.ProfileViewModel

enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    HOME("home", "Inicio", Icons.Filled.Home),
    MAP("map", "Mapa", Icons.Filled.Map),
    LOGBOOK("logbook", "Bitácora", Icons.Filled.Book),
    FORECAST("forecast", "Previsión", Icons.Filled.Timeline),
    PROFILE("profile", "Perfil", Icons.Filled.Person)
}

private const val ROUTE_DETAIL = "session/{id}"
private const val ROUTE_EDIT = "session/edit/{id}"
private const val ROUTE_STATS = "stats"
private val ID_ARG = listOf(navArgument("id") { type = NavType.LongType })

private fun NavHostController.goToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun AppRoot(container: AppContainer) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    // ViewModels compartidos entre pestañas: viven mientras viva la actividad.
    val conditionsVm: ConditionsViewModel = viewModel(factory = ConditionsViewModel.factory(container))
    val logbookVm: LogbookViewModel = viewModel(factory = LogbookViewModel.factory(container))

    val isTab = Tab.entries.any { it.route == route }

    Scaffold(
        // Cada pantalla gestiona el margen superior (TopAppBar / statusBarsPadding). En las pestañas la barra
        // inferior ya respeta la barra de navegación; en el resto la respeta el propio Scaffold.
        contentWindowInsets = if (isTab) WindowInsets(0, 0, 0, 0) else WindowInsets.navigationBars,
        bottomBar = {
            if (isTab) {
                NavigationBar {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = { nav.goToTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(nav, startDestination = Tab.HOME.route, modifier = Modifier.padding(padding)) {
            composable(Tab.HOME.route) {
                HomeScreen(conditionsVm, onOpenForecast = { nav.goToTab(Tab.FORECAST.route) })
            }
            composable(Tab.MAP.route) {
                val mapVm: MapViewModel = viewModel(factory = MapViewModel.factory(container))
                MapScreen(mapVm, conditionsVm, onSpotChosen = { nav.goToTab(Tab.HOME.route) })
            }
            composable(Tab.LOGBOOK.route) {
                LogbookScreen(
                    vm = logbookVm,
                    onOpen = { id -> nav.navigate("session/$id") },
                    onNew = { nav.navigate("session/edit/0") },
                    onStats = { nav.navigate(ROUTE_STATS) }
                )
            }
            composable(Tab.FORECAST.route) {
                ForecastScreen(conditionsVm, container.weatherProvider.attribution)
            }
            composable(Tab.PROFILE.route) {
                val vm: ProfileViewModel = viewModel(factory = ProfileViewModel.factory(container))
                ProfileScreen(vm, container.weatherProvider.attribution)
            }
            composable(ROUTE_STATS) {
                StatsScreen(logbookVm, onBack = { nav.popBackStack() })
            }
            composable(ROUTE_DETAIL, arguments = ID_ARG) { entry ->
                val id = entry.arguments?.getLong("id") ?: 0L
                val vm: SessionDetailViewModel = viewModel(factory = SessionDetailViewModel.factory(container, id))
                SessionDetailScreen(
                    vm = vm,
                    onBack = { nav.popBackStack() },
                    onEdit = { nav.navigate("session/edit/$it") }
                )
            }
            composable(ROUTE_EDIT, arguments = ID_ARG) { entry ->
                val id = entry.arguments?.getLong("id") ?: 0L
                val vm: EditSessionViewModel = viewModel(factory = EditSessionViewModel.factory(container, id))
                EditSessionScreen(vm, onClose = { nav.popBackStack() })
            }
        }
    }
}
