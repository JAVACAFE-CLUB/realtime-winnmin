package com.javacafe.rtwcollector.filecrawler.processor

import com.ctc.wstx.stax.WstxInputFactory
import com.javacafe.rtwcollector.filecrawler.model.Contributor
import com.javacafe.rtwcollector.filecrawler.model.Revision
import com.javacafe.rtwcollector.filecrawler.model.WikiPage
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.springframework.stereotype.Service
import java.io.BufferedInputStream
import java.io.FileInputStream
import java.nio.file.Path
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamReader

@Service
class WikiXmlStreamParser {

    private val xmlInputFactory: XMLInputFactory by lazy {
        WstxInputFactory().apply {
            setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, false)
            setProperty(XMLInputFactory.IS_COALESCING, true)
            setProperty(XMLInputFactory.SUPPORT_DTD, false)
        }
    }

    companion object {
        private val logger = KotlinLogging.logger { }
        private const val BUFFER_SIZE = 65536
    }

    fun parsePages(filePath: Path): Flow<WikiPage> = flow {
        logger.info { "Starting to parse wiki file: $filePath" }

        BufferedInputStream(FileInputStream(filePath.toFile()), BUFFER_SIZE).use { inputStream ->
            val reader = xmlInputFactory.createXMLStreamReader(inputStream)

            try {
                var pageCount = 0
                while (reader.hasNext()) {
                    when (reader.eventType) {
                        XMLStreamConstants.START_ELEMENT -> {
                            if (reader.localName == "page") {
                                parsePage(reader)?.let {
                                    emit(it)
                                    pageCount++
                                    if (pageCount % 1000 == 0) {
                                        logger.debug { "Parsed $pageCount pages so far" }
                                    }
                                }
                            }
                        }
                    }
                    reader.next()
                }
                logger.info { "Completed parsing wiki file: $filePath (total pages: $pageCount)" }
            } finally {
                reader.close()
            }
        }
    }

    private fun parsePage(reader: XMLStreamReader): WikiPage? {
        var title: String? = null
        var pageId: String? = null
        var ns: String? = null
        var revision: Revision? = null

        while (reader.hasNext()) {
            when (reader.eventType) {
                XMLStreamConstants.START_ELEMENT -> {
                    when (reader.localName) {
                        "title" -> title = reader.elementText?.takeIf { it.isNotBlank() }
                        "id" -> if (pageId == null) pageId = reader.elementText?.takeIf { it.isNotBlank() }
                        "ns" -> ns = reader.elementText?.takeIf { it.isNotBlank() }
                        "revision" -> revision = parseRevision(reader)
                    }
                }
                XMLStreamConstants.END_ELEMENT -> {
                    if (reader.localName == "page") {
                        return if (title != null && pageId != null && revision != null) {
                            WikiPage(
                                title = title,
                                id = pageId,
                                ns = ns ?: "0",
                                revision = revision
                            )
                        } else {
                            logger.debug { "Skipping incomplete page: title=$title, id=$pageId" }
                            null
                        }
                    }
                }
            }
            reader.next()
        }
        return null
    }

    private fun parseRevision(reader: XMLStreamReader): Revision {
        var revisionId: String? = null
        var parentId: String? = null
        var timestamp: String? = null
        var contributor: Contributor? = null
        var text: String? = null
        var sha1: String? = null

        while (reader.hasNext()) {
            when (reader.eventType) {
                XMLStreamConstants.START_ELEMENT -> {
                    when (reader.localName) {
                        "id" -> if (revisionId == null) revisionId = reader.elementText?.takeIf { it.isNotBlank() }
                        "parentid" -> parentId = reader.elementText?.takeIf { it.isNotBlank() }
                        "timestamp" -> timestamp = reader.elementText?.takeIf { it.isNotBlank() }
                        "contributor" -> contributor = parseContributor(reader)
                        "text" -> text = reader.elementText ?: ""
                        "sha1" -> sha1 = reader.elementText?.takeIf { it.isNotBlank() }
                    }
                }
                XMLStreamConstants.END_ELEMENT -> {
                    if (reader.localName == "revision") {
                        return Revision(
                            id = revisionId ?: "",
                            parentId = parentId,
                            timestamp = timestamp ?: "",
                            contributor = contributor ?: Contributor.empty(),
                            text = text ?: "",
                            sha1 = sha1 ?: ""
                        )
                    }
                }
            }
            reader.next()
        }
        return Revision.empty()
    }

    private fun parseContributor(reader: XMLStreamReader): Contributor {
        var username: String? = null
        var ip: String? = null
        var id: String? = null

        while (reader.hasNext()) {
            when (reader.eventType) {
                XMLStreamConstants.START_ELEMENT -> {
                    when (reader.localName) {
                        "username" -> username = reader.elementText?.takeIf { it.isNotBlank() }
                        "ip" -> ip = reader.elementText?.takeIf { it.isNotBlank() }
                        "id" -> id = reader.elementText?.takeIf { it.isNotBlank() }
                    }
                }
                XMLStreamConstants.END_ELEMENT -> {
                    if (reader.localName == "contributor") {
                        return Contributor(username, ip, id)
                    }
                }
            }
            reader.next()
        }
        return Contributor.empty()
    }
}
