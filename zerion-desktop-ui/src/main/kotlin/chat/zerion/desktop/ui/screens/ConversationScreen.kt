package chat.zerion.desktop.ui.screens

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.DisposableEffect
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

import chat.zerion.desktop.ui.components.safeImageBitmap
import chat.zerion.desktop.ui.ZerionModel
import chat.zerion.desktop.ui.components.AvatarWithPresence
import chat.zerion.desktop.ui.theme.ConnectedGreen

import org.jetbrains.skia.Image as SkiaImage
import org.zerionproject.core.api.contact.ContactId

import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.text.DateFormat
import java.util.Date

@Composable
fun ConversationScreen(model: ZerionModel, contactId: ContactId) {
	val contact = model.contacts.firstOrNull { it.id == contactId }
	var showRename by remember(contactId) { mutableStateOf(false) }
	var showDelete by remember(contactId) { mutableStateOf(false) }
	var showSetLock by remember(contactId) { mutableStateOf(false) }
	var showRemoveLock by remember(contactId) { mutableStateOf(false) }
	var showTimer by remember(contactId) { mutableStateOf(false) }
	var showClear by remember(contactId) { mutableStateOf(false) }
	var joinLink by remember(contactId) { mutableStateOf<String?>(null) }
	var joinResult by remember(contactId) { mutableStateOf<String?>(null) }
	var replyingTo by remember(contactId) { mutableStateOf<ZerionModel.UiMessage?>(null) }
	var forwardMsg by remember(contactId) { mutableStateOf<ZerionModel.UiMessage?>(null) }
	var viewerImage by remember { mutableStateOf<ByteArray?>(null) }

	val locked = contactId.int in model.lockedChatIds
	if (contact != null && !model.isChatVisible(contactId)) {
		ChatLockGate(
				name = contact.name,
				onUnlock = { pw, cb -> model.unlockChat(contactId, pw, cb) })
		return
	}

	Column(Modifier.fillMaxSize()) {
		ConversationHeader(
				contact = contact,
				locked = locked,
				timerSupported = model.timerSupported,
				timerMs = model.conversationTimer,
				onRename = { showRename = true },
				onDelete = { showDelete = true },
				onSetLock = { showSetLock = true },
				onRemoveLock = { showRemoveLock = true },
				onLockNow = { model.relockChat(contactId) },
				onDisappearing = { showTimer = true },
				onClearChat = { showClear = true },
				callsEnabled = model.callsEnabled,
				onCall = { model.startVoiceCall(contactId) })
		androidx.compose.material3.Divider(
				color = MaterialTheme.colorScheme.outlineVariant)
		val listState = rememberLazyListState()
		LaunchedEffect(model.messages.size) {
			if (model.messages.isNotEmpty()) {
				listState.scrollToItem(model.messages.lastIndex)
			}
		}
		LazyColumn(
				state = listState,
				modifier = Modifier.weight(1f).fillMaxWidth()
						.background(MaterialTheme.colorScheme.background),
				contentPadding = androidx.compose.foundation.layout.PaddingValues(
						horizontal = 20.dp, vertical = 14.dp),
				verticalArrangement = Arrangement.spacedBy(6.dp)) {
			items(model.messages, key = { it.id }) { msg ->
				MessageBubble(msg, model, onJoinChannel = { joinLink = it },
						onReply = { replyingTo = it },
						onForward = { forwardMsg = it },
						onViewImage = { viewerImage = it })
			}
		}
		MessageInput(
				error = model.sendError,
				replyText = replyingTo?.let { quoteSnippet(it.text) },
				onCancelReply = { replyingTo = null },
				onSend = { text, cb ->
					val reply = replyingTo
					if (reply != null) {
						model.sendReply(text, reply.msgId) { ok ->
							if (ok) replyingTo = null
							cb(ok)
						}
					} else {
						model.sendMessage(text) { ok -> cb(ok) }
					}
				},
				onAttachImage = { caption ->
					val file = chooseFile("Send image",
							listOf(".jpg", ".jpeg", ".png", ".gif", ".bmp"))
					if (file != null) model.sendImage(file, caption) {}
					file != null
				},
				onAttachDocument = {
					val file = chooseFile("Send PDF document", listOf(".pdf"))
					if (file != null) model.sendDocument(file) {}
				},
				onAttachVideo = {
					val file = chooseFile("Send MP4 video", listOf(".mp4"))
					if (file != null) model.sendVideo(file) {}
				},
				onSendVoice = { pcm, durationMs, cb ->
					model.sendVoiceMemo(pcm, durationMs, cb)
				})
	}

	if (showRename && contact != null) {
		RenameDialog(
				current = contact.name,
				onRename = { model.renameContact(contactId, it) },
				onClose = { showRename = false })
	}
	if (showSetLock) {
		SetLockDialog(
				onSet = { pw, cb ->
					model.setChatLock(contactId, pw) { cb() } },
				onClose = { showSetLock = false })
	}
	if (showRemoveLock) {
		VerifyLockDialog(
				title = "Remove chat lock",
				confirmLabel = "Remove lock",
				onVerify = { pw, cb ->
					model.removeChatLock(contactId, pw, cb) },
				onClose = { showRemoveLock = false })
	}
	if (showTimer) {
		DisappearingDialog(
				current = model.conversationTimer,
				onSelect = { model.setConversationTimer(contactId, it) },
				onClose = { showTimer = false })
	}
	if (showClear) {
		AlertDialog(
				onDismissRequest = { showClear = false },
				title = { Text("Clear chat?") },
				text = {
					Text("This permanently deletes every message in this " +
							"conversation from this device. This cannot be undone.")
				},
				confirmButton = {
					TextButton(onClick = {
						model.clearChat(contactId) {}
						showClear = false
					}) { Text("Clear chat") }
				},
				dismissButton = {
					TextButton(onClick = { showClear = false }) {
						Text("Cancel")
					}
				})
	}
	joinLink?.let { link ->
		AlertDialog(
				onDismissRequest = { joinLink = null },
				title = { Text("Join channel?") },
				text = { Text("Join the channel from this invite link? You will " +
						"start receiving its posts.") },
				confirmButton = {
					TextButton(onClick = {
						model.joinChannelFromLink(link) { err ->
							joinResult = err ?: "Joined the channel."
						}
						joinLink = null
					}) { Text("Join") }
				},
				dismissButton = {
					TextButton(onClick = { joinLink = null }) { Text("Cancel") }
				})
	}
	joinResult?.let { msg ->
		AlertDialog(
				onDismissRequest = { joinResult = null },
				title = { Text("Channel") },
				text = { Text(msg) },
				confirmButton = {
					TextButton(onClick = { joinResult = null }) { Text("OK") }
				})
	}
	forwardMsg?.let { fm ->
		AlertDialog(
				onDismissRequest = { forwardMsg = null },
				title = { Text("Forward to…") },
				text = {
					Column {
						val others = model.contacts.filter { it.id != contactId }
						if (others.isEmpty()) Text("No other contacts.")
						others.forEach { c ->
							TextButton(onClick = {
								model.forwardMessage(c.id, fm.text) {}
								forwardMsg = null
							}, modifier = Modifier.fillMaxWidth()) {
								Text(c.name, modifier = Modifier.fillMaxWidth())
							}
						}
					}
				},
				confirmButton = {},
				dismissButton = {
					TextButton(onClick = { forwardMsg = null }) { Text("Cancel") }
				})
	}
	viewerImage?.let { bytes ->
		val bmp = remember(bytes) { safeImageBitmap(bytes) }
		AlertDialog(
				onDismissRequest = { viewerImage = null },
				title = { Text("Image") },
				text = {
					if (bmp != null)
						Image(bitmap = bmp, contentDescription = "Image",
								modifier = Modifier.widthIn(max = 640.dp)
										.heightIn(max = 640.dp))
					else Text("This image couldn't be displayed.")
				},
				confirmButton = {
					TextButton(onClick = {
						val f = chooseSaveFile("Save image", "zerion-image.jpg")
						if (f != null) f.writeBytes(bytes)
						viewerImage = null
					}) { Text("Save") }
				},
				dismissButton = {
					TextButton(onClick = { viewerImage = null }) { Text("Close") }
				})
	}
	if (showDelete && contact != null) {
		AlertDialog(
				onDismissRequest = { showDelete = false },
				title = { Text("Delete contact?") },
				text = {
					Text("This removes ${contact.name} and your entire " +
							"conversation. This cannot be undone.")
				},
				confirmButton = {
					TextButton(onClick = {
						model.removeContact(contactId)
						showDelete = false
					}) { Text("Delete") }
				},
				dismissButton = {
					TextButton(onClick = { showDelete = false }) {
						Text("Cancel")
					}
				})
	}
}

