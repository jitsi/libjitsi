find_package(PkgConfig)
pkg_check_modules(PC_SPEEXDSP QUIET speexdsp)
set(SPEEXDSP_DEFINITIONS ${PC_SPEEXDSP_CFLAGS_OTHER})

find_path(SPEEXDSP_INCLUDE_DIR
          NAMES speex/speex_resampler.h
          HINTS ${PC_SPEEXDSP_INCLUDEDIR} ${PC_SPEEXDSP_INCLUDE_DIRS}
          PATHS include
          )

find_library(SPEEXDSP_LIBRARY NAMES speexdsp
             HINTS ${PC_SPEEXDSP_LIBDIR} ${PC_SPEEXDSP_LIBRARY_DIRS})

include(FindPackageHandleStandardArgs)
find_package_handle_standard_args(speexdsp DEFAULT_MSG
  SPEEXDSP_LIBRARY SPEEXDSP_INCLUDE_DIR)

mark_as_advanced(SPEEXDSP_INCLUDE_DIR SPEEXDSP_LIBRARY )

set(SPEEXDSP_LIBRARIES ${SPEEXDSP_LIBRARY})
set(SPEEXDSP_INCLUDE_DIRS ${SPEEXDSP_INCLUDE_DIR})
