package br.com.redesurftank.havalshisuku.hud

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.redesurftank.havalshisuku.managers.AndroidAutoNavigationTelemetry.Directions
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Renders HUD frames with the real [HudOverlayRenderer] to PNGs, to look at a change without the
 * car (owner, 2026-10-25). Each frame is the settings preview's picture: the road mock, the
 * native HUD mock and the Impulse overlay, band y 40..200. Run on an emulator:
 *
 *   gradlew :app:connectedDebugAndroidTest
 *   adb shell run-as br.com.redesurftank.havalshisuku ls files/hud-renders
 *
 * then pull each file with `adb exec-out run-as ... cat files/hud-renders/<name>.png`.
 */
@RunWith(AndroidJUnit4::class)
class HudRenderTest {
    private val out: File by lazy {
        File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "hud-renders").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    private fun save(name: String, bitmap: Bitmap) {
        File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun turn(turn: String, meters: Int, street: String, remainingM: Int = 4_800, remainingS: Int = 720) = Directions(
        active = true,
        distance = if (meters >= 1000) "${meters / 1000}.${meters % 1000 / 100} km" else "$meters m",
        distanceM = meters, turn = turn, street = street, remainingM = remainingM, remainingS = remainingS
    )

    private fun render(name: String, config: HudOverlayModel.Config, speedKmh: Int, dirs: Directions, power: Int = 20, rpm: Int? = null) {
        save(name, HudOverlayRenderer().preview(config, PreviewScenario(speedKmh, dirs, power, rpm), width = 960, height = 320))
    }

    @Test
    fun renderHudFrames() {
        val ar = HudOverlayModel.Config(layout = HudOverlayModel.Layout.AR, chevronsAlways = false)
        val nav = HudOverlayModel.Config(layout = HudOverlayModel.Layout.NAVIGATION)
        // 3. Next turn hidden by distance: the trip on two lines at the trip line's size and place.
        render("3a_ar_hidden_turn", ar.copy(showTurnFromM = 500), 64, turn("TURN_RIGHT", 650, "Av. Paulista"))
        render("3b_ar_hidden_turn_hours", ar.copy(showTurnFromM = 500), 96, turn("TURN_RIGHT", 4_200, "Rod. Pres. Dutra", 123_400, 94 * 60))
        render("3c_nav_hidden_turn", nav.copy(showTurnFromM = 1000), 96, turn("OFF_RAMP_RIGHT", 2_400, "Rod. Anhanguera", 48_300, 85 * 60))
        render("3d_ar_shown_for_reference", ar, 64, turn("TURN_RIGHT", 650, "Av. Paulista"))
        // 1. Chevrons over the street line: a sharp left with a long street, along the climb.
        for (m in listOf(48, 40, 30, 20, 8)) {
            render("1_chevrons_left_${m}m", ar, 30, turn("SHARP_LEFT", m, "R. Manuel da Nóbrega"))
        }
        // 2. Gentle exits: the 45 degree icon (no fork glyph, no 90 degree turn).
        render("2a_ar_off_ramp_right", ar, 80, turn("OFF_RAMP_RIGHT", 150, "Saída 12 - Centro"))
        render("2b_ar_fork_left", ar, 80, turn("FORK_LEFT", 150, "Marginal Pinheiros"))
        render("2c_ar_keep_right", ar, 80, turn("KEEP_RIGHT", 150, "Av. Brasil"))
        render("2d_nav_off_ramp_right", nav, 80, turn("OFF_RAMP_RIGHT", 150, "Saída 12 - Centro"))
        render("2e_ar_merge_no_side", ar, 80, turn("MERGE", 300, "Rod. Anhanguera"))
    }
}
