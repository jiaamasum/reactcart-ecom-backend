package org.masumjia.reactcartecom.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.masumjia.reactcartecom.user.User;
import org.masumjia.reactcartecom.user.UserRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Optional;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = null;
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader != null) { String ah = authHeader.trim(); if (ah.length() > 7 && ah.substring(0,7).equalsIgnoreCase("Bearer ")) { token = ah.substring(7); } }
        if (token != null) {
            try {
                Claims claims = jwtService.parse(token);
                String userId = claims.getSubject();
                if (userId != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    Optional<User> u = userRepository.findById(userId);
                    if (u.isPresent() && !u.get().isBanned()) {
                        User user = u.get();
                        UserDetails details = org.springframework.security.core.userdetails.User
                                .withUsername(user.getId())
                                .password(user.getPasswordHash())
                                .roles(user.getRole())
                                .build();
                        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
                        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    }
                }
            } catch (Exception ignored) {}
        }
        filterChain.doFilter(request, response);
    }
}
