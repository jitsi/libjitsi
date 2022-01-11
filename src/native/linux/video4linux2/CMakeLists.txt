project(jnvideo4linux2 C)

include(CheckIncludeFiles)
check_include_files(linux/videodev2.h HAVE_V4L)

add_library(${PROJECT_NAME} SHARED
            org_jitsi_impl_neomedia_jmfext_media_protocol_video4linux2_Video4Linux2.c)

target_link_libraries(${PROJECT_NAME} LINK_PUBLIC
                      ${JNI_LIBRARIES}
                      )

target_include_directories(${PROJECT_NAME} PUBLIC
                           ${LIBJITSI_JAVAH}
                           ${JNI_INCLUDE_DIRS}
                           ${CMAKE_BINARY_DIR}/include/)

if (JITSI_BUILD_DEBIAN)
    install(TARGETS ${PROJECT_NAME}
            LIBRARY DESTINATION ${CMAKE_INSTALL_LIBDIR})
else ()

    install(TARGETS ${PROJECT_NAME}
            RUNTIME DESTINATION "."
            LIBRARY DESTINATION ".")
endif ()
