package com.chatviewer.blog.config;

import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "my-conf")
public class ESConfig {
    /**
     * 配置ES的初始化连接
     * @return
     */
    @Value("${my-conf.es-server}")
    private String EsServer;

    @Bean
    public RestHighLevelClient client() {
        RestHighLevelClient client = new RestHighLevelClient(
                RestClient.builder(new HttpHost(EsServer, 9200, "http"))
        );
        return client;
    }

}
