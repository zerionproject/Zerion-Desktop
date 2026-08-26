package chat.zerion.desktop.ui.components

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap

fun safeImageBitmap(bytes: ByteArray?): ImageBitmap? {
	if (bytes == null || bytes.isEmpty()) return null
	return try {
		org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap()
	} catch (e: Throwable) {
		null
	}
}
