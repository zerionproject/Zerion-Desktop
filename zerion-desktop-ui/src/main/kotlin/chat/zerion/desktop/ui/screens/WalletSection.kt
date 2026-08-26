package chat.zerion.desktop.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

import chat.zerion.desktop.ui.QrCode
import chat.zerion.desktop.ui.components.SecureClipboard
import chat.zerion.desktop.ui.theme.ConnectedGreen
import chat.zerion.desktop.ui.components.StrengthPasswordField
import chat.zerion.desktop.ui.wallet.WalletModel

import java.math.BigDecimal
import java.math.RoundingMode

@Composable
fun WalletSection(wallet: WalletModel) {
	LaunchedEffect(Unit) { wallet.refreshWallets() }
	when {
		wallet.pendingMnemonic != null -> CreateFlow(wallet, wallet.pendingMnemonic!!)
		wallet.creatingXmr -> Column(Modifier.fillMaxSize(),
				verticalArrangement = Arrangement.Center,
				horizontalAlignment = Alignment.CenterHorizontally) {
			CircularProgressIndicator()
			Spacer(Modifier.size(12.dp))
			Text("Starting Monero over Tor…",
					color = MaterialTheme.colorScheme.onSurfaceVariant)
		}
		!wallet.listLoaded -> Box(Modifier.fillMaxSize(),
				contentAlignment = Alignment.Center) {
			CircularProgressIndicator()
		}
		!wallet.hasAnyWallet -> WalletSetup(wallet)
		else -> WalletHome(wallet)
	}
}


@Composable
private fun WalletSetup(wallet: WalletModel) {
	var showImport by remember { mutableStateOf(false) }
	Column(Modifier.fillMaxSize().padding(24.dp),
			verticalArrangement = Arrangement.spacedBy(12.dp),
			horizontalAlignment = Alignment.CenterHorizontally) {
		Spacer(Modifier.size(24.dp))
		Text("Crypto wallet", style = MaterialTheme.typography.titleLarge,
				fontWeight = FontWeight.SemiBold)
		Text("A non-custodial wallet inside your vault. Each wallet is a single " +
				"coin with its own recovery phrase and its own password, fully " +
				"isolated, and all network traffic goes over Tor.",
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant)
		Button(onClick = { wallet.beginCreate("ETH") },
				modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp)) {
			Text("New Ethereum wallet")
		}
		Button(onClick = { wallet.beginCreate("BTC") },
				modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp)) {
			Text("New Bitcoin wallet")
		}
		if (wallet.xmrAvailable) {
			Button(onClick = { wallet.beginCreate("XMR") },
					modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp)) {
				Text("New Monero wallet")
			}
		}
		OutlinedButton(onClick = { showImport = true },
				modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp)) {
			Text("Import recovery phrase")
		}
		if (wallet.error != null) {
			Text(wallet.error!!, color = MaterialTheme.colorScheme.error,
					style = MaterialTheme.typography.labelMedium)
		}
	}
	if (showImport) ImportDialog(wallet) { showImport = false }
}

@Composable
private fun CreateFlow(wallet: WalletModel, mnemonic: String) {
	val words = remember(mnemonic) { mnemonic.trim().split(Regex("\\s+")) }
	val verifyPositions = remember(mnemonic) {
		words.indices.shuffled().take(3).sorted()
	}
	var step by remember { mutableStateOf(0) }

	Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
			verticalArrangement = Arrangement.spacedBy(14.dp)) {
		when (step) {
			0 -> {
				val clipboard = LocalClipboardManager.current
				val clipScope = rememberCoroutineScope()
				var copied by remember { mutableStateOf(false) }
				Text("Your recovery phrase",
						style = MaterialTheme.typography.titleLarge,
						fontWeight = FontWeight.SemiBold)
				Text("Write these ${words.size} words down in order and keep them " +
						"offline. Anyone with this phrase can take your funds, and " +
						"it cannot be recovered if lost.",
						style = MaterialTheme.typography.bodyMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant)
				WordGrid(words)
				Row(verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(8.dp)) {
					OutlinedButton(onClick = {
						SecureClipboard.copyWithAutoClear(clipboard, clipScope,
								mnemonic, SEED_CLIP_SECONDS)
						copied = true
					}) {
						Icon(Icons.Filled.ContentCopy, contentDescription = null,
								modifier = Modifier.size(16.dp))
						Spacer(Modifier.width(6.dp))
						Text(if (copied) "Copied" else "Copy phrase")
					}
					Text("Clipboard clears after ${SEED_CLIP_SECONDS}s. Prefer " +
							"writing it down.",
							style = MaterialTheme.typography.labelSmall,
							color = MaterialTheme.colorScheme.onSurfaceVariant)
				}
				Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
					OutlinedButton(onClick = { wallet.cancelCreate() }) {
						Text("Cancel")
					}
					Button(onClick = { step = 1 }) { Text("I've written it down") }
				}
			}
			1 -> VerifyStep(words, verifyPositions,
					onBack = { step = 0 }, onVerified = { step = 2 })
			2 -> NameAndPasswordStep(wallet, onBack = { step = 1 })
		}
	}
}

@Composable
private fun WordGrid(words: List<String>) {
	Card(colors = CardDefaults.cardColors(
			containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)) {
		Column(Modifier.padding(16.dp),
				verticalArrangement = Arrangement.spacedBy(6.dp)) {
			words.chunked(3).forEachIndexed { rowIdx, row ->
				Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
					row.forEachIndexed { i, w ->
						Text("${rowIdx * 3 + i + 1}. $w",
								modifier = Modifier.width(150.dp),
								fontFamily = FontFamily.Monospace,
								style = MaterialTheme.typography.bodyMedium)
					}
				}
			}
		}
	}
}

@Composable
private fun VerifyStep(words: List<String>, positions: List<Int>,
		onBack: () -> Unit, onVerified: () -> Unit) {
	val answers = remember { mutableStateListOf("", "", "") }
	var msg by remember { mutableStateOf<String?>(null) }
	Text("Confirm your phrase", style = MaterialTheme.typography.titleLarge,
			fontWeight = FontWeight.SemiBold)
	Text("Enter the following words to confirm you saved the phrase.",
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant)
	positions.forEachIndexed { i, pos ->
		OutlinedTextField(answers[i], { answers[i] = it; msg = null },
				label = { Text("Word #${pos + 1}") }, singleLine = true,
				modifier = Modifier.fillMaxWidth().widthIn(max = 320.dp))
	}
	if (msg != null) {
		Text(msg!!, color = MaterialTheme.colorScheme.error,
				style = MaterialTheme.typography.labelMedium)
	}
	Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
		OutlinedButton(onClick = onBack) { Text("Back") }
		Button(onClick = {
			val ok = positions.withIndex().all { (i, pos) ->
				answers[i].trim().equals(words[pos], ignoreCase = true)
			}
			if (ok) onVerified() else msg = "Those words don't match. Check your backup."
		}) { Text("Confirm") }
	}
}

@Composable
private fun NameAndPasswordStep(wallet: WalletModel, onBack: () -> Unit) {
	var name by remember { mutableStateOf("") }
	var pw by remember { mutableStateOf("") }
	var confirm by remember { mutableStateOf("") }
	var msg by remember { mutableStateOf<String?>(null) }
	Text("Name and password", style = MaterialTheme.typography.titleLarge,
			fontWeight = FontWeight.SemiBold)
	Text("Give this wallet a name and its own password. You'll enter this " +
			"password to open the wallet; there is no recovery if you forget it.",
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant)
	OutlinedTextField(name, { name = it }, singleLine = true,
			label = { Text("Wallet name") },
			modifier = Modifier.fillMaxWidth().widthIn(max = 380.dp))
	StrengthPasswordField("Wallet password", pw, { pw = it; msg = null })
	PwField("Confirm password", confirm) { confirm = it; msg = null }
	if (msg != null) {
		Text(msg!!, color = MaterialTheme.colorScheme.error,
				style = MaterialTheme.typography.labelMedium)
	}
	Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
		OutlinedButton(onClick = onBack) { Text("Back") }
		Button(
				onClick = {
					when {
						pw.length < 8 -> msg = "Use at least 8 characters."
						pw != confirm -> msg = "Passwords don't match."
						else -> wallet.finishCreate(name, pw.toCharArray()) {}
					}
				},
				enabled = !wallet.busy) {
			Text(if (wallet.busy) "Creating…" else "Create wallet")
		}
	}
}


@Composable
private fun WalletHome(wallet: WalletModel) {
	Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
			.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
		WalletSwitcher(wallet)
		if (wallet.activeIsMultiChain) ChainTabs(wallet)
		when {
			wallet.walletLocked -> WalletUnlockInline(wallet)
			wallet.selectedChain == WalletModel.Chain.ETH -> EthContent(wallet)
			wallet.selectedChain == WalletModel.Chain.BTC -> BtcContent(wallet)
			wallet.selectedChain == WalletModel.Chain.XMR -> XmrContent(wallet)
			else -> ChainComingSoon(wallet.selectedChain)
		}
	}
}

@Composable
private fun WalletSwitcher(wallet: WalletModel) {
	var menuOpen by remember { mutableStateOf(false) }
	var showAdd by remember { mutableStateOf(false) }
	var showSettings by remember { mutableStateOf(false) }
	WalletCard {
		Row(verticalAlignment = Alignment.CenterVertically) {
			Column(Modifier.weight(1f)) {
				Text("Wallet", style = MaterialTheme.typography.labelMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant)
				Text(wallet.activeWalletName,
						style = MaterialTheme.typography.titleMedium,
						fontWeight = FontWeight.SemiBold)
			}
			Box {
				OutlinedButton(onClick = { menuOpen = true }) {
					Text("Switch")
					Icon(Icons.Filled.ExpandMore, contentDescription = null,
							modifier = Modifier.size(18.dp))
				}
				DropdownMenu(expanded = menuOpen,
						onDismissRequest = { menuOpen = false }) {
					wallet.wallets.forEach { w ->
						DropdownMenuItem(text = { Text(w.name) },
								onClick = { menuOpen = false; wallet.selectWallet(w.id) })
					}
					Divider()
					DropdownMenuItem(text = { Text("+ Add wallet") },
							onClick = { menuOpen = false; showAdd = true })
				}
			}
			IconButton(onClick = { showSettings = true }) {
				Icon(Icons.Filled.Settings, contentDescription = "Wallet settings",
						tint = MaterialTheme.colorScheme.onSurfaceVariant)
			}
		}
	}
	if (showAdd) AddWalletDialog(wallet) { showAdd = false }
	if (showSettings) WalletSettingsDialog(wallet) { showSettings = false }
}

