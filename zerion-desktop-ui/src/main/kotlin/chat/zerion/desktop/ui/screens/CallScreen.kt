package chat.zerion.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

import chat.zerion.desktop.ui.ZerionModel
import chat.zerion.desktop.ui.components.Avatar
import chat.zerion.desktop.ui.voice.VoiceCallEngine

private val Green = Color(0xFF2ECC71)
private val Red = Color(0xFFE74C3C)
private val MuteAmber = Color(0xFFF39C12)

/**
 * In-call UI. Shows a full-window overlay for ringing/incoming calls and while
 * the call is expanded, or a small non-blocking bar when minimized so the rest
 * of the app stays fully usable during a call (open other chats, groups, etc.).
 */
@Composable
fun CallOverlay(model: ZerionModel) {
	val call = model.call
	if (call.phase == VoiceCallEngine.Phase.IDLE) return
	if (call.minimized) {
		Box(Modifier.fillMaxSize().padding(12.dp),
				contentAlignment = Alignment.TopCenter) {
			MinimizedCallBar(call)
		}
	} else {
		FullCallOverlay(call)
	}
}

@Composable
private fun FullCallOverlay(call: VoiceCallEngine) {
	val phase = call.phase
	Box(Modifier.fillMaxSize().background(Color(0xF20F1014))) {
		if (phase != VoiceCallEngine.Phase.INCOMING) {
			IconButton(onClick = { call.applyMinimized(true) },
					modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
				Icon(Icons.Filled.CloseFullscreen, contentDescription = "Minimize",
						tint = Color.White.copy(alpha = 0.8f))
			}
		}

		Column(
				horizontalAlignment = Alignment.CenterHorizontally,
				verticalArrangement = Arrangement.spacedBy(14.dp),
				modifier = Modifier.align(Alignment.Center).padding(32.dp)) {

			Box(Modifier.size(120.dp).clip(CircleShape),
					contentAlignment = Alignment.Center) {
				Avatar(name = call.peerName.ifEmpty { "?" }, colorKey = 0,
						size = 120.dp, photo = null)
			}

			Text(call.peerName.ifEmpty { "Contact" },
					style = MaterialTheme.typography.headlineSmall,
					fontWeight = FontWeight.SemiBold,
					color = Color.White)

			Text(statusFor(call),
					style = MaterialTheme.typography.titleMedium,
					color = Color.White.copy(alpha = 0.7f))

			Row(verticalAlignment = Alignment.CenterVertically) {
				Icon(Icons.Filled.Call, contentDescription = null,
						modifier = Modifier.size(14.dp),
						tint = Color.White.copy(alpha = 0.4f))
				Spacer(Modifier.size(6.dp))
				Text("Encrypted · routed over Tor",
						style = MaterialTheme.typography.labelMedium,
						color = Color.White.copy(alpha = 0.4f))
			}

			Spacer(Modifier.size(18.dp))

			when (phase) {
				VoiceCallEngine.Phase.INCOMING -> {
					Row(horizontalArrangement = Arrangement.spacedBy(48.dp)) {
						CallButton(Icons.Filled.CallEnd, "Decline", Red) {
							call.declineCall()
						}
						CallButton(Icons.Filled.Call, "Accept", Green) {
							call.acceptCall()
						}
					}
				}
				VoiceCallEngine.Phase.CONNECTED -> {
					Row(horizontalArrangement = Arrangement.spacedBy(48.dp)) {
						CallButton(
								if (call.muted) Icons.Filled.MicOff
										else Icons.Filled.Mic,
								if (call.muted) "Unmute" else "Mute",
								if (call.muted) MuteAmber else Color(0xFF444A52)) {
							call.toggleMute()
						}
						CallButton(Icons.Filled.CallEnd, "Hang up", Red) {
							call.hangUp()
						}
					}
				}
				VoiceCallEngine.Phase.OUTGOING,
				VoiceCallEngine.Phase.CONNECTING -> {
					CallButton(Icons.Filled.CallEnd, "Cancel", Red) {
						call.hangUp()
					}
				}
				else -> {
				}
			}
		}
	}
}

