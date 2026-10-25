package br.com.redesurftank.havalshisuku.hud

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import androidx.core.graphics.PathParser
import br.com.redesurftank.havalshisuku.map.LocalRoad
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
        // Not wiped here: JUnit makes one instance per test, each would delete the others' files.
        File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "hud-renders").apply { mkdirs() }
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

    /** The last section: the map flag (route blue, small) beside the white trip/icon flag on the left. */
    @Test
    fun renderDestination() {
        val ar = HudOverlayModel.Config(layout = HudOverlayModel.Layout.AR)
        for (m in listOf(150, 80, 30)) render("4_ar_destination_${m}m", ar, 30, turn("DESTINATION_RIGHT", m, "R. Oscar Freire"))
        // The map view: a straight street with a cross street, the destination on the right curb.
        val roads = listOf(
            LocalRoad(floatArrayOf(0f, -30f, 0f, 120f, 0f, 400f), 1f, "R. Oscar Freire"),
            LocalRoad(floatArrayOf(-200f, 120f, 0f, 120f, 200f, 120f), 0.8f, "R. Augusta")
        )
        val map = ar.copy(mapStreets = true)
        for (m in listOf(150, 80, 30)) {
            val frame = HudOverlayModel.frameFor(map, turn("DESTINATION_RIGHT", m, "R. Oscar Freire"), 20, null, streets = roads, speedKmh = 30.0)
            // The HUD layer is transparent (draw() clears it): composite it over a dark backdrop.
            val layer = Bitmap.createBitmap(960, 320, Bitmap.Config.ARGB_8888)
            Canvas(layer).apply {
                scale(2f, 2f)
                translate(0f, -40f)
                HudOverlayRenderer().draw(this, frame, 0L)
            }
            val bitmap = Bitmap.createBitmap(960, 320, Bitmap.Config.ARGB_8888)
            Canvas(bitmap).apply {
                drawColor(Color.rgb(16, 18, 24))
                drawBitmap(layer, 0f, 0f, null)
            }
            save("4_map_destination_${m}m", bitmap)
        }
    }

    /**
     * Our HUD maneuver icons beside Google's (Material Symbols, the set Google Maps uses), pushed
     * beforehand into files/gicons/<name>.svg (owner, 2026-10-25). Each row: our icon as the
     * Navegação layout draws it at 150 m, then Google's at the same size.
     */
    @Test
    fun renderIconComparison() {
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "gicons")
        if (!dir.isDirectory) return
        val nav = HudOverlayModel.Config(layout = HudOverlayModel.Layout.NAVIGATION)
        val rows = listOf(
            "TURN_RIGHT" to "turn_right", "TURN_LEFT" to "turn_left", "SLIGHT_RIGHT" to "turn_slight_right",
            "SLIGHT_LEFT" to "turn_slight_left", "SHARP_RIGHT" to "turn_sharp_right", "STRAIGHT" to "straight",
            "U_TURN_LEFT" to "u_turn_left", "FORK_RIGHT" to "fork_right", "OFF_RAMP_RIGHT" to "ramp_right",
            "MERGE" to "merge", "ROUNDABOUT" to "roundabout_right", "DESTINATION" to "sports_score"
        )
        val cellW = 200
        val cellH = 160
        val sheet = Bitmap.createBitmap(cellW * 2 + 260, cellH * rows.size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        canvas.drawColor(Color.rgb(16, 18, 24))
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.LTGRAY; textSize = 22f }
        val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        rows.forEachIndexed { i, (token, google) ->
            val top = i * cellH
            canvas.drawText(token, 12f, top + cellH / 2f + 8f, label)
            // Ours: the Navegação big arrow (y 40..200 band, x 0..180), as the HUD draws it.
            val dirs = if (token == "ROUNDABOUT") turn(token, 150, "").copy(exitNumber = 2, turnAngle = 90, roundaboutClockwise = false)
            else turn(token, 150, "")
            val frame = HudOverlayRenderer().preview(nav, PreviewScenario(40, dirs, 0, null), width = 480, height = 160)
            canvas.drawBitmap(frame, Rect(0, 0, 180, 160), Rect(260, top, 260 + cellW - 20, top + cellH), null)
            // Google's: white, 96 px, centred in its cell.
            val svg = File(dir, "$google.svg").takeIf { it.isFile }?.readText() ?: return@forEachIndexed
            val path = android.graphics.Path()
            Regex("d=\"([^\"]+)\"").findAll(svg).forEach { path.addPath(PathParser.createPathFromPathData(it.groupValues[1])) }
            val size = 96f
            path.transform(Matrix().apply { setTranslate(0f, 960f); postScale(size / 960f, size / 960f) })
            canvas.save()
            canvas.translate(260f + cellW + (cellW - size) / 2, top + (cellH - size) / 2)
            canvas.drawPath(path, white)
            canvas.restore()
        }
        save("5_icons_ours_vs_google", sheet)
    }
}
