package com.example.ui.lobby.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun NetworkStatusPill(
    localIp: String?,
    hotspotSsid: String?,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isHotspot = !hotspotSsid.isNullOrEmpty()
    val isOffline = localIp == null || localIp.contains("Offline") || localIp.contains("Detecting")

    val (badgeBg, badgeBorder, textColor, statusText) = when {
        isHotspot -> {
            Quadruple(
                Color(0xFF00E5FF).copy(alpha = 0.15f),
                Color(0xFF00E5FF).copy(alpha = 0.4f),
                Color(0xFF00E5FF),
                "Hotspot Active • ${hotspotSsid ?: "BeamDrop"}"
            )
        }
        !isOffline -> {
            Quadruple(
                Color(0xFF10B981).copy(alpha = 0.15f),
                Color(0xFF10B981).copy(alpha = 0.4f),
                Color(0xFF10B981),
                "Wi-Fi LAN • $localIp:8080"
            )
        }
        else -> {
            Quadruple(
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                MaterialTheme.colorScheme.error.copy(alpha = 0.4f),
                MaterialTheme.colorScheme.error,
                "Offline • Tap to refresh or start Hotspot"
            )
        }
    }

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onRefresh)
            .testTag("network_status_pill"),
        shape = RoundedCornerShape(24.dp),
        color = badgeBg,
        border = androidx.compose.foundation.BorderStroke(1.dp, badgeBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Pulsing status dot
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(textColor, CircleShape)
            )

            Icon(
                imageVector = when {
                    isHotspot -> Icons.Default.WifiTethering
                    !isOffline -> Icons.Default.Wifi
                    else -> Icons.Default.WifiOff
                },
                contentDescription = "Network Icon",
                tint = textColor,
                modifier = Modifier.size(16.dp)
            )

            Text(
                text = statusText,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = textColor
            )

            Spacer(modifier = Modifier.width(2.dp))

            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Refresh Network",
                tint = textColor.copy(alpha = 0.7f),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
