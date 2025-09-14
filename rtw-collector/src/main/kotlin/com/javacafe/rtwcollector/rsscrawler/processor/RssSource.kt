package com.javacafe.rtwcollector.rsscrawler.processor

enum class RssSource(
    val sourceName: String,
    val category: String,
    val url: String,
    val code: String,
    val enabled: Boolean
) {
    KMIB_POLITICS("국민일보", "정치", "https://www.kmib.co.kr/rss/data/kmibPolRss.xml", "km", true),
    KMIB_ECONOMY("국민일보", "경제", "https://www.kmib.co.kr/rss/data/kmibEcoRss.xml", "km", true),
    KMIB_SOCIETY("국민일보", "사회", "https://www.kmib.co.kr/rss/data/kmibSocRss.xml", "km", true),
    KMIB_INTERNATIONAL("국민일보", "국제", "https://www.kmib.co.kr/rss/data/kmibIntRss.xml", "km", true),
    KMIB_ENTERTAINMENT("국민일보", "연예", "https://www.kmib.co.kr/rss/data/kmibEntRss.xml", "km", true),
    KMIB_SPORTS("국민일보", "스포츠", "https://www.kmib.co.kr/rss/data/kmibSpoRss.xml", "km", true);

    companion object {
        fun getEnabledSources() = RssSource.entries.filter { it.enabled }
    }
}
