project(jnpulseaudio C)

find_package(PulseAudio REQUIRED)

add_library(${PROJECT_NAME} SHARED
            org_jitsi_impl_neomedia_pulseaudio_PA.c)

target_link_libraries(${PROJECT_NAME} LINK_PUBLIC pulse)

target_include_directories(${PROJECT_NAME} PUBLIC
                           ${LIBJITSI_JAVAH}
                           ${JNI_INCLUDE_DIRS}
                           ${CMAKE_BINARY_DIR}/include/)

install(TARGETS ${PROJECT_NAME}
        RUNTIME DESTINATION "."
        LIBRARY DESTINATION ".")
