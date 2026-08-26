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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Divider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.toComposeImageBitmap
import chat.zerion.desktop.ui.components.SecureClipboard
import chat.zerion.desktop.ui.components.StrengthPasswordField
import chat.zerion.desktop.ui.vault.PasswordEntry
import chat.zerion.desktop.ui.vault.VaultModel
import java.io.File

import java.security.SecureRandom

@Composable
fun VaultScreen(vault: VaultModel, onClose: () -> Unit) {
	when (vault.phase) {
		VaultModel.Phase.NOT_CREATED -> VaultOnboarding(vault, onClose)
		VaultModel.Phase.LOCKED -> VaultUnlock(vault, onClose)
		VaultModel.Phase.UNLOCKED -> VaultHome(vault, onClose)
	}
}

@Composable
private fun VaultHeaderBar(title: String, onBack: () -> Unit,
		trailing: @Composable () -> Unit = {}) {
	Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
			.padding(horizontal = 12.dp, vertical = 10.dp),
			verticalAlignment = Alignment.CenterVertically) {
		IconButton(onClick = onBack) {
			Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
		}
		Spacer(Modifier.width(4.dp))
		Text(title, style = MaterialTheme.typography.titleLarge,
				fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
		trailing()
	}
}

@Composable
private fun VaultGate(onClose: () -> Unit, content: @Composable () -> Unit) {
	Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
		VaultHeaderBar("Vault", onClose)
		Box(Modifier.weight(1f).fillMaxWidth(),
				contentAlignment = Alignment.Center) {
			Card(
					modifier = Modifier.widthIn(max = 400.dp).padding(24.dp),
					shape = RoundedCornerShape(20.dp),
					colors = CardDefaults.cardColors(
							containerColor = MaterialTheme.colorScheme.surface),
					elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
				Column(Modifier.fillMaxWidth()
						.padding(horizontal = 30.dp, vertical = 34.dp),
						horizontalAlignment = Alignment.CenterHorizontally,
						verticalArrangement = Arrangement.spacedBy(16.dp)) {
					Box(Modifier.size(66.dp).clip(RoundedCornerShape(20.dp))
							.background(MaterialTheme.colorScheme.primary
									.copy(alpha = 0.12f)),
							contentAlignment = Alignment.Center) {
						Icon(Icons.Filled.Lock, contentDescription = null,
								modifier = Modifier.size(30.dp),
								tint = MaterialTheme.colorScheme.primary)
					}
					content()
				}
			}
		}
	}
}

@Composable
private fun VaultBadgeFooter(text: String) {
	Row(verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(6.dp)) {
		Icon(Icons.Filled.Lock, contentDescription = null,
				modifier = Modifier.size(13.dp),
				tint = MaterialTheme.colorScheme.onSurfaceVariant)
		Text(text, style = MaterialTheme.typography.labelSmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				textAlign = TextAlign.Center)
	}
}

@Composable
private fun VaultOnboarding(vault: VaultModel, onClose: () -> Unit) {
	var pw by remember { mutableStateOf("") }
	var confirm by remember { mutableStateOf("") }
	var msg by remember { mutableStateOf<String?>(null) }
	VaultGate(onClose) {
		Text("Create your vault", style = MaterialTheme.typography.headlineSmall,
				fontWeight = FontWeight.SemiBold)
		Text("An encrypted store on this device for your passwords, notes, and " +
				"wallet. It has its own password, separate from your sign-in, and " +
				"is bound to this machine.",
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				textAlign = TextAlign.Center)
		StrengthPasswordField("Vault password", pw, { pw = it; msg = null })
		PwField("Confirm password", confirm) { confirm = it; msg = null }
		if (msg != null) {
			Text(msg!!, color = MaterialTheme.colorScheme.error,
					style = MaterialTheme.typography.labelMedium,
					textAlign = TextAlign.Center)
		}
		Button(
				onClick = {
					when {
						pw.length < 8 -> msg = "Use at least 8 characters."
						pw != confirm -> msg = "Passwords don't match."
						else -> vault.createVault(pw.toCharArray()) {}
					}
				},
				enabled = !vault.busy,
				modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
			Text(if (vault.busy) "Creating…" else "Create vault")
		}
		VaultBadgeFooter("There is no recovery if you forget this password.")
	}
}

@Composable
private fun VaultUnlock(vault: VaultModel, onClose: () -> Unit) {
	var pw by remember { mutableStateOf("") }
	fun go() { if (pw.isNotEmpty() && !vault.busy) vault.unlock(pw.toCharArray()) {} }
	VaultGate(onClose) {
		Text("Vault locked", style = MaterialTheme.typography.headlineSmall,
				fontWeight = FontWeight.SemiBold)
		Text("Enter your vault password to unlock.",
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				textAlign = TextAlign.Center)
		PwField("Vault password", pw) { pw = it }
		if (vault.error != null) {
			Text(vault.error!!, color = MaterialTheme.colorScheme.error,
					style = MaterialTheme.typography.labelMedium,
					textAlign = TextAlign.Center)
		}
		Button(
				onClick = { go() },
				enabled = !vault.busy && pw.isNotEmpty(),
				modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
			Text(if (vault.busy) "Unlocking…" else "Unlock")
		}
		VaultBadgeFooter("Encrypted and bound to this device.")
	}
}

