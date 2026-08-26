package chat.zerion.desktop.ui

import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.image.BufferedImage

/**
 * Native desktop notifications via the system tray. Notifications are kept
 * deliberately minimal - a sender name, never message content - so nothing
 * sensitive is handed to the OS notification layer. No-op on platforms without
 * a system tray.
 */
internal class DesktopNotifier {

	private var trayIcon: TrayIcon? = null

	fun install() {
		if (trayIcon != null || !SystemTray.isSupported()) return
		try {
			val icon = TrayIcon(makeIcon(), "Zerion")
			icon.isImageAutoSize = true
			SystemTray.getSystemTray().add(icon)
			trayIcon = icon
		} catch (ignored: Exception) {
		}
	}

	fun notify(title: String, text: String, sound: Boolean = false) {
		try {
			val type = if (sound) TrayIcon.MessageType.INFO
					else TrayIcon.MessageType.NONE
			trayIcon?.displayMessage(title, text, type)
		} catch (ignored: Exception) {
		}
	}

	fun remove() {
		trayIcon?.let {
			try {
				SystemTray.getSystemTray().remove(it)
			} catch (ignored: Exception) {
			}
		}
		trayIcon = null
	}

	private fun makeIcon(): BufferedImage {
		val size = 16
		val img = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
		val g = img.createGraphics()
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
				RenderingHints.VALUE_ANTIALIAS_ON)
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
				RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
		g.color = Color(0x7C, 0x63, 0xF0)
		g.fillRoundRect(0, 0, size, size, 5, 5)
		g.color = Color.WHITE
		g.font = Font("SansSerif", Font.BOLD, 12)
		val fm = g.fontMetrics
		val z = "Z"
		val x = (size - fm.stringWidth(z)) / 2
		val y = (size - fm.height) / 2 + fm.ascent
		g.drawString(z, x, y)
		g.dispose()
		return img
	}
}
