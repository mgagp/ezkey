import React
import ReactAppDependencyProvider
import React_RCTAppDelegate
import UIKit

class SceneDelegate: RCTDefaultReactNativeFactoryDelegate, UIWindowSceneDelegate {
  var window: UIWindow?
  var reactNativeFactory: RCTReactNativeFactory?

  func scene(
    _ scene: UIScene,
    willConnectTo session: UISceneSession,
    options connectionOptions: UIScene.ConnectionOptions
  ) {
    guard let windowScene = scene as? UIWindowScene else {
      return
    }

    dependencyProvider = RCTAppDependencyProvider()
    reactNativeFactory = RCTReactNativeFactory(delegate: self)
    window = UIWindow(windowScene: windowScene)
    // Saved Appearance choice (#1188) before React Native starts, so the first frame already
    // uses the chosen palette (Android: per-app night mode, EzkeyAppearanceModule).
    window?.overrideUserInterfaceStyle = EzkeyAppearanceModule.savedInterfaceStyle()

    #if DEBUG
      let devMenuConfiguration = RCTDevMenuConfiguration(
        devMenuEnabled: true,
        shakeGestureEnabled: true,
        keyboardShortcutsEnabled: true
      )
      reactNativeFactory?.devMenuConfiguration = devMenuConfiguration
    #endif

    reactNativeFactory?.startReactNative(
      withModuleName: "EzkeyMobile",
      in: window,
      connectionOptions: connectionOptions
    )

  }

  func scene(_ scene: UIScene, openURLContexts urlContexts: Set<UIOpenURLContext>) {
    RCTLinkingManager.scene(scene, openURLContexts: urlContexts)
  }

  func scene(_ scene: UIScene, continue userActivity: NSUserActivity) {
    RCTLinkingManager.scene(scene, continue: userActivity)
  }

  override func bundleURL() -> URL? {
    #if DEBUG
      RCTBundleURLProvider.sharedSettings().jsBundleURL(forBundleRoot: "index")
    #else
      Bundle.main.url(forResource: "main", withExtension: "jsbundle")
    #endif
  }
}
