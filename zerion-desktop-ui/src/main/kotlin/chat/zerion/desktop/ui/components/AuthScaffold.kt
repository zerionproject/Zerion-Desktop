package chat.zerion.desktop.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Shared accent + dark scheme for the pre-sign-in screens. */
val AuthAccent = Color(0xFF26B7F0)

val AuthScheme = darkColorScheme(
		primary = AuthAccent,
		onPrimary = Color.White,
		background = Color(0xFF0B0E15),
		onBackground = Color(0xFFECEFF4),
		surface = Color(0xFF141A24),
		onSurface = Color(0xFFECEFF4),
		onSurfaceVariant = Color(0xFF9AA5B4),
)

/** Translucent card colour used for content sitting over the particle field. */
val AuthCard = Color(0xFF141A24).copy(alpha = 0.82f)

/**
 * Common branded background for the profile-select and sign-in screens: the
 * animated particle field, a fixed dark scheme, and a centred column headed by
 * the ZERION wordmark and a subtitle. Callers supply the content below.
 */
@Composable
fun AuthScreen(
		subtitle: String,
		content: @Composable ColumnScope.() -> Unit,
) {
	MaterialTheme(colorScheme = AuthScheme) {
		Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
			ParticleField(Modifier.fillMaxSize())
			Column(
					Modifier.widthIn(max = 420.dp).fillMaxWidth().padding(24.dp),
					horizontalAlignment = Alignment.CenterHorizontally,
					verticalArrangement = Arrangement.spacedBy(8.dp)) {
				Text("ZERION",
						color = Color.White,
						fontWeight = FontWeight.Bold,
						fontSize = 40.sp,
						letterSpacing = 8.sp)
				Text(subtitle,
						style = MaterialTheme.typography.bodyMedium,
						color = AuthScheme.onSurfaceVariant,
						textAlign = TextAlign.Center)
				Spacer12()
				content()
			}
		}
	}
}

@Composable
private fun Spacer12() {
	androidx.compose.foundation.layout.Spacer(Modifier.size(12.dp))
}
