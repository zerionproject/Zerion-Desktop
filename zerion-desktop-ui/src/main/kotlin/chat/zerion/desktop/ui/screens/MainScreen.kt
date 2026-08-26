package chat.zerion.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

import chat.zerion.desktop.ui.ZerionModel
import chat.zerion.desktop.ui.components.Avatar
import chat.zerion.desktop.ui.components.AvatarWithPresence
import chat.zerion.desktop.ui.components.I2pStatusPill
import chat.zerion.desktop.ui.components.TorStatusPill

@Composable
fun MainScreen(model: ZerionModel, onLogout: () -> Unit) {
	var showAddDialog by remember { mutableStateOf(false) }
	var showSettings by remember { mutableStateOf(false) }
	var showVault by remember { mutableStateOf(false) }
	var showCreateGroup by remember { mutableStateOf(false) }
	var showCreateChannel by remember { mutableStateOf(false) }
	var showJoinChannel by remember { mutableStateOf(false) }
	var search by remember { mutableStateOf("") }

	Box(Modifier.fillMaxSize()) {
	Row(Modifier.fillMaxSize()) {
		Sidebar(
				model = model,
				search = search,
				onSearch = { search = it },
				onAddContact = {
					model.loadMyLink()
					showAddDialog = true
				},
				onOpenSettings = {
					model.loadMyLink()
					showVault = false
					showSettings = true
				},
				onOpenVault = {
					showSettings = false
					showVault = true
				},
				onSelectContact = {
					showSettings = false; showVault = false
					model.selectContact(it)
				},
				onSelectGroup = {
					showSettings = false; showVault = false
					model.selectGroup(it)
				},
				onSelectChannel = {
					showSettings = false; showVault = false
					model.selectChannel(it)
				},
				onNewGroup = { showCreateGroup = true },
				onNewChannel = { showCreateChannel = true },
				onJoinChannel = { showJoinChannel = true },
				settingsOpen = showSettings,
				modifier = Modifier.width(300.dp).fillMaxHeight())
		Divider(
				Modifier.fillMaxHeight().width(1.dp),
				color = MaterialTheme.colorScheme.outlineVariant)
		Box(Modifier.weight(1f).fillMaxHeight()
				.background(MaterialTheme.colorScheme.background)) {
			val selectedContact = model.selectedId
			when {
				showVault -> VaultScreen(model.vault,
						onClose = { showVault = false })
				showSettings -> SettingsScreen(model,
						onBack = { showSettings = false }, onLogout = onLogout)
				model.selectedChannelHex != null -> ChannelScreen(model)
				model.selectedGroupHex != null -> GroupScreen(model)
				selectedContact != null ->
					ConversationScreen(model, selectedContact)
				else -> EmptyConversation()
			}
		}
	}

	if (showAddDialog) {
		AddContactDialog(
				myLink = model.myLink,
				onAdd = { link, alias, cb ->
					model.addContactFromLink(link, alias, cb)
				},
				onClose = { showAddDialog = false })
	}
	if (showCreateGroup) {
		CreateGroupDialog(
				onCreate = { model.createGroup(it) {} },
				onClose = { showCreateGroup = false })
	}
	if (showCreateChannel) {
		CreateChannelDialog(
				onCreate = { name, desc, pub ->
					model.createChannel(name, desc, pub) {} },
				onClose = { showCreateChannel = false })
	}
	if (showJoinChannel) {
		JoinChannelDialog(
				onJoin = { link, cb -> model.joinChannelFromLink(link, cb) },
				onClose = { showJoinChannel = false })
	}

		CallOverlay(model)
	}
}

