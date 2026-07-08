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
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceElevated)
            .padding(4.dp),
    ) {
        TimeRange.entries.forEach { range ->
            val isSelected = range == selected
            Text(
                text = range.label,
                color = if (isSelected) Background else TextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (isSelected) Up else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { onSelect(range) }
                    .padding(vertical = 8.dp),
            )
        }
    }
}
