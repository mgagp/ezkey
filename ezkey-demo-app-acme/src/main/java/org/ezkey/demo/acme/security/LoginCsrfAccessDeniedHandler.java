/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Security: LoginCsrfAccessDeniedHandler
 * Description: Maps CSRF failures on POST /login to sessionexpired (with optional entry=link).
 */

package org.ezkey.demo.acme.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.ezkey.demo.acme.web.LinkEntryMarker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.csrf.InvalidCsrfTokenException;
import org.springframework.security.web.csrf.MissingCsrfTokenException;

/**
 * Narrow CSRF failure handler for {@code POST /login}.
 *
 * <p>Stale-tab posts after session expiry never reach the controller; this redirects to a fixed
 * internal login URL (optionally with {@code entry=link} from the form field). Targets are
 * hard-coded — never echo user input (open-redirect safe). All other 403s keep the default
 * behaviour.
 *
 * @author Ezkey contributors
 * @since 2026
 */
public final class LoginCsrfAccessDeniedHandler implements AccessDeniedHandler {

  private static final Logger LOG = LoggerFactory.getLogger(LoginCsrfAccessDeniedHandler.class);

  private static final String LOGIN_SESSION_EXPIRED = "/login?error=sessionexpired";

  private final AccessDeniedHandler fallback = new AccessDeniedHandlerImpl();

  @Override
  public void handle(
      HttpServletRequest request, HttpServletResponse response, AccessDeniedException denied)
      throws IOException, ServletException {

    if (isPostLoginCsrfFailure(request, denied)) {
      boolean entryLink = LinkEntryMarker.isLink(request.getParameter(LinkEntryMarker.PARAM));
      String target =
          entryLink ? LinkEntryMarker.withMarker(LOGIN_SESSION_EXPIRED) : LOGIN_SESSION_EXPIRED;
      LOG.info(
          "CSRF failure on POST /login — redirecting to login sessionexpired (entryLink={})",
          entryLink);
      response.sendRedirect(request.getContextPath() + target);
      return;
    }

    fallback.handle(request, response, denied);
  }

  private static boolean isPostLoginCsrfFailure(
      HttpServletRequest request, AccessDeniedException denied) {
    if (!"POST".equalsIgnoreCase(request.getMethod())) {
      return false;
    }
    String path = request.getRequestURI();
    String context = request.getContextPath();
    String relative =
        (context != null && !context.isEmpty() && path.startsWith(context))
            ? path.substring(context.length())
            : path;
    if (!"/login".equals(relative)) {
      return false;
    }
    return denied instanceof InvalidCsrfTokenException
        || denied instanceof MissingCsrfTokenException;
  }
}
