package chat.zerion.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

import chat.zerion.desktop.ui.QrCode
import chat.zerion.desktop.ui.ZerionModel
import chat.zerion.desktop.ui.components.Avatar
import chat.zerion.desktop.ui.components.I2pStatusPill
import chat.zerion.desktop.ui.components.StrengthPasswordField
import chat.zerion.desktop.ui.components.TorStatusPill
import chat.zerion.desktop.ui.theme.ThemeMode

private const val DESKTOP_VERSION = "1.0.0"

private val AccentBlue = Color(0xFF26B7F0)

private enum class Section(val title: String) {
	MENU("Settings"),
	PROFILE("Profile"),
	APPEARANCE("Appearance"),
	PRIVACY("Privacy & security"),
	NOTIFICATIONS("Notifications"),
	NETWORK("Network"),
	CALLS("Calls"),
	DONATE("Support Zerion"),
	ABOUT("About"),
}

@Composable
fun SettingsScreen(model: ZerionModel, onBack: () -> Unit, onLogout: () -> Unit) {
	var section by remember { mutableStateOf(Section.MENU) }
	val toMenu = { section = Section.MENU }
	when (section) {
		Section.MENU -> SettingsMenu(model, onBack, onLogout) { section = it }
		else -> SettingsDetail(section.title, toMenu) {
			when (section) {
				Section.PROFILE -> ProfileBody(model)
				Section.APPEARANCE -> AppearanceBody(model)
				Section.PRIVACY -> PrivacyBody(model)
				Section.NOTIFICATIONS -> NotificationsBody(model)
				Section.NETWORK -> NetworkBody(model)
				Section.CALLS -> CallsBody(model)
				Section.DONATE -> DonateBody()
				Section.ABOUT -> AboutBody()
				Section.MENU -> {}
			}
		}
	}
}


@Composable
private fun SettingsScaffold(
		title: String,
		onBack: () -> Unit,
		content: ColumnScopeContent,
) {
	Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
		Row(Modifier.fillMaxWidth()
				.background(MaterialTheme.colorScheme.surface)
				.padding(horizontal = 12.dp, vertical = 10.dp),
				verticalAlignment = Alignment.CenterVertically) {
			IconButton(onClick = onBack) {
				Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
			}
			Spacer(Modifier.width(4.dp))
			Text(title, style = MaterialTheme.typography.titleLarge,
					fontWeight = FontWeight.SemiBold)
		}
		Divider(color = MaterialTheme.colorScheme.outlineVariant)
		Column(Modifier.fillMaxWidth().weight(1f)
				.verticalScroll(rememberScrollState()),
				horizontalAlignment = Alignment.CenterHorizontally) {
			Column(Modifier.widthIn(max = 680.dp).fillMaxWidth().padding(24.dp),
					verticalArrangement = Arrangement.spacedBy(18.dp)) {
				content()
			}
		}
	}
}

private typealias ColumnScopeContent =
		@Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit

@Composable
private fun SettingsDetail(
		title: String,
		onBack: () -> Unit,
		body: ColumnScopeContent,
) = SettingsScaffold(title, onBack, body)

@Composable
private fun SettingsGroup(
		title: String?,
		content: ColumnScopeContent,
) {
	Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
		if (title != null) {
			Text(title.uppercase(),
					style = MaterialTheme.typography.labelMedium,
					fontWeight = FontWeight.SemiBold,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					modifier = Modifier.padding(start = 6.dp))
		}
		Card(Modifier.fillMaxWidth(),
				colors = CardDefaults.cardColors(
						containerColor = MaterialTheme.colorScheme.surface)) {
			Column(Modifier.fillMaxWidth(), content = content)
		}
	}
}

@Composable
private fun MenuRow(
		icon: ImageVector,
		tint: Color,
		title: String,
		subtitle: String?,
		onClick: () -> Unit,
) {
	Row(Modifier.fillMaxWidth().clickable(onClick = onClick)
			.padding(horizontal = 14.dp, vertical = 13.dp),
			verticalAlignment = Alignment.CenterVertically) {
		Box(Modifier.size(34.dp).background(tint.copy(alpha = 0.16f),
				RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
			Icon(icon, contentDescription = null, tint = tint,
					modifier = Modifier.size(19.dp))
		}
		Spacer(Modifier.width(14.dp))
		Column(Modifier.weight(1f)) {
			Text(title, style = MaterialTheme.typography.bodyLarge)
			if (subtitle != null) {
				Text(subtitle, style = MaterialTheme.typography.labelMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant)
			}
		}
		Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null,
				tint = MaterialTheme.colorScheme.onSurfaceVariant)
	}
}

