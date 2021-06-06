find_path(speex_INCLUDE_DIR
          NAMES speex/speex.h
          PATHS include
          )

find_library(speex_LIBRARY NAMES speex)

include(FindPackageHandleStandardArgs)
find_package_handle_standard_args(speex speex_INCLUDE_DIR speexdsp_LIBRARY)
