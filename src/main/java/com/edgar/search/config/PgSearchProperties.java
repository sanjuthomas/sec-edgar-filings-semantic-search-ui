package com.edgar.search.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.pgsearch")
public record PgSearchProperties(
        boolean enabled,
        String url,
        String username,
        String password
) {
    public PgSearchProperties {
        if (url == null) {
            url = "";
        }
        if (username == null) {
            username = "";
        }
        if (password == null) {
            password = "";
        }
    }
}