@Composable
private fun RowDivider() {
	Divider(Modifier.padding(start = 62.dp),
			color = MaterialTheme.colorScheme.outlineVariant)
}

/** A titled card used inside a detail screen for a block of controls. */
@Composable
private fun DetailCard(content: ColumnScopeContent) {
	Card(Modifier.fillMaxWidth(),
			colors = CardDefaults.cardColors(
					containerColor = MaterialTheme.colorScheme.surface)) {
		Column(Modifier.padding(18.dp),
				verticalArrangement = Arrangement.spacedBy(14.dp),
				content = content)
	}
}


@Composable
private fun SettingsMenu(
		model: ZerionModel,
		onBack: () -> Unit,
		onLogout: () -> Unit,
		onOpen: (Section) -> Unit,
) {
	SettingsScaffold("Settings", onBack) {
		Card(Modifier.fillMaxWidth().clickable { onOpen(Section.PROFILE) },
				colors = CardDefaults.cardColors(
						containerColor = MaterialTheme.colorScheme.surface)) {
			Row(Modifier.fillMaxWidth().padding(16.dp),
					verticalAlignment = Alignment.CenterVertically) {
				Avatar(model.localName.ifEmpty { "Z" }, 0, size = 52.dp,
						photo = model.myAvatar)
				Spacer(Modifier.width(14.dp))
				Column(Modifier.weight(1f)) {
					Text(model.localName.ifEmpty { "Zerion" },
							style = MaterialTheme.typography.titleMedium,
							fontWeight = FontWeight.SemiBold)
					Text("Profile, photo and invite link",
							style = MaterialTheme.typography.labelMedium,
							color = MaterialTheme.colorScheme.onSurfaceVariant)
				}
				Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight,
						contentDescription = null,
						tint = MaterialTheme.colorScheme.onSurfaceVariant)
			}
		}

		SettingsGroup("General") {
			MenuRow(Icons.Filled.DarkMode, Color(0xFF9B59B6), "Appearance",
					"Theme", { onOpen(Section.APPEARANCE) })
			RowDivider()
			MenuRow(Icons.Filled.Notifications, Color(0xFFE67E22),
					"Notifications", "Message and call alerts",
					{ onOpen(Section.NOTIFICATIONS) })
			RowDivider()
			MenuRow(Icons.Filled.Hub, AccentBlue, "Network",
					"Tor, offline mode and I2P", { onOpen(Section.NETWORK) })
			RowDivider()
			MenuRow(Icons.Filled.Call, Color(0xFF2ECC71), "Calls",
					"Voice calls over Tor", { onOpen(Section.CALLS) })
		}

		SettingsGroup("Security") {
			MenuRow(Icons.Filled.Shield, Color(0xFF3498DB), "Privacy & security",
					"Password, duress password", { onOpen(Section.PRIVACY) })
			RowDivider()
		}

		SettingsGroup("About") {
			MenuRow(Icons.Filled.Favorite, Color(0xFFE74C3C), "Support Zerion",
					"Donate to fund development", { onOpen(Section.DONATE) })
			RowDivider()
			MenuRow(Icons.Filled.Info, Color(0xFF95A5A6), "About Zerion",
					"Version and protocols", { onOpen(Section.ABOUT) })
		}

		OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
			Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null,
					modifier = Modifier.size(18.dp))
			Spacer(Modifier.width(8.dp))
			Text("Sign out")
		}
		Text("Zerion Desktop $DESKTOP_VERSION",
				style = MaterialTheme.typography.labelMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				modifier = Modifier.padding(start = 6.dp))
	}
}


