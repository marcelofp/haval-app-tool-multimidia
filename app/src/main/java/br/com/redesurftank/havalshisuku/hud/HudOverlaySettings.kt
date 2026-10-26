package br.com.redesurftank.havalshisuku.hud

import android.content.Context
import android.content.SharedPreferences
import br.com.redesurftank.App
import br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys

/** Reads and writes [HudOverlayModel.Config] in the app prefs. */
object HudOverlaySettings {
    /** The on/off switch this replaced; a car that had it on starts on the Navigation layout. */
    private const val LEGACY_ENABLED_KEY = "hudOverlayEnabled"

    fun prefs(): SharedPreferences =
        App.getDeviceProtectedContext().getSharedPreferences("haval_prefs", Context.MODE_PRIVATE)

    fun load(prefs: SharedPreferences = prefs()): HudOverlayModel.Config {
        val defaults = HudOverlayModel.Config()
        val layout = HudOverlayModel.Layout.fromKey(prefs.getString(SharedPreferencesKeys.HUD_LAYOUT.key, null))
            ?: if (prefs.getBoolean(LEGACY_ENABLED_KEY, false)) HudOverlayModel.Layout.NAVIGATION else defaults.layout
        return HudOverlayModel.Config(
            layout = layout,
            navigation = prefs.getBoolean(SharedPreferencesKeys.HUD_NAV_ENABLED.key, defaults.navigation),
            smallArrowWhenFar = prefs.getBoolean(SharedPreferencesKeys.HUD_NAV_SMALL_WHEN_FAR.key, defaults.smallArrowWhenFar),
            trip = prefs.getBoolean(SharedPreferencesKeys.HUD_NAV_TRIP.key, defaults.trip),
            powerBar = prefs.getBoolean(SharedPreferencesKeys.HUD_POWER_BAR.key, defaults.powerBar),
            rpmBar = prefs.getBoolean(SharedPreferencesKeys.HUD_RPM_BAR.key, defaults.rpmBar),
            navGauges = gauges(prefs, SharedPreferencesKeys.HUD_NAV_GAUGES, SharedPreferencesKeys.HUD_NAV_ARCS, defaults.navGauges),
            arGauges = gauges(prefs, SharedPreferencesKeys.HUD_AR_GAUGES, SharedPreferencesKeys.HUD_AR_ARCS, defaults.arGauges),
            chevrons = prefs.getBoolean(SharedPreferencesKeys.HUD_AR_CHEVRONS.key, defaults.chevrons),
            chevronsAlways = prefs.getBoolean(SharedPreferencesKeys.HUD_AR_CHEVRONS_ALWAYS.key, defaults.chevronsAlways),
            chevronsEvenHidden = prefs.getBoolean(SharedPreferencesKeys.HUD_CHEVRONS_EVEN_HIDDEN.key, defaults.chevronsEvenHidden),
            arPlacement = HudArArrow.Placement.fromKey(prefs.getString(SharedPreferencesKeys.HUD_AR_PLACEMENT.key, null))
                ?: defaults.arPlacement,
            mapStreets = prefs.getBoolean(SharedPreferencesKeys.HUD_AR_MAP.key, defaults.mapStreets),
            mapEdges = prefs.getBoolean(SharedPreferencesKeys.HUD_AR_MAP_EDGES.key, defaults.mapEdges),
            font = HudOverlayModel.Font.fromKey(prefs.getString(SharedPreferencesKeys.HUD_FONT.key, null)) ?: defaults.font,
            iconStyle = HudOverlayModel.IconStyle.fromKey(prefs.getString(SharedPreferencesKeys.HUD_ICON_STYLE.key, null)) ?: defaults.iconStyle,
            showTurnFromM = prefs.getInt(SharedPreferencesKeys.HUD_NAV_SHOW_TURN_FROM_M.key, defaults.showTurnFromM)
                .takeIf { it in HudOverlayModel.SHOW_TURN_FROM_OPTIONS } ?: defaults.showTurnFromM,
            colors = colors(prefs, defaults.colors)
        )
    }

