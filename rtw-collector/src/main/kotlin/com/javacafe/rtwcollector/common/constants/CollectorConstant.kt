package com.javacafe.rtwcollector.common.constants

object CollectorConstant {

    const val CRAWL_TYPE_WIKI_FILE = "WIKI-FILE"
    const val CRAWL_TYPE_NEWS_RSS = "NEWS_RSS"
    const val CRAWL_TYPE_TWITTER_API = "TWITTER_API"

    const val WIKI_CRAWL_PREFIX = "wiki"
    const val WIKI_KAFKA_TOPIC = "wiki-chunks"
    const val WIKI_CHUNK_PER_SIZE = 20


    const val RSS_CRAWL_PREFIX = "rss"
    const val RSS_KAFKA_TOPIC = "rss-chunks"
    const val RSS_MAX_CONCURRENT_CRAWLS = 3
    const val RSS_CHUNK_SIZE = 10



    const val TWITTER_CRAWL_PREFIX = "twitter"
    const val TWITTER_KAFKA_TOPIC = "twitter-chunks"

}
