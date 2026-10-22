package br.com.redesurftank.havalshisuku.navigation

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import br.com.redesurftank.havalshisuku.hud.HudOverlayController
import br.com.redesurftank.havalshisuku.managers.AndroidAutoNavigationTelemetry

object CarPlayExperimentalHud {
    private val main = Handler(Looper.getMainLooper())
    private val policy = CarPlayHudPolicy()
    private var published = AndroidAutoNavigationTelemetry.inactive()
    @Volatile var enabled = false
        private set
    private val expire = Runnable { publish() }

    fun begin(id: String) = change { policy.begin(id) }

    fun setEnabled(value: Boolean) = change { policy.setEnabled(value) }

    fun connect(id: String, generation: Long) = change { policy.connect(id, generation) }

    fun disconnect(id: String, generation: Long) = change { policy.disconnect(id, generation) }

    fun end(id: String) = change { policy.end(id) }

    fun onAndroidAuto(directions: AndroidAutoNavigationTelemetry.Directions) {
        val frozen = directions.copy(lanes = directions.lanes.map { it.toList() })
        change(forwardUnchanged = true) { policy.onAndroidAuto(frozen) }
    }

    fun onCarPlay(id: String, generation: Long, raw: RawCarPlayGuidance?, receivedAtMs: Long) {
        change {
            policy.onCarPlay(id, generation, raw, receivedAtMs)
        }
    }

    private fun change(forwardUnchanged: Boolean = false, action: () -> Unit) {
        main.post {
            action()
            enabled = policy.enabled
            publish(forwardUnchanged)
            main.removeCallbacks(expire)
            policy.nextExpiryMs()?.let { deadline ->
                val delay = deadline - SystemClock.elapsedRealtime()
                if (delay > 0L) main.postDelayed(expire, delay)
            }
        }
    }

    private fun publish(forwardUnchanged: Boolean = false) {
        val next = policy.snapshot(SystemClock.elapsedRealtime())
        if (!forwardUnchanged && next == published) return
        published = next
        HudOverlayController.onDirections(next)
    }
}
