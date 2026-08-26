package chat.zerion.desktop.ui.components

import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.text.AnnotatedString

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Clipboard helper that copies a sensitive value and then clears it after a
 * timeout, so linkability artifacts (invite links, later vault passwords) don't
 * linger in the OS clipboard / clipboard-history managers indefinitely. The
 * clear only fires if the clipboard still holds the value we put there, so a
 * later copy by the user is never clobbered.
 */
object SecureClipboard {

	const val DEFAULT_CLEAR_SECONDS = 60

	fun copyWithAutoClear(
			clipboard: ClipboardManager,
			scope: CoroutineScope,
			text: String,
			clearAfterSeconds: Int = DEFAULT_CLEAR_SECONDS,
	) {
		clipboard.setText(AnnotatedString(text))
		scope.launch {
			delay(clearAfterSeconds * 1000L)
			val current = try {
				clipboard.getText()?.text
			} catch (e: Exception) {
				null
			}
			if (current == text) clipboard.setText(AnnotatedString(""))
		}
	}
}
