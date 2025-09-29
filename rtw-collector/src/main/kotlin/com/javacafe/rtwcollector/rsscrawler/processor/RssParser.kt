package com.javacafe.rtwcollector.rsscrawler.processor

import com.javacafe.rtwcollector.rsscrawler.model.NewsItem
import com.javacafe.rtwcollector.rsscrawler.model.RssParseResult
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Entities
import org.jsoup.parser.Parser
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets

@Component
class RssParser(
    private val ioDispatcher: CoroutineDispatcher
) {
    private val logger = KotlinLogging.logger { }

    suspend fun parseRssXml(xmlContent: String, source: RssSource): RssParseResult = withContext(ioDispatcher) {
        try {
            val document = Jsoup.parse(xmlContent, "", Parser.xmlParser())
            val items = document.select("item").map { item ->
                NewsItem(
                    title = item.select("title").text(),
                    link = item.select("link").text(),
                    pubDate = item.select("pubDate").text(),
                    author = item.select("author").text(),
                    category = item.select("category").text(),
                    htmlContent = extractAndCleanHtmlFromCdata(item.select("description").text())
                )
            }

            logger.info { "Parsed ${items.size} items from ${source.sourceName} - ${source.category}" }
            RssParseResult(source, items)
        } catch (e: Exception) {
            logger.error(e) { "Failed to parse RSS for ${source.sourceName} - ${source.category}" }
            RssParseResult(source, emptyList())
        }
    }

    private fun extractAndCleanHtmlFromCdata(cdata: String): String {
        val htmlContent = cdata.trim()
            .removePrefix("<![CDATA[")
            .removeSuffix("]]>")
            .trim()

        // HTML 엔티티 디코딩
        return Jsoup.parse(htmlContent).apply {
            outputSettings().escapeMode(Entities.EscapeMode.xhtml)
            outputSettings().charset(StandardCharsets.UTF_8)
        }.html()
    }

    private fun cleanText(text: String): String {
        // HTML 엔티티를 일반 텍스트로 변환
        return Jsoup.parse(text).text()
    }

}
