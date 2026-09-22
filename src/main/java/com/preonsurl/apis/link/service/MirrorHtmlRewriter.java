package com.preonsurl.apis.link.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Rewrites HTML documents for MIRROR mode using Jsoup.
 * Resolves attributes against the upstream document URI and rewrites same-origin
 * resources to the PreonsURL mirror namespace (/{shortCode}/*).
 */
@Component
public class MirrorHtmlRewriter {

    private static final Map<String, String> TAG_ATTR_MAP = Map.ofEntries(
            Map.entry("a", "href"),
            Map.entry("link", "href"),
            Map.entry("img", "src"),
            Map.entry("script", "src"),
            Map.entry("source", "src"),
            Map.entry("video", "src"),
            Map.entry("audio", "src"),
            Map.entry("iframe", "src"),
            Map.entry("form", "action"),
            Map.entry("input", "src"),
            Map.entry("object", "data"),
            Map.entry("embed", "src")
    );

    private final MirrorUrlResolver urlResolver;
    private final MirrorCssRewriter cssRewriter;

    public MirrorHtmlRewriter(MirrorUrlResolver urlResolver, MirrorCssRewriter cssRewriter) {
        this.urlResolver = urlResolver;
        this.cssRewriter = cssRewriter;
    }

    /**
     * Rewrites all relevant URLs in the given HTML string.
     *
     * @param html          The raw upstream HTML content
     * @param shortCode     The short code identifying the mirror link
     * @param currentDocUri The URI of the current upstream document
     * @return The rewritten HTML content
     */
    public String rewrite(String html, String shortCode, URI currentDocUri) {
        if (html == null || html.isBlank()) {
            return html;
        }

        Document doc = Jsoup.parse(html, currentDocUri.toString());
        doc.outputSettings().prettyPrint(false);

        // If upstream has <base href="...">, extract it to resolve relative links, then remove
        // so browser does not bypass PreonsURL mirror routing
        Elements baseTags = doc.select("base[href]");
        URI effectiveDocUri = currentDocUri;
        if (!baseTags.isEmpty()) {
            String baseHref = baseTags.first().attr("href");
            try {
                effectiveDocUri = currentDocUri.resolve(baseHref);
            } catch (Exception ignored) {
            }
            baseTags.remove();
        }

        // Rewrite standard tag attributes
        for (Map.Entry<String, String> entry : TAG_ATTR_MAP.entrySet()) {
            String tag = entry.getKey();
            String attr = entry.getValue();
            Elements elements = doc.select(tag + "[" + attr + "]");
            for (Element el : elements) {
                String val = el.attr(attr);
                if (val != null && !val.isBlank()) {
                    String rewritten = urlResolver.toMirrorUrl(shortCode, effectiveDocUri, val);
                    el.attr(attr, rewritten);
                }
            }
        }

        // Rewrite srcset attributes on <img> and <source>
        Elements srcsetElements = doc.select("img[srcset], source[srcset]");
        for (Element el : srcsetElements) {
            String srcset = el.attr("srcset");
            if (srcset != null && !srcset.isBlank()) {
                String rewritten = rewriteSrcset(srcset, shortCode, effectiveDocUri);
                el.attr("srcset", rewritten);
            }
        }

        // Rewrite embedded <style> tags
        Elements styleTags = doc.select("style");
        for (Element style : styleTags) {
            String css = style.data();
            if (css != null && !css.isBlank()) {
                String rewritten = cssRewriter.rewrite(css, shortCode, effectiveDocUri);
                style.text(rewritten);
            }
        }

        // Rewrite inline style attributes
        Elements styledElements = doc.select("[style]");
        for (Element el : styledElements) {
            String inlineStyle = el.attr("style");
            if (inlineStyle != null && inlineStyle.contains("url(")) {
                String rewritten = cssRewriter.rewrite(inlineStyle, shortCode, effectiveDocUri);
                el.attr("style", rewritten);
            }
        }

        // Rewrite inline scripts that contain literal references to the upstream origin
        String scheme = effectiveDocUri.getScheme() != null ? effectiveDocUri.getScheme() : "https";
        String host = effectiveDocUri.getHost();
        if (host != null && !host.isBlank()) {
            int port = effectiveDocUri.getPort();
            String portPart = (port > 0 && port != 80 && port != 443) ? ":" + port : "";
            String upstreamOrigin = scheme + "://" + host + portPart;
            String mirrorPrefix = "/" + shortCode;

            Elements inlineScripts = doc.select("script:not([src])");
            for (Element script : inlineScripts) {
                String data = script.data();
                if (data != null && !data.isBlank() && data.contains(upstreamOrigin)) {
                    script.text(data.replace(upstreamOrigin, mirrorPrefix));
                }
            }

            // Inject client-side fetch and XMLHttpRequest interceptor to prevent browser CORS violations
            injectClientInterceptor(doc, shortCode, upstreamOrigin, host, mirrorPrefix);
        }

        return doc.outerHtml();
    }

    private void injectClientInterceptor(Document doc, String shortCode, String upstreamOrigin, String upstreamHost, String mirrorPrefix) {
        String interceptorJs = String.format("""
                (function() {
                    var upstreamOrigin = '%s';
                    var upstreamHost = '%s';
                    var mirrorPrefix = '%s';
                    function rewriteUrl(url) {
                        if (!url || typeof url !== 'string') return url;
                        if (url.indexOf(upstreamOrigin) === 0) {
                            return mirrorPrefix + url.substring(upstreamOrigin.length);
                        }
                        if (url.indexOf('//' + upstreamHost) === 0) {
                            return mirrorPrefix + url.substring(('//' + upstreamHost).length);
                        }
                        if (url.charAt(0) === '/' && url.indexOf(mirrorPrefix + '/') !== 0 && url !== mirrorPrefix) {
                            return mirrorPrefix + url;
                        }
                        return url;
                    }
                    if (window.fetch) {
                        var _origFetch = window.fetch;
                        window.fetch = function(input, init) {
                            if (typeof input === 'string') {
                                input = rewriteUrl(input);
                            } else if (input && typeof input.url === 'string') {
                                var rewritten = rewriteUrl(input.url);
                                if (rewritten !== input.url) {
                                    try { input = new Request(rewritten, input); } catch (e) {}
                                }
                            }
                            return _origFetch.call(this, input, init);
                        };
                    }
                    if (window.XMLHttpRequest) {
                        var _origOpen = window.XMLHttpRequest.prototype.open;
                        window.XMLHttpRequest.prototype.open = function(method, url, async, user, password) {
                            var rewritten = rewriteUrl(url);
                            return _origOpen.call(this, method, rewritten, async, user, password);
                        };
                    }
                    if (navigator.sendBeacon) {
                        var _origBeacon = navigator.sendBeacon;
                        navigator.sendBeacon = function(url, data) {
                            return _origBeacon.call(this, rewriteUrl(url), data);
                        };
                    }
                })();
                """, upstreamOrigin, upstreamHost, mirrorPrefix);

        Element scriptTag = doc.createElement("script");
        scriptTag.attr("id", "__preons_mirror_interceptor");
        scriptTag.text(interceptorJs);

        if (doc.head() != null) {
            doc.head().prependChild(scriptTag);
        } else if (doc.body() != null) {
            doc.body().prependChild(scriptTag);
        }
    }

    private String rewriteSrcset(String srcset, String shortCode, URI docUri) {
        String[] candidates = srcset.split(",");
        List<String> rewrittenCandidates = new ArrayList<>();

        for (String candidate : candidates) {
            String trimmed = candidate.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            int spaceIdx = trimmed.indexOf(' ');
            if (spaceIdx > 0) {
                String url = trimmed.substring(0, spaceIdx).trim();
                String descriptor = trimmed.substring(spaceIdx).trim();
                String rewrittenUrl = urlResolver.toMirrorUrl(shortCode, docUri, url);
                rewrittenCandidates.add(rewrittenUrl + " " + descriptor);
            } else {
                String rewrittenUrl = urlResolver.toMirrorUrl(shortCode, docUri, trimmed);
                rewrittenCandidates.add(rewrittenUrl);
            }
        }

        return String.join(", ", rewrittenCandidates);
    }
}