@Composable
private fun ChatLockGate(
		name: String,
		onUnlock: (CharArray, (Boolean) -> Unit) -> Unit,
) {
	var password by remember { mutableStateOf("") }
	var error by remember { mutableStateOf(false) }
	var busy by remember { mutableStateOf(false) }
	fun submit() {
		if (password.isNotEmpty() && !busy) {
			busy = true; error = false
			onUnlock(password.toCharArray()) { ok ->
				busy = false
				if (ok) password = "" else error = true
			}
		}
	}
	Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
			contentAlignment = Alignment.Center) {
		Column(Modifier.widthIn(max = 340.dp).padding(24.dp),
				horizontalAlignment = Alignment.CenterHorizontally,
				verticalArrangement = Arrangement.spacedBy(14.dp)) {
			Icon(Icons.Filled.Lock, contentDescription = null,
					modifier = Modifier.size(40.dp),
					tint = MaterialTheme.colorScheme.primary)
			Text("$name is locked",
					style = MaterialTheme.typography.titleMedium)
			Text("Enter this chat's password to open it.",
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant)
			OutlinedTextField(
					value = password,
					onValueChange = { password = it; error = false },
					label = { Text("Chat password") },
					singleLine = true,
					isError = error,
					visualTransformation = PasswordVisualTransformation(),
					modifier = Modifier.fillMaxWidth())
			if (error) {
				Text("Incorrect password.",
						color = MaterialTheme.colorScheme.error,
						style = MaterialTheme.typography.labelMedium)
			}
			Button(onClick = { submit() },
					enabled = password.isNotEmpty() && !busy,
					modifier = Modifier.fillMaxWidth()) {
				Text("Unlock chat")
			}
		}
	}
}

