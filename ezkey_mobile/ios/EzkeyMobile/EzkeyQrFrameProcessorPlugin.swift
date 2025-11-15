import Foundation
import MLKitBarcodeScanning
import MLKitVision
import VisionCamera

@objc(EzkeyQrFrameProcessorPlugin)
class EzkeyQrFrameProcessorPlugin: FrameProcessorPlugin {
  private let scanner: BarcodeScanner

  override init(proxy: VisionCameraProxyHolder, options: [AnyHashable: Any]?) {
    let formats: BarcodeFormat = [
      .qrCode,
      .aztec,
      .dataMatrix,
      .pdf417,
      .ean13,
    ]
    let scannerOptions = BarcodeScannerOptions(formats: formats)
    scanner = BarcodeScanner.barcodeScanner(options: scannerOptions)
    super.init(proxy: proxy, options: options)
  }

  override func callback(_ frame: Frame, withArguments arguments: [AnyHashable: Any]?) -> Any? {
    do {
      let visionImage = VisionImage(buffer: frame.buffer)
      visionImage.orientation = frame.orientation
      let barcodes = try scanner.results(in: visionImage)
      if barcodes.isEmpty {
        return nil
      }
      return barcodes.compactMap { $0.rawValue }
    } catch {
      NSLog("[EzkeyQrPlugin] Failed to process frame: %@", error.localizedDescription)
      return nil
    }
  }
}

VISION_EXPORT_SWIFT_FRAME_PROCESSOR(EzkeyQrFrameProcessorPlugin, scanEzkey)

