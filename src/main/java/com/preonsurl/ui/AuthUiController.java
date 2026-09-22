package com.preonsurl.ui;

import com.preonsurl.apis.auth.AuthController;
import com.preonsurl.apis.auth.dto.LoginRequest;
import com.preonsurl.apis.auth.dto.LoginResponse;
import com.preonsurl.apis.auth.dto.RegisterRequest;
import com.preonsurl.apis.auth.dto.RegisterResponse;
import com.preonsurl.apis.link.dto.ApiResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Set;

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
            @RequestParam(value = "error", required = false) String error,
            Model model) {
        model.addAttribute("registered", registered);
        if (error != null && !error.isBlank()) {
            model.addAttribute("error", error);
        }
        return "login";
    }

    @PostMapping("/register")
    public String handleRegister(
            @RequestParam("fullName") String fullName,
            @RequestParam("username") String username,
            @RequestParam("password") String password,
            Model model,
            RedirectAttributes redirectAttributes) {
        try {
            RegisterRequest request = new RegisterRequest(
                    fullName != null ? fullName.trim() : "",
                    username != null ? username.trim() : "",
                    password
            );

            if (validator != null) {
                Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);
                if (!violations.isEmpty()) {
                    String errorMsg = violations.iterator().next().getMessage();
                    model.addAttribute("error", errorMsg);
                    model.addAttribute("fullName", fullName);
                    model.addAttribute("username", username);
                    return "register";
                }
            }

            ResponseEntity<ApiResponse<RegisterResponse>> response = authController.register(request);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null && response.getBody().success()) {
                log.info("User registered successfully via UI: username='{}'", username);
                redirectAttributes.addAttribute("registered", "true");
                return "redirect:/login";
            } else {
                String errorMsg = (response.getBody() != null && response.getBody().message() != null)
                        ? response.getBody().message()
                        : "Registration failed. Please check your inputs.";
                model.addAttribute("error", errorMsg);
                model.addAttribute("fullName", fullName);
                model.addAttribute("username", username);
                return "register";
            }
        } catch (Exception e) {
            log.error("Exception during registration for username='{}'", username, e);
            model.addAttribute("error", e.getMessage() != null ? e.getMessage() : "Registration failed.");
            model.addAttribute("fullName", fullName);
            model.addAttribute("username", username);
            return "register";
        }
    }

    @PostMapping("/login")
    public String handleLogin(
            @RequestParam("username") String username,
            @RequestParam("password") String password,
            HttpServletResponse httpResponse,
            Model model) {
        try {
            LoginRequest request = new LoginRequest(
                    username != null ? username.trim() : "",
                    password
            );

            ResponseEntity<ApiResponse<LoginResponse>> response = authController.login(request);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null && response.getBody().success()) {
                LoginResponse loginData = response.getBody().data();
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
}