@Composable
private fun SetLockDialog(
		onSet: (CharArray, () -> Unit) -> Unit,
		onClose: () -> Unit,
) {
	var pw by remember { mutableStateOf("") }
	var confirm by remember { mutableStateOf("") }
	var error by remember { mutableStateOf<String?>(null) }
	var busy by remember { mutableStateOf(false) }
	AlertDialog(
			onDismissRequest = { if (!busy) onClose() },
			title = { Text("Lock this chat") },
			text = {
				Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
					Text("Set a separate password to open this chat. It is " +
							"stored only as a salted hash, never in plaintext.",
							style = MaterialTheme.typography.bodyMedium,
							color = MaterialTheme.colorScheme.onSurfaceVariant)
					OutlinedTextField(pw, { pw = it; error = null },
							label = { Text("Chat password") }, singleLine = true,
							visualTransformation = PasswordVisualTransformation(),
							modifier = Modifier.fillMaxWidth())
					OutlinedTextField(confirm, { confirm = it; error = null },
							label = { Text("Confirm") }, singleLine = true,
							visualTransformation = PasswordVisualTransformation(),
							modifier = Modifier.fillMaxWidth())
					if (error != null) {
						Text(error!!, color = MaterialTheme.colorScheme.error,
								style = MaterialTheme.typography.labelMedium)
					}
				}
			},
			confirmButton = {
				TextButton(
						onClick = {
							when {
								pw.length < 4 -> error =
										"Use at least 4 characters."
								pw != confirm -> error = "Passwords don't match."
								else -> {
									busy = true
									onSet(pw.toCharArray()) { onClose() }
								}
							}
						},
						enabled = !busy) { Text("Lock chat") }
			},
			dismissButton = {
				TextButton(onClick = onClose, enabled = !busy) { Text("Cancel") }
			})
}

@Composable
private fun VerifyLockDialog(
		title: String,
		confirmLabel: String,
		onVerify: (CharArray, (Boolean) -> Unit) -> Unit,
		onClose: () -> Unit,
) {
	var pw by remember { mutableStateOf("") }
	var error by remember { mutableStateOf(false) }
	var busy by remember { mutableStateOf(false) }
	AlertDialog(
			onDismissRequest = { if (!busy) onClose() },
			title = { Text(title) },
			text = {
				Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
					OutlinedTextField(pw, { pw = it; error = false },
							label = { Text("Chat password") }, singleLine = true,
							isError = error,
							visualTransformation = PasswordVisualTransformation(),
							modifier = Modifier.fillMaxWidth())
					if (error) {
						Text("Incorrect password.",
								color = MaterialTheme.colorScheme.error,
								style = MaterialTheme.typography.labelMedium)
					}
				}
			},
			confirmButton = {
				TextButton(
						onClick = {
							busy = true; error = false
							onVerify(pw.toCharArray()) { ok ->
								busy = false
								if (ok) onClose() else error = true
							}
						},
						enabled = !busy && pw.isNotEmpty()) { Text(confirmLabel) }
			},
			dismissButton = {
				TextButton(onClick = onClose, enabled = !busy) { Text("Cancel") }
			})
}

