package org.masumjia.reactcartecom.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;

@Configuration
public class WebConfig {
    @Bean
    public CorsFilter corsFilter(@Value("${app.cors.allowed-origins}") String origins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        // Support both explicit origins and patterns (e.g., http://localhost:*, https://*.yourdomain.com, *)
        for (String entry : Arrays.asList(origins.split(","))) {
            String o = entry.trim();
            if (o.isEmpty()) continue;
            if (o.contains("*")) {
                config.addAllowedOriginPattern(o);
            } else {
                config.addAllowedOrigin(o);
            }
        }
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}
