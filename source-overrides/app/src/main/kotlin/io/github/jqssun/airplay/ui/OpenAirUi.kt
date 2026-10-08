package io.github.jqssun.airplay.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.jqssun.airplay.R
import io.github.jqssun.airplay.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@Composable
internal fun OpenAirHeader(status: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
    val compact = maxWidth < 480.dp || maxHeight < 400.dp
    Row(Modifier.fillMaxWidth().padding(horizontal = if (compact) 16.dp else 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Default.Radio, null, tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp))
            Text("OpenAir", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (!compact) Text("TV", style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(Modifier.widthIn(max = if (compact) 140.dp else 240.dp)) { status() }
    }
    }
}

@Composable
internal fun SessionBadge(label: String, active: Boolean = true) {
    Row(verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(6.dp).background(if (active) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant, CircleShape))
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun OpenAirSettingItem(
    headlineContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    supportingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
) {
    ListItem(
        headlineContent = headlineContent, supportingContent = supportingContent,
        trailingContent = trailingContent, leadingContent = leadingContent,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .then(modifier),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    )
}

@Composable
internal fun ReceiverInfoScreen(viewModel: MainViewModel) {
    val name by viewModel.serverName.collectAsState()
    val port by viewModel.serverPort.collectAsState()
    val state by viewModel.serverState.collectAsState()
    val connections by viewModel.connectionCount.collectAsState()
    val pin by viewModel.requirePin.collectAsState()
    val audio by viewModel.advertiseAudio.collectAsState()
    val video by viewModel.advertiseVideo.collectAsState()
    val resolution by viewModel.resolution.collectAsState()
    val background by viewModel.runInBackground.collectAsState()
    val boot by viewModel.bootAutoStart.collectAsState()
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val step = with(LocalDensity.current) { 72.dp.toPx() }
    Column(Modifier.fillMaxSize().dpadFocus().onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) false
        else when {
            event.key == Key.DirectionDown && scroll.canScrollForward -> {
                scope.launch { scroll.animateScrollBy(step) }; true
            }
            event.key == Key.DirectionUp && scroll.canScrollBackward -> {
                scope.launch { scroll.animateScrollBy(-step) }; true
            }
            else -> false
        }
    }.focusable().verticalScroll(scroll).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.openair_receiver), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        InfoRow("Receptor", name)
        InfoRow("Estado", state.name)
        InfoRow("Porta", port.toString())
        InfoRow("Conexões", connections.toString())
        InfoRow("Segurança / PIN", if (pin) "Ativado" else "Desativado")
        InfoRow("AirPlay áudio", if (audio) "Ativado" else "Desativado")
        InfoRow("AirPlay vídeo", if (video) "Ativado" else "Desativado")
        InfoRow("Resolução anunciada", resolution)
        InfoRow("Segundo plano", if (background) "Ativado" else "Desativado")
        InfoRow("Iniciar com a TV", if (boot) "Ativado" else "Desativado")
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    OpenAirSettingItem(headlineContent = { Text(label) },
        trailingContent = { Text(value, color = MaterialTheme.colorScheme.primary) })
}
