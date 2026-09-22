package com.preonsurl.apis;

import com.preonsurl.apis.link.service.MirrorCssRewriter;
import com.preonsurl.apis.link.service.MirrorUrlResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

class MirrorCssRewriterTest {

    private MirrorCssRewriter cssRewriter;

    @BeforeEach
    void setUp() {
        MirrorUrlResolver resolver = new MirrorUrlResolver();
        cssRewriter = new MirrorCssRewriter(resolver);
    }

    @Test
    void testRewriteCssUrls() {
        String css = """
                @import url("/css/reset.css");
                @import "/css/typography.css";
                @import 'https://fonts.googleapis.com/css?family=Roboto';
                
                .header {
                    background: url('../images/logo.png');
                }
                .banner {
                    background-image: url("/images/hero.jpg");
                }
                .icon {
                    background: url(icons/arrow.svg);
                }
                .data-icon {
                    background: url("data:image/svg+xml;utf8,<svg></svg>");
                }
                .external {
                    background: url("https://cdn.example.com/asset.png");
                }
                """;

        URI cssDocUri = URI.create("https://www.ogaan.com/pub/static/frontend/main.css");
        String rewritten = cssRewriter.rewrite(css, "1BiVqa8OZJl", cssDocUri);

        // Root absolute /css/...
        assertTrue(rewritten.contains("url(\"/1BiVqa8OZJl/css/reset.css\")"));
        assertTrue(rewritten.contains("@import \"/1BiVqa8OZJl/css/typography.css\""));

        // Relative ../images/logo.png relative to /pub/static/frontend/main.css -> /pub/static/images/logo.png
        assertTrue(rewritten.contains("url('/1BiVqa8OZJl/pub/static/images/logo.png')"));

        // Absolute /images/hero.jpg
        assertTrue(rewritten.contains("url(\"/1BiVqa8OZJl/images/hero.jpg\")"));

        // Relative icons/arrow.svg relative to /pub/static/frontend/main.css -> /pub/static/frontend/icons/arrow.svg
        assertTrue(rewritten.contains("url(\"/1BiVqa8OZJl/pub/static/frontend/icons/arrow.svg\")")
                || rewritten.contains("url(/1BiVqa8OZJl/pub/static/frontend/icons/arrow.svg)"));

        // Data URI must remain untouched
        assertTrue(rewritten.contains("data:image/svg+xml;utf8,<svg></svg>"));

        // External Google fonts and CDN must remain untouched
        assertTrue(rewritten.contains("https://fonts.googleapis.com/css?family=Roboto"));
        assertTrue(rewritten.contains("https://cdn.example.com/asset.png"));
    }
}
