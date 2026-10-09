/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Controller: AccessCodeController
 * Description: Temporary access-link entry point for the Play closed-testing path.
 */

package org.ezkey.demo.acme.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.Optional;
import org.ezkey.demo.acme.DemoAuthMessages;
import org.ezkey.demo.acme.security.DemoRateLimitService;
import org.ezkey.demo.acme.service.AccessCodeService;
import org.ezkey.demo.acme.service.DemoApiKeyConfigService;
import org.ezkey.demo.acme.web.SessionHelpers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.view.RedirectView;

/**
 * Handles {@code GET /t/{code}} for temporary evaluator access links.
 *
 * <p>Unknown codes render the login page with the generic error (no distinct 404). Known codes
 * invalidate any existing session, store only the slot id, and 303-redirect to {@code /login}.
 * Access codes are never logged.
 *
 * @author Ezkey contributors
 * @since 2026
 */
@Controller
public class AccessCodeController {

  private static final Logger LOG = LoggerFactory.getLogger(AccessCodeController.class);

  private final AccessCodeService accessCodeService;
  private final DemoApiKeyConfigService demoApiKeyConfigService;
  private final DemoRateLimitService demoRateLimitService;

  /**
   * Creates the controller.
   *
   * @param accessCodeService slot lookup
   * @param demoApiKeyConfigService session slot activation
   * @param demoRateLimitService access-link rate-limit buckets (shared ceiling with POST /login)
   */
  public AccessCodeController(
      AccessCodeService accessCodeService,
      DemoApiKeyConfigService demoApiKeyConfigService,
      DemoRateLimitService demoRateLimitService) {
    this.accessCodeService = accessCodeService;
    this.demoApiKeyConfigService = demoApiKeyConfigService;
    this.demoRateLimitService = demoRateLimitService;
  }

  /**
   * Activates an access-code slot or shows the generic login error.
   *
   * <p>Resolves the code first, then applies {@link DemoRateLimitService#checkAccessLink}: IP-only
   * for unknown codes; IP-only plus slot+IP once valid. Rate-limit and unknown-code responses stay
   * generic (no oracle). Access codes are never logged.
   *
   * @param code access code from the path (never logged)
   * @param request current request
   * @param model Spring MVC model
   * @return redirect to login (303) or the login template
   */
  @GetMapping("/t/{code}")
  public Object activateAccessCode(
      @PathVariable("code") String code, HttpServletRequest request, Model model) {

    Optional<String> slotId = accessCodeService.findSlotIdByCode(code);

    DemoRateLimitService.RateLimitDecision rateLimitDecision =
        demoRateLimitService.checkAccessLink(request, slotId.orElse(null));
    if (!rateLimitDecision.allowed()) {
      LOG.warn(
          "Access-link rate limit exceeded for clientIp={} retryAfterSeconds={}",
          rateLimitDecision.clientId(),
          rateLimitDecision.retryAfterSeconds());
      return renderLinkLostLogin(
          model, DemoAuthMessages.RATE_LIMIT_LOGIN, DemoAuthMessages.LINK_HINT_WAIT_AND_REOPEN);
    }

    if (slotId.isEmpty()) {
      return renderLinkLostLogin(
          model, DemoAuthMessages.GENERIC_SIGN_IN_FAILED, DemoAuthMessages.LINK_HINT_CHECK_OR_ASK);
    }

    String resolvedSlotId = slotId.get();
    String label = accessCodeService.getLabel(resolvedSlotId);
    LOG.info("Access-code slot activated: label={}", label != null ? label : resolvedSlotId);

    HttpSession session = SessionHelpers.invalidateAndCreate(request);
    demoApiKeyConfigService.activateAccessCodeSlot(session, resolvedSlotId);

    RedirectView redirect = new RedirectView("/login", true);
    redirect.setStatusCode(HttpStatus.SEE_OTHER);
    redirect.setExposeModelAttributes(false);
    return redirect;
  }

  /**
   * Renders login in link-lost layout (state B): no self-service chrome, no form.
   *
   * @param model Spring MVC model
   * @param errorMessage primary error text
   * @param recoveryHint bilingual recovery / hint line
   * @return login template name
   */
  private static String renderLinkLostLogin(Model model, String errorMessage, String recoveryHint) {
    model.addAttribute("pageTitle", "Login - ACME Inc");
    model.addAttribute("hasError", true);
    model.addAttribute("error", errorMessage);
    model.addAttribute("showSelfServiceChrome", false);
    model.addAttribute("showTenantLinkInfoCard", false);
    model.addAttribute("showLoginForm", false);
    model.addAttribute("entryLink", true);
    model.addAttribute("recoveryHint", recoveryHint);
    return "login";
  }
}
