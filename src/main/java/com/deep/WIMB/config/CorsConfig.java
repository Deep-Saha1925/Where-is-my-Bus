/*
 * Where Is My Bus (WIMB)
 * Copyright (c) 2025-2026 Deep Saha. All rights reserved.
 * Proprietary and confidential. See the LICENSE file in the project root.
 * Unauthorized copying, modification, distribution, hosting or use is prohibited.
 */
package com.deep.WIMB.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;

    /**
     * Origins that may call the API from a different site, e.g. a front end served by VS Code
     * Live Server during development: CORS_ALLOWED_ORIGINS=http://localhost:5500,http://127.0.0.1:5500
     *
     * Empty (the default) means no cross-origin access at all, which is correct for the real
     * app: its pages are served by this same server, so browsers never need CORS for them.
     * Origins are listed explicitly -- "*" is rejected because these mappings allow credentials.
     */
    public CorsConfig(@Value("${wimb.cors.allowed-origins:}") String origins) {
        this.allowedOrigins = Arrays.stream(origins.split(","))
                .map(String::trim)
                .filter(o -> !o.isEmpty())
                .toArray(String[]::new);

        for (String origin : allowedOrigins) {
            if (origin.contains("*")) {
                throw new IllegalStateException(
                        "CORS_ALLOWED_ORIGINS must list exact origins (e.g. https://example.com); "
                                + "wildcards are not allowed. Offending value: " + origin);
            }
        }
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (allowedOrigins.length == 0) {
            return; // same-origin only
        }
        registry.addMapping("/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }

    // RouteExcelLoader writes the live stops.json to the external, writable
    // "data/" directory (same runtime-data folder as depot-codes.xlsx and
    // data/routes/) so it can be regenerated without a rebuild. Without this
    // mapping, a request for /data/stops.json falls through to Spring Boot's
    // default classpath:/static/** handler and serves the *build-time*
    // src/main/resources/static/data/stops.json instead — a completely
    // different, never-updated file. This mapping makes "/data/**" resolve
    // to the real runtime folder first, ahead of the default static handler.
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/data/**")
                .addResourceLocations("file:data/");
    }
}