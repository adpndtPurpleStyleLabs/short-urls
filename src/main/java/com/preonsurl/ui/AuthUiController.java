package com.preonsurl.ui;

import com.preonsurl.apis.auth.AuthController;
import com.preonsurl.apis.auth.dto.*;
import com.preonsurl.apis.link.dto.ApiResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Set;

@Profile("app")
@Controller
public class AuthUiController {

    private static final Logger log = LoggerFactory.getLogger(AuthUiController.class);

    private final AuthController authController;
    private final Validator validator;

    public AuthUiController(AuthController authController, Validator validator) {
        this.authController = authController;
        this.validator = validator;
    }

    @GetMapping("/register")
    public String showRegisterPage(Model model) {
        return "register";
    }

    @GetMapping("/login")
    public String showLoginPage(
            @RequestParam(value = "registered", required = false, defaultValue = "false") boolean registered,
            @RequestParam(value = "verified", required = false, defaultValue = "false") boolean verified,
            @RequestParam(value = "error", required = false) String error,
            Model model) {
        model.addAttribute("registered", registered);
        model.addAttribute("verified", verified);
        if (error != null && !error.isBlank()) {
            model.addAttribute("error", error);
        }
        return "login";
    }

    @GetMapping({"/console", "/console/**"})
    public String showConsolePage() {
        return "console";
    }

    @GetMapping("/404")
    public String showNotFoundPage(jakarta.servlet.http.HttpServletRequest request, Model model) {
        model.addAttribute("status", 404);
        model.addAttribute("path", "/404");
        model.addAttribute("message", "The page or route you are looking for does not exist on this server.");
        boolean loggedIn = false;
        if (request != null && request.getCookies() != null) {
            for (jakarta.servlet.http.Cookie c : request.getCookies()) {
                if ("preons_jwt".equals(c.getName()) && c.getValue() != null && !c.getValue().isBlank()) {
                    loggedIn = true;
                    break;
                }
            }
        }
        model.addAttribute("isLoggedIn", loggedIn);
        return "404";
    }

