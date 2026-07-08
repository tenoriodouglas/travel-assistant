package com.travelassistant.app.ui.markets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.travelassistant.app.data.model.RouteMarket
import com.travelassistant.app.ui.components.ChangeChip
import com.travelassistant.app.ui.components.LiveIndicator
import com.travelassistant.app.ui.components.Sparkline
import com.travelassistant.app.ui.theme.Background
import com.travelassistant.app.ui.theme.Divider
import com.travelassistant.app.ui.theme.Down
import com.travelassistant.app.ui.theme.MonoNumber
import com.travelassistant.app.ui.theme.Surface
import com.travelassistant.app.ui.theme.TextMuted
import com.travelassistant.app.ui.theme.TextPrimary
import com.travelassistant.app.ui.theme.TextSecondary
import com.travelassistant.app.ui.theme.Up
import com.travelassistant.app.util.formatMoney

@Composable
fun MarketsScreen(
    onRouteClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MarketsViewModel = viewModel(factory = MarketsViewModel.Factory),
) {
    // markets emits a fresh list on every feed tick, so the row prices stay live.
    val markets by viewModel.markets.collectAsStateWithLifecycle()
    val interval by viewModel.intervalSeconds.collectAsStateWithLifecycle()

    var query by remember { mutableStateOf("") }
    val filtered = remember(markets, query) {
        if (query.isBlank()) markets
        else markets.filter {
            val q = query.trim().uppercase()
            it.route.origin.contains(q) || it.route.destination.contains(q) ||
                it.route.originCity.uppercase().contains(q) ||
                it.route.destinationCity.uppercase().contains(q)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Background),
    ) {
        Header(intervalSeconds = interval)
        SearchBox(query = query, onQueryChange = { query = it })
        TableHeader()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            items(filtered, key = { it.route.id }) { market ->
                MarketRow(market = market, onClick = { onRouteClick(market.route.id) })
            }
        }
    }
}

@Composable
private fun Header(intervalSeconds: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Travel Assistant",
                color = TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(10.dp))
            LiveIndicator()
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Preços de passagens • feed a cada ${intervalSeconds}s",
            color = TextSecondary,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun SearchBox(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        placeholder = { Text("Buscar rota (GRU, Lisboa...)", color = TextMuted) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = TextMuted) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            focusedContainerColor = Surface,
            unfocusedContainerColor = Surface,
            focusedBorderColor = Up,
            unfocusedBorderColor = Divider,
            cursorColor = Up,
        ),
    )
}

@Composable
private fun TableHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Rota", color = TextMuted, fontSize = 11.sp, modifier = Modifier.weight(1f))
        Text("Tendência", color = TextMuted, fontSize = 11.sp)
        Spacer(Modifier.width(24.dp))
        Text(
            "Preço / 24h",
            color = TextMuted,
            fontSize = 11.sp,
            modifier = Modifier.width(120.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
}

@Composable
private fun MarketRow(market: RouteMarket, onClick: () -> Unit) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Route identity
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = market.route.pair,
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${market.route.originCity} → ${market.route.destinationCity}",
                    color = TextSecondary,
                    fontSize = 12.sp,
                )
            }
            // Sparkline
            Sparkline(
                values = market.spark,
                modifier = Modifier
                    .width(64.dp)
                    .height(32.dp),
            )
            Spacer(Modifier.width(16.dp))
            // Price + change
            Column(
                modifier = Modifier.width(112.dp),
                horizontalAlignment = Alignment.End,
            ) {
                Text(
                    text = formatMoney(market.price, market.route.currency),
                    color = TextPrimary,
                    fontSize = 15.sp,
                    style = MonoNumber,
                )
                Spacer(Modifier.height(4.dp))
                ChangeChip(percent = market.changePct)
            }
        }
        Box(
            modifier = Modifier
                .padding(start = 20.dp)
                .fillMaxWidth()
                .height(1.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(Divider.copy(alpha = 0.4f)),
        )
    }
}
