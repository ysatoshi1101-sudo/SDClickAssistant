package com.example.sdclickassistant.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.sdclickassistant.presentation.executionprep.ScenarioExecutionPrepScreen
import com.example.sdclickassistant.presentation.scenariodetail.ScenarioDetailScreen
import com.example.sdclickassistant.presentation.scenariolist.ScenarioListScreen

private object Routes {
    const val SCENARIO_LIST = "scenario_list"
    const val SCENARIO_DETAIL = "scenario_detail/{scenarioId}"
    const val EXECUTION_PREP = "execution_prep/{scenarioId}"

    fun scenarioDetail(scenarioId: String) = "scenario_detail/$scenarioId"
    fun executionPrep(scenarioId: String) = "execution_prep/$scenarioId"
}

@Composable
fun AppNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.SCENARIO_LIST
    ) {
        composable(Routes.SCENARIO_LIST) {
            ScenarioListScreen(
                onScenarioClick = { scenario ->
                    navController.navigate(Routes.scenarioDetail(scenario.id))
                }
            )
        }

        composable(
            route = Routes.SCENARIO_DETAIL,
            arguments = listOf(navArgument("scenarioId") { type = NavType.StringType })
        ) { backStackEntry ->
            val scenarioId = backStackEntry.arguments?.getString("scenarioId") ?: return@composable
            ScenarioDetailScreen(
                scenarioId = scenarioId,
                onBackClick = { navController.popBackStack() },
                onExecuteClick = { navController.navigate(Routes.executionPrep(scenarioId)) }
            )
        }

        composable(
            route = Routes.EXECUTION_PREP,
            arguments = listOf(navArgument("scenarioId") { type = NavType.StringType })
        ) { backStackEntry ->
            val scenarioId = backStackEntry.arguments?.getString("scenarioId") ?: return@composable
            ScenarioExecutionPrepScreen(
                scenarioId = scenarioId,
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}