package chat.zerion.desktop.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.DisposableEffect
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

import chat.zerion.desktop.ui.components.safeImageBitmap
import chat.zerion.desktop.ui.ZerionModel
import chat.zerion.desktop.ui.components.Avatar

import org.jetbrains.skia.Image as SkiaImage

@Composable
fun GroupScreen(model: ZerionModel) {
	val group = model.selectedGroup ?: return
	var showMembers by remember(group.idHex) { mutableStateOf(false) }
	var showInvite by remember(group.idHex) { mutableStateOf(false) }
	var showLeave by remember(group.idHex) { mutableStateOf(false) }
	var showDissolve by remember(group.idHex) { mutableStateOf(false) }

	Column(Modifier.fillMaxSize()) {
		GroupHeader(
				group = group,
				onMembers = { showMembers = true },
				onInvite = { showInvite = true },
				onLeave = { showLeave = true },
				onDissolve = { showDissolve = true })
		Divider(color = MaterialTheme.colorScheme.outlineVariant)

		val listState = rememberLazyListState()
		LaunchedEffect(model.groupPosts.size) {
			if (model.groupPosts.isNotEmpty()) {
				listState.scrollToItem(model.groupPosts.lastIndex)
			}
		}
		LazyColumn(
				state = listState,
				modifier = Modifier.weight(1f).fillMaxWidth()
						.background(MaterialTheme.colorScheme.background),
				contentPadding = PaddingValues(horizontal = 20.dp,
						vertical = 14.dp),
				verticalArrangement = Arrangement.spacedBy(6.dp)) {
			items(model.groupPosts, key = { it.id }) { post ->
				GroupBubble(post, model)
			}
		}
		GroupInput(
				error = model.sendError,
				onSend = { text, cb -> model.sendGroupMessage(text, cb) },
				onAttachImage = {
					val file = chooseGroupImage()
					if (file != null) model.sendGroupImage(file) {}
				},
				onSendVoice = { pcm, cb -> model.sendGroupVoice(pcm, cb) })
	}

	if (showMembers) {
		MemberListDialog(
				members = model.groupMembers,
				canManage = group.isCreator,
				onPromote = { model.promoteMember(group.id, it) },
				onDemote = { model.demoteMember(group.id, it) },
				onRemove = { model.removeGroupMember(group.id, it) },
				onClose = { showMembers = false })
	}
	if (showDissolve) {
		AlertDialog(
				onDismissRequest = { showDissolve = false },
				title = { Text("Dissolve group?") },
				text = {
					Text("This permanently dissolves ${group.name} for all " +
							"members. This cannot be undone.")
				},
				confirmButton = {
					TextButton(onClick = {
						model.dissolveGroup(group.id); showDissolve = false
					}) { Text("Dissolve") }
				},
				dismissButton = {
					TextButton(onClick = { showDissolve = false }) {
						Text("Cancel")
					}
				})
	}
	if (showInvite) {
		InviteDialog(
				contacts = model.contacts,
				onInvite = { model.inviteToGroup(group.id, it) {} },
				onClose = { showInvite = false })
	}
	if (showLeave) {
		AlertDialog(
				onDismissRequest = { showLeave = false },
				title = { Text("Leave group?") },
				text = {
					Text("You'll stop receiving messages from " +
							"${group.name} and leave for everyone to see.")
				},
				confirmButton = {
					TextButton(onClick = {
						model.leaveGroup(group.id); showLeave = false
					}) { Text("Leave") }
				},
				dismissButton = {
					TextButton(onClick = { showLeave = false }) {
						Text("Cancel")
					}
				})
	}
}

@Composable
private fun GroupHeader(
		group: ZerionModel.GroupItem,
		onMembers: () -> Unit,
		onInvite: () -> Unit,
		onLeave: () -> Unit,
		onDissolve: () -> Unit,
) {
	Row(
			Modifier.fillMaxWidth()
					.background(MaterialTheme.colorScheme.surface)
					.padding(horizontal = 20.dp, vertical = 12.dp),
			verticalAlignment = Alignment.CenterVertically) {
		Avatar(group.name, group.idHex.hashCode(), size = 40.dp)
		Spacer(Modifier.size(12.dp))
		Column(Modifier.weight(1f)) {
			Text(group.name, style = MaterialTheme.typography.titleMedium)
			Text("${group.members} " +
					if (group.members == 1) "member" else "members",
					style = MaterialTheme.typography.labelMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant)
		}
		var menuOpen by remember { mutableStateOf(false) }
		Box {
			IconButton(onClick = { menuOpen = true }) {
				Icon(Icons.Filled.MoreVert, contentDescription = "Group options")
			}
			DropdownMenu(
					expanded = menuOpen,
					onDismissRequest = { menuOpen = false }) {
				DropdownMenuItem(text = { Text("Members") },
						onClick = { menuOpen = false; onMembers() })
				if (group.isCreator) {
					DropdownMenuItem(text = { Text("Invite member…") },
							onClick = { menuOpen = false; onInvite() })
					DropdownMenuItem(text = { Text("Dissolve group") },
							onClick = { menuOpen = false; onDissolve() })
				} else {
					DropdownMenuItem(text = { Text("Leave group") },
							onClick = { menuOpen = false; onLeave() })
				}
			}
		}
	}
}

