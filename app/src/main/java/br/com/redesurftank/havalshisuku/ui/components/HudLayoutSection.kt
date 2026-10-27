package br.com.redesurftank.havalshisuku.ui.components

import android.content.SharedPreferences
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import kotlin.math.roundToInt
import androidx.compose.material.icons.filled.Add
import br.com.redesurftank.havalshisuku.hud.PreviewFocus
import br.com.redesurftank.havalshisuku.hud.previewFocusIndex
import br.com.redesurftank.havalshisuku.hud.previewScenarios
import kotlinx.coroutines.delay
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import br.com.redesurftank.havalshisuku.hud.HudArArrow
import br.com.redesurftank.havalshisuku.hud.HudOverlayModel
import br.com.redesurftank.havalshisuku.hud.HudOverlayController
import br.com.redesurftank.havalshisuku.hud.HudOverlayRenderer
import br.com.redesurftank.havalshisuku.hud.HudOverlaySettings

/**
 * Telas tab: pick the HUD style. Each style is a card with a preview drawn by the same renderer
 * the HUD uses (road scene + the car's own HUD + Impulse's overlay), so the picture matches the
 * windshield. The gear adjusts any style; the first change there makes "Personalizado" (owner,
 * 2026-10-27), listed right after Original.
 */
@Composable
fun HudLayoutSection(modifier: Modifier = Modifier, prefs: SharedPreferences = remember { HudOverlaySettings.prefs() }) {
    var preset by remember { mutableStateOf(HudOverlaySettings.preset(prefs)) }
    var custom by remember { mutableStateOf(HudOverlaySettings.custom(prefs)) }
    var customBase by remember { mutableStateOf(HudOverlaySettings.customBase(prefs)) }
    fun refresh() {
        preset = HudOverlaySettings.preset(prefs)
        custom = HudOverlaySettings.custom(prefs)
        customBase = HudOverlaySettings.customBase(prefs)
    }
    /** Style whose gear was tapped (its settings dialog is open), or null. */
    var settingsFor by remember { mutableStateOf<HudOverlayModel.Preset?>(null) }

    StyledCard(modifier = modifier) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column {
                Text("HUD", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Escolha um estilo para o HUD do para-brisa. O Impulse só desenha nos cantos livres, sem cobrir a velocidade. Na engrenagem, ajuste qualquer estilo: suas mudanças viram o Personalizado.",
                    color = ImpTokens.TextSecondary,
                    fontSize = 14.sp
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val cards = buildList {
                    add(HudOverlayModel.Preset.ORIGINAL)
                    if (custom != null) add(HudOverlayModel.Preset.CUSTOM)
                    addAll(HudOverlayModel.Preset.FIXED)
                }
                for (p in cards) {
                    HudLayoutCard(
                        title = presetTitle(p),
                        subtitle = presetSubtitle(p, customBase),
                        preview = p.config ?: custom!!,
                        selected = preset == p,
                        onSelect = {
                            HudOverlaySettings.select(p, prefs)
                            refresh()
                        },
                        onSettings = if (p == HudOverlayModel.Preset.ORIGINAL) null else ({ settingsFor = p })
                    )
                }
            }
        }
    }

    settingsFor?.let { p ->
        HudStyleSettings(
            start = p,
            prefs = prefs,
            onSaved = { refresh() },
            onDismiss = {
                settingsFor = null
                refresh()
            }
        )
    }
}

/** The card and dialog name of [preset]. */
private fun presetTitle(preset: HudOverlayModel.Preset): String = when (preset) {
    HudOverlayModel.Preset.ORIGINAL -> "Original"
    HudOverlayModel.Preset.CUSTOM -> "Personalizado"
    HudOverlayModel.Preset.MAP -> "Mapa 3D"
    HudOverlayModel.Preset.BASIC -> "Navegação Básica"
    HudOverlayModel.Preset.DISCREET -> "Discreto"
}

private fun presetSubtitle(preset: HudOverlayModel.Preset, customBase: HudOverlayModel.Preset?): String = when (preset) {
    HudOverlayModel.Preset.ORIGINAL -> "Só o HUD do carro"
    HudOverlayModel.Preset.CUSTOM -> "Suas escolhas, a partir de ${presetTitle(customBase ?: HudOverlayModel.Preset.MAP)}"
    HudOverlayModel.Preset.MAP -> "Ruas reais e a rota em azul · usa a internet do celular"
    HudOverlayModel.Preset.BASIC -> "Seta grande da manobra e barras de potência"
    HudOverlayModel.Preset.DISCREET -> "Só perto das manobras, tudo em azul"
}

/**
 * The gear of [start]: its options in the settings dialog. A fixed style is only shown until the
 * first change, which saves Personalizado from it and selects it (asking first when that replaces
 * another Personalizado); Personalizado's own changes save at once. [onSaved] after each save.
 */
