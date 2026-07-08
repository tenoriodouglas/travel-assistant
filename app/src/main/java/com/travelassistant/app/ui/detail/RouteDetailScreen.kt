package com.travelassistant.app.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.travelassistant.app.data.model.ProviderKind
import com.travelassistant.app.data.model.ProviderQuote
import com.travelassistant.app.data.model.RouteDetail
import com.travelassistant.app.ui.components.ChangeChip
import com.travelassistant.app.ui.components.ChartMode
import com.travelassistant.app.ui.components.LiveIndicator
import com.travelassistant.app.ui.components.PriceChart
import com.travelassistant.app.ui.components.Sparkline
import com.travelassistant.app.ui.components.Tag
import com.travelassistant.app.ui.theme.Accent
import com.travelassistant.app.ui.theme.AccentDim
import com.travelassistant.app.ui.theme.Background
import com.travelassistant.app.ui.theme.Divider
import com.travelassistant.app.ui.theme.Down
import com.travelassistant.app.ui.theme.Info
import com.travelassistant.app.ui.theme.MonoNumber
import com.travelassistant.app.ui.theme.Surface
import com.travelassistant.app.ui.theme.SurfaceElevated
import com.travelassistant.app.ui.theme.TextMuted
import com.travelassistant.app.ui.theme.TextPrimary
import com.travelassistant.app.ui.theme.TextSecondary
import com.travelassistant.app.ui.theme.Up
import com.travelassistant.app.util.formatMoney
import com.travelassistant.app.util.formatMoneyPrecise
import com.travelassistant.app.util.formatPercent
import com.travelassistant.app.util.formatSignedMoney

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteDetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RouteDetailViewModel = viewModel(factory = RouteDetailViewModel.Factory),
) {
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    val interval by viewModel.intervalSeconds.collectAsStateWithLifecycle()
    val d = detail

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = d?.market?.route?.pair ?: "",
                            color = TextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (d != null) {
                            Text(
                                text = "${d.market.route.originCity} → ${d.market.route.destinationCity}",
                                color = TextSecondary,
                                fontSize = 12.sp,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Atualizar agora", tint = Up)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        },
    ) { padding ->
        if (d == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Rota não encontrada", color = TextSecondary)
            }
            return@Scaffold
        }
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            PriceHeader(d, interval)
            Spacer(Modifier.height(16.dp))
            StatsRow(d)
            Spacer(Modifier.height(16.dp))
            ChartCard(d)
            Spacer(Modifier.height(24.dp))
            ProvidersSection(d)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PriceHeader(d: RouteDetail, intervalSeconds: Int) {
    val m = d.market
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Text(
            text = formatMoneyPrecise(m.price, m.route.currency),
            color = if (m.isUp) Up else Down,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            style = MonoNumber,
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = formatSignedMoney(m.changeAbs, m.route.currency),
                color = if (m.isUp) Up else Down,
                fontSize = 14.sp,
                style = MonoNumber,
            )
            Spacer(Modifier.width(8.dp))
            ChangeChip(percent = m.changePct)
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            LiveIndicator()
            Spacer(Modifier.width(10.dp))
            Text(
                text = "atualiza a cada ${intervalSeconds}s",
                color = TextMuted,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun StatsRow(d: RouteDetail) {
    val m = d.market
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Surface)
            .padding(vertical = 14.dp, horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Stat("Abertura", formatMoney(m.openPrice, m.route.currency), TextPrimary)
        Stat("Máx período", formatMoney(m.high, m.route.currency), Up)
        Stat("Mín período", formatMoney(m.low, m.route.currency), Down)
        val best = d.bestQuote
        Stat(
            label = "Melhor agora",
            value = if (best != null) formatMoney(best.price, m.route.currency) else "—",
            color = Accent,
        )
    }
}

@Composable
private fun Stat(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(label, color = TextMuted, fontSize = 10.sp)
        Spacer(Modifier.height(4.dp))
        Text(value, color = color, fontSize = 13.sp, style = MonoNumber)
    }
}

@Composable
private fun ChartCard(d: RouteDetail) {
    var mode by remember { mutableStateOf(ChartMode.CANDLES) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Surface)
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Histórico de preço",
                color = TextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            ModeToggle(mode = mode, onModeChange = { mode = it })
        }
        Spacer(Modifier.height(12.dp))
        PriceChart(
            candles = d.candles,
            mode = mode,
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp),
        )
    }
}

@Composable
private fun ModeToggle(mode: ChartMode, onModeChange: (ChartMode) -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceElevated)
            .padding(3.dp),
    ) {
        ToggleChip("Velas", mode == ChartMode.CANDLES) { onModeChange(ChartMode.CANDLES) }
        ToggleChip("Linha", mode == ChartMode.LINE) { onModeChange(ChartMode.LINE) }
    }
}

@Composable
private fun ToggleChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        color = if (selected) Background else TextSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) Up else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp),
    )
}

@Composable
private fun ProvidersSection(d: RouteDetail) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "Companhias & Plataformas",
            color = TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Ordenado do menor para o maior preço",
            color = TextMuted,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(12.dp))
        val bestId = d.bestQuote?.provider?.id
        d.quotes.forEach { quote ->
            ProviderRow(
                quote = quote,
                currency = d.market.route.currency,
                isBest = quote.provider.id == bestId,
            )
        }
    }
}

@Composable
private fun ProviderRow(quote: ProviderQuote, currency: String, isBest: Boolean) {
    val kindColor = if (quote.provider.kind == ProviderKind.AIRLINE) Info else Accent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(36.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(kindColor),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = quote.provider.name,
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
                if (isBest) {
                    Spacer(Modifier.width(8.dp))
                    Tag("MELHOR", Accent, AccentDim)
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (quote.provider.kind == ProviderKind.AIRLINE) "Companhia aérea" else "Plataforma",
                color = TextMuted,
                fontSize = 11.sp,
            )
        }
        Sparkline(
            values = quote.spark,
            modifier = Modifier
                .width(56.dp)
                .height(26.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(104.dp)) {
            Text(
                text = formatMoney(quote.price, currency),
                color = TextPrimary,
                fontSize = 15.sp,
                style = MonoNumber,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = formatPercent(quote.changePct),
                color = if (quote.changePct >= 0) Up else Down,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
