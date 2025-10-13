package org.ezkey.demo.device.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Phone simulator controller.
 *
 * @since 2025
 */
@Controller
@RequestMapping("/phone")
public class PhoneController {

  @GetMapping
  public String phoneHome(Model model) {
    model.addAttribute("pageTitle", "Phone Simulator");
    return "phone/home";
  }
}
