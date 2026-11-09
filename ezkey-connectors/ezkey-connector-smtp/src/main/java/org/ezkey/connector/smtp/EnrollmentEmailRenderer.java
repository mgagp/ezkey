/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 *
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.connector.smtp;

import org.springframework.web.util.HtmlUtils;

/**
 * Fixed templates for the {@code enrollment-qr} email, in English and French. The connector owns
 * the wording, so the contract carries no free text except the organisation name, which is escaped
 * here. The challenge is never part of the email.
 *
 * <p>Required content (TB-2026-11-06 §5): who invites, have the code in hand before scanning, which
 * server the app should show, and what to do with an unexpected email.
 */
final class EnrollmentEmailRenderer {

  /** Content id of the inline QR image. */
  static final String QR_CONTENT_ID = "enrollment-qr";

  /** One rendered email. */
  record EnrollmentEmail(String subject, String text, String html) {}

  private EnrollmentEmailRenderer() {}

  static EnrollmentEmail render(String locale, String organisationName, String expectedServer) {
    return "fr".equals(locale)
        ? french(organisationName, expectedServer)
        : english(organisationName, expectedServer);
  }

  private static EnrollmentEmail english(String org, String server) {
    String subject = org + " invites you to set up sign-in approval";
    String text =
        """
        Hello,

        %1$s invites you to set up the Ezkey app on your phone. You will use it to approve sign-ins.

        Before you scan:

        1. Have your 6-digit code in hand. %1$s gives it to you separately, not in this email. You will enter it \
        right after scanning.
        2. Open the Ezkey app and scan the QR code attached to this email.
        3. Check that the app shows this server: %2$s. If it shows another one, stop and contact %1$s.

        Did not expect this email? Do not scan the code. Contact %1$s through a channel you already know, not by \
        replying to this message.

        This invitation expires. If it no longer works, ask %1$s for a new one.
        """
            .formatted(org, server);
    String html =
        page(
            "en",
            """
            <p>Hello,</p>
            <p>%1$s invites you to set up the Ezkey app on your phone. You will use it to approve sign-ins.</p>
            <p><strong>Before you scan:</strong></p>
            <ol>
              <li>Have your 6-digit code in hand. %1$s gives it to you separately, not in this email. You will enter \
            it right after scanning.</li>
              <li>Open the Ezkey app and scan this QR code:</li>
            </ol>
            <p><img src="cid:%3$s" width="240" height="240" alt="Ezkey invitation QR code"></p>
            <ol start="3">
              <li>Check that the app shows this server: <strong>%2$s</strong>. If it shows another one, stop and \
            contact %1$s.</li>
            </ol>
            <p><strong>Did not expect this email?</strong> Do not scan the code. Contact %1$s through a channel you \
            already know, not by replying to this message.</p>
            <p>This invitation expires. If it no longer works, ask %1$s for a new one.</p>
            """
                .formatted(HtmlUtils.htmlEscape(org), HtmlUtils.htmlEscape(server), QR_CONTENT_ID));
    return new EnrollmentEmail(subject, text, html);
  }

  private static EnrollmentEmail french(String org, String server) {
    String subject = org + " vous invite à configurer l’approbation de connexion";
    String text =
        """
        Bonjour,

        %1$s vous invite à configurer l’application Ezkey sur votre téléphone. Elle vous servira à approuver vos \
        connexions.

        Avant de scanner :

        1. Ayez votre code à 6 chiffres en main. %1$s vous le donne séparément, pas dans ce courriel. Vous le \
        saisirez juste après le scan.
        2. Ouvrez l’application Ezkey et scannez le code QR joint à ce courriel.
        3. Vérifiez que l’application affiche ce serveur : %2$s. Si elle en affiche un autre, arrêtez et contactez %1$s.

        Vous n’attendiez pas ce courriel ? Ne scannez pas le code. Contactez %1$s par un moyen que vous connaissez \
        déjà, pas en répondant à ce message.

        Cette invitation expire. Si elle ne fonctionne plus, demandez-en une nouvelle à %1$s.
        """
            .formatted(org, server);
    String html =
        page(
            "fr",
            """
            <p>Bonjour,</p>
            <p>%1$s vous invite à configurer l’application Ezkey sur votre téléphone. Elle vous servira à approuver \
            vos connexions.</p>
            <p><strong>Avant de scanner :</strong></p>
            <ol>
              <li>Ayez votre code à 6 chiffres en main. %1$s vous le donne séparément, pas dans ce courriel. Vous le \
            saisirez juste après le scan.</li>
              <li>Ouvrez l’application Ezkey et scannez ce code QR :</li>
            </ol>
            <p><img src="cid:%3$s" width="240" height="240" alt="Code QR de l’invitation Ezkey"></p>
            <ol start="3">
              <li>Vérifiez que l’application affiche ce serveur : <strong>%2$s</strong>. Si elle en affiche un \
            autre, arrêtez et contactez %1$s.</li>
            </ol>
            <p><strong>Vous n’attendiez pas ce courriel ?</strong> Ne scannez pas le code. Contactez %1$s par un \
            moyen que vous connaissez déjà, pas en répondant à ce message.</p>
            <p>Cette invitation expire. Si elle ne fonctionne plus, demandez-en une nouvelle à %1$s.</p>
            """
                .formatted(HtmlUtils.htmlEscape(org), HtmlUtils.htmlEscape(server), QR_CONTENT_ID));
    return new EnrollmentEmail(subject, text, html);
  }

  private static String page(String lang, String body) {
    return "<!doctype html><html lang=\""
        + lang
        + "\"><head><meta charset=\"utf-8\"></head>"
        + "<body style=\"font-family: sans-serif; line-height: 1.5; max-width: 560px\">"
        + body
        + "</body></html>";
  }
}