@Composable
private fun HudStyleSettings(
    start: HudOverlayModel.Preset,
    prefs: SharedPreferences,
    onSaved: () -> Unit,
    onDismiss: () -> Unit
) {
    val base = remember {
        if (start == HudOverlayModel.Preset.CUSTOM) HudOverlaySettings.customBase(prefs) ?: HudOverlayModel.Preset.MAP else start
    }
    var editingCustom by remember { mutableStateOf(start == HudOverlayModel.Preset.CUSTOM) }
    var config by remember { mutableStateOf(HudOverlaySettings.configOf(start, prefs)) }
    /** A first change that would replace the existing Personalizado, waiting for the owner's OK. */
    var replacing by remember { mutableStateOf<HudOverlayModel.Config?>(null) }
    var discarding by remember { mutableStateOf(false) }
    fun commit(next: HudOverlayModel.Config) {
        config = next
        editingCustom = true
        HudOverlaySettings.saveCustom(next, base, prefs)
        onSaved()
    }

    HudNavigationSettingsDialog(
        title = if (editingCustom) "Personalizado · a partir de ${presetTitle(base)}" else presetTitle(start),
        config = config,
        defaults = base.config!!,
        fixed = !editingCustom,
        onChange = { next ->
            when {
                editingCustom -> commit(next)
                HudOverlaySettings.customBase(prefs) != null -> replacing = next
                else -> commit(next)
            }
        },
        onDismiss = onDismiss,
        onDiscard = if (editingCustom) ({ discarding = true }) else null
    )

    replacing?.let { next ->
        ConfirmDialog(
            title = "Substituir o Personalizado?",
            text = "Você já tem um Personalizado. Mudar o ${presetTitle(start)} cria um novo Personalizado no lugar dele.",
            confirm = "Substituir",
            onConfirm = {
                replacing = null
                commit(next)
            },
            onDismiss = { replacing = null }
        )
    }
    if (discarding) {
        ConfirmDialog(
            title = "Descartar o Personalizado?",
            text = "Suas escolhas são apagadas e o HUD volta para o ${presetTitle(base)}.",
            confirm = "Descartar",
            onConfirm = {
                discarding = false
                HudOverlaySettings.discardCustom(prefs)
                onDismiss()
            },
            onDismiss = { discarding = false }
        )
    }
}

@Composable
private fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ImpTokens.Container,
        titleContentColor = Color.White,
        textContentColor = ImpTokens.TextSecondary,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Text(text, fontSize = 15.sp) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = ImpTokens.Accent, contentColor = Color.White)
            ) { Text(confirm, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancelar", fontWeight = FontWeight.Bold) }
        }
    )
}

/** How long each sample scenario stays up in a preview before the next one fades in. */
private const val PREVIEW_CYCLE_MS = 3_000L
/** After a settings change: the focused example is held this long before cycling again. */
private const val PREVIEW_FOCUS_HOLD_MS = 4_000L
/** ...and the change shows this long after the preview moved to it (owner: ~300 ms). */
private const val PREVIEW_APPLY_DELAY_MS = 300L

/**
 * The layout preview, cycling through its sample scenarios (town turn, highway exit, roundabout
 * regen...) with a crossfade. Bitmaps are rendered once per [config] by the HUD's own renderer.
 */
@Composable
private fun HudPreview(
    config: HudOverlayModel.Config,
    description: String,
    modifier: Modifier = Modifier,
    focus: Int? = null,
    focusTick: Int = 0
) {
    val frames = remember(config) {
        val renderer = HudOverlayRenderer()
        previewScenarios(config.layout).map { renderer.preview(config, it).asImageBitmap() }
    }
    var index by remember { mutableIntStateOf(0) }
    // A settings change: jump to the example that shows it (the dialog applies it ~300 ms later).
    LaunchedEffect(focusTick) {
        if (focusTick > 0 && focus != null) index = focus.coerceIn(0, frames.size - 1)
    }
    LaunchedEffect(frames.size, focusTick) {
        // Hold the focused example a while, then cycle again.
        if (focusTick > 0) delay(PREVIEW_FOCUS_HOLD_MS)
        while (true) {
            delay(PREVIEW_CYCLE_MS)
            index = (index + 1) % frames.size
        }
    }
    Crossfade(targetState = index.coerceIn(0, frames.size - 1), animationSpec = tween(600), label = "hudPreview", modifier = modifier) { i ->
        Image(
            bitmap = frames[i],
            contentDescription = description,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().aspectRatio(3f)
        )
    }
}

