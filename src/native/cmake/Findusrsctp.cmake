find_package(PkgConfig)
pkg_check_modules(PC_USRSCTP QUIET usrsctp)
set(USRSCTP_DEFINITIONS ${PC_USRSCTP_CFLAGS_OTHER})

find_path(USRSCTP_INCLUDE_DIR
          NAMES usrsctp.h
          HINTS ${PC_USRSCTP_INCLUDEDIR} ${PC_USRSCTP_INCLUDE_DIRS}
          PATHS include
          )

find_library(USRSCTP_LIBRARY NAMES usrsctp
             HINTS ${PC_USRSCTP_LIBDIR} ${PC_USRSCTP_LIBRARY_DIRS})

include(FindPackageHandleStandardArgs)
find_package_handle_standard_args(usrsctp DEFAULT_MSG
  USRSCTP_LIBRARY USRSCTP_INCLUDE_DIR)

mark_as_advanced(USRSCTP_INCLUDE_DIR USRSCTP_LIBRARY)

set(USRSCTP_LIBRARIES ${USRSCTP_LIBRARY})
set(USRSCTP_INCLUDE_DIRS ${USRSCTP_INCLUDE_DIR})
