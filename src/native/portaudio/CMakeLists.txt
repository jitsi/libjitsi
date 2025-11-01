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
    set(OS_LIBS pthread asound m)
endif ()


set(PORTAUDIO_ROOT ${CMAKE_BINARY_DIR}/libportaudio)
set(libportaudio_INCLUDE_DIRS ${PORTAUDIO_ROOT}/include)
set(libportaudio_LIBRARY_DIRS ${PORTAUDIO_ROOT}/lib)

if (${JITSI_BUILD_DEBIAN})
    set(PA_ARGS ${CL_ARGS})
else ()
    list(APPEND PA_ARGS "-DCMAKE_BUILD_TYPE=${CMAKE_BUILD_TYPE}")
    list(APPEND PA_ARGS "-DCMAKE_TOOLCHAIN_FILE=${EXTPROJECT_CMAKE_TOOLCHAIN_FILE}")
    if (APPLE)
        list(APPEND PA_ARGS "-DCMAKE_OSX_ARCHITECTURES=${CMAKE_OSX_ARCHITECTURES}")
    endif ()
endif ()

externalproject_add(ext_lib_portaudio
                    URL ${CMAKE_CURRENT_LIST_DIR}/../third_party/portaudio
                    INSTALL_DIR ${PORTAUDIO_ROOT}
                    CMAKE_ARGS
                    ${PA_ARGS}
                    -DPA_BUILD_SHARED:BOOL=OFF
                    -DPA_LIBNAME_ADD_SUFFIX:BOOL=OFF
                    -DPA_DLL_LINK_WITH_STATIC_RUNTIME:BOOL=OFF
                    -DCMAKE_POSITION_INDEPENDENT_CODE:BOOL=ON
                    -DCMAKE_INSTALL_PREFIX:PATH=<INSTALL_DIR>
                    -DCMAKE_INSTALL_LIBDIR:PATH=lib
                    )

add_library(${PROJECT_NAME} SHARED
            AudioQualityImprovement.c
            org_jitsi_impl_neomedia_portaudio_Pa.c
            ${OS_SOURCES})

find_package(speexdsp REQUIRED)
find_package(speex REQUIRED)

# we cannot use the system default portaudio - it has no hotplug
add_dependencies(${PROJECT_NAME} ext_lib_portaudio)

target_link_libraries(${PROJECT_NAME} LINK_PUBLIC
                      ${JNI_LIBRARIES}
                      ${libportaudio_LIBRARY_DIRS}/${CMAKE_STATIC_LIBRARY_PREFIX}portaudio${CMAKE_STATIC_LIBRARY_SUFFIX}
                      ${SPEEX_LIBRARIES}
                      ${SPEEXDSP_LIBRARIES}
                      ${OS_LIBS})

target_include_directories(${PROJECT_NAME} PUBLIC
                           ${LIBJITSI_JAVAH}
                           ${JNI_INCLUDE_DIRS}
                           ${CMAKE_BINARY_DIR}/include/
                           ${SPEEX_INCLUDE_DIRS}
                           ${SPEEXDSP_INCLUDE_DIRS}
                           ${libportaudio_INCLUDE_DIRS})

if (JITSI_BUILD_DEBIAN)
    install(TARGETS ${PROJECT_NAME}
            LIBRARY DESTINATION ${CMAKE_INSTALL_LIBDIR})
else ()
    install(TARGETS ${PROJECT_NAME}
            RUNTIME DESTINATION "."
            LIBRARY DESTINATION ".")
endif ()
