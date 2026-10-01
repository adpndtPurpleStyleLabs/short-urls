package com.preonsurl.ui;


import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Profile("app")
@Controller
public class SeoLandingController {

    @GetMapping("/surl-shortener")
    public String surlShortener() { return "landing-pages/surl-shortener"; }

    @GetMapping("/secure-url")
    public String secureUrl() { return "landing-pages/secure-url"; }

    @GetMapping("/password-protected-url")
    public String passwordProtectedUrl() { return "landing-pages/password-protected-url"; }

    @GetMapping("/pin-protected-url")
    public String pinProtectedUrl() { return "landing-pages/pin-protected-url"; }

    @GetMapping("/one-time-link")
    public String oneTimeLink() { return "landing-pages/one-time-link"; }

    @GetMapping("/expiring-link")
    public String expiringLink() { return "landing-pages/expiring-link"; }

    @GetMapping("/temporary-url")
    public String temporaryUrl() { return "landing-pages/temporary-url"; }

    @GetMapping("/limited-use-link")
    public String limitedUseLink() { return "landing-pages/limited-use-link"; }

    @GetMapping("/secure-file-sharing")
    public String secureFileSharing() { return "landing-pages/secure-file-sharing"; }

    @GetMapping("/secure-download-link")
    public String secureDownloadLink() { return "landing-pages/secure-download-link"; }

    @GetMapping("/temporary-download-link")
    public String temporaryDownloadLink() { return "landing-pages/temporary-download-link"; }

    @GetMapping("/expiring-download-link")
    public String expiringDownloadLink() { return "landing-pages/expiring-download-link"; }

    @GetMapping("/one-time-download-link")
    public String oneTimeDownloadLink() { return "landing-pages/one-time-download-link"; }

    @GetMapping("/branded-short-links")
    public String brandedShortLinks() { return "landing-pages/branded-short-links"; }

    @GetMapping("/branded-url-shortener")
    public String brandedUrlShortener() { return "landing-pages/branded-url-shortener"; }

    @GetMapping("/custom-domain-url-shortener")
    public String customDomainUrlShortener() { return "landing-pages/custom-domain-url-shortener"; }

    @GetMapping("/white-label-url-shortener")
    public String whiteLabelUrlShortener() { return "landing-pages/white-label-url-shortener"; }

    @GetMapping("/url-shortener-api")
    public String urlShortenerApi() { return "landing-pages/url-shortener-api"; }

    @GetMapping("/url-shortener-api-java")
    public String urlShortenerApiJava() { return "landing-pages/url-shortener-api-java"; }

    @GetMapping("/url-shortener-api-python")
    public String urlShortenerApiPython() { return "landing-pages/url-shortener-api-python"; }

    @GetMapping("/url-shortener-api-nodejs")
    public String urlShortenerApiNodejs() { return "landing-pages/url-shortener-api-nodejs"; }

    @GetMapping("/url-shortener-api-go")
    public String urlShortenerApiGo() { return "landing-pages/url-shortener-api-go"; }

    @GetMapping("/url-shortener-api-csharp")
    public String urlShortenerApiCsharp() { return "landing-pages/url-shortener-api-csharp"; }

    @GetMapping("/smart-links")
    public String smartLinks() { return "landing-pages/smart-links"; }

    @GetMapping("/dynamic-links")
    public String dynamicLinks() { return "landing-pages/dynamic-links"; }

    @GetMapping("/link-tracking")
    public String linkTracking() { return "landing-pages/link-tracking"; }

    @GetMapping("/link-analytics")
    public String linkAnalytics() { return "landing-pages/link-analytics"; }

    @GetMapping("/qr-code-url-shortener")
    public String qrCodeUrlShortener() { return "landing-pages/qr-code-url-shortener"; }

    @GetMapping("/bitly-alternative")
    public String bitlyAlternative() { return "landing-pages/bitly-alternative"; }

    @GetMapping("/tinyurl-alternative")
    public String tinyurlAlternative() { return "landing-pages/tinyurl-alternative"; }
}
