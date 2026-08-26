package chat.zerion.desktop.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

import chat.zerion.desktop.ui.components.safeImageBitmap
import chat.zerion.desktop.ui.ZerionModel

import org.jetbrains.skia.Image as SkiaImage

@Composable
fun ChannelScreen(model: ZerionModel) {
	val channel = model.selectedChannel ?: return
	var link by remember(channel.idHex) { mutableStateOf<String?>(null) }
	var confirmRemove by remember(channel.idHex) { mutableStateOf(false) }
	var commentsFor by remember(channel.idHex) {
		mutableStateOf<ZerionModel.ChannelPostItem?>(null)
	}
	var showModeration by remember(channel.idHex) { mutableStateOf(false) }

	Column(Modifier.fillMaxSize()) {
		ChannelHeader(
				channel = channel,
				onShare = { model.exportChannelLink(channel.id) { link = it } },
				onRefresh = { model.refreshChannel(channel.id) },
				onRemove = { confirmRemove = true },
				onModerate = { showModeration = true })
		Divider(color = MaterialTheme.colorScheme.outlineVariant)
		if (model.channelRefreshing) {
			LinearProgressIndicator(Modifier.fillMaxWidth())
		}

		val listState = rememberLazyListState()
		LaunchedEffect(model.channelPosts.size) {
			if (model.channelPosts.isNotEmpty()) {
				listState.scrollToItem(model.channelPosts.lastIndex)
			}
		}
		LazyColumn(
				state = listState,
				modifier = Modifier.weight(1f).fillMaxWidth()
						.background(MaterialTheme.colorScheme.background),
				contentPadding = PaddingValues(horizontal = 20.dp,
						vertical = 14.dp),
				verticalArrangement = Arrangement.spacedBy(10.dp)) {
			if (model.channelPosts.isEmpty()) {
				item {
					Text("No posts yet.",
							color = MaterialTheme.colorScheme.onSurfaceVariant,
							style = MaterialTheme.typography.bodyMedium)
				}
			}
			items(model.channelPosts, key = { it.id }) { post ->
				ChannelPostView(post,
						onComments = { commentsFor = post },
						onReact = {
							model.reactToChannelPost(channel.id,
									post.id.toLong(), "👍")
						})
			}
		}

		if (channel.publisher) {
			ChannelComposeBar(
					error = model.sendError,
					onPublish = { text, cb ->
						model.publishChannelText(text, cb) },
					onAttach = {
						val file = chooseChannelImage()
						if (file != null) model.publishChannelImage(file) {}
					})
		} else {
			Text("You're subscribed to this channel. Only the owner can post.",
					Modifier.fillMaxWidth()
							.background(MaterialTheme.colorScheme.surface)
							.padding(16.dp),
					style = MaterialTheme.typography.labelMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant)
		}
	}

	link?.let { l ->
		val clipboard = LocalClipboardManager.current
		AlertDialog(
				onDismissRequest = { link = null },
				title = { Text("Share ${channel.name}") },
				text = {
					Column {
						Text("Anyone with this link can subscribe:",
								style = MaterialTheme.typography.bodyMedium,
								color = MaterialTheme.colorScheme.onSurfaceVariant)
						Spacer(Modifier.size(8.dp))
						OutlinedTextField(l, {}, readOnly = true,
								modifier = Modifier.fillMaxWidth(), maxLines = 3)
					}
				},
				confirmButton = {
					TextButton(onClick = {
						clipboard.setText(AnnotatedString(l)); link = null
					}) { Text("Copy") }
				},
				dismissButton = {
					TextButton(onClick = { link = null }) { Text("Close") }
				})
	}
	if (confirmRemove) {
		val publisher = channel.publisher
		AlertDialog(
				onDismissRequest = { confirmRemove = false },
				title = { Text(if (publisher) "Delete channel?"
						else "Leave channel?") },
				text = {
					Text(if (publisher)
						"This permanently deletes ${channel.name} for everyone."
					else "You'll stop receiving posts from ${channel.name}.")
				},
				confirmButton = {
					TextButton(onClick = {
						if (publisher) model.deleteChannel(channel.id)
						else model.leaveChannel(channel.id)
						confirmRemove = false
					}) { Text(if (publisher) "Delete" else "Leave") }
				},
				dismissButton = {
					TextButton(onClick = { confirmRemove = false }) {
						Text("Cancel")
					}
				})
	}
	commentsFor?.let { post ->
		ChannelCommentsDialog(model, channel.id, post) { commentsFor = null }
	}
	if (showModeration) {
		ChannelModerationDialog(model, channel.id) { showModeration = false }
	}
}