@Composable
private fun WalletSettingsDialog(wallet: WalletModel, onClose: () -> Unit) {
	var sub by remember { mutableStateOf("") }
	var curMenu by remember { mutableStateOf(false) }
	AlertDialog(
			onDismissRequest = onClose,
			title = { Text("Wallet settings") },
			text = {
				Column(Modifier.verticalScroll(rememberScrollState()),
						verticalArrangement = Arrangement.spacedBy(2.dp)) {
					Row(Modifier.fillMaxWidth().padding(vertical = 8.dp),
							verticalAlignment = Alignment.CenterVertically) {
						Text("Currency", Modifier.weight(1f))
						Box {
							OutlinedButton(onClick = { curMenu = true }) {
								Text(wallet.fiatCurrency)
								Icon(Icons.Filled.ExpandMore, contentDescription = null,
										modifier = Modifier.size(18.dp))
							}
							DropdownMenu(expanded = curMenu,
									onDismissRequest = { curMenu = false }) {
								wallet.fiatCurrencies.forEach { c ->
									DropdownMenuItem(text = { Text(c) }, onClick = {
										wallet.applyFiatCurrency(c); curMenu = false
									})
								}
							}
						}
					}
					Divider()
					SettingRow("View recovery phrase") { sub = "seed" }
					Divider()
					SettingRow("Change wallet password") { sub = "password" }
					Divider()
					SettingRow("Rename wallet") { sub = "rename" }
					Divider()
					SettingRow("Back up all wallets") { sub = "backup" }
					Divider()
					SettingRow("Restore from backup") { sub = "restore" }
					Divider()
					Row(Modifier.fillMaxWidth().padding(vertical = 8.dp),
							verticalAlignment = Alignment.CenterVertically) {
						Column(Modifier.weight(1f)) {
							Text("Password required to send")
							Text("The wallet password is required before every "
									+ "transaction.",
									style = MaterialTheme.typography.labelSmall,
									color = MaterialTheme.colorScheme.onSurfaceVariant)
						}
					}
					Divider()
					SettingRow("Remove wallet", danger = true) { sub = "remove" }
				}
			},
			confirmButton = { TextButton(onClick = onClose) { Text("Done") } })

	when (sub) {
		"seed" -> RevealSeedDialog(wallet) { sub = "" }
		"password" -> ChangeWalletPasswordDialog(wallet) { sub = "" }
		"rename" -> RenameWalletDialog(wallet) { sub = "" }
		"remove" -> RemoveWalletDialog(wallet, onRemoved = { sub = ""; onClose() }) { sub = "" }
		"backup" -> ExportBackupDialog(wallet) { sub = "" }
		"restore" -> RestoreBackupDialog(wallet) { sub = "" }
	}
}

@Composable
private fun ExportBackupDialog(wallet: WalletModel, onClose: () -> Unit) {
	var pw by remember { mutableStateOf("") }
	var confirm by remember { mutableStateOf("") }
	var msg by remember { mutableStateOf<String?>(null) }
	var busy by remember { mutableStateOf(false) }
	AlertDialog(
			onDismissRequest = { if (!busy) onClose() },
			title = { Text("Back up all wallets") },
			text = {
				Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
					Text("Creates one encrypted file with every wallet (seeds, settings, " +
							"address book). Choose a strong backup password — you'll need " +
							"it to restore, and it can't be recovered if lost.",
							style = MaterialTheme.typography.bodyMedium,
							color = MaterialTheme.colorScheme.onSurfaceVariant)
					StrengthPasswordField("Backup password", pw, { pw = it; msg = null })
					PwField("Confirm password", confirm) { confirm = it; msg = null }
					if (msg != null) Text(msg!!, color = MaterialTheme.colorScheme.error,
							style = MaterialTheme.typography.labelMedium)
				}
			},
			confirmButton = {
				TextButton(onClick = {
					when {
						pw.length < 8 -> msg = "Use at least 8 characters."
						pw != confirm -> msg = "Passwords don't match."
						else -> {
							busy = true
							wallet.exportBackup(pw.toCharArray()) { bytes ->
								busy = false
								if (bytes == null) msg = "Couldn't create the backup."
								else {
									val f = chooseSaveFile("zerion-wallets.zwbk")
									if (f != null) {
										try {
											java.nio.file.Files.write(f.toPath(), bytes)
											onClose()
										} catch (e: Exception) { msg = "Couldn't write the file." }
									}
								}
							}
						}
					}
				}, enabled = !busy && pw.isNotEmpty()) {
					Text(if (busy) "Working…" else "Export")
				}
			},
			dismissButton = { TextButton(onClick = onClose, enabled = !busy) { Text("Cancel") } })
}

@Composable
private fun RestoreBackupDialog(wallet: WalletModel, onClose: () -> Unit) {
	var file by remember { mutableStateOf<java.io.File?>(null) }
	var pw by remember { mutableStateOf("") }
	var msg by remember { mutableStateOf<String?>(null) }
	var busy by remember { mutableStateOf(false) }
	AlertDialog(
			onDismissRequest = { if (!busy) onClose() },
			title = { Text("Restore from backup") },
			text = {
				Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
					Text("Restores wallets from an encrypted Zerion backup file. " +
							"Restored wallets appear locked until you open them with " +
							"their own passwords.", style = MaterialTheme.typography.bodyMedium,
							color = MaterialTheme.colorScheme.onSurfaceVariant)
					OutlinedButton(onClick = { file = chooseOpenFile() },
							modifier = Modifier.fillMaxWidth()) {
						Text(file?.name ?: "Choose backup file")
					}
					PwField("Backup password", pw) { pw = it; msg = null }
					if (msg != null) Text(msg!!, color = MaterialTheme.colorScheme.error,
							style = MaterialTheme.typography.labelMedium)
				}
			},
			confirmButton = {
				TextButton(onClick = {
					val f = file
					if (f == null) msg = "Choose a backup file first."
					else {
						busy = true
						try {
							val data = java.nio.file.Files.readAllBytes(f.toPath())
							wallet.importBackup(data, pw.toCharArray()) { err ->
								busy = false
								if (err == null) onClose() else msg = err
							}
						} catch (e: Exception) { busy = false; msg = "Couldn't read the file." }
					}
				}, enabled = !busy && pw.isNotEmpty()) {
					Text(if (busy) "Restoring…" else "Restore")
				}
			},
			dismissButton = { TextButton(onClick = onClose, enabled = !busy) { Text("Cancel") } })
}

private fun chooseSaveFile(defaultName: String): java.io.File? {
	val fc = javax.swing.JFileChooser()
	fc.selectedFile = java.io.File(defaultName)
	return if (fc.showSaveDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION)
		fc.selectedFile else null
}

private fun chooseOpenFile(): java.io.File? {
	val fc = javax.swing.JFileChooser()
	return if (fc.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION)
		fc.selectedFile else null
}

@Composable
private fun SettingRow(label: String, danger: Boolean = false, onClick: () -> Unit) {
	Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
			verticalAlignment = Alignment.CenterVertically) {
		Text(label, Modifier.weight(1f),
				color = if (danger) MaterialTheme.colorScheme.error
				else MaterialTheme.colorScheme.onSurface)
	}
}

@Composable
private fun RevealSeedDialog(wallet: WalletModel, onClose: () -> Unit) {
	var pw by remember { mutableStateOf("") }
	var words by remember { mutableStateOf<List<String>?>(null) }
	var msg by remember { mutableStateOf<String?>(null) }
	AlertDialog(
			onDismissRequest = onClose,
			title = { Text("Recovery phrase") },
			text = {
				if (words != null) {
					Column(Modifier.verticalScroll(rememberScrollState())) {
						WordGrid(words!!)
						Spacer(Modifier.size(8.dp))
						Text("Never share this. Anyone with it controls your funds.",
								style = MaterialTheme.typography.labelSmall,
								color = MaterialTheme.colorScheme.error)
					}
				} else {
					Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
						Text("Enter your wallet password to reveal the phrase.",
								style = MaterialTheme.typography.bodyMedium,
								color = MaterialTheme.colorScheme.onSurfaceVariant)
						PwField("Wallet password", pw) { pw = it; msg = null }
						if (msg != null) {
							Text(msg!!, color = MaterialTheme.colorScheme.error,
									style = MaterialTheme.typography.labelMedium)
						}
					}
				}
			},
			confirmButton = {
				if (words == null) {
					TextButton(onClick = {
						wallet.revealSeed(pw.toCharArray()) { m ->
							if (m != null) words = m.trim().split(Regex("\\s+"))
							else msg = "Incorrect password."
						}
						pw = ""
					}, enabled = pw.isNotEmpty()) { Text("Reveal") }
				} else {
					TextButton(onClick = onClose) { Text("Done") }
				}
			},
			dismissButton = {
				if (words == null) TextButton(onClick = onClose) { Text("Cancel") }
			})
}

@Composable
private fun ChangeWalletPasswordDialog(wallet: WalletModel, onClose: () -> Unit) {
	var old by remember { mutableStateOf("") }
	var new by remember { mutableStateOf("") }
	var confirm by remember { mutableStateOf("") }
	var msg by remember { mutableStateOf<String?>(null) }
	var busy by remember { mutableStateOf(false) }
	AlertDialog(
			onDismissRequest = onClose,
			title = { Text("Change wallet password") },
			text = {
				Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
					PwField("Current password", old) { old = it; msg = null }
					StrengthPasswordField("New password", new, { new = it; msg = null })
					PwField("Confirm new password", confirm) { confirm = it; msg = null }
					if (msg != null) {
						Text(msg!!, color = MaterialTheme.colorScheme.error,
								style = MaterialTheme.typography.labelMedium)
					}
				}
			},
			confirmButton = {
				TextButton(onClick = {
					when {
						new.length < 8 -> msg = "Use at least 8 characters."
						new != confirm -> msg = "New passwords don't match."
						else -> {
							busy = true
							wallet.changeWalletPassword(old.toCharArray(), new.toCharArray()) { err ->
								busy = false
								if (err == null) onClose() else msg = err
							}
						}
					}
				}, enabled = !busy && old.isNotEmpty() && new.isNotEmpty()) {
					Text(if (busy) "Changing…" else "Change")
				}
			},
			dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } })
}

@Composable
private fun RenameWalletDialog(wallet: WalletModel, onClose: () -> Unit) {
	var name by remember { mutableStateOf(wallet.activeWalletName) }
	AlertDialog(
			onDismissRequest = onClose,
			title = { Text("Rename wallet") },
			text = {
				OutlinedTextField(name, { name = it }, singleLine = true,
						label = { Text("Wallet name") }, modifier = Modifier.fillMaxWidth())
			},
			confirmButton = {
				TextButton(onClick = { wallet.renameWallet(name); onClose() },
						enabled = name.isNotBlank()) { Text("Save") }
			},
			dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } })
}

@Composable
private fun RemoveWalletDialog(wallet: WalletModel, onRemoved: () -> Unit,
		onClose: () -> Unit) {
	AlertDialog(
			onDismissRequest = onClose,
			title = { Text("Remove this wallet?") },
			text = {
				Text("This removes \"${wallet.activeWalletName}\" from this device. " +
						"You can restore it later only with its recovery phrase — " +
						"without it, funds are lost.")
			},
			confirmButton = {
				TextButton(onClick = {
					wallet.activeWalletId?.let { wallet.deleteWallet(it) }; onRemoved()
				}) { Text("Remove") }
			},
			dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } })
}

@Composable
private fun ChainTabs(wallet: WalletModel) {
	Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
		WalletModel.Chain.entries
				.filter { it != WalletModel.Chain.XMR || wallet.xmrAvailable }
				.forEach { c ->
					FilterChip(selected = wallet.selectedChain == c,
							onClick = { wallet.selectChain(c) },
							label = { Text(c.label) })
				}
	}
}

@Composable
private fun WalletUnlockInline(wallet: WalletModel) {
	var pw by remember { mutableStateOf("") }
	WalletCard {
		Text("Wallet locked", style = MaterialTheme.typography.titleSmall,
				fontWeight = FontWeight.SemiBold)
		Text("Enter the password for \"${wallet.activeWalletName}\".",
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant)
		PwField("Wallet password", pw) { pw = it }
		if (wallet.error != null) {
			Text(wallet.error!!, color = MaterialTheme.colorScheme.error,
					style = MaterialTheme.typography.labelMedium)
		}
		Button(onClick = { if (pw.isNotEmpty()) wallet.unlockActive(pw.toCharArray()) {} },
				enabled = !wallet.busy && pw.isNotEmpty(),
				modifier = Modifier.fillMaxWidth()) {
			Text(if (wallet.busy) "Unlocking…" else "Unlock wallet")
		}
	}
}

@Composable
private fun ChainComingSoon(chain: WalletModel.Chain) {
	WalletCard {
		Text(chain.label, style = MaterialTheme.typography.titleMedium,
				fontWeight = FontWeight.SemiBold)
		Text("${chain.label} support is coming. Your recovery phrase already " +
				"covers it — when it's ready, your ${chain.label} accounts derive " +
				"from the same wallet.",
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant)
	}
}


