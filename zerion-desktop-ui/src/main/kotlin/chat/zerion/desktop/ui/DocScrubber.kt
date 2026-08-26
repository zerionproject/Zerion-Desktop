package chat.zerion.desktop.ui

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDDocumentInformation

import org.mp4parser.muxer.builder.DefaultMp4Builder
import org.mp4parser.muxer.container.mp4.MovieCreator

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.channels.Channels

/**
 * In-memory metadata scrubbers for document and video attachments. Nothing is
 * written to disk; the scrubbed bytes go straight to the encrypted DB.
 */
internal object DocScrubber {

	fun scrubPdf(input: ByteArray): ByteArray {
		PDDocument.load(input).use { doc ->
			doc.documentInformation = PDDocumentInformation()
			doc.documentCatalog.metadata = null
			val out = ByteArrayOutputStream()
			doc.save(out)
			return out.toByteArray()
		}
	}
}

internal object VideoScrubber {

	fun scrubMp4(file: File): ByteArray {
		val movie = MovieCreator.build(file.absolutePath)
		val container = DefaultMp4Builder().build(movie)
		val out = ByteArrayOutputStream()
		Channels.newChannel(out).use { channel ->
			container.writeContainer(channel)
		}
		return out.toByteArray()
	}
}
