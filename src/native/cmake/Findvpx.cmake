find_path(vpx_INCLUDE_DIR
          NAMES vpx/vp8.h vpx/vpx_codec.h
          PATHS include
          )

find_library(vpx_LIBRARY NAMES vpx vpxmd)

include(FindPackageHandleStandardArgs)
find_package_handle_standard_args(vpx vpx_INCLUDE_DIR vpx_LIBRARY)
