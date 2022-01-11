project(jnpulseaudio C)

find_package(PulseAudio REQUIRED)

add_library(${PROJECT_NAME} SHARED
            org_jitsi_impl_neomedia_pulseaudio_PA.c)

target_link_libraries(${PROJECT_NAME} LINK_PUBLIC
                      ${JNI_LIBRARIES}
                      dl
                      pulse)

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
