package com.example.apiwatch.config;

import com.example.apiwatch.security.PublicDnsResolver;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.Timeout;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MonitoringHttpClientConfig {

    @Bean(destroyMethod = "close")
    public CloseableHttpClient monitoringHttpClient(
            PublicDnsResolver dnsResolver
    ) {
        ConnectionConfig connectionConfig = ConnectionConfig.custom()
                .setConnectTimeout(Timeout.ofSeconds(5))
                .setSocketTimeout(Timeout.ofSeconds(5))
                .build();

        var connectionManager =
                PoolingHttpClientConnectionManagerBuilder.create()
                        .setDnsResolver(dnsResolver)
                        .setDefaultConnectionConfig(connectionConfig)
                        .setMaxConnTotal(20)
                        .setMaxConnPerRoute(5)
                        .build();

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.ofSeconds(5))
                .setResponseTimeout(Timeout.ofSeconds(5))
                .build();

        return HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .setConnectionReuseStrategy(
                        (request, response, context) -> false
                )
                .disableRedirectHandling()
                .disableAutomaticRetries()
                .disableCookieManagement()
                .disableContentCompression()
                .build();
    }
}