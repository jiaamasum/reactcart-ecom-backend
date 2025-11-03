package org.masumjia.reactcartecom.security;

import org.masumjia.reactcartecom.user.User;
import org.masumjia.reactcartecom.user.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {
    private final UserRepository users;

    public CustomUserDetailsService(UserRepository users) { this.users = users; }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // username = email
        User u = users.findByEmail(username).orElseThrow(() -> new UsernameNotFoundException("User not found"));
        if (u.isBanned()) {
            throw new UsernameNotFoundException("User is banned");
        }
        return org.springframework.security.core.userdetails.User
                .withUsername(u.getEmail())
                .password(u.getPasswordHash())
                .roles(u.getRole())
                .build();
    }
}