@Composable
private fun GroupBubble(post: ZerionModel.GroupPost, model: ZerionModel) {
	val align = if (post.outgoing) Alignment.CenterEnd else Alignment.CenterStart
	val bubble = if (post.outgoing) chat.zerion.desktop.ui.theme.SentBubble
	else MaterialTheme.colorScheme.surfaceContainerHigh
	val onBubble = if (post.outgoing) androidx.compose.ui.graphics.Color.White
	else MaterialTheme.colorScheme.onSurface
	val shape = RoundedCornerShape(
			topStart = 14.dp, topEnd = 14.dp,
			bottomStart = if (post.outgoing) 14.dp else 4.dp,
			bottomEnd = if (post.outgoing) 4.dp else 14.dp)
	Box(Modifier.fillMaxWidth(), contentAlignment = align) {
		Column(Modifier.widthIn(max = 460.dp).clip(shape).background(bubble)
				.padding(horizontal = 12.dp, vertical = 8.dp)) {
			if (!post.outgoing) {
				Text(post.sender, color = MaterialTheme.colorScheme.primary,
						style = MaterialTheme.typography.labelMedium,
						fontWeight = FontWeight.SemiBold)
			}
			post.image?.let { bytes ->
				val bitmap = remember(bytes) { safeImageBitmap(bytes) }
				if (bitmap != null)
					Image(bitmap = bitmap, contentDescription = "Image",
							modifier = Modifier.widthIn(max = 300.dp)
									.heightIn(max = 340.dp)
									.clip(RoundedCornerShape(10.dp))
									.padding(bottom = 2.dp))
			}
			if (post.voiceOgg != null) {
				GroupVoiceRow(post, model, onBubble)
			}
			if (post.text.isNotEmpty()) {
				Text(post.text, color = onBubble,
						style = MaterialTheme.typography.bodyLarge)
			}
		}
	}
}

@Composable
private fun GroupVoiceRow(post: ZerionModel.GroupPost, model: ZerionModel,
		onBubble: androidx.compose.ui.graphics.Color) {
	val player = remember { chat.zerion.desktop.ui.voice.VoicePlayer() }
	var playing by remember { mutableStateOf(false) }
	var loading by remember { mutableStateOf(false) }
	DisposableEffect(post.id) { onDispose { player.stop() } }
	Row(verticalAlignment = Alignment.CenterVertically) {
		IconButton(onClick = {
			if (playing) { player.stop(); playing = false }
			else {
				loading = true
				model.decodeGroupVoice(post.voiceOgg!!) { pcm ->
					loading = false
					if (pcm != null) {
						playing = player.playPcm(pcm,
								chat.zerion.desktop.ui.voice.GroupVoice.SAMPLE_RATE) {
							playing = false
						}
					}
				}
			}
		}) {
			Icon(if (playing) Icons.Filled.Stop else Icons.Filled.PlayArrow,
					contentDescription = if (playing) "Stop" else "Play",
					tint = onBubble)
		}
		Text("🎤 " + formatGroupDuration(post.voiceDurationMs), color = onBubble,
				style = MaterialTheme.typography.bodyMedium)
	}
}

private fun formatGroupDuration(ms: Int): String {
	val s = ms / 1000
	return String.format("%d:%02d", s / 60, s % 60)
}