@Composable
private fun HudLayoutCard(
    title: String,
    subtitle: String,
    preview: HudOverlayModel.Config,
    selected: Boolean,
    onSelect: () -> Unit,
    onSettings: (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = Modifier
            .width(280.dp)
            .clip(shape)
            .border(BorderStroke(if (selected) 2.dp else 1.dp, if (selected) ImpTokens.Accent else ImpTokens.TrackOff), shape)
            .clickable(onClick = onSelect)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HudPreview(
            config = preview,
            description = "Prévia do estilo $title",
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    if (selected) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Selecionado",
                            tint = ImpTokens.Accent,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Text(subtitle, color = ImpTokens.TextSecondary, fontSize = 12.sp)
            }
            if (onSettings != null) {
                Box(
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onSettings),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Configurar estilo $title",
                        tint = ImpTokens.Accent,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    }
}

/** The settings dialog's fixed height (fits the 720 px screen between the status bar and the dock). */
private val DIALOG_HEIGHT = 344.dp

/**
 * Tabs of the settings dialog. Three rows per tab, so the dialog stays short with no gap over the
 * buttons (owner). Related settings share a row (navigation and its extras; the turn warning's
 * switch and when; the two indicators). Manobra holds how the maneuver is drawn, the arrow or the
 * 3D map, and that one's own options.
 */
private enum class HudSettingsTab(val title: String) { NAVIGATION("Navegação"), MANEUVER("Manobra"), BARS("Indicadores"), STYLE("Estilo"), COLORS("Cores") }

/** The parts the Cores tab colours (owner, 2026-10-26), with how to read and change each. */
private enum class HudColorItem(
    val label: String,
    /** Whether the part is on the HUD with [config] (only those are offered). */
    val shown: (HudOverlayModel.Config) -> Boolean,
    val get: (HudOverlayModel.Colors) -> Int,
    val set: (HudOverlayModel.Colors, Int) -> HudOverlayModel.Colors,
    val focus: PreviewFocus?
) {
    TEXT("Texto", { true }, { it.text }, { c, v -> c.copy(text = v) }, null),
    DRIVE("EV", { it.powerBar && it.gauges != HudOverlayModel.Gauges.NONE },
        { it.drive }, { c, v -> c.copy(drive = v) }, PreviewFocus.GAUGES),
    REGEN("Regen", { it.powerBar && it.gauges != HudOverlayModel.Gauges.NONE },
        { it.regen }, { c, v -> c.copy(regen = v) }, PreviewFocus.GAUGES),
    RPM("RPM", { it.rpmBar && it.gauges != HudOverlayModel.Gauges.NONE },
        { it.rpm }, { c, v -> c.copy(rpm = v) }, PreviewFocus.GAUGES),
    STREET("Ruas", { it.layout == HudOverlayModel.Layout.AR && it.navigation && it.mapStreets },
        { it.street }, { c, v -> c.copy(street = v) }, PreviewFocus.ARROW),
    ROUTE("Rota", { it.layout == HudOverlayModel.Layout.AR && it.navigation },
        { it.route }, { c, v -> c.copy(route = v) }, PreviewFocus.ARROW),
    CHEVRON("Aviso de curva", { it.navigation && it.chevrons },
        { it.chevron }, { c, v -> c.copy(chevron = v) }, PreviewFocus.CHEVRONS)
}

/**
 * Wide two-pane dialog for the 1920x720 head unit: the options on the left split into tabs so
 * each fits without scrolling, live preview on the right. (A tall single column got clipped on
 * this screen, with the bar switches drawn on top of each other.) [defaults] are the style's own
 * options (what "padrão" means in Cores); [fixed] is a style not changed yet, whose first change
 * makes Personalizado. [onDiscard] (Personalizado only) offers to remove it.
 */
/** Internal (not private) so androidTest/HudSettingsRenderTest can open it on an emulator. */
@Composable
internal fun HudNavigationSettingsDialog(
    title: String,
    config: HudOverlayModel.Config,
    defaults: HudOverlayModel.Config,
    fixed: Boolean,
    onChange: (HudOverlayModel.Config) -> Unit,
    onDismiss: () -> Unit,
    onDiscard: (() -> Unit)? = null
) {
    var tab by remember { mutableStateOf(HudSettingsTab.NAVIGATION) }
    var colorItem by remember { mutableStateOf(HudColorItem.TEXT) }
    // The preview shows [shown]: a change applies at once, but the preview first moves to the
    // example where it is visible and shows it ~300 ms later (owner), so it reads as a change.
    // It follows [config], so a change that isn't accepted (Personalizado not replaced) never shows.
    var shown by remember { mutableStateOf(config) }
    var focus by remember { mutableStateOf<Int?>(null) }
    var focusTick by remember { mutableIntStateOf(0) }
    var requested by remember { mutableStateOf<PreviewFocus?>(null) }
    LaunchedEffect(config) {
        if (config == shown) return@LaunchedEffect
        val what = requested
        requested = null
        if (what != null && config.layout == shown.layout) {
            focus = previewFocusIndex(config.layout, what)
            focusTick++
            delay(PREVIEW_APPLY_DELAY_MS)
        }
        shown = config
    }
    fun change(next: HudOverlayModel.Config, what: PreviewFocus? = null) {
        requested = what
        onChange(next)
    }
    val layout = config.layout
    val map = layout == HudOverlayModel.Layout.AR
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            // Fixed size (owner): the same box on every tab, wide enough for the descriptions.
            modifier = Modifier.fillMaxWidth(0.72f).height(DIALOG_HEIGHT).padding(start = 16.dp, top = 16.dp, end = 28.dp, bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = ImpTokens.Container),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.5.dp, ImpTokens.Accent)
        ) {
            Column(modifier = Modifier.fillMaxHeight().padding(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp), modifier = Modifier.weight(1f)) {
                    // Left: one tab of options at a time, the buttons under it (near the driver).
                    Column(modifier = Modifier.weight(1.3f).fillMaxHeight()) {
                        HudTabs(selected = tab, tabs = HudSettingsTab.entries, onSelect = { tab = it })
                        Spacer(Modifier.height(8.dp))
                        // Scrolls if a tab ever outgrows the box.
                        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        when (tab) {
                            HudSettingsTab.NAVIGATION -> {
                                // Navigation and what comes with it on one row (still separate settings),
                                // so every tab has three rows and the dialog stays short (owner).
                                SwitchTogglesRow(
                                    "Mostrar navegação",
                                    "Do app de navegação, com:",
                                    config.navigation,
                                    listOf("Distância e tempo" to config.trip),
                                    info = "Mostra a próxima manobra enquanto o app de navegação está guiando: a seta ou o mapa, a distância, as faixas e a rua. Distância e tempo: até o destino, ex.: 4.8km · 12m.",
                                    onCheckedChange = { change(config.copy(navigation = it)) },
                                    onToggle = { _, on -> change(config.copy(trip = on)) }
                                )
                                ChoiceRow(
                                    "Mostrar manobra a partir de",
                                    "Antes disso, só distância e tempo.",
                                    listOf("Sempre", "200 m", "300 m", "500 m", "1 km"),
                                    HudOverlayModel.SHOW_TURN_FROM_OPTIONS.indexOf(config.showTurnFromM).coerceAtLeast(0),
                                    enabled = config.navigation,
                                    info = "Mais longe que isso, a seta, a distância, a rua e as faixas da próxima manobra ficam ocultas; a distância e o tempo até o destino aparecem em duas linhas. O mapa 3D continua na tela."
                                ) { change(config.copy(showTurnFromM = HudOverlayModel.SHOW_TURN_FROM_OPTIONS[it]), PreviewFocus.FAR) }
                                // Both turn-warning settings on one row: the switch, and when (owner: two settings).
                                // Aviso de curva (owner, 2026-10-26): near the turns / also a still arrow
                                // while the next maneuver shows / that arrow even while it is hidden.
                                SwitchChoiceRow(
                                    "Aviso de curva",
                                    "Setas para o lado da curva.",
                                    config.chevrons,
                                    listOf("Perto da curva", "Junto com a manobra", "O tempo todo"),
                                    when {
                                        !config.chevronsAlways -> 0
                                        !config.chevronsEvenHidden -> 1
                                        else -> 2
                                    },
                                    enabled = config.navigation,
                                    info = "Perto da curva: de 100 m uma seta na diagonal do lado da curva; de 50 m ela sobe e mais setas surgem atrás, em fila na borda de cima a partir de 10 m (em curva suave seguem pela diagonal). Na rotatória, só perto da saída. Junto com a manobra: além disso, uma seta parada junto aos arcos enquanto a próxima manobra aparece no HUD, seguindo a rua e se inclinando para a curva ao chegar perto. O tempo todo: essa seta parada aparece mesmo com a próxima manobra oculta (mais longe que \"Mostrar manobra a partir de\"). Somem com o carro parado.",
                                    onCheckedChange = { change(config.copy(chevrons = it), PreviewFocus.CHEVRONS) },
                                    onSelect = { change(config.copy(chevronsAlways = it >= 1, chevronsEvenHidden = it == 2), PreviewFocus.CHEVRONS) }
                                )
                            }
                            HudSettingsTab.MANEUVER -> {
                                // Seta or Mapa 3D (owner, 2026-10-27: the 3D arrows alone are no longer an option).
                                ChoiceRow(
                                    "Visual da manobra",
                                    "Seta grande ou mapa 3D.",
                                    listOf("Seta", "Mapa 3D"),
                                    if (map) 1 else 0,
                                    enabled = config.navigation,
                                    info = "Seta: a seta da manobra à esquerda, grande perto da curva. Mapa 3D: ruas reais em volta do carro em cinza e a rota em azul sobre elas, em perspectiva, sob a velocidade. O mapa usa o GPS e o OpenStreetMap, baixado pela internet do celular (também à frente enquanto dirige); enquanto carrega, aparece uma seta 3D."
                                ) {
                                    val toMap = it == 1
                                    change(config.copy(layout = if (toMap) HudOverlayModel.Layout.AR else HudOverlayModel.Layout.NAVIGATION, mapStreets = toMap))
                                }
                                if (!map) {
                                    OptionRow(
                                        "Seta menor longe",
                                        "Pequena até faltar 1 km.",
                                        config.smallArrowWhenFar,
                                        enabled = config.navigation,
                                        info = "A seta da manobra fica pequena até faltar 1 km e cresce ao chegar perto."
                                    ) { change(config.copy(smallArrowWhenFar = it), PreviewFocus.FAR) }
                                } else {
                                    ChoiceRow(
                                        "Posição do mapa",
                                        "Ao centro ou à direita.",
                                        listOf("Ao centro", "À direita"),
                                        if (config.arPlacement == HudArArrow.Placement.RIGHT) 1 else 0,
                                        enabled = config.navigation,
                                        info = "Ao centro: sob a velocidade do carro. À direita: menor, entre as linhas do assistente de faixa do HUD."
                                    ) { change(config.copy(arPlacement = if (it == 1) HudArArrow.Placement.RIGHT else HudArArrow.Placement.CENTER), PreviewFocus.ARROW) }
                                    ChoiceRow(
                                        "Ruas do mapa",
                                        "Preenchidas ou só as bordas.",
                                        listOf("Preenchidas", "Contorno"),
                                        if (config.mapEdges) 1 else 0,
                                        enabled = config.navigation,
                                        info = "Preenchidas: as ruas em cinza claro. Contorno: só as linhas das bordas das ruas, deixando o HUD mais limpo e a rota azul em destaque."
                                    ) { change(config.copy(mapEdges = it == 1), PreviewFocus.ARROW) }
                                }
                            }
                            HudSettingsTab.BARS -> {
                                ChoiceRow(
                                    "Potência e rotação",
                                    "Como mostrar os indicadores.",
                                    listOf("Nenhum", "Barras", "Arcos"),
                                    HudOverlayModel.Gauges.entries.indexOf(config.gauges),
                                    info = "Barras: nos cantos de baixo do HUD. Arcos: em volta da velocidade do carro."
                                ) { change(config.copy(gauges = HudOverlayModel.Gauges.entries[it]), PreviewFocus.GAUGES) }
                                // The two indicators as toggles on one row (still two settings).
                                ToggleChipsRow(
                                    "Indicadores",
                                    "Quais mostrar.",
                                    listOf("Potência" to config.powerBar, "Rotação" to config.rpmBar),
                                    enabled = config.gauges != HudOverlayModel.Gauges.NONE,
                                    info = "Potência: azul acelerando, verde regenerando. Rotação: só com o motor a combustão ligado, vermelha a partir de 5 mil rpm."
                                ) { i, on ->
                                    change(if (i == 0) config.copy(powerBar = on) else config.copy(rpmBar = on), PreviewFocus.GAUGES)
                                }
                            }
                            HudSettingsTab.COLORS -> {
                                val colorful = config.colors.mode == HudOverlayModel.ColorMode.COLORFUL
                                ChoiceRow(
                                    "Cores",
                                    "Escolhidas ou tudo em azul.",
                                    listOf("Colorido", "Azul"),
                                    HudOverlayModel.ColorMode.entries.indexOf(config.colors.mode),
                                    info = "Colorido: escolha a cor de cada parte abaixo (o padrão são as cores do estilo; só aparecem as partes ligadas nas outras abas). Azul: tudo no mesmo azul, inclusive o texto."
                                ) { change(config.copy(colors = config.colors.copy(mode = HudOverlayModel.ColorMode.entries[it]))) }
                                // Only the parts that are on (owner): a part switched off elsewhere isn't offered.
                                val items = HudColorItem.entries.filter { it.shown(config) }
                                val item = colorItem.takeIf { it in items } ?: items.first()
                                // The parts and their opacity stay open in Azul too (it only sets the colour).
                                ColorItemsRow(config.colors, items, item, enabled = true) { colorItem = it }
                                val current = item.get(config.colors)
                                val default = item.get(defaults.colors)
                                SwatchRow(
                                    selected = current,
                                    default = default,
                                    enabled = colorful
                                ) { v ->
                                    // A new colour keeps the part's opacity.
                                    val kept = (current and HudOverlayModel.ALPHA_MASK) or (v and HudOverlayModel.RGB_MASK)
                                    change(config.copy(colors = item.set(config.colors, kept)), item.focus)
                                }
                                OpacityRow(HudOverlayModel.opacityOf(current), HudOverlayModel.opacityOf(default)) { o ->
                                    change(config.copy(colors = item.set(config.colors, HudOverlayModel.withOpacity(current, o))), item.focus)
                                }
                            }
                            HudSettingsTab.STYLE -> {
                                ChoiceRow(
                                    "Fonte",
                                    "Texto no HUD.",
                                    listOf("Leve", "Normal", "Condensada"),
                                    HudOverlayModel.Font.entries.indexOf(config.font),
                                    info = "Leve é a mais discreta no para-brisa; Normal, a mais fácil de ler sob sol forte; Condensada tem o peso da Normal em menos largura."
                                ) { change(config.copy(font = HudOverlayModel.Font.entries[it])) }
                                ChoiceRow(
                                    "Ícones das manobras",
                                    "Básicos ou os do Google Maps.",
                                    listOf("Básico", "Google"),
                                    HudOverlayModel.IconStyle.entries.indexOf(config.iconStyle),
                                    enabled = config.navigation,
                                    info = "Básico: setas cheias, grossas. Google: os ícones do Google Maps (Material Symbols), com ícones próprios para curva fechada, bifurcação, saída e retorno. A rotatória com a saída desenhada e a bandeira do destino são as mesmas nos dois."
                                ) { change(config.copy(iconStyle = HudOverlayModel.IconStyle.entries[it])) }
                            }
                        }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(onClick = onDismiss) {
                                Text("Fechar", fontWeight = FontWeight.Bold)
                            }
                            if (onDiscard != null) {
                                OutlinedButton(onClick = onDiscard) {
                                    Text("Descartar", fontWeight = FontWeight.Bold)
                                }
                            }
                            DemoDriveButton(config)
                        }
                    }
                    // Right: what the HUD will show with these options.
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        // The title sits over the preview, so the options start right under the tabs.
                        Text(
                            title,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                        HudPreview(
                            config = shown,
                            description = "Prévia com estas opções",
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
                            focus = focus,
                            focusTick = focusTick
                        )
                        Text(
                            if (fixed) "Prévia: cidade, rodovia e rotatória. Mudar qualquer opção cria o Personalizado."
                            else "Prévia: cidade, rodovia e rotatória, com estas opções.",
                            color = ImpTokens.TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DemoDriveButton(config: HudOverlayModel.Config, modifier: Modifier = Modifier) {
    var running by remember { mutableStateOf(HudOverlayController.isDemoDriveRunning()) }
    var stopped by remember { mutableStateOf(HudOverlayController.isVehicleStopped()) }
    DisposableEffect(Unit) {
        val listener = { on: Boolean, carStopped: Boolean ->
            running = on
            stopped = carStopped
        }
        HudOverlayController.addDemoUiListener(listener)
        onDispose { HudOverlayController.removeDemoUiListener(listener) }
    }
    val enabled = stopped || running
    Button(
        onClick = { HudOverlayController.setDemoDrive(!running, config) },
        enabled = enabled,
        modifier = modifier,
        // Amber while it plays (owner): the settings stay live on it, so it reads as "on air".
        colors = ButtonDefaults.buttonColors(
            containerColor = if (running) ImpTokens.Attention else ImpTokens.Accent,
            contentColor = if (running) ImpTokens.OnAccent else Color.White,
            disabledContainerColor = ImpTokens.TrackOff,
            disabledContentColor = ImpTokens.TextMuted
        )
    ) {
        Text(
            if (running) "Parar demonstração no HUD" else "Iniciar demonstração no HUD",
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun HudTabs(selected: HudSettingsTab, tabs: List<HudSettingsTab>, onSelect: (HudSettingsTab) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (t in tabs) {
            val on = t == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (on) ImpTokens.Accent.copy(alpha = 0.18f) else Color.Transparent)
                    .border(1.dp, if (on) ImpTokens.Accent else ImpTokens.TrackOff, RoundedCornerShape(8.dp))
                    .clickable { onSelect(t) }
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Text(
                    t.title,
                    color = if (on) Color.White else ImpTokens.TextSecondary,
                    fontSize = 14.sp,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

/**
 * Title and a one-line description, readable at arm's length; the longer explanation sits behind
 * the (i) and opens under the row.
 */
@Composable
private fun OptionText(title: String, description: String, enabled: Boolean, info: String?, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = if (enabled) Color.White else ImpTokens.TextMuted, fontSize = 16.sp)
            if (info != null) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = "Mais informações sobre $title",
                    tint = if (open) ImpTokens.Accent else ImpTokens.TextSecondary,
                    modifier = Modifier.padding(start = 8.dp).size(22.dp).clip(RoundedCornerShape(11.dp)).clickable { open = !open }
                )
            }
        }
        Text(
            description,
            color = if (enabled) ImpTokens.TextSecondary else ImpTokens.TextMuted,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
        if (open && info != null) {
            Text(info, color = ImpTokens.TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** An on/off option; tapping anywhere on the row toggles it. */
@Composable
private fun OptionRow(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean = true,
    info: String? = null,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 4.dp)
    ) {
        OptionText(title, description, enabled, info, Modifier.weight(1f))
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = ImpTokens.Accent)
        )
    }
}

/** An on/off option with its own choice beside it (the choice greys out while it is off). */
@Composable
private fun SwitchChoiceRow(
    title: String,
    description: String,
    checked: Boolean,
    options: List<String>,
    selected: Int,
    enabled: Boolean = true,
    info: String? = null,
    onCheckedChange: (Boolean) -> Unit,
    onSelect: (Int) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        OptionText(title, description, enabled, info, Modifier.weight(1f))
        Chips(options, { it == selected }, enabled && checked) { onSelect(it) }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = ImpTokens.Accent),
            modifier = Modifier.padding(start = 10.dp)
        )
    }
}

/** An on/off option with independent extras as chips beside it (greyed out while it is off). */
@Composable
private fun SwitchTogglesRow(
    title: String,
    description: String,
    checked: Boolean,
    toggles: List<Pair<String, Boolean>>,
    info: String? = null,
    onCheckedChange: (Boolean) -> Unit,
    onToggle: (Int, Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        OptionText(title, description, true, info, Modifier.weight(1f))
        Chips(toggles.map { it.first }, { toggles[it].second }, checked) { onToggle(it, !toggles[it].second) }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = ImpTokens.Accent),
            modifier = Modifier.padding(start = 10.dp)
        )
    }
}