@Composable
private fun RenameDialog(
		current: String,
		onRename: (String) -> Unit,
		onClose: () -> Unit,
) {
	var value by remember { mutableStateOf(current) }
	AlertDialog(
			onDismissRequest = onClose,
			title = { Text("Rename contact") },
			text = {
				OutlinedTextField(
						value = value,
						onValueChange = { value = it },
						singleLine = true,
						label = { Text("Name") })
			},
			confirmButton = {
				TextButton(onClick = { onRename(value); onClose() }) {
					Text("Save")
				}
			},
			dismissButton = {
				TextButton(onClick = onClose) { Text("Cancel") }
			})
}

@Composable
private fun ConversationHeader(
		contact: ZerionModel.ContactItem?,
		locked: Boolean,
		timerSupported: Boolean,
		timerMs: Long,
		onRename: () -> Unit,
		onDelete: () -> Unit,
		onSetLock: () -> Unit,
		onRemoveLock: () -> Unit,
		onLockNow: () -> Unit,
		onDisappearing: () -> Unit,
		onClearChat: () -> Unit,
		callsEnabled: Boolean,
		onCall: () -> Unit,
) {
	Row(
			Modifier.fillMaxWidth()
					.background(MaterialTheme.colorScheme.surface)
					.padding(horizontal = 20.dp, vertical = 12.dp),
			verticalAlignment = Alignment.CenterVertically) {
		if (contact != null) {
			AvatarWithPresence(contact.name, contact.colorKey,
					contact.connected, size = 40.dp, photo = contact.avatar)
			Spacer(Modifier.size(12.dp))
			Column(Modifier.weight(1f)) {
				Text(contact.name, style = MaterialTheme.typography.titleMedium)
				val presence = if (contact.connected) "Online" else "Offline"
				val sub = if (timerMs > 0) {
					"$presence · Disappears after ${formatDuration(timerMs)}"
				} else presence
				Text(sub,
						style = MaterialTheme.typography.labelMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant)
			}
			if (contact.postQuantum) {
				Row(verticalAlignment = Alignment.CenterVertically) {
					Icon(Icons.Filled.Lock, contentDescription = null,
							modifier = Modifier.size(14.dp),
							tint = ConnectedGreen)
					Spacer(Modifier.size(4.dp))
					Text("Post-quantum",
							style = MaterialTheme.typography.labelMedium,
							color = MaterialTheme.colorScheme.onSurfaceVariant)
				}
			}
			if (callsEnabled) {
				IconButton(onClick = onCall) {
					Icon(Icons.Filled.Call, contentDescription = "Voice call",
							tint = MaterialTheme.colorScheme.primary)
				}
			}
			var menuOpen by remember { mutableStateOf(false) }
			Box {
				IconButton(onClick = { menuOpen = true }) {
					Icon(Icons.Filled.MoreVert,
							contentDescription = "Contact options")
				}
				DropdownMenu(
						expanded = menuOpen,
						onDismissRequest = { menuOpen = false }) {
					DropdownMenuItem(
							text = { Text("Rename") },
							onClick = { menuOpen = false; onRename() })
					if (timerSupported) {
						DropdownMenuItem(
								text = { Text("Disappearing messages…") },
								onClick = { menuOpen = false; onDisappearing() })
					}
					if (locked) {
						DropdownMenuItem(
								text = { Text("Lock now") },
								onClick = { menuOpen = false; onLockNow() })
						DropdownMenuItem(
								text = { Text("Remove chat lock") },
								onClick = { menuOpen = false; onRemoveLock() })
					} else {
						DropdownMenuItem(
								text = { Text("Lock chat…") },
								onClick = { menuOpen = false; onSetLock() })
					}
					DropdownMenuItem(
							text = { Text("Clear chat") },
							onClick = { menuOpen = false; onClearChat() })
					DropdownMenuItem(
							text = { Text("Delete contact") },
							onClick = { menuOpen = false; onDelete() })
				}
			}
		}
	}
}