@Composable
private fun ProfileBody(model: ZerionModel) {
	val clipboard = LocalClipboardManager.current
	val scope = androidx.compose.runtime.rememberCoroutineScope()
	DetailCard {
		Row(verticalAlignment = Alignment.CenterVertically) {
			Avatar(model.localName.ifEmpty { "Z" }, 0, size = 56.dp,
					photo = model.myAvatar)
			Spacer(Modifier.width(14.dp))
			Column(Modifier.weight(1f)) {
				Text(model.localName.ifEmpty { "Zerion" },
						style = MaterialTheme.typography.titleMedium,
						fontWeight = FontWeight.SemiBold)
				Text("Your display name",
						style = MaterialTheme.typography.labelMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant)
			}
			TextButton(onClick = {
				val file = chooseImageFile()
				if (file != null) model.setMyAvatar(file) {}
			}) { Text("Change photo") }
		}
	}
	DetailCard {
		Text("Invite link",
				style = MaterialTheme.typography.titleSmall,
				fontWeight = FontWeight.SemiBold)
		Text("Share this link so someone can add you as a contact. It's tied " +
				"only to your Tor address — no phone number, no account.",
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant)
		Row(verticalAlignment = Alignment.CenterVertically) {
			OutlinedTextField(
					value = model.myLink ?: "Generating…",
					onValueChange = {},
					readOnly = true,
					singleLine = true,
					modifier = Modifier.weight(1f))
			IconButton(
					onClick = {
						model.myLink?.let {
							chat.zerion.desktop.ui.components.SecureClipboard
									.copyWithAutoClear(clipboard, scope, it)
						}
					},
					enabled = model.myLink != null) {
				Icon(Icons.Filled.ContentCopy, contentDescription = "Copy link")
			}
		}
		Text("Copied links clear from the clipboard automatically after a " +
				"minute.",
				style = MaterialTheme.typography.labelSmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant)
	}
}

@Composable
private fun AppearanceBody(model: ZerionModel) {
	DetailCard {
		Text("Theme", style = MaterialTheme.typography.titleSmall,
				fontWeight = FontWeight.SemiBold)
		Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
			ThemeMode.entries.forEach { mode ->
				FilterChip(
						selected = model.themeMode == mode,
						onClick = { model.selectTheme(mode) },
						label = {
							Text(mode.name.lowercase()
									.replaceFirstChar { it.uppercase() })
						})
			}
		}
	}
}

@Composable
private fun NotificationsBody(model: ZerionModel) {
	DetailCard {
		ToggleRow(
				title = "Notifications",
				subtitle = "Show a tray notification for new activity and " +
						"incoming calls. Only the sender's name is shown, never " +
						"message content.",
				checked = model.notificationsEnabled,
				onChange = { model.applyNotifications(it) })
		val on = model.notificationsEnabled
		Divider(color = MaterialTheme.colorScheme.outlineVariant)
		ToggleRow(
				title = "Private messages",
				subtitle = "Notify on new one-to-one messages.",
				checked = model.notifyPrivate,
				onChange = { model.applyNotifyPrivate(it) },
				enabled = on)
		Divider(color = MaterialTheme.colorScheme.outlineVariant)
		ToggleRow(
				title = "Group messages",
				subtitle = "Notify on new messages in private groups.",
				checked = model.notifyGroups,
				onChange = { model.applyNotifyGroups(it) },
				enabled = on)
		Divider(color = MaterialTheme.colorScheme.outlineVariant)
		ToggleRow(
				title = "Channel posts",
				subtitle = "Notify on new posts in channels you follow.",
				checked = model.notifyChannels,
				onChange = { model.applyNotifyChannels(it) },
				enabled = on)
		Divider(color = MaterialTheme.colorScheme.outlineVariant)
		ToggleRow(
				title = "Sound",
				subtitle = "Play the system alert sound with each notification.",
				checked = model.notifySound,
				onChange = { model.applyNotifySound(it) },
				enabled = on)
	}
}

@Composable
private fun NetworkBody(model: ZerionModel) {
	DetailCard {
		Text("Connection", style = MaterialTheme.typography.titleSmall,
				fontWeight = FontWeight.SemiBold)
		TorStatusPill(model.torState)
		Divider(color = MaterialTheme.colorScheme.outlineVariant)
		ToggleRow(
				title = "Offline mode",
				subtitle = "Stop all connections. Nobody can reach you and you " +
						"can't reach anyone.",
				checked = model.offlineMode,
				onChange = { model.applyOfflineMode(it) })
		Divider(color = MaterialTheme.colorScheme.outlineVariant)
		ToggleRow(
				title = "I2P (extra network)",
				subtitle = "Also reach contacts over the I2P network. The " +
						"router reseeds over Tor and can take a few minutes to " +
						"build tunnels the first time.",
				checked = model.i2pEnabled,
				onChange = { model.applyI2p(it) })
		if (model.i2pEnabled) I2pStatusPill(model.i2pState)
	}
	Spacer(Modifier.size(12.dp))
	TorNetworkCard(model)
}

