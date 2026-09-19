package com.preonsurl.apis.apikey;

import com.preonsurl.apis.apikey.dto.ApiKeyResponse;
import com.preonsurl.apis.apikey.dto.CreateApiKeyRequest;
import com.preonsurl.apis.apikey.dto.CreateApiKeyResponse;
import com.preonsurl.apis.auth.dto.AuthenticatedUser;
import com.preonsurl.apis.link.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@Tag(name = "API Key Management", description = "Endpoints for creating, viewing, and deleting user API keys")
@RestController
@RequestMapping({ "/api/apikey" })
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    public ApiKeyController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @Operation(summary = "Create or rotate API key", description = "Creates a new API key for the authenticated user and automatically deactivates any previous API keys, enforcing at most 1 active API key per user")
    @PostMapping
    public ResponseEntity<ApiResponse<CreateApiKeyResponse>> createApiKey(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody(required = false) CreateApiKeyRequest request) {
        AuthenticatedUser currentUser = resolveUser(user);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("User not authenticated"));
        }

        String name = (request != null) ? request.name() : null;
        CreateApiKeyResponse response = apiKeyService.createApiKey(currentUser.userId(), name);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "API key created successfully"));
    }

    @Operation(summary = "Get active API key", description = "Retrieves the active API key metadata for the authenticated user")
    @GetMapping
    public ResponseEntity<ApiResponse<ApiKeyResponse>> getActiveApiKey(
            @AuthenticationPrincipal AuthenticatedUser user) {
        AuthenticatedUser currentUser = resolveUser(user);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("User not authenticated"));
        }

        ApiKeyResponse response = apiKeyService.getActiveApiKey(currentUser.userId());
        if (response == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("No active API key found"));
        }
        return ResponseEntity.ok(ApiResponse.success(response, "Active API key retrieved"));
    }

    @Operation(summary = "Delete active API key", description = "Deactivates the currently active API key for the authenticated user")
    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> deleteActiveApiKey(
            @AuthenticationPrincipal AuthenticatedUser user) {
        AuthenticatedUser currentUser = resolveUser(user);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("User not authenticated"));
        }

        boolean deleted = apiKeyService.deleteActiveApiKey(currentUser.userId());
        if (!deleted) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("No active API key found to delete"));
        }
        return ResponseEntity.ok(ApiResponse.success(null, "Active API key deleted successfully"));
    }

    @Operation(summary = "Delete API key by ID", description = "Deactivates a specific API key owned by the authenticated user")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteApiKeyById(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id) {
        AuthenticatedUser currentUser = resolveUser(user);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("User not authenticated"));
        }

        boolean deleted = apiKeyService.deleteApiKeyById(id, currentUser.userId());
        if (!deleted) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("API key not found or not owned by user"));
        }
        return ResponseEntity.ok(ApiResponse.success(null, "API key deleted successfully"));
    }

    private AuthenticatedUser resolveUser(AuthenticatedUser user) {
        if (user != null) {
            return user;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser authUser) {
            return authUser;
        }
        return null;
    }
}
