find_package(PkgConfig)
pkg_check_modules(PC_OPUS QUIET opus)
set(OPUS_DEFINITIONS ${PC_OPUS_CFLAGS_OTHER})

find_path(OPUS_INCLUDE_DIR
          NAMES opus/opus.h
          HINTS ${PC_OPUS_INCLUDEDIR} ${PC_OPUS_INCLUDE_DIRS}
          PATHS include
          )

find_library(OPUS_LIBRARY NAMES opus
             HINTS ${PC_OPUS_LIBDIR} ${PC_OPUS_LIBRARY_DIRS})

include(FindPackageHandleStandardArgs)
find_package_handle_standard_args(opus OPUS_LIBRARY OPUS_INCLUDE_DIR)

mark_as_advanced(OPUS_INCLUDE_DIR OPUS_LIBRARY)

set(OPUS_LIBRARIES ${OPUS_LIBRARY})
set(OPUS_INCLUDE_DIRS ${OPUS_INCLUDE_DIR})
