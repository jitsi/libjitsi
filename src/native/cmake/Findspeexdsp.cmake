find_path(speexdsp_INCLUDE_DIR
          NAMES speex/speexdsp_types.h
          PATHS include
          )

find_library(speexdsp_LIBRARY NAMES speexdsp)

include(FindPackageHandleStandardArgs)
find_package_handle_standard_args(speexdsp speexdsp_INCLUDE_DIR speexdsp_LIBRARY)
