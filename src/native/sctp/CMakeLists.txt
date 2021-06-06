project(jnsctp C)

set(OS_SOURCES "")
set(OS_LIBS "")
set(DEFAULT_USE_SYSTEM_USRSCTP 0)
if (WIN32)
    set(OS_SOURCES sctp.rc)
    set(OS_LIBS Ws2_32)
elseif (APPLE)
    set(OS_LIBS pthread)
elseif (UNIX)
    set(OS_LIBS pthread)
    set(DEFAULT_USE_SYSTEM_USRSCTP 1)
endif ()

option(USE_SYSTEM_USRSCTP "Use system-installed usrsctp" ${DEFAULT_USE_SYSTEM_USRSCTP})

if (NOT ${USE_SYSTEM_USRSCTP})
    set(USRSCTP_ROOT ${CMAKE_BINARY_DIR}/usrsctp)
    set(libusrsctp_INCLUDE_DIRS ${USRSCTP_ROOT}/include)
    set(libusrsctp_LIBRARY_DIRS ${USRSCTP_ROOT}/lib)

    externalproject_add(ext_lib_usrsctp
                        INSTALL_DIR ${USRSCTP_ROOT}
                        GIT_REPOSITORY https://github.com/sctplab/usrsctp
                        GIT_TAG 995c0b84414466d77d52011e5b572cbe213b770a
                        GIT_SHALLOW false
                        CMAKE_ARGS -Dsctp_werror:BOOL=NO -Dsctp_build_programs:BOOL=NO -DCMAKE_INSTALL_PREFIX:PATH=<INSTALL_DIR>
                        )
endif ()

add_library(${PROJECT_NAME} SHARED
            org_jitsi_sctp4j_Sctp.c
            ${OS_SOURCES})

target_link_directories(${PROJECT_NAME}
                        PRIVATE ${libusrsctp_LIBRARY_DIRS})

target_link_libraries(${PROJECT_NAME} LINK_PUBLIC
                      ${OS_LIBS}
                      usrsctp
                      )

if (NOT ${USE_SYSTEM_USRSCTP})
    add_dependencies(${PROJECT_NAME} ext_lib_usrsctp)
else ()
    find_package(usrsctp REQUIRED)
endif ()

target_include_directories(${PROJECT_NAME} PUBLIC
                           ${LIBJITSI_JAVAH}
                           ${JNI_INCLUDE_DIRS}
                           ${CMAKE_BINARY_DIR}/include/
                           ${libusrsctp_INCLUDE_DIRS})

install(TARGETS ${PROJECT_NAME}
        RUNTIME DESTINATION "."
        LIBRARY DESTINATION ".")
