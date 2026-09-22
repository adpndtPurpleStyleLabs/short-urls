package com.preonsurl.apis.auth;

import com.preonsurl.apis.auth.dto.*;
import com.preonsurl.apis.link.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Authentication", description = "Endpoints for user registration, verification, and JWT login")
@RequestMapping("/api/auth")
@RestController
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "Register a new user", description = "Creates a new user and tenant account, and sends email verification code")
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<RegisterResponse>> register(@Valid @RequestBody RegisterRequest request) {
        try {
            RegisterResponse response = authService.register(request);
            return ResponseEntity.ok(ApiResponse.success(response, "User registered successfully. Verification code dispatched."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @Operation(summary = "Login user", description = "Authenticates user and returns JWT access token or verification requirement")
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@RequestBody LoginRequest request) {
        try {
            LoginResponse response = authService.login(request);
            if (!response.verified()) {
                return ResponseEntity.ok(ApiResponse.success(response, "Email verification required before accessing your account"));
            }
            return ResponseEntity.ok(ApiResponse.success(response, "Login successful"));
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(e.getMessage()));
        }
    }

    @Operation(summary = "Verify email with 6-digit code", description = "Validates the 6-digit verification code and activates user account")
    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<Boolean>> verifyCode(@Valid @RequestBody VerifyCodeRequest request) {
        try {
            String identifier = (request.email() != null && !request.email().isBlank())
                    ? request.email()
                    : request.username();
            authService.verifyCode(identifier, request.code());
            return ResponseEntity.ok(ApiResponse.success(true, "Email verified successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage(), false));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to verify code: " + e.getMessage(), false));
        }
    }

    @Operation(summary = "Resend verification code", description = "Generates and emails a fresh 6-digit verification code")
    @PostMapping("/resend-code")
    public ResponseEntity<ApiResponse<Boolean>> resendCode(@Valid @RequestBody ResendCodeRequest request) {
        try {
            String identifier = (request.email() != null && !request.email().isBlank())
                    ? request.email()
                    : request.username();
            authService.resendCode(identifier);
            return ResponseEntity.ok(ApiResponse.success(true, "A fresh verification code has been dispatched to your email"));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage(), false));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to resend verification code: " + e.getMessage(), false));
        }
    }

    @Operation(summary = "Change email for pending verification", description = "Updates email for unverified user and sends new code")
    @PostMapping("/change-email")
    public ResponseEntity<ApiResponse<Boolean>> changeEmail(@Valid @RequestBody ChangeEmailRequest request) {
        try {
            authService.changeEmail(request.currentIdentifier(), request.newEmail());
            return ResponseEntity.ok(ApiResponse.success(true, "Email updated successfully. Verification code dispatched to new address."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage(), false));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to change email: " + e.getMessage(), false));
        }
    }
}