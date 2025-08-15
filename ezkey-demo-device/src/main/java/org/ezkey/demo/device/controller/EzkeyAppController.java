package org.ezkey.demo.device.controller;

import java.util.Optional;

import org.ezkey.demo.device.service.AuthApiService;
import org.ezkey.demo.device.service.DeviceCryptoService;
import org.ezkey.demo.device.service.EnrollmentStoreService;
import org.ezkey.demo.device.service.EnrollmentStoreService.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    @GetMapping("/enrollments/{enrollmentId}/auth")
    public String enrollmentAuth(@PathVariable("enrollmentId") Integer enrollmentId, Model model) {
        model.addAttribute("pageTitle", "Authentication");
        model.addAttribute("enrollmentId", enrollmentId);
        return "phone/ezkey/auth";
    }
}


