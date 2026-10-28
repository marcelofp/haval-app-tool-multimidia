package br.com.redesurftank.havalshisuku.managers
import br.com.redesurftank.havalshisuku.navigation.CarPlayHudPolicy
object AndroidAutoClusterController {
 var policy = CarPlayHudPolicy()
 val updates = mutableListOf<AndroidAutoNavigationTelemetry.Directions>()
 fun onNavigationUpdate(d: AndroidAutoNavigationTelemetry.Directions, now: Long = android.os.SystemClock.elapsedRealtime()) {
  updates.add(d); policy.onAndroidAuto(d)
 }
}
object AndroidAutoTelemetryKeys { const val SESSION_ACTIVE = "active"; const val SESSION_STOPPED = "stopped" }