@Composable
private fun EthContent(wallet: WalletModel) {
	var showSend by remember { mutableStateOf(false) }
	var showReceive by remember { mutableStateOf(false) }
	var showNode by remember { mutableStateOf(false) }

	AccountSelector(wallet)

	var showAddToken by remember { mutableStateOf(false) }
	WalletCard {
		Row(verticalAlignment = Alignment.CenterVertically) {
			Column(Modifier.weight(1f)) {
				Text("Assets", style = MaterialTheme.typography.titleSmall,
						fontWeight = FontWeight.SemiBold)
				if (wallet.totalUsd != null) {
					Text("${wallet.totalUsd} total",
							style = MaterialTheme.typography.labelMedium,
							color = MaterialTheme.colorScheme.onSurfaceVariant)
				}
			}
			if (wallet.busy) CircularProgressIndicator(Modifier.size(18.dp),
					strokeWidth = 2.dp)
			IconButton(onClick = { showAddToken = true }) {
				Icon(Icons.Filled.Add, contentDescription = "Add token")
			}
			IconButton(onClick = { wallet.refreshBalance() }) {
				Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
			}
		}
		if (wallet.assetBalances.isEmpty()) {
			AssetRow("ETH", wallet.balanceEth ?: "—", null)
		} else {
			wallet.assetBalances.forEach { a -> AssetRow(a.symbol, a.formatted, a.usd) }
		}
		if (wallet.error != null) {
			Text(wallet.error!!, color = MaterialTheme.colorScheme.error,
					style = MaterialTheme.typography.labelMedium)
		}
		Text("Live over Tor · " + shortNode(wallet.nodeUrl),
				style = MaterialTheme.typography.labelSmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant)
		Row(Modifier.fillMaxWidth().padding(top = 4.dp),
				horizontalArrangement = Arrangement.spacedBy(12.dp)) {
			OutlinedButton(onClick = { showReceive = true },
					modifier = Modifier.weight(1f)) {
				Icon(Icons.Filled.QrCode2, contentDescription = null,
						modifier = Modifier.size(18.dp))
				Spacer(Modifier.width(6.dp)); Text("Receive")
			}
			Button(onClick = { showSend = true }, modifier = Modifier.weight(1f)) {
				Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null,
						modifier = Modifier.size(18.dp))
				Spacer(Modifier.width(6.dp)); Text("Send")
			}
		}
	}

	WalletCard {
		Text("Transactions", style = MaterialTheme.typography.titleSmall,
				fontWeight = FontWeight.SemiBold)
		if (wallet.history.isEmpty()) {
			Text("No transactions yet.",
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant)
		} else {
			wallet.history.take(30).forEach { tx ->
				Divider(color = MaterialTheme.colorScheme.outlineVariant)
				Column {
					val verb = if (tx.incoming) "Received" else "Sent"
					val rel = if (tx.incoming) "from" else "to"
					Text("$verb ${tx.amount} ${tx.symbol} $rel ${shortAddr(tx.to)}",
							style = MaterialTheme.typography.bodyMedium)
					Row(verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.spacedBy(8.dp)) {
						Text(statusLabel(tx.status), color = statusColor(tx.status),
								style = MaterialTheme.typography.labelSmall,
								fontWeight = FontWeight.Medium)
						Text(shortAddr(tx.hash), fontFamily = FontFamily.Monospace,
								style = MaterialTheme.typography.labelSmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant)
					}
				}
			}
		}
	}

	WalletCard {
		Row(verticalAlignment = Alignment.CenterVertically) {
			Column(Modifier.weight(1f)) {
				Text("Node", style = MaterialTheme.typography.titleSmall,
						fontWeight = FontWeight.SemiBold)
				Text(wallet.nodeUrl, style = MaterialTheme.typography.labelSmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant)
			}
			NodeHealthDot(wallet, WalletModel.Chain.ETH, wallet.nodeUrl)
			TextButton(onClick = { showNode = true }) { Text("Change") }
		}
	}

	if (showSend) SendDialog(wallet) { showSend = false }
	if (showReceive) ReceiveDialog(wallet) { showReceive = false }
	if (showNode) NodeDialog(wallet.nodeUrl,
			onSave = { wallet.setNode(it); showNode = false },
			onClose = { showNode = false })
	if (showAddToken) AddTokenDialog(wallet) { showAddToken = false }
}

@Composable
private fun AddTokenDialog(wallet: WalletModel, onClose: () -> Unit) {
	var contract by remember { mutableStateOf("") }
	var msg by remember { mutableStateOf<String?>(null) }
	var busy by remember { mutableStateOf(false) }
	AlertDialog(
			onDismissRequest = { if (!busy) onClose() },
			title = { Text("Add token") },
			text = {
				Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
					Text("Paste an ERC-20 token contract address. Its symbol and " +
							"decimals are read from the contract over Tor.",
							style = MaterialTheme.typography.bodyMedium,
							color = MaterialTheme.colorScheme.onSurfaceVariant)
					OutlinedTextField(contract, { contract = it; msg = null },
							label = { Text("Contract 0x… address") }, singleLine = true,
							modifier = Modifier.fillMaxWidth())
					if (msg != null) {
						Text(msg!!, color = MaterialTheme.colorScheme.error,
								style = MaterialTheme.typography.labelMedium)
					}
				}
			},
			confirmButton = {
				TextButton(onClick = {
					busy = true; msg = null
					wallet.addCustomToken(contract) { err ->
						busy = false
						if (err == null) onClose() else msg = err
					}
				}, enabled = !busy && contract.isNotBlank()) {
					Text(if (busy) "Reading…" else "Add")
				}
			},
			dismissButton = {
				TextButton(onClick = onClose, enabled = !busy) { Text("Cancel") }
			})
}

private fun statusLabel(s: String): String = when (s) {
	"confirmed" -> "Confirmed"
	"failed" -> "Failed"
	else -> "Pending"
}

@Composable
private fun statusColor(s: String): Color = when (s) {
	"confirmed" -> Color(0xFF2ECC71)
	"failed" -> MaterialTheme.colorScheme.error
	else -> Color(0xFFF39C12)
}

@Composable
private fun BtcContent(wallet: WalletModel) {
	var showSend by remember { mutableStateOf(false) }
	var showReceive by remember { mutableStateOf(false) }
	var showNode by remember { mutableStateOf(false) }
	var showPayjoin by remember { mutableStateOf(false) }
	var showSp by remember { mutableStateOf(false) }
	var bumpFor by remember { mutableStateOf<String?>(null) }

	AccountSelector(wallet)

	WalletCard {
		Row(verticalAlignment = Alignment.CenterVertically) {
			Column(Modifier.weight(1f)) {
				Text("Bitcoin", style = MaterialTheme.typography.labelMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant)
				Text((wallet.btcBalance ?: "—") + " BTC",
						style = MaterialTheme.typography.headlineSmall,
						fontWeight = FontWeight.SemiBold)
				if (wallet.btcUsd != null) {
					Text(wallet.btcUsd!!, style = MaterialTheme.typography.labelMedium,
							color = MaterialTheme.colorScheme.onSurfaceVariant)
				}
			}
			if (wallet.busy) CircularProgressIndicator(Modifier.size(18.dp),
					strokeWidth = 2.dp)
			IconButton(onClick = { wallet.refreshBtc() }) {
				Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
			}
		}
		if (wallet.error != null) {
			Text(wallet.error!!, color = MaterialTheme.colorScheme.error,
					style = MaterialTheme.typography.labelMedium)
		}
		Text("Native SegWit · live over Tor · " + shortNode(wallet.btcServer),
				style = MaterialTheme.typography.labelSmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant)
		Row(Modifier.fillMaxWidth().padding(top = 4.dp),
				horizontalArrangement = Arrangement.spacedBy(12.dp)) {
			OutlinedButton(onClick = { showReceive = true },
					modifier = Modifier.weight(1f)) {
				Icon(Icons.Filled.QrCode2, contentDescription = null,
						modifier = Modifier.size(18.dp))
				Spacer(Modifier.width(6.dp)); Text("Receive")
			}
			Button(onClick = { showSend = true }, modifier = Modifier.weight(1f)) {
				Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null,
						modifier = Modifier.size(18.dp))
				Spacer(Modifier.width(6.dp)); Text("Send")
			}
		}
		Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
			OutlinedButton(onClick = { showPayjoin = true }, modifier = Modifier.weight(1f)) {
				Icon(Icons.Filled.Shield, contentDescription = null,
						modifier = Modifier.size(18.dp))
				Spacer(Modifier.width(6.dp)); Text("Private send")
			}
		}
	}

	WalletCard {
		Text("Transactions", style = MaterialTheme.typography.titleSmall,
				fontWeight = FontWeight.SemiBold)
		if (wallet.btcHistory.isEmpty()) {
			Text("No transactions yet.", style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant)
		} else {
			wallet.btcHistory.take(30).forEach { tx ->
				Divider(color = MaterialTheme.colorScheme.outlineVariant)
				val verb = if (tx.incoming) "Received" else "Sent"
				Column {
					Text("$verb ${tx.amount} BTC",
							style = MaterialTheme.typography.bodyMedium)
					Row(verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.spacedBy(8.dp)) {
						val confText = when {
							tx.confirmations <= 0 -> "Pending"
							tx.confirmations >= 6 -> "Confirmed"
							else -> "${tx.confirmations} conf"
						}
						Text(confText,
								color = if (tx.confirmations <= 0) Color(0xFFF39C12)
								else Color(0xFF2ECC71),
								style = MaterialTheme.typography.labelSmall,
								fontWeight = FontWeight.Medium)
						Text(shortAddr(tx.hash), Modifier.weight(1f),
								fontFamily = FontFamily.Monospace,
								style = MaterialTheme.typography.labelSmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant)
						if (!tx.incoming && tx.confirmations <= 0) {
							TextButton(onClick = { bumpFor = tx.hash },
									enabled = !wallet.busy,
									contentPadding = androidx.compose.foundation.layout
											.PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
								Text("Bump fee", style = MaterialTheme.typography.labelSmall)
							}
						}
					}
				}
			}
		}
	}

	WalletCard {
		Row(verticalAlignment = Alignment.CenterVertically) {
			Column(Modifier.weight(1f)) {
				Text("Electrum server", style = MaterialTheme.typography.titleSmall,
						fontWeight = FontWeight.SemiBold)
				Text(wallet.btcServer, style = MaterialTheme.typography.labelSmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant)
			}
			NodeHealthDot(wallet, WalletModel.Chain.BTC, wallet.btcServer)
			TextButton(onClick = { showNode = true }) { Text("Change") }
		}
	}

	WalletCard {
		Row(verticalAlignment = Alignment.CenterVertically) {
			Column(Modifier.weight(1f)) {
				Text("Silent payments (receiving)",
						style = MaterialTheme.typography.titleSmall,
						fontWeight = FontWeight.SemiBold)
				Text(if (wallet.spOracle.isBlank())
						"Off. Scan for payments to your sp1… address via an oracle."
						else "Received: ${wallet.spBalance} BTC · scanned to " +
								"${wallet.spScannedHeight}",
						style = MaterialTheme.typography.labelSmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant)
			}
			TextButton(onClick = { showSp = true }) { Text("Setup") }
		}
	}

	if (showReceive) BtcReceiveDialog(wallet) { showReceive = false }
	if (showSend) BtcSendDialog(wallet) { showSend = false }
	if (showNode) BtcNodeDialog(wallet.btcServer,
			onSave = { wallet.applyBtcServer(it); showNode = false },
			onClose = { showNode = false })
	if (showPayjoin) PayjoinDialog(wallet) { showPayjoin = false }
	if (showSp) SilentPaymentsDialog(wallet) { showSp = false }
	bumpFor?.let { hash ->
		BumpFeeDialog(wallet, hash) { bumpFor = null }
	}
}

@Composable
private fun BumpFeeDialog(wallet: WalletModel, txHash: String,
		onClose: () -> Unit) {
	var authPw by remember { mutableStateOf("") }
	var msg by remember { mutableStateOf<String?>(null) }
	var sending by remember { mutableStateOf(false) }
	AlertDialog(
			onDismissRequest = { if (!sending) onClose() },
			title = { Text("Raise the network fee") },
			text = {
				Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
					Text("Rebroadcast this unconfirmed transaction with a higher " +
							"fee. The recipient and amount stay the same; the extra " +
							"fee comes from your change.",
							style = MaterialTheme.typography.bodyMedium,
							color = MaterialTheme.colorScheme.onSurfaceVariant)
					if (wallet.require2fa)
						PwField("Wallet password", authPw) { authPw = it; msg = null }
					if (msg != null) Text(msg!!,
							color = MaterialTheme.colorScheme.error,
							style = MaterialTheme.typography.labelMedium)
				}
			},
			confirmButton = {
				TextButton(enabled = !sending &&
						(!wallet.require2fa || authPw.isNotEmpty()), onClick = {
					sending = true; msg = null
					val go = {
						wallet.bumpBtcFee(txHash) { err ->
							sending = false
							if (err == null) onClose() else msg = err
						}
					}
					if (wallet.require2fa) {
						wallet.verifyWalletPassword(authPw.toCharArray()) { ok ->
							authPw = ""
							if (ok) go() else { sending = false
								msg = "Incorrect wallet password." }
						}
					} else go()
				}) { Text(if (sending) "Sending…" else "Raise fee") }
			},
			dismissButton = {
				TextButton(onClick = onClose, enabled = !sending) { Text("Cancel") }
			})
}

@Composable
private fun SilentPaymentsDialog(wallet: WalletModel, onClose: () -> Unit) {
	var oracle by remember { mutableStateOf(wallet.spOracle) }
	var birthday by remember { mutableStateOf(if (wallet.spBirthday > 0) wallet.spBirthday.toString() else "") }
	var sweepTo by remember { mutableStateOf("") }
	var sweepPw by remember { mutableStateOf("") }
	var msg by remember { mutableStateOf<String?>(null) }
	AlertDialog(
			onDismissRequest = { if (!wallet.spScanning && !wallet.busy) onClose() },
			title = { Text("Silent payments (receiving)") },
			text = {
				Column(Modifier.verticalScroll(rememberScrollState()),
						verticalArrangement = Arrangement.spacedBy(8.dp)) {
					Text("Detect payments made to your reusable sp1… address. This needs " +
							"a BIP352 light-client oracle reached over Tor; running your " +
							"own is best. Experimental.",
							style = MaterialTheme.typography.bodyMedium,
							color = MaterialTheme.colorScheme.onSurfaceVariant)
					OutlinedTextField(oracle, { oracle = it; msg = null },
							label = { Text("Oracle URL (host:port)") }, singleLine = true,
							modifier = Modifier.fillMaxWidth())
					OutlinedTextField(birthday, { birthday = it.filter { c -> c.isDigit() }; msg = null },
							label = { Text("Start from block height") }, singleLine = true,
							modifier = Modifier.fillMaxWidth())
					Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
						TextButton(onClick = {
							wallet.applySpConfig(oracle, birthday.toIntOrNull() ?: 0)
							msg = "Saved."
						}, enabled = !wallet.spScanning) { Text("Save") }
						TextButton(onClick = {
							wallet.applySpConfig(oracle, birthday.toIntOrNull() ?: 0)
							wallet.scanSilentPayments { err -> msg = err ?: "Scan complete." }
						}, enabled = !wallet.spScanning && oracle.isNotBlank()) {
							Text(if (wallet.spScanning) "Scanning…" else "Scan")
						}
					}
					Divider()
					Text("Received: ${wallet.spBalance} BTC (${wallet.spUtxos.size} output(s))",
							style = MaterialTheme.typography.labelMedium,
							fontWeight = FontWeight.Medium)
					if (wallet.spUtxos.isNotEmpty()) {
						OutlinedTextField(sweepTo, { sweepTo = it; msg = null },
								label = { Text("Move all to Bitcoin address") }, singleLine = true,
								modifier = Modifier.fillMaxWidth())
						if (wallet.require2fa)
							PwField("Wallet password", sweepPw) { sweepPw = it; msg = null }
						Button(onClick = {
							msg = null
							val go = {
								wallet.sweepSilentPayments(sweepTo, null) { err ->
									msg = err ?: "Sent."
								}
							}
							if (wallet.require2fa) {
								wallet.verifyWalletPassword(sweepPw.toCharArray()) { ok ->
									sweepPw = ""
									if (ok) go() else msg = "Incorrect wallet password."
								}
							} else go()
						}, enabled = !wallet.busy && sweepTo.isNotBlank() &&
								(!wallet.require2fa || sweepPw.isNotEmpty()),
								modifier = Modifier.fillMaxWidth()) {
							Text(if (wallet.busy) "Sending…" else "Move received funds")
						}
					}
					if (msg != null) Text(msg!!,
							color = if (msg == "Saved." || msg == "Sent." || msg == "Scan complete.")
									MaterialTheme.colorScheme.primary
							else MaterialTheme.colorScheme.error,
							style = MaterialTheme.typography.labelMedium)
				}
			},
			confirmButton = { TextButton(onClick = onClose,
					enabled = !wallet.spScanning && !wallet.busy) { Text("Done") } })
}

@Composable
private fun PayjoinDialog(wallet: WalletModel, onClose: () -> Unit) {
	var uri by remember { mutableStateOf("") }
	var authPw by remember { mutableStateOf("") }
	var msg by remember { mutableStateOf<String?>(null) }
	var sending by remember { mutableStateOf(false) }
	AlertDialog(
			onDismissRequest = { if (!sending) onClose() },
			title = { Text("Private send (PayJoin)") },
			text = {
				Column(Modifier.verticalScroll(rememberScrollState()),
						verticalArrangement = Arrangement.spacedBy(10.dp)) {
					Text("Paste a PayJoin payment request (a bitcoin: URI with a pj= " +
							"endpoint). Your wallet and the recipient jointly build one " +
							"transaction, breaking the assumption that all inputs are " +
							"yours. The whole exchange runs over Tor.",
							style = MaterialTheme.typography.bodyMedium,
							color = MaterialTheme.colorScheme.onSurfaceVariant)
					OutlinedTextField(uri, { uri = it; msg = null },
							label = { Text("bitcoin:…?amount=…&pj=…") }, minLines = 3,
							modifier = Modifier.fillMaxWidth())
					if (wallet.require2fa) {
						PwField("Wallet password", authPw) { authPw = it; msg = null }
					}
					if (msg != null) {
						Text(msg!!, color = MaterialTheme.colorScheme.error,
								style = MaterialTheme.typography.labelMedium)
					}
					Text("If the recipient can't PayJoin, this falls back to an " +
							"ordinary payment for the same amount — your funds are " +
							"never at risk.", style = MaterialTheme.typography.labelSmall,
							color = MaterialTheme.colorScheme.onSurfaceVariant)
				}
			},
			confirmButton = {
				TextButton(
						onClick = {
							sending = true; msg = null
							val go = {
								wallet.sendBtcPayjoin(uri) { err, payjoined ->
									sending = false
									if (err == null) {
										msg = if (payjoined) "Sent privately with PayJoin."
										else "Recipient didn't PayJoin — sent as an " +
												"ordinary payment."
										onClose()
									} else msg = err
								}
							}
							if (wallet.require2fa) {
								wallet.verifyWalletPassword(authPw.toCharArray()) { ok ->
									authPw = ""
									if (ok) go() else { sending = false
										msg = "Incorrect wallet password." }
								}
							} else go()
						},
						enabled = !sending && uri.isNotBlank() &&
								(!wallet.require2fa || authPw.isNotEmpty())) {
					Text(if (sending) "Sending…" else "Send privately")
				}
			},
			dismissButton = {
				TextButton(onClick = onClose, enabled = !sending) { Text("Cancel") }
			})
}

@Composable
private fun XmrAccountSelector(wallet: WalletModel) {
	var menuOpen by remember { mutableStateOf(false) }
	val current = wallet.xmrAccounts.firstOrNull { it.index == wallet.xmrAccount }
	WalletCard {
		Row(verticalAlignment = Alignment.CenterVertically) {
			Column(Modifier.weight(1f)) {
				Text("Account", style = MaterialTheme.typography.labelMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant)
				Text(current?.label ?: "Account ${wallet.xmrAccount + 1}",
						style = MaterialTheme.typography.titleMedium,
						fontWeight = FontWeight.SemiBold)
			}
			Box {
				OutlinedButton(onClick = { menuOpen = true }) {
					Text("Switch")
					Icon(Icons.Filled.ExpandMore, contentDescription = null,
							modifier = Modifier.size(18.dp))
				}
				DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
					wallet.xmrAccounts.forEach { a ->
						DropdownMenuItem(text = { Text(a.label) },
								onClick = { menuOpen = false; wallet.selectXmrAccount(a.index) })
					}
					DropdownMenuItem(text = { Text("+ Add account") },
							onClick = { menuOpen = false; wallet.addXmrAccount() })
				}
			}
		}
	}
}

@Composable
private fun XmrContent(wallet: WalletModel) {
	var showSend by remember { mutableStateOf(false) }
	var showReceive by remember { mutableStateOf(false) }
	var showNode by remember { mutableStateOf(false) }

	XmrAccountSelector(wallet)

	WalletCard {
		Row(verticalAlignment = Alignment.CenterVertically) {
			Column(Modifier.weight(1f)) {
				Text("Monero", style = MaterialTheme.typography.labelMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant)
				Text((wallet.xmrBalance ?: "—") + " XMR",
						style = MaterialTheme.typography.headlineSmall,
						fontWeight = FontWeight.SemiBold)
				if (wallet.xmrUsd != null) {
					Text(wallet.xmrUsd!!, style = MaterialTheme.typography.labelMedium,
							color = MaterialTheme.colorScheme.onSurfaceVariant)
				}
			}
			if (wallet.busy || wallet.xmrStatus != null)
				CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
			IconButton(onClick = { wallet.refreshXmr() }) {
				Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
			}
		}
		if (wallet.xmrStatus != null) {
			Text(wallet.xmrStatus!!, style = MaterialTheme.typography.labelMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant)
		}
		if (wallet.error != null) {
			Text(wallet.error!!, color = MaterialTheme.colorScheme.error,
					style = MaterialTheme.typography.labelMedium)
		}
		Text("Private by default · over Tor · " + shortNode(wallet.xmrNode),
				style = MaterialTheme.typography.labelSmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant)
		Row(Modifier.fillMaxWidth().padding(top = 4.dp),
				horizontalArrangement = Arrangement.spacedBy(12.dp)) {
			OutlinedButton(onClick = { showReceive = true },
					modifier = Modifier.weight(1f), enabled = wallet.xmrAddress.isNotEmpty()) {
				Icon(Icons.Filled.QrCode2, contentDescription = null,
						modifier = Modifier.size(18.dp))
				Spacer(Modifier.width(6.dp)); Text("Receive")
			}
			Button(onClick = { showSend = true }, modifier = Modifier.weight(1f),
					enabled = wallet.xmrAddress.isNotEmpty()) {
				Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null,
						modifier = Modifier.size(18.dp))
				Spacer(Modifier.width(6.dp)); Text("Send")
			}
		}
	}

	WalletCard {
		Text("Transactions", style = MaterialTheme.typography.titleSmall,
				fontWeight = FontWeight.SemiBold)
		if (wallet.xmrHistory.isEmpty()) {
			Text("No transactions yet.", style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant)
		} else {
			wallet.xmrHistory.take(30).forEach { tx ->
				Divider(color = MaterialTheme.colorScheme.outlineVariant)
				val verb = if (tx.incoming) "Received" else "Sent"
				Column {
					Text("$verb ${tx.amount} XMR",
							style = MaterialTheme.typography.bodyMedium)
					Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
						Text(statusLabel(tx.status), color = statusColor(tx.status),
								style = MaterialTheme.typography.labelSmall,
								fontWeight = FontWeight.Medium)
						Text(shortAddr(tx.hash), fontFamily = FontFamily.Monospace,
								style = MaterialTheme.typography.labelSmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant)
					}
				}
			}
		}
	}

	WalletCard {
		Row(verticalAlignment = Alignment.CenterVertically) {
			Column(Modifier.weight(1f)) {
				Text("Monero node", style = MaterialTheme.typography.titleSmall,
						fontWeight = FontWeight.SemiBold)
				Text(wallet.xmrNode, style = MaterialTheme.typography.labelSmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant)
			}
			NodeHealthDot(wallet, WalletModel.Chain.XMR, wallet.xmrNode)
			TextButton(onClick = { showNode = true }) { Text("Change") }
		}
		Row(verticalAlignment = Alignment.CenterVertically) {
			Column(Modifier.weight(1f)) {
				Text("Trusted node", style = MaterialTheme.typography.bodyMedium)
				Text("Off by default: a remote node is treated as untrusted for " +
						"privacy. Turn on only for a node you run yourself.",
						style = MaterialTheme.typography.labelSmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant)
			}
			androidx.compose.material3.Switch(checked = wallet.xmrTrusted,
					onCheckedChange = { wallet.applyXmrTrusted(it) })
		}
	}

	if (showReceive) SimpleReceiveDialog("Receive XMR", wallet.xmrAddress,
			"Send only Monero (XMR) to this address.",
			onNew = { wallet.newXmrAddress() }) { showReceive = false }
	if (showSend) XmrSendDialog(wallet) { showSend = false }
	if (showNode) BtcNodeLikeDialog("Monero node", wallet.xmrNode,
			listOf("node.monerodevs.org:18089",
					"node.sethforprivacy.com:18089"),
			onSave = { wallet.applyXmrNode(it); showNode = false },
			onClose = { showNode = false })
}

@Composable
private fun XmrSendDialog(wallet: WalletModel, onClose: () -> Unit) {
	val rows = remember { mutableStateListOf(WalletModel.Recipient("", "")) }
	var authPw by remember { mutableStateOf("") }
	var msg by remember { mutableStateOf<String?>(null) }
	var busy by remember { mutableStateOf(false) }
	var plan by remember { mutableStateOf<WalletModel.XmrPlanInfo?>(null) }
	val balance = remember(wallet.xmrBalance) {
		wallet.xmrBalance?.toBigDecimalOrNull() ?: BigDecimal.ZERO
	}
	fun atomicToXmr(v: java.math.BigInteger): String =
			v.toBigDecimal().movePointLeft(12).stripTrailingZeros().toPlainString()
	fun close() { wallet.cancelXmrSend(); onClose() }
	fun setPct(p: Int) {
		rows[0] = rows[0].copy(amount = balance.multiply(BigDecimal(p)).divide(BigDecimal(100))
				.setScale(12, RoundingMode.DOWN).stripTrailingZeros().toPlainString()); msg = null
	}
	AlertDialog(
			onDismissRequest = { if (!busy) close() },
			title = { Text(if (plan == null) "Send XMR" else "Confirm XMR send") },
			text = {
				val current = plan
				Column(Modifier.verticalScroll(rememberScrollState()),
						verticalArrangement = Arrangement.spacedBy(10.dp)) {
					if (current == null) {
						Text("Balance: ${wallet.xmrBalance ?: "—"} XMR",
								style = MaterialTheme.typography.labelMedium,
								color = MaterialTheme.colorScheme.onSurfaceVariant)
						RecipientRows("XMR", wallet, rows, "Amount (XMR)") { msg = null }
						if (rows.size == 1) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
							PctChip("25%") { setPct(25) }; PctChip("50%") { setPct(50) }
							PctChip("75%") { setPct(75) }; PctChip("Max") { setPct(100) }
						}
						Text("Monero addresses only. Sent privately over Tor.",
								style = MaterialTheme.typography.labelSmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant)
					} else {
						current.destinations.forEach { (addr, amt) ->
							Text("$amt XMR",
									style = MaterialTheme.typography.titleMedium)
							Text(addr, style = MaterialTheme.typography.labelSmall,
									color = MaterialTheme.colorScheme.onSurfaceVariant)
						}
						Divider()
						Text("Network fee: ${atomicToXmr(current.feeAtomic)} XMR",
								style = MaterialTheme.typography.labelMedium)
						Text("Total: ${atomicToXmr(current.amountAtomic
								.add(current.feeAtomic))} XMR",
								style = MaterialTheme.typography.labelMedium)
						PwField("Wallet password", authPw) { authPw = it; msg = null }
						Text("Enter your wallet password to authorize this exact "
								+ "transaction.",
								style = MaterialTheme.typography.labelSmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant)
					}
					if (msg != null) {
						Text(msg!!, color = MaterialTheme.colorScheme.error,
								style = MaterialTheme.typography.labelMedium)
					}
				}
			},
			confirmButton = {
				val current = plan
				if (current == null) {
					TextButton(
							onClick = {
								busy = true; msg = null
								wallet.prepareXmrSend(rows.toList(), 0) { p, err ->
									busy = false
									if (p != null) plan = p else msg = err
								}
							},
							enabled = !busy &&
									rows.all { it.address.isNotBlank() && it.amount.isNotBlank() }) {
						Text(if (busy) "Preparing…" else "Review")
					}
				} else {
					TextButton(
							onClick = {
								busy = true; msg = null
								val pw = authPw.toCharArray(); authPw = ""
								wallet.confirmXmrSend(current, pw) { err ->
									busy = false
									if (err == null) close()
									else { msg = err; plan = null }
								}
							},
							enabled = !busy && authPw.isNotEmpty()) {
						Text(if (busy) "Sending…" else "Authorize & send")
					}
				}
			},
			dismissButton = {
				TextButton(onClick = {
					if (plan != null && !busy) { wallet.cancelXmrSend(); plan = null; authPw = "" }
					else if (!busy) close()
				}, enabled = !busy) {
					Text(if (plan != null) "Back" else "Cancel")
				}
			})
}

@Composable
private fun SimpleReceiveDialog(title: String, address: String, note: String,
		onNew: () -> Unit, onClose: () -> Unit) {
	val clipboard = LocalClipboardManager.current
	AlertDialog(
			onDismissRequest = onClose,
			title = { Text(title) },
			text = {
				Column(horizontalAlignment = Alignment.CenterHorizontally,
						verticalArrangement = Arrangement.spacedBy(12.dp)) {
					if (address.isEmpty()) CircularProgressIndicator() else {
						val qr = remember(address) { QrCode.pngFor(address, 360) }
						val bmp = remember(qr) {
							org.jetbrains.skia.Image.makeFromEncoded(qr).toComposeImageBitmap()
						}
						Box(Modifier.background(Color.White, RoundedCornerShape(14.dp))
								.padding(12.dp)) {
							Image(bmp, contentDescription = "Receive address QR",
									modifier = Modifier.size(200.dp))
						}
						Box(Modifier.fillMaxWidth()
								.background(MaterialTheme.colorScheme.surfaceContainerHighest,
										RoundedCornerShape(10.dp))
								.padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp)) {
							Row(verticalAlignment = Alignment.CenterVertically) {
								Text(address, Modifier.weight(1f),
										fontFamily = FontFamily.Monospace,
										style = MaterialTheme.typography.labelMedium)
								IconButton(onClick = {
									clipboard.setText(AnnotatedString(address))
								}) {
									Icon(Icons.Filled.ContentCopy,
											contentDescription = "Copy address")
								}
							}
						}
						Text(note, style = MaterialTheme.typography.labelSmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant)
					}
				}
			},
			confirmButton = { TextButton(onClick = onNew) { Text("New address") } },
			dismissButton = { TextButton(onClick = onClose) { Text("Close") } })
}

@Composable
private fun BtcNodeLikeDialog(title: String, current: String, presets: List<String>,
		onSave: (String) -> Unit, onClose: () -> Unit) {
	var server by remember { mutableStateOf(current) }
	AlertDialog(
			onDismissRequest = onClose,
			title = { Text(title) },
			text = {
				Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
					Text("Pick a node or enter host:port. All traffic goes over Tor.",
							style = MaterialTheme.typography.bodyMedium,
							color = MaterialTheme.colorScheme.onSurfaceVariant)
					presets.forEach { p ->
						TextButton(onClick = { server = p }) {
							Text(p, style = MaterialTheme.typography.labelMedium)
						}
					}
					OutlinedTextField(server, { server = it },
							label = { Text("host:port") }, singleLine = true,
							modifier = Modifier.fillMaxWidth())
				}
			},
			confirmButton = {
				TextButton(onClick = { onSave(server) },
						enabled = server.isNotBlank()) { Text("Use node") }
			},
			dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } })
}

@Composable
private fun BtcReceiveDialog(wallet: WalletModel, onClose: () -> Unit) {
	val clipboard = LocalClipboardManager.current
	val address = wallet.btcReceiveAddress
	AlertDialog(
			onDismissRequest = onClose,
			title = { Text("Receive BTC") },
			text = {
				Column(horizontalAlignment = Alignment.CenterHorizontally,
						verticalArrangement = Arrangement.spacedBy(12.dp)) {
					if (address.isEmpty()) {
						CircularProgressIndicator()
					} else {
						val qr = remember(address) { QrCode.pngFor(address, 340) }
						val bmp = remember(qr) {
							org.jetbrains.skia.Image.makeFromEncoded(qr).toComposeImageBitmap()
						}
						Box(Modifier.background(Color.White, RoundedCornerShape(14.dp))
								.padding(12.dp)) {
							Image(bmp, contentDescription = "Receive address QR",
									modifier = Modifier.size(200.dp))
						}
						Box(Modifier.fillMaxWidth()
								.background(MaterialTheme.colorScheme.surfaceContainerHighest,
										RoundedCornerShape(10.dp))
								.padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp)) {
							Row(verticalAlignment = Alignment.CenterVertically) {
								Text(address, Modifier.weight(1f),
										fontFamily = FontFamily.Monospace,
										style = MaterialTheme.typography.bodyMedium)
								IconButton(onClick = {
									clipboard.setText(AnnotatedString(address))
								}) {
									Icon(Icons.Filled.ContentCopy,
											contentDescription = "Copy address")
								}
							}
						}
						Text("Send only Bitcoin (BTC) to this address.",
								style = MaterialTheme.typography.labelSmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant)
					}
					if (wallet.btcSilentAddress.isNotEmpty()) {
						Divider(color = MaterialTheme.colorScheme.outlineVariant)
						Text("Silent payment address (reusable, private)",
								style = MaterialTheme.typography.labelMedium,
								fontWeight = FontWeight.Medium)
						Box(Modifier.fillMaxWidth()
								.background(MaterialTheme.colorScheme.surfaceContainerHighest,
										RoundedCornerShape(10.dp))
								.padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp)) {
							Row(verticalAlignment = Alignment.CenterVertically) {
								Text(wallet.btcSilentAddress, Modifier.weight(1f),
										fontFamily = FontFamily.Monospace,
										style = MaterialTheme.typography.labelSmall)
								IconButton(onClick = {
									clipboard.setText(AnnotatedString(wallet.btcSilentAddress))
								}) {
									Icon(Icons.Filled.ContentCopy,
											contentDescription = "Copy silent payment address")
								}
							}
						}
						Text("Share this once; each sender derives a unique, unlinkable " +
								"output. Automatic scanning for received silent payments " +
								"is coming; sending to sp1… addresses works now.",
								style = MaterialTheme.typography.labelSmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant)
					}
				}
			},
			confirmButton = {
				TextButton(onClick = { wallet.newBtcReceiveAddress() }) {
					Text("New address")
				}
			},
			dismissButton = { TextButton(onClick = onClose) { Text("Close") } })
}


