package chat.zerion.desktop.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

import chat.zerion.desktop.ui.DesktopProfiles
import chat.zerion.desktop.ui.components.AuthCard
import chat.zerion.desktop.ui.components.AuthScreen
import chat.zerion.desktop.ui.components.Avatar

@Composable
internal fun ProfileSelectScreen(
		profiles: List<DesktopProfiles.Profile>,
		onSelect: (DesktopProfiles.Profile) -> Unit,
		onCreate: (String) -> Unit,
		onDelete: (DesktopProfiles.Profile) -> Unit,
) {
	var showCreate by remember { mutableStateOf(false) }
	var toDelete by remember {
		mutableStateOf<DesktopProfiles.Profile?>(null)
	}

	AuthScreen(subtitle = if (profiles.isEmpty())
			"Create your first profile to get started"
	else "Choose a profile") {
		profiles.forEach { p ->
			ProfileRow(p, onClick = { onSelect(p) },
					onDelete = { toDelete = p })
		}
		OutlinedButton(onClick = { showCreate = true },
				modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
			Icon(Icons.Filled.Add, contentDescription = null,
					modifier = Modifier.size(18.dp))
			Spacer(Modifier.width(8.dp))
			Text("New profile")
		}
	}

	if (showCreate) {
		var name by remember { mutableStateOf("") }
		AlertDialog(
				onDismissRequest = { showCreate = false },
				title = { Text("New profile") },
				text = {
					Column {
						Text("A profile is a separate identity with its own " +
								"contacts, groups and encrypted data.",
								style = MaterialTheme.typography.bodyMedium,
								color = MaterialTheme.colorScheme
										.onSurfaceVariant)
						Spacer(Modifier.size(12.dp))
						OutlinedTextField(name, { name = it }, singleLine = true,
								label = { Text("Profile name") },
								modifier = Modifier.fillMaxWidth())
					}
				},
				confirmButton = {
					TextButton(onClick = {
						onCreate(name.trim()); showCreate = false
					}, enabled = name.isNotBlank()) { Text("Create") }
				},
				dismissButton = {
					TextButton(onClick = { showCreate = false }) {
						Text("Cancel")
					}
				})
	}

	toDelete?.let { p ->
		AlertDialog(
				onDismissRequest = { toDelete = null },
				title = { Text("Delete profile?") },
				text = {
					Text("This permanently deletes the profile \"${p.name}\" " +
							"and all of its data from this device. This cannot " +
							"be undone.")
				},
				confirmButton = {
					TextButton(onClick = { onDelete(p); toDelete = null }) {
						Text("Delete")
					}
				},
				dismissButton = {
					TextButton(onClick = { toDelete = null }) { Text("Cancel") }
				})
	}
}

@Composable
private fun ProfileRow(
		profile: DesktopProfiles.Profile,
		onClick: () -> Unit,
		onDelete: () -> Unit,
) {
	Card(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
			.clickable(onClick = onClick),
			colors = CardDefaults.cardColors(containerColor = AuthCard)) {
		Row(Modifier.fillMaxWidth().padding(12.dp),
				verticalAlignment = Alignment.CenterVertically) {
			Avatar(profile.name, profile.id.hashCode(), size = 40.dp)
			Spacer(Modifier.width(12.dp))
			Text(profile.name, Modifier.weight(1f),
					style = MaterialTheme.typography.titleMedium,
					fontWeight = FontWeight.Medium,
					color = MaterialTheme.colorScheme.onSurface)
			IconButton(onClick = onDelete) {
				Icon(Icons.Filled.Delete, contentDescription = "Delete profile",
						tint = MaterialTheme.colorScheme.onSurfaceVariant)
			}
		}
	}
}
