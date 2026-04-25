/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * File: EzkeyQrFrameProcessorPlugin.kt
 * Description: Frame processor plugin that decodes Ezkey enrollment QR payloads for the Vision
 * Camera bridge.
 * Security Context: Ensures QR decoding follows the enrollment handshake outlined in
 * docs/ENDPOINT.md by returning only raw values without persisting images or metadata.
 * @since 2025
 */

package org.ezkey.mobile.qr

import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.mrousavy.camera.frameprocessors.Frame
import com.mrousavy.camera.frameprocessors.FrameProcessorPlugin
import com.mrousavy.camera.core.types.Orientation

/**
 * ML Kit powered frame processor that extracts enrollment QR payloads for the React Native layer.
 *
 * @since 2025
 */
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

  /**
   * Processes camera frames to return decoded QR payloads.
   *
   * @since 2025
   */
  override fun callback(frame: Frame, params: Map<String, Any>?): Any? {
    return try {
      val mediaImage = frame.image
      val rotationDegrees = frame.orientation.toRotationDegrees()
      val inputImage = InputImage.fromMediaImage(mediaImage, rotationDegrees)
      val barcodes = Tasks.await(scanner.process(inputImage))
      if (barcodes.isEmpty()) return null
      val values = ArrayList<String>(barcodes.size)
      for (barcode in barcodes) {
        barcode.rawValue?.let { values.add(it) }
      }
      if (values.isEmpty()) null else values
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

/**
 * Converts orientation metadata into rotation degrees accepted by ML Kit.
 *
 * @since 2025
 */
private fun Orientation.toRotationDegrees(): Int =
    when (this) {
      Orientation.PORTRAIT -> 0
      Orientation.LANDSCAPE_RIGHT -> 90
      Orientation.PORTRAIT_UPSIDE_DOWN -> 180
      Orientation.LANDSCAPE_LEFT -> 270
    }

