package chat.zerion.desktop.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Cyan = Color(0xFF26B7F0)
private val CyanDeep = Color(0xFF1A92C4)
private val DarkBg = Color(0xFF0F1014)
private val CardBg = Color(0xFF1C1D22)
private val SurfaceElevated = Color(0xFF252631)
private val SurfacePressed = Color(0xFF2E3039)
private val TextPrimary = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xFF9CA3AF)

private val Cloud0 = Color(0xFFFFFFFF)
private val Cloud50 = Color(0xFFF6F8FB)
private val Cloud100 = Color(0xFFEEF1F6)
private val Cloud200 = Color(0xFFDCE1EA)
private val Cloud300 = Color(0xFFC4CBD6)
private val InkText = Color(0xFF0F1014)

val ConnectedGreen = Color(0xFF22C55E)
val ConnectingAmber = Color(0xFFF59E0B)
val OfflineGray = Color(0xFF9CA3AF)
val SentBubble = CyanDeep
private val ErrorRed = Color(0xFFF43F5E)

private val DarkColors = darkColorScheme(
		primary = Cyan,
		onPrimary = Color(0xFF04222E),
		primaryContainer = CyanDeep,
		onPrimaryContainer = TextPrimary,
		secondary = Cyan,
		onSecondary = Color(0xFF04222E),
		background = DarkBg,
		onBackground = TextPrimary,
		surface = CardBg,
		onSurface = TextPrimary,
		surfaceVariant = SurfaceElevated,
		onSurfaceVariant = TextSecondary,
		surfaceContainer = Color(0xFF17181D),
		surfaceContainerHigh = SurfaceElevated,
		surfaceContainerHighest = SurfacePressed,
		outline = SurfacePressed,
		outlineVariant = SurfaceElevated,
		error = ErrorRed,
		onError = TextPrimary,
)

private val LightColors = lightColorScheme(
		primary = CyanDeep,
		onPrimary = Cloud0,
		primaryContainer = Color(0xFFBEE6F7),
		onPrimaryContainer = Color(0xFF04384A),
		secondary = Cyan,
		onSecondary = Cloud0,
		background = Cloud50,
		onBackground = InkText,
		surface = Cloud0,
		onSurface = InkText,
		surfaceVariant = Cloud100,
		onSurfaceVariant = Color(0xFF51606B),
		surfaceContainer = Cloud50,
		surfaceContainerHigh = Cloud100,
		surfaceContainerHighest = Cloud200,
		outline = Cloud300,
		outlineVariant = Cloud200,
		error = ErrorRed,
		onError = Cloud0,
)

private val ZerionTypography = Typography(
		headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold,
				fontSize = 26.sp, lineHeight = 32.sp),
		titleLarge = TextStyle(fontWeight = FontWeight.SemiBold,
				fontSize = 20.sp, lineHeight = 26.sp),
		titleMedium = TextStyle(fontWeight = FontWeight.Medium,
				fontSize = 16.sp, lineHeight = 22.sp),
		bodyLarge = TextStyle(fontWeight = FontWeight.Normal,
				fontSize = 15.sp, lineHeight = 21.sp),
		bodyMedium = TextStyle(fontWeight = FontWeight.Normal,
				fontSize = 14.sp, lineHeight = 20.sp),
		labelLarge = TextStyle(fontWeight = FontWeight.Medium,
				fontSize = 14.sp, lineHeight = 18.sp),
		labelMedium = TextStyle(fontWeight = FontWeight.Medium,
				fontSize = 12.sp, lineHeight = 16.sp),
)

enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Composable
fun ZerionTheme(
		mode: ThemeMode = ThemeMode.DARK,
		content: @Composable () -> Unit,
) {
	val dark = when (mode) {
		ThemeMode.SYSTEM -> isSystemInDarkTheme()
		ThemeMode.LIGHT -> false
		ThemeMode.DARK -> true
	}
	MaterialTheme(
			colorScheme = if (dark) DarkColors else LightColors,
			typography = ZerionTypography,
			content = content,
	)
}
