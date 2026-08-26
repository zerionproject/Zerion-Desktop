package chat.zerion.desktop.ui

/**
 * Defence-in-depth input hygiene for user-entered names and identifiers.
 *
 * The desktop UI is not a web view and never interprets HTML/markup - Compose
 * renders every string literally, so there is no XSS surface - and the engine
 * stores everything through parameterised JDBC, never string-built SQL, so there
 * is no SQL-injection surface either. This helper is an extra guard: it strips
 * control characters (including NUL and bidi/zero-width tricks) from short
 * identifier fields and bounds their length, so nothing weird is ever persisted
 * or shown.
 */
internal fun sanitizeName(input: String, max: Int = 120): String {
	val cleaned = buildString(input.length) {
		for (ch in input) {
			if (ch == ' ') {
				append(ch)
			} else if (!ch.isISOControl() &&
					Character.getType(ch) != Character.FORMAT.toInt()) {
				append(ch)
			}
		}
	}
	return cleaned.trim().take(max)
}
