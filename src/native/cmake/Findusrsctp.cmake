find_path(usrsctp_INCLUDE_DIR
          NAMES usrsctp.h
          PATHS include
          )

find_library(usrsctp_LIBRARY NAMES usrsctp)

include(FindPackageHandleStandardArgs)
find_package_handle_standard_args(usrsctp usrsctp_INCLUDE_DIR usrsctp_LIBRARY)
