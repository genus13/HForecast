package dev.weather.hforecast

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.weather.ui.HForecastApp
import dev.weather.core.NotificationPreferences

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            val appTheme by viewModel.theme.collectAsStateWithLifecycle()
            var permissionRequested by remember { mutableStateOf(false) }
            var pendingNotificationPreferences by remember { mutableStateOf<NotificationPreferences?>(null) }
            var notificationPermissionGranted by remember {
                mutableStateOf(
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                        ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.POST_NOTIFICATIONS,
                        ) == PackageManager.PERMISSION_GRANTED,
                )
            }
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions(),
            ) { result ->
                viewModel.onLocationPermission(result.values.any { it })
            }
            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { granted ->
                notificationPermissionGranted = granted
                if (granted) pendingNotificationPreferences?.let(viewModel::updateNotificationPreferences)
                pendingNotificationPreferences = null
            }

            fun hasLocationPermission(): Boolean =
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                    ) == PackageManager.PERMISSION_GRANTED

            fun requestPermission() {
                permissionRequested = true
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                    ),
                )
            }

            LaunchedEffect(Unit) {
                if (hasLocationPermission()) {
                    viewModel.onLocationPermission(true)
                } else if (!permissionRequested) {
                    requestPermission()
                }
            }

            HForecastApp(
                state = state,
                appTheme = appTheme,
                onRefresh = viewModel::refresh,
                onRequestLocationPermission = { requestPermission() },
                onSelectLocation = viewModel::selectLocation,
                onUseCurrentLocation = {
                    if (hasLocationPermission()) viewModel.useCurrentLocation() else requestPermission()
                },
                notificationPermissionGranted = notificationPermissionGranted,
                onNotificationSettingsChange = { preferences ->
                    if ((preferences.enabled || preferences.auroraAlertsEnabled) &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        !notificationPermissionGranted
                    ) {
                        pendingNotificationPreferences = preferences
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        viewModel.updateNotificationPreferences(preferences)
                    }
                },
                onThemeChange = viewModel::updateTheme,
            )
        }
    }
}