@Composable
private fun GroupInput(
		error: String?,
		onSend: (String, (Boolean) -> Unit) -> Unit,
		onAttachImage: () -> Unit,
		onSendVoice: (ByteArray, (Boolean) -> Unit) -> Unit,
) {
	var text by remember { mutableStateOf("") }
	var sending by remember { mutableStateOf(false) }
	val recorder = remember {
		chat.zerion.desktop.ui.voice.VoiceRecorder(
				chat.zerion.desktop.ui.voice.GroupVoice.SAMPLE_RATE)
	}
	var recording by remember { mutableStateOf(false) }
	DisposableEffect(Unit) { onDispose { recorder.cancel() } }
	fun submit() {
		if (text.isNotBlank() && !sending) {
			sending = true
			onSend(text) { ok -> sending = false; if (ok) text = "" }
		}
	}
	Column(Modifier.fillMaxWidth()
			.background(MaterialTheme.colorScheme.surface)) {
		if (error != null) {
			Text(error, color = MaterialTheme.colorScheme.error,
					style = MaterialTheme.typography.labelMedium,
					modifier = Modifier.padding(
							start = 16.dp, end = 16.dp, top = 8.dp))
		}
		Row(Modifier.fillMaxWidth().padding(12.dp),
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(8.dp)) {
			IconButton(onClick = onAttachImage) {
				Icon(Icons.Filled.AttachFile,
						contentDescription = "Attach image")
			}
			IconButton(onClick = {
				if (recording) {
					recording = false
					val res = recorder.stop(System.currentTimeMillis())
					if (res != null && res.first.isNotEmpty())
						onSendVoice(res.first) { }
				} else {
					recording = recorder.start()
				}
			}) {
				Icon(if (recording) Icons.Filled.Stop else Icons.Filled.Mic,
						contentDescription = if (recording) "Stop recording"
								else "Record voice message",
						tint = if (recording) MaterialTheme.colorScheme.error
								else LocalContentColor.current)
			}
			OutlinedTextField(
					value = text,
					onValueChange = { text = it },
					placeholder = { Text(
							if (recording) "Recording… tap stop to send"
							else "Message the group…") },
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
					keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
					keyboardActions = KeyboardActions(onSend = { submit() }))
			FilledIconButton(onClick = { submit() },
					enabled = text.isNotBlank() && !sending,
					modifier = Modifier.size(48.dp)) {
				Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
			}
		}
	}
}

@Composable
private fun MemberListDialog(
		members: List<ZerionModel.GroupMemberItem>,
		canManage: Boolean,
		onPromote: (ByteArray) -> Unit,
		onDemote: (ByteArray) -> Unit,
		onRemove: (ByteArray) -> Unit,
		onClose: () -> Unit,
) {
	AlertDialog(
			onDismissRequest = onClose,
			title = { Text("Members (${members.size})") },
			text = {
				Column {
					members.forEach { m ->
						Row(Modifier.fillMaxWidth().padding(vertical = 6.dp),
								verticalAlignment = Alignment.CenterVertically) {
							Avatar(m.name, m.name.hashCode(), size = 32.dp)
							Spacer(Modifier.size(10.dp))
							Column(Modifier.weight(1f)) {
								Text(m.name + if (m.isSelf) " (you)" else "",
										style = MaterialTheme.typography.bodyLarge)
								Text(m.role,
										style = MaterialTheme.typography
												.labelMedium,
										color = MaterialTheme.colorScheme
												.onSurfaceVariant)
							}
							if (canManage && !m.isSelf && !m.isCreator) {
								var open by remember { mutableStateOf(false) }
								Box {
									IconButton(onClick = { open = true }) {
										Icon(Icons.Filled.MoreVert,
												contentDescription = "Manage")
									}
									DropdownMenu(expanded = open,
											onDismissRequest = { open = false }) {
										if (m.role == "Admin") {
											DropdownMenuItem(
													text = { Text("Demote") },
													onClick = {
														open = false
														onDemote(m.pubKey)
													})
										} else {
											DropdownMenuItem(
													text = { Text("Make admin") },
													onClick = {
														open = false
														onPromote(m.pubKey)
													})
										}
										DropdownMenuItem(
												text = { Text("Remove") },
												onClick = {
													open = false
													onRemove(m.pubKey)
												})
									}
								}
							}
						}
					}
				}
			},
			confirmButton = { TextButton(onClick = onClose) { Text("Done") } })
}

private fun chooseGroupImage(): java.io.File? {
	val dialog = java.awt.FileDialog(null as java.awt.Frame?, "Send image",
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

@Composable
private fun InviteDialog(
		contacts: List<ZerionModel.ContactItem>,
		onInvite: (org.zerionproject.core.api.contact.ContactId) -> Unit,
		onClose: () -> Unit,
) {
	AlertDialog(
			onDismissRequest = onClose,
			title = { Text("Invite a contact") },
			text = {
				if (contacts.isEmpty()) {
					Text("You have no contacts to invite yet.")
				} else {
					Column {
						contacts.forEach { c ->
							Row(Modifier.fillMaxWidth()
									.clip(RoundedCornerShape(8.dp))
									.padding(vertical = 8.dp, horizontal = 4.dp),
									verticalAlignment =
											Alignment.CenterVertically) {
								Avatar(c.name, c.colorKey, size = 32.dp)
								Spacer(Modifier.size(10.dp))
								Text(c.name, Modifier.weight(1f),
										style = MaterialTheme.typography.bodyLarge)
								TextButton(onClick = {
									onInvite(c.id); onClose()
								}) { Text("Invite") }
							}
						}
					}
				}
			},
			confirmButton = { TextButton(onClick = onClose) { Text("Close") } })
}
