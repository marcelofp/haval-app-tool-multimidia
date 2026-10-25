package br.com.redesurftank.havalshisuku.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
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
 * Telas tab: pick what the physical HUD shows. Each layout is a card with a preview drawn by the
 * same renderer the HUD uses (road scene + the car's own HUD + Impulse's overlay), so the picture
 * matches the windshield. Layouts with options get a gear.
 */
@Composable
fun HudLayoutSection(modifier: Modifier = Modifier) {
    var config by remember { mutableStateOf(HudOverlaySettings.load()) }
    /** Layout whose gear was tapped (its settings dialog is open), or null. */
    var settingsFor by remember { mutableStateOf<HudOverlayModel.Layout?>(null) }
    val apply: (HudOverlayModel.Config) -> Unit = {
        config = it
        HudOverlaySettings.save(it)
    }

    StyledCard(modifier = modifier) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column {
                Text("HUD", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Escolha o que aparece no HUD do para-brisa. O Impulse só desenha nos cantos livres, sem cobrir a velocidade.",
                    color = ImpTokens.TextSecondary,
                    fontSize = 14.sp
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HudLayoutCard(
                    title = "Original",
                    subtitle = "Só o HUD do carro",
                    preview = config.copy(layout = HudOverlayModel.Layout.ORIGINAL),
                    selected = config.layout == HudOverlayModel.Layout.ORIGINAL,
                    onSelect = { apply(config.copy(layout = HudOverlayModel.Layout.ORIGINAL)) }
                )
                HudLayoutCard(
                    title = "Navegação",
                    subtitle = "Manobras do Android Auto, potência e rotação",
                    preview = config.copy(layout = HudOverlayModel.Layout.NAVIGATION),
                    selected = config.layout == HudOverlayModel.Layout.NAVIGATION,
                    onSelect = { apply(config.copy(layout = HudOverlayModel.Layout.NAVIGATION)) },
                    onSettings = { settingsFor = HudOverlayModel.Layout.NAVIGATION }
                )
                HudLayoutCard(
                    title = "Navegação AR",
                    subtitle = "Seta azul em perspectiva que vira a manobra ao chegar",
                    preview = config.copy(layout = HudOverlayModel.Layout.AR),
                    selected = config.layout == HudOverlayModel.Layout.AR,
                    onSelect = { apply(config.copy(layout = HudOverlayModel.Layout.AR)) },
                    onSettings = { settingsFor = HudOverlayModel.Layout.AR }
                )
            }
        }
    }

    settingsFor?.let { layout ->
        HudNavigationSettingsDialog(
            layout = layout,
            config = config,
            onChange = apply,
            onDismiss = { settingsFor = null }
        )
    }
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
            .width(300.dp)
            .clip(shape)
            .border(BorderStroke(if (selected) 2.dp else 1.dp, if (selected) ImpTokens.Accent else ImpTokens.TrackOff), shape)
            .clickable(onClick = onSelect)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HudPreview(
            config = preview,
            description = "Prévia do layout $title",
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
                        contentDescription = "Configurar layout $title",
                        tint = ImpTokens.Accent,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    }
}

/** Tabs of the Navegação settings dialog. */
/** The settings dialog's fixed height (fits the 720 px screen between the status bar and the dock). */
private val DIALOG_HEIGHT = 344.dp

/**
 * Three rows per tab, so the dialog stays short with no gap over the buttons (owner). Related
 * settings share a row (navigation and its extras; the chevrons' switch and when; the two
 * indicators). Mapa is for Navegação AR only.
 */
private enum class HudSettingsTab(val title: String) { NAVIGATION("Navegação"), ARROW("Mapa"), BARS("Indicadores"), STYLE("Estilo") }

/**
 * Wide two-pane dialog for the 1920x720 head unit: the options on the left split into tabs so
 * each fits without scrolling, live preview on the right. (A tall single column got clipped on
 * this screen, with the bar switches drawn on top of each other.)
 */
