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
    set(opus_DIR ${OPUS_ROOT}/lib/cmake/opus)
    set(libopus_INCLUDE_DIRS ${OPUS_ROOT}/include)
    set(libopus_LIBRARY_DIRS ${OPUS_ROOT}/lib)

    externalproject_add(ext_lib_opus
                        INSTALL_DIR ${OPUS_ROOT}
                        GIT_REPOSITORY https://github.com/xiph/opus
                        GIT_TAG e85ed7726db5d677c9c0677298ea0cb9c65bdd23
                        GIT_SHALLOW false
                        CMAKE_ARGS -DCMAKE_INSTALL_PREFIX:PATH=<INSTALL_DIR> -DOPUS_X86_PRESUME_AVX:BOOL=ON -DOPUS_X86_PRESUME_SSE4_1:BOOL=ON
                        )
endif ()

add_library(${PROJECT_NAME} SHARED
            org_jitsi_impl_neomedia_codec_audio_opus_Opus.c
            ${OS_SOURCES})

if (NOT ${USE_SYSTEM_OPUS})
    add_dependencies(${PROJECT_NAME} ext_lib_opus)
else ()
    find_package(opus REQUIRED)
endif ()


target_link_directories(${PROJECT_NAME}
                        PRIVATE ${libopus_LIBRARY_DIRS})

target_link_libraries(${PROJECT_NAME} LINK_PUBLIC
                      ${OS_LIBS}
                      opus
                      )

target_include_directories(${PROJECT_NAME} PUBLIC
                           ${LIBJITSI_JAVAH}
                           ${JNI_INCLUDE_DIRS}
                           ${CMAKE_BINARY_DIR}/include/
                           ${libopus_INCLUDE_DIRS})

install(TARGETS ${PROJECT_NAME}
        RUNTIME DESTINATION "."
        LIBRARY DESTINATION ".")