@Composable
private fun RecipientRows(coin: String, wallet: WalletModel,
		rows: androidx.compose.runtime.snapshots.SnapshotStateList<WalletModel.Recipient>,
		amountLabel: String, onChange: () -> Unit) {
	var pickFor by remember { mutableStateOf(-1) }
	var saveAddr by remember { mutableStateOf<String?>(null) }
	rows.forEachIndexed { i, r ->
		Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
			OutlinedTextField(r.address,
					{ rows[i] = rows[i].copy(address = it); onChange() },
					label = { Text(if (rows.size > 1) "Recipient ${i + 1} address"
							else "Recipient address") },
					singleLine = true, modifier = Modifier.fillMaxWidth(),
					trailingIcon = {
						Row {
							IconButton(onClick = { pickFor = i }) {
								Icon(Icons.Filled.Contacts, "Address book",
										modifier = Modifier.size(20.dp))
							}
							if (r.address.isNotBlank()) IconButton(onClick = { saveAddr = r.address }) {
								Icon(Icons.Filled.Star, "Save recipient",
										modifier = Modifier.size(20.dp))
							}
						}
					})
			Row(verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(8.dp)) {
				OutlinedTextField(r.amount,
						{ rows[i] = rows[i].copy(amount = it); onChange() },
						label = { Text(amountLabel) }, singleLine = true,
						modifier = Modifier.weight(1f))
				if (rows.size > 1) IconButton(onClick = { rows.removeAt(i); onChange() }) {
					Icon(Icons.Filled.Delete, "Remove recipient")
				}
			}
		}
	}
	TextButton(onClick = { rows.add(WalletModel.Recipient("", "")); onChange() }) {
		Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
		Spacer(Modifier.width(4.dp)); Text("Add recipient")
	}
	if (pickFor >= 0) ContactPickerDialog(wallet, coin,
			onPick = { addr ->
				if (pickFor in rows.indices) rows[pickFor] = rows[pickFor].copy(address = addr)
				onChange(); pickFor = -1
			}, onClose = { pickFor = -1 })
	if (saveAddr != null) SaveContactDialog(wallet, coin, saveAddr!!) { saveAddr = null }
}

@Composable
private fun ContactPickerDialog(wallet: WalletModel, coin: String,
		onPick: (String) -> Unit, onClose: () -> Unit) {
	val list = wallet.contactsFor(coin)
	AlertDialog(
			onDismissRequest = onClose,
			title = { Text("Address book") },
			text = {
				if (list.isEmpty()) Text("No saved recipients yet. Tap the star on an " +
						"address to save one.", color = MaterialTheme.colorScheme.onSurfaceVariant)
				else Column(Modifier.verticalScroll(rememberScrollState())) {
					list.forEach { c ->
						Row(Modifier.fillMaxWidth().clickable { onPick(c.address) }
								.padding(vertical = 8.dp),
								verticalAlignment = Alignment.CenterVertically) {
							Column(Modifier.weight(1f)) {
								Text(c.label, style = MaterialTheme.typography.bodyMedium)
								Text(shortAddr(c.address), fontFamily = FontFamily.Monospace,
										style = MaterialTheme.typography.labelSmall,
										color = MaterialTheme.colorScheme.onSurfaceVariant)
							}
							IconButton(onClick = { wallet.removeContact(c) }) {
								Icon(Icons.Filled.Delete, "Remove",
										modifier = Modifier.size(18.dp))
							}
						}
						Divider()
					}
				}
			},
			confirmButton = { TextButton(onClick = onClose) { Text("Close") } })
}

@Composable
private fun SaveContactDialog(wallet: WalletModel, coin: String, address: String,
		onClose: () -> Unit) {
	var label by remember { mutableStateOf("") }
	AlertDialog(
			onDismissRequest = onClose,
			title = { Text("Save recipient") },
			text = {
				Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
					Text(shortAddr(address), fontFamily = FontFamily.Monospace,
							style = MaterialTheme.typography.labelMedium)
					OutlinedTextField(label, { label = it }, singleLine = true,
							label = { Text("Label") }, modifier = Modifier.fillMaxWidth())
				}
			},
			confirmButton = {
				TextButton(onClick = { wallet.addContact(label, address, coin); onClose() },
						enabled = label.isNotBlank()) { Text("Save") }
			},
			dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } })
}

@Composable
private fun CoinControlDialog(wallet: WalletModel, current: Set<String>?,
		onApply: (Set<String>?) -> Unit) {
	LaunchedEffect(Unit) { wallet.loadBtcUtxos(); wallet.loadCoinMeta() }
	val sel = remember { mutableStateListOf<String>().apply { current?.let { addAll(it) } } }
	var labelFor by remember { mutableStateOf<String?>(null) }
	AlertDialog(
			onDismissRequest = { onApply(if (sel.isEmpty()) null else sel.toSet()) },
			title = { Text("Choose coins to spend") },
			text = {
				if (wallet.btcUtxosLoading && wallet.btcUtxos.isEmpty())
					Box(Modifier.fillMaxWidth().padding(16.dp),
							contentAlignment = Alignment.Center) { CircularProgressIndicator() }
				else if (wallet.btcUtxos.isEmpty()) Text("No spendable coins found.",
						color = MaterialTheme.colorScheme.onSurfaceVariant)
				else Column(Modifier.verticalScroll(rememberScrollState())) {
					Text("Pick which outputs this transaction may spend. Selecting none " +
							"uses automatic selection. Frozen coins are never spent.",
							style = MaterialTheme.typography.labelSmall,
							color = MaterialTheme.colorScheme.onSurfaceVariant)
					wallet.btcUtxos.forEach { u ->
						val key = "${u.txid}:${u.vout}"
						val isFrozen = key in wallet.btcFrozen
						val label = wallet.btcLabels[key]
						Row(Modifier.fillMaxWidth().clickable(enabled = !isFrozen) {
							if (key in sel) sel.remove(key) else sel.add(key)
						}.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
							androidx.compose.material3.Checkbox(checked = key in sel,
									enabled = !isFrozen,
									onCheckedChange = { if (it) sel.add(key) else sel.remove(key) })
							Column(Modifier.weight(1f)) {
								Text(BigDecimal(u.valueSat).movePointLeft(8).stripTrailingZeros()
										.toPlainString() + " BTC" +
										(if (isFrozen) "  ❄ frozen" else ""),
										style = MaterialTheme.typography.bodyMedium)
								if (!label.isNullOrBlank())
									Text(label, style = MaterialTheme.typography.labelMedium,
											color = MaterialTheme.colorScheme.primary)
								Text("${shortAddr(u.address)} · " +
										(if (u.confirmations <= 0) "pending"
										else "${u.confirmations} conf"),
										fontFamily = FontFamily.Monospace,
										style = MaterialTheme.typography.labelSmall,
										color = MaterialTheme.colorScheme.onSurfaceVariant)
							}
							TextButton(onClick = { labelFor = key }) { Text("Label") }
							TextButton(onClick = {
								if (isFrozen) wallet.freezeUtxo(key, false)
								else { sel.remove(key); wallet.freezeUtxo(key, true) }
							}) { Text(if (isFrozen) "Unfreeze" else "Freeze") }
						}
						Divider()
					}
				}
			},
			confirmButton = {
				TextButton(onClick = { onApply(if (sel.isEmpty()) null else sel.toSet()) }) {
					Text("Use selection")
				}
			},
			dismissButton = { TextButton(onClick = { onApply(null) }) { Text("Automatic") } })
	labelFor?.let { key ->
		var text by remember(key) {
			mutableStateOf(wallet.btcLabels[key] ?: "")
		}
		AlertDialog(
				onDismissRequest = { labelFor = null },
				title = { Text("Label coin") },
				text = {
					OutlinedTextField(text, { text = it }, singleLine = true,
							label = { Text("Private label") },
							modifier = Modifier.fillMaxWidth())
				},
				confirmButton = {
					TextButton(onClick = {
						wallet.setUtxoLabel(key, text); labelFor = null
					}) { Text("Save") }
				},
				dismissButton = {
					TextButton(onClick = { labelFor = null }) { Text("Cancel") }
				})
	}
}

@Composable
private fun BtcSendDialog(wallet: WalletModel, onClose: () -> Unit) {
	val rows = remember { mutableStateListOf(WalletModel.Recipient("", "")) }
	var authPw by remember { mutableStateOf("") }
	var msg by remember { mutableStateOf<String?>(null) }
	var sending by remember { mutableStateOf(false) }
	var feeRates by remember { mutableStateOf<Triple<Double, Double, Double>?>(null) }
	var feeChoice by remember { mutableStateOf(1) }
	var showCoins by remember { mutableStateOf(false) }
	var selectedUtxos by remember { mutableStateOf<Set<String>?>(null) }
	var sweep by remember { mutableStateOf(false) }
	var plan by remember { mutableStateOf<WalletModel.BtcPlanInfo?>(null) }
	LaunchedEffect(Unit) {
		wallet.fetchBtcFeeRates { feeRates = it }
		wallet.loadCoinMeta()
	}
	val chosenRate = feeRates?.let { listOf(it.first, it.second, it.third).getOrNull(feeChoice) }
	val balance = remember(wallet.btcBalance) {
		wallet.btcBalance?.toBigDecimalOrNull() ?: BigDecimal.ZERO
	}
	fun setPct(p: Int) {
		sweep = false
		rows[0] = rows[0].copy(amount = balance.multiply(BigDecimal(p)).divide(BigDecimal(100))
				.setScale(8, RoundingMode.DOWN).stripTrailingZeros().toPlainString()); msg = null
	}
	AlertDialog(
			onDismissRequest = { if (!sending) onClose() },
			title = { Text("Send BTC") },
			text = {
				Column(Modifier.verticalScroll(rememberScrollState()),
						verticalArrangement = Arrangement.spacedBy(10.dp)) {
					Text("Balance: ${wallet.btcBalance ?: "—"} BTC",
							style = MaterialTheme.typography.labelMedium,
							color = MaterialTheme.colorScheme.onSurfaceVariant)
					RecipientRows("BTC", wallet, rows, "Amount (BTC)") { msg = null; sweep = false }
					if (rows.size == 1) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
						PctChip("25%") { setPct(25) }; PctChip("50%") { setPct(50) }
						PctChip("75%") { setPct(75) }; PctChip("Max") { sweep = true; msg = null }
					}
					if (sweep) Text("Max — sends the entire balance minus the network fee.",
							style = MaterialTheme.typography.labelSmall,
							color = MaterialTheme.colorScheme.primary)
					Text("Network fee", style = MaterialTheme.typography.labelMedium)
					Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
						val labels = listOf("Economy", "Normal", "Priority")
						labels.forEachIndexed { i, label ->
							val r = feeRates?.let { listOf(it.first, it.second, it.third)[i] }
							FilterChip(selected = feeChoice == i, onClick = { feeChoice = i },
									label = {
										Text(if (r != null) "$label · ${"%.1f".format(r)} s/vB"
												else label, style = MaterialTheme.typography.labelSmall)
									})
						}
					}
					OutlinedButton(onClick = { showCoins = true },
							modifier = Modifier.fillMaxWidth()) {
						Text(if (selectedUtxos == null) "Coin control: automatic"
								else "Coin control: ${selectedUtxos!!.size} selected")
					}
					Row(Modifier.fillMaxWidth(),
							verticalAlignment = Alignment.CenterVertically) {
						Column(Modifier.weight(1f)) {
							Text("Extreme privacy",
									style = MaterialTheme.typography.bodyMedium)
							Text("Refuse sends that link your addresses.",
									style = MaterialTheme.typography.labelSmall,
									color = MaterialTheme.colorScheme.onSurfaceVariant)
						}
						androidx.compose.material3.Switch(
								checked = wallet.extremePrivacy,
								onCheckedChange = { wallet.applyExtremePrivacy(it) })
					}
					if (wallet.require2fa) {
						PwField("Wallet password", authPw) { authPw = it; msg = null }
					}
					if (msg != null) Text(msg!!, color = MaterialTheme.colorScheme.error,
							style = MaterialTheme.typography.labelMedium)
					Text("Bitcoin or silent-payment (sp1…) address — paying an sp1… " +
							"address is private and unlinkable. Over Tor, RBF-enabled.",
							style = MaterialTheme.typography.labelSmall,
							color = MaterialTheme.colorScheme.onSurfaceVariant)
				}
			},
			confirmButton = {
				TextButton(
						onClick = {
							sending = true; msg = null
							val go = {
								if (rows.size == 1 &&
										wallet.isSilentPaymentAddress(rows[0].address)) {
									wallet.sendBtc(rows.toList(), chosenRate,
											selectedUtxos, sweep) { err ->
										sending = false
										if (err == null) onClose() else msg = err
									}
								} else {
									wallet.prepareBtcSend(rows.toList(), chosenRate,
											selectedUtxos, sweep) { info, err ->
										sending = false
										if (info != null) plan = info else msg = err
									}
								}
							}
							if (wallet.require2fa) {
								wallet.verifyWalletPassword(authPw.toCharArray()) { ok ->
									authPw = ""
									if (ok) go() else { sending = false
										msg = "Incorrect wallet password." }
								}
							} else go()
						},
						enabled = !sending &&
								rows.all { it.address.isNotBlank() && (sweep || it.amount.isNotBlank()) } &&
								(!wallet.require2fa || authPw.isNotEmpty())) {
					Text(if (sending) "Sending…" else "Send BTC")
				}
			},
			dismissButton = {
				TextButton(onClick = onClose, enabled = !sending) { Text("Cancel") }
			})
	if (showCoins) CoinControlDialog(wallet, selectedUtxos) {
		selectedUtxos = it; showCoins = false
	}
	plan?.let { p ->
		BtcReviewDialog(p,
				sending = sending,
				onConfirm = {
					sending = true; msg = null
					wallet.confirmBtcSend(p) { err ->
						sending = false; plan = null
						if (err == null) onClose() else msg = err
					}
				},
				onCancel = { if (!sending) { plan = null; wallet.cancelPreparedSend() } })
	}
}

private fun satToBtc(sat: Long): String =
		java.math.BigDecimal(sat).movePointLeft(8)
				.setScale(8, java.math.RoundingMode.DOWN)
				.stripTrailingZeros().toPlainString()

@Composable
private fun BtcReviewDialog(plan: WalletModel.BtcPlanInfo, sending: Boolean,
		onConfirm: () -> Unit, onCancel: () -> Unit) {
	AlertDialog(
			onDismissRequest = onCancel,
			title = { Text("Review transaction") },
			text = {
				Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
					ReviewRow("Amount", "${satToBtc(plan.amountSat)} BTC")
					ReviewRow("Network fee", "${satToBtc(plan.feeSat)} BTC")
					ReviewRow("Total",
							"${satToBtc(plan.amountSat + plan.feeSat)} BTC")
					ReviewRow("Fee rate",
							"${"%.1f".format(plan.feeRateSatPerVb)} sat/vB")
					ReviewRow("Inputs", plan.inputCount.toString())
					if (plan.changeSat > 0)
						ReviewRow("Change", "${satToBtc(plan.changeSat)} BTC")
					val privacyColor = when (plan.privacyLevel) {
						"HIGH" -> MaterialTheme.colorScheme.primary
						"MEDIUM" -> MaterialTheme.colorScheme.tertiary
						else -> MaterialTheme.colorScheme.error
					}
					Row(Modifier.fillMaxWidth(),
							verticalAlignment = Alignment.CenterVertically) {
						Text("Privacy", Modifier.weight(1f),
								style = MaterialTheme.typography.bodyMedium,
								color = MaterialTheme.colorScheme.onSurfaceVariant)
						Text(plan.privacyLevel, color = privacyColor,
								fontWeight = FontWeight.Medium,
								style = MaterialTheme.typography.bodyMedium)
					}
					if (plan.privacyNote != null)
						Text(plan.privacyNote!!,
								style = MaterialTheme.typography.labelSmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant)
					Divider(color = MaterialTheme.colorScheme.outlineVariant)
					Text("Transaction id",
							style = MaterialTheme.typography.labelSmall,
							color = MaterialTheme.colorScheme.onSurfaceVariant)
					Text(plan.fingerprint.take(32) + "…",
							style = MaterialTheme.typography.labelSmall,
							fontFamily = androidx.compose.ui.text.font.FontFamily
									.Monospace)
					Text("This exact transaction will be broadcast.",
							style = MaterialTheme.typography.labelSmall,
							color = MaterialTheme.colorScheme.onSurfaceVariant)
				}
			},
			confirmButton = {
				TextButton(onClick = onConfirm, enabled = !sending) {
					Text(if (sending) "Sending…" else "Confirm and send")
				}
			},
			dismissButton = {
				TextButton(onClick = onCancel, enabled = !sending) { Text("Back") }
			})
}

@Composable
private fun ReviewRow(label: String, value: String) {
	Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
		Text(label, Modifier.weight(1f),
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant)
		Text(value, style = MaterialTheme.typography.bodyMedium,
				fontWeight = FontWeight.Medium)
	}
}

@Composable
private fun BtcNodeDialog(current: String, onSave: (String) -> Unit, onClose: () -> Unit) {
	var server by remember { mutableStateOf(current) }
	val presets = listOf(
			"electrum.blockstream.info:50001",
			"fulcrum.sethforprivacy.com:50001",
			"electrum.emzy.de:50001")
	AlertDialog(
			onDismissRequest = onClose,
			title = { Text("Electrum server") },
			text = {
				Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
					Text("Pick a server or enter host:port. All traffic goes over " +
							"Tor.", style = MaterialTheme.typography.bodyMedium,
							color = MaterialTheme.colorScheme.onSurfaceVariant)
					presets.forEach { p ->
						TextButton(onClick = { server = p }) {
							Text(p, style = MaterialTheme.typography.labelMedium)
						}
					}
					OutlinedTextField(server, { server = it },
							label = { Text("host:port") }, singleLine = true,
							modifier = Modifier.fillMaxWidth())
				}
			},
			confirmButton = {
				TextButton(onClick = { onSave(server) },
						enabled = server.isNotBlank()) { Text("Use server") }
			},
			dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } })
}

@Composable
private fun AccountSelector(wallet: WalletModel) {
	var menuOpen by remember { mutableStateOf(false) }
	var showAdd by remember { mutableStateOf(false) }
	val current = wallet.accounts.firstOrNull { it.index == wallet.selectedAccount }
	WalletCard {
		Row(verticalAlignment = Alignment.CenterVertically) {
			Column(Modifier.weight(1f)) {
				Text("Account", style = MaterialTheme.typography.labelMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant)
				Text(current?.name ?: "Account",
						style = MaterialTheme.typography.titleMedium,
						fontWeight = FontWeight.SemiBold)
			}
			Box {
				OutlinedButton(onClick = { menuOpen = true }) { Text("Switch") }
				DropdownMenu(expanded = menuOpen,
						onDismissRequest = { menuOpen = false }) {
					wallet.accounts.forEach { a ->
						DropdownMenuItem(text = { Text(a.name) },
								onClick = { menuOpen = false; wallet.selectAccount(a.index) })
					}
					DropdownMenuItem(text = { Text("+ Add account") },
							onClick = { menuOpen = false; showAdd = true })
				}
			}
		}
	}
	if (showAdd) {
		var name by remember { mutableStateOf("") }
		AlertDialog(
				onDismissRequest = { showAdd = false },
				title = { Text("New account") },
				text = {
					OutlinedTextField(name, { name = it }, singleLine = true,
							label = { Text("Account name") },
							modifier = Modifier.fillMaxWidth())
				},
				confirmButton = {
					TextButton(onClick = { wallet.addAccount(name); showAdd = false }) {
						Text("Add")
					}
				},
				dismissButton = {
					TextButton(onClick = { showAdd = false }) { Text("Cancel") }
				})
	}
}


@Composable
private fun AddWalletDialog(wallet: WalletModel, onClose: () -> Unit) {
	var showImport by remember { mutableStateOf(false) }
	AlertDialog(
			onDismissRequest = onClose,
			title = { Text("Add wallet") },
			text = {
				Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
					Text("Each wallet is a single coin with its own recovery phrase " +
							"and password.", style = MaterialTheme.typography.bodyMedium,
							color = MaterialTheme.colorScheme.onSurfaceVariant)
					Button(onClick = { onClose(); wallet.beginCreate("ETH") },
							modifier = Modifier.fillMaxWidth()) { Text("New Ethereum wallet") }
					Button(onClick = { onClose(); wallet.beginCreate("BTC") },
							modifier = Modifier.fillMaxWidth()) { Text("New Bitcoin wallet") }
					Button(onClick = { onClose(); wallet.beginCreate("XMR") },
							modifier = Modifier.fillMaxWidth()) { Text("New Monero wallet") }
					OutlinedButton(onClick = { showImport = true },
							modifier = Modifier.fillMaxWidth()) { Text("Import recovery phrase") }
				}
			},
			confirmButton = { TextButton(onClick = onClose) { Text("Cancel") } })
	if (showImport) ImportDialog(wallet) { showImport = false; onClose() }
}

@Composable
private fun ReceiveDialog(wallet: WalletModel, onClose: () -> Unit) {
	val clipboard = LocalClipboardManager.current
	val address = wallet.receiveAddresses.lastOrNull()?.address
			?: wallet.primaryAddress
	AlertDialog(
			onDismissRequest = onClose,
			title = { Text("Receive ETH") },
			text = {
				Column(horizontalAlignment = Alignment.CenterHorizontally,
						verticalArrangement = Arrangement.spacedBy(12.dp)) {
					if (address.isNotEmpty()) {
						val qr = remember(address) { QrCode.pngFor(address, 340) }
						val bmp = remember(qr) {
							org.jetbrains.skia.Image.makeFromEncoded(qr)
									.toComposeImageBitmap()
						}
						Box(Modifier.background(Color.White, RoundedCornerShape(14.dp))
								.padding(12.dp)) {
							Image(bmp, contentDescription = "Receive address QR",
									modifier = Modifier.size(200.dp))
						}
						Box(Modifier.fillMaxWidth()
								.background(MaterialTheme.colorScheme.surfaceContainerHighest,
										RoundedCornerShape(10.dp))
								.padding(start = 12.dp, top = 10.dp, bottom = 10.dp,
										end = 4.dp)) {
							Row(verticalAlignment = Alignment.CenterVertically) {
								Text(address, Modifier.weight(1f),
										fontFamily = FontFamily.Monospace,
										style = MaterialTheme.typography.bodyMedium)
								IconButton(onClick = {
									clipboard.setText(AnnotatedString(address))
								}) {
									Icon(Icons.Filled.ContentCopy,
											contentDescription = "Copy address")
								}
							}
						}
						Text("Send only Ethereum (ETH) to this address.",
								style = MaterialTheme.typography.labelSmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant)
					}
				}
			},
			confirmButton = {
				TextButton(onClick = { wallet.newReceiveAddress() }) {
					Text("New address")
				}
			},
			dismissButton = { TextButton(onClick = onClose) { Text("Close") } })
}

@Composable
private fun AssetRow(symbol: String, amount: String, usd: String?) {
	Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
			verticalAlignment = Alignment.CenterVertically) {
		Text(symbol, Modifier.weight(1f),
				style = MaterialTheme.typography.titleMedium,
				fontWeight = FontWeight.Medium)
		Column(horizontalAlignment = Alignment.End) {
			Text(amount, style = MaterialTheme.typography.bodyLarge)
			if (usd != null) {
				Text(usd, style = MaterialTheme.typography.labelSmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant)
			}
		}
	}
}

