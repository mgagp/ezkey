package org.ezkey.demo.device.controller;

import java.security.KeyPair;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.ezkey.demodevice.generated.dto.EnrollmentBindResponseDto;
import org.ezkey.demodevice.generated.dto.EnrollmentVerifyRequestDto;
import org.ezkey.demodevice.generated.dto.EnrollmentVerifyResponseDto;

import org.ezkey.demo.device.service.AuthApiService;
import org.ezkey.demo.device.service.DeviceCryptoService;
import org.ezkey.demo.device.service.EnrollmentStoreService;
import org.ezkey.demo.device.service.EnrollmentStoreService.Record;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Simulated Ezkey mobile app controller.
 *
 * @since 2025
 */
@Controller
@RequestMapping("/phone/ezkey")
public class EzkeyAppController {

    private static final Logger logger = LoggerFactory.getLogger(EzkeyAppController.class);

    private final AuthApiService authApiService;

    private final DeviceCryptoService cryptoService;

    private final EnrollmentStoreService storeService;

    public EzkeyAppController(AuthApiService authApiService, DeviceCryptoService cryptoService, 
                             EnrollmentStoreService storeService) {
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

    @GetMapping("/enrollment/new")
    public String newEnrollment(Model model) {
        model.addAttribute("pageTitle", "New Enrollment");
        return "phone/ezkey/new_enrollment";
    }

    @PostMapping("/enrollment/bind")
    public String bindEnrollment(@RequestParam("enrollmentId") Integer enrollmentId,
                                 @RequestParam(value = "language", defaultValue = "en") String language,
                                 Model model) {
        model.addAttribute("pageTitle", "Enrollment - Bind");
        model.addAttribute("enrollmentId", enrollmentId);
        
        try {
            // Call the bind API
            EnrollmentBindResponseDto bindResponse = authApiService.bind(enrollmentId).block();
            
            if (bindResponse != null) {
                // Generate device keys
                KeyPair keyPair = cryptoService.generateDeviceKeyPair();
                String devicePublicKeyB64 = cryptoService.publicKeyToBase64(keyPair.getPublic());
                String devicePrivateKeyB64 = cryptoService.privateKeyToBase64(keyPair.getPrivate());

                String integrationPublicKey = bindResponse.getIntegrationPublicKey();
                String enrollmentProofToken = bindResponse.getEnrollmentProofToken();

                // Get integration information from the response
                String integrationName = bindResponse.getIntegrationName();
                String integrationDescription = bindResponse.getIntegrationDescription();
                String integrationLogo = bindResponse.getIntegrationLogo();

                logger.info("Using integration info for enrollment {} with language {}: name={}, description={}, logo={}", 
                           enrollmentId, language, integrationName, integrationDescription, integrationLogo);

                // Save interim record before verify with integration information
                Record record = new Record(
                        enrollmentId,
                        null, // integrationId - would be set if available
                        null,
                        integrationPublicKey,
                        enrollmentProofToken, // Store the proof token (not signed)
                        devicePublicKeyB64,
                        devicePrivateKeyB64,
                        null,
                        "Device",
                        null,
                        integrationName,
                        integrationDescription,
                        integrationLogo
                );
                storeService.save(record);

                model.addAttribute("enrollmentId", enrollmentId);
                model.addAttribute("enrollmentProofToken", enrollmentProofToken);
                model.addAttribute("integrationPublicKey", integrationPublicKey);
                model.addAttribute("integrationName", integrationName);
                model.addAttribute("integrationDescription", integrationDescription);
                model.addAttribute("integrationLogo", integrationLogo);
                model.addAttribute("language", language);
                model.addAttribute("success", "Bind successful! Enter the challenge code to verify.");
            } else {
                model.addAttribute("error", "Bind failed: No response from server");
            }
        } catch (Exception e) {
            logger.error("Bind failed for enrollment {}", enrollmentId, e);
            model.addAttribute("error", "Bind failed: " + e.getMessage());
        }
        
        return "phone/ezkey/bind_enrollment";
    }

    @PostMapping("/enrollment/verify")
    public String verifyEnrollment(@RequestParam("enrollmentId") Integer enrollmentId,
                                   @RequestParam("challengeResponse") String challengeResponse,
                                   Model model) {
        model.addAttribute("pageTitle", "Enrollment - Verify");
        
        try {
            Optional<Record> recOpt = storeService.load(enrollmentId);
            if (recOpt.isEmpty()) {
                model.addAttribute("error", "Enrollment not found in local store");
                return "phone/ezkey/bind_enrollment";
            }
            
            Record rec = recOpt.get();
            
            // Sign the enrollment proof token with device private key
            String enrollmentProofTokenSigned = cryptoService.signStringToBase64(rec.enrollmentProofToken(), 
                cryptoService.base64ToPrivateKey(rec.devicePrivateKey()));

            // Create typed request DTO
            EnrollmentVerifyRequestDto requestDto = new EnrollmentVerifyRequestDto()
                    .enrollmentId(enrollmentId)
                    .challengeResponse(Integer.parseInt(challengeResponse))
                    .devicePublicKey(rec.devicePublicKey())
                    .enrollmentProofTokenSigned(enrollmentProofTokenSigned);

            // Log the request for debugging
            logger.info("Verify request for enrollment {}: {}", enrollmentId, requestDto);
            logger.info("Enrollment proof token being signed: {}", rec.enrollmentProofToken());
            logger.info("Device public key: {}", rec.devicePublicKey());
            logger.info("Signed enrollment proof token: {}", enrollmentProofTokenSigned);

            EnrollmentVerifyResponseDto verifyResponse = authApiService.verify(requestDto).block();

            if (verifyResponse != null) {
                Boolean active = verifyResponse.getActive();
                if (Boolean.TRUE.equals(active)) {
                    // Update the record with verification status
                    Record updatedRecord = new Record(
                            rec.enrollmentId(),
                            rec.integrationId(),
                            rec.enrollmentUrl(),
                            rec.integrationPublicKey(),
                            rec.enrollmentProofToken(),
                            rec.devicePublicKey(),
                            rec.devicePrivateKey(),
                            true, // authAttemptChallengeRequired
                            rec.deviceLabel(),
                            rec.createdAt(),
                            rec.integrationName(),
                            rec.integrationDescription(),
                            rec.integrationLogo()
                    );
                    storeService.save(updatedRecord);
                    
                    model.addAttribute("success", "Enrollment verified successfully!");
                    model.addAttribute("enrollmentId", enrollmentId);
                    return "phone/ezkey/verify_success";
                } else {
                    model.addAttribute("error", "Verification failed: Enrollment is not active");
                }
            } else {
                model.addAttribute("error", "Verification failed: No response from server");
            }
        } catch (Exception e) {
            logger.error("Verify failed for enrollment {}", enrollmentId, e);
            model.addAttribute("error", "Verification failed: " + e.getMessage());
        }
        
        return "phone/ezkey/bind_enrollment";
    }

    @GetMapping("/enrollments/{enrollmentId}/auth")
    public String enrollmentAuth(@PathVariable("enrollmentId") Integer enrollmentId, Model model) {
        model.addAttribute("pageTitle", "Authentication");
        model.addAttribute("enrollmentId", enrollmentId);
        return "phone/ezkey/auth";
    }
}
