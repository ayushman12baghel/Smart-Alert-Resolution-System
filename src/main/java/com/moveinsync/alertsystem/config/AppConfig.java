package com.moveinsync.alertsystem.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

/**
 * Central Spring configuration.
 *
 * {@code @EnableAsync} is required for {@code @Async} on AuditListener; without it,
 * history writes would block the HTTP request thread.
 *
 * PasswordEncoder is defined here (not in SecurityConfig) to break the circular
 * dependency: SecurityConfig → JwtAuthFilter → UserDetailsService → SecurityConfig.
 */
@Configuration
@EnableAsync
@EnableCaching
@EnableScheduling
@EnableConfigurationProperties(RuleProperties.class)
public class AppConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {
        var admin = User.builder()
                .username("admin")
                .password(passwordEncoder.encode("admin123"))
                .roles("ADMIN")
                .build();
        return new InMemoryUserDetailsManager(admin);
    }
}
