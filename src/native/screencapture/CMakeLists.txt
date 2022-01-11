project(jnscreencapture C)

set(OS_SOURCES "")
set(OS_LIBS "")
if (WIN32)
    set(OS_SOURCES screencapture.rc)
elseif (APPLE)
    set(OS_LIBS "-framework ApplicationServices")
elseif (UNIX AND NOT APPLE)
    find_package(X11 REQUIRED)
    if (NOT X11_FOUND)
        message(FATAL_ERROR "libx11-dev is missing")
    endif ()
    if (NOT X11_Xext_FOUND)
        message(FATAL_ERROR "libxext-dev is missing")
    endif ()
    set(OS_LIBS
        ${X11_X11_LIB}
        ${X11_Xext_LIB}
        )
endif ()

add_library(${PROJECT_NAME} SHARED
            org_jitsi_impl_neomedia_imgstreaming_ScreenCapture.c
            ${OS_SOURCES})

target_link_libraries(${PROJECT_NAME} LINK_PUBLIC
                      ${JNI_LIBRARIES}
                      ${OS_LIBS}
                      )

target_include_directories(${PROJECT_NAME} PUBLIC
                           ${LIBJITSI_JAVAH}
                           ${JNI_INCLUDE_DIRS}
                           ${CMAKE_BINARY_DIR}/include/
                           )

if (JITSI_BUILD_DEBIAN)
    install(TARGETS ${PROJECT_NAME}
            LIBRARY DESTINATION ${CMAKE_INSTALL_LIBDIR})
else ()
    install(TARGETS ${PROJECT_NAME}
            RUNTIME DESTINATION "."
            LIBRARY DESTINATION ".")
endif ()
