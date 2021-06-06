project(jnawtrenderer C)

set(OS_SOURCES "")
set(OS_LIBS = "")
if (WIN32)
    set(OS_SOURCES
        JAWTRenderer_Windows.c
        awtrenderer.rc)
    set(OS_LIBS "d3d9")
elseif (APPLE)
    set(OS_SOURCES JAWTRenderer_MacOSX.m)
    set(OS_LIBS
        "-framework OpenGL"
        "-framework Foundation"
        "-framework AppKit"
        "-framework QuartzCore")
elseif (UNIX)
    find_package(X11 REQUIRED)
    if (NOT X11_FOUND)
        message(FATAL_ERROR "libx11-dev is missing")
    endif ()
    if (NOT X11_Xv_FOUND)
        message(FATAL_ERROR "libxv-dev is missing")
    endif ()
    set(OS_SOURCES JAWTRenderer_Linux.c)
    set(OS_LIBS
        "Xv"
        "X11")
endif ()

add_library(${PROJECT_NAME} SHARED
            ${OS_SOURCES}
            org_jitsi_impl_neomedia_jmfext_media_renderer_video_JAWTRenderer.c
            )

target_link_libraries(${PROJECT_NAME} LINK_PUBLIC
                      ${JNI_LIBRARIES}
                      ${OS_LIBS}
                      )

target_include_directories(${PROJECT_NAME} PUBLIC
                           ${LIBJITSI_JAVAH}
                           ${JNI_INCLUDE_DIRS}
                           ${CMAKE_BINARY_DIR}/include/
                           )

install(TARGETS ${PROJECT_NAME}
        RUNTIME DESTINATION "."
        LIBRARY DESTINATION ".")