private enum class VaultTab { PASSWORDS, NOTES, DOCUMENTS, GALLERY, WALLET }

@Composable
private fun VaultHome(vault: VaultModel, onClose: () -> Unit) {
	var tab by remember { mutableStateOf(VaultTab.PASSWORDS) }
	var showAdd by remember { mutableStateOf(false) }
	var viewPasswordId by remember { mutableStateOf<String?>(null) }
	var viewNoteId by remember { mutableStateOf<String?>(null) }
	var mediaViewId by remember { mutableStateOf<String?>(null) }
	var showSettings by remember { mutableStateOf(false) }
	var openProtected by remember {
		mutableStateOf<Pair<VaultModel.RevealedNote, CharArray>?>(null)
	}

	Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
		VaultHeaderBar("Vault", onClose) {
			IconButton(onClick = { showSettings = true }) {
				Icon(Icons.Filled.Settings, contentDescription = "Vault settings")
			}
			IconButton(onClick = { vault.lock() }) {
				Icon(Icons.Filled.Lock, contentDescription = "Lock vault")
			}
		}
		Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
				horizontalArrangement = Arrangement.spacedBy(8.dp)) {
			FilterChip(tab == VaultTab.PASSWORDS, { tab = VaultTab.PASSWORDS },
					label = { Text("Passwords") })
			FilterChip(tab == VaultTab.NOTES, { tab = VaultTab.NOTES },
					label = { Text("Notes") })
			FilterChip(tab == VaultTab.DOCUMENTS, { tab = VaultTab.DOCUMENTS },
					label = { Text("Files") })
			FilterChip(tab == VaultTab.GALLERY, { tab = VaultTab.GALLERY },
					label = { Text("Gallery") })
			FilterChip(tab == VaultTab.WALLET, { tab = VaultTab.WALLET },
					label = { Text("Wallet") })
		}
		if (tab == VaultTab.WALLET) {
			Box(Modifier.weight(1f).fillMaxWidth()) {
				WalletSection(vault.wallet)
			}
			return@Column
		}
		Box(Modifier.weight(1f).fillMaxWidth()) {
			when (tab) {
				VaultTab.NOTES -> NotesPane(vault, onOpenNote = { viewNoteId = it },
						onOpenProtected = { r, s -> openProtected = r to s })
				VaultTab.DOCUMENTS -> DocumentsPane(vault)
				VaultTab.GALLERY -> GalleryPane(vault) { mediaViewId = it }
				else -> {
					val entries = vault.passwords
					if (entries.isEmpty()) {
						Column(Modifier.fillMaxSize(),
								verticalArrangement = Arrangement.Center,
								horizontalAlignment = Alignment.CenterHorizontally) {
							Icon(Icons.Filled.Password, contentDescription = null,
									modifier = Modifier.size(48.dp),
									tint = MaterialTheme.colorScheme.onSurfaceVariant)
							Spacer(Modifier.size(12.dp))
							Text("No saved passwords yet",
									color = MaterialTheme.colorScheme.onSurfaceVariant)
						}
					} else {
						LazyColumn(Modifier.fillMaxSize()
								.padding(horizontal = 16.dp)) {
							items(entries, key = { it.id }) { e ->
								EntryRow(e.title, Icons.Filled.Password) {
									vault.onActivity(); viewPasswordId = e.id
								}
							}
						}
					}
				}
			}
			FilledIconButton(onClick = {
				vault.onActivity()
				when (tab) {
					VaultTab.DOCUMENTS -> {
						val f = chooseVaultFile("Add file to vault")
						if (f != null) vault.addDocument(f) {}
					}
					VaultTab.GALLERY -> {
						val f = chooseVaultFile("Add photo or video")
						if (f != null) vault.addMedia(f) {}
					}
					else -> showAdd = true
				}
			},
					modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)
							.size(56.dp)) {
				Icon(Icons.Filled.Add, contentDescription = "Add")
			}
		}
	}

	if (showAdd) {
		if (tab == VaultTab.PASSWORDS) {
			AddPasswordDialog(
					onSave = { vault.addPassword(it) {}; showAdd = false },
					onClose = { showAdd = false })
		} else {
			AddNoteDialog(
					onSave = { t, b, secret ->
						if (secret != null) vault.addProtectedNote(secret, t, b) {}
						else vault.addNote(t, b) {}
						showAdd = false
					},
					onClose = { showAdd = false })
		}
	}
	viewPasswordId?.let { id ->
		ViewPasswordDialog(vault, id, onDelete = { vault.deleteItem(id) },
				onClose = { viewPasswordId = null })
	}
	viewNoteId?.let { id ->
		ViewNoteDialog(vault, id, onDelete = { vault.deleteItem(id) },
				onClose = { viewNoteId = null })
	}
	openProtected?.let { (note, secret) ->
		ProtectedNoteDialog(vault, note, secret,
				onDelete = { vault.deleteItem(note.id) },
				onClose = { openProtected = null })
	}
	mediaViewId?.let { id ->
		val entry = vault.media.firstOrNull { it.id == id }
		MediaViewDialog(vault, id, entry?.title ?: "",
				onDelete = { vault.deleteItem(id) },
				onClose = { mediaViewId = null })
	}
	if (showSettings) {
		VaultSettingsDialog(vault, onClose = { showSettings = false })
	}
}

