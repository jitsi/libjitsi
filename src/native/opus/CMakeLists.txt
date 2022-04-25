project(jnopus C)

set(OS_SOURCES "")
set(OS_LIBS "")
set(DEFAULT_USE_SYSTEM_OPUS 0)
if (WIN32)
    set(OS_SOURCES opus.rc)
elseif (APPLE)
    set(OS_LIBS "-framework AudioToolbox" "-framework AudioUnit" "-framework CoreAudio" "-framework Carbon")
elseif (UNIX)
    set(DEFAULT_USE_SYSTEM_OPUS 1)
endif ()

option(USE_SYSTEM_OPUS "Use system-installed Opus" ${DEFAULT_USE_SYSTEM_OPUS})

if (NOT ${USE_SYSTEM_OPUS})
    set(OPUS_ROOT ${CMAKE_BINARY_DIR}/libopus)
    set(OPUS_INCLUDE_DIRS ${OPUS_ROOT}/include)
    set(OPUS_LIBRARY_DIRS ${OPUS_ROOT}/lib)
    if (APPLE)
        list(APPEND OPUS_ARGS "-DCMAKE_OSX_ARCHITECTURES=${CMAKE_OSX_ARCHITECTURES}")
    endif ()

    externalproject_add(ext_lib_opus
                        INSTALL_DIR ${OPUS_ROOT}
                        GIT_REPOSITORY https://github.com/xiph/opus
                        GIT_TAG a8e6a77c5fe0c37aa6788f939f24f8cd22ae2652
                        GIT_SHALLOW false
                        CMAKE_ARGS
                        -DCMAKE_INSTALL_PREFIX:PATH=<INSTALL_DIR>
                        -DCMAKE_BUILD_TYPE=${CMAKE_BUILD_TYPE}
                        -DCMAKE_TOOLCHAIN_FILE=${EXTPROJECT_CMAKE_TOOLCHAIN_FILE}
                        -DCMAKE_POSITION_INDEPENDENT_CODE=ON
                        ${OPUS_ARGS}
                        )

    set(OPUS_LIBRARIES ${OPUS_LIBRARY_DIRS}/${CMAKE_STATIC_LIBRARY_PREFIX}opus${CMAKE_STATIC_LIBRARY_SUFFIX})
endif ()

add_library(${PROJECT_NAME} SHARED
            org_jitsi_impl_neomedia_codec_audio_opus_Opus.c
            ${OS_SOURCES})

if (NOT ${USE_SYSTEM_OPUS})
    add_dependencies(${PROJECT_NAME} ext_lib_opus)
else ()
    find_package(opus REQUIRED)
endif ()

target_link_libraries(${PROJECT_NAME} LINK_PUBLIC
                      ${JNI_LIBRARIES}
                      ${OS_LIBS}
                      ${OPUS_LIBRARIES}
                      )

target_include_directories(${PROJECT_NAME} PUBLIC
                           ${LIBJITSI_JAVAH}
                           ${JNI_INCLUDE_DIRS}
                           ${CMAKE_BINARY_DIR}/include/
                           ${OPUS_INCLUDE_DIRS})

if (JITSI_BUILD_DEBIAN)
    install(TARGETS ${PROJECT_NAME}
            LIBRARY DESTINATION ${CMAKE_INSTALL_LIBDIR})
else ()
    install(TARGETS ${PROJECT_NAME}
            RUNTIME DESTINATION "."
            LIBRARY DESTINATION ".")
endif ()