@Composable
private fun Sidebar(
		model: ZerionModel,
		search: String,
		onSearch: (String) -> Unit,
		onAddContact: () -> Unit,
		onOpenSettings: () -> Unit,
		onOpenVault: () -> Unit,
		onSelectContact: (org.zerionproject.core.api.contact.ContactId) -> Unit,
		onSelectGroup: (ZerionModel.GroupItem) -> Unit,
		onSelectChannel: (ZerionModel.ChannelItem) -> Unit,
		onNewGroup: () -> Unit,
		onNewChannel: () -> Unit,
		onJoinChannel: () -> Unit,
		settingsOpen: Boolean,
		modifier: Modifier = Modifier,
) {
	val q = search.trim().lowercase()
	val contacts = if (q.isEmpty()) model.contacts
	else model.contacts.filter { it.name.lowercase().contains(q) }
	val groups = if (q.isEmpty()) model.groups
	else model.groups.filter { it.name.lowercase().contains(q) }
	val channels = if (q.isEmpty()) model.channels
	else model.channels.filter { it.name.lowercase().contains(q) }

	Column(modifier.background(MaterialTheme.colorScheme.surface)) {
		AccountHeader(model, onOpenSettings)
		Divider(color = MaterialTheme.colorScheme.outlineVariant)
		OutlinedTextField(
				value = search,
				onValueChange = onSearch,
				singleLine = true,
				placeholder = { Text("Search") },
				leadingIcon = {
					Icon(Icons.Filled.Search, contentDescription = null)
				},
				modifier = Modifier.fillMaxWidth()
						.padding(horizontal = 12.dp, vertical = 8.dp))
		Row(Modifier.fillMaxWidth().clickable(onClick = onOpenVault)
				.padding(horizontal = 16.dp, vertical = 12.dp),
				verticalAlignment = Alignment.CenterVertically) {
			Box(Modifier.size(36.dp).clip(CircleShape)
					.background(MaterialTheme.colorScheme.primary),
					contentAlignment = Alignment.Center) {
				Icon(Icons.Filled.Lock, contentDescription = null,
						tint = MaterialTheme.colorScheme.onPrimary,
						modifier = Modifier.size(18.dp))
			}
			Spacer(Modifier.width(12.dp))
			Text("Vault", style = MaterialTheme.typography.bodyLarge,
					fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
			Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight,
					contentDescription = null,
					tint = MaterialTheme.colorScheme.onSurfaceVariant)
		}
		Divider(color = MaterialTheme.colorScheme.outlineVariant)
		LazyColumn(Modifier.weight(1f)) {
			if (model.groupInvites.isNotEmpty()) {
				item { SectionHeader("Group invitations", null, null) }
				items(model.groupInvites, key = { "inv-" + it.idHex }) { inv ->
					InviteRow(
							invite = inv,
							onAccept = { model.acceptGroupInvite(inv.id) },
							onDecline = { model.declineGroupInvite(inv.id) })
				}
			}
			if (model.pending.isNotEmpty()) {
				item { SectionHeader("Pending requests", null, null) }
				items(model.pending, key = { "p-" + it.id.hashCode() }) { p ->
					PendingRow(p, onCancel = { model.cancelPendingContact(p.id) })
				}
			}
			item {
				SectionHeader("Contacts", Icons.Filled.PersonAdd, onAddContact)
			}
			items(contacts, key = { "c-" + it.id.int }) { c ->
				ContactRow(
						item = c,
						selected = !settingsOpen &&
								model.selectedGroupHex == null &&
								c.id == model.selectedId,
						onClick = { onSelectContact(c.id) })
			}
			item { SectionHeader("Groups", Icons.Filled.GroupAdd, onNewGroup) }
			items(groups, key = { "g-" + it.idHex }) { g ->
				GroupRow(
						item = g,
						selected = !settingsOpen &&
								model.selectedGroupHex == g.idHex,
						onClick = { onSelectGroup(g) })
			}
			item { ChannelsHeader(onNewChannel, onJoinChannel) }
			items(channels, key = { "ch-" + it.idHex }) { ch ->
				ChannelRow(
						item = ch,
						selected = !settingsOpen &&
								model.selectedChannelHex == ch.idHex,
						onClick = { onSelectChannel(ch) })
			}
		}
	}
}

