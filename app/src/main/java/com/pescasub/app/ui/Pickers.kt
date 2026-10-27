package com.pescasub.app.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.time.LocalDate
import java.time.LocalTime

fun showDatePicker(context: Context, initial: LocalDate, onPicked: (LocalDate) -> Unit) {
    DatePickerDialog(
        context,
        { _, year, month, day -> onPicked(LocalDate.of(year, month + 1, day)) },
        initial.year,
        initial.monthValue - 1,
        initial.dayOfMonth
    ).show()
}

fun showTimePicker(context: Context, initial: LocalTime, onPicked: (LocalTime) -> Unit) {
    TimePickerDialog(
        context,
        { _, hour, minute -> onPicked(LocalTime.of(hour, minute)) },
        initial.hour,
        initial.minute,
        true
    ).show()
}

/** Chip que despliega un menú de opciones (null suele significar "todos"). */
@Composable
fun <T> DropdownChip(
    label: String,
    active: Boolean,
    options: List<Pair<String, T>>,
    onSelect: (T) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = active,
            onClick = { open = true },
            label = { Text(label, maxLines = 1) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) }
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (text, value) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        open = false
                        onSelect(value)
                    }
                )
            }
        }
    }
}
