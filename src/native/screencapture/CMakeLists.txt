project(jnscreencapture C)

set(OS_SOURCES "")
set(OS_LIBS "")
set(MACOS_COMPILE_FLAGS "")
if (WIN32)
    set(OS_SOURCES screencapture.rc)
elseif (APPLE)
    set(OS_LIBS "-framework ApplicationServices")
    # CGWindowListCreateImage is deprecated/unavailable in macOS 15.0+
    # Set deployment target to 14.0 to allow using this API
    # TODO: Migrate to ScreenCaptureKit for macOS 15+ support
    set(MACOS_COMPILE_FLAGS "-mmacosx-version-min=14.0" "-Wno-deprecated-declarations" "-Wno-unguarded-availability")
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

if (APPLE)
    target_compile_options(${PROJECT_NAME} PRIVATE ${MACOS_COMPILE_FLAGS})
endif ()

if (JITSI_BUILD_DEBIAN)
    install(TARGETS ${PROJECT_NAME}
            LIBRARY DESTINATION ${CMAKE_INSTALL_LIBDIR})
else ()
    install(TARGETS ${PROJECT_NAME}
            RUNTIME DESTINATION "."
            LIBRARY DESTINATION ".")
endif ()
