package chat.zerion.desktop.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import chat.zerion.desktop.ui.components.ParticleField
import chat.zerion.desktop.ui.components.StrengthPasswordField

private val Accent = Color(0xFF26B7F0)

private val LoginScheme = darkColorScheme(
		primary = Accent,
		onPrimary = Color.White,
		background = Color(0xFF0B0E15),
		onBackground = Color(0xFFECEFF4),
		surface = Color(0xFF141A24),
		onSurface = Color(0xFFECEFF4),
		onSurfaceVariant = Color(0xFF9AA5B4),
)

@Composable
fun SignInScreen(
		profileName: String,
		firstRun: Boolean,
		busy: Boolean,
		error: String?,
		onBack: () -> Unit,
		onSubmit: (CharArray) -> Unit,
) {
	var password by remember { mutableStateOf("") }
	val canSubmit = password.isNotEmpty() && !busy
	fun submit() {
		if (canSubmit) onSubmit(password.toCharArray())
	}

	MaterialTheme(colorScheme = LoginScheme) {
		Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
			ParticleField(Modifier.fillMaxSize())

			Column(
					Modifier.widthIn(max = 380.dp).padding(24.dp),
					horizontalAlignment = Alignment.CenterHorizontally,
					verticalArrangement = Arrangement.spacedBy(6.dp)) {

				Text("ZERION",
						color = Color.White,
						fontWeight = FontWeight.Bold,
						fontSize = 40.sp,
						letterSpacing = 8.sp)
				Text("Private messaging over Tor",
						style = MaterialTheme.typography.bodyMedium,
						color = LoginScheme.onSurfaceVariant,
						textAlign = TextAlign.Center)

				Box(Modifier.padding(top = 28.dp)) {
					Card(colors = CardDefaults.cardColors(
							containerColor = Color(0xFF141A24).copy(alpha = 0.82f)),
							shape = RoundedCornerShape(18.dp)) {
						Column(Modifier.padding(22.dp).widthIn(min = 300.dp),
								horizontalAlignment = Alignment.CenterHorizontally,
								verticalArrangement = Arrangement.spacedBy(14.dp)) {

							Text(profileName.ifBlank { "Zerion" },
									style = MaterialTheme.typography.titleLarge,
									color = Color.White)
							Text(
									if (firstRun)
										"Choose a password for this profile"
									else "Enter your password to unlock",
									style = MaterialTheme.typography.bodyMedium,
									color = LoginScheme.onSurfaceVariant,
									textAlign = TextAlign.Center)

							if (firstRun) {
								StrengthPasswordField("Password", password,
										{ password = it })
							} else {
								OutlinedTextField(
										value = password,
										onValueChange = { password = it },
										label = { Text("Password") },
										singleLine = true,
										enabled = !busy,
										visualTransformation =
												PasswordVisualTransformation(),
										isError = error != null,
										keyboardOptions = KeyboardOptions(
												imeAction = ImeAction.Done),
										keyboardActions = KeyboardActions(
												onDone = { submit() }),
										modifier = Modifier.fillMaxWidth())
							}

							if (error != null) {
								Text(error,
										color = MaterialTheme.colorScheme.error,
										style = MaterialTheme.typography.labelMedium,
										textAlign = TextAlign.Center)
							}

							Button(onClick = { submit() }, enabled = canSubmit,
									modifier = Modifier.fillMaxWidth()) {
								if (busy) {
									Row(verticalAlignment =
													Alignment.CenterVertically,
											horizontalArrangement =
													Arrangement.spacedBy(10.dp)) {
										CircularProgressIndicator(
												Modifier.size(18.dp),
												strokeWidth = 2.dp,
												color = Color.White)
										Text(if (firstRun) "Creating…"
												else "Unlocking…")
									}
								} else {
									Text(if (firstRun) "Create profile"
											else "Sign in")
								}
							}

							if (firstRun) {
								Text("There is no password recovery. If you " +
										"forget it, this profile's data cannot " +
										"be unlocked.",
										style = MaterialTheme.typography.labelMedium,
										color = LoginScheme.onSurfaceVariant,
										textAlign = TextAlign.Center)
							}
							TextButton(onClick = onBack) {
								Text("Back to profiles")
							}
						}
					}
				}
			}
		}
	}
}
