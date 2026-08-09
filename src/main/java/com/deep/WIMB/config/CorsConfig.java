package com.deep.WIMB.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(
                        "http://127.0.0.1:5500",
                        "http://localhost:5500"
                )
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