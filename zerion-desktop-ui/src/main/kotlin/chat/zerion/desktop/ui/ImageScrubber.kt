package chat.zerion.desktop.ui

import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

/**
 * Strips ALL metadata from an image and normalises it for sending. The image is
 * decoded to raw pixels and re-encoded as a fresh JPEG: EXIF, GPS location,
 * timestamps, device make/model and every other ancillary tag are dropped by
 * reconstruction (the decoder keeps only pixels, and the JPEG writer emits no
 * EXIF). Everything happens in memory - no temp files, so no plaintext image is
 * ever written to disk. Also downscales oversized images to keep well under the
 * engine's attachment cap.
 */
internal object ImageScrubber {

	const val OUTPUT_CONTENT_TYPE = "image/jpeg"

	private const val MAX_DIMENSION = 2048
	private const val JPEG_QUALITY = 0.85f

	class UnsupportedImageException(message: String) : Exception(message)

	fun scrubToJpeg(input: ByteArray): ByteArray {
		val source: BufferedImage = ByteArrayInputStream(input).use {
			ImageIO.read(it)
		} ?: throw UnsupportedImageException(
				"That file isn't a supported image.")

		val scaled = downscale(source)
		val rgb = BufferedImage(scaled.width, scaled.height,
				BufferedImage.TYPE_INT_RGB)
		val g = rgb.createGraphics()
		g.color = java.awt.Color.WHITE
		g.fillRect(0, 0, rgb.width, rgb.height)
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
				RenderingHints.VALUE_INTERPOLATION_BILINEAR)
		g.drawImage(scaled, 0, 0, null)
		g.dispose()

		return encodeJpeg(rgb)
	}

	private fun downscale(image: BufferedImage): BufferedImage {
		val max = maxOf(image.width, image.height)
		if (max <= MAX_DIMENSION) return image
		val ratio = MAX_DIMENSION.toDouble() / max
		val w = (image.width * ratio).toInt().coerceAtLeast(1)
		val h = (image.height * ratio).toInt().coerceAtLeast(1)
		val out = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
		val g = out.createGraphics()
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
				RenderingHints.VALUE_INTERPOLATION_BILINEAR)
		g.setRenderingHint(RenderingHints.KEY_RENDERING,
				RenderingHints.VALUE_RENDER_QUALITY)
		g.drawImage(image, 0, 0, w, h, null)
		g.dispose()
		return out
	}

	private fun encodeJpeg(image: BufferedImage): ByteArray {
		val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
		val out = ByteArrayOutputStream()
		try {
			ImageIO.createImageOutputStream(out).use { ios ->
				writer.output = ios
				val param = writer.defaultWriteParam
				if (param.canWriteCompressed()) {
					param.compressionMode = ImageWriteParam.MODE_EXPLICIT
					param.compressionQuality = JPEG_QUALITY
				}
				writer.write(null, IIOImage(image, null, null), param)
			}
		} finally {
			writer.dispose()
		}
		return out.toByteArray()
	}
}
