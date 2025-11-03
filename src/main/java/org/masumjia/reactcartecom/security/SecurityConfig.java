package org.masumjia.reactcartecom.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;
    private final RestAuthEntryPoint restAuthEntryPoint;
    private final RestAccessDeniedHandler restAccessDeniedHandler;

    private final CustomUserDetailsService userDetailsService;
    public SecurityConfig(JwtAuthenticationFilter jwtFilter, RestAuthEntryPoint restAuthEntryPoint,
                          CustomUserDetailsService userDetailsService, RestAccessDeniedHandler restAccessDeniedHandler) {
        this.jwtFilter = jwtFilter;
        this.restAuthEntryPoint = restAuthEntryPoint;
        this.userDetailsService = userDetailsService;
        this.restAccessDeniedHandler = restAccessDeniedHandler;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(c -> c.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint(restAuthEntryPoint)
                        .accessDeniedHandler(restAccessDeniedHandler)
                )
                .authorizeHttpRequests(reg -> reg
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login", "/api/auth/reset-password", "/api/auth/logout").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/products/**", "/api/categories/**", "/api/settings", "/api/coupons/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/carts/**").permitAll()
                        .requestMatchers(HttpMethod.PATCH, "/api/carts/**").permitAll()
                        .requestMatchers(HttpMethod.DELETE, "/api/carts/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/carts/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/orders").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/orders/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/me/orders").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/me/orders/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/me/orders").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/me/orders/**").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/api/me/orders/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/me/cart").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/api/me/cart/summary").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/me/cart/merge").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/me/cart/apply-coupon").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/me/cart/coupon").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/coupons/**").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .authenticationProvider(daoAuthenticationProvider())
                .logout(l -> l
                        .logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler(logoutSuccessResponder())
                )
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public DaoAuthenticationProvider daoAuthenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public org.springframework.web.cors.CorsConfigurationSource corsConfigurationSource() {
        org.springframework.web.cors.CorsConfiguration cfg = new org.springframework.web.cors.CorsConfiguration();
        cfg.setAllowCredentials(false);
        cfg.addAllowedOriginPattern("*");
        cfg.addAllowedHeader("*");
        cfg.addAllowedMethod("*");
        cfg.addExposedHeader("*");
        org.springframework.web.cors.UrlBasedCorsConfigurationSource src = new org.springframework.web.cors.UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/**", cfg);
        return src;
    }

    @Bean
    public org.springframework.security.web.authentication.logout.LogoutSuccessHandler logoutSuccessResponder() {
        return (request, response, authentication) -> {
            response.setStatus(200);
            response.setContentType("application/json");
            String body = new com.fasterxml.jackson.databind.ObjectMapper()
                    .writeValueAsString(org.masumjia.reactcartecom.common.ApiResponse.success(null, java.util.Map.of("message", "Logged out successfully")));
            response.getWriter().write(body);
        };
    }
}
