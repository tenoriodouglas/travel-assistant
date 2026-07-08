package com.travelassistant.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.travelassistant.app.data.SettingsRepository
import com.travelassistant.app.ui.theme.Background
import com.travelassistant.app.ui.theme.Divider
import com.travelassistant.app.ui.theme.Surface
import com.travelassistant.app.ui.theme.TextMuted
import com.travelassistant.app.ui.theme.TextPrimary
import com.travelassistant.app.ui.theme.TextSecondary
import com.travelassistant.app.ui.theme.Up
import com.travelassistant.app.ui.theme.UpDim
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val interval by viewModel.intervalSeconds.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Background)
            .padding(20.dp),
    ) {
        Text(
            "Configurações",
            color = TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(24.dp))

        SectionCard(title = "Intervalo do feed ao vivo") {
            Text(
                "Com que frequência os preços são atualizados no gráfico.",
                color = TextSecondary,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "$interval",
                    color = Up,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    " segundos",
                    color = TextSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            IntervalSlider(interval = interval, onChange = viewModel::setInterval)
            Spacer(Modifier.height(16.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsRepository.PRESETS.forEach { preset ->
                    PresetChip(
                        seconds = preset,
                        selected = preset == interval,
                        onClick = { viewModel.setInterval(preset) },
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        SectionCard(title = "Sobre") {
            InfoLine("App", "Travel Assistant")
            InfoLine("Versão", "1.0")
            InfoLine("Feed", "Preços reais (Travelpayouts)")
            Spacer(Modifier.height(8.dp))
            Text(
                "O app usa apenas preços reais sob demanda. Configure o token do Travelpayouts " +
                    "para habilitar. O intervalo acima é reservado para futuras atualizações automáticas.",
                color = TextMuted,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun IntervalSlider(interval: Int, onChange: (Int) -> Unit) {
    var sliderValue by remember(interval) { mutableFloatStateOf(interval.toFloat()) }
    Slider(
        value = sliderValue,
        onValueChange = { sliderValue = it },
        onValueChangeFinished = { onChange(sliderValue.roundToInt()) },
        valueRange = SettingsRepository.MIN_INTERVAL.toFloat()..SettingsRepository.MAX_INTERVAL.toFloat(),
        colors = SliderDefaults.colors(
            thumbColor = Up,
            activeTrackColor = Up,
            inactiveTrackColor = Divider,
        ),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("${SettingsRepository.MIN_INTERVAL}s", color = TextMuted, fontSize = 11.sp)
        Text("${SettingsRepository.MAX_INTERVAL}s", color = TextMuted, fontSize = 11.sp)
    }
}

@Composable
private fun PresetChip(seconds: Int, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = "${seconds}s",
        color = if (selected) Up else TextSecondary,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) UpDim else Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Surface)
            .padding(16.dp),
    ) {
        Text(title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = TextSecondary, fontSize = 13.sp)
        Text(value, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
