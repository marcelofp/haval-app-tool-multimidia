package br.com.redesurftank.havalshisuku.navigation

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.redesurftank.havalshisuku.ui.components.AppColors
import br.com.redesurftank.havalshisuku.ui.components.StyledCard
import kotlinx.coroutines.delay

@Composable
fun CarPlayCaptureScreen(onBackToFeatures: () -> Unit) {
    val context = LocalContext.current
    var status by remember { mutableStateOf(CarPlayNavigationCapture.status) }
    var now by remember { mutableStateOf(SystemClock.elapsedRealtime()) }
    var experimentalHud by remember { mutableStateOf(CarPlayExperimentalHud.enabled) }
    LaunchedEffect(Unit) {
        while (true) {
            status = CarPlayNavigationCapture.status
            now = SystemClock.elapsedRealtime()
            experimentalHud = CarPlayExperimentalHud.enabled
            delay(1_000L)
        }
    }
    Column(
        modifier = Modifier.fillMaxSize().background(AppColors.Background)
            .verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OutlinedButton(onClick = onBackToFeatures) { Text("Voltar aos recursos") }
        Text("Diagnóstico de navegação CarPlay", color = AppColors.TextPrimary,
            fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("Inicie com o carro parado e o CarPlay aberto. Depois faça uma rota no Apple Maps ou Waze.",
            color = AppColors.TextSecondary, fontSize = 18.sp)
        StyledCard {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Captura manual de até 60 minutos", color = AppColors.TextPrimary, fontSize = 20.sp)
                Text("Salva ruas, distâncias e códigos de navegação em arquivos privados na central. " +
                    "Continua ao sair desta tela; encerra ao parar, reiniciar o app ou atingir uma hora. " +
                    "Os registros não são enviados automaticamente. Ainda não exibe novas setas nem mapa limpo.",
                    color = AppColors.TextSecondary, fontSize = 16.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { CarPlayNavigationCapture.start(context) }, enabled = !status.running) {
                        Text("Iniciar captura")
                    }
                    OutlinedButton(onClick = { CarPlayNavigationCapture.stop() }, enabled = status.running) {
                        Text("Encerrar captura")
                    }
                }
            }
        }
        StyledCard {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Texto CarPlay no HUD nativo · experimental", color = AppColors.TextPrimary, fontSize = 20.sp)
                Text("Ative apenas parado ou pelo passageiro, após iniciar a captura. Mostra via após a manobra e distância no HUD nativo já configurado em Navegação/AR. " +
                    "Sem setas, ETA ou mapa limpo; não altera temas. Android Auto com rota ativa tem prioridade. " +
                    "Desliga ao encerrar a captura ou reiniciar o app. Silêncio de 5 segundos retira os dados (o HUD pode levar mais 5 segundos para ocultar o card).",
                    color = AppColors.TextSecondary)
                Switch(checked = experimentalHud, onCheckedChange = {
                    CarPlayExperimentalHud.setEnabled(it)
                    CarPlayNavigationCapture.mark(if (it) "experimental_hud_on" else "experimental_hud_off")
                }, enabled = status.running)
            }
        }
        StyledCard {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(status.message, color = AppColors.TextPrimary, fontSize = 20.sp)
                Text("Serviço: ${if (status.connected) "Binder conectado" else "sem conexão"}", color = AppColors.TextSecondary)
                Text("Callbacks: ${status.callbacks} · Erros: ${status.errors} · Descartados: ${status.dropped}", color = AppColors.TextSecondary)
                Text("Nulos: ${status.summary.empty} · Sem rota elegível: ${status.summary.inactive}", color = AppColors.TextSecondary)
                Text("Potencialmente úteis: ${status.summary.candidates} · Não confirmados: ${status.summary.unconfirmed}", color = AppColors.TextSecondary)
                Text("Os contadores são desta sessão. Serviço conectado ou callback recebido não garante orientação ativa nem mapa limpo.",
                    color = AppColors.TextSecondary)
                val end = if (status.running) now else status.stoppedAtMs
                val duration = if (status.startedAtMs == 0L) 0L else ((end - status.startedAtMs).coerceAtLeast(0L) / 1_000L)
                Text("Duração: ${duration / 60}min ${duration % 60}s", color = AppColors.TextSecondary)
                val age = if (status.lastCallbackMs == 0L) "nenhum" else "há ${(now - status.lastCallbackMs) / 1_000}s"
                Text("Último callback: $age", color = AppColors.TextSecondary)
                Text("Estado bruto: ${status.routeState ?: "—"} · Manobra bruta: ${status.maneuver ?: "—"}", color = AppColors.TextSecondary)
                if (status.session.isNotEmpty()) Text("Sessão: ${status.session}", color = AppColors.TextSecondary)
            }
        }
        Text("Marcadores para comparar com os registros. Use com o carro parado ou peça ao passageiro.",
            color = AppColors.TextSecondary, fontSize = 16.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { CarPlayNavigationCapture.mark("apple_maps") }, enabled = status.running) { Text("Apple Maps") }
            OutlinedButton(onClick = { CarPlayNavigationCapture.mark("waze") }, enabled = status.running) { Text("Waze") }
            OutlinedButton(onClick = { CarPlayNavigationCapture.mark("incident") }, enabled = status.running) { Text("Marcar evento") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { CarPlayNavigationCapture.mark("route_start") }, enabled = status.running) { Text("Início da rota") }
            OutlinedButton(onClick = { CarPlayNavigationCapture.mark("route_end") }, enabled = status.running) { Text("Fim da rota") }
        }
    }
}
