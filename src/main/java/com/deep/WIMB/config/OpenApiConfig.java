package com.deep.WIMB.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Metadata for the Swagger UI / OpenAPI docs (springdoc-openapi generates
 * the actual endpoint list automatically from the existing controllers and
 * DTOs -- this just controls the title/description shown at the top of the
 * page). Reachable at /swagger-ui.html, gated behind admin login -- see
 * SecurityConfig for why.
 */
@Configuration
public class OpenApiConfig {

    private static final String DRIVER_TOKEN_SCHEME = "DriverToken";

    @Bean
    public OpenAPI wimbOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Where Is My Bus API")
                        .description("""
                                Real-time bus tracking for North Bengal routes -- GPS ingestion \
                                from drivers, live tracking for passengers, and admin management \
                                of routes, depots and buses.

                                Most endpoints here are also reachable without any tooling at all \
                                by driver.html / track.html / admin-buses.html directly -- this \
                                page is mainly useful for testing an endpoint in isolation, or for \
                                anyone integrating against the API directly rather than through the \
                                existing frontend.

                                Driver-facing endpoints (/api/ride/**, /api/location/**) expect an \
                                X-Driver-Token header, issued by POST /api/driver/verify -- click \
                                Authorize below and paste a token in once rather than adding the \
                                header to every request by hand. Live location push is also \
                                available over WebSocket (STOMP over SockJS at /ws) rather than \
                                polling -- see the project README for topics.
                                """)
                        .version("v1")
                        .contact(new Contact()
                                .name("Deep Saha")
                                .url("https://github.com/Deep-Saha1925/Where-is-my-Bus")))
                // Not real Spring Security auth -- this endpoint's own controller
                // code checks X-Driver-Token manually (see DriverTokenService /
                // ActiveRideCache), Spring Security itself doesn't know about it.
                // This just gives Swagger's UI an "Authorize" button that fills
                // the header in for you on every "Try it out" call afterward.
                .components(new Components()
                        .addSecuritySchemes(DRIVER_TOKEN_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-Driver-Token")
                                .description("Token returned by POST /api/driver/verify")))
                .addSecurityItem(new SecurityRequirement().addList(DRIVER_TOKEN_SCHEME));
    }
}