@Composable
private fun ChannelHeader(
		channel: ZerionModel.ChannelItem,
		onShare: () -> Unit,
		onRefresh: () -> Unit,
		onRemove: () -> Unit,
		onModerate: () -> Unit,
) {
	Row(
			Modifier.fillMaxWidth()
					.background(MaterialTheme.colorScheme.surface)
					.padding(horizontal = 20.dp, vertical = 12.dp),
			verticalAlignment = Alignment.CenterVertically) {
		Box(Modifier.size(40.dp).clip(CircleShape)
				.background(MaterialTheme.colorScheme.primary),
				contentAlignment = Alignment.Center) {
			Icon(Icons.Filled.Campaign, contentDescription = null,
					tint = MaterialTheme.colorScheme.onPrimary,
					modifier = Modifier.size(22.dp))
		}
		Spacer(Modifier.size(12.dp))
		Column(Modifier.weight(1f)) {
			Text(channel.name, style = MaterialTheme.typography.titleMedium,
					maxLines = 1, overflow = TextOverflow.Ellipsis)
			Text(if (channel.publisher) "You own this channel"
			else "Subscribed",
					style = MaterialTheme.typography.labelMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant)
		}
		IconButton(onClick = onRefresh) {
			Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
		}
		var menuOpen by remember { mutableStateOf(false) }
		Box {
			IconButton(onClick = { menuOpen = true }) {
				Icon(Icons.Filled.MoreVert, contentDescription = "Options")
			}
			DropdownMenu(expanded = menuOpen,
					onDismissRequest = { menuOpen = false }) {
				DropdownMenuItem(text = { Text("Share invite link") },
						onClick = { menuOpen = false; onShare() })
				if (channel.publisher) {
					DropdownMenuItem(text = { Text("Moderation") },
							onClick = { menuOpen = false; onModerate() })
				}
				DropdownMenuItem(
						text = { Text(if (channel.publisher) "Delete channel"
								else "Leave channel") },
						onClick = { menuOpen = false; onRemove() })
			}
		}
	}
}

@Composable
private fun ChannelPostView(post: ZerionModel.ChannelPostItem,
		onComments: () -> Unit, onReact: () -> Unit) {
	Card(Modifier.fillMaxWidth()) {
		Column(Modifier.padding(12.dp),
				verticalArrangement = Arrangement.spacedBy(6.dp)) {
			post.images.forEach { bytes ->
				val bitmap = remember(bytes) { safeImageBitmap(bytes) }
				if (bitmap != null)
					Image(bitmap = bitmap, contentDescription = "Image",
							modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)
									.clip(RoundedCornerShape(8.dp)))
			}
			if (post.body.isNotEmpty()) {
				Text(post.body, style = MaterialTheme.typography.bodyLarge)
			}
			Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
				TextButton(onClick = onReact) { Text("👍 Like") }
				TextButton(onClick = onComments) { Text("💬 Comments") }
			}
		}
	}
}

@Composable
private fun ChannelComposeBar(
		error: String?,
		onPublish: (String, (Boolean) -> Unit) -> Unit,
		onAttach: () -> Unit,
) {
	var text by remember { mutableStateOf("") }
	var busy by remember { mutableStateOf(false) }
	fun submit() {
		if (text.isNotBlank() && !busy) {
			busy = true
			onPublish(text) { ok -> busy = false; if (ok) text = "" }
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
			IconButton(onClick = onAttach) {
				Icon(Icons.Filled.AttachFile, contentDescription = "Attach")
			}
			OutlinedTextField(
					value = text,
					onValueChange = { text = it },
					placeholder = { Text("Post to your channel…") },
					modifier = Modifier.weight(1f).heightIn(max = 120.dp),
					maxLines = 5,
					shape = RoundedCornerShape(22.dp),
					keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
					keyboardActions = KeyboardActions(onSend = { submit() }))
			FilledIconButton(onClick = { submit() },
					enabled = text.isNotBlank() && !busy,
					modifier = Modifier.size(48.dp)) {
				Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Post")
			}
		}
	}
}