/** Independent on/off options as chips on one row. */
@Composable
private fun ToggleChipsRow(
    title: String,
    description: String,
    toggles: List<Pair<String, Boolean>>,
    enabled: Boolean = true,
    info: String? = null,
    onToggle: (Int, Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        OptionText(title, description, enabled, info, Modifier.weight(1f))
        Chips(toggles.map { it.first }, { toggles[it].second }, enabled) { onToggle(it, !toggles[it].second) }
    }
}

/** The chips used by the rows: [isOn] lit, tapping one calls [onTap]. */
@Composable
private fun Chips(labels: List<String>, isOn: (Int) -> Boolean, enabled: Boolean, onTap: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        labels.forEachIndexed { i, label ->
            val on = isOn(i)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (on && enabled) ImpTokens.Accent.copy(alpha = 0.25f) else Color.Transparent)
                    .border(1.dp, if (on && enabled) ImpTokens.Accent else ImpTokens.TrackOff, RoundedCornerShape(16.dp))
                    .clickable(enabled = enabled) { onTap(i) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    label,
                    color = if (!enabled) ImpTokens.TextMuted else if (on) Color.White else ImpTokens.TextSecondary,
                    fontSize = 14.sp,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

/** The parts to colour, each a chip with its colour; the selected one is what the swatches change. */
@Composable
private fun ColorItemsRow(
    colors: HudOverlayModel.Colors,
    items: List<HudColorItem>,
    selected: HudColorItem,
    enabled: Boolean,
    onSelect: (HudColorItem) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        // Tight and on one line: on the car the font is wider and "Seta de direção" wrapped (owner).
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            for (item in items) {
                val on = item == selected
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (on && enabled) ImpTokens.Accent.copy(alpha = 0.25f) else Color.Transparent)
                        .border(1.dp, if (on && enabled) ImpTokens.Accent else ImpTokens.TrackOff, RoundedCornerShape(16.dp))
                        .clickable(enabled = enabled) { onSelect(item) }
                        .padding(horizontal = 9.dp, vertical = 8.dp)
                ) {
                    Box(
                        Modifier.size(14.dp).clip(RoundedCornerShape(7.dp))
                            .background(Color(item.get(colors)).copy(alpha = if (enabled) 1f else 0.35f))
                    )
                    Text(
                        item.label,
                        color = if (!enabled) ImpTokens.TextMuted else if (on) Color.White else ImpTokens.TextSecondary,
                        fontSize = 14.sp,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
            }
        }
    }
}

