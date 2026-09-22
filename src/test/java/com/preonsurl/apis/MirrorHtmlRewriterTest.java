package com.preonsurl.apis;

import com.preonsurl.apis.link.service.MirrorCssRewriter;
import com.preonsurl.apis.link.service.MirrorHtmlRewriter;
import com.preonsurl.apis.link.service.MirrorUrlResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

class MirrorHtmlRewriterTest {

    private MirrorHtmlRewriter htmlRewriter;

    @BeforeEach
    void setUp() {
        MirrorUrlResolver resolver = new MirrorUrlResolver();
        MirrorCssRewriter cssRewriter = new MirrorCssRewriter(resolver);
        htmlRewriter = new MirrorHtmlRewriter(resolver, cssRewriter);
    }

    @Test
    void testRewriteHtmlTags() {
        String html = """
                <!DOCTYPE html>
                <html>
                <head>
                    <link rel="stylesheet" href="/pub/static/frontend.css">
                    <script src="/pub/static/main.js"></script>
                </head>
                <body>
                    <a href="/sale/dresses?sort=price">Sale Dresses</a>
                    <img src="pub/media/banner.jpg" alt="Banner">
                    <form action="/checkout/cart" method="get">
                        <input type="image" src="/pub/static/submit.png">
                    </form>
                    <video src="/media/promo.mp4">
                        <source src="/media/promo.webm" type="video/webm">
                    </video>
                    <audio src="/media/sound.mp3"></audio>
                    <iframe src="/embedded/frame"></iframe>
                </body>
                </html>
                """;

        URI currentUri = URI.create("https://www.ogaan.com/sale");
        String rewritten = htmlRewriter.rewrite(html, "1BiVqa8OZJl", currentUri);

        assertTrue(rewritten.contains("href=\"/1BiVqa8OZJl/pub/static/frontend.css\""));
        assertTrue(rewritten.contains("src=\"/1BiVqa8OZJl/pub/static/main.js\""));
        assertTrue(rewritten.contains("href=\"/1BiVqa8OZJl/sale/dresses?sort=price\""));
        assertTrue(rewritten.contains("src=\"/1BiVqa8OZJl/pub/media/banner.jpg\""));
        assertTrue(rewritten.contains("action=\"/1BiVqa8OZJl/checkout/cart\""));
        assertTrue(rewritten.contains("src=\"/1BiVqa8OZJl/pub/static/submit.png\""));
        assertTrue(rewritten.contains("src=\"/1BiVqa8OZJl/media/promo.mp4\""));
        assertTrue(rewritten.contains("src=\"/1BiVqa8OZJl/media/promo.webm\""));
        assertTrue(rewritten.contains("src=\"/1BiVqa8OZJl/media/sound.mp3\""));
        assertTrue(rewritten.contains("src=\"/1BiVqa8OZJl/embedded/frame\""));
    }

    @Test
    void testThirdPartyUrlsUntouched() {
        String html = """
                <html>
                <body>
                    <a href="https://www.google.com/search">Google</a>
                    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5/dist/js/bootstrap.min.js"></script>
                    <img src="https://images.unsplash.com/photo-1">
                </body>
                </html>
                """;

        URI currentUri = URI.create("https://www.ogaan.com/sale");
        String rewritten = htmlRewriter.rewrite(html, "1BiVqa8OZJl", currentUri);

        assertTrue(rewritten.contains("href=\"https://www.google.com/search\""));
        assertTrue(rewritten.contains("src=\"https://cdn.jsdelivr.net/npm/bootstrap@5/dist/js/bootstrap.min.js\""));
        assertTrue(rewritten.contains("src=\"https://images.unsplash.com/photo-1\""));
    }