@Composable
private fun ChannelsHeader(onNew: () -> Unit, onJoin: () -> Unit) {
	Row(
			Modifier.fillMaxWidth().padding(
					start = 16.dp, end = 12.dp, top = 14.dp, bottom = 4.dp),
			verticalAlignment = Alignment.CenterVertically) {
		Text("Channels",
				style = MaterialTheme.typography.labelLarge,
				color = MaterialTheme.colorScheme.onSurfaceVariant)
		Spacer(Modifier.weight(1f))
		var open by remember { mutableStateOf(false) }
		Box {
			FilledIconButton(onClick = { open = true },
					modifier = Modifier.size(32.dp)) {
				Icon(Icons.Filled.Add, contentDescription = "Channels",
						modifier = Modifier.size(18.dp))
			}
			DropdownMenu(expanded = open,
					onDismissRequest = { open = false }) {
				DropdownMenuItem(text = { Text("Create channel") },
						onClick = { open = false; onNew() })
				DropdownMenuItem(text = { Text("Join with link") },
						onClick = { open = false; onJoin() })
			}
		}
	}
}

@Composable
private fun ChannelRow(
		item: ZerionModel.ChannelItem,
		selected: Boolean,
		onClick: () -> Unit,
) {
	val bg = if (selected) MaterialTheme.colorScheme.surfaceContainerHigh
	else Color.Transparent
	Row(
			Modifier.fillMaxWidth()
					.clickable(onClick = onClick)
					.background(bg)
					.padding(horizontal = 12.dp, vertical = 10.dp),
			verticalAlignment = Alignment.CenterVertically) {
		Box(Modifier.size(42.dp).clip(CircleShape)
				.background(MaterialTheme.colorScheme.primary),
				contentAlignment = Alignment.Center) {
			Icon(Icons.Filled.Campaign, contentDescription = null,
					tint = MaterialTheme.colorScheme.onPrimary,
					modifier = Modifier.size(20.dp))
		}
		Spacer(Modifier.width(12.dp))
		Column(Modifier.weight(1f)) {
			Text(item.name, style = MaterialTheme.typography.bodyLarge,
					fontWeight = FontWeight.Medium,
					maxLines = 1, overflow = TextOverflow.Ellipsis)
			Text(if (item.publisher) "Owner" else "Subscribed",
					style = MaterialTheme.typography.labelMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant)
		}
		if (item.unread > 0) {
			Badge(containerColor = MaterialTheme.colorScheme.primary) {
				Text(item.unread.toString())
			}
		}
	}
}

@Composable
private fun CreateChannelDialog(
		onCreate: (String, String, Boolean) -> Unit,
		onClose: () -> Unit,
) {
	var name by remember { mutableStateOf("") }
	var desc by remember { mutableStateOf("") }
	var pub by remember { mutableStateOf(true) }
	AlertDialog(
			onDismissRequest = onClose,
			title = { Text("New channel") },
			text = {
				Column {
					Text("A channel is a feed only you can post to. Share its " +
							"link so others can subscribe.",
							style = MaterialTheme.typography.bodyMedium,
							color = MaterialTheme.colorScheme.onSurfaceVariant)
					Spacer(Modifier.size(12.dp))
					OutlinedTextField(name, { name = it }, singleLine = true,
							label = { Text("Channel name") },
							modifier = Modifier.fillMaxWidth())
					Spacer(Modifier.size(8.dp))
					OutlinedTextField(desc, { desc = it },
							label = { Text("Description (optional)") },
							modifier = Modifier.fillMaxWidth())
				}
			},
			confirmButton = {
				TextButton(onClick = { onCreate(name, desc, pub); onClose() },
						enabled = name.isNotBlank()) { Text("Create") }
			},
			dismissButton = {
				TextButton(onClick = onClose) { Text("Cancel") }
			})
}

@Composable
private fun JoinChannelDialog(
		onJoin: (String, (String?) -> Unit) -> Unit,
		onClose: () -> Unit,
) {
	var link by remember { mutableStateOf("") }
	var error by remember { mutableStateOf<String?>(null) }
	var busy by remember { mutableStateOf(false) }
	AlertDialog(
			onDismissRequest = { if (!busy) onClose() },
			title = { Text("Join a channel") },
			text = {
				Column {
					OutlinedTextField(link, { link = it; error = null },
							singleLine = true,
							label = { Text("Channel invite link") },
							isError = error != null,
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
							busy = true; error = null
							onJoin(link) { err ->
								busy = false
								if (err == null) onClose() else error = err
							}
						},
						enabled = !busy && link.isNotBlank()) {
					Text(if (busy) "Joining…" else "Join")
				}
			},
			dismissButton = {
				TextButton(onClick = onClose, enabled = !busy) { Text("Cancel") }
			})
}

@Composable
private fun SectionHeader(
		title: String,
		actionIcon: androidx.compose.ui.graphics.vector.ImageVector?,
		onAction: (() -> Unit)?,
) {
	Row(
			Modifier.fillMaxWidth().padding(
					start = 16.dp, end = 12.dp, top = 14.dp, bottom = 4.dp),
			verticalAlignment = Alignment.CenterVertically) {
		Text(title,
				style = MaterialTheme.typography.labelLarge,
				color = MaterialTheme.colorScheme.onSurfaceVariant)
		Spacer(Modifier.weight(1f))
		if (actionIcon != null && onAction != null) {
			FilledIconButton(onClick = onAction,
					modifier = Modifier.size(32.dp)) {
				Icon(actionIcon, contentDescription = title,
						modifier = Modifier.size(18.dp))
			}
		}
	}
}

@Composable
private fun GroupRow(
		item: ZerionModel.GroupItem,
		selected: Boolean,
		onClick: () -> Unit,
) {
	val bg = if (selected) MaterialTheme.colorScheme.surfaceContainerHigh
	else Color.Transparent
	Row(
			Modifier.fillMaxWidth()
					.clickable(onClick = onClick)
					.background(bg)
					.padding(horizontal = 12.dp, vertical = 10.dp),
			verticalAlignment = Alignment.CenterVertically) {
		Box(Modifier.size(42.dp).clip(CircleShape)
				.background(MaterialTheme.colorScheme.secondary),
				contentAlignment = Alignment.Center) {
			Icon(Icons.Filled.Groups, contentDescription = null,
					tint = MaterialTheme.colorScheme.onSecondary,
					modifier = Modifier.size(22.dp))
		}
		Spacer(Modifier.width(12.dp))
		Column(Modifier.weight(1f)) {
			Text(item.name, style = MaterialTheme.typography.bodyLarge,
					fontWeight = FontWeight.Medium,
					maxLines = 1, overflow = TextOverflow.Ellipsis)
			Text("${item.members} " +
					if (item.members == 1) "member" else "members",
					style = MaterialTheme.typography.labelMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant)
		}
		if (item.unread > 0) {
			Badge(containerColor = MaterialTheme.colorScheme.primary) {
				Text(item.unread.toString())
			}
		}
	}
}

@Composable
private fun InviteRow(
		invite: ZerionModel.GroupInvite,
		onAccept: () -> Unit,
		onDecline: () -> Unit,
) {
	Column(Modifier.fillMaxWidth()
			.padding(horizontal = 12.dp, vertical = 8.dp)) {
		Text(invite.name, style = MaterialTheme.typography.bodyLarge,
				fontWeight = FontWeight.Medium,
				maxLines = 1, overflow = TextOverflow.Ellipsis)
		Text("Invite from ${invite.from}",
				style = MaterialTheme.typography.labelMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant)
		Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
			TextButton(onClick = onAccept) { Text("Accept") }
			TextButton(onClick = onDecline) { Text("Decline") }
		}
	}
}

@Composable
private fun CreateGroupDialog(
		onCreate: (String) -> Unit,
		onClose: () -> Unit,
) {
	var name by remember { mutableStateOf("") }
	AlertDialog(
			onDismissRequest = onClose,
			title = { Text("New group") },
			text = {
				OutlinedTextField(
						value = name,
						onValueChange = { name = it },
						singleLine = true,
						label = { Text("Group name") })
			},
			confirmButton = {
				TextButton(
						onClick = { onCreate(name); onClose() },
						enabled = name.isNotBlank()) { Text("Create") }
			},
			dismissButton = {
				TextButton(onClick = onClose) { Text("Cancel") }
			})
}

