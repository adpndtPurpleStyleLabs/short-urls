package com.preonsurl.apis.domain.controller;

import com.preonsurl.apis.auth.dto.AuthenticatedUser;
import com.preonsurl.apis.domain.dto.AddDomainRequest;
import com.preonsurl.apis.domain.dto.CustomDomainResponse;
import com.preonsurl.apis.domain.dto.DnsInstructionDto;
import com.preonsurl.apis.domain.dto.DomainAvailabilityResponse;
import com.preonsurl.apis.domain.dto.DomainVerificationResponse;
import com.preonsurl.apis.domain.service.CustomDomainService;
import com.preonsurl.apis.link.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@Tag(name = "Custom Domains", description = "Endpoints for managing and verifying custom branded domains via CNAME")
@RestController
@RequestMapping("/api/domains")
public class CustomDomainController {

    private final CustomDomainService customDomainService;

    public CustomDomainController(CustomDomainService customDomainService) {
        this.customDomainService = customDomainService;
    }

    @Operation(summary = "Add a new custom domain", description = "Registers a new custom domain for the user and checks CNAME configuration")
    @PostMapping
    public ResponseEntity<ApiResponse<CustomDomainResponse>> addDomain(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody AddDomainRequest request) {
        AuthenticatedUser currentUser = resolveUser(user);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("User not authenticated"));
        }

        try {
            CustomDomainResponse response = customDomainService.addDomain(currentUser.userId(), request.domain());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success(response, "Domain registered successfully"));
        } catch (IllegalArgumentException e) {
            if (e.getMessage().contains("already registered")) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(e.getMessage()));
            }
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @Operation(summary = "Check domain availability", description = "Checks whether a custom domain is already registered or available")
    @GetMapping("/check")
    public ResponseEntity<ApiResponse<DomainAvailabilityResponse>> checkDomainAvailability(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam String domain) {
        AuthenticatedUser currentUser = resolveUser(user);
        Long userId = (currentUser != null) ? currentUser.userId() : null;

        DomainAvailabilityResponse response = customDomainService.checkDomainAvailability(userId, domain);
        return ResponseEntity.ok(ApiResponse.success(response, response.message()));
    }

    @Operation(summary = "List registered domains", description = "Retrieves a paginated list of domains registered by the authenticated user")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<CustomDomainResponse>>> listDomains(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        AuthenticatedUser currentUser = resolveUser(user);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("User not authenticated"));
        }

        Page<CustomDomainResponse> page = customDomainService.listDomains(currentUser.userId(), pageable);
        return ResponseEntity.ok(ApiResponse.success(page, "Domains retrieved successfully"));
    }

    @Operation(summary = "Get default system domain", description = "Retrieves the system's default serving domain")
    @GetMapping("/default")
    public ResponseEntity<ApiResponse<CustomDomainResponse>> getDefaultDomain() {
        CustomDomainResponse response = customDomainService.getDefaultDomain();
        return ResponseEntity.ok(ApiResponse.success(response, "Default domain retrieved"));
    }

    @Operation(summary = "Get verified domains", description = "Retrieves the list of verified active custom domains for the user, plus the default domain")
    @GetMapping("/verified")
    public ResponseEntity<ApiResponse<List<CustomDomainResponse>>> getVerifiedDomains(
            @AuthenticationPrincipal AuthenticatedUser user) {
        AuthenticatedUser currentUser = resolveUser(user);
        Long userId = currentUser != null ? currentUser.userId() : null;
        List<CustomDomainResponse> verifiedDomains = customDomainService.listVerifiedDomains(userId);
        return ResponseEntity.ok(ApiResponse.success(verifiedDomains, "Verified domains retrieved"));
    }

    @Operation(summary = "Verify domain CNAME", description = "Checks the public DNS CNAME record for the domain against the required target")
    @PostMapping("/{id}/verify")
    public ResponseEntity<ApiResponse<DomainVerificationResponse>> verifyDomain(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id) {
        AuthenticatedUser currentUser = resolveUser(user);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("User not authenticated"));
        }

        try {
            DomainVerificationResponse response = customDomainService.verifyDomain(currentUser.userId(), id);
            return ResponseEntity.ok(ApiResponse.success(response, response.message()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        }
    }

    @Operation(summary = "Delete domain", description = "Removes a registered custom domain")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteDomain(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id) {
        AuthenticatedUser currentUser = resolveUser(user);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("User not authenticated"));
        }

        boolean deleted = customDomainService.deleteDomain(currentUser.userId(), id);
        if (!deleted) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("Domain not found or not owned by user"));
        }
        return ResponseEntity.ok(ApiResponse.success(null, "Domain deleted successfully"));
    }

    @Operation(summary = "Get DNS setup instructions", description = "Returns CNAME setup instructions for major DNS providers like Cloudflare and GoDaddy")
    @GetMapping("/instructions")
    public ResponseEntity<ApiResponse<List<DnsInstructionDto>>> getDnsInstructions() {
        List<DnsInstructionDto> instructions = customDomainService.getDnsInstructions();
        return ResponseEntity.ok(ApiResponse.success(instructions, "DNS instructions retrieved"));
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
