package com.preonsurl.ui;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;

@Profile("app")
@ControllerAdvice
public class AppNotFoundExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(AppNotFoundExceptionHandler.class);

    @ExceptionHandler(NoResourceFoundException.class)
    public Object handleNoResourceFound(NoResourceFoundException ex, HttpServletRequest request) {
        String uri = request.getRequestURI();
        log.info("404 Not Found (App profile): uri='{}'", uri);

        String accept = request.getHeader("Accept");
        boolean isApi = (uri != null && (uri.startsWith("/api/") || uri.startsWith("/link/")))
                || (accept != null && accept.contains("application/json") && !accept.contains("text/html"));

        if (isApi) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "success", false,
                            "error", "Not Found",
                            "message", "Endpoint or resource not found: " + (uri != null ? uri : ""),
                            "status", 404,
                            "path", uri != null ? uri : ""
                    ));
        }

        boolean loggedIn = isUserLoggedIn(request);
        ModelAndView mav = new ModelAndView("404");
        mav.setStatus(HttpStatus.NOT_FOUND);
        mav.addObject("status", 404);
        mav.addObject("path", uri != null ? uri : "");
        mav.addObject("isLoggedIn", loggedIn);
        return mav;
    }

    private boolean isUserLoggedIn(HttpServletRequest request) {
        if (request == null) return false;
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ") && authHeader.length() > 7) {
            return true;
        }
        if (request.getCookies() != null) {
            for (Cookie c : request.getCookies()) {
                if ("preons_jwt".equals(c.getName()) && c.getValue() != null && !c.getValue().isBlank()) {
                    return true;
                }
            }
        }
        return false;
    }
}
