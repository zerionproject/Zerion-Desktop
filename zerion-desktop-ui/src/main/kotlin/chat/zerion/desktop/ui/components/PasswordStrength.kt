package chat.zerion.desktop.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/**
 * Password-strength scoring and a reusable field that reflects strength in the
 * colour of its show/hide (eye) toggle and an optional meter, matching the
 * requested green / yellow / orange / red scale.
 *
 * The score is a simple, transparent heuristic (length + character-class
 * variety). It is a UX hint only; the account password itself is stretched with
 * Argon2id and the database is AES-encrypted regardless of what this shows.
 */
enum class PwStrength(val label: String, val color: Color, val fraction: Float) {
	WEAK("Weak", Color(0xFFE74C3C), 0.25f),
	FAIR("Fair", Color(0xFFE67E22), 0.55f),
	GOOD("Good", Color(0xFFF1C40F), 0.8f),
	STRONG("Strong", Color(0xFF2ECC71), 1f),
}

fun passwordStrength(pw: String): PwStrength? {
	if (pw.isEmpty()) return null
	val len = pw.length
	var classes = 0
	if (pw.any { it.isLowerCase() }) classes++
	if (pw.any { it.isUpperCase() }) classes++
	if (pw.any { it.isDigit() }) classes++
	if (pw.any { !it.isLetterOrDigit() }) classes++
	return when {
		len >= 12 && classes >= 3 -> PwStrength.STRONG
		len >= 10 && classes >= 2 -> PwStrength.GOOD
		len >= 8 && classes >= 2 -> PwStrength.FAIR
		len >= 8 -> PwStrength.FAIR
		else -> PwStrength.WEAK
	}
}

/** Minimum strength we accept for security-critical gates (device linking). */
fun isStrongEnoughToLink(pw: String): Boolean {
	val s = passwordStrength(pw) ?: return false
	return s == PwStrength.GOOD || s == PwStrength.STRONG
}

@Composable
fun StrengthPasswordField(
		label: String,
		value: String,
		onChange: (String) -> Unit,
		modifier: Modifier = Modifier,
		showMeter: Boolean = true,
) {
	var visible by remember { mutableStateOf(false) }
	val strength = passwordStrength(value)
	val eyeTint by animateColorAsState(
			strength?.color ?: MaterialTheme.colorScheme.onSurfaceVariant)
	OutlinedTextField(
			value = value,
			onValueChange = onChange,
			label = { Text(label) },
			singleLine = true,
			visualTransformation = if (visible) VisualTransformation.None
					else PasswordVisualTransformation(),
			trailingIcon = {
				IconButton(onClick = { visible = !visible }) {
					Icon(
							if (visible) Icons.Filled.VisibilityOff
									else Icons.Filled.Visibility,
							contentDescription = if (visible) "Hide password"
									else "Show password",
							tint = eyeTint)
				}
			},
			modifier = modifier.fillMaxWidth())
	if (showMeter && strength != null) {
		StrengthMeter(strength)
	}
}

@Composable
private fun StrengthMeter(strength: PwStrength) {
	val color by animateColorAsState(strength.color)
	Row(Modifier.fillMaxWidth().padding(top = 6.dp, start = 4.dp, end = 4.dp),
			verticalAlignment = Alignment.CenterVertically) {
		androidx.compose.foundation.layout.Box(
				Modifier.weight(1f).height(6.dp)
						.background(MaterialTheme.colorScheme.surfaceContainerHighest,
								RoundedCornerShape(3.dp))) {
			androidx.compose.foundation.layout.Box(
					Modifier.fillMaxWidth(strength.fraction).height(6.dp)
							.background(color, RoundedCornerShape(3.dp)))
		}
		Spacer(Modifier.width(10.dp))
		Text(strength.label,
				style = MaterialTheme.typography.labelMedium,
				fontWeight = FontWeight.Medium,
				color = color)
	}
}
