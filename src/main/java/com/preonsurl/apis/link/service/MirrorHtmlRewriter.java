package com.preonsurl.apis.link.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.DataNode;
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
        return rewrite(html, shortCode, currentDocUri, new String[0]);
    }

    public String rewrite(String html, String shortCode, URI currentDocUri, String... additionalTokens) {
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
                    script.empty();
                    script.appendChild(new DataNode(data.replace(upstreamOrigin, mirrorPrefix)));
                }
            }

            // Inject client-side fetch and XMLHttpRequest interceptor to prevent browser CORS violations
            injectClientInterceptor(doc, shortCode, upstreamOrigin, host, mirrorPrefix, additionalTokens);
        }

        return doc.outerHtml();
    }

    private void injectClientInterceptor(Document doc, String shortCode, String upstreamOrigin, String upstreamHost, String mirrorPrefix, String... additionalTokens) {
        java.util.Set<String> tokenSet = new java.util.LinkedHashSet<>();
        if (shortCode != null && !shortCode.isBlank()) {
            String clean = shortCode.trim();
            while (clean.startsWith("/")) clean = clean.substring(1);
            while (clean.endsWith("/")) clean = clean.substring(0, clean.length() - 1);
            if (!clean.isBlank()) {
                tokenSet.add(clean);
                if (clean.contains("/")) {
                    for (String part : clean.split("/")) {
                        String p = part.trim();
                        if (!p.isBlank()) tokenSet.add(p);
                    }
                }
            }
        }
        if (additionalTokens != null) {
            for (String tok : additionalTokens) {
                if (tok != null && !tok.isBlank()) {
                    String clean = tok.trim();
                    while (clean.startsWith("/")) clean = clean.substring(1);
                    while (clean.endsWith("/")) clean = clean.substring(0, clean.length() - 1);
                    if (!clean.isBlank()) {
                        tokenSet.add(clean);
                        if (clean.contains("/")) {
                            for (String part : clean.split("/")) {
                                String p = part.trim();
                                if (!p.isBlank()) tokenSet.add(p);
                            }
                        }
                    }
                }
            }
        }

        java.util.List<String> sortedTokens = tokenSet.stream()
                .sorted((a, b) -> Integer.compare(b.length(), a.length()))
                .toList();

        StringBuilder tokensJson = new StringBuilder("[");
        for (int i = 0; i < sortedTokens.size(); i++) {
            if (i > 0) tokensJson.append(",");
            tokensJson.append("\"").append(sortedTokens.get(i).replace("\"", "\\\"")).append("\"");
        }
        tokensJson.append("]");

        String template = """
                (function() {
                    var upstreamOrigin = '__UPSTREAM_ORIGIN__';
                    var upstreamHost = '__UPSTREAM_HOST__';
                    var mirrorPrefix = '__MIRROR_PREFIX__';
                    var tokens = __TOKENS_JSON__;

                    function cleanQuery(qs) {
                        if (!qs || typeof qs !== 'string') return qs;
                        for (var i = 0; i < tokens.length; i++) {
                            var tok = tokens[i];
                            if (!tok) continue;
                            var sc = tok.replace(/[^a-zA-Z0-9_\\-]/g, '\\\\$&');
                            // 1. key equals token alone (root page) -> "home"
                            qs = qs.replace(new RegExp('("key"\\\\s*:\\\\s*")/?' + sc + '/?(")', 'g'), '$1home$2');
                            qs = qs.replace(new RegExp('(%22key%22\\\\s*(?::|%3A)\\\\s*(?:%22|"))(?:/|%2F)?' + sc + '(?:/|%2F)?((?:%22|"))', 'gi'), '$1home$2');

                            // 2. key with subpath: "key":"/token/path" -> "key":"/path"
                            qs = qs.replace(new RegExp('("key"\\\\s*:\\\\s*")/?' + sc + '/', 'g'), '$1/');
                            qs = qs.replace(new RegExp('(%22key%22\\\\s*(?::|%3A)\\\\s*(?:%22|"))(?:/|%2F)?' + sc + '(%2F|/)', 'gi'), '$1$2');

                            // 3. Any /token/ in query string -> /
                            qs = qs.replace(new RegExp('/' + sc + '/', 'g'), '/');
                            qs = qs.replace(new RegExp('%2F' + sc + '%2F', 'gi'), '%2F');
                            qs = qs.replace(new RegExp('%2F' + sc + '/', 'gi'), '/');
                            qs = qs.replace(new RegExp('/' + sc + '%2F', 'gi'), '%2F');

                            // 4. Any standalone /token at end of value
                            qs = qs.replace(new RegExp('/' + sc + '(?=[&"\\'\\\\}\\]]|%22|%27|%7D|$)', 'g'), '/');
                            qs = qs.replace(new RegExp('%2F' + sc + '(?=[&"\\'\\\\}\\]]|%22|%27|%7D|$)', 'gi'), '%2F');
                        }

                        // 5. If key was left as root "/" or empty "", map to "home"
                        qs = qs.replace(/("key"\\s*:\\s*")\\/+(")/g, '$1home$2');
                        qs = qs.replace(/(%22key%22\\s*(?::|%3A)\\s*(?:%22|"))(?:%2F|\\/)+((?:%22|"))/gi, '$1home$2');

                        return qs;
                    }

                    function rewriteUrl(url) {
                        if (!url || typeof url !== 'string') return url;

                        var hashIdx = url.indexOf('#');
                        var hash = '';
                        var baseAndQuery = url;
                        if (hashIdx >= 0) {
                            hash = url.substring(hashIdx);
                            baseAndQuery = url.substring(0, hashIdx);
                        }

                        var qIdx = baseAndQuery.indexOf('?');
                        var base = qIdx >= 0 ? baseAndQuery.substring(0, qIdx) : baseAndQuery;
                        var query = qIdx >= 0 ? baseAndQuery.substring(qIdx) : '';

                        if (base.indexOf(upstreamOrigin) === 0) {
                            base = mirrorPrefix + base.substring(upstreamOrigin.length);
                        } else if (base.indexOf('//' + upstreamHost) === 0) {
                            base = mirrorPrefix + base.substring(('//' + upstreamHost).length);
                        } else if (base.charAt(0) === '/' && base.indexOf(mirrorPrefix + '/') !== 0 && base !== mirrorPrefix) {
                            base = mirrorPrefix + base;
                        }

                        if (query) {
                            query = cleanQuery(query);
                        }

                        return base + query + hash;
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
                    if (window.history && window.history.pushState) {
                        var _origPushState = window.history.pushState;
                        window.history.pushState = function(state, title, url) {
                            if (typeof url === 'string') {
                                url = rewriteUrl(url);
                            }
                            return _origPushState.call(this, state, title, url);
                        };
                    }
                    if (window.history && window.history.replaceState) {
                        var _origReplaceState = window.history.replaceState;
                        window.history.replaceState = function(state, title, url) {
                            if (typeof url === 'string') {
                                url = rewriteUrl(url);
                            }
                            return _origReplaceState.call(this, state, title, url);
                        };
                    }
                })();
                """;

        String interceptorJs = template
                .replace("__UPSTREAM_ORIGIN__", upstreamOrigin)
                .replace("__UPSTREAM_HOST__", upstreamHost)
                .replace("__MIRROR_PREFIX__", mirrorPrefix)
                .replace("__TOKENS_JSON__", tokensJson.toString());

        Element scriptTag = doc.createElement("script");
        scriptTag.attr("id", "__preons_mirror_interceptor");
        scriptTag.appendChild(new DataNode(interceptorJs));

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
