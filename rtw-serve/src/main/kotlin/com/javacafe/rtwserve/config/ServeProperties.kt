package com.javacafe.rtwserve.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "app")
class ServeProperties {
    val elasticsearch = ElasticsearchProps()
    val search = SearchProps()
    val keyword = KeywordProps()
    
    class ElasticsearchProps {
        var articleIndex: String = "rtw-articles"
        var keywordIndex: String = "rtw-keywords"
    }
    
    class SearchProps {
        var defaultPageSize: Int = 20
        var maxPageSize: Int = 100
        var highlightFragmentSize: Int = 150
    }
    
    class KeywordProps {
        var cachePrefix: String = "rtw:keyword:daily:"
        var topCount: Int = 10
    }
}