/** Internal (not private) so androidTest/HudSettingsRenderTest can open it on an emulator. */
@Composable
internal fun HudNavigationSettingsDialog(
    layout: HudOverlayModel.Layout,
    config: HudOverlayModel.Config,
    onChange: (HudOverlayModel.Config) -> Unit,
    onDismiss: () -> Unit
) {
    var tab by remember { mutableStateOf(HudSettingsTab.NAVIGATION) }
    // The preview shows [shown]: a change is saved at once, but the preview first moves to the
    // example where it is visible and applies it ~300 ms later (owner), so it reads as a change.
    var shown by remember { mutableStateOf(config) }
    var focus by remember { mutableStateOf<Int?>(null) }
    var focusTick by remember { mutableIntStateOf(0) }
    var pending by remember { mutableStateOf<HudOverlayModel.Config?>(null) }
    LaunchedEffect(focusTick) {
        val next = pending ?: return@LaunchedEffect
        delay(PREVIEW_APPLY_DELAY_MS)
        shown = next
        pending = null
    }
    fun change(next: HudOverlayModel.Config, what: PreviewFocus? = null) {
        onChange(next)
        if (what == null) {
            shown = next
        } else {
            focus = previewFocusIndex(layout, what)
            pending = next
            focusTick++
        }
    }
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
                        HudTabs(
                            selected = tab,
                            tabs = HudSettingsTab.entries.filter { it != HudSettingsTab.ARROW || layout == HudOverlayModel.Layout.AR },
                            onSelect = { tab = it }
                        )
                        Spacer(Modifier.height(8.dp))
                        // Scrolls if a tab ever outgrows the box.
                        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        when (tab) {
                            HudSettingsTab.NAVIGATION -> {
                                // Navigation and what comes with it on one row (still separate settings),
                                // so every tab has three rows and the dialog stays short (owner).
                                val extras = buildList {
                                    add("Distância e tempo" to config.trip)
                                    if (layout != HudOverlayModel.Layout.AR) add("Seta menor longe" to config.smallArrowWhenFar)
                                }
                                SwitchTogglesRow(
                                    "Mostrar navegação",
                                    "Do Android Auto, com:",
                                    config.navigation,
                                    extras,
                                    info = "Mostra a próxima manobra enquanto o Android Auto está guiando: seta, distância, faixas e a rua. Distância e tempo: até o destino, ex.: 4.8km · 12m." +
                                        (if (layout != HudOverlayModel.Layout.AR) " Seta menor longe: pequena até faltar 1 km para a manobra." else ""),
                                    onCheckedChange = { change(config.copy(navigation = it)) },
                                    onToggle = { i, on ->
                                        if (i == 0) change(config.copy(trip = on)) else change(config.copy(smallArrowWhenFar = on), PreviewFocus.FAR)
                                    }
                                )
                                ChoiceRow(
                                    "Mostrar próxima manobra",
                                    "A partir de que distância.",
                                    listOf("Sempre", "200 m", "300 m", "500 m", "1 km"),
                                    HudOverlayModel.SHOW_TURN_FROM_OPTIONS.indexOf(config.showTurnFromM).coerceAtLeast(0),
                                    enabled = config.navigation,
                                    info = "Mais longe que isso, a seta, a distância, a rua e as faixas da próxima manobra ficam ocultas; a distância e o tempo até o destino aparecem em duas linhas. O mapa e a seta da Navegação AR continuam na tela."
                                ) { change(config.copy(showTurnFromM = HudOverlayModel.SHOW_TURN_FROM_OPTIONS[it]), PreviewFocus.FAR) }
                                // Both chevron settings on one row: the switch, and when (owner: two settings).
                                SwitchChoiceRow(
                                    "Setas de aproximação",
                                    "Azuis, para o lado da curva.",
                                    config.chevrons,
                                    listOf("Perto da curva", "Sempre"),
                                    if (config.chevronsAlways) 1 else 0,
                                    enabled = config.navigation,
                                    info = "Perto da curva: de 100 m uma seta azul na diagonal do lado da curva; de 50 m ela sobe e mais setas surgem atrás, em fila na borda de cima a partir de 10 m (em curva suave seguem pela diagonal). Na rotatória, só perto da saída. Sempre: além disso, uma seta parada que gira junto aos arcos seguindo a rua e se inclina para a curva ao chegar perto. Somem com o carro parado.",
                                    onCheckedChange = { change(config.copy(chevrons = it), PreviewFocus.CHEVRONS) },
                                    onSelect = { change(config.copy(chevronsAlways = it == 1), PreviewFocus.CHEVRONS) }
                                )
                            }
                            HudSettingsTab.ARROW -> {
                                ChoiceRow(
                                    "Posição do mapa",
                                    "Ao centro ou à direita.",
                                    listOf("Ao centro", "À direita"),
                                    if (config.arPlacement == HudArArrow.Placement.RIGHT) 1 else 0,
                                    enabled = config.navigation,
                                    info = "Ao centro: sob a velocidade do carro. À direita: menor, entre as linhas do assistente de faixa do HUD."
                                ) { change(config.copy(arPlacement = if (it == 1) HudArArrow.Placement.RIGHT else HudArArrow.Placement.CENTER), PreviewFocus.ARROW) }
                                OptionRow(
                                    "Mapa completo",
                                    "Ruas reais em cinza, rota em azul.",
                                    config.mapStreets,
                                    enabled = config.navigation,
                                    info = "Troca as setas pelo mapa: ruas reais em volta do carro em cinza e a rota em azul sobre elas, até onde dá para desenhar. Usa o GPS e o OpenStreetMap (baixado pela internet do celular, também à frente enquanto dirige)."
                                ) { change(config.copy(mapStreets = it)) }
                                ChoiceRow(
                                    "Ruas do mapa",
                                    "Preenchidas ou só as bordas.",
                                    listOf("Preenchidas", "Contorno"),
                                    if (config.mapEdges) 1 else 0,
                                    enabled = config.navigation && config.mapStreets,
                                    info = "Preenchidas: as ruas em cinza claro. Contorno: só as linhas das bordas das ruas, deixando o HUD mais limpo e a rota azul em destaque."
                                ) { change(config.copy(mapEdges = it == 1)) }
                            }
                            HudSettingsTab.BARS -> {
                                // Each layout keeps its own: Navegação bars, Navegação AR arcs by default.
                                val gauges = config.copy(layout = layout).gauges
                                ChoiceRow(
                                    "Potência e rotação",
                                    "Como mostrar os indicadores.",
                                    listOf("Nenhum", "Barras", "Arcos"),
                                    HudOverlayModel.Gauges.entries.indexOf(gauges),
                                    info = "Barras: nos cantos de baixo do HUD. Arcos: em volta da velocidade do carro."
                                ) { change(config.withGauges(layout, HudOverlayModel.Gauges.entries[it]), PreviewFocus.GAUGES) }
                                // The two indicators as toggles on one row (still two settings).
                                ToggleChipsRow(
                                    "Indicadores",
                                    "Quais mostrar.",
                                    listOf("Potência" to config.powerBar, "Rotação" to config.rpmBar),
                                    enabled = gauges != HudOverlayModel.Gauges.NONE,
                                    info = "Potência: azul acelerando, verde regenerando. Rotação: só com o motor a combustão ligado, vermelha a partir de 5 mil rpm."
                                ) { i, on ->
                                    change(if (i == 0) config.copy(powerBar = on) else config.copy(rpmBar = on), PreviewFocus.GAUGES)
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
                                    "Nossos ou os do Google Maps.",
                                    listOf("Clássicos", "Google"),
                                    HudOverlayModel.IconStyle.entries.indexOf(config.iconStyle),
                                    enabled = config.navigation,
                                    info = "Clássicos: setas cheias, grossas. Google: os ícones do Google Maps (Material Symbols), com ícones próprios para curva fechada, bifurcação, saída e retorno. A rotatória com a saída desenhada e a bandeira do destino são as mesmas nos dois."
                                ) { change(config.copy(iconStyle = HudOverlayModel.IconStyle.entries[it])) }
                            }
                        }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(onClick = onDismiss) {
                                Text("Fechar", fontWeight = FontWeight.Bold)
                            }
                            DemoDriveButton(layout)
                        }
                    }
                    // Right: what the HUD will show with these options.
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        // The title sits over the preview, so the options start right under the tabs.
                        Text(
                            if (layout == HudOverlayModel.Layout.AR) "Layout Navegação AR" else "Layout Navegação",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                        HudPreview(
                            config = shown.copy(layout = layout),
                            description = "Prévia com estas opções",
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
                            focus = focus,
                            focusTick = focusTick
                        )
                        Text(
                            "Prévia: cidade, rodovia e rotatória, com estas opções.",
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
private fun DemoDriveButton(layout: HudOverlayModel.Layout, modifier: Modifier = Modifier) {
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
        onClick = { HudOverlayController.setDemoDrive(!running, layout) },
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
