project(jnscreencapture C)

set(OS_SOURCES "")
if (WIN32)
    set(OS_SOURCES screencapture.rc)
elseif (UNIX AND NOT APPLE)
    include(CheckIncludeFiles)
    find_package(X11 REQUIRED)
    if (NOT X11_FOUND)
        message(FATAL_ERROR "libx11-dev is missing")
    endif ()
    if (NOT X11_Xext_FOUND)
        message(FATAL_ERROR "libxext-dev is missing")
    endif ()
endif ()

add_library(${PROJECT_NAME} SHARED
            org_jitsi_impl_neomedia_imgstreaming_ScreenCapture.c
            ${OS_SOURCES})

target_link_libraries(${PROJECT_NAME} LINK_PUBLIC
                      )

target_include_directories(${PROJECT_NAME} PUBLIC
                           ${LIBJITSI_JAVAH}
                           ${JNI_INCLUDE_DIRS}
                           ${CMAKE_BINARY_DIR}/include/
                           )

install(TARGETS ${PROJECT_NAME}
        RUNTIME DESTINATION ".")
