find_path(opus_INCLUDE_DIR
          NAMES opus/opus.h
          PATHS include
          )

find_library(opus_LIBRARY NAMES opus)

include(FindPackageHandleStandardArgs)
find_package_handle_standard_args(Opus opus_INCLUDE_DIR opus_LIBRARY)
