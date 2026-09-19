package com.preonsurl.apis.auth;

import com.preonsurl.apis.auth.dto.RegisterRequest;
import com.preonsurl.apis.auth.dto.RegisterResponse;
import com.preonsurl.apis.auth.entity.Tenant;
import com.preonsurl.apis.auth.entity.User;
import com.preonsurl.apis.auth.repository.TenantRepository;
import com.preonsurl.apis.auth.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.preonsurl.apis.auth.dto.LoginRequest;
import com.preonsurl.apis.auth.dto.LoginResponse;
import org.springframework.security.authentication.BadCredentialsException;

@Service
public class AuthService {
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(TenantRepository tenantRepository,
                       UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.passwordEncoder = passwordEncoder;
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String username = request.username().trim();
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already exists: " + username);
        }
        Tenant tenant = new Tenant(request.fullName().trim());
        tenant = tenantRepository.save(tenant);
        User user = new User(tenant.getId(), request.fullName().trim(), username, passwordEncoder.encode(request.password()));
        user = userRepository.save(user);
        return new RegisterResponse(user.getUsername());
    }

    public LoginResponse login(LoginRequest request) {
        if (request.username() == null || request.username().isBlank() || request.password() == null) {
            throw new BadCredentialsException("Username and password cannot be empty");
        }
        String username = request.username().trim();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid username or password");
        }

        String token = jwtService.generateToken(user);
        return new LoginResponse(token, "Bearer", jwtService.getExpirationSeconds());
    }
}
