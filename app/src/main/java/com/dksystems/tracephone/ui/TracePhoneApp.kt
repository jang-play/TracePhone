package com.dksystems.tracephone.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.dksystems.tracephone.ui.detail.AppDetailScreen
import com.dksystems.tracephone.ui.history.BillingCycleHistoryScreen
import com.dksystems.tracephone.ui.hourly.HourlyUsageScreen
import com.dksystems.tracephone.ui.navigation.TracePhoneDestinations
import com.dksystems.tracephone.ui.permission.PermissionScreen
import com.dksystems.tracephone.ui.permission.PermissionViewModel
import com.dksystems.tracephone.ui.settings.SettingsScreen
import com.dksystems.tracephone.ui.summary.UsageSummaryScreen

/**
 * 앱 최상위 화면.
 *
 * 권한이 없으면 어떤 화면도 의미 있는 값을 보여줄 수 없으므로,
 * 내비게이션 그래프 자체를 권한 뒤에 둔다.
 * 권한 상태는 사용자가 설정에서 돌아올 때마다 다시 확인해야 하므로 `ON_RESUME`에서 갱신한다.
 */
@Composable
fun TracePhoneApp(
    modifier: Modifier = Modifier,
    permissionViewModel: PermissionViewModel = hiltViewModel()
) {
    val isPermissionGranted by permissionViewModel.isGranted.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionViewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (!isPermissionGranted) {
        PermissionScreen(modifier = modifier)
        return
    }

    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = TracePhoneDestinations.SUMMARY_ROUTE,
        modifier = modifier
    ) {
        composable(TracePhoneDestinations.SUMMARY_ROUTE) {
            UsageSummaryScreen(
                onAppSelected = { uid, period ->
                    navController.navigate(TracePhoneDestinations.appDetail(uid, period))
                },
                onSettingsSelected = {
                    navController.navigate(TracePhoneDestinations.SETTINGS_ROUTE)
                },
                onCycleHistorySelected = {
                    navController.navigate(TracePhoneDestinations.CYCLE_HISTORY_ROUTE)
                }
            )
        }

        composable(TracePhoneDestinations.SETTINGS_ROUTE) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onCycleHistorySelected = {
                    navController.navigate(TracePhoneDestinations.CYCLE_HISTORY_ROUTE)
                }
            )
        }

        composable(TracePhoneDestinations.CYCLE_HISTORY_ROUTE) {
            BillingCycleHistoryScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(
            route = TracePhoneDestinations.APP_DETAIL_ROUTE,
            arguments = listOf(
                navArgument(TracePhoneDestinations.ARG_UID) { type = NavType.IntType },
                navArgument(TracePhoneDestinations.ARG_START_DATE) { type = NavType.StringType },
                navArgument(TracePhoneDestinations.ARG_END_DATE) { type = NavType.StringType }
            )
        ) {
            AppDetailScreen(
                onNavigateBack = { navController.popBackStack() },
                onDateSelected = { uid, date ->
                    navController.navigate(TracePhoneDestinations.hourlyUsage(uid, date))
                }
            )
        }

        composable(
            route = TracePhoneDestinations.HOURLY_USAGE_ROUTE,
            arguments = listOf(
                navArgument(TracePhoneDestinations.ARG_UID) { type = NavType.IntType },
                navArgument(TracePhoneDestinations.ARG_DATE) { type = NavType.StringType }
            )
        ) {
            HourlyUsageScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}
