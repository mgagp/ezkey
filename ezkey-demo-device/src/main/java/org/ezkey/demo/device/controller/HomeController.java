package org.ezkey.demo.device.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Home controller for demo device.
 * @since 2025
 */
@Controller
public class HomeController {

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("pageTitle", "Ezkey Demo Device");
        return "index";
    }
}