@Composable
private fun SendDialog(wallet: WalletModel, onClose: () -> Unit) {
	var assetMenu by remember { mutableStateOf(false) }
	var assetSymbol by remember { mutableStateOf("ETH") }
	var to by remember { mutableStateOf("") }
	var amount by remember { mutableStateOf("") }
	var authPw by remember { mutableStateOf("") }
	var msg by remember { mutableStateOf<String?>(null) }
	var sending by remember { mutableStateOf(false) }
	var pickTo by remember { mutableStateOf(false) }
	var saveTo by remember { mutableStateOf(false) }
	val symbols = wallet.tokenSymbols
	val assetBal = wallet.assetBalances.firstOrNull { it.symbol == assetSymbol }
	val balAmount = assetBal?.amount ?: BigDecimal.ZERO
	fun setPct(p: Int) {
		val v = balAmount.multiply(BigDecimal(p)).divide(BigDecimal(100))
		amount = v.setScale(6, RoundingMode.DOWN).stripTrailingZeros().toPlainString()
		msg = null
	}
	fun setMax() {
		val v = if (assetSymbol == "ETH") (balAmount - GAS_RESERVE)
				.coerceAtLeast(BigDecimal.ZERO) else balAmount
		amount = v.setScale(6, RoundingMode.DOWN).stripTrailingZeros().toPlainString()
		msg = null
	}
	AlertDialog(
			onDismissRequest = { if (!sending) onClose() },
			title = { Text("Send") },
			text = {
				Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
					Box {
						OutlinedButton(onClick = { assetMenu = true },
								modifier = Modifier.fillMaxWidth()) {
							Text("Asset: $assetSymbol", modifier = Modifier.weight(1f))
							Icon(Icons.Filled.ExpandMore, contentDescription = null,
									modifier = Modifier.size(18.dp))
						}
						DropdownMenu(expanded = assetMenu,
								onDismissRequest = { assetMenu = false }) {
							symbols.forEach { s ->
								DropdownMenuItem(text = { Text(s) },
										onClick = { assetSymbol = s; assetMenu = false
											amount = ""; msg = null })
							}
						}
					}
					OutlinedTextField(to, { to = it; msg = null },
							label = { Text("Recipient 0x… address") },
							singleLine = true, modifier = Modifier.fillMaxWidth(),
							trailingIcon = {
								Row {
									IconButton(onClick = { pickTo = true }) {
										Icon(Icons.Filled.Contacts, "Address book",
												modifier = Modifier.size(20.dp))
									}
									if (to.isNotBlank()) IconButton(onClick = { saveTo = true }) {
										Icon(Icons.Filled.Star, "Save recipient",
												modifier = Modifier.size(20.dp))
									}
								}
							})
					Row(verticalAlignment = Alignment.CenterVertically) {
						Text("Amount ($assetSymbol)",
								style = MaterialTheme.typography.labelMedium,
								modifier = Modifier.weight(1f))
						Text("Balance: ${assetBal?.formatted ?: "—"} $assetSymbol",
								style = MaterialTheme.typography.labelMedium,
								color = MaterialTheme.colorScheme.onSurfaceVariant)
					}
					OutlinedTextField(amount, { amount = it; msg = null },
							singleLine = true, modifier = Modifier.fillMaxWidth())
					Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
						PctChip("25%") { setPct(25) }
						PctChip("50%") { setPct(50) }
						PctChip("75%") { setPct(75) }
						PctChip("Max") { setMax() }
					}
					if (msg != null) {
						Text(msg!!, color = MaterialTheme.colorScheme.error,
								style = MaterialTheme.typography.labelMedium)
					}
					if (wallet.require2fa) {
						PwField("Wallet password", authPw) { authPw = it; msg = null }
					}
					Text("Ethereum addresses only (0x…). Sent over Tor from the " +
							"address that holds the funds.",
							style = MaterialTheme.typography.labelSmall,
							color = MaterialTheme.colorScheme.onSurfaceVariant)
				}
			},
			confirmButton = {
				TextButton(
						onClick = {
							sending = true; msg = null
							val go = {
								wallet.send(assetSymbol, to, amount) { err ->
									sending = false
									if (err == null) onClose() else msg = err
								}
							}
							if (wallet.require2fa) {
								wallet.verifyWalletPassword(authPw.toCharArray()) { ok ->
									authPw = ""
									if (ok) go() else { sending = false
										msg = "Incorrect wallet password." }
								}
							} else go()
						},
						enabled = !sending && to.isNotBlank() && amount.isNotBlank() &&
								(!wallet.require2fa || authPw.isNotEmpty())) {
					Text(if (sending) "Sending…" else "Send $assetSymbol")
				}
			},
			dismissButton = {
				TextButton(onClick = onClose, enabled = !sending) { Text("Cancel") }
			})
	if (pickTo) ContactPickerDialog(wallet, "ETH",
			onPick = { to = it; pickTo = false }, onClose = { pickTo = false })
	if (saveTo) SaveContactDialog(wallet, "ETH", to) { saveTo = false }
}

@Composable
private fun PctChip(label: String, onClick: () -> Unit) {
	OutlinedButton(onClick = onClick,
			contentPadding = androidx.compose.foundation.layout.PaddingValues(
					horizontal = 12.dp, vertical = 4.dp)) {
		Text(label, style = MaterialTheme.typography.labelMedium)
	}
}

@Composable
private fun ImportDialog(wallet: WalletModel, onClose: () -> Unit) {
	var name by remember { mutableStateOf("") }
	var coin by remember { mutableStateOf("ETH") }
	var words by remember { mutableStateOf("") }
	var height by remember { mutableStateOf("") }
	var pw by remember { mutableStateOf("") }
	var msg by remember { mutableStateOf<String?>(null) }
	val isXmr = coin == "XMR"
	AlertDialog(
			onDismissRequest = { if (!wallet.busy) onClose() },
			title = { Text("Import recovery phrase") },
			text = {
				Column(Modifier.verticalScroll(rememberScrollState()),
						verticalArrangement = Arrangement.spacedBy(8.dp)) {
					Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
						FilterChip(coin == "ETH", { coin = "ETH"; msg = null },
								label = { Text("Ethereum") })
						FilterChip(coin == "BTC", { coin = "BTC"; msg = null },
								label = { Text("Bitcoin") })
						FilterChip(coin == "XMR", { coin = "XMR"; msg = null },
								label = { Text("Monero") })
					}
					OutlinedTextField(name, { name = it }, singleLine = true,
							label = { Text("Wallet name") },
							modifier = Modifier.fillMaxWidth())
					OutlinedTextField(words, { words = it; msg = null },
							label = { Text(if (isXmr) "25-word Monero phrase"
									else "12 or 24-word phrase") }, minLines = 3,
							modifier = Modifier.fillMaxWidth())
					if (isXmr) {
						OutlinedTextField(height, {
									height = it.filter { c -> c.isDigit() }; msg = null
								},
								label = { Text("Restore height (optional)") },
								singleLine = true, modifier = Modifier.fillMaxWidth())
						Text("The block height around when this wallet was created. " +
								"Leaving it blank scans from the start, which is slow " +
								"over Tor.", style = MaterialTheme.typography.labelSmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant)
					}
					PwField("Wallet password", pw) { pw = it; msg = null }
					if (msg != null) {
						Text(msg!!, color = MaterialTheme.colorScheme.error,
								style = MaterialTheme.typography.labelMedium)
					}
					if (isXmr && wallet.busy) {
						Text("Checking the phrase over Tor…",
								style = MaterialTheme.typography.labelSmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant)
					}
				}
			},
			confirmButton = {
				TextButton(onClick = {
					if (pw.length < 8) msg = "Password: at least 8 characters."
					else if (isXmr) wallet.importXmrWallet(name, words,
							height.toLongOrNull() ?: 0L, pw.toCharArray()) { ok ->
						if (ok) onClose() else msg = wallet.error
					}
					else wallet.importWallet(name, coin, words, pw.toCharArray()) { ok ->
						if (ok) onClose() else msg = wallet.error
					}
				}, enabled = !wallet.busy) {
					Text(if (wallet.busy) "Checking…" else "Import")
				}
			},
			dismissButton = {
				TextButton(onClick = onClose, enabled = !wallet.busy) { Text("Cancel") }
			})
}

@Composable
private fun NodeDialog(current: String, onSave: (String) -> Unit,
		onClose: () -> Unit) {
	var url by remember { mutableStateOf(current) }
	val presets = listOf(
			"https://ethereum-rpc.publicnode.com",
			"https://rpc.flashbots.net",
			"https://eth-mainnet.public.blastapi.io",
			"https://eth.nownodes.io",
			"https://eth.llamarpc.com")
	AlertDialog(
			onDismissRequest = onClose,
			title = { Text("Ethereum node") },
			text = {
				Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
					Text("Pick a node or enter your own RPC URL. All requests go " +
							"over Tor.", style = MaterialTheme.typography.bodyMedium,
							color = MaterialTheme.colorScheme.onSurfaceVariant)
					presets.forEach { p ->
						TextButton(onClick = { url = p }) {
							Text(p, style = MaterialTheme.typography.labelMedium)
						}
					}
					OutlinedTextField(url, { url = it }, label = { Text("RPC URL") },
							singleLine = true, modifier = Modifier.fillMaxWidth())
				}
			},
			confirmButton = {
				TextButton(onClick = { onSave(url) },
						enabled = url.isNotBlank()) { Text("Use node") }
			},
			dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } })
}

@Composable
private fun WalletCard(content: @Composable ColumnScope.() -> Unit) {
	Card(Modifier.fillMaxWidth(),
			colors = CardDefaults.cardColors(
					containerColor = MaterialTheme.colorScheme.surface)) {
		Column(Modifier.padding(16.dp),
				verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
	}
}

@Composable
private fun PwField(label: String, value: String, onChange: (String) -> Unit) {
	OutlinedTextField(value, onChange, label = { Text(label) }, singleLine = true,
			visualTransformation = PasswordVisualTransformation(),
			modifier = Modifier.fillMaxWidth())
}

private const val SEED_CLIP_SECONDS = 30

private val GAS_RESERVE = BigDecimal("0.0005")

private fun shortAddr(a: String): String =
		if (a.length > 12) a.take(6) + "…" + a.takeLast(4) else a

private fun shortNode(url: String): String =
		url.removePrefix("https://").removePrefix("http://").substringBefore("/")

@Composable
private fun NodeHealthDot(wallet: WalletModel, chain: WalletModel.Chain, url: String) {
	LaunchedEffect(url) { wallet.checkNodeHealth(chain, url) }
	val health = wallet.nodeHealth[url.trim()]
	val (color, label) = when (health) {
		WalletModel.NodeHealth.GREEN -> ConnectedGreen to "Healthy"
		WalletModel.NodeHealth.ORANGE -> Color(0xFFE0A400) to "Slow"
		WalletModel.NodeHealth.RED ->
			MaterialTheme.colorScheme.error to "Unreachable"
		else -> MaterialTheme.colorScheme.onSurfaceVariant to "Checking…"
	}
	Row(verticalAlignment = Alignment.CenterVertically,
			modifier = Modifier.clip(RoundedCornerShape(8.dp))
					.clickable { wallet.checkNodeHealth(chain, url) }
					.padding(horizontal = 8.dp, vertical = 4.dp)) {
		if (health == null || health == WalletModel.NodeHealth.CHECKING) {
			CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 2.dp)
		} else {
			Box(Modifier.size(10.dp).clip(CircleShape).background(color))
		}
		Spacer(Modifier.size(6.dp))
		Text(label, style = MaterialTheme.typography.labelSmall, color = color)
	}
}
