package com.preonsurl.apis;

import com.preonsurl.apis.link.service.ProxyResourceValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class ProxyResourceValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "https://example.com/files/archive.zip",
            "https://example.com/docs/manual.pdf",
            "https://example.com/images/photo.jpg",
            "https://example.com/images/photo.jpeg",
            "https://example.com/images/logo.png",
            "https://example.com/media/clip.mp4",
            "https://example.com/audio/song.mp3",
            "https://example.com/data/export.csv",
            "https://example.com/api/sample.json",
            "https://example.com/config/data.xml",
            "https://example.com/notes.txt",
            "https://example.com/archive.tar.gz",
            "https://example.com/docs/report.docx",
            "https://example.com/spreadsheets/sheet.xlsx",
            "https://example.com/files/archive.zip?token=xyz123&download=true",
            "http://localhost:8080/assets/banner.webp#section"
    })
    void validResourceUrls_passValidation(String url) {
        assertTrue(ProxyResourceValidator.isAcceptableProxyUrl(url));
        assertDoesNotThrow(() -> ProxyResourceValidator.validateProxyUrl(url));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://example.com",
            "https://example.com/",
            "https://example.com/home",
            "https://example.com/about-us/",
            "https://example.com/index.html",
            "https://example.com/page.htm",
            "https://example.com/dashboard.php",
            "https://example.com/profile.jsp",
            "https://example.com/article?id=123"
    })
    void webpageUrls_failValidationWithExplicitMessage(String url) {
        assertFalse(ProxyResourceValidator.isAcceptableProxyUrl(url));
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> ProxyResourceValidator.validateProxyUrl(url));
        assertTrue(exception.getMessage().contains("URL cannot be a webpage when proxy mode is selected"));
        assertTrue(exception.getMessage().contains(".zip, .pdf, .jpg"));
    }

    @Test
    void emptyOrNullUrl_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> ProxyResourceValidator.validateProxyUrl(null));
        assertThrows(IllegalArgumentException.class, () -> ProxyResourceValidator.validateProxyUrl("   "));
    }
}