/**
 * The colours to pick from ([HudOverlayModel.COLOR_SWATCHES]; [default]'s swatch is marked), then
 * a custom one (owner, 2026-10-26): it shows the part's colour when that isn't a swatch.
 */
@Composable
private fun SwatchRow(selected: Int, default: Int, enabled: Boolean, onPick: (Int) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    if (picking) {
        CustomColorDialog(selected, onDismiss = { picking = false }) {
            picking = false
            onPick(it)
        }
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
    ) {
        for ((name, value) in HudOverlayModel.COLOR_SWATCHES) {
            val on = HudOverlayModel.sameRgb(value, selected)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(17.dp))
                    .border(if (on) 3.dp else 1.dp, if (on && enabled) Color.White else ImpTokens.TrackOff, RoundedCornerShape(17.dp))
                    .padding(4.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(Color(value).copy(alpha = if (enabled) 1f else 0.35f))
                    .clickable(enabled = enabled) { onPick(value) }
                    .semantics { contentDescription = name }
            ) {
                // The colour the HUD always had for this part.
                if (HudOverlayModel.sameRgb(value, default)) {
                    Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(Color.Black.copy(alpha = 0.55f)))
                }
            }
        }
        val custom = HudOverlayModel.COLOR_SWATCHES.none { HudOverlayModel.sameRgb(it.second, selected) }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(17.dp))
                .border(if (custom) 3.dp else 1.dp, if (custom && enabled) Color.White else ImpTokens.TrackOff, RoundedCornerShape(17.dp))
                .padding(4.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(
                    if (custom) androidx.compose.ui.graphics.SolidColor(Color(selected or HudOverlayModel.ALPHA_MASK))
                    else androidx.compose.ui.graphics.Brush.sweepGradient(HUE_STOPS),
                    alpha = if (enabled) 1f else 0.35f
                )
                .clickable(enabled = enabled) { picking = true }
                .semantics { contentDescription = "Cor personalizada" }
        ) {
            if (!custom) Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
    }
}