@Composable
private fun MessageBubble(msg: ZerionModel.UiMessage, model: ZerionModel,
		onJoinChannel: (String) -> Unit, onReply: (ZerionModel.UiMessage) -> Unit,
		onForward: (ZerionModel.UiMessage) -> Unit,
		onViewImage: (ByteArray) -> Unit) {
	val clipboard = LocalClipboardManager.current
	val quote = remember(msg.text) { splitQuote(msg.text) }
	val bodyText = quote.second
	val inviteLink = remember(bodyText) { extractInviteLink(bodyText) }
	val align = if (msg.outgoing) Alignment.CenterEnd else Alignment.CenterStart
	val bubble = if (msg.outgoing) chat.zerion.desktop.ui.theme.SentBubble
	else MaterialTheme.colorScheme.surfaceContainerHigh
	val onBubble = if (msg.outgoing) Color.White
	else MaterialTheme.colorScheme.onSurface
	val shape = RoundedCornerShape(
			topStart = 14.dp, topEnd = 14.dp,
			bottomStart = if (msg.outgoing) 14.dp else 4.dp,
			bottomEnd = if (msg.outgoing) 4.dp else 14.dp)
	var dragX by remember(msg.id) { mutableStateOf(0f) }
	ContextMenuArea(items = {
		buildList {
			add(ContextMenuItem("Reply") { onReply(msg) })
			if (bodyText.isNotEmpty()) add(ContextMenuItem("Copy") {
				clipboard.setText(AnnotatedString(bodyText))
			})
			if (bodyText.isNotEmpty())
				add(ContextMenuItem("Forward") { onForward(msg) })
			add(ContextMenuItem("React 👍") {
				model.addReaction(msg.msgId, "👍")
			})
			add(ContextMenuItem("React ❤️") {
				model.addReaction(msg.msgId, "❤️")
			})
			add(ContextMenuItem("React 😂") {
				model.addReaction(msg.msgId, "😂")
			})
			add(ContextMenuItem("Delete") { model.deleteMessage(msg.msgId) })
			if (inviteLink != null) {
				add(ContextMenuItem("Copy link") {
					clipboard.setText(AnnotatedString(inviteLink))
				})
				add(ContextMenuItem("Join channel") { onJoinChannel(inviteLink) })
			}
		}
	}) {
		Box(Modifier.fillMaxWidth(), contentAlignment = align) {
			if (dragX != 0f) {
				Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null,
						modifier = Modifier.align(Alignment.CenterStart)
								.padding(start = 8.dp).size(20.dp),
						tint = MaterialTheme.colorScheme.primary)
			}
			Column(
					Modifier.offset { IntOffset(dragX.roundToInt(), 0) }
							.pointerInput(msg.id) {
								val threshold = 56.dp.toPx()
								detectHorizontalDragGestures(
										onDragEnd = {
											if (abs(dragX) > threshold) onReply(msg)
											dragX = 0f
										}) { change, amount ->
									change.consume()
									dragX = (dragX + amount).coerceIn(-140f, 140f)
								}
							}
							.widthIn(max = 460.dp).clip(shape).background(bubble)
							.padding(horizontal = 12.dp, vertical = 8.dp)) {
				if (msg.replyPreview != null)
					QuoteBlock(quoteSnippet(msg.replyPreview), onBubble)
				else if (quote.first != null)
					QuoteBlock(quote.first!!, onBubble)
				if (msg.voice != null) VoiceMemoRow(msg.voice, onBubble)
				msg.attachments.forEach { att ->
					if (att.isImage && att.bytes != null) {
						Box(Modifier.clickable { onViewImage(att.bytes) }) {
							AttachmentImage(att.bytes)
						}
					} else {
						AttachmentFileRow(att, onBubble,
								onOpen = { model.openAttachment(att) {} },
								onSave = {
									val f = chooseSaveFile("Save attachment",
											att.fileName)
									if (f != null) model.saveAttachment(att, f) {}
								})
					}
				}
				if (bodyText.isNotEmpty()) {
					Text(bodyText, color = onBubble,
							style = MaterialTheme.typography.bodyLarge)
				}
				if (inviteLink != null && !msg.outgoing) {
					Spacer(Modifier.size(4.dp))
					Button(onClick = { onJoinChannel(inviteLink) },
							modifier = Modifier.fillMaxWidth()) {
						Text("Join channel")
					}
				}
				if (msg.reactions.isNotEmpty()) {
					Row(Modifier.padding(top = 4.dp),
							horizontalArrangement = Arrangement.spacedBy(4.dp)) {
						msg.reactions.forEach { (emoji, count) ->
							Box(Modifier.clip(RoundedCornerShape(10.dp))
									.background(onBubble.copy(alpha = 0.12f))
									.padding(horizontal = 6.dp, vertical = 2.dp)) {
								Text("$emoji $count",
										style = MaterialTheme.typography.labelSmall,
										color = onBubble)
							}
						}
					}
				}
				Row(Modifier.align(Alignment.End).padding(top = 2.dp),
						verticalAlignment = Alignment.CenterVertically) {
					Text(formatTime(msg.timestamp),
							style = MaterialTheme.typography.labelMedium,
							color = onBubble.copy(alpha = 0.7f))
					if (msg.outgoing) {
						Spacer(Modifier.size(4.dp))
						Icon(
								if (msg.seen) Icons.Filled.DoneAll
								else Icons.Filled.Done,
								contentDescription = if (msg.seen) "Seen"
								else "Sent",
								modifier = Modifier.size(14.dp),
								tint = onBubble.copy(alpha = 0.8f))
					}
				}
			}
		}
	}
}