@Composable
private fun EntryRow(title: String, icon: ImageVector, onClick: () -> Unit) {
	Row(Modifier.fillMaxWidth().clickable(onClick = onClick)
			.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
		Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
				.background(MaterialTheme.colorScheme.surfaceContainerHighest),
				contentAlignment = Alignment.Center) {
			Icon(icon, contentDescription = null,
					tint = MaterialTheme.colorScheme.primary,
					modifier = Modifier.size(20.dp))
		}
		Spacer(Modifier.width(14.dp))
		Text(title, style = MaterialTheme.typography.bodyLarge,
				fontWeight = FontWeight.Medium)
	}
}

@Composable
private fun AddPasswordDialog(onSave: (PasswordEntry) -> Unit, onClose: () -> Unit) {
	var title by remember { mutableStateOf("") }
	var username by remember { mutableStateOf("") }
	var password by remember { mutableStateOf("") }
	var url by remember { mutableStateOf("") }
	var notes by remember { mutableStateOf("") }
	AlertDialog(
			onDismissRequest = onClose,
			title = { Text("Add password") },
			text = {
				Column(Modifier.verticalScroll(rememberScrollState()),
						verticalArrangement = Arrangement.spacedBy(8.dp)) {
					Field("Title", title) { title = it }
					Field("Username / email", username) { username = it }
					Row(verticalAlignment = Alignment.CenterVertically) {
						OutlinedTextField(password, { password = it },
								label = { Text("Password") }, singleLine = true,
								modifier = Modifier.weight(1f))
						IconButton(onClick = { password = generatePassword() }) {
							Icon(Icons.Filled.Refresh,
									contentDescription = "Generate")
						}
					}
					Field("Website (optional)", url) { url = it }
					Field("Notes (optional)", notes) { notes = it }
				}
			},
			confirmButton = {
				TextButton(
						onClick = {
							onSave(PasswordEntry(title.trim(), username.trim(),
									password, url.trim(), notes.trim()))
						},
						enabled = title.isNotBlank() && password.isNotEmpty()) {
					Text("Save")
				}
			},
			dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } })
}

@Composable
private fun ViewPasswordDialog(vault: VaultModel, id: String,
		onDelete: () -> Unit, onClose: () -> Unit) {
	val clipboard = LocalClipboardManager.current
	val scope = rememberCoroutineScope()
	var entry by remember { mutableStateOf<PasswordEntry?>(null) }
	var reveal by remember { mutableStateOf(false) }
	androidx.compose.runtime.LaunchedEffect(id) {
		vault.loadPassword(id) { entry = it }
	}
	AlertDialog(
			onDismissRequest = onClose,
			title = { Text(entry?.title ?: "Password") },
			text = {
				val e = entry
				if (e == null) Text("Decrypting…") else Column(
						verticalArrangement = Arrangement.spacedBy(10.dp)) {
					if (e.username.isNotEmpty()) CopyRow("Username", e.username,
							false, clipboard, scope)
					CopyRow("Password", e.password, !reveal, clipboard, scope,
							onToggle = { reveal = !reveal }, revealed = reveal)
					if (e.url.isNotEmpty()) {
						Text("Website", style = MaterialTheme.typography.labelMedium,
								color = MaterialTheme.colorScheme.onSurfaceVariant)
						Text(e.url, style = MaterialTheme.typography.bodyMedium)
					}
					if (e.notes.isNotEmpty()) {
						Text("Notes", style = MaterialTheme.typography.labelMedium,
								color = MaterialTheme.colorScheme.onSurfaceVariant)
						Text(e.notes, style = MaterialTheme.typography.bodyMedium)
					}
					Text("Copied values clear from the clipboard after 30s.",
							style = MaterialTheme.typography.labelSmall,
							color = MaterialTheme.colorScheme.onSurfaceVariant)
				}
			},
			confirmButton = { TextButton(onClick = onClose) { Text("Close") } },
			dismissButton = {
				TextButton(onClick = { onDelete(); onClose() }) {
					Icon(Icons.Filled.Delete, contentDescription = null,
							modifier = Modifier.size(18.dp))
					Spacer(Modifier.width(4.dp))
					Text("Delete")
				}
			})
}

