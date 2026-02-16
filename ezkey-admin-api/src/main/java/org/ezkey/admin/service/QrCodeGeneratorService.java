/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: QrCodeGeneratorService
 * Description: Service for generating QR code images using ZXing library.
 */

package org.ezkey.admin.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Service for generating QR code images for enrollment binding.
 *
 * <p>This service uses the ZXing (Zebra Crossing) library to generate QR codes as PNG images. QR
 * codes contain enrollment credentials in JSON format that can be scanned by the mobile
 * application.
 *
 * <p><b>QR Code JSON Format:</b>
 *
 * <pre>
 * {"enrollmentId":"4","enrollmentProofToken":"abc...","authUrl":"https://..."}
 * </pre>
 *
 * <p>The {@code authUrl} field is included only when {@code ezkey.qr.auth-base-url} is configured.
 * Payload composition is handled by {@link QrCodePayloadService}.
 *
 * @author Ezkey contributors
 * @since 2025
 * @see QrCodePayloadService
 */
@Service
public class QrCodeGeneratorService {

  /**
   * Generates a QR code image as PNG byte array.
   *
   * <p>The QR code is generated with UTF-8 encoding and minimal margin.
   *
   * @param content the content to encode in QR code (e.g., "4|abc123...")
   * @param width the width of the QR code in pixels (recommended: 300)
   * @param height the height of the QR code in pixels (recommended: 300)
   * @return byte array containing PNG image data
   * @throws WriterException if QR code generation fails (e.g., content too large)
   * @throws IOException if PNG image conversion fails
   */
  public byte[] generateQrCodeImage(String content, int width, int height)
      throws WriterException, IOException {

    QRCodeWriter qrCodeWriter = new QRCodeWriter();

    // Configure QR code hints
    Map<EncodeHintType, Object> hints = new HashMap<>();
    hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
    hints.put(EncodeHintType.MARGIN, 1); // Minimal white border

    // Generate QR code bit matrix
    BitMatrix bitMatrix = qrCodeWriter.encode(content, BarcodeFormat.QR_CODE, width, height, hints);

    // Convert to PNG image
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream);

    return outputStream.toByteArray();
  }
}
