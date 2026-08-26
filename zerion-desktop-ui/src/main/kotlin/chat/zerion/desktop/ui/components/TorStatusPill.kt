package chat.zerion.desktop.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

import chat.zerion.desktop.ui.theme.ConnectedGreen
import chat.zerion.desktop.ui.theme.ConnectingAmber
import chat.zerion.desktop.ui.theme.OfflineGray

enum class TorUiState { CONNECTING, CONNECTED, OFFLINE }

@Composable
fun TorStatusPill(state: TorUiState, modifier: Modifier = Modifier) {
	StatusPill(state, "Connected over Tor", "Connecting over Tor…", "Offline",
			modifier)
}

@Composable
fun I2pStatusPill(state: TorUiState, modifier: Modifier = Modifier) {
	StatusPill(state, "Connected over I2P", "I2P: building tunnels…", "I2P: off",
			modifier)
}

@Composable
private fun StatusPill(
		state: TorUiState,
		connected: String,
		connecting: String,
		offline: String,
		modifier: Modifier = Modifier,
) {
	val (dotColor, label) = when (state) {
		TorUiState.CONNECTED -> ConnectedGreen to connected
		TorUiState.CONNECTING -> ConnectingAmber to connecting
		TorUiState.OFFLINE -> OfflineGray to offline
	}
	Row(
			modifier
					.clip(RoundedCornerShape(50))
					.background(MaterialTheme.colorScheme.surfaceContainerHighest)
					.padding(horizontal = 10.dp, vertical = 5.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(6.dp),
	) {
		val pulse = if (state == TorUiState.CONNECTING) {
			val t = rememberInfiniteTransition(label = "tor-pulse")
			t.animateFloat(
					initialValue = 0.35f, targetValue = 1f,
					animationSpec = infiniteRepeatable(
							tween(700), RepeatMode.Reverse),
					label = "tor-pulse-alpha").value
		} else 1f
		androidx.compose.foundation.layout.Box(
				Modifier
						.size(8.dp)
						.alpha(pulse)
						.clip(CircleShape)
						.background(dotColor))
		Text(
				text = label,
				style = MaterialTheme.typography.labelMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant)
	}
}
