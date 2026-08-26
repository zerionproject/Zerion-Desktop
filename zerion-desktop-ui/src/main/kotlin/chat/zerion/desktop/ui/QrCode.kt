package chat.zerion.desktop.ui

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream

import javax.imageio.ImageIO

/**
 * Generates a QR code PNG (in memory) for a string such as a wallet address.
 */
internal object QrCode {

	fun pngFor(text: String, size: Int = 320): ByteArray {
		val hints = mapOf(
				EncodeHintType.MARGIN to 1,
				EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M)
		val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size,
				size, hints)
		val black = 0xFF000000.toInt()
		val white = 0xFFFFFFFF.toInt()
		val img = BufferedImage(size, size, BufferedImage.TYPE_INT_RGB)
		for (x in 0 until size) {
			for (y in 0 until size) {
				img.setRGB(x, y, if (matrix.get(x, y)) black else white)
			}
		}
		val out = ByteArrayOutputStream()
		ImageIO.write(img, "png", out)
		return out.toByteArray()
	}
}
