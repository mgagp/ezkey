import QRCode from 'qrcode';

/**
 * Renders the enrollment QR payload as a PNG data URL (same pixel size as server-generated PNGs).
 */
export async function enrollmentPayloadToQrDataUrl(payloadJson: string): Promise<string> {
  return QRCode.toDataURL(payloadJson, {
    width: 300,
    margin: 2,
    errorCorrectionLevel: 'M',
  });
}
