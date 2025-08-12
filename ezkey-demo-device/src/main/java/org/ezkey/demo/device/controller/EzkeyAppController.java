package org.ezkey.demo.device.controller;

import java.security.KeyPair;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.ezkey.demo.device.service.AuthApiService;
import org.ezkey.demo.device.service.DeviceCryptoService;
import org.ezkey.demo.device.service.EnrollmentStoreService;
import org.ezkey.demo.device.service.EnrollmentStoreService.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import reactor.core.publisher.Mono;

/**
 * Simulated Ezkey mobile app controller.
 * @since 2025
 */
@Controller
@RequestMapping("/phone/ezkey")
public class EzkeyAppController {

    private static final Logger logger = LoggerFactory.getLogger(EzkeyAppController.class);

    private final AuthApiService authApiService;
    private final DeviceCryptoService cryptoService;
    private final EnrollmentStoreService storeService;

    public EzkeyAppController(AuthApiService authApiService, DeviceCryptoService cryptoService, EnrollmentStoreService storeService) {
        this.authApiService = authApiService;
        this.cryptoService = cryptoService;
        this.storeService = storeService;
    }

    @GetMapping
    public String appHome(Model model) {
        model.addAttribute("pageTitle", "Ezkey App");
        model.addAttribute("enrollments", storeService.list());
        return "phone/ezkey/home";
    }

    /**
     * Entry point when user taps Ezkey on the phone home.
     * Expects the enrollment URL to be provided as query param (e.g., from sessionStorage bridge).
     */
    @GetMapping("/enrollment/bind")
    public String bindEnrollment(@RequestParam(name = "url", required = false) String enrollmentUrl, Model model) {
        model.addAttribute("pageTitle", "Enrollment - Bind");
        if (!StringUtils.hasText(enrollmentUrl)) {
            model.addAttribute("error", "Missing enrollment URL. Go back and paste the URL on the home page.");
            return "phone/ezkey/bind";
        }
        Integer enrollmentId = extractEnrollmentId(enrollmentUrl);
        if (enrollmentId == null) {
            model.addAttribute("error", "Invalid enrollment URL. Expected an ID.");
            return "phone/ezkey/bind";
        }
        // Generate device keys
        KeyPair keyPair = cryptoService.generateDeviceKeyPair();
        String devicePublicKeyB64 = cryptoService.publicKeyToBase64(keyPair.getPublic());
        String devicePrivateKeyB64 = cryptoService.privateKeyToBase64(keyPair.getPrivate());

        Map bindResponse = authApiService.bind(enrollmentId).onErrorResume(ex -> {
            model.addAttribute("error", "Bind failed: " + ex.getMessage());
            return Mono.empty();
        }).blockOptional().orElse(null);

        if (bindResponse == null) {
            return "phone/ezkey/bind";
        }

        String integrationPublicKey = String.valueOf(bindResponse.getOrDefault("integrationPublicKey", ""));
        String enrollmentProofToken = String.valueOf(bindResponse.getOrDefault("enrollmentProofToken", ""));

        // Save interim record before verify
        Record record = new Record(
                enrollmentId,
                null,
                enrollmentUrl,
                integrationPublicKey,
                enrollmentProofToken,
                devicePublicKeyB64,
                devicePrivateKeyB64,
                null,
                "Device",
                null
        );
        storeService.save(record);

        model.addAttribute("enrollmentId", enrollmentId);
        model.addAttribute("enrollmentUrl", enrollmentUrl);
        model.addAttribute("enrollmentProofToken", enrollmentProofToken);
        model.addAttribute("integrationPublicKey", integrationPublicKey);
        return "phone/ezkey/bind";
    }

    @PostMapping("/enrollment/verify")
    public String verifyEnrollment(@RequestParam("enrollmentId") Integer enrollmentId,
                                   @RequestParam("challenge") String challenge,
                                   Model model) {
        Optional<Record> recOpt = storeService.load(enrollmentId);
        if (recOpt.isEmpty()) {
            model.addAttribute("error", "Enrollment not found in local store");
            return "phone/ezkey/bind";
        }
        Record rec = recOpt.get();
        // sign proof token
        String signed = cryptoService.signStringToBase64(rec.enrollmentProofToken(), cryptoService.base64ToPrivateKey(rec.devicePrivateKey()));

        Map<String, Object> payload = new HashMap<>();
        payload.put("enrollmentId", enrollmentId);
        payload.put("challengeResponse", challenge);
        payload.put("devicePublicKey", rec.devicePublicKey());
        payload.put("enrollmentProofTokenSigned", signed);

        Map verifyResponse = authApiService.verify(payload).onErrorResume(ex -> {
            model.addAttribute("error", "Verify failed: " + ex.getMessage());
            return Mono.empty();
        }).blockOptional().orElse(null);

        model.addAttribute("pageTitle", "Enrollment - Verify");
        model.addAttribute("verifyResponse", verifyResponse);
        model.addAttribute("enrollmentId", enrollmentId);
        return "phone/ezkey/verify";
    }

    private Integer extractEnrollmentId(String url) {
        // very simple parsing: expect .../enroll/{id} or query param id
        try {
            String digits = url.replaceAll("[^0-9]", " ").trim();
            if (!digits.isEmpty()) {
                String[] parts = digits.split(" +");
                return Integer.parseInt(parts[0]);
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}


