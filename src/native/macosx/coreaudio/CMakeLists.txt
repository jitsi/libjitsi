project(jnmaccoreaudio C)

add_library(${PROJECT_NAME} SHARED
        device.c
        libjitsi_webrtc_aec.cc
        maccoreaudio_util.c
        org_jitsi_impl_neomedia_device_CoreAudioDevice.c
        )

target_link_libraries(${PROJECT_NAME} LINK_PUBLIC
        "-framework Foundation"
        "-framework Coreaudio"
        "-framework AudioToolbox"
        )

target_include_directories(${PROJECT_NAME} PUBLIC
        ${LIBJITSI_JAVAH}
        ${JNI_INCLUDE_DIRS}
        ${CMAKE_BINARY_DIR}/include/
        )

install(TARGETS ${PROJECT_NAME}
        RUNTIME DESTINATION "."
        LIBRARY DESTINATION ".")
