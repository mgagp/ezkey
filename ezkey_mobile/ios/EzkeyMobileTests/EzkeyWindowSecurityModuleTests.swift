//
// Ezkey - Open Source Cryptographic MFA Platform
//
// Copyright (c) 2026 Ezkey contributors
// Licensed under the MIT License. See LICENSE file in the project root for full license information.
//

import UIKit
import XCTest

@testable import EzkeyMobile

private final class RecordingRenderer: EzkeySensitiveScreenRendering {
  var covers: [EzkeySensitiveScreenCover] = []

  func render(_ cover: EzkeySensitiveScreenCover, in scene: UIWindowScene?) {
    covers.append(cover)
  }
}

final class EzkeyWindowSecurityModuleTests: XCTestCase {

  private var center: NotificationCenter!
  private var renderer: RecordingRenderer!
  private var isActive = true
  private var isCaptured = false
  private var controller: EzkeySensitiveScreenController!

  override func setUp() {
    super.setUp()
    center = NotificationCenter()
    renderer = RecordingRenderer()
    isActive = true
    isCaptured = false
    controller = EzkeySensitiveScreenController(
      center: center,
      scene: { [unowned self] in
        .init(scene: nil, isActive: self.isActive, isCaptured: self.isCaptured)
      },
      renderer: renderer
    )
    controller.captureMessage = "stop"
  }

  func testNoCoverWhileActiveAndNotCaptured() {
    controller.setEnabled(true)
    XCTAssertEqual(renderer.covers.last, EzkeySensitiveScreenCover.none)
  }

  func testCoverIsUpBeforeTheAppSwitcherSnapshotAndGoesAwayOnReturn() {
    controller.setEnabled(true)
    center.post(name: UIScene.willDeactivateNotification, object: nil)
    XCTAssertEqual(renderer.covers.last, .blank)
    center.post(name: UIScene.didActivateNotification, object: nil)
    XCTAssertEqual(renderer.covers.last, EzkeySensitiveScreenCover.none)
  }

  func testOtherScreensStayUncovered() {
    center.post(name: UIScene.willDeactivateNotification, object: nil)
    XCTAssertEqual(renderer.covers.last, EzkeySensitiveScreenCover.none)
  }

  func testDisablingClearsTheCover() {
    controller.setEnabled(true)
    center.post(name: UIScene.willDeactivateNotification, object: nil)
    controller.setEnabled(false)
    XCTAssertEqual(renderer.covers.last, EzkeySensitiveScreenCover.none)
  }

  func testCaptureStartingOnTheScreenShowsTheNoticeAndStoppingClearsIt() {
    controller.setEnabled(true)
    isCaptured = true
    center.post(name: UIScreen.capturedDidChangeNotification, object: nil)
    XCTAssertEqual(renderer.covers.last, .captureNotice("stop"))
    isCaptured = false
    center.post(name: UIScreen.capturedDidChangeNotification, object: nil)
    XCTAssertEqual(renderer.covers.last, EzkeySensitiveScreenCover.none)
  }

  func testCaptureAlreadyRunningWhenTheScreenOpensShowsTheNotice() {
    isCaptured = true
    controller.setEnabled(true)
    XCTAssertEqual(renderer.covers.last, .captureNotice("stop"))
  }

  func testAppInactiveWhenTheScreenOpensIsCovered() {
    isActive = false
    controller.setEnabled(true)
    XCTAssertEqual(renderer.covers.last, .blank)
  }

  func testCaptureNoticeWinsOverInactive() {
    XCTAssertEqual(
      EzkeySensitiveScreenController.cover(
        enabled: true, inactive: true, captured: true, message: "stop"),
      .captureNotice("stop"))
  }

  /// The real window, in the test host's scene: above alerts, and gone once cleared.
  func testRendererShowsTheNoticeAboveAlertsAndRemovesIt() throws {
    let scene = try XCTUnwrap(EzkeySensitiveScreenController.foregroundScene().scene)
    let renderer = EzkeyCoverWindowRenderer()

    renderer.render(.captureNotice("stop"), in: scene)
    let window = try XCTUnwrap(renderer.window)
    XCTAssertFalse(window.isHidden)
    XCTAssertGreaterThan(window.windowLevel, .alert)
    XCTAssertTrue(window.isUserInteractionEnabled)
    XCTAssertEqual(renderer.label.text, "stop")

    renderer.render(.blank, in: scene)
    XCTAssertFalse(window.isHidden)
    XCTAssertTrue(renderer.label.isHidden)

    renderer.render(.none, in: scene)
    XCTAssertTrue(window.isHidden)
    XCTAssertNil(renderer.window)
  }
}
