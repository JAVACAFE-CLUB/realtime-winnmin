package com.javacafe.realtimewinnmin.infrastructure.config

import org.springframework.context.annotation.Configuration
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories

@Configuration
@EnableElasticsearchRepositories(
    basePackages = ["com.javacafe.realtimewinnmin.domain.*.repository"]
)
class ElasticsearchConfig