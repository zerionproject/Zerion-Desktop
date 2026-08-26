package chat.zerion.desktop.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import chat.zerion.desktop.ui.QrCode
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp

@Composable
fun AddContactDialog(
		myLink: String?,
		onAdd: (link: String, alias: String, onResult: (String?) -> Unit) -> Unit,
		onClose: () -> Unit,
) {
	var remoteLink by remember { mutableStateOf("") }
	var alias by remember { mutableStateOf("") }
	var error by remember { mutableStateOf<String?>(null) }
	var busy by remember { mutableStateOf(false) }
	val clipboard = LocalClipboardManager.current

	AlertDialog(
			onDismissRequest = { if (!busy) onClose() },
			title = { Text("Add a contact") },
			text = {
				Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
					Text("Give this link to the person you want to add:",
							style = MaterialTheme.typography.bodyMedium,
							color = MaterialTheme.colorScheme.onSurfaceVariant)
					Row(verticalAlignment =
							androidx.compose.ui.Alignment.CenterVertically) {
						OutlinedTextField(
								value = myLink ?: "Generating…",
								onValueChange = {},
								readOnly = true,
								singleLine = true,
								label = { Text("Your link") },
								modifier = Modifier.weight(1f))
						IconButton(
								onClick = {
									myLink?.let {
										clipboard.setText(AnnotatedString(it))
									}
								},
								enabled = myLink != null) {
							Icon(Icons.Filled.ContentCopy,
									contentDescription = "Copy your link")
						}
					}
					if (myLink != null) {
						Box(Modifier.fillMaxWidth(),
								contentAlignment = Alignment.Center) {
							val qr = remember(myLink) { QrCode.pngFor(myLink, 320) }
							val bmp = remember(qr) {
								org.jetbrains.skia.Image.makeFromEncoded(qr)
										.toComposeImageBitmap()
							}
							Box(Modifier.background(Color.White,
									RoundedCornerShape(12.dp)).padding(10.dp)) {
								Image(bitmap = bmp,
										contentDescription = "Your contact QR code",
										modifier = Modifier.size(200.dp))
							}
						}
						Text("Or let them scan this code.",
								style = MaterialTheme.typography.labelSmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant)
					}
					Text("Paste their link here:",
							style = MaterialTheme.typography.bodyMedium,
							color = MaterialTheme.colorScheme.onSurfaceVariant)
					OutlinedTextField(
							value = remoteLink,
							onValueChange = { remoteLink = it; error = null },
							singleLine = true,
							label = { Text("Their zerion:// link") },
							isError = error != null,
							modifier = Modifier.fillMaxWidth())
					OutlinedTextField(
							value = alias,
							onValueChange = { alias = it },
							singleLine = true,
							label = { Text("Name for this contact") },
							modifier = Modifier.fillMaxWidth())
					if (error != null) {
						Text(error!!,
								color = MaterialTheme.colorScheme.error,
								style = MaterialTheme.typography.labelMedium)
					}
				}
			},
			confirmButton = {
				TextButton(
						onClick = {
							if (remoteLink.isBlank() || alias.isBlank()) {
								error = "Enter their link and a name."
								return@TextButton
							}
							busy = true
							error = null
							onAdd(remoteLink, alias) { err ->
								busy = false
								if (err == null) onClose() else error = err
							}
						},
						enabled = !busy) {
					Text(if (busy) "Adding…" else "Add contact")
				}
			},
			dismissButton = {
				TextButton(onClick = onClose, enabled = !busy) {
					Text("Cancel")
				}
			})
}
