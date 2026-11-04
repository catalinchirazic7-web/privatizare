package com.example.privatizare.ui.game

import androidx.compose.foundation.background
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.privatizare.data.model.PawnColor

/**
 * Paleta de 8 culori. Culorile deja alese de alți jucători sunt dezactivate.
 * Culoarea selectată are contur mai gros.
 */
@Composable
fun ColorPalette(
    selected: PawnColor?,
    taken: Set<PawnColor>,
    onSelect: (PawnColor) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PawnColor.entries
    Column(modifier = modifier) {
        colors.chunked(4).forEach { row ->
            Row {
                row.forEachIndexed { i, color ->
                    if (i > 0) Spacer(Modifier.width(10.dp))
                    val isTaken = color in taken && color != selected
                    Swatch(
                        color = color,
                        isSelected = color == selected,
                        isTaken = isTaken,
                        onClick = { if (!isTaken) onSelect(color) },
                    )
                }
            }
            Spacer(Modifier.size(height = 10.dp, width = 1.dp))
        }
    }
}

@Composable
private fun Swatch(color: PawnColor, isSelected: Boolean, isTaken: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .size(width = 52.dp, height = 78.dp)
            .clip(shape)
            .background(if (isSelected) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.08f))
            .border(
                width = if (isSelected) 3.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.secondary else Color.Gray,
                shape = shape,
            )
            .clickable(enabled = !isTaken, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        PawnShape(
            color = color,
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp).alpha(if (isTaken) 0.25f else 1f),
        )
    }
}
