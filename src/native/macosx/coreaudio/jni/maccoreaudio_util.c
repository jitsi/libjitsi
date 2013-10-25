/*
 * SIP Communicator, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */

#include "maccoreaudio_util.h"

#include "../lib/device.h"

#include <string.h>

/**
 * JNI utilities.
 *
 * @author Vincent Lucas
 */

// Private static objects.

static JavaVM * maccoreaudio_VM = NULL;

static jclass maccoreaudio_devicesChangedCallbackClass = 0;
static jmethodID maccoreaudio_devicesChangedCallbackMethodID = 0;

void maccoreaudio_initHotplug(
        void);
void maccoreaudio_freeHotplug(
        void);


// Implementation

JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM *vm, void *pvt)
{
    maccoreaudio_VM = vm;
    maccoreaudio_initHotplug();
    return JNI_VERSION_1_6;
}

JNIEXPORT void JNICALL
JNI_OnUnload(JavaVM *vm, void *pvt)
{
    maccoreaudio_freeHotplug();
    maccoreaudio_VM = NULL;
}

/**
 * Gets a new <tt>jbyteArray</tt> instance which is initialized with the bytes
 * of a specific C string i.e. <tt>const char *</tt>.
 *
 * @param env
 * @param str the bytes/C string to initialize the new <tt>jbyteArray</tt>
 * instance with
 * @return a new <tt>jbyteArray</tt> instance which is initialized with the
 * bytes of the specified <tt>str</tt>
 */
jbyteArray maccoreaudio_getStrBytes(
        JNIEnv *env,
        const char *str)
{
    jbyteArray bytes;

    if (str)
    {
        size_t length = strlen(str);

        bytes = (*env)->NewByteArray(env, length);
        if (bytes && length)
            (*env)->SetByteArrayRegion(env, bytes, 0, length, (jbyte *) str);
    }
    else
        bytes = NULL;
    return bytes;
}

/**
 * Returns a callback method identifier.
 *
 * @param env
 * @param callback The object called back.
 * @param callbackFunctionName The name of the function used for the callback.
 *
 * @return A callback method identifier. 0 if the callback function is not
 * found.
 */
jmethodID maccoreaudio_getCallbackMethodID(
        JNIEnv *env,
        jobject callback,
        char* callbackFunctionName)
{
    jclass callbackClass;
    jmethodID callbackMethodID = NULL;

    if(callback)
    {
        if((callbackClass = (*env)->GetObjectClass(env, callback)))
        {
            callbackMethodID = (*env)->GetMethodID(
                    env,
                    callbackClass,
                    callbackFunctionName,
                    "([BI)V");
        }
    }

    return callbackMethodID;
}

/**
 * Calls back the java side when respectively reading / wrtiting the input
 * /output stream.
 */
void maccoreaudio_callbackMethod(
        char *buffer,
        int bufferLength,
        void* callback,
        void* callbackMethod)
{
    JNIEnv *env = NULL;

    if((*maccoreaudio_VM)->AttachCurrentThreadAsDaemon(
                maccoreaudio_VM,
                (void**) &env,
                NULL)
            == 0)
    {
        jbyteArray bufferBytes = (*env)->NewByteArray(env, bufferLength);
        (*env)->SetByteArrayRegion(
                env,
                bufferBytes,
                0,
                bufferLength,
                (jbyte *) buffer);

        (*env)->CallVoidMethod(
                env,
                callback,
                (jmethodID) callbackMethod,
                bufferBytes,
                bufferLength);

        jbyte* bytes = (*env)->GetByteArrayElements(env, bufferBytes, NULL);
        memcpy(buffer, bytes, bufferLength);
        (*env)->ReleaseByteArrayElements(env, bufferBytes, bytes, 0);

        (*maccoreaudio_VM)->DetachCurrentThread(maccoreaudio_VM);
    }
}

/**
 * Calls back the java side when the device list has changed.
 */
