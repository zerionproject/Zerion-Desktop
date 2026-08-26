package chat.zerion.desktop.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import org.jetbrains.skia.Image as SkiaImage

import chat.zerion.desktop.ui.theme.ConnectedGreen
import chat.zerion.desktop.ui.theme.OfflineGray

private val AvatarColors = listOf(
		Color(0xFF6A4FE0), Color(0xFF2E8BD1), Color(0xFF1FA98A),
		Color(0xFFDB7A2E), Color(0xFFCB4E82), Color(0xFF5B8C2A),
		Color(0xFF8A5CD1), Color(0xFFD1483B),
)

private fun colorFor(key: Int): Color =
		AvatarColors[((key % AvatarColors.size) + AvatarColors.size)
				% AvatarColors.size]

private fun initials(name: String): String {
	val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
	return when {
		parts.isEmpty() -> "?"
		parts.size == 1 -> parts[0].take(1).uppercase()
		else -> (parts[0].take(1) + parts.last().take(1)).uppercase()
	}
}

@Composable
fun Avatar(
		name: String,
		colorKey: Int,
		size: Dp = 40.dp,
		modifier: Modifier = Modifier,
		photo: ByteArray? = null,
) {
	val bitmap = remember(photo) { safeImageBitmap(photo) }
	if (bitmap != null) {
		Image(
				bitmap = bitmap,
				contentDescription = null,
				contentScale = ContentScale.Crop,
				modifier = modifier.size(size).clip(CircleShape))
		return
	}
	val base = colorFor(colorKey)
	Box(
			modifier
					.size(size)
					.clip(CircleShape)
					.background(Brush.linearGradient(
							listOf(base, base.copy(alpha = 0.82f)))),
			contentAlignment = Alignment.Center,
	) {
		Text(
				text = initials(name),
				color = Color.White,
				fontWeight = FontWeight.SemiBold,
				textAlign = TextAlign.Center,
				fontSize = (size.value * 0.4f).sp,
		)
	}
}

@Composable
fun AvatarWithPresence(
		name: String,
		colorKey: Int,
		connected: Boolean,
		size: Dp = 44.dp,
		modifier: Modifier = Modifier,
		photo: ByteArray? = null,
) {
	Box(modifier) {
		Avatar(name, colorKey, size, photo = photo)
		val dot = size * 0.28f
		PresenceDot(
				connected = connected,
				modifier = Modifier
						.align(Alignment.BottomEnd)
						.size(dot),
		)
	}
}

@Composable
private fun PresenceDot(connected: Boolean, modifier: Modifier = Modifier) {
	Box(
			modifier
					.clip(CircleShape)
					.background(MaterialTheme.colorScheme.surface),
			contentAlignment = Alignment.Center,
	) {
		Box(
				Modifier
						.size(if (connected) 8.dp else 7.dp)
						.clip(CircleShape)
						.background(if (connected) ConnectedGreen
								else OfflineGray),
		)
	}
}
