package com.ezkeymobile.qr

import android.util.Log
import com.facebook.react.bridge.WritableNativeArray
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.mrousavy.camera.frameprocessors.Frame
import com.mrousavy.camera.frameprocessors.FrameProcessorPlugin
import com.mrousavy.camera.core.types.Orientation

class EzkeyQrFrameProcessorPlugin : FrameProcessorPlugin() {

  private val options =
      BarcodeScannerOptions.Builder()
          .setBarcodeFormats(
              Barcode.FORMAT_QR_CODE,
              Barcode.FORMAT_AZTEC,
              Barcode.FORMAT_DATA_MATRIX,
              Barcode.FORMAT_PDF417,
              Barcode.FORMAT_EAN_13,
          )
          .build()

  private val scanner = BarcodeScanning.getClient(options)

  override fun callback(frame: Frame, params: Map<String, Any>?): Any? {
    return try {
      val mediaImage = frame.image
      val rotationDegrees = frame.orientation.toRotationDegrees()
      val inputImage = InputImage.fromMediaImage(mediaImage, rotationDegrees)
      val barcodes = Tasks.await(scanner.process(inputImage))
      if (barcodes.isEmpty()) {
        null
      } else {
        val array = WritableNativeArray()
        for (barcode in barcodes) {
          barcode.rawValue?.let { array.pushString(it) }
        }
        if (array.size() > 0) array else null
      }
    } catch (error: Throwable) {
      Log.e(TAG, "Failed to process frame", error)
      null
    }
  }

  companion object {
    const val NAME = "scanEzkey"
    private const val TAG = "EzkeyQrPlugin"
  }
}

private fun Orientation.toRotationDegrees(): Int =
    when (this) {
      Orientation.PORTRAIT -> 0
      Orientation.LANDSCAPE_RIGHT -> 90
      Orientation.PORTRAIT_UPSIDE_DOWN -> 180
      Orientation.LANDSCAPE_LEFT -> 270
    }

