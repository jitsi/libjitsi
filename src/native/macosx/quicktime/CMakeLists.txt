project(jnquicktime C)

add_library(${PROJECT_NAME} SHARED
        org_jitsi_impl_neomedia_quicktime_CVPixelBuffer.m
        org_jitsi_impl_neomedia_quicktime_CVPixelBufferAttributeKey.m
        org_jitsi_impl_neomedia_quicktime_NSDictionary.m
        org_jitsi_impl_neomedia_quicktime_NSMutableDictionary.m
        org_jitsi_impl_neomedia_quicktime_NSObject.m
        org_jitsi_impl_neomedia_quicktime_QTCaptureDecompressedVideoOutput.m
        org_jitsi_impl_neomedia_quicktime_QTCaptureDevice.m
        org_jitsi_impl_neomedia_quicktime_QTCaptureDeviceInput.m
        org_jitsi_impl_neomedia_quicktime_QTCaptureSession.m
        org_jitsi_impl_neomedia_quicktime_QTFormatDescription.m
        org_jitsi_impl_neomedia_quicktime_QTSampleBuffer.m
        )

target_link_libraries(${PROJECT_NAME} LINK_PUBLIC
        "-framework Foundation"
        "-framework CoreMedia"
        "-framework CoreVideo"
        "-framework AVFoundation"
        )

target_include_directories(${PROJECT_NAME} PUBLIC
        ${LIBJITSI_JAVAH}
        ${JNI_INCLUDE_DIRS}
        ${CMAKE_BINARY_DIR}/include/
        )

install(TARGETS ${PROJECT_NAME}
        RUNTIME DESTINATION "."
        LIBRARY DESTINATION ".")