void maccoreaudio_devicesChangedCallbackMethod(void)
{
    JNIEnv *env = NULL;

    if((*maccoreaudio_VM)->AttachCurrentThreadAsDaemon(
                maccoreaudio_VM,
                (void**) &env,
                NULL)
            == 0)
    {
        jclass class = maccoreaudio_devicesChangedCallbackClass;
        jmethodID methodID = maccoreaudio_devicesChangedCallbackMethodID;
        if(class && methodID)
        {
            (*env)->CallStaticVoidMethod(env, class, methodID);
        }

        (*maccoreaudio_VM)->DetachCurrentThread(maccoreaudio_VM);
    }
}

/**
 * Initializes the hotplug callback process.
 */
void maccoreaudio_initHotplug(
        void)
{
    JNIEnv *env = NULL;

    if((*maccoreaudio_VM)->AttachCurrentThreadAsDaemon(
                maccoreaudio_VM,
                (void**) &env,
                NULL)
            == 0)
    {
        if(maccoreaudio_devicesChangedCallbackClass == NULL
                && maccoreaudio_devicesChangedCallbackMethodID == NULL)
        {
            jclass devicesChangedCallbackClass = (*env)->FindClass(
                    env,
                    "org/jitsi/impl/neomedia/CoreAudioDevice");

            if (devicesChangedCallbackClass)
            {
                devicesChangedCallbackClass
                    = (*env)->NewGlobalRef(env, devicesChangedCallbackClass);
                if (devicesChangedCallbackClass)
                {
                    jmethodID devicesChangedCallbackMethodID
                        = (*env)->GetStaticMethodID(
                                env,
                                devicesChangedCallbackClass,
                                "devicesChangedCallback",
                                "()V");

                    if (devicesChangedCallbackMethodID)
                    {
                        maccoreaudio_devicesChangedCallbackClass
                            = devicesChangedCallbackClass;
                        maccoreaudio_devicesChangedCallbackMethodID
                            = devicesChangedCallbackMethodID;

                        maccoreaudio_initializeHotplug(
                                maccoreaudio_devicesChangedCallbackMethod);
                    }
                }
            }
        }
        (*maccoreaudio_VM)->DetachCurrentThread(maccoreaudio_VM);
    }
}

/**
 * Frees the hotplug callback process.
 */
void maccoreaudio_freeHotplug(
        void)
{
    maccoreaudio_uninitializeHotplug();
    JNIEnv *env = NULL;

    if((*maccoreaudio_VM)->AttachCurrentThreadAsDaemon(
                maccoreaudio_VM,
                (void**) &env,
                NULL)
            == 0)
    {
        (*env)->DeleteGlobalRef(
                env,
                maccoreaudio_devicesChangedCallbackClass);
        (*maccoreaudio_VM)->DetachCurrentThread(maccoreaudio_VM);
    }
    maccoreaudio_devicesChangedCallbackClass = NULL;
    maccoreaudio_devicesChangedCallbackMethodID = NULL;
}

/**
 * Logs the corresponding error message.
 *
 * @param error_format The format of the error message.
 * @param ... The list of variable specified in the format argument.
 */
void maccoreaudio_log(
        const char * error_format,
        ...)
{
    JNIEnv *env = NULL;

    if((*maccoreaudio_VM)->AttachCurrentThreadAsDaemon(
                maccoreaudio_VM,
                (void**) &env,
                NULL)
            == 0)
    {
        jclass clazz = (*env)->FindClass(
                env,
                "org/jitsi/impl/neomedia/CoreAudioDevice");
        if (clazz)
        {
            jmethodID methodID
                = (*env)->GetStaticMethodID(env, clazz, "log", "([B)V");

            int error_length = 2048;
            char error[error_length];
            va_list arg;
            va_start (arg, error_format);
            vsnprintf(error, error_length, error_format, arg);
            va_end (arg);

            int str_len = strlen(error);
            jbyteArray bufferBytes = (*env)->NewByteArray(env, str_len);
            (*env)->SetByteArrayRegion(
                    env,
                    bufferBytes,
                    0,
                    str_len,
                    (jbyte *) error);

            (*env)->CallStaticVoidMethod(env, clazz, methodID, bufferBytes);
        }

        (*maccoreaudio_VM)->DetachCurrentThread(maccoreaudio_VM);
    }
}