/**
 * The part's opacity, 10..100 % (owner, 2026-10-26). Saved when the finger lifts, so the preview
 * isn't redrawn on every step of the drag. [default] is marked by the reset chip.
 */
@Composable
private fun OpacityRow(opacity: Float, default: Float, onChange: (Float) -> Unit) {
    var value by remember(opacity) { mutableFloatStateOf(opacity) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text("Opacidade", color = Color.White, fontSize = 14.sp, modifier = Modifier.width(90.dp))
        androidx.compose.material3.Slider(
            value = value,
            onValueChange = { value = it },
            onValueChangeFinished = { onChange(value) },
            valueRange = HudOverlayModel.MIN_OPACITY..1f,
            colors = androidx.compose.material3.SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = ImpTokens.Accent,
                inactiveTrackColor = ImpTokens.TrackOff
            ),
            modifier = Modifier.weight(1f)
        )
        Text(
            "${(value * 100).roundToInt()} %",
            color = ImpTokens.TextSecondary,
            fontSize = 14.sp,
            modifier = Modifier.width(52.dp).padding(start = 8.dp)
        )
        val atDefault = kotlin.math.abs(value - default) < 0.01f
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, if (atDefault) ImpTokens.TrackOff else ImpTokens.Accent, RoundedCornerShape(14.dp))
                .clickable(enabled = !atDefault) { value = default; onChange(default) }
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text("Padrão", color = if (atDefault) ImpTokens.TextMuted else Color.White, fontSize = 13.sp)
        }
    }
}

