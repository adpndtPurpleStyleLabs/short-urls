package com.preonsurl.apis.auth;

import com.preonsurl.apis.auth.cache.UserCache;
import com.preonsurl.apis.auth.dto.LoginRequest;
import com.preonsurl.apis.auth.dto.LoginResponse;
import com.preonsurl.apis.auth.dto.RegisterRequest;
import com.preonsurl.apis.auth.dto.RegisterResponse;
import com.preonsurl.apis.auth.entity.Tenant;
import com.preonsurl.apis.auth.entity.User;
import com.preonsurl.apis.auth.repository.TenantRepository;
import com.preonsurl.apis.auth.repository.UserRepository;
import com.preonsurl.emailer.EmailService;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailService emailService;
    private final SecureRandom secureRandom = new SecureRandom();
    private final UserCache userCache;

    public AuthService(TenantRepository tenantRepository,
                       UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       UserCache userCache,
                       EmailService emailService) {
        this.passwordEncoder = passwordEncoder;
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.emailService = emailService;
        this.userCache = userCache;
    }

    private String generate6DigitCode() {
        int codeInt = secureRandom.nextInt(900_000) + 100_000;
        return String.valueOf(codeInt);
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String username = request.username().trim();
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already exists: " + username);
        }

        String email = request.email().trim();
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email address is already in use: " + email);
        }

        Tenant tenant = new Tenant(request.fullName().trim());
        tenant = tenantRepository.save(tenant);

        String verificationCode = generate6DigitCode();
        Instant codeExpiry = Instant.now().plus(15, ChronoUnit.MINUTES);

        User user = new User(
                tenant.getId(),
                request.fullName().trim(),
                username,
                passwordEncoder.encode(request.password()),
                email,
                false,
                verificationCode,
                codeExpiry
        );
        user = userRepository.save(user);

        // Dispatch verification code via Mailtrap emailer
        emailService.sendVerificationCode(user.getEmail(), verificationCode, user.getFullName());

        return new RegisterResponse(user.getUsername(), user.getEmail(), false, true);
    }

    public LoginResponse login(LoginRequest request) {
        if (request.username() == null || request.username().isBlank() || request.password() == null) {
            throw new BadCredentialsException("Username and password cannot be empty");
        }
        String identifier = request.username().trim();

        User user = userCache.getByEmail(identifier.toLowerCase());
        if(user == null){
            user = userRepository.findByUsernameOrEmail(identifier, identifier.toLowerCase())
                    .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid username or password");
        }

        if (!user.isVerified()) {
            return new LoginResponse(null, null, null, false, user.getEmail());
        }

        String token = jwtService.generateToken(user);
        userCache.put(user);
        return new LoginResponse(token, "Bearer", jwtService.getExpirationSeconds(), true, user.getEmail());
    }

    @Transactional
    public boolean verifyCode(String identifier, String code) {
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException("Identifier (email/username) cannot be empty");
        }
        if (code == null || code.isBlank() || !code.matches("^[0-9]{6}$")) {
            throw new IllegalArgumentException("Verification code must be exactly 6 digits");
        }
        String trimmedIdentifier = identifier.trim();
        User user = userRepository.findByUsernameOrEmail(trimmedIdentifier, trimmedIdentifier.toLowerCase())
                .orElseThrow(() -> new IllegalArgumentException("No account found for: " + trimmedIdentifier));

        if (user.isVerified()) {
            return true;
        }

        if (!user.isVerificationCodeValid(code)) {
            throw new IllegalArgumentException("Invalid or expired verification code");
        }

        user.setVerified(true);
        user.setVerificationCode(null);
        user.setVerificationCodeExpiresAt(null);
        userRepository.save(user);

        // Send welcome email upon successful account verification
        if (user.getEmail() != null && !user.getEmail().isBlank()) {
            String recipientName = (user.getFullName() != null && !user.getFullName().isBlank())
                    ? user.getFullName().trim()
                    : user.getUsername();
            try {
                emailService.sendWelcomeEmail(user.getEmail(), recipientName);
            } catch (Exception e) {
                log.warn("Failed to dispatch welcome email to '{}': {}", user.getEmail(), e.getMessage());
            }
        }

        return true;
    }

    @Transactional
    public void resendCode(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException("Identifier (email/username) cannot be empty");
        }
        String trimmedIdentifier = identifier.trim();
        User user = userRepository.findByUsernameOrEmail(trimmedIdentifier, trimmedIdentifier.toLowerCase())
                .orElseThrow(() -> new IllegalArgumentException("No account found for: " + trimmedIdentifier));

        if (user.isVerified()) {
            throw new IllegalStateException("Account is already verified");
        }

        String newCode = generate6DigitCode();
        user.setVerificationCode(newCode);
        user.setVerificationCodeExpiresAt(Instant.now().plus(15, ChronoUnit.MINUTES));
        userRepository.save(user);

        emailService.sendVerificationCode(user.getEmail(), newCode, user.getFullName());
    }

    @Transactional
    public void changeEmail(String currentIdentifier, String newEmail) {
        if (currentIdentifier == null || currentIdentifier.isBlank()) {
            throw new IllegalArgumentException("Current identifier cannot be empty");
        }
        if (newEmail == null || newEmail.isBlank() || !newEmail.contains("@")) {
            throw new IllegalArgumentException("Please provide a valid new email address");
        }
        String trimmedIdentifier = currentIdentifier.trim();
        String normalizedNewEmail = newEmail.trim().toLowerCase();

        User user = userRepository.findByUsernameOrEmail(trimmedIdentifier, trimmedIdentifier.toLowerCase())
                .orElseThrow(() -> new IllegalArgumentException("No account found for: " + trimmedIdentifier));

        if (userRepository.existsByEmail(normalizedNewEmail) && !normalizedNewEmail.equalsIgnoreCase(user.getEmail())) {
            throw new IllegalArgumentException("Email address is already in use: " + normalizedNewEmail);
        }

        String newCode = generate6DigitCode();
        user.setEmail(normalizedNewEmail);
        user.setVerified(false);
        user.setVerificationCode(newCode);
        user.setVerificationCodeExpiresAt(Instant.now().plus(15, ChronoUnit.MINUTES));
        userRepository.save(user);

        emailService.sendVerificationCode(normalizedNewEmail, newCode, user.getFullName());
    }
}
