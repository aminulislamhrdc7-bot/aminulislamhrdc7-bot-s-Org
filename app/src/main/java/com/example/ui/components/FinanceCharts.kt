package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.CategoryStat
import com.example.ui.MonthlyChartBar
import com.example.ui.strings.AppStrings
import com.example.ui.strings.formatCurrency
import com.example.ui.theme.ChartPalette
import com.example.ui.theme.ExpenseCoral
import com.example.ui.theme.IncomeGreen

@Composable
fun IncomeExpenseBarChartCard(
    title: String,
    bars: List<MonthlyChartBar>,
    incomeLabel: String,
    expenseLabel: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LegendDot(color = IncomeGreen, label = incomeLabel)
                    LegendDot(color = ExpenseCoral, label = expenseLabel)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            val maxVal = (bars.maxOfOrNull { maxOf(it.income, it.expense) } ?: 1.0).coerceAtLeast(1.0)
            val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
            ) {
                val count = bars.size.coerceAtLeast(1)
                val groupWidth = size.width / count
                val barWidth = (groupWidth * 0.28f).coerceIn(10f, 34f)
                val gap = barWidth * 0.35f

                // Subtle horizontal guide lines
                listOf(0.25f, 0.5f, 0.75f, 1f).forEach { fraction ->
                    val y = size.height * (1f - fraction)
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.5f
                    )
                }

                bars.forEachIndexed { index, bar ->
                    val centerX = groupWidth * index + groupWidth / 2f
                    val incHeight = ((bar.income / maxVal).toFloat() * size.height).coerceAtLeast(
                        if (bar.income > 0) 6f else 2f
                    )
                    val expHeight = ((bar.expense / maxVal).toFloat() * size.height).coerceAtLeast(
                        if (bar.expense > 0) 6f else 2f
                    )
                    drawRoundRect(
                        color = IncomeGreen,
                        topLeft = Offset(centerX - barWidth - gap / 2f, size.height - incHeight),
                        size = Size(barWidth, incHeight),
                        cornerRadius = CornerRadius(6f, 6f)
                    )
                    drawRoundRect(
                        color = ExpenseCoral,
                        topLeft = Offset(centerX + gap / 2f, size.height - expHeight),
                        size = Size(barWidth, expHeight),
                        cornerRadius = CornerRadius(6f, 6f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                bars.forEach { bar ->
                    Text(
                        text = bar.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun CategoryDonutChartCard(
    title: String,
    stats: List<CategoryStat>,
    strings: AppStrings,
    currencyCode: String,
    emptyText: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(14.dp))
            if (stats.isEmpty()) {
                Text(
                    text = emptyText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val trackColor = MaterialTheme.colorScheme.surfaceVariant
                    Canvas(modifier = Modifier.size(128.dp)) {
                        val strokeWidth = 24.dp.toPx()
                        val diameter = size.minDimension - strokeWidth
                        val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
                        val arcSize = Size(diameter, diameter)

                        drawArc(
                            color = trackColor,
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth)
                        )

                        var startAngle = -90f
                        stats.forEachIndexed { index, stat ->
                            val sweep = (stat.percentage * 360f).coerceAtLeast(4f)
                            val color = ChartPalette[index % ChartPalette.size]
                            drawArc(
                                color = color,
                                startAngle = startAngle,
                                sweepAngle = (sweep - 2f).coerceAtLeast(2f),
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                            )
                            startAngle += sweep
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        stats.take(5).forEachIndexed { index, stat ->
                            val color = ChartPalette[index % ChartPalette.size]
                            val pct = (stat.percentage * 100f).toInt()
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                    )
                                    Text(
                                        text = strings.expenseCategoryLabel(stat.category),
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1
                                    )
                                }
                                Text(
                                    text = "$pct% · ${formatCurrency(stat.amountInPreferred, currencyCode)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