    private fun colors(prefs: SharedPreferences, d: HudOverlayModel.Colors) = HudOverlayModel.Colors(
        mode = HudOverlayModel.ColorMode.fromKey(prefs.getString(SharedPreferencesKeys.HUD_COLOR_MODE.key, null)) ?: d.mode,
        drive = prefs.getInt(SharedPreferencesKeys.HUD_COLOR_DRIVE.key, d.drive),
        regen = prefs.getInt(SharedPreferencesKeys.HUD_COLOR_REGEN.key, d.regen),
        rpm = prefs.getInt(SharedPreferencesKeys.HUD_COLOR_RPM.key, d.rpm),
        street = prefs.getInt(SharedPreferencesKeys.HUD_COLOR_STREET.key, d.street),
        route = prefs.getInt(SharedPreferencesKeys.HUD_COLOR_ROUTE.key, d.route),
        text = prefs.getInt(SharedPreferencesKeys.HUD_COLOR_TEXT.key, d.text),
        chevron = prefs.getInt(SharedPreferencesKeys.HUD_COLOR_CHEVRON.key, d.chevron)
    )

    /** The indicators pref, else the older arcs on/off one (arcs, or bars), else [default]. */
    private fun gauges(
        prefs: SharedPreferences,
        key: SharedPreferencesKeys,
        legacyArcs: SharedPreferencesKeys,
        default: HudOverlayModel.Gauges
    ): HudOverlayModel.Gauges =
        HudOverlayModel.Gauges.fromKey(prefs.getString(key.key, null))
            ?: if (prefs.contains(legacyArcs.key)) {
                if (prefs.getBoolean(legacyArcs.key, false)) HudOverlayModel.Gauges.ARCS else HudOverlayModel.Gauges.BARS
            } else default

    /** Saves [config] and applies it to the HUD at once. */
    fun save(config: HudOverlayModel.Config, prefs: SharedPreferences = prefs()) {
        prefs.edit()
            .putString(SharedPreferencesKeys.HUD_LAYOUT.key, config.layout.key)
            .putBoolean(SharedPreferencesKeys.HUD_NAV_ENABLED.key, config.navigation)
            .putBoolean(SharedPreferencesKeys.HUD_NAV_SMALL_WHEN_FAR.key, config.smallArrowWhenFar)
            .putBoolean(SharedPreferencesKeys.HUD_NAV_TRIP.key, config.trip)
            .putBoolean(SharedPreferencesKeys.HUD_POWER_BAR.key, config.powerBar)
            .putBoolean(SharedPreferencesKeys.HUD_RPM_BAR.key, config.rpmBar)
            .putString(SharedPreferencesKeys.HUD_NAV_GAUGES.key, config.navGauges.key)
            .putString(SharedPreferencesKeys.HUD_AR_GAUGES.key, config.arGauges.key)
            .putBoolean(SharedPreferencesKeys.HUD_AR_CHEVRONS.key, config.chevrons)
            .putBoolean(SharedPreferencesKeys.HUD_AR_CHEVRONS_ALWAYS.key, config.chevronsAlways)
            .putBoolean(SharedPreferencesKeys.HUD_CHEVRONS_EVEN_HIDDEN.key, config.chevronsEvenHidden)
            .putString(SharedPreferencesKeys.HUD_AR_PLACEMENT.key, config.arPlacement.key)
            .putBoolean(SharedPreferencesKeys.HUD_AR_MAP.key, config.mapStreets)
            .putBoolean(SharedPreferencesKeys.HUD_AR_MAP_EDGES.key, config.mapEdges)
            .putString(SharedPreferencesKeys.HUD_FONT.key, config.font.key)
            .putString(SharedPreferencesKeys.HUD_ICON_STYLE.key, config.iconStyle.key)
            .putInt(SharedPreferencesKeys.HUD_NAV_SHOW_TURN_FROM_M.key, config.showTurnFromM)
            .putString(SharedPreferencesKeys.HUD_COLOR_MODE.key, config.colors.mode.key)
            .putInt(SharedPreferencesKeys.HUD_COLOR_DRIVE.key, config.colors.drive)
            .putInt(SharedPreferencesKeys.HUD_COLOR_REGEN.key, config.colors.regen)
            .putInt(SharedPreferencesKeys.HUD_COLOR_RPM.key, config.colors.rpm)
            .putInt(SharedPreferencesKeys.HUD_COLOR_STREET.key, config.colors.street)
            .putInt(SharedPreferencesKeys.HUD_COLOR_ROUTE.key, config.colors.route)
            .putInt(SharedPreferencesKeys.HUD_COLOR_TEXT.key, config.colors.text)
            .putInt(SharedPreferencesKeys.HUD_COLOR_CHEVRON.key, config.colors.chevron)
            .remove(LEGACY_ENABLED_KEY)
            .apply()
        HudOverlayController.setConfig(config)
    }
}
