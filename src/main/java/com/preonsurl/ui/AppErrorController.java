package com.preonsurl.ui;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import java.util.Map;

@Profile("app")
@Controller
public class AppErrorController implements ErrorController {

    private static final Logger log = LoggerFactory.getLogger(AppErrorController.class);

    @RequestMapping("/error")
    public Object handleError(HttpServletRequest request, HttpServletResponse response) {
        int status = response.getStatus();
        Object statusObj = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        if (statusObj instanceof Integer) {
            status = (Integer) statusObj;
        } else if (status < 400) {
            status = HttpStatus.NOT_FOUND.value();
        }

        String requestUri = (String) request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        if (requestUri == null || requestUri.isBlank()) {
            requestUri = request.getRequestURI();
        }

        log.info("AppErrorController: status={} uri='{}'", status, requestUri);

        String accept = request.getHeader("Accept");
        boolean isApi = (requestUri != null && (requestUri.startsWith("/api/") || requestUri.startsWith("/link/")))
                || (accept != null && accept.contains("application/json") && !accept.contains("text/html"));

        HttpStatus httpStatus = HttpStatus.resolve(status);
        if (httpStatus == null) {
            httpStatus = HttpStatus.NOT_FOUND;
        }

        if (isApi) {
            String message = (String) request.getAttribute(RequestDispatcher.ERROR_MESSAGE);
            if (message == null || message.isBlank()) {
                Throwable ex = (Throwable) request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
                if (ex != null && ex.getMessage() != null && !ex.getMessage().isBlank()) {
                    message = ex.getMessage();
                } else if (httpStatus == HttpStatus.NOT_FOUND) {
                    message = "Endpoint or resource not found: " + (requestUri != null ? requestUri : "");
                } else {
                    message = httpStatus.getReasonPhrase();
                }
            }
            return ResponseEntity.status(httpStatus)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "success", false,
                            "error", httpStatus.getReasonPhrase(),
                            "message", message,
                            "status", status,
                            "path", requestUri != null ? requestUri : ""
                    ));
        }

        boolean loggedIn = isUserLoggedIn(request);
        ModelAndView mav = new ModelAndView("404");
        mav.setStatus(httpStatus);
        mav.addObject("status", status);
        mav.addObject("path", requestUri != null ? requestUri : "");
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