@Composable
private fun CopyRow(label: String, value: String, masked: Boolean,
		clipboard: androidx.compose.ui.platform.ClipboardManager,
		scope: kotlinx.coroutines.CoroutineScope,
		onToggle: (() -> Unit)? = null, revealed: Boolean = false) {
	Column {
		Text(label, style = MaterialTheme.typography.labelMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant)
		Row(verticalAlignment = Alignment.CenterVertically) {
			Text(if (masked) "•".repeat(minOf(value.length, 16)) else value,
					modifier = Modifier.weight(1f),
					fontFamily = FontFamily.Monospace,
					style = MaterialTheme.typography.bodyMedium)
			if (onToggle != null) {
				IconButton(onClick = onToggle) {
					Icon(if (revealed) Icons.Filled.VisibilityOff
							else Icons.Filled.Visibility,
							contentDescription = "Reveal")
				}
			}
			IconButton(onClick = {
				SecureClipboard.copyWithAutoClear(clipboard, scope, value, 30)
			}) {
				Icon(Icons.Filled.ContentCopy, contentDescription = "Copy $label")
			}
		}
	}
}

@Composable
private fun AddNoteDialog(onSave: (String, String, CharArray?) -> Unit,
		onClose: () -> Unit) {
	var title by remember { mutableStateOf("") }
	var body by remember { mutableStateOf("") }
	var lock by remember { mutableStateOf(false) }
	var secret by remember { mutableStateOf("") }
	AlertDialog(
			onDismissRequest = onClose,
			title = { Text("Add note") },
			text = {
				Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
					Field("Title", title) { title = it }
					OutlinedTextField(body, { body = it },
							label = { Text("Note") },
							modifier = Modifier.fillMaxWidth().widthIn(min = 320.dp),
							minLines = 4)
					Row(verticalAlignment = Alignment.CenterVertically,
							modifier = Modifier.clickable { lock = !lock }) {
						Checkbox(lock, { lock = it })
						Spacer(Modifier.width(4.dp))
						Text("Lock this note with a secret key",
								style = MaterialTheme.typography.bodyMedium)
					}
					if (lock) {
						OutlinedTextField(secret, { secret = it },
								label = { Text("Secret key") }, singleLine = true,
								visualTransformation = PasswordVisualTransformation(),
								modifier = Modifier.fillMaxWidth())
						Text("A locked note is hidden from the list. Type its " +
								"secret key in the notes search to reveal it. There " +
								"is no recovery if you forget the key.",
								style = MaterialTheme.typography.labelSmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant)
					}
				}
			},
			confirmButton = {
				TextButton(
						onClick = {
							onSave(title.trim(), body,
									if (lock) secret.toCharArray() else null)
						},
						enabled = (title.isNotBlank() || body.isNotBlank()) &&
								(!lock || secret.length >= 4)) {
					Text("Save")
				}
			},
			dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } })
}

@Composable
private fun NotesPane(vault: VaultModel, onOpenNote: (String) -> Unit,
		onOpenProtected: (VaultModel.RevealedNote, CharArray) -> Unit) {
	var search by remember { mutableStateOf("") }
	var revealed by remember { mutableStateOf<List<VaultModel.RevealedNote>>(emptyList()) }
	fun reveal() {
		if (search.isNotEmpty())
			vault.revealHiddenNotes(search.toCharArray()) { revealed = it }
	}
	Column(Modifier.fillMaxSize()) {
		OutlinedTextField(search, { search = it; if (it.isBlank()) revealed = emptyList() },
				label = { Text("Search notes, or enter a secret key") },
				singleLine = true,
				leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
				keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
				keyboardActions = KeyboardActions(onSearch = { reveal() }),
				modifier = Modifier.fillMaxWidth()
						.padding(horizontal = 16.dp, vertical = 8.dp))
		val filtered = vault.notes.filter {
			search.isBlank() || it.title.contains(search, ignoreCase = true)
		}
		if (filtered.isEmpty() && revealed.isEmpty()) {
			Column(Modifier.fillMaxSize(),
					verticalArrangement = Arrangement.Center,
					horizontalAlignment = Alignment.CenterHorizontally) {
				Icon(Icons.Filled.Notes, contentDescription = null,
						modifier = Modifier.size(48.dp),
						tint = MaterialTheme.colorScheme.onSurfaceVariant)
				Spacer(Modifier.size(12.dp))
				Text(if (search.isNotEmpty()) "No note matches that."
						else "No notes yet",
						color = MaterialTheme.colorScheme.onSurfaceVariant)
			}
		} else {
			LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
				items(revealed, key = { "r:" + it.id }) { r ->
					EntryRow(r.title.ifBlank { "Untitled" }, Icons.Filled.LockOpen) {
						vault.onActivity()
						onOpenProtected(r, search.toCharArray())
					}
				}
				items(filtered, key = { it.id }) { e ->
					EntryRow(e.title, Icons.Filled.Notes) {
						vault.onActivity(); onOpenNote(e.id)
					}
				}
			}
		}
	}
}

