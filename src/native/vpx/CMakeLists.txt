project(jnvpx CXX)

set(OS_SOURCES "")
set(OS_LIBS "")
set(DEFAULT_USE_SYSTEM_VPX 0)
if (WIN32)
    set(OS_SOURCES vpx.rc)
elseif (APPLE)
elseif (UNIX)
    set(DEFAULT_USE_SYSTEM_VPX 1)
endif ()

option(USE_SYSTEM_VPX "Use system-installed vpx" ${DEFAULT_USE_SYSTEM_VPX})

add_library(${PROJECT_NAME} SHARED
            org_jitsi_impl_neomedia_codec_video_VPX.c
            org_jitsi_impl_neomedia_recording_WebmWriter.cpp
            ${CMAKE_CURRENT_SOURCE_DIR}/../third_party/libmkv/EbmlWriter.c
            ${OS_SOURCES})

if (NOT ${USE_SYSTEM_VPX})
    find_package(unofficial-libvpx REQUIRED)
    list(APPEND OS_LIBS unofficial::libvpx::libvpx)
else ()
    find_package(vpx REQUIRED)
    list(APPEND OS_LIBS ${vpx_LIBRARY})
endif ()

target_link_libraries(${PROJECT_NAME} LINK_PUBLIC
                      ${JNI_LIBRARIES}
                      ${OS_LIBS})

target_include_directories(${PROJECT_NAME} PUBLIC
                           ${LIBJITSI_JAVAH}
                           ${JNI_INCLUDE_DIRS}
                           ${CMAKE_BINARY_DIR}/include/
                           ${CMAKE_CURRENT_SOURCE_DIR}/../third_party/)

if (JITSI_BUILD_DEBIAN)
    install(TARGETS ${PROJECT_NAME}
            LIBRARY DESTINATION ${CMAKE_INSTALL_LIBDIR})
else ()
    install(TARGETS ${PROJECT_NAME}
            RUNTIME DESTINATION "."
            LIBRARY DESTINATION ".")
endif ()
