package com.travelassistant.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlightLand
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import com.travelassistant.app.data.model.RouteBoard
import com.travelassistant.app.ui.components.ChangeChip
import com.travelassistant.app.ui.components.ChartMode
import com.travelassistant.app.ui.components.AirportSearchField
import com.travelassistant.app.ui.components.LiveIndicator
import com.travelassistant.app.ui.components.PriceChart
import com.travelassistant.app.ui.components.RangeSelector
import com.travelassistant.app.ui.components.Sparkline
import com.travelassistant.app.ui.components.Tag
import com.travelassistant.app.ui.theme.Accent
import com.travelassistant.app.ui.theme.AccentDim
import com.travelassistant.app.ui.theme.Background
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

private val POPULAR = listOf(
    Triple("GRU", "LIS", "SP → Lisboa"),
    Triple("GRU", "JFK", "SP → Nova York"),
    Triple("GRU", "MIA", "SP → Miami"),
    Triple("GIG", "SCL", "Rio → Santiago"),
    Triple("BSB", "GRU", "Brasília → SP"),
    Triple("GRU", "EZE", "SP → Buenos Aires"),
)

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val origin by viewModel.origin.collectAsStateWithLifecycle()
    val destination by viewModel.destination.collectAsStateWithLifecycle()
    val range by viewModel.range.collectAsStateWithLifecycle()
    val interval by viewModel.intervalSeconds.collectAsStateWithLifecycle()
    val board by viewModel.board.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Travel Assistant", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(10.dp))
            LiveIndicator()
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Surface)
                    .clickable { viewModel.refresh() }
                    .padding(8.dp),
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = "Atualizar agora", tint = Up)
            }
        }
        Spacer(Modifier.height(16.dp))

        SearchCard(
            selectedOrigin = origin,
            selectedDestination = destination,
            onSelectOrigin = viewModel::setOrigin,
            onSelectDestination = viewModel::setDestination,
            onSwap = viewModel::swap,
        )

        Spacer(Modifier.height(16.dp))
        RangeSelector(selected = range, onSelect = viewModel::setRange, modifier = Modifier.fillMaxWidth())

        Spacer(Modifier.height(12.dp))
        PopularRow(onPick = viewModel::pickPopular)

        Spacer(Modifier.height(20.dp))
        val current = board
        if (current == null) {
            EmptyState()
        } else {
            BoardContent(board = current, intervalSeconds = interval)
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun SearchCard(
    selectedOrigin: com.travelassistant.app.data.model.Airport?,
    selectedDestination: com.travelassistant.app.data.model.Airport?,
    onSelectOrigin: (com.travelassistant.app.data.model.Airport) -> Unit,
    onSelectDestination: (com.travelassistant.app.data.model.Airport) -> Unit,
    onSwap: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Surface)
            .padding(14.dp),
    ) {
        AirportSearchField(
            label = "Origem",
            selected = selectedOrigin,
            onSelect = onSelectOrigin,
            leadingIcon = Icons.Filled.FlightTakeoff,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .padding(vertical = 6.dp)
                    .clip(CircleShape)
                    .background(SurfaceElevated)
                    .clickable(onClick = onSwap)
                    .padding(6.dp),
            ) {
                Icon(Icons.Filled.SwapVert, contentDescription = "Inverter origem e destino", tint = Up)
            }
        }
        AirportSearchField(
            label = "Destino",
            selected = selectedDestination,
            onSelect = onSelectDestination,
            leadingIcon = Icons.Filled.FlightLand,
        )
    }
}

@Composable
private fun PopularRow(onPick: (String, String) -> Unit) {
    Column {
        Text("Rotas populares", color = TextMuted, fontSize = 11.sp)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            POPULAR.forEach { (o, d, label) ->
                Text(
                    text = label,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Surface)
                        .clickable { onPick(o, d) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Surface)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Escolha origem e destino", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            "Digite as cidades acima ou toque numa rota popular para ver o gráfico de preços.",
            color = TextSecondary,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun BoardContent(board: RouteBoard, intervalSeconds: Int) {
    PriceHeader(board, intervalSeconds)
    Spacer(Modifier.height(16.dp))
    StatsRow(board)
    Spacer(Modifier.height(16.dp))
    ChartCard(board)
    Spacer(Modifier.height(24.dp))
    ProvidersSection(board)
}

@Composable
private fun PriceHeader(board: RouteBoard, intervalSeconds: Int) {
    Column {
        Text(
            text = "${board.route.origin.city} → ${board.route.destination.city}",
            color = TextSecondary,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = formatMoneyPrecise(board.price, board.route.currency),
            color = if (board.isUp) Up else Down,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            style = MonoNumber,
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = formatSignedMoney(board.changeAbs, board.route.currency),
                color = if (board.isUp) Up else Down,
                fontSize = 14.sp,
                style = MonoNumber,
            )
            Spacer(Modifier.width(8.dp))
            ChangeChip(percent = board.changePct)
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("menor preço • ${board.range.label}", color = TextMuted, fontSize = 12.sp)
            Spacer(Modifier.width(10.dp))
            LiveIndicator(label = "${intervalSeconds}s")
        }
    }
}

@Composable
private fun StatsRow(board: RouteBoard) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Surface)
            .padding(vertical = 14.dp, horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Stat("Máx período", formatMoney(board.high, board.route.currency), Down)
        Stat("Mín período", formatMoney(board.low, board.route.currency), Up)
        Stat("Melhor data", board.cheapestDateLabel, Accent)
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
private fun ChartCard(board: RouteBoard) {
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
            Column {
                Text("Preço por data de embarque", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text("${board.range.label} • datas futuras", color = TextMuted, fontSize = 11.sp)
            }
            ModeToggle(mode = mode, onModeChange = { mode = it })
        }
        Spacer(Modifier.height(12.dp))
        PriceChart(
            candles = board.candles,
            mode = mode,
            xLabels = board.xLabels,
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp),
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
private fun ProvidersSection(board: RouteBoard) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Companhias & Plataformas", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text("Menor preço de cada uma no período • do mais barato ao mais caro", color = TextMuted, fontSize = 12.sp)
        Spacer(Modifier.height(12.dp))
        val bestId = board.bestQuote?.provider?.id
        board.quotes.forEach { quote ->
            ProviderRow(quote = quote, currency = board.route.currency, isBest = quote.provider.id == bestId)
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
                Text(quote.provider.name, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
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
            Text(formatMoney(quote.price, currency), color = TextPrimary, fontSize = 15.sp, style = MonoNumber)
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
