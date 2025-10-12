package expo.modules.cryptonative

import android.content.Context
import expo.modules.core.BasePackage

class ExpoCryptoNativePackage : BasePackage() {
  override fun createInternalModules(context: Context) =
    listOf(ExpoCryptoNativeModule())
}