@Composable
private fun QuoteBlock(quote: String, onBubble: Color) {
	Row(Modifier.padding(bottom = 4.dp).heightIn(min = 0.dp)) {
		Box(Modifier.width(3.dp).heightIn(min = 16.dp)
				.background(onBubble.copy(alpha = 0.5f),
						RoundedCornerShape(2.dp)))
		Spacer(Modifier.size(8.dp))
		Text(quote, color = onBubble.copy(alpha = 0.75f),
				style = MaterialTheme.typography.labelMedium,
				maxLines = 3)
	}
}

private fun quoteSnippet(text: String): String {
	val body = splitQuote(text).second.replace("\n", " ").trim()
	return if (body.length > 140) body.take(139) + "…" else body
}

private fun splitQuote(text: String): Pair<String?, String> {
	if (!text.startsWith("> ")) return null to text
	val lines = text.split("\n")
	val q = StringBuilder()
	var i = 0
	while (i < lines.size && lines[i].startsWith("> ")) {
		if (q.isNotEmpty()) q.append("\n")
		q.append(lines[i].removePrefix("> "))
		i++
	}
	val body = lines.drop(i).joinToString("\n").trimStart('\n')
	return q.toString() to body
}

private fun extractInviteLink(text: String): String? {
	val i = text.indexOf("zerion://")
	if (i < 0) return null
	var end = i + "zerion://".length
	while (end < text.length && !text[end].isWhitespace()) end++
	val link = text.substring(i, end)
	return if (link.length > "zerion://".length + 8) link else null
}

@Composable
private fun AttachmentImage(bytes: ByteArray) {
	val bitmap = remember(bytes) { safeImageBitmap(bytes) } ?: return
	Image(
			bitmap = bitmap,
			contentDescription = "Image attachment",
			modifier = Modifier
					.widthIn(max = 300.dp)
					.heightIn(max = 340.dp)
					.clip(RoundedCornerShape(10.dp))
					.padding(bottom = 4.dp))
}

private fun chooseFile(title: String, extensions: List<String>): File? {
	val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
	dialog.setFilenameFilter { _, name ->
		val n = name.lowercase()
		extensions.any { n.endsWith(it) }
	}
	dialog.isVisible = true
	val dir = dialog.directory
	val name = dialog.file
	return if (dir != null && name != null) File(dir, name) else null
}

private fun chooseSaveFile(title: String, suggestedName: String): File? {
	val dialog = FileDialog(null as Frame?, title, FileDialog.SAVE)
	dialog.file = suggestedName
	dialog.isVisible = true
	val dir = dialog.directory
	val name = dialog.file
	return if (dir != null && name != null) File(dir, name) else null
}

@Composable
private fun AttachmentFileRow(att: ZerionModel.UiAttachment, onBubble: Color,
		onOpen: () -> Unit, onSave: () -> Unit) {
	val label = when {
		att.contentType.startsWith("video/") -> "🎬 Video"
		att.contentType == "application/pdf" -> "📄 Document"
		att.contentType.startsWith("audio/") -> "🎤 Audio"
		else -> "📎 File"
	}
	Row(Modifier.padding(bottom = 4.dp),
			verticalAlignment = Alignment.CenterVertically) {
		Column(Modifier.weight(1f, false)) {
			Text("$label  ${att.fileName}", color = onBubble,
					style = MaterialTheme.typography.bodyMedium,
					maxLines = 1, overflow = TextOverflow.Ellipsis)
			if (att.size >= 0)
				Text(humanSize(att.size), color = onBubble.copy(alpha = 0.7f),
						style = MaterialTheme.typography.labelSmall)
		}
		Spacer(Modifier.size(8.dp))
		TextButton(onClick = onOpen) { Text("Open") }
		TextButton(onClick = onSave) { Text("Save") }
	}
}

