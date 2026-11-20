package com.shavlegi.fakegps

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.location.LocationProvider
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Current mocking state, shared between the foreground service and the UI.
 * Lives in the same process, so a simple singleton StateFlow is enough.
 */
sealed interface MockState {
    data object Idle : MockState
    data class Mocking(val latitude: Double, val longitude: Double) : MockState
    /** messageKey is a string-resource name, e.g. "mock_not_selected". */
    data class Error(val messageKey: String) : MockState
}

object MockLocationState {
    private val _state = MutableStateFlow<MockState>(MockState.Idle)
    val state: StateFlow<MockState> = _state.asStateFlow()
    internal fun set(value: MockState) {
        _state.value = value
    }
}

/**
 * Foreground service that feeds a fake location into Android's official
 * mock-location system (LocationManager test provider).
 *
 * No root required and no system-level tricks — apps that check for
 * mock locations will still see this as a mock location. That is an
 * Android system limit, not something this app can work around.
 */
class MockLocationService : Service() {

    companion object {
        const val ACTION_START = "com.shavlegi.fakegps.ACTION_START"
        const val ACTION_STOP = "com.shavlegi.fakegps.ACTION_STOP"
        const val EXTRA_LAT = "lat"
        const val EXTRA_LNG = "lng"

        private const val CHANNEL_ID = "fake_gps_channel"
        private const val NOTIFICATION_ID = 1
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var mockJob: Job? = null
    private var locationManager: LocationManager? = null
    private var latitude = 0.0
    private var longitude = 0.0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                latitude = intent.getDoubleExtra(EXTRA_LAT, 0.0)
                longitude = intent.getDoubleExtra(EXTRA_LNG, 0.0)
                startMocking()
            }
            ACTION_STOP -> stopMocking()
        }
        return START_STICKY
    }

    private fun startMocking() {
        try {
            startMockingInner()
        } catch (e: Exception) {
            // Never crash the app from the service: report the failure
            // as an error state so the UI can show a message instead.
            MockLocationState.set(MockState.Error("mock_failed"))
            try {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } catch (ignored: Exception) {
            }
            stopSelf()
        }
    }

    private fun startMockingInner() {
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        locationManager = lm
        try {
            // May throw IllegalArgumentException if the test provider already
            // exists (e.g. service restarted) — that is fine, we continue.
            lm.addTestProvider(
                LocationManager.GPS_PROVIDER,
                false, false, false, false, true, true, true,
                Criteria.POWER_LOW, Criteria.ACCURACY_FINE
            )
        } catch (e: SecurityException) {
            // App is not selected as the mock location app in Developer options.
            MockLocationState.set(MockState.Error("mock_not_selected"))
            stopSelf()
            return
        } catch (e: IllegalArgumentException) {
            // Test provider already registered — continue with it.
        }
        try {
            lm.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true)
            @Suppress("DEPRECATION")
            lm.setTestProviderStatus(
                LocationManager.GPS_PROVIDER,
                LocationProvider.AVAILABLE,
                null,
                System.currentTimeMillis()
            )
        } catch (e: SecurityException) {
            MockLocationState.set(MockState.Error("mock_not_selected"))
            stopSelf()
            return
        }

        createChannel()
        val notification = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: SecurityException) {
            // Android 14+: a location-type foreground service needs a
            // granted runtime location permission. Without it the system
            // throws here — report it instead of crashing.
            MockLocationState.set(MockState.Error("location_permission"))
            stopSelf()
            return
        }

        MockLocationState.set(MockState.Mocking(latitude, longitude))
        mockJob?.cancel()
        mockJob = scope.launch {
            while (isActive) {
                pushLocation()
                delay(1000)
            }
        }
    }

    private fun pushLocation() {
        val loc = Location(LocationManager.GPS_PROVIDER).apply {
            latitude = this@MockLocationService.latitude
            longitude = this@MockLocationService.longitude
            accuracy = 5f
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        }
        try {
            locationManager?.setTestProviderLocation(LocationManager.GPS_PROVIDER, loc)
        } catch (e: Exception) {
            // Provider was removed externally — stop gracefully.
            stopMocking()
        }
    }

    private fun stopMocking() {
        mockJob?.cancel()
        mockJob = null
        try {
            locationManager?.removeTestProvider(LocationManager.GPS_PROVIDER)
        } catch (e: Exception) {
            // Provider was already gone — nothing to do.
        }
        locationManager = null
        MockLocationState.set(MockState.Idle)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        mockJob?.cancel()
        try {
            locationManager?.removeTestProvider(LocationManager.GPS_PROVIDER)
        } catch (e: Exception) {
            // Already gone.
        }
        if (MockLocationState.state.value is MockState.Mocking) {
            MockLocationState.set(MockState.Idle)
        }
        scope.cancel()
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val coords = "%.5f, %.5f".format(latitude, longitude)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text, coords))
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()
    }
}