@Composable
private fun TorNetworkCard(model: ZerionModel) {
	var mode by remember(model.torNetworkMode) {
		mutableStateOf(model.torNetworkMode)
	}
	var bridges by remember(model.customBridges) {
		mutableStateOf(model.customBridges)
	}
	val dirty = mode != model.torNetworkMode || bridges != model.customBridges
	DetailCard {
		Text("Tor network", style = MaterialTheme.typography.titleSmall,
				fontWeight = FontWeight.SemiBold)
		Text("Direct Tor is the normal mode. Use bridges only if Tor is " +
				"blocked on your network. Traffic always stays on Tor — if a " +
				"bridge can't be reached, the connection fails rather than " +
				"falling back.",
				style = MaterialTheme.typography.labelMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant)
		Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
			FilterChip(mode == 0, { mode = 0 },
					label = { Text("Automatic") })
			FilterChip(mode == 1, { mode = 1 },
					label = { Text("Direct") })
			FilterChip(mode == 2, { mode = 2 },
					label = { Text("Bridges") })
		}
		if (mode == 2) {
			OutlinedTextField(
					value = bridges,
					onValueChange = { bridges = it },
					label = { Text("Bridge lines (one per line)") },
					placeholder = { Text("obfs4 1.2.3.4:443 CERT… iat-mode=0") },
					modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp),
					maxLines = 6)
			Text("Leave empty to use the built-in bridges. Each line is a " +
					"standard Tor bridge line.",
					style = MaterialTheme.typography.labelSmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant)
		}
		Button(onClick = { model.applyTorNetwork(mode, bridges) },
				enabled = dirty,
				modifier = Modifier.align(Alignment.End)) {
			Text("Apply")
		}
	}
}

@Composable
private fun CallAudioCard(model: ZerionModel) {
	val inputs = remember { model.inputDevices() }
	val outputs = remember { model.outputDevices() }
	DetailCard {
		Text("Call audio", style = MaterialTheme.typography.titleSmall,
				fontWeight = FontWeight.SemiBold)
		Text("Choose which microphone and speaker calls use. This only selects " +
				"a local device and does not change the call itself.",
				style = MaterialTheme.typography.labelMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant)
		DevicePicker("Microphone", inputs, model.micDevice) {
			model.applyMicDevice(it)
		}
		DevicePicker("Speaker", outputs, model.speakerDevice) {
			model.applySpeakerDevice(it)
		}
	}
}

@Composable
private fun DevicePicker(label: String, devices: List<String>,
		selected: String?, onSelect: (String?) -> Unit) {
	var open by remember { mutableStateOf(false) }
	Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
		Text(label, Modifier.weight(1f),
				style = MaterialTheme.typography.bodyMedium)
		Box {
			OutlinedButton(onClick = { open = true }) {
				Text(selected ?: "System default", maxLines = 1)
			}
			DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
				DropdownMenuItem(text = { Text("System default") },
						onClick = { onSelect(null); open = false })
				devices.forEach { d ->
					DropdownMenuItem(text = { Text(d) },
							onClick = { onSelect(d); open = false })
				}
			}
		}
	}
}

@Composable
private fun CallsBody(model: ZerionModel) {
	DetailCard {
		ToggleRow(
				title = "Enable voice calls",
				subtitle = "Off by default. When off, you can't place calls and " +
						"incoming calls are declined automatically without " +
						"ringing — your microphone is never opened. Turn this on " +
						"only when you want to make or receive calls.",
				checked = model.callsEnabled,
				onChange = { model.applyCallsEnabled(it) })
	}
	CallAudioCard(model)
	DetailCard {
		Text("How calls work", style = MaterialTheme.typography.titleSmall,
				fontWeight = FontWeight.SemiBold)
		Text("Voice calls are end-to-end encrypted with AES-256-GCM and carried " +
				"over a dedicated Tor stream — the same protocol as Zerion on " +
				"mobile, so desktop and phone can call each other. Start a call " +
				"from the phone icon at the top of a conversation.",
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant)
		Text("Your microphone is opened only for the duration of a connected " +
				"call and released the moment it ends. Video calls are not " +
				"available on desktop; incoming video calls connect as voice.",
				style = MaterialTheme.typography.labelMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant)
	}
}


@Composable
private fun PrivacyBody(model: ZerionModel) {
	DefaultTimerCard(model)
	ChangePasswordCard(model)
	DuressCard(model)
}

