package com.shergill.tryon.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.shergill.tryon.domain.CalibrationOffsets
import kotlin.math.roundToInt

@Composable
fun CalibrationControls(
    offsets: CalibrationOffsets,
    onChange: (CalibrationOffsets) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                "Calibration",
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onReset) {
                Text("Reset", color = Color.White)
            }
        }
        Text(
            "Drag sliders until the accessory sits on your face. Values are saved per accessory type.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.75f),
        )
        Spacer(Modifier.height(4.dp))
        LabeledSlider("Offset X", offsets.offsetX, -0.8f..0.8f) {
            onChange(offsets.copy(offsetX = it))
        }
        LabeledSlider("Offset Y", offsets.offsetY, -0.8f..0.8f) {
            onChange(offsets.copy(offsetY = it))
        }
        LabeledSlider("Offset Z", offsets.offsetZ, -0.8f..0.8f) {
            onChange(offsets.copy(offsetZ = it))
        }
        LabeledSlider("Scale", offsets.scale, 0.15f..4f) {
            onChange(offsets.copy(scale = it))
        }
        LabeledSlider("Yaw°", offsets.rotationYawDeg, -90f..90f) {
            onChange(offsets.copy(rotationYawDeg = it))
        }
        LabeledSlider("Pitch°", offsets.rotationPitchDeg, -90f..90f) {
            onChange(offsets.copy(rotationPitchDeg = it))
        }
        LabeledSlider("Roll°", offsets.rotationRollDeg, -90f..90f) {
            onChange(offsets.copy(rotationRollDeg = it))
        }
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Text("$label: ${"%.2f".format(value)}", color = Color.White)
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = range,
        steps = ((range.endInclusive - range.start) * 20).roundToInt().coerceAtLeast(0),
    )
}
