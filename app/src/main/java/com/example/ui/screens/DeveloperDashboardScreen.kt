package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.UiState
import com.example.ui.components.EmptyStateView
import com.example.ui.components.ErrorStateView
import com.example.ui.components.StatusChip
import com.example.ui.components.formatCompactCount
import com.example.ui.components.formatTimestamp
import com.example.ui.viewmodel.AnalyticsRange
import com.example.ui.viewmodel.DeveloperDashboardSummary
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperDashboardScreen(
    dashboardState: UiState<DeveloperDashboardSummary>,
    selectedRange: AnalyticsRange,
    onSelectRange: (AnalyticsRange) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Developer Dashboard") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        when (dashboardState) {
            is UiState.Loading -> {
                Column(modifier = Modifier.padding(innerPadding).padding(20.dp)) {
                    Text("Loading analytics…")
                }
            }

            is UiState.Error -> {
                Column(modifier = Modifier.padding(innerPadding)) {
                    ErrorStateView(message = dashboardState.message, onRetry = onBack)
                }
            }

            is UiState.Success -> {
                val data = dashboardState.data
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AnalyticsRange.entries.forEach { range ->
                                FilterChip(
                                    selected = selectedRange == range,
                                    onClick = { onSelectRange(range) },
                                    label = { Text(range.label) }
                                )
                            }
                        }
                    }

                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            KpiCard("Published", data.totalPublished.toString(), Modifier.weight(1f))
                            KpiCard("Downloads", formatCompactCount(data.totalDownloads), Modifier.weight(1f))
                        }
                    }
                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            KpiCard("App Views", formatCompactCount(data.totalViews), Modifier.weight(1f))
                            KpiCard(
                                "Avg Rating",
                                if (data.averageRating > 0) String.format(Locale.US, "%.1f ★", data.averageRating) else "—",
                                Modifier.weight(1f)
                            )
                        }
                    }
                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            KpiCard("Reviews", data.totalReviews.toString(), Modifier.weight(1f))
                            KpiCard("Pending / Rejected", "${data.totalPending} / ${data.totalRejected}", Modifier.weight(1f))
                        }
                    }

                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Downloads Trend (${selectedRange.label})", style = MaterialTheme.typography.titleMedium)
                                val barColor = MaterialTheme.colorScheme.primary
                                val trackColor = MaterialTheme.colorScheme.surfaceVariant
                                Canvas(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(140.dp)
                                ) {
                                    val points = data.chartPoints.ifEmpty { listOf(0L) }
                                    val maxVal = (points.maxOrNull() ?: 1L).coerceAtLeast(1L)
                                    val count = points.size.coerceAtLeast(1)
                                    val slotWidth = size.width / count
                                    val barWidth = (slotWidth * 0.58f).coerceAtLeast(6f)
                                    points.forEachIndexed { idx, value ->
                                        val left = idx * slotWidth + (slotWidth - barWidth) / 2f
                                        drawRoundRect(
                                            color = trackColor,
                                            topLeft = Offset(left, 0f),
                                            size = Size(barWidth, size.height),
                                            cornerRadius = CornerRadius(8f, 8f)
                                        )
                                        val ratio = value.toFloat() / maxVal.toFloat()
                                        val barHeight = (size.height * ratio).coerceAtLeast(if (value > 0) 6f else 2f)
                                        drawRoundRect(
                                            color = barColor,
                                            topLeft = Offset(left, size.height - barHeight),
                                            size = Size(barWidth, barHeight),
                                            cornerRadius = CornerRadius(8f, 8f)
                                        )
                                    }
                                }
                                Text(
                                    "Analytics are computed exclusively from trusted backend download & review events.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    item {
                        Text("Application Performance", style = MaterialTheme.typography.titleLarge)
                    }

                    if (data.apps.isEmpty()) {
                        item {
                            EmptyStateView(
                                title = "No applications yet",
                                subtitle = "Submit your first Android APK from the Publish tab to monitor performance here."
                            )
                        }
                    } else {
                        items(data.apps, key = { it.appId }) { app ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp).fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(app.appName, style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            "Downloads: ${app.downloadCount} • Reviews: ${app.totalReviews} • Updated ${formatTimestamp(app.updatedAt)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    StatusChip(status = app.status)
                                }
                            }
                        }
                    }

                    if (data.submissions.isNotEmpty()) {
                        item {
                            Text("Update & Submission History", style = MaterialTheme.typography.titleLarge)
                        }
                        items(data.submissions.take(10), key = { it.id }) { sub ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("${sub.appName} v${sub.versionName}", style = MaterialTheme.typography.titleSmall)
                                        Text(formatTimestamp(sub.updatedAt ?: sub.createdAt), style = MaterialTheme.typography.bodySmall)
                                    }
                                    StatusChip(status = sub.status)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KpiCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
    }
}
