package com.ayanami.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Elasticsearch 客户端配置。构造时不会连接 ES，未启用时也不创建 Bean。
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "hm.es.enabled", havingValue = "true", matchIfMissing = true)
public class ElasticsearchConfig {

    @Value("${hm.es.host:http://127.0.0.1:9200}")
    private String host;

    @Value("${hm.es.username:}")
    private String username;

    @Value("${hm.es.password:}")
    private String password;

    @Bean
    public RestHighLevelClient restHighLevelClient() {
        // 解析 host，如 http://127.0.0.1:9200
        String scheme = "http";
        String hostPort = host;
        int idx = host.indexOf("://");
        if (idx > 0) {
            scheme = host.substring(0, idx);
            hostPort = host.substring(idx + 3);
        }
        String[] hp = hostPort.split(":");
        HttpHost httpHost = new HttpHost(hp[0], hp.length > 1 ? Integer.parseInt(hp[1]) : 9200, scheme);

        RestClientBuilder builder = RestClient.builder(httpHost);
        if (username != null && !username.isEmpty()) {
            // 配置了账号则使用 Basic Auth
            CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(AuthScope.ANY,
                    new UsernamePasswordCredentials(username, password == null ? "" : password));
            builder.setHttpClientConfigCallback(httpClientBuilder ->
                    httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider));
        }
        RestHighLevelClient client = new RestHighLevelClient(builder);
        log.info("Elasticsearch client 初始化完成, host={}", host);
        return client;
    }
}
