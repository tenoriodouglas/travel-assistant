package com.travelassistant.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import com.travelassistant.app.data.model.Granularity
import com.travelassistant.app.data.model.TimeRange
import com.travelassistant.app.ui.theme.Background
import com.travelassistant.app.ui.theme.SurfaceElevated
import com.travelassistant.app.ui.theme.TextSecondary
import com.travelassistant.app.ui.theme.Up

/** Crypto-style range buttons selecting the future departure window. */
@Composable
fun RangeSelector(
    selected: TimeRange,
    onSelect: (TimeRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    SegmentedRow(
        items = TimeRange.entries,
        isSelected = { it == selected },
        label = { it.label },
        onSelect = onSelect,
        modifier = modifier,
    )
}

/** Buttons selecting the candle granularity (day / week / month). */
@Composable
fun GranularitySelector(
    selected: Granularity,
    onSelect: (Granularity) -> Unit,
    modifier: Modifier = Modifier,
) {
    SegmentedRow(
        items = Granularity.entries,
        isSelected = { it == selected },
        label = { it.label },
        onSelect = onSelect,
        modifier = modifier,
    )
}

@Composable
private fun <T> SegmentedRow(
    items: List<T>,
    isSelected: (T) -> Boolean,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceElevated)
            .padding(4.dp),
    ) {
        items.forEach { item ->
            val sel = isSelected(item)
            Text(
                text = label(item),
                color = if (sel) Background else TextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (sel) Up else Color.Transparent)
                    .clickable { onSelect(item) }
                    .padding(vertical = 8.dp),
            )
        }
    }
}