@Composable
private fun AccountHeader(model: ZerionModel, onOpenSettings: () -> Unit) {
	Column(Modifier.fillMaxWidth()
			.clickable(onClick = onOpenSettings)
			.padding(16.dp),
			verticalArrangement = Arrangement.spacedBy(10.dp)) {
		Row(verticalAlignment = Alignment.CenterVertically) {
			Avatar(name = model.localName.ifEmpty { "Z" }, colorKey = 0,
					size = 40.dp, photo = model.myAvatar)
			Spacer(Modifier.width(12.dp))
			Column(Modifier.weight(1f)) {
				Text(model.localName.ifEmpty { "Zerion" },
						style = MaterialTheme.typography.titleMedium,
						maxLines = 1, overflow = TextOverflow.Ellipsis)
				Text("View profile and settings",
						style = MaterialTheme.typography.labelMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant)
			}
			Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight,
					contentDescription = "Open settings",
					tint = MaterialTheme.colorScheme.onSurfaceVariant)
		}
		Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
			TorStatusPill(model.torState)
			if (model.i2pEnabled) {
				I2pStatusPill(model.i2pState)
			}
		}
	}
}

@Composable
private fun ContactRow(
		item: ZerionModel.ContactItem,
		selected: Boolean,
		onClick: () -> Unit,
) {
	val bg = if (selected) MaterialTheme.colorScheme.surfaceContainerHigh
	else Color.Transparent
	Row(
			Modifier.fillMaxWidth()
					.clickable(onClick = onClick)
					.background(bg)
					.padding(horizontal = 12.dp, vertical = 10.dp),
			verticalAlignment = Alignment.CenterVertically) {
		AvatarWithPresence(item.name, item.colorKey, item.connected,
				size = 42.dp, photo = item.avatar)
		Spacer(Modifier.width(12.dp))
		Column(Modifier.weight(1f)) {
			Text(item.name, style = MaterialTheme.typography.bodyLarge,
					fontWeight = FontWeight.Medium,
					maxLines = 1, overflow = TextOverflow.Ellipsis)
			Text(
					if (item.connected) "Online" else "Offline",
					style = MaterialTheme.typography.labelMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant)
		}
		if (item.unread > 0) {
			Badge(containerColor = MaterialTheme.colorScheme.primary) {
				Text(item.unread.toString())
			}
		}
	}
}

@Composable
private fun PendingRow(
		item: ZerionModel.PendingItem,
		onCancel: () -> Unit,
) {
	Row(
			Modifier.fillMaxWidth().padding(
					start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
			verticalAlignment = Alignment.CenterVertically) {
		Box(Modifier.size(42.dp).clip(CircleShape)
				.background(MaterialTheme.colorScheme.surfaceContainerHighest),
				contentAlignment = Alignment.Center) {
			Icon(Icons.Filled.HourglassEmpty, contentDescription = null,
					modifier = Modifier.size(20.dp),
					tint = MaterialTheme.colorScheme.onSurfaceVariant)
		}
		Spacer(Modifier.width(12.dp))
		Column(Modifier.weight(1f)) {
			Text(item.name.ifEmpty { "Pending contact" },
					style = MaterialTheme.typography.bodyLarge,
					maxLines = 1, overflow = TextOverflow.Ellipsis)
			Text(item.state,
					style = MaterialTheme.typography.labelMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant)
		}
		IconButton(onClick = onCancel) {
			Icon(Icons.Filled.Close, contentDescription = "Cancel request",
					modifier = Modifier.size(18.dp))
		}
	}
}

@Composable
private fun EmptyConversation() {
	Column(Modifier.fillMaxSize(),
			verticalArrangement = Arrangement.Center,
			horizontalAlignment = Alignment.CenterHorizontally) {
		Box(Modifier.size(72.dp).clip(RoundedCornerShape(20.dp))
				.background(MaterialTheme.colorScheme.surfaceContainerHigh),
				contentAlignment = Alignment.Center) {
			Text("Z", style = MaterialTheme.typography.headlineMedium,
					color = MaterialTheme.colorScheme.primary)
		}
		Spacer(Modifier.size(16.dp))
		Text("Select a contact to start chatting",
				style = MaterialTheme.typography.titleMedium)
		Text("All messages are end-to-end encrypted and routed over Tor.",
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant)
	}
}
