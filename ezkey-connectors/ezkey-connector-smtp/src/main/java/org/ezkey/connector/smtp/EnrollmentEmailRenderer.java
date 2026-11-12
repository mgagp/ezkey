/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 *
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.connector.smtp;

import java.time.DateTimeException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.web.util.HtmlUtils;

/**
 * Fixed templates for the {@code enrollment-qr} email, in English and French. The connector owns
 * the wording, so the contract carries no free text except the organisation name, which is escaped
 * here. The challenge is never part of the email.
 *
 * <p>Required content (TB-2026-11-06 §5): who invites, have the code in hand before scanning, which
 * server the app should show, what to do with an unexpected email, and until when the invitation
 * works when it has a deadline.
 */
final class EnrollmentEmailRenderer {

  /** Content id of the inline QR image. */
  static final String QR_CONTENT_ID = "enrollment-qr";

  /** One rendered email. */
  record EnrollmentEmail(String subject, String text, String html) {}

  private static final DateTimeFormatter ENGLISH_DEADLINE =
      DateTimeFormatter.ofPattern("MMMM d, uuuu 'at' h:mm a (vvvv)", Locale.ENGLISH);
  private static final DateTimeFormatter FRENCH_DAY_MONTH =
      DateTimeFormatter.ofPattern("MMMM uuuu 'à' H 'h' mm (vvvv)", Locale.FRENCH);

  /** The UTC region, named in words, unlike the bare {@code Z} offset. */
  private static final ZoneId UTC = ZoneId.of("UTC");

  private EnrollmentEmailRenderer() {}

  /**
   * Renders the email.
   *
   * @param locale {@code en} or {@code fr}
   * @param organisationName who invites
   * @param expectedServer the server the app should show
   * @param expiresAt when the invitation stops working, or null when it has no deadline
   * @param timeZone the organisation's IANA zone, or null for UTC
   * @return the subject, text and HTML bodies
   */
  static EnrollmentEmail render(
      String locale,
      String organisationName,
      String expectedServer,
      OffsetDateTime expiresAt,
      String timeZone) {
    ZonedDateTime deadline = expiresAt == null ? null : expiresAt.atZoneSameInstant(zone(timeZone));
    return "fr".equals(locale)
        ? french(organisationName, expectedServer, deadline)
        : english(organisationName, expectedServer, deadline);
  }

  /** The organisation's zone, or UTC when it has none or it is unknown. */
  private static ZoneId zone(String timeZone) {
    if (timeZone == null || timeZone.isBlank()) {
      return UTC;
    }
    try {
      return ZoneId.of(timeZone);
    } catch (DateTimeException e) {
      return UTC;
    }
  }

  private static String englishExpiry(String org, ZonedDateTime deadline) {
    return deadline == null
        ? "If this invitation no longer works, ask %s for a new one.".formatted(org)
        : "This invitation is valid until %s. After that, ask %s for a new one."
            .formatted(ENGLISH_DEADLINE.format(deadline), org);
  }

  private static String frenchExpiry(String org, ZonedDateTime deadline) {
    if (deadline == null) {
      return "Si cette invitation ne fonctionne plus, demandez-en une nouvelle à %s."
          .formatted(org);
    }
    int day = deadline.getDayOfMonth();
    String date =
        (day == 1 ? "1er" : String.valueOf(day)) + " " + FRENCH_DAY_MONTH.format(deadline);
    return "Cette invitation est valable jusqu’au %s. Passé ce délai, demandez-en une nouvelle à %s."
        .formatted(date, org);
  }

  private static EnrollmentEmail english(String org, String server, ZonedDateTime deadline) {
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

        %3$s
        """
            .formatted(org, server, englishExpiry(org, deadline));
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
            <p>%4$s</p>
            """
                .formatted(
                    HtmlUtils.htmlEscape(org),
                    HtmlUtils.htmlEscape(server),
                    QR_CONTENT_ID,
                    HtmlUtils.htmlEscape(englishExpiry(org, deadline), "UTF-8")));
    return new EnrollmentEmail(subject, text, html);
  }

  private static EnrollmentEmail french(String org, String server, ZonedDateTime deadline) {
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

        %3$s
        """
            .formatted(org, server, frenchExpiry(org, deadline));
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
            <p>%4$s</p>
            """
                .formatted(
                    HtmlUtils.htmlEscape(org),
                    HtmlUtils.htmlEscape(server),
                    QR_CONTENT_ID,
                    HtmlUtils.htmlEscape(frenchExpiry(org, deadline), "UTF-8")));
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
