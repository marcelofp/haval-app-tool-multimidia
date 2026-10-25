package br.com.redesurftank.havalshisuku.hud

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.redesurftank.havalshisuku.ui.components.HudNavigationSettingsDialog
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Screenshots of the HUD settings dialog, one per tab, to review it without the car (owner,
 * 2026-10-25). Set the emulator to the head unit's screen first:
 * `adb shell wm size 1920x720` and `adb shell wm density 160`. Saved next to HudRenderTest's.
 */
@RunWith(AndroidJUnit4::class)
class HudSettingsRenderTest {
    @get:Rule
    val compose = createComposeRule()

    private fun save(name: String, bitmap: Bitmap) {
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "hud-renders").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun shoot(name: String) {
        compose.waitForIdle()
        Thread.sleep(800) // the preview's crossfade
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { save(name, it) }
    }

    @Test
    fun renderSettingsDialogArLayout() {
        compose.setContent {
            var config by remember {
                mutableStateOf(HudOverlayModel.Config(layout = HudOverlayModel.Layout.AR, chevronsAlways = true, showTurnFromM = 1000, mapStreets = true))
            }
            HudNavigationSettingsDialog(layout = HudOverlayModel.Layout.AR, config = config, onChange = { config = it }, onDismiss = {})
        }
        for ((i, tab) in listOf("Navegação", "Mapa", "Indicadores", "Estilo").withIndex()) {
            compose.onNodeWithText(tab).performClick()
            shoot("7_settings_ar_${i + 1}_${tab.lowercase()}")
        }
    }

    @Test
    fun renderSettingsDialogNavigationLayout() {
        compose.setContent {
            var config by remember { mutableStateOf(HudOverlayModel.Config(layout = HudOverlayModel.Layout.NAVIGATION)) }
            HudNavigationSettingsDialog(layout = HudOverlayModel.Layout.NAVIGATION, config = config, onChange = { config = it }, onDismiss = {})
        }
        for ((i, tab) in listOf("Navegação", "Indicadores", "Estilo").withIndex()) {
            compose.onNodeWithText(tab).performClick()
            shoot("7_settings_navigation_${i + 1}_${tab.lowercase()}")
        }
    }
}
