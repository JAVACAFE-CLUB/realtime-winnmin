package com.javacafe.rtwserving.config

import org.springframework.context.annotation.Configuration
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories

@Configuration
@EnableElasticsearchRepositories(
    basePackages = ["com.javacafe.rtwserving.domain.*.repository"]
)
class ElasticsearchConfig