@Composable
private fun MinimizedCallBar(call: VoiceCallEngine) {
	Card(colors = CardDefaults.cardColors(
			containerColor = Color(0xFF10151D).copy(alpha = 0.97f)),
			shape = RoundedCornerShape(28.dp)) {
		Row(Modifier.padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)
				.widthIn(max = 420.dp),
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(10.dp)) {
			Box(Modifier.size(9.dp).clip(CircleShape).background(
					if (call.phase == VoiceCallEngine.Phase.CONNECTED) Green
					else MuteAmber))
			Column(Modifier.widthIn(max = 200.dp)) {
				Text(call.peerName.ifEmpty { "Contact" },
						style = MaterialTheme.typography.bodyMedium,
						fontWeight = FontWeight.Medium,
						color = Color.White,
						maxLines = 1, overflow = TextOverflow.Ellipsis)
				Text(statusFor(call),
						style = MaterialTheme.typography.labelSmall,
						color = Color.White.copy(alpha = 0.6f),
						maxLines = 1, overflow = TextOverflow.Ellipsis)
			}
			if (call.phase == VoiceCallEngine.Phase.CONNECTED) {
				BarIconButton(
						if (call.muted) Icons.Filled.MicOff else Icons.Filled.Mic,
						if (call.muted) "Unmute" else "Mute",
						if (call.muted) MuteAmber else Color.White) {
					call.toggleMute()
				}
			}
			BarIconButton(Icons.Filled.OpenInFull, "Expand", Color.White) {
				call.applyMinimized(false)
			}
			FilledIconButton(onClick = { call.hangUp() },
					modifier = Modifier.size(38.dp),
					colors = IconButtonDefaults.filledIconButtonColors(
							containerColor = Red, contentColor = Color.White)) {
				Icon(Icons.Filled.CallEnd, contentDescription = "Hang up",
						modifier = Modifier.size(20.dp))
			}
		}
	}
}

private fun statusFor(call: VoiceCallEngine): String = when (call.phase) {
	VoiceCallEngine.Phase.CONNECTED ->
		(if (call.muted) "Muted · " else "") + formatCallTime(call.durationSeconds)
	else -> call.statusText ?: when (call.phase) {
		VoiceCallEngine.Phase.OUTGOING -> "Calling…"
		VoiceCallEngine.Phase.INCOMING -> "Incoming voice call"
		VoiceCallEngine.Phase.CONNECTING -> "Connecting…"
		else -> ""
	}
}

@Composable
private fun BarIconButton(
		icon: ImageVector,
		label: String,
		tint: Color,
		onClick: () -> Unit,
) {
	IconButton(onClick = onClick, modifier = Modifier.size(38.dp)) {
		Icon(icon, contentDescription = label, tint = tint,
				modifier = Modifier.size(20.dp))
	}
}

@Composable
private fun CallButton(
		icon: ImageVector,
		label: String,
		color: Color,
		onClick: () -> Unit,
) {
	Column(horizontalAlignment = Alignment.CenterHorizontally,
			verticalArrangement = Arrangement.spacedBy(8.dp)) {
		FilledIconButton(
				onClick = onClick,
				modifier = Modifier.size(64.dp),
				colors = IconButtonDefaults.filledIconButtonColors(
						containerColor = color, contentColor = Color.White)) {
			Icon(icon, contentDescription = label,
					modifier = Modifier.size(28.dp))
		}
		Text(label, style = MaterialTheme.typography.labelMedium,
				color = Color.White.copy(alpha = 0.7f))
	}
}

private fun formatCallTime(seconds: Int): String {
	val m = seconds / 60
	val s = seconds % 60
	return "%d:%02d".format(m, s)
}