@Composable
private fun ProtectedNoteDialog(vault: VaultModel, note: VaultModel.RevealedNote,
		secret: CharArray, onDelete: () -> Unit, onClose: () -> Unit) {
	var title by remember(note.id) { mutableStateOf(note.title) }
	var body by remember(note.id) { mutableStateOf(note.body) }
	var saving by remember(note.id) { mutableStateOf(false) }
	AlertDialog(
			onDismissRequest = onClose,
			title = {
				Row(verticalAlignment = Alignment.CenterVertically) {
					Icon(Icons.Filled.LockOpen, contentDescription = null,
							modifier = Modifier.size(18.dp),
							tint = MaterialTheme.colorScheme.primary)
					Spacer(Modifier.width(6.dp))
					Text("Locked note")
				}
			},
			text = {
				Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
					OutlinedTextField(title, { title = it },
							label = { Text("Title") }, singleLine = true,
							modifier = Modifier.fillMaxWidth().widthIn(min = 320.dp))
					OutlinedTextField(body, { body = it },
							label = { Text("Note") },
							modifier = Modifier.fillMaxWidth(), minLines = 6)
				}
			},
			confirmButton = {
				TextButton(
						onClick = {
							saving = true
							vault.updateProtectedNote(note.id, secret, title.trim(),
									body) {
								saving = false
								onClose()
							}
						},
						enabled = !saving) {
					Text(if (saving) "Saving…" else "Save")
				}
			},
			dismissButton = {
				Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
					TextButton(onClick = { onDelete(); onClose() }) { Text("Delete") }
					TextButton(onClick = onClose) { Text("Close") }
				}
			})
}

@Composable
private fun ViewNoteDialog(vault: VaultModel, id: String,
		onDelete: () -> Unit, onClose: () -> Unit) {
	var title by remember(id) {
		mutableStateOf(vault.notes.find { it.id == id }?.title ?: "")
	}
	var body by remember(id) { mutableStateOf<String?>(null) }
	var saving by remember(id) { mutableStateOf(false) }
	androidx.compose.runtime.LaunchedEffect(id) {
		vault.loadNote(id) { body = it }
	}
	val loaded = body != null
	AlertDialog(
			onDismissRequest = onClose,
			title = { Text("Note") },
			text = {
				if (!loaded) Text("Decrypting…") else Column(
						verticalArrangement = Arrangement.spacedBy(10.dp)) {
					OutlinedTextField(title, { title = it },
							label = { Text("Title") }, singleLine = true,
							modifier = Modifier.fillMaxWidth().widthIn(min = 320.dp))
					OutlinedTextField(body ?: "", { body = it },
							label = { Text("Note") },
							modifier = Modifier.fillMaxWidth(), minLines = 6)
				}
			},
			confirmButton = {
				TextButton(
						onClick = {
							saving = true
							vault.updateNote(id, title.trim(), body ?: "") {
								saving = false
								onClose()
							}
						},
						enabled = loaded && !saving) {
					Text(if (saving) "Saving…" else "Save")
				}
			},
			dismissButton = {
				Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
					TextButton(onClick = { onDelete(); onClose() }) { Text("Delete") }
					TextButton(onClick = onClose) { Text("Close") }
				}
			})
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit) {
	OutlinedTextField(value, onChange, label = { Text(label) }, singleLine = true,
			modifier = Modifier.fillMaxWidth())
}

@Composable
private fun PwField(label: String, value: String, onChange: (String) -> Unit) {
	OutlinedTextField(value, onChange, label = { Text(label) }, singleLine = true,
			visualTransformation = PasswordVisualTransformation(),
			modifier = Modifier.fillMaxWidth())
}

private fun generatePassword(): String {
	val upper = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
	val lower = "abcdefghijklmnopqrstuvwxyz"
	val digit = "0123456789"
	val symbol = "!@#$%^&*()-_=+[]{}|;:,.<>?"
	val all = upper + lower + digit + symbol
	val rnd = SecureRandom()
	val chars = CharArray(20)
	chars[0] = upper[rnd.nextInt(upper.length)]
	chars[1] = lower[rnd.nextInt(lower.length)]
	chars[2] = digit[rnd.nextInt(digit.length)]
	chars[3] = symbol[rnd.nextInt(symbol.length)]
	for (i in 4 until chars.size) chars[i] = all[rnd.nextInt(all.length)]
	for (i in chars.size - 1 downTo 1) {
		val j = rnd.nextInt(i + 1)
		val t = chars[i]; chars[i] = chars[j]; chars[j] = t
	}
	return String(chars)
}

