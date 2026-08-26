package chat.zerion.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

import chat.zerion.desktop.DesktopBoot
import chat.zerion.desktop.ZerionDesktopComponent
import chat.zerion.desktop.ui.screens.MainScreen
import chat.zerion.desktop.ui.screens.ProfileSelectScreen
import chat.zerion.desktop.ui.screens.SignInScreen
import chat.zerion.desktop.ui.theme.ThemeMode
import chat.zerion.desktop.ui.theme.ZerionTheme

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private sealed interface AppState {
	data object Loading : AppState
	data class ProfileSelect(
			val profiles: List<DesktopProfiles.Profile>) : AppState
	data class Preparing(val profile: DesktopProfiles.Profile) : AppState
	data class SignIn(
			val profile: DesktopProfiles.Profile,
			val component: ZerionDesktopComponent,
			val firstRun: Boolean) : AppState
	data class Ready(val model: ZerionModel) : AppState
	data class SigningOut(val model: ZerionModel) : AppState
}

fun main() = application {
	OpenCache.purgeStale()
	val windowState = rememberWindowState(width = 1040.dp, height = 720.dp)
	Window(
			onCloseRequest = ::exitApplication,
			state = windowState,
			title = "Zerion") {
		ZerionApp()
	}
}

@Composable
private fun ZerionApp() {
	var state by remember { mutableStateOf<AppState>(AppState.Loading) }
	var error by remember { mutableStateOf<String?>(null) }
	var pendingAuth by remember { mutableStateOf<CharArray?>(null) }

	val themeMode = (state as? AppState.Ready)?.model?.themeMode
			?: ThemeMode.DARK
	ZerionTheme(themeMode) {
		Surface(color = MaterialTheme.colorScheme.background) {
			when (val s = state) {
				is AppState.Loading -> {
					LaunchedEffect(Unit) {
						state = AppState.ProfileSelect(
								withContext(Dispatchers.IO) {
									DesktopProfiles.list()
								})
					}
					LoadingScreen("Loading Zerion…")
				}
				is AppState.ProfileSelect -> ProfileSelectScreen(
						profiles = s.profiles,
						onSelect = { error = null; state = AppState.Preparing(it) },
						onCreate = { name ->
							error = null
							state = AppState.Preparing(
									DesktopProfiles.create(name))
						},
						onDelete = {
							DesktopProfiles.delete(it.id)
							state = AppState.Loading
						})
				is AppState.Preparing -> {
					LaunchedEffect(s.profile.id) {
						val c = withContext(Dispatchers.IO) {
							DesktopBoot.build(s.profile.dataDir)
						}
						val firstRun = withContext(Dispatchers.IO) {
							!DesktopBoot.accountExists(c)
						}
						state = AppState.SignIn(s.profile, c, firstRun)
					}
					LoadingScreen("Opening ${s.profile.name}…")
				}
				is AppState.SignIn -> {
					val auth = pendingAuth
					if (auth != null) {
						BootEffect(s.profile, s.component, s.profile.name, auth,
								s.firstRun) { err ->
							pendingAuth = null
							when (err) {
								null -> {
									DesktopProfiles.setLastActive(s.profile.id)
									val model = ZerionModel(s.component,
											s.profile.dataDir)
									model.start()
									state = AppState.Ready(model)
								}
								DURESS_WIPED -> {
									error = null
									state = AppState.Loading
								}
								else -> error = err
							}
						}
					}
					SignInScreen(
							profileName = s.profile.name,
							firstRun = s.firstRun,
							busy = pendingAuth != null,
							error = error,
							onBack = {
								pendingAuth = null
								error = null
								state = AppState.Loading
							},
							onSubmit = { password ->
								if (pendingAuth == null) {
									error = null
									pendingAuth = password
								}
							})
				}
				is AppState.Ready -> {
					DisposableEffect(s.model) { onDispose { s.model.close() } }
					MainScreen(s.model, onLogout = {
						state = AppState.SigningOut(s.model)
					})
				}
				is AppState.SigningOut -> {
					LaunchedEffect(s.model) {
						withContext(Dispatchers.IO) { s.model.shutdownBlocking() }
						state = AppState.Loading
					}
					LoadingScreen("Signing out…")
				}
			}
		}
	}
}

private const val DURESS_WIPED = "__duress_wiped__"

@Composable
private fun BootEffect(
		profile: DesktopProfiles.Profile,
		component: ZerionDesktopComponent,
		name: String,
		password: CharArray,
		firstRun: Boolean,
		onResult: (String?) -> Unit,
) {
	LaunchedEffect(password) {
		val duress = withContext(Dispatchers.IO) {
			DesktopProfiles.isDuress(profile.dataDir, password)
		}
		if (duress) {
			withContext(Dispatchers.IO) {
				DesktopProfiles.secureWipe(profile.dataDir)
			}
			java.util.Arrays.fill(password, ' ')
			onResult(DURESS_WIPED)
			return@LaunchedEffect
		}
		val err = withContext(Dispatchers.IO) {
			DesktopBoot.signInAndStart(component, name, password, firstRun)
		}
		onResult(err)
	}
}

@Composable
private fun LoadingScreen(message: String) {
	Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
		Column(horizontalAlignment = Alignment.CenterHorizontally,
				verticalArrangement = Arrangement.spacedBy(16.dp)) {
			CircularProgressIndicator()
			Text(message,
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant)
		}
	}
}
