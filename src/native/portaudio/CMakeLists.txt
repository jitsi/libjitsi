project(jnportaudio CXX)

set(OS_SOURCES "")
set(OS_LIBS "")
if (WIN32)
    set(OS_SOURCES WMME_DSound.c portaudio.rc)
elseif (APPLE)
    set(OS_LIBS "-framework AudioToolbox"
            "-framework AudioUnit"
            "-framework CoreAudio"
            "-framework Carbon")
elseif (UNIX)
    find_package(ALSA REQUIRED)
    set(OS_LIBS pthread asound)
endif ()


set(PORTAUDIO_ROOT ${CMAKE_BINARY_DIR}/libportaudio)
set(libportaudio_INCLUDE_DIRS ${PORTAUDIO_ROOT}/include)
set(libportaudio_LIBRARY_DIRS ${PORTAUDIO_ROOT}/lib)

externalproject_add(ext_lib_portaudio
                    INSTALL_DIR ${PORTAUDIO_ROOT}
                    GIT_REPOSITORY https://github.com/jitsi/portaudio
                    GIT_TAG libjitsi_v2
                    GIT_SHALLOW false
                    CMAKE_ARGS (-DPA_BUILD_SHARED:BOOL=OFF
                    -DPA_LIBNAME_ADD_SUFFIX:BOOL=OFF
                    -DPA_DLL_LINK_WITH_STATIC_RUNTIME:BOOL=OFF
                    -DCMAKE_POSITION_INDEPENDENT_CODE:BOOL=ON
                    -DCMAKE_INSTALL_PREFIX:PATH=<INSTALL_DIR>)
                    )

add_library(${PROJECT_NAME} SHARED
            AudioQualityImprovement.c
            org_jitsi_impl_neomedia_portaudio_Pa.c
            ${OS_SOURCES})

if (NOT ${USE_SYSTEM_SPEEX})
    add_dependencies(${PROJECT_NAME} ext_lib_speex)
else ()
    find_package(speexdsp REQUIRED)
    find_package(speex REQUIRED)
endif ()

# we cannot use the system default portaudio - it has no hotplug
add_dependencies(${PROJECT_NAME} ext_lib_portaudio)

target_link_directories(${PROJECT_NAME} PRIVATE
                        ${libspeex_LIBRARY_DIRS}
                        ${libspeexdsp_LIBRARY_DIRS}
                        ${libportaudio_LIBRARY_DIRS})

target_link_libraries(${PROJECT_NAME} LINK_PUBLIC
                      portaudio
                      speex
                      speexdsp
                      ${OS_LIBS})

target_include_directories(${PROJECT_NAME} PUBLIC
                           ${LIBJITSI_JAVAH}
                           ${JNI_INCLUDE_DIRS}
                           ${CMAKE_BINARY_DIR}/include/
                           ${libspeex_INCLUDE_DIRS}
                           ${libspeexdsp_INCLUDE_DIRS}
                           ${libportaudio_INCLUDE_DIRS})

install(TARGETS ${PROJECT_NAME}
        RUNTIME DESTINATION "."
        LIBRARY DESTINATION ".")