@Composable
private fun DefaultTimerCard(model: ZerionModel) {
	val options = listOf(
			"Off" to -1L,
			"1 hour" to 3_600_000L,
			"1 day" to 86_400_000L,
			"1 week" to 604_800_000L)
	var open by remember { mutableStateOf(false) }
	val current = options.firstOrNull { it.second == model.defaultDisappearingMs }
			?.first ?: "Off"
	DetailCard {
		Text("Disappearing messages", style = MaterialTheme.typography.titleSmall,
				fontWeight = FontWeight.SemiBold)
		Text("Sets the default timer for conversations you start from now on. " +
				"Existing conversations keep their own setting.",
				style = MaterialTheme.typography.labelMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant)
		Row(Modifier.fillMaxWidth(),
				verticalAlignment = Alignment.CenterVertically) {
			Text("Default timer", Modifier.weight(1f),
					style = MaterialTheme.typography.bodyMedium)
			Box {
				OutlinedButton(onClick = { open = true }) { Text(current) }
				DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
					options.forEach { (label, ms) ->
						DropdownMenuItem(text = { Text(label) },
								onClick = { model.applyDefaultTimer(ms); open = false })
					}
				}
			}
		}
	}
}

@Composable
private fun ChangePasswordCard(model: ZerionModel) {
	var current by remember { mutableStateOf("") }
	var next by remember { mutableStateOf("") }
	var confirm by remember { mutableStateOf("") }
	var message by remember { mutableStateOf<String?>(null) }
	var success by remember { mutableStateOf(false) }
	var busy by remember { mutableStateOf(false) }

	DetailCard {
		Text("Change password", style = MaterialTheme.typography.titleSmall,
				fontWeight = FontWeight.SemiBold)
		PlainPasswordField("Current password", current) { current = it }
		StrengthPasswordField("New password", next, { next = it })
		PlainPasswordField("Confirm new password", confirm) { confirm = it }
		if (message != null) {
			Text(message!!,
					color = if (success) MaterialTheme.colorScheme.primary
					else MaterialTheme.colorScheme.error,
					style = MaterialTheme.typography.labelMedium)
		}
		OutlinedButton(
				onClick = {
					when {
						next != confirm -> {
							message = "New passwords don't match."; success = false
						}
						next.isEmpty() -> {
							message = "Enter a new password."; success = false
						}
						else -> {
							busy = true; message = null
							model.changePassword(current.toCharArray(),
									next.toCharArray()) { err ->
								busy = false
								success = err == null
								message = err ?: "Password changed successfully."
								if (err == null) {
									current = ""; next = ""; confirm = ""
								}
							}
						}
					}
				},
				enabled = !busy && current.isNotEmpty() && next.isNotEmpty()) {
			Text(if (busy) "Changing…" else "Change password")
		}
	}
}

@Composable
private fun DuressCard(model: ZerionModel) {
	var pw by remember { mutableStateOf("") }
	var confirm by remember { mutableStateOf("") }
	var message by remember { mutableStateOf<String?>(null) }

	DetailCard {
		Text("Duress password", style = MaterialTheme.typography.titleSmall,
				fontWeight = FontWeight.SemiBold)
		Text("Set a second password. If it is ever entered at sign-in, this " +
				"profile and all its data are permanently and irreversibly " +
				"wiped from this device instead of unlocking.",
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant)
		if (model.hasDuress) {
			Text("A duress password is set.",
					style = MaterialTheme.typography.labelMedium,
					color = MaterialTheme.colorScheme.primary)
			OutlinedButton(onClick = { model.removeDuressPassword() }) {
				Text("Remove duress password")
			}
		} else {
			StrengthPasswordField("Duress password", pw,
					{ pw = it; message = null })
			PlainPasswordField("Confirm", confirm) { confirm = it; message = null }
			if (message != null) {
				Text(message!!, color = MaterialTheme.colorScheme.error,
						style = MaterialTheme.typography.labelMedium)
			}
			OutlinedButton(
					onClick = {
						when {
							pw.length < 4 -> message = "Use at least 4 characters."
							pw != confirm -> message = "Passwords don't match."
							else -> {
								model.setDuressPassword(pw.toCharArray())
								pw = ""; confirm = ""
							}
						}
					},
					enabled = pw.isNotEmpty()) { Text("Set duress password") }
		}
	}
}

@Composable
private fun PlainPasswordField(
		label: String,
		value: String,
		onChange: (String) -> Unit,
) {
	OutlinedTextField(
			value = value,
			onValueChange = onChange,
			label = { Text(label) },
			singleLine = true,
			visualTransformation = PasswordVisualTransformation(),
			modifier = Modifier.fillMaxWidth())
}


private data class DonateCoin(
		val chip: String,
		val name: String,
		val network: String,
		val address: String,
)

