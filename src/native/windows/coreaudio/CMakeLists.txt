project(jnwincoreaudio CXX)

add_library(${PROJECT_NAME} SHARED
            device.cpp
            org_jitsi_impl_neomedia_device_CoreAudioDevice.cpp
            coreaudio.rc)

target_link_libraries(${PROJECT_NAME} LINK_PUBLIC
#                      strmiids
#                      uuid
                      )

target_include_directories(${PROJECT_NAME} PUBLIC
                           ${LIBJITSI_JAVAH}
                           ${JNI_INCLUDE_DIRS}
                           ${CMAKE_BINARY_DIR}/include/
                           )

install(TARGETS ${PROJECT_NAME}
        RUNTIME DESTINATION ".")
