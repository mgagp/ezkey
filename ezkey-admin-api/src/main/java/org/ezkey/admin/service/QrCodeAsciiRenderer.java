/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: QrCodeAsciiRenderer
 * Description: Utility service that renders QR codes as ASCII art for terminal/log output.
 */

package org.ezkey.admin.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Utility for rendering QR codes as ASCII art.
 *
 * <p>The output uses two-character blocks for each module so the QR code maintains a square aspect
 * ratio when displayed in terminal logs. This is intended for bootstrap flows where administrators
 * need to scan credentials directly from a console.
 */
@Service
public class QrCodeAsciiRenderer {

  private static final Map<EncodeHintType, Object> ENCODE_HINTS =
      Map.of(EncodeHintType.CHARACTER_SET, "UTF-8", EncodeHintType.MARGIN, 1);

  private static final String DARK = "██";

  private static final String LIGHT = "  ";

  private static final int DEFAULT_SIZE = 33;

  private final QRCodeWriter qrCodeWriter = new QRCodeWriter();

  /**
   * Renders the provided content as an ASCII QR code.
   *
   * @param content the content to encode
   * @return string containing the ASCII QR code
   * @throws IllegalArgumentException if encoding fails
   */
  public String renderAscii(String content) {
    try {
      BitMatrix bitMatrix =
          qrCodeWriter.encode(
              content, BarcodeFormat.QR_CODE, DEFAULT_SIZE, DEFAULT_SIZE, ENCODE_HINTS);

      StringBuilder builder = new StringBuilder();
      for (int y = 0; y < bitMatrix.getHeight(); y++) {
        for (int x = 0; x < bitMatrix.getWidth(); x++) {
          builder.append(bitMatrix.get(x, y) ? DARK : LIGHT);
        }
        if (y < bitMatrix.getHeight() - 1) {
          builder.append(System.lineSeparator());
        }
      }
      return builder.toString();
    } catch (WriterException e) {
      throw new IllegalArgumentException("Failed to render ASCII QR code", e);
    }
  }
}


