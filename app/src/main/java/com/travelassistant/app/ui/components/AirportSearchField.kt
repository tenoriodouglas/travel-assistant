package com.travelassistant.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.travelassistant.app.data.Airports
import com.travelassistant.app.data.model.Airport
import com.travelassistant.app.ui.theme.Divider
import com.travelassistant.app.ui.theme.Surface
import com.travelassistant.app.ui.theme.TextMuted
import com.travelassistant.app.ui.theme.TextPrimary
import com.travelassistant.app.ui.theme.TextSecondary
import com.travelassistant.app.ui.theme.Up

/** Origin/destination field with live autocomplete over the airport catalog. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AirportSearchField(
    label: String,
    selected: Airport?,
    onSelect: (Airport) -> Unit,
    leadingIcon: ImageVector,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf(selected?.label.orEmpty()) }
    var expanded by remember { mutableStateOf(false) }

    // Keep the text in sync when the selection changes from outside (e.g. swap button).
    LaunchedEffect(selected) {
        if (selected != null && selected.label != query) query = selected.label
    }

    val results = if (expanded) Airports.search(query) else emptyList()

    ExposedDropdownMenuBox(
        expanded = expanded && results.isNotEmpty(),
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                expanded = true
            },
            label = { Text(label) },
            leadingIcon = { Icon(leadingIcon, contentDescription = null, tint = TextMuted) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedContainerColor = Surface,
                unfocusedContainerColor = Surface,
                focusedBorderColor = Up,
                unfocusedBorderColor = Divider,
                focusedLabelColor = Up,
                unfocusedLabelColor = TextMuted,
                cursorColor = Up,
            ),
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded && results.isNotEmpty(),
            onDismissRequest = { expanded = false },
        ) {
            results.forEach { airport ->
                DropdownMenuItem(
                    text = { SuggestionRow(airport) },
                    onClick = {
                        onSelect(airport)
                        query = airport.label
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

@Composable
private fun SuggestionRow(airport: Airport) {
    Column {
        Text(
            text = "${airport.city} (${airport.code})",
            color = TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = airport.country,
            color = TextSecondary,
            fontSize = 12.sp,
        )
    }
}