    @Test
    void testSpecialSchemesIgnored() {
        String html = """
                <html>
                <body>
                    <a href="javascript:void(0)">Click</a>
                    <a href="mailto:support@ogaan.com">Support</a>
                    <a href="tel:+123456789">Call</a>
                    <a href="#section2">Jump</a>
                    <img src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUg==">
                </body>
                </html>
                """;

        URI currentUri = URI.create("https://www.ogaan.com/sale");
        String rewritten = htmlRewriter.rewrite(html, "1BiVqa8OZJl", currentUri);

        assertTrue(rewritten.contains("href=\"javascript:void(0)\""));
        assertTrue(rewritten.contains("href=\"mailto:support@ogaan.com\""));
        assertTrue(rewritten.contains("href=\"tel:+123456789\""));
        assertTrue(rewritten.contains("href=\"#section2\""));
        assertTrue(rewritten.contains("src=\"data:image/png;base64,iVBORw0KGgoAAAANSUhEUg==\""));
    }

    @Test
    void testSrcsetRewriting() {
        String html = """
                <html>
                <body>
                    <img src="/img/default.jpg"
                         srcset="/img/small.jpg 300w, /img/medium.jpg 600w, https://cdn.external.com/big.jpg 1200w">
                </body>
                </html>
                """;

        URI currentUri = URI.create("https://www.ogaan.com/sale");
        String rewritten = htmlRewriter.rewrite(html, "1BiVqa8OZJl", currentUri);

        assertTrue(rewritten.contains("/1BiVqa8OZJl/img/small.jpg 300w"));
        assertTrue(rewritten.contains("/1BiVqa8OZJl/img/medium.jpg 600w"));
        assertTrue(rewritten.contains("https://cdn.external.com/big.jpg 1200w"));
    }

    @Test
    void testStylesRewritingInHtml() {
        String html = """
                <html>
                <head>
                    <style>
                        body { background: url('/pub/media/bg.jpg'); }
                        @import url('/pub/static/fonts.css');
                    </style>
                </head>
                <body>
                    <div style="background-image: url('pub/media/tile.png');">Content</div>
                </body>
                </html>
                """;

        URI currentUri = URI.create("https://www.ogaan.com/sale");
        String rewritten = htmlRewriter.rewrite(html, "1BiVqa8OZJl", currentUri);

        assertTrue(rewritten.contains("url('/1BiVqa8OZJl/pub/media/bg.jpg')"));
        assertTrue(rewritten.contains("url('/1BiVqa8OZJl/pub/static/fonts.css')"));
        assertTrue(rewritten.contains("url('/1BiVqa8OZJl/pub/media/tile.png')"));
    }

    @Test
    void testBaseTagExtraction() {
        String html = """
                <html>
                <head>
                    <base href="https://www.ogaan.com/catalog/">
                </head>
                <body>
                    <a href="product123">Product</a>
                </body>
                </html>
                """;

        URI currentUri = URI.create("https://www.ogaan.com/sale");
        String rewritten = htmlRewriter.rewrite(html, "1BiVqa8OZJl", currentUri);

        // base tag should be removed or resolved against
        assertFalse(rewritten.contains("<base"));
        assertTrue(rewritten.contains("href=\"/1BiVqa8OZJl/catalog/product123\""));
    }

    @Test
    void testInjectClientInterceptorAndRewriteInlineScripts() {
        String html = """
                <html>
                <head>
                    <title>Pernia's Popup Shop</title>
                    <script>
                        window.API_URL = "https://www.perniaspopupshop.com/napi";
                    </script>
                </head>
                <body>
                    <h1>Welcome</h1>
                </body>
                </html>
                """;

        URI currentUri = URI.create("https://www.perniaspopupshop.com/designers");
        String rewritten = htmlRewriter.rewrite(html, "1BiVqa8OZJl", currentUri);

        // Verify interceptor script is injected into head
        assertTrue(rewritten.contains("id=\"__preons_mirror_interceptor\""));
        assertTrue(rewritten.contains("window.fetch"));
        assertTrue(rewritten.contains("window.XMLHttpRequest"));
        assertTrue(rewritten.contains("upstreamOrigin = 'https://www.perniaspopupshop.com'"));
        assertTrue(rewritten.contains("mirrorPrefix = '/1BiVqa8OZJl'"));

        // Verify inline script URL is rewritten
        assertTrue(rewritten.contains("window.API_URL = \"/1BiVqa8OZJl/napi\""));
        assertFalse(rewritten.contains("\"https://www.perniaspopupshop.com/napi\""));
    }
}