private val DONATE_COINS = listOf(
		DonateCoin("BTC", "Bitcoin", "Bitcoin network",
				"bc1q5hfmyzkadwww9r96sff2ew36ctksmyapucx4kq"),
		DonateCoin("XMR", "Monero", "Monero network",
				"89GAQXYpdb13ReGi1c86PrFqxheEBfoB3ekoSL1AWUcV9DfH9PKn" +
						"faRRmoispTUSymKK3ykPK4tdYX1uiLxTNjPC8eGX9V4"),
		DonateCoin("ETH", "Ethereum / USDT", "ERC-20",
				"0x8F639ec074a4d89546e61bDd84F081EE61E1FCF6"))

@Composable
private fun DonateBody() {
	val clipboard = LocalClipboardManager.current
	var selected by remember { mutableStateOf(0) }
	val coin = DONATE_COINS[selected]

	DetailCard {
		Text("Zerion is free and open-source, with no ads, no tracking, and no " +
				"corporate backing. Donations fund development, security audits, " +
				"and infrastructure.",
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant)
		Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
			DONATE_COINS.forEachIndexed { i, c ->
				FilterChip(
						selected = selected == i,
						onClick = { selected = i },
						label = { Text(c.chip) })
			}
		}
	}

	DetailCard {
		Text(coin.name, style = MaterialTheme.typography.titleSmall,
				fontWeight = FontWeight.SemiBold)
		Text(coin.network, style = MaterialTheme.typography.labelMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant)
		Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
			val qr = remember(coin.address) { QrCode.pngFor(coin.address, 320) }
			val bitmap = remember(qr) {
				org.jetbrains.skia.Image.makeFromEncoded(qr).toComposeImageBitmap()
			}
			Box(Modifier.background(Color.White, RoundedCornerShape(12.dp))
					.padding(10.dp)) {
				androidx.compose.foundation.Image(
						bitmap = bitmap,
						contentDescription = "${coin.name} donation address QR",
						modifier = Modifier.size(200.dp))
			}
		}
		Box(Modifier.fillMaxWidth()
				.background(MaterialTheme.colorScheme.surfaceContainerHighest,
						RoundedCornerShape(10.dp))
				.padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp)) {
			Row(verticalAlignment = Alignment.CenterVertically) {
				Text(coin.address, Modifier.weight(1f),
						style = MaterialTheme.typography.bodyMedium,
						fontFamily = FontFamily.Monospace)
				IconButton(onClick = {
					clipboard.setText(AnnotatedString(coin.address))
				}) {
					Icon(Icons.Filled.ContentCopy,
							contentDescription = "Copy ${coin.name} address")
				}
			}
		}
		Text("Thank you for your support. Every contribution goes straight into " +
				"development.",
				style = MaterialTheme.typography.labelMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant)
	}
}

@Composable
private fun AboutBody() {
	DetailCard {
		Text("Zerion Desktop $DESKTOP_VERSION",
				style = MaterialTheme.typography.titleSmall,
				fontWeight = FontWeight.SemiBold)
		Text("A private messenger. All traffic is routed over the Tor network " +
				"and protected with post-quantum encryption. No servers, no " +
				"accounts, no metadata.",
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant)
		Divider(color = MaterialTheme.colorScheme.outlineVariant)
		Text("Built on Zerion's own protocols: ZTP transport, ZWF fixed-size " +
				"framing, ZPP constant-rate pull with cover traffic, and the ZMM " +
				"message model.",
				style = MaterialTheme.typography.labelMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant)
	}
}


@Composable
private fun ToggleRow(
		title: String,
		subtitle: String,
		checked: Boolean,
		onChange: (Boolean) -> Unit,
		enabled: Boolean = true,
) {
	Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
		Column(Modifier.weight(1f)) {
			Text(title, style = MaterialTheme.typography.bodyLarge,
					fontWeight = FontWeight.Medium)
			Text(subtitle, style = MaterialTheme.typography.labelMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant)
		}
		Spacer(Modifier.width(12.dp))
		Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
	}
}

private fun chooseImageFile(): java.io.File? {
	val dialog = java.awt.FileDialog(null as java.awt.Frame?, "Choose a photo",
			java.awt.FileDialog.LOAD)
	dialog.setFilenameFilter { _, name ->
		val n = name.lowercase()
		listOf(".jpg", ".jpeg", ".png", ".gif", ".bmp").any { n.endsWith(it) }
	}
	dialog.isVisible = true
	val dir = dialog.directory
	val name = dialog.file
	return if (dir != null && name != null) java.io.File(dir, name) else null
}
