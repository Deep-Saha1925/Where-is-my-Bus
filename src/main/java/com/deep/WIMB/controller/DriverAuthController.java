package com.deep.WIMB.controller;

import com.deep.WIMB.service.ClientIpResolver;
import com.deep.WIMB.service.DriverAccessService;
import com.deep.WIMB.service.DriverTokenService;
import com.deep.WIMB.service.LoginRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/driver")
@RequiredArgsConstructor
@Slf4j
public class DriverAuthController {

    private static final int MAX_FIELD_LENGTH = 100; // keeps absurdly long inputs out of the limiter's keys

    private final DriverAccessService driverAccessService;
    private final DriverTokenService driverTokenService;
    private final LoginRateLimiter loginRateLimiter;
    private final ClientIpResolver clientIpResolver;

    @GetMapping("/depots")
    public List<String> getDepots() {
        return driverAccessService.getDepotNames();
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verify(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String depotName = body.get("depotName");
        String code      = body.get("code");
        String busNumber = body.get("busNumber");

        if (busNumber == null || busNumber.isBlank()) {
            return ResponseEntity.status(400).body(Map.of("error", "Bus number is required"));
        }
        if (depotName == null || depotName.isBlank() || code == null || code.isBlank()) {
            return ResponseEntity.status(400).body(Map.of("error", "Depot and code are required"));
        }
        if (depotName.length() > MAX_FIELD_LENGTH || code.length() > MAX_FIELD_LENGTH
                || busNumber.length() > MAX_FIELD_LENGTH) {
            return ResponseEntity.status(400).body(Map.of("error", "Invalid request"));
        }

        String ip = clientIpResolver.resolve(request);

        // Locked out? Refuse before looking at the code, so a locked-out client learns nothing.
        LoginRateLimiter.Verdict blocked = loginRateLimiter.check(ip, depotName, busNumber);
        if (blocked.blocked()) {
            return tooManyAttempts(blocked, ip, depotName, busNumber);
        }

        if (!driverAccessService.isValid(depotName, code)) {
            loginRateLimiter.recordFailure(ip, depotName, busNumber);
            // That failure may have just tripped a limit -- tell the client straight away
            LoginRateLimiter.Verdict now = loginRateLimiter.check(ip, depotName, busNumber);
            if (now.blocked()) {
                return tooManyAttempts(now, ip, depotName, busNumber);
            }
            return ResponseEntity.status(401).body(Map.of("error", "Invalid depot or code"));
        }

        return ResponseEntity.ok(Map.of("token", driverTokenService.issueToken(busNumber)));
    }

    private ResponseEntity<?> tooManyAttempts(LoginRateLimiter.Verdict verdict, String ip,
                                              String depotName, String busNumber) {
        // Never logs the code. The IP is logged so you can confirm the proxy setup resolves real client IPs.
        log.warn("Driver login locked out: ip={} depot={} bus={} retryAfter={}s",
                ip, depotName.trim(), busNumber.trim(), verdict.retryAfterSeconds());

        long minutes = Math.max(1, (verdict.retryAfterSeconds() + 59) / 60);
        return ResponseEntity.status(429)
                .header("Retry-After", String.valueOf(verdict.retryAfterSeconds()))
                .body(Map.of(
                        "error", "Too many failed attempts. Please try again in about " + minutes
                                + (minutes == 1 ? " minute." : " minutes."),
                        "retryAfterSeconds", verdict.retryAfterSeconds()));
    }
}