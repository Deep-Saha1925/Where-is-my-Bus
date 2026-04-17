package com.deep.WIMB.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${wimb.admin.username}")
    private String adminUsername;

    @Value("${wimb.admin.password}")
    private String adminPassword;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        // Admin user loaded from application.properties — no database needed
        var admin = User.builder()
                .username(adminUsername)
                .password(passwordEncoder().encode(adminPassword))
                .roles("ADMIN")
                .build();

        return new InMemoryUserDetailsManager(admin);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        // ── Public pages (passengers & drivers) ──
                        .requestMatchers(
                                "/",
                                "/index",
                                "/track",
                                "/driver",
                                "/csrf-token",
                                "/js/**",
                                "/data/**",
                                "/images/**",
                                "/css/**"
                        ).permitAll()

                        // ── Public APIs (passengers & drivers need these) ──
                        .requestMatchers(
                                "/api/ride/active",
                                "/api/ride/start",
                                "/api/ride/location",
                                "/api/ride/cancel/**",
                                "/api/location/**",
                                "/api/routes/**"
                        ).permitAll()

                        // ── Admin pages & APIs — login required ──
                        .requestMatchers(
                                "/admin-buses.html",
                                "/admin/**",
                                "/api/ride/active/all"
                        ).hasRole("ADMIN")
                        // ── Everything else requires login ──
                        .anyRequest().authenticated()
                )

                // Use Spring's built-in login form
                .formLogin(form -> form
                        .loginPage("/login.html")        // our custom login page
                        .loginProcessingUrl("/do-login") // Spring processes this URL
                        .defaultSuccessUrl("/admin/active/all-buses", true)
                        .failureUrl("/login.html?error=true")
                        .permitAll()
                )

                .logout(logout -> logout
                        .logoutSuccessUrl("/login.html?logout=true")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll()
                )

                // Disable CSRF only for driver API (GPS posts from mobile)
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers(
                                "/do-login",
                                "/api/ride/**",
                                "/api/location/**"
                        )
                );

        return http.build();
    }
}