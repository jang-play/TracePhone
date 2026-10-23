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
import com.dksystems.tracephone.ui.allapps.AllAppsScreen
import com.dksystems.tracephone.ui.detail.AppDetailScreen
import com.dksystems.tracephone.ui.history.BillingCycleHistoryScreen
import com.dksystems.tracephone.ui.hourly.HourlyUsageScreen
import com.dksystems.tracephone.ui.navigation.TracePhoneDestinations
import com.dksystems.tracephone.ui.permission.PermissionScreen
import com.dksystems.tracephone.ui.permission.PermissionViewModel
import com.dksystems.tracephone.ui.settings.SettingsScreen
import com.dksystems.tracephone.ui.update.AppUpdateViewModel
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
    permissionViewModel: PermissionViewModel = hiltViewModel(),
    appUpdateViewModel: AppUpdateViewModel = hiltViewModel()
) {
    val isPermissionGranted by permissionViewModel.isGranted.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    // 앱 밖에서 바뀌는 것들을 앱이 앞으로 올 때마다 다시 읽는다.
    // 사용 정보 권한은 시스템 설정에서, 새 버전 기록은 서버에서 바뀌고, 둘 다 바뀐 것을
    // 알려 주지 않는다. 관찰자를 붙이는 순간 현재 상태가 다시 전달되므로 첫 실행도 덮는다.
    //
    // **화면이 아니라 여기서 묻는다.** 화면마다 걸면 어느 화면을 거쳤는지에 따라 확인
    // 여부가 갈리고, 화면이 처음 만들어질 때만 발사되는 효과로는 따뜻한 프로세스에서
    // 영원히 다시 묻지 않는다.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionViewModel.refresh()
                appUpdateViewModel.checkIfStale()
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
                },
                onAllAppsSelected = { period, unit ->
                    navController.navigate(TracePhoneDestinations.allApps(period, unit))
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
            route = TracePhoneDestinations.ALL_APPS_ROUTE,
            arguments = listOf(
                navArgument(TracePhoneDestinations.ARG_START_DATE) { type = NavType.StringType },
                navArgument(TracePhoneDestinations.ARG_END_DATE) { type = NavType.StringType },
                navArgument(TracePhoneDestinations.ARG_PERIOD_UNIT) { type = NavType.StringType }
            )
        ) {
            AllAppsScreen(onNavigateBack = { navController.popBackStack() })
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
