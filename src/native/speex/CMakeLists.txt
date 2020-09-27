project(jnspeex C)

set(OS_SOURCES "")
set(OS_LIBS "")
set(DEFAULT_USE_SYSTEM_SPEEX 0)
if (WIN32)
    set(OS_SOURCES speex.rc)
elseif (APPLE)
    set(OS_LIBS AudioToolbox AudioUnit CoreAudio Carbon)
elseif (UNIX)
    set(OS_LIBS pthread asound)
endif ()

add_library(${PROJECT_NAME} SHARED
            org_jitsi_impl_neomedia_codec_audio_speex_Speex.c
            ${OS_SOURCES})


if (NOT ${USE_SYSTEM_SPEEX})
    add_dependencies(${PROJECT_NAME} ext_lib_speex)
else ()
    find_package(speexdsp REQUIRED)
    find_package(speex REQUIRED)
endif ()


target_link_directories(${PROJECT_NAME}
                        PRIVATE ${libspeex_LIBRARY_DIRS} ${libspeexdsp_LIBRARY_DIRS})

target_link_libraries(${PROJECT_NAME} LINK_PUBLIC
                      ${OS_LIBS}
                      speexdsp
                      speex
                      )

target_include_directories(${PROJECT_NAME} PUBLIC
                           ${LIBJITSI_JAVAH}
                           ${JNI_INCLUDE_DIRS}
                           ${CMAKE_BINARY_DIR}/include/
                           ${libspeex_INCLUDE_DIRS}
                           ${libspeexdsp_INCLUDE_DIRS})

install(TARGETS ${PROJECT_NAME}
        RUNTIME DESTINATION ".")