@Composable
private fun VaultSettingsDialog(vault: VaultModel, onClose: () -> Unit) {
	var mode by remember { mutableStateOf("home") }
	var current by remember { mutableStateOf("") }
	var next by remember { mutableStateOf("") }
	var confirm by remember { mutableStateOf("") }
	var wipeText by remember { mutableStateOf("") }
	var error by remember { mutableStateOf<String?>(null) }
	var busy by remember { mutableStateOf(false) }

	AlertDialog(
			onDismissRequest = { if (!busy) onClose() },
			title = {
				Text(when (mode) {
					"password" -> "Change vault password"
					"wipe" -> "Wipe vault"
					else -> "Vault settings"
				})
			},
			text = {
				when (mode) {
					"password" -> Column(verticalArrangement =
							Arrangement.spacedBy(10.dp)) {
						OutlinedTextField(current, { current = it; error = null },
								label = { Text("Current password") },
								singleLine = true,
								visualTransformation =
										PasswordVisualTransformation(),
								modifier = Modifier.fillMaxWidth())
						OutlinedTextField(next, { next = it; error = null },
								label = { Text("New password") },
								singleLine = true,
								visualTransformation =
										PasswordVisualTransformation(),
								modifier = Modifier.fillMaxWidth())
						OutlinedTextField(confirm, { confirm = it; error = null },
								label = { Text("Confirm new password") },
								singleLine = true,
								visualTransformation =
										PasswordVisualTransformation(),
								modifier = Modifier.fillMaxWidth())
						if (error != null) Text(error!!,
								color = MaterialTheme.colorScheme.error,
								style = MaterialTheme.typography.labelMedium)
					}
					"wipe" -> Column(verticalArrangement =
							Arrangement.spacedBy(10.dp)) {
						Text("This permanently deletes the vault and everything " +
								"in it, including any wallets. This cannot be " +
								"undone. Type WIPE to confirm.",
								style = MaterialTheme.typography.bodyMedium)
						OutlinedTextField(wipeText, { wipeText = it },
								label = { Text("Type WIPE") }, singleLine = true,
								modifier = Modifier.fillMaxWidth())
					}
					"export" -> Column(verticalArrangement =
							Arrangement.spacedBy(10.dp)) {
						Text("Choose a password for the backup file. You'll need " +
								"it to restore. The backup includes your notes, " +
								"files, gallery and wallets.",
								style = MaterialTheme.typography.bodyMedium)
						OutlinedTextField(next, { next = it; error = null },
								label = { Text("Backup password") },
								singleLine = true,
								visualTransformation =
										PasswordVisualTransformation(),
								modifier = Modifier.fillMaxWidth())
						OutlinedTextField(confirm, { confirm = it; error = null },
								label = { Text("Confirm password") },
								singleLine = true,
								visualTransformation =
										PasswordVisualTransformation(),
								modifier = Modifier.fillMaxWidth())
						if (error != null) Text(error!!,
								color = MaterialTheme.colorScheme.error,
								style = MaterialTheme.typography.labelMedium)
					}
					"import" -> Column(verticalArrangement =
							Arrangement.spacedBy(10.dp)) {
						Text("Enter the backup's password, then pick the backup " +
								"file to restore into this vault.",
								style = MaterialTheme.typography.bodyMedium)
						OutlinedTextField(current, { current = it; error = null },
								label = { Text("Backup password") },
								singleLine = true,
								visualTransformation =
										PasswordVisualTransformation(),
								modifier = Modifier.fillMaxWidth())
						if (error != null) Text(error!!,
								color = MaterialTheme.colorScheme.error,
								style = MaterialTheme.typography.labelMedium)
					}
					else -> Column(verticalArrangement =
							Arrangement.spacedBy(6.dp)) {
						SettingsActionRow("Change password",
								"Re-key the vault with a new password.") {
							mode = "password"
						}
						Divider(color = MaterialTheme.colorScheme.outlineVariant)
						SettingsActionRow("Back up vault",
								"Save an encrypted backup file.") {
							mode = "export"
						}
						Divider(color = MaterialTheme.colorScheme.outlineVariant)
						SettingsActionRow("Restore backup",
								"Import items and wallets from a backup file.") {
							mode = "import"
						}
						Divider(color = MaterialTheme.colorScheme.outlineVariant)
						Text("Auto-lock", fontWeight = FontWeight.Medium)
						Text("The vault locks automatically after 30 minutes of " +
								"inactivity.",
								style = MaterialTheme.typography.labelMedium,
								color = MaterialTheme.colorScheme.onSurfaceVariant)
						Divider(color = MaterialTheme.colorScheme.outlineVariant)
						SettingsActionRow("Wipe vault",
								"Permanently delete the vault and its contents.",
								danger = true) { mode = "wipe" }
					}
				}
			},
			confirmButton = {
				when (mode) {
					"password" -> TextButton(enabled = !busy, onClick = {
						if (next.length < 8) {
							error = "Use at least 8 characters."
							return@TextButton
						}
						if (next != confirm) {
							error = "New passwords don't match."
							return@TextButton
						}
						busy = true
						vault.changePassword(current.toCharArray(),
								next.toCharArray()) { ok ->
							busy = false
							if (ok) onClose()
							else error = "Current password is incorrect."
						}
					}) { Text(if (busy) "Changing…" else "Change") }
					"export" -> TextButton(enabled = !busy, onClick = {
						if (next.length < 8) {
							error = "Use at least 8 characters."
							return@TextButton
						}
						if (next != confirm) {
							error = "Passwords don't match."
							return@TextButton
						}
						val dest = chooseVaultSaveFile("Save vault backup",
								"zerion-vault.zbk")
						if (dest != null) {
							busy = true
							vault.exportBackup(next.toCharArray(), dest) { ok ->
								busy = false
								if (ok) onClose()
								else error = "Couldn't write the backup."
							}
						}
					}) { Text(if (busy) "Saving…" else "Save backup") }
					"import" -> TextButton(enabled = !busy, onClick = {
						if (current.isEmpty()) {
							error = "Enter the backup password."
							return@TextButton
						}
						val src = chooseVaultFile("Choose backup file")
						if (src != null) {
							busy = true
							vault.importBackup(current.toCharArray(), src) { n ->
								busy = false
								if (n >= 0) onClose()
								else error = "Wrong password or invalid backup."
							}
						}
					}) { Text(if (busy) "Restoring…" else "Restore") }
					"wipe" -> TextButton(
							enabled = wipeText == "WIPE",
							onClick = { vault.wipe { onClose() } }) {
						Text("Wipe", color = MaterialTheme.colorScheme.error)
					}
					else -> TextButton(onClick = onClose) { Text("Done") }
				}
			},
			dismissButton = {
				if (mode != "home") TextButton(enabled = !busy,
						onClick = { mode = "home"; error = null }) { Text("Back") }
				else TextButton(onClick = onClose) { Text("Close") }
			})
}