@Composable
private fun VoiceMemoRow(voice: ZerionModel.UiVoice, onBubble: Color) {
	val player = remember { chat.zerion.desktop.ui.voice.VoicePlayer() }
	var playing by remember { mutableStateOf(false) }
	DisposableEffect(voice) { onDispose { player.stop() } }
	Row(Modifier.padding(vertical = 2.dp),
			verticalAlignment = Alignment.CenterVertically) {
		if (voice.incomplete) {
			Text("🎤 Voice message (receiving…)", color = onBubble,
					style = MaterialTheme.typography.bodyMedium)
		} else if (voice.muLaw == null) {
			Text("🎤 Voice message (unavailable)", color = onBubble,
					style = MaterialTheme.typography.bodyMedium)
		} else {
			IconButton(onClick = {
				if (playing) {
					player.stop(); playing = false
				} else {
					playing = player.play(voice.muLaw) { playing = false }
				}
			}) {
				Icon(if (playing) Icons.Filled.Stop
						else Icons.Filled.PlayArrow,
						contentDescription = if (playing) "Stop" else "Play",
						tint = onBubble)
			}
			Text("🎤 " + formatDuration(voice.durationMs), color = onBubble,
					style = MaterialTheme.typography.bodyMedium)
		}
	}
}

private fun formatDuration(ms: Int): String {
	val s = ms / 1000
	return String.format("%d:%02d", s / 60, s % 60)
}

private fun humanSize(bytes: Long): String {
	if (bytes < 1024) return "$bytes B"
	val kb = bytes / 1024.0
	if (kb < 1024) return String.format("%.1f KB", kb)
	val mb = kb / 1024.0
	if (mb < 1024) return String.format("%.1f MB", mb)
	return String.format("%.1f GB", mb / 1024.0)
}

private val TIMER_OPTIONS = listOf(
		"Off" to -1L,
		"1 day" to 24L * 60 * 60 * 1000,
		"1 week" to 7L * 24 * 60 * 60 * 1000,
		"4 weeks" to 28L * 24 * 60 * 60 * 1000)

@Composable
private fun DisappearingDialog(
		current: Long,
		onSelect: (Long) -> Unit,
		onClose: () -> Unit,
) {
	AlertDialog(
			onDismissRequest = onClose,
			title = { Text("Disappearing messages") },
			text = {
				Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
					Text("New messages in this chat disappear for both of you " +
							"after the chosen time.",
							style = MaterialTheme.typography.bodyMedium,
							color = MaterialTheme.colorScheme.onSurfaceVariant)
					Spacer(Modifier.size(8.dp))
					TIMER_OPTIONS.forEach { (label, value) ->
						Row(Modifier.fillMaxWidth()
								.clip(RoundedCornerShape(8.dp))
								.clickable { onSelect(value); onClose() }
								.padding(vertical = 10.dp, horizontal = 8.dp),
								verticalAlignment = Alignment.CenterVertically) {
							Text(label, Modifier.weight(1f),
									style = MaterialTheme.typography.bodyLarge)
							if (current == value) {
								Icon(Icons.Filled.Done,
										contentDescription = "Selected",
										tint = MaterialTheme.colorScheme.primary)
							}
						}
					}
				}
			},
			confirmButton = {
				TextButton(onClick = onClose) { Text("Done") }
			})
}

private fun formatDuration(ms: Long): String {
	val days = ms / (24L * 60 * 60 * 1000)
	return when {
		days >= 28 && days % 7 == 0L -> "${days / 7} weeks"
		days >= 7 && days % 7 == 0L -> "${days / 7} week"
		days >= 1 -> if (days == 1L) "1 day" else "$days days"
		else -> "${ms / (60L * 60 * 1000)} hours"
	}
}

