project(jnwasapi CXX)

add_library(${PROJECT_NAME} SHARED
            HResultException.cpp
            MediaBuffer.cpp
            org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_VoiceCaptureDSP.cpp
            org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_WASAPI.cpp
            Typecasting.cpp
            wasapi.rc)

target_link_libraries(${PROJECT_NAME} LINK_PUBLIC
                      dmoguids
                      msdmo
                      ole32
                      propsys
                      )

target_include_directories(${PROJECT_NAME} PUBLIC
                           ${LIBJITSI_JAVAH}
                           ${JNI_INCLUDE_DIRS}
                           ${CMAKE_BINARY_DIR}/include/
                           )

install(TARGETS ${PROJECT_NAME}
        RUNTIME DESTINATION ".")