    @PostMapping("/register")
    public String handleRegister(
            @RequestParam("fullName") String fullName,
            @RequestParam("username") String username,
            @RequestParam(value = "email", required = false) String email,
            @RequestParam("password") String password,
            Model model,
            RedirectAttributes redirectAttributes) {
        try {
            String cleanEmail = (email != null && !email.isBlank())
                    ? email.trim()
                    : ((username != null ? username.trim() : "user") + "@example.com");

            RegisterRequest request = new RegisterRequest(
                    fullName != null ? fullName.trim() : "",
                    username != null ? username.trim() : "",
                    cleanEmail,
                    password
            );

            if (validator != null) {
                Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);
                if (!violations.isEmpty()) {
                    String errorMsg = violations.iterator().next().getMessage();
                    model.addAttribute("error", errorMsg);
                    model.addAttribute("fullName", fullName);
                    model.addAttribute("username", username);
                    model.addAttribute("email", email);
                    return "register";
                }
            }

            ResponseEntity<ApiResponse<RegisterResponse>> response = authController.register(request);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null && response.getBody().success()) {
                log.info("User registered successfully via UI: username='{}', redirecting to /verification", username);
                RegisterResponse registerData = response.getBody().data();
                String targetEmail = (registerData != null && registerData.email() != null)
                        ? registerData.email()
                        : cleanEmail;
                redirectAttributes.addAttribute("email", targetEmail);
                return "redirect:/verification";
            } else {
                String errorMsg = (response.getBody() != null && response.getBody().message() != null)
                        ? response.getBody().message()
                        : "Registration failed. Please check your inputs.";
                model.addAttribute("error", errorMsg);
                model.addAttribute("fullName", fullName);
                model.addAttribute("username", username);
                model.addAttribute("email", email);
                return "register";
            }
        } catch (Exception e) {
            log.error("Exception during registration for username='{}'", username, e);
            model.addAttribute("error", e.getMessage() != null ? e.getMessage() : "Registration failed.");
            model.addAttribute("fullName", fullName);
            model.addAttribute("username", username);
            model.addAttribute("email", email);
            return "register";
        }
    }

    @PostMapping("/login")
    public String handleLogin(
            @RequestParam("username") String username,
            @RequestParam("password") String password,
            HttpServletResponse httpResponse,
            RedirectAttributes redirectAttributes,
            Model model) {
        try {
            LoginRequest request = new LoginRequest(
                    username != null ? username.trim() : "",
                    password
            );

            ResponseEntity<ApiResponse<LoginResponse>> response = authController.login(request);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null && response.getBody().success()) {
                LoginResponse loginData = response.getBody().data();

                // If user account is not verified, redirect to /verification
                if (loginData != null && !loginData.verified()) {
                    log.info("User '{}' attempted login but is not verified. Redirecting to /verification.", username);
                    String redirectEmail = (loginData.email() != null && !loginData.email().isBlank())
                            ? loginData.email()
                            : username;
                    redirectAttributes.addAttribute("email", redirectEmail);
                    return "redirect:/verification";
                }

                if (loginData != null && loginData.accessToken() != null) {
                    Cookie cookie = new Cookie("preons_jwt", loginData.accessToken());
                    cookie.setHttpOnly(true);
                    cookie.setPath("/");
                    cookie.setMaxAge((int) (loginData.expiresIn() != null ? loginData.expiresIn() / 1000 : 86400));
                    httpResponse.addCookie(cookie);
                }
                log.info("User logged in successfully via UI: username='{}'", username);
                model.addAttribute("loginSuccess", true);
                model.addAttribute("token", loginData != null ? loginData.accessToken() : "");
                model.addAttribute("username", username);
                return "login";
            } else {
                String errorMsg = (response.getBody() != null && response.getBody().message() != null)
                        ? response.getBody().message()
                        : "Invalid username or password.";
                model.addAttribute("error", errorMsg);
                model.addAttribute("username", username);
                return "login";
            }
        } catch (Exception e) {
            log.error("Exception during login for username='{}'", username, e);
            model.addAttribute("error", "Invalid username or password.");
            model.addAttribute("username", username);
            return "login";
        }
    }

    @GetMapping("/verification")
    public String showVerificationPage(
            @RequestParam(value = "email", required = false) String email,
            @RequestParam(value = "username", required = false) String username,
            @RequestParam(value = "error", required = false) String error,
            @RequestParam(value = "message", required = false) String message,
            Model model) {
        String effectiveEmail = (email != null && !email.isBlank()) ? email.trim() : "";
        if (effectiveEmail.isBlank() && username != null && !username.isBlank()) {
            effectiveEmail = username.trim();
        }
        model.addAttribute("email", effectiveEmail);
        if (error != null && !error.isBlank()) {
            model.addAttribute("error", error);
        }
        if (message != null && !message.isBlank()) {
            model.addAttribute("message", message);
        }
        return "verification";
    }

    @PostMapping("/verification")
    public String handleVerification(
            @RequestParam("email") String email,
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "digit1", required = false) String digit1,
            @RequestParam(value = "digit2", required = false) String digit2,
            @RequestParam(value = "digit3", required = false) String digit3,
            @RequestParam(value = "digit4", required = false) String digit4,
            @RequestParam(value = "digit5", required = false) String digit5,
            @RequestParam(value = "digit6", required = false) String digit6,
            Model model,
            RedirectAttributes redirectAttributes) {
        String finalCode = code;
        if (finalCode == null || finalCode.isBlank()) {
            finalCode = (digit1 != null ? digit1 : "") +
                        (digit2 != null ? digit2 : "") +
                        (digit3 != null ? digit3 : "") +
                        (digit4 != null ? digit4 : "") +
                        (digit5 != null ? digit5 : "") +
                        (digit6 != null ? digit6 : "");
        }
        finalCode = finalCode.trim();

        try {
            ResponseEntity<ApiResponse<Boolean>> response = authController.verifyCode(new VerifyCodeRequest(email, finalCode));
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null && response.getBody().success()) {
                log.info("User verified successfully via UI for email='{}'", email);
                redirectAttributes.addAttribute("verified", "true");
                return "redirect:/login";
            } else {
                String errorMsg = (response.getBody() != null && response.getBody().message() != null)
                        ? response.getBody().message()
                        : "Invalid verification code.";
                model.addAttribute("error", errorMsg);
                model.addAttribute("email", email);
                return "verification";
            }
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage() != null ? e.getMessage() : "Verification failed.");
            model.addAttribute("email", email);
            return "verification";
        }
    }

    @PostMapping("/verification/resend")
    public String handleResendCode(
            @RequestParam("email") String email,
            RedirectAttributes redirectAttributes) {
        try {
            ResponseEntity<ApiResponse<Boolean>> response = authController.resendCode(new ResendCodeRequest(email));
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null && response.getBody().success()) {
                redirectAttributes.addAttribute("email", email);
                redirectAttributes.addAttribute("message", "A new verification code has been dispatched to your email.");
            } else {
                String errorMsg = (response.getBody() != null && response.getBody().message() != null)
                        ? response.getBody().message()
                        : "Failed to resend verification code.";
                redirectAttributes.addAttribute("email", email);
                redirectAttributes.addAttribute("error", errorMsg);
            }
            return "redirect:/verification";
        } catch (Exception e) {
            redirectAttributes.addAttribute("email", email);
            redirectAttributes.addAttribute("error", e.getMessage() != null ? e.getMessage() : "Failed to resend code.");
            return "redirect:/verification";
        }
    }

    @PostMapping("/verification/change-email")
    public String handleChangeEmail(
            @RequestParam("currentEmail") String currentEmail,
            @RequestParam("newEmail") String newEmail,
            RedirectAttributes redirectAttributes) {
        try {
            ResponseEntity<ApiResponse<Boolean>> response = authController.changeEmail(new ChangeEmailRequest(currentEmail, newEmail));
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null && response.getBody().success()) {
                redirectAttributes.addAttribute("email", newEmail.trim());
                redirectAttributes.addAttribute("message", "Email updated. Verification code sent to " + newEmail.trim());
            } else {
                String errorMsg = (response.getBody() != null && response.getBody().message() != null)
                        ? response.getBody().message()
                        : "Failed to update email.";
                redirectAttributes.addAttribute("email", currentEmail);
                redirectAttributes.addAttribute("error", errorMsg);
            }
            return "redirect:/verification";
        } catch (Exception e) {
            redirectAttributes.addAttribute("email", currentEmail);
            redirectAttributes.addAttribute("error", e.getMessage() != null ? e.getMessage() : "Failed to update email.");
            return "redirect:/verification";
        }
    }
}