@Composable
private fun SettingsActionRow(title: String, subtitle: String,
		danger: Boolean = false, onClick: () -> Unit) {
	Row(Modifier.fillMaxWidth().clickable(onClick = onClick)
			.padding(vertical = 8.dp),
			verticalAlignment = Alignment.CenterVertically) {
		Column(Modifier.weight(1f)) {
			Text(title, fontWeight = FontWeight.Medium,
					color = if (danger) MaterialTheme.colorScheme.error
							else MaterialTheme.colorScheme.onSurface)
			Text(subtitle, style = MaterialTheme.typography.labelMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant)
		}
	}
}

@Composable
private fun DocumentsPane(vault: VaultModel) {
	val entries = vault.documents
	if (entries.isEmpty()) {
		VaultEmptyState(Icons.Filled.InsertDriveFile, "No files in the vault yet")
		return
	}
	LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
		items(entries, key = { it.id }) { e ->
			Row(Modifier.fillMaxWidth().padding(vertical = 10.dp),
					verticalAlignment = Alignment.CenterVertically) {
				Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
						.background(MaterialTheme.colorScheme
								.surfaceContainerHighest),
						contentAlignment = Alignment.Center) {
					Icon(Icons.Filled.InsertDriveFile, contentDescription = null,
							tint = MaterialTheme.colorScheme.primary,
							modifier = Modifier.size(20.dp))
				}
				Spacer(Modifier.width(14.dp))
				Column(Modifier.weight(1f)) {
					Text(e.title, style = MaterialTheme.typography.bodyLarge,
							fontWeight = FontWeight.Medium, maxLines = 1)
					Text(humanBytes(e.size),
							style = MaterialTheme.typography.labelSmall,
							color = MaterialTheme.colorScheme.onSurfaceVariant)
				}
				TextButton(onClick = {
					vault.onActivity()
					vault.loadItemBytes(e.id) { bytes ->
						if (bytes != null) openBytesWithOs(e.title, bytes)
					}
				}) { Text("Open") }
				TextButton(onClick = {
					vault.onActivity()
					val dest = chooseVaultSaveFile("Export file", e.title)
					if (dest != null) vault.exportItem(e.id, dest) {}
				}) { Text("Export") }
				IconButton(onClick = { vault.deleteItem(e.id) }) {
					Icon(Icons.Filled.Delete, contentDescription = "Delete",
							tint = MaterialTheme.colorScheme.error)
				}
			}
		}
	}
}

@Composable
private fun GalleryPane(vault: VaultModel, onView: (String) -> Unit) {
	val entries = vault.media
	if (entries.isEmpty()) {
		VaultEmptyState(Icons.Filled.Image, "No photos or videos yet")
		return
	}
	LazyVerticalGrid(columns = GridCells.Adaptive(120.dp),
			modifier = Modifier.fillMaxSize().padding(12.dp),
			horizontalArrangement = Arrangement.spacedBy(8.dp),
			verticalArrangement = Arrangement.spacedBy(8.dp)) {
		items(entries, key = { it.id }) { e ->
			val isVideo = vault.isVideoName(e.title)
			Box(Modifier.size(120.dp).clip(RoundedCornerShape(10.dp))
					.background(MaterialTheme.colorScheme.surfaceContainerHighest)
					.clickable { vault.onActivity(); onView(e.id) },
					contentAlignment = Alignment.Center) {
				if (isVideo) {
					Icon(Icons.Filled.PlayCircle, contentDescription = e.title,
							tint = MaterialTheme.colorScheme.primary,
							modifier = Modifier.size(36.dp))
				} else {
					var bmp by remember(e.id) {
						mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(
								null)
					}
					DisposableEffect(e.id) {
						vault.loadItemBytes(e.id) { bytes ->
							if (bytes != null) bmp = decodeImage(bytes)
						}
						onDispose { }
					}
					val b = bmp
					if (b != null) {
						Image(bitmap = b, contentDescription = e.title,
								modifier = Modifier.fillMaxSize())
					} else {
						Icon(Icons.Filled.Image, contentDescription = e.title,
								tint = MaterialTheme.colorScheme.onSurfaceVariant,
								modifier = Modifier.size(28.dp))
					}
				}
			}
		}
	}
}

@Composable
private fun MediaViewDialog(vault: VaultModel, id: String, title: String,
		onDelete: () -> Unit, onClose: () -> Unit) {
	val isVideo = vault.isVideoName(title)
	var bmp by remember(id) {
		mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
	}
	DisposableEffect(id) {
		if (!isVideo) vault.loadItemBytes(id) { bytes ->
			if (bytes != null) bmp = decodeImage(bytes)
		}
		onDispose { }
	}
	AlertDialog(
			onDismissRequest = onClose,
			title = { Text(title, maxLines = 1) },
			text = {
				if (isVideo) {
					Text("Open the video with your system player, or export " +
							"it to a file.")
				} else {
					val b = bmp
					if (b != null) Image(bitmap = b, contentDescription = title,
							modifier = Modifier.fillMaxWidth())
					else Text("Decrypting…")
				}
			},
			confirmButton = {
				Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
					if (isVideo) {
						TextButton(onClick = {
							vault.loadItemBytes(id) { bytes ->
								if (bytes != null) openBytesWithOs(title, bytes)
							}
						}) { Text("Open") }
					}
					TextButton(onClick = {
						val dest = chooseVaultSaveFile("Export", title)
						if (dest != null) vault.exportItem(id, dest) {}
					}) { Text("Export") }
					TextButton(onClick = { onDelete(); onClose() }) {
						Text("Delete",
								color = MaterialTheme.colorScheme.error)
					}
				}
			},
			dismissButton = {
				TextButton(onClick = onClose) { Text("Close") }
			})
}

@Composable
private fun VaultEmptyState(icon: ImageVector, message: String) {
	Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center,
			horizontalAlignment = Alignment.CenterHorizontally) {
		Icon(icon, contentDescription = null, modifier = Modifier.size(48.dp),
				tint = MaterialTheme.colorScheme.onSurfaceVariant)
		Spacer(Modifier.size(12.dp))
		Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
	}
}

private fun decodeImage(bytes: ByteArray):
		androidx.compose.ui.graphics.ImageBitmap? = try {
	org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap()
} catch (e: Exception) {
	null
}

private fun humanBytes(bytes: Long): String {
	if (bytes < 1024) return "$bytes B"
	val kb = bytes / 1024.0
	if (kb < 1024) return String.format("%.1f KB", kb)
	val mb = kb / 1024.0
	if (mb < 1024) return String.format("%.1f MB", mb)
	return String.format("%.1f GB", mb / 1024.0)
}

private fun chooseVaultFile(title: String): File? {
	val dialog = java.awt.FileDialog(null as java.awt.Frame?, title,
			java.awt.FileDialog.LOAD)
	dialog.isVisible = true
	val dir = dialog.directory
	val name = dialog.file
	return if (dir != null && name != null) File(dir, name) else null
}

private fun chooseVaultSaveFile(title: String, defaultName: String): File? {
	val dialog = java.awt.FileDialog(null as java.awt.Frame?, title,
			java.awt.FileDialog.SAVE)
	dialog.file = defaultName
	dialog.isVisible = true
	val dir = dialog.directory
	val name = dialog.file
	return if (dir != null && name != null) File(dir, name) else null
}

private fun openBytesWithOs(name: String, bytes: ByteArray) {
	chat.zerion.desktop.ui.OpenCache.open(name.ifBlank { "file" }, bytes)
}
