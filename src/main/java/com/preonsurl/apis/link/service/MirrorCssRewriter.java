package com.preonsurl.apis.link.service;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rewrites CSS stylesheets for MIRROR mode.
 * Resolves url(...) and @import statements relative to the CSS document URI
 * and rewrites same-origin references into PreonsURL mirror URLs.
 */
@Component
public class MirrorCssRewriter {

    // Matches url('...'), url("..."), url(...)
    private static final Pattern URL_PATTERN = Pattern.compile(
            "url\\(\\s*(['\"]?)(.*?)\\1\\s*\\)",
            Pattern.CASE_INSENSITIVE
    );

    // Matches @import "..." and @import '...' (when not using url(...))
    private static final Pattern IMPORT_STRING_PATTERN = Pattern.compile(
            "@import\\s+(['\"])(.*?)\\1",
            Pattern.CASE_INSENSITIVE
    );

    private final MirrorUrlResolver urlResolver;

    public MirrorCssRewriter(MirrorUrlResolver urlResolver) {
        this.urlResolver = urlResolver;
    }

    /**
     * Rewrites all resource URLs in the given CSS content.
     *
     * @param css           The raw CSS content from upstream
     * @param shortCode     The short code identifying the mirror link
     * @param currentDocUri The URI of the CSS stylesheet
     * @return The rewritten CSS content
     */
    public String rewrite(String css, String shortCode, URI currentDocUri) {
        if (css == null || css.isBlank()) {
            return css;
        }

        // 1. Rewrite url(...)
        Matcher urlMatcher = URL_PATTERN.matcher(css);
        StringBuilder sb = new StringBuilder();
        while (urlMatcher.find()) {
            String quote = urlMatcher.group(1);
            String rawUrl = urlMatcher.group(2);

            if (rawUrl != null && !rawUrl.isBlank() && !isIgnoredCssUrl(rawUrl)) {
                String rewritten = urlResolver.toMirrorUrl(shortCode, currentDocUri, rawUrl.trim());
                String replacement = "url(" + quote + Matcher.quoteReplacement(rewritten) + quote + ")";
                urlMatcher.appendReplacement(sb, replacement);
            } else {
                urlMatcher.appendReplacement(sb, Matcher.quoteReplacement(urlMatcher.group(0)));
            }
        }
        urlMatcher.appendTail(sb);
        String afterUrls = sb.toString();

        // 2. Rewrite standalone @import "..."
        Matcher importMatcher = IMPORT_STRING_PATTERN.matcher(afterUrls);
        StringBuilder sbImport = new StringBuilder();
        while (importMatcher.find()) {
            String quote = importMatcher.group(1);
            String rawUrl = importMatcher.group(2);

            if (rawUrl != null && !rawUrl.isBlank() && !isIgnoredCssUrl(rawUrl)) {
                String rewritten = urlResolver.toMirrorUrl(shortCode, currentDocUri, rawUrl.trim());
                String replacement = "@import " + quote + Matcher.quoteReplacement(rewritten) + quote;
                importMatcher.appendReplacement(sbImport, replacement);
            } else {
                importMatcher.appendReplacement(sbImport, Matcher.quoteReplacement(importMatcher.group(0)));
            }
        }
        importMatcher.appendTail(sbImport);

        return sbImport.toString();
    }

    private boolean isIgnoredCssUrl(String url) {
        String trimmed = url.trim().toLowerCase();
        return trimmed.startsWith("data:") ||
                trimmed.startsWith("#") ||
                trimmed.startsWith("javascript:");
    }
}
