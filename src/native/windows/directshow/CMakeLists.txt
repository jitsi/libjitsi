project(jndirectshow CXX)

add_library(${PROJECT_NAME} SHARED
            DSCaptureDevice.cpp
            DSManager.cpp
            org_jitsi_impl_neomedia_jmfext_media_protocol_directshow_DSCaptureDevice.cpp
            org_jitsi_impl_neomedia_jmfext_media_protocol_directshow_DSFormat.cpp
            org_jitsi_impl_neomedia_jmfext_media_protocol_directshow_DSManager.cpp
            directshow.rc)

target_link_libraries(${PROJECT_NAME} LINK_PUBLIC
                      ole32
                      oleaut32
                      strmiids
                      uuid
                      )

target_include_directories(${PROJECT_NAME} PUBLIC
                           ${LIBJITSI_JAVAH}
                           ${JNI_INCLUDE_DIRS}
                           ${CMAKE_BINARY_DIR}/include/
                           )

install(TARGETS ${PROJECT_NAME}
        RUNTIME DESTINATION ".")
