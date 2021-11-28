find_package(PkgConfig)
pkg_check_modules(PC_SPEEX QUIET speex)
set(SPEEX_DEFINITIONS ${PC_SPEEX_CFLAGS_OTHER})

find_path(SPEEX_INCLUDE_DIR
          NAMES speex/speex.h
          HINTS ${PC_SPEEX_INCLUDEDIR} ${PC_SPEEX_INCLUDE_DIRS}
          PATHS include
          )

find_library(SPEEX_LIBRARY NAMES speex
             HINTS ${PC_SPEEX_LIBDIR} ${PC_SPEEX_LIBRARY_DIRS})

include(FindPackageHandleStandardArgs)
find_package_handle_standard_args(speex DEFAULT_MSG
  SPEEX_LIBRARY SPEEX_INCLUDE_DIR)

mark_as_advanced(SPEEX_INCLUDE_DIR SPEEX_LIBRARY)

set(SPEEX_LIBRARIES ${SPEEX_LIBRARY})
set(SPEEX_INCLUDE_DIRS ${SPEEX_INCLUDE_DIR})
