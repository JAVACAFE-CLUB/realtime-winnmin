package com.javacafe.rtwserve.config

import org.springframework.context.annotation.Configuration
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories

@Configuration
@EnableElasticsearchRepositories(
    basePackages = ["com.javacafe.rtwserve.domain.*.repository"]
)
class ElasticsearchConfig