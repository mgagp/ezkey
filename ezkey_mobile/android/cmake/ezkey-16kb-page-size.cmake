# Ezkey #659 — 16 KB page-size GNU_RELRO padding for locally built native libs.
#
# NDK r28 defaults LOAD segments to max-page-size=16384, but LLD still pads
# PT_GNU_RELRO using common-page-size (4096) unless raised. Pixel's
# "RELRO alignment check failed" dialog keys off VirtAddr+MemSiz % 0x4000.
#
# Included via -DCMAKE_PROJECT_INCLUDE from android/build.gradle for every
# Android library/app CMake build. Do not use useLegacyPackaging as a substitute.
#
# Ref: https://developer.android.com/guide/practices/page-sizes

add_link_options(
  "-Wl,-z,max-page-size=16384"
  "-Wl,-z,common-page-size=16384"
)
