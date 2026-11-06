//
// Ezkey - Open Source Cryptographic MFA Platform
//
// Copyright (c) 2026 Ezkey contributors
// Licensed under the MIT License. See LICENSE file in the project root for full license information.
//
// Module: EzkeyWindowSecurityModule (iOS)
// Description: iOS counterpart of the Android EzkeyWindowSecurityModule (#1154), same module name.
//              iOS has no FLAG_SECURE: while the pending-auth screen is focused, a cover hides the
//              request from the app switcher, and from screen recording or mirroring. Screenshots
//              cannot be blocked with a public API. Third-party overlays do not exist on iOS.
// Security Context: Defense in depth and fail-open, like Android. The authentication guarantee
//              rests on the enrollment key signature.
//

import Foundation
import React
import UIKit

@objc(EzkeyWindowSecurityModule)
final class EzkeyWindowSecurityModule: NSObject {

  private static let controller = EzkeySensitiveScreenController(
    center: .default,
    scene: EzkeySensitiveScreenController.foregroundScene,
    renderer: EzkeyCoverWindowRenderer()
  )

  @objc static func requiresMainQueueSetup() -> Bool {
    false
  }

  /// Turns the protections on or off.
  ///
  /// - Parameter enabled: true while the pending-auth screen is focused
  @objc(setSensitiveScreen:)
  func setSensitiveScreen(_ enabled: Bool) {
    DispatchQueue.main.async {
      Self.controller.setEnabled(enabled)
    }
  }

  /// Sets the text shown while the screen is recorded or mirrored, in the app language.
  ///
  /// - Parameter message: translated text from JS
  @objc(setCaptureMessage:)
  func setCaptureMessage(_ message: String) {
    DispatchQueue.main.async {
      Self.controller.captureMessage = message
    }
  }
}

/// What the sensitive-screen cover shows.
enum EzkeySensitiveScreenCover: Equatable {
  case none
  /// Hides the request from the app switcher thumbnail.
  case blank
  /// Hides the request from a recording or mirror. Also blocks Approve and Deny, which the user
  /// could not see, until the capture stops.
  case captureNotice(String)
}

/// Draws a cover state. Separate from the state logic so tests need no window.
protocol EzkeySensitiveScreenRendering: AnyObject {
  func render(_ cover: EzkeySensitiveScreenCover, in scene: UIWindowScene?)
}

/// Decides when the cover shows. Call on the main thread.
final class EzkeySensitiveScreenController {

  /// Scene state the controller reads when it is enabled.
  struct SceneState {
    let scene: UIWindowScene?
    let isActive: Bool
    let isCaptured: Bool
  }

  var captureMessage = "Stop recording or mirroring the screen to answer this request."

  private let center: NotificationCenter
  private let scene: () -> SceneState
  private let renderer: EzkeySensitiveScreenRendering
  private var enabled = false
  private var inactive = false
  private var captured = false
  private var observers: [NSObjectProtocol] = []

  init(
    center: NotificationCenter,
    scene: @escaping () -> SceneState,
    renderer: EzkeySensitiveScreenRendering
  ) {
    self.center = center
    self.scene = scene
    self.renderer = renderer
    // activationState can still read active when this notification arrives, so it is tracked here.
    // The cover must be up before the system takes the app switcher snapshot.
    observe(UIScene.willDeactivateNotification) { $0.inactive = true }
    observe(UIScene.didActivateNotification) { $0.inactive = false }
    observe(UIScreen.capturedDidChangeNotification) { $0.captured = $0.scene().isCaptured }
  }

  deinit {
    observers.forEach(center.removeObserver)
  }

  /// Turns the protections on or off, starting from the scene's current state.
  func setEnabled(_ enabled: Bool) {
    self.enabled = enabled
    if enabled {
      let state = scene()
      inactive = !state.isActive
      captured = state.isCaptured
    }
    refresh()
  }

  private func observe(
    _ name: Notification.Name, update: @escaping (EzkeySensitiveScreenController) -> Void
  ) {
    let observer = center.addObserver(forName: name, object: nil, queue: nil) { [weak self] _ in
      guard let self else { return }
      update(self)
      self.refresh()
    }
    observers.append(observer)
  }

  /// The cover for a given state. A capture wins: it also needs the notice while the app is active.
  static func cover(
    enabled: Bool, inactive: Bool, captured: Bool, message: String
  ) -> EzkeySensitiveScreenCover {
    guard enabled else { return .none }
    if captured { return .captureNotice(message) }
    return inactive ? .blank : .none
  }

  private func refresh() {
    let cover = Self.cover(
      enabled: enabled, inactive: inactive, captured: captured, message: captureMessage)
    renderer.render(cover, in: scene().scene)
  }

  /// State of the app's window scene (the app declares a single scene).
  static func foregroundScene() -> SceneState {
    let windowScene = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first
    return SceneState(
      scene: windowScene,
      isActive: windowScene?.activationState == .foregroundActive,
      isCaptured: windowScene?.screen.isCaptured ?? false
    )
  }
}

/// Shows the cover in its own window, above React Native's window and its alerts. It catches every
/// touch while visible.
final class EzkeyCoverWindowRenderer: EzkeySensitiveScreenRendering {

  private(set) var window: UIWindow?
  let label = UILabel()

  func render(_ cover: EzkeySensitiveScreenCover, in scene: UIWindowScene?) {
    guard cover != .none, let scene else {
      window?.isHidden = true
      window = nil
      return
    }
    let coverWindow = window ?? makeWindow(in: scene)
    window = coverWindow
    coverWindow.overrideUserInterfaceStyle = EzkeyAppearanceModule.savedInterfaceStyle()
    if case .captureNotice(let message) = cover {
      label.text = message
      label.isHidden = false
    } else {
      label.text = nil
      label.isHidden = true
    }
    coverWindow.isHidden = false
    if !label.isHidden {
      UIAccessibility.post(notification: .screenChanged, argument: label)
    }
  }

  private func makeWindow(in scene: UIWindowScene) -> UIWindow {
    let coverWindow = UIWindow(windowScene: scene)
    coverWindow.windowLevel = .alert + 1
    let controller = UIViewController()
    controller.view.backgroundColor = UIColor(named: "AppBackground")
    controller.view.accessibilityViewIsModal = true
    label.numberOfLines = 0
    label.textAlignment = .center
    label.textColor = .label
    label.font = .preferredFont(forTextStyle: .body)
    label.adjustsFontForContentSizeCategory = true
    label.translatesAutoresizingMaskIntoConstraints = false
    controller.view.addSubview(label)
    let margins = controller.view.layoutMarginsGuide
    NSLayoutConstraint.activate([
      label.centerYAnchor.constraint(equalTo: controller.view.centerYAnchor),
      label.leadingAnchor.constraint(equalTo: margins.leadingAnchor),
      label.trailingAnchor.constraint(equalTo: margins.trailingAnchor),
    ])
    coverWindow.rootViewController = controller
    return coverWindow
  }
}