private fun chooseChannelImage(): java.io.File? {
	val dialog = java.awt.FileDialog(null as java.awt.Frame?, "Post image",
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
private fun ChannelCommentsDialog(model: ZerionModel, channelId: ByteArray,
		post: ZerionModel.ChannelPostItem, onClose: () -> Unit) {
	val parentSeq = post.id.toLong()
	var comments by remember(post.id) {
		mutableStateOf<List<ZerionModel.ChannelCommentItem>>(emptyList())
	}
	var input by remember(post.id) { mutableStateOf("") }
	var busy by remember { mutableStateOf(false) }
	LaunchedEffect(post.id) {
		model.loadChannelComments(channelId, parentSeq) { comments = it }
	}
	AlertDialog(
			onDismissRequest = onClose,
			title = { Text("Comments") },
			text = {
				Column(Modifier.heightIn(max = 380.dp)
						.verticalScroll(rememberScrollState()),
						verticalArrangement = Arrangement.spacedBy(8.dp)) {
					if (comments.isEmpty()) {
						Text("No comments yet.",
								color = MaterialTheme.colorScheme.onSurfaceVariant)
					}
					comments.forEach { c ->
						Column {
							Text(c.author,
									style = MaterialTheme.typography.labelMedium,
									color = MaterialTheme.colorScheme.primary)
							Text(c.body,
									style = MaterialTheme.typography.bodyMedium)
						}
					}
					OutlinedTextField(value = input,
							onValueChange = { input = it },
							label = { Text("Write a comment") },
							modifier = Modifier.fillMaxWidth())
				}
			},
			confirmButton = {
				TextButton(enabled = !busy && input.isNotBlank(), onClick = {
					busy = true
					model.postChannelComment(channelId, parentSeq, input) { ok ->
						busy = false
						if (ok) {
							input = ""
							model.loadChannelComments(channelId, parentSeq) {
								comments = it
							}
						}
					}
				}) { Text("Post") }
			},
			dismissButton = {
				TextButton(onClick = onClose) { Text("Close") }
			})
}

@Composable
private fun ChannelModerationDialog(model: ZerionModel, channelId: ByteArray,
		onClose: () -> Unit) {
	var subs by remember {
		mutableStateOf<List<ZerionModel.ChannelSubscriberItem>>(emptyList())
	}
	var apps by remember {
		mutableStateOf<List<ZerionModel.ChannelApplicationItem>>(emptyList())
	}
	var delegs by remember {
		mutableStateOf<List<ZerionModel.ChannelDelegationItem>>(emptyList())
	}
	fun reload() {
		model.loadChannelSubscribers(channelId) { subs = it }
		model.loadChannelApplications(channelId) { apps = it }
		model.loadChannelDelegations(channelId) { delegs = it }
	}
	LaunchedEffect(Unit) { reload() }
	val muted = MaterialTheme.colorScheme.onSurfaceVariant
	AlertDialog(
			onDismissRequest = onClose,
			title = { Text("Moderation") },
			text = {
				Column(Modifier.heightIn(max = 460.dp)
						.verticalScroll(rememberScrollState()),
						verticalArrangement = Arrangement.spacedBy(6.dp)) {
					Text("Pending applications",
							style = MaterialTheme.typography.titleSmall)
					if (apps.isEmpty()) Text("None.", color = muted)
					apps.forEach { a ->
						Row(verticalAlignment = Alignment.CenterVertically) {
							Text(a.name, Modifier.weight(1f))
							TextButton(onClick = {
								model.approveChannelApplication(channelId,
										a.ed25519) { reload() }
							}) { Text("Approve") }
							TextButton(onClick = {
								model.denyChannelApplication(channelId,
										a.ed25519) { reload() }
							}) { Text("Deny") }
						}
					}
					Divider()
					Text("Subscribers",
							style = MaterialTheme.typography.titleSmall)
					if (subs.isEmpty()) Text("None.", color = muted)
					subs.forEach { s ->
						Row(verticalAlignment = Alignment.CenterVertically) {
							Text(s.name, Modifier.weight(1f))
							TextButton(onClick = {
								model.delegateChannelPublisher(channelId,
										s.ed25519, s.mlDsa) { reload() }
							}) { Text("Make publisher") }
							TextButton(onClick = {
								model.banChannelSubscriber(channelId,
										s.ed25519) { reload() }
							}) { Text("Ban") }
						}
					}
					Divider()
					Text("Co-publishers",
							style = MaterialTheme.typography.titleSmall)
					if (delegs.isEmpty()) Text("None.", color = muted)
					delegs.forEach { d ->
						Row(verticalAlignment = Alignment.CenterVertically) {
							Text(d.name, Modifier.weight(1f))
							TextButton(onClick = {
								model.revokeChannelDelegation(channelId,
										d.delegationSeq) { reload() }
							}) { Text("Revoke") }
						}
					}
					Divider()
					TextButton(onClick = {
						model.rotateChannelJoinKey(channelId) {}
					}) { Text("Rotate invite link") }
				}
			},
			confirmButton = {
				TextButton(onClick = onClose) { Text("Done") }
			})
}