@Composable
private fun MessageInput(
		error: String?,
		replyText: String?,
		onCancelReply: () -> Unit,
		onSend: (String, (Boolean) -> Unit) -> Unit,
		onAttachImage: (String) -> Boolean,
		onAttachDocument: () -> Unit,
		onAttachVideo: () -> Unit,
		onSendVoice: (ByteArray, Int, (Boolean) -> Unit) -> Unit,
) {
	var text by remember { mutableStateOf("") }
	var sending by remember { mutableStateOf(false) }
	var attachOpen by remember { mutableStateOf(false) }
	val recorder = remember { chat.zerion.desktop.ui.voice.VoiceRecorder() }
	var recording by remember { mutableStateOf(false) }
	var voiceError by remember { mutableStateOf<String?>(null) }
	DisposableEffect(Unit) { onDispose { recorder.cancel() } }
	fun submit() {
		if (text.isNotBlank() && !sending) {
			sending = true
			onSend(text) { ok ->
				sending = false
				if (ok) text = ""
			}
		}
	}
	Column(Modifier.fillMaxWidth()
			.background(MaterialTheme.colorScheme.surface)) {
		if (replyText != null) {
			Row(Modifier.fillMaxWidth()
					.padding(start = 16.dp, end = 8.dp, top = 8.dp),
					verticalAlignment = Alignment.CenterVertically) {
				Box(Modifier.width(3.dp).heightIn(min = 28.dp)
						.background(MaterialTheme.colorScheme.primary,
								RoundedCornerShape(2.dp)))
				Spacer(Modifier.size(8.dp))
				Column(Modifier.weight(1f)) {
					Text("Replying to",
							style = MaterialTheme.typography.labelSmall,
							color = MaterialTheme.colorScheme.primary)
					Text(replyText, maxLines = 1,
							style = MaterialTheme.typography.labelMedium,
							color = MaterialTheme.colorScheme.onSurfaceVariant)
				}
				IconButton(onClick = onCancelReply) {
					Icon(Icons.Filled.Close, contentDescription = "Cancel reply",
							modifier = Modifier.size(18.dp))
				}
			}
		}
		if (error != null || voiceError != null) {
			Text(error ?: voiceError!!,
					color = MaterialTheme.colorScheme.error,
					style = MaterialTheme.typography.labelMedium,
					modifier = Modifier.padding(
							start = 16.dp, end = 16.dp, top = 8.dp))
		}
		Row(
				Modifier.fillMaxWidth().padding(12.dp),
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(8.dp)) {
			Box {
				IconButton(onClick = { attachOpen = true }) {
					Icon(Icons.Filled.AttachFile,
							contentDescription = "Attach a file")
				}
				DropdownMenu(
						expanded = attachOpen,
						onDismissRequest = { attachOpen = false }) {
					DropdownMenuItem(
							text = { Text("Image") },
							onClick = {
								attachOpen = false
								if (onAttachImage(text)) text = ""
							})
					DropdownMenuItem(
							text = { Text("Document (PDF)") },
							onClick = { attachOpen = false; onAttachDocument() })
					DropdownMenuItem(
							text = { Text("Video (MP4)") },
							onClick = { attachOpen = false; onAttachVideo() })
				}
			}
			IconButton(onClick = {
				voiceError = null
				if (recording) {
					recording = false
					val res = recorder.stop(System.currentTimeMillis())
					if (res != null && res.first.isNotEmpty())
						onSendVoice(res.first, res.second) { }
				} else {
					recording = recorder.start()
					if (!recording) voiceError = "No microphone available."
				}
			}) {
				Icon(if (recording) Icons.Filled.Stop else Icons.Filled.Mic,
						contentDescription =
								if (recording) "Stop recording"
								else "Record voice message",
						tint = if (recording) MaterialTheme.colorScheme.error
								else LocalContentColor.current)
			}
			OutlinedTextField(
					value = text,
					onValueChange = { text = it },
					placeholder = { Text(
							if (recording) "Recording… tap stop to send"
							else "Write a message…") },
					modifier = Modifier.weight(1f).heightIn(max = 120.dp)
							.onPreviewKeyEvent { e ->
								if (e.key == Key.Enter &&
										e.type == KeyEventType.KeyDown &&
										!e.isShiftPressed) {
									submit(); true
								} else false
							},
					maxLines = 5,
					shape = RoundedCornerShape(22.dp),
					keyboardOptions =
							KeyboardOptions(imeAction = ImeAction.Send),
					keyboardActions = KeyboardActions(onSend = { submit() }))
			FilledIconButton(onClick = { submit() },
					enabled = text.isNotBlank() && !sending,
					modifier = Modifier.size(48.dp)) {
				Icon(Icons.AutoMirrored.Filled.Send,
						contentDescription = "Send")
			}
		}
	}
}

private val timeFormat = DateFormat.getTimeInstance(DateFormat.SHORT)

private fun formatTime(ts: Long): String = timeFormat.format(Date(ts))
