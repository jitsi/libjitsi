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
    set(USRSCTP_INCLUDE_DIRS ${USRSCTP_ROOT}/include)
    set(USRSCTP_LIBRARY_DIRS ${USRSCTP_ROOT}/lib)

    externalproject_add(ext_lib_usrsctp
                        INSTALL_DIR ${USRSCTP_ROOT}
                        GIT_REPOSITORY https://github.com/sctplab/usrsctp
                        GIT_TAG 995c0b84414466d77d52011e5b572cbe213b770a
                        GIT_SHALLOW false
                        CMAKE_ARGS
                        -Dsctp_werror:BOOL=NO
                        -Dsctp_build_programs:BOOL=NO
                        -DCMAKE_INSTALL_PREFIX:PATH=<INSTALL_DIR>
                        -DCMAKE_BUILD_TYPE=${CMAKE_BUILD_TYPE}
                        -DCMAKE_TOOLCHAIN_FILE=${EXTPROJECT_CMAKE_TOOLCHAIN_FILE}
                        -DCMAKE_POSITION_INDEPENDENT_CODE=ON
                        -DCMAKE_BUILD_TYPE=release
                        )

    set(USRSCTP_LIBRARIES ${USRSCTP_LIBRARY_DIRS}/${CMAKE_STATIC_LIBRARY_PREFIX}usrsctp${CMAKE_STATIC_LIBRARY_SUFFIX})
endif ()

add_library(${PROJECT_NAME} SHARED
            org_jitsi_sctp4j_Sctp.c
            ${OS_SOURCES})

if (NOT ${USE_SYSTEM_USRSCTP})
    add_dependencies(${PROJECT_NAME} ext_lib_usrsctp)
else ()
    find_package(usrsctp REQUIRED)
endif ()

target_link_libraries(${PROJECT_NAME} LINK_PUBLIC
                      ${JNI_LIBRARIES}
                      ${OS_LIBS}
                      ${USRSCTP_LIBRARIES}
                      )

target_include_directories(${PROJECT_NAME} PUBLIC
                           ${LIBJITSI_JAVAH}
                           ${JNI_INCLUDE_DIRS}
                           ${CMAKE_BINARY_DIR}/include/
                           ${USRSCTP_INCLUDE_DIRS})

if (JITSI_BUILD_DEBIAN)
    install(TARGETS ${PROJECT_NAME}
            LIBRARY DESTINATION ${CMAKE_INSTALL_LIBDIR})
else ()
    install(TARGETS ${PROJECT_NAME}
            RUNTIME DESTINATION "."
            LIBRARY DESTINATION ".")
endif ()
