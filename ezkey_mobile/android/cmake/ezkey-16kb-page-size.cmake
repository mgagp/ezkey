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

# Re-apply on every target at directory end. CMAKE_PROJECT_INCLUDE runs at the
# end of project(), before add_library in the same CMakeLists; directory-level
# add_link_options should inherit, but some RN autolink CMakeLists reorder
# project()/cmake_minimum_required and clear flags — target_link_options is
# the durable backstop.
if(CMAKE_VERSION VERSION_GREATER_EQUAL "3.19")
  function(ezkey_16kb_apply_link_options)
    get_property(_ezkey_targets DIRECTORY PROPERTY BUILDSYSTEM_TARGETS)
    foreach(_t IN LISTS _ezkey_targets)
      get_target_property(_type ${_t} TYPE)
      if(_type STREQUAL "SHARED_LIBRARY"
         OR _type STREQUAL "MODULE_LIBRARY"
         OR _type STREQUAL "EXECUTABLE")
        target_link_options(
          ${_t}
          PRIVATE
          "-Wl,-z,max-page-size=16384"
          "-Wl,-z,common-page-size=16384")
      endif()
    endforeach()
  endfunction()
  cmake_language(
    DEFER
    DIRECTORY
    "${CMAKE_SOURCE_DIR}"
    CALL
    ezkey_16kb_apply_link_options)
endif()
