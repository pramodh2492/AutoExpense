package com.expensetracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.expensetracker.data.model.TransactionCategory

private val categoryColors = listOf(
    Color(0xFFFF5252),  // Red — Food
    Color(0xFF4CAF50),  // Green — Groceries
    Color(0xFF2196F3),  // Blue — Transport
    Color(0xFFFF9800),  // Orange — Shopping
    Color(0xFF9C27B0),  // Purple — Bills
    Color(0xFFE91E63),  // Pink — Entertainment
    Color(0xFF00BCD4),  // Cyan — Health
    Color(0xFFFFC107),  // Amber — Education
    Color(0xFF8BC34A),  // Light green — Transfer
    Color(0xFF607D8B),  // Blue grey — ATM
    Color(0xFFFF5722),  // Deep orange — EMI
    Color(0xFF673AB7),  // Deep purple — Subscription
    Color(0xFF009688),  // Teal — Fuel
    Color(0xFF3F51B5),  // Indigo — Travel
    Color(0xFFDCE775),
)

@Composable
fun CategoryPieChart(
    data: Map<TransactionCategory, Double>,
    modifier: Modifier = Modifier
) {
    val total = data.values.sum()
    if (total == 0.0) return

    val sortedData = data.entries.sortedByDescending { it.value }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(modifier = Modifier.size(160.dp)) {
            var startAngle = -90f
            sortedData.forEachIndexed { index, (_, amount) ->
                val sweepAngle = (amount / total * 360f).toFloat()
                drawArc(
                    color = categoryColors[index % categoryColors.size],
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = true,
                    topLeft = Offset(20f, 20f),
                    size = Size(size.width - 40f, size.height - 40f)
                )
                startAngle += sweepAngle
            }
        }

        Column(
            modifier = Modifier.padding(start = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            sortedData.take(5).forEachIndexed { index, (category, amount) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(modifier = Modifier.size(10.dp)) {
                        drawCircle(color = categoryColors[index % categoryColors.size])
                    }
                    Text(
                        text = "${category.displayName}\n₹${String.format("%,.0f", amount)} (${((amount / total) * 100).toInt()}%)",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
            }
        }
    }
}
