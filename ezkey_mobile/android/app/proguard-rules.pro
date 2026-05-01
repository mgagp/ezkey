# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /usr/local/Cellar/android-sdk/24.3.3/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# ----- Ezkey app rules ------------------------------------------------------
# React Native, Hermes, MLKit, Vision Camera, Keychain, AsyncStorage and other
# RN ecosystem libraries ship their own consumer ProGuard rules through their
# AAR consumer-rules.pro files, so they are applied automatically.
#
# The rules below cover Ezkey-specific concerns:
#   - The native EzkeyCryptoModule is invoked from JS via @ReactMethod reflection,
#     so its public surface must survive shrinking.
#   - Conscrypt registers Ed25519 providers at runtime through reflection.
#
# When you add a new native module or a new reflective dependency, add a -keep
# rule here, then re-run `bundletool install-apks` and exercise the affected
# screen end-to-end.

-keep class org.ezkey.mobile.crypto.** { *; }

-keep class org.conscrypt.** { *; }
-dontwarn org.conscrypt.**

# RN 0.80 can still initialize inspector flags on startup; R8 must not strip
# or rename the native bridge class loaded from libreact_devsupportjni.
-keep class com.facebook.react.devsupport.CxxInspectorPackagerConnection { *; }
-keep class com.facebook.react.devsupport.InspectorFlags { *; }

# Strip android.util.Log debug/verbose calls from release binaries.
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
}