/** The hue wheel's colours (red round to red), for the custom button and the hue slider. */
private val HUE_STOPS = listOf(0f, 60f, 120f, 180f, 240f, 300f, 360f)
    .map { Color(android.graphics.Color.HSVToColor(floatArrayOf(it, 1f, 1f))) }

/**
 * A colour of one's own: hue, saturation and brightness, the result beside the title. Brightness
 * starts at 30 %: on the HUD black is see-through, so darker would barely show.
 */
@Composable
private fun CustomColorDialog(initial: Int, onDismiss: () -> Unit, onApply: (Int) -> Unit) {
    val start = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(initial, it) } }
    var h by remember { mutableFloatStateOf(start[0]) }
    var s by remember { mutableFloatStateOf(start[1]) }
    var v by remember { mutableFloatStateOf(start[2].coerceIn(CUSTOM_MIN_BRIGHTNESS, 1f)) }
    fun hsv(hh: Float, ss: Float, vv: Float) = android.graphics.Color.HSVToColor(floatArrayOf(hh, ss, vv))
    val color = hsv(h, s, v)
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.width(520.dp),
            colors = CardDefaults.cardColors(containerColor = ImpTokens.Container),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.5.dp, ImpTokens.Accent)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Cor personalizada", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(20.dp)).background(Color(color)))
                }
                GradientSlider("Matiz", h, 0f..360f, androidx.compose.ui.graphics.Brush.horizontalGradient(HUE_STOPS)) { h = it }
                GradientSlider(
                    "Saturação", s, 0f..1f,
                    androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color(hsv(h, 0f, v)), Color(hsv(h, 1f, v))))
                ) { s = it }
                GradientSlider(
                    "Brilho", v, CUSTOM_MIN_BRIGHTNESS..1f,
                    androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color(hsv(h, s, CUSTOM_MIN_BRIGHTNESS)), Color(hsv(h, s, 1f))))
                ) { v = it }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 4.dp)) {
                    OutlinedButton(onClick = onDismiss) { Text("Cancelar", fontWeight = FontWeight.Bold) }
                    Button(
                        onClick = { onApply(color) },
                        colors = ButtonDefaults.buttonColors(containerColor = ImpTokens.Accent, contentColor = Color.White)
                    ) { Text("Aplicar", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

private const val CUSTOM_MIN_BRIGHTNESS = 0.3f

/** A slider over its own gradient (the track is the gradient). */
@Composable
private fun GradientSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    track: androidx.compose.ui.graphics.Brush,
    onChange: (Float) -> Unit
) {
    Column {
        Text(label, color = ImpTokens.TextSecondary, fontSize = 13.sp)
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().height(32.dp)) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp).height(10.dp).clip(RoundedCornerShape(5.dp)).background(track))
            androidx.compose.material3.Slider(
                value = value,
                onValueChange = onChange,
                valueRange = range,
                colors = androidx.compose.material3.SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent
                )
            )
        }
    }
}

/** A pick among a few options, as chips beside the title. */
@Composable
private fun ChoiceRow(
    title: String,
    description: String,
    options: List<String>,
    selected: Int,
    enabled: Boolean = true,
    info: String? = null,
    onSelect: (Int) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        OptionText(title, description, enabled, info, Modifier.weight(1f))
        Chips(options, { it == selected }, enabled, onSelect)
    }
}
