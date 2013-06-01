/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */

/**
 * \file org_jitsi_impl_neomedia_jmfext_media_protocol_directshow_DSCaptureDevice.cpp
 * \brief JNI part of DSCaptureDevice.
 * \author Sebastien Vincent
 * \author Lyubomir Marinov
 */

#include "BasicSampleGrabberCB.h"
#include "DSCaptureDevice.h"
#include "DSFormat.h"

#ifdef __cplusplus
extern "C" {
#endif

#include "org_jitsi_impl_neomedia_jmfext_media_protocol_directshow_DSCaptureDevice.h"

/**
 * \class SampleGrabberCB.
 * \brief Frame grabber.
 */
class SampleGrabberCB : public BasicSampleGrabberCB
{
public:
    /**
     * \brief Constructor.
     * \param vm Java Virtual Machine
     * \param delegate delegate Java object
     */
    SampleGrabberCB(JavaVM* vm, jobject delegate, DSCaptureDevice* dev)
        : _delegate(delegate), _dev(dev), _vm(vm) {}

    /**
     * \brief Destructor.
     */
    virtual ~SampleGrabberCB() {}

    /**
     * \brief Method callback when device capture a frame.
     * \param time time when frame was received
     * \param sample media sample
     * \see ISampleGrabberCB
     */
    STDMETHODIMP SampleCB(double time, IMediaSample* sample)
    {
        LONG length = sample->GetActualDataLength();

        if (length == 0)
            return S_OK;

        JNIEnv *env = NULL;

        if (_vm->AttachCurrentThreadAsDaemon((void **) &env, NULL) != 0)
            return E_FAIL;

        jclass clazz = env->GetObjectClass(_delegate);

        if (clazz)
        {
            jmethodID methodID = env->GetMethodID(clazz, "SampleCB", "(JJI)V");

            if (methodID)
            {
                BYTE *data = NULL;
                HRESULT hr = sample->GetPointer(&data);

                if (FAILED(hr))
                    return hr;

                env->CallVoidMethod(
                        _delegate,
                        methodID,
                        (jlong) (intptr_t) _dev,
                        (jlong) (intptr_t) data,
                        (jint) length);
            }
        }
        return S_OK;
    }

    /**
     * \brief Delegate Java object.
     */
    jobject const _delegate;

private:

    /**
     * \brief DirectShow device.
     */
    DSCaptureDevice * const _dev;

    /**
     * \brief Java VM.
     */
    JavaVM * const _vm;
};

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_directshow_DSCaptureDevice_samplecopy
    (JNIEnv *env, jclass clazz, jlong thiz, jlong src, jlong dst, jint length)
{
    DSCaptureDevice* thiz_ = reinterpret_cast<DSCaptureDevice *>(thiz);
    /* It appears that RGB is always flipped. */
    DSFormat fmt = thiz_->getFormat();
    bool flip
        = (fmt.mediaType == MEDIASUBTYPE_ARGB32)
            || (fmt.mediaType == MEDIASUBTYPE_RGB32)
            || (fmt.mediaType == MEDIASUBTYPE_RGB24);
    BYTE *src_ = (BYTE *) (intptr_t) src;
    BYTE *dst_ = (BYTE *) (intptr_t) dst;

    if (flip)
    {
        size_t height = fmt.height;

        if (height > 1)
        {
            size_t stride = fmt.width * (thiz_->getBitPerPixel() / 8);

            src_ += (height - 1) * stride;
            for (size_t row = 0; row < height; row++)
            {
                memcpy(dst_, src_, stride);
                dst_ += stride;
                src_ -= stride;
            }
            return length;
        }
    }

    memcpy(dst_, src_, length);
    return length;
}

JNIEXPORT jint JNICALL Java_org_jitsi_impl_neomedia_jmfext_media_protocol_directshow_DSCaptureDevice_getBytes
  (JNIEnv* env, jclass clazz, jlong ptr, jlong buf, jint len)
{
    /* copy data */
    memcpy((void*)buf, (void*)ptr, len);
    return len;
}

/**
 * \brief Connects to the specified capture device.
 * \param env JNI environment
 * \param obj DSCaptureDevice object
 * \param ptr a pointer to a DSCaptureDevice instance to connect to
 */
JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_directshow_DSCaptureDevice_connect
    (JNIEnv* env, jobject obj, jlong ptr)
{
    DSCaptureDevice* thiz = reinterpret_cast<DSCaptureDevice*>(ptr);

    thiz->buildGraph();
}

/**
 * \brief Disconnects from the specified capture device.
 * \param env JNI environment
 * \param obj DSCaptureDevice object
 * \param ptr a pointer to a DSCaptureDevice instance to disconnect from
 */
JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_directshow_DSCaptureDevice_disconnect
    (JNIEnv* env, jobject obj, jlong ptr)
{
    /* TODO Auto-generated method stub */
}

/**
 * \brief Get current format.
 * \param env JNI environment
 * \param obj object
 * \param native pointer
 * \return current format
 */
JNIEXPORT jobject JNICALL Java_org_jitsi_impl_neomedia_jmfext_media_protocol_directshow_DSCaptureDevice_getFormat
  (JNIEnv* env, jobject obj, jlong ptr)
{
    DSCaptureDevice* dev = reinterpret_cast<DSCaptureDevice*>(ptr);
    DSFormat fmt = dev->getFormat();
    jclass clazzDSFormat = NULL;
    jmethodID initDSFormat = NULL;

    /* get DSFormat class to instantiate some object */
    clazzDSFormat = env->FindClass("org/jitsi/impl/neomedia/jmfext/media/protocol/directshow/DSFormat");
    if(clazzDSFormat == NULL)
        return NULL;

    initDSFormat = env->GetMethodID(clazzDSFormat, "<init>", "(III)V");

    if(initDSFormat == NULL)
        return NULL;

    return
        env->NewObject(
                clazzDSFormat,
                initDSFormat,
                static_cast<jint>(fmt.width),
                static_cast<jint>(fmt.height),
                static_cast<jint>(fmt.pixelFormat));
}

/**
 * \brief Get name of native capture device.
 * \param env JNI environment
 * \param obj DSCaptureDevice object
 * \param ptr native pointer of DSCaptureDevice
 * \return name of the native capture device
 */
JNIEXPORT jstring JNICALL Java_org_jitsi_impl_neomedia_jmfext_media_protocol_directshow_DSCaptureDevice_getName
  (JNIEnv* env, jobject obj, jlong ptr)
{
    DSCaptureDevice* dev = reinterpret_cast<DSCaptureDevice*>(ptr);
    jstring ret = NULL;
    jsize len = static_cast<jsize>(wcslen(dev->getName()));
    jchar* name = new jchar[len];

    /* jchar is two bytes! */
    memcpy((void*)name, (void*)dev->getName(), len * 2);

    ret = env->NewString(name, len);
    delete[] name;

    return ret;
}

/**
 * \brief Get formats supported by native capture device.
 * \param env JNI environment
 * \param obj DSCaptureDevice object
 * \param ptr native pointer of DSCaptureDevice
 * \return array of DSFormat object
 */
JNIEXPORT jobjectArray JNICALL Java_org_jitsi_impl_neomedia_jmfext_media_protocol_directshow_DSCaptureDevice_getSupportedFormats
  (JNIEnv* env, jobject obj, jlong ptr)
{
    jobjectArray ret = NULL;
    DSCaptureDevice* dev = reinterpret_cast<DSCaptureDevice*>(ptr);
    std::list<DSFormat> formats;
    jclass clazzDSFormat = NULL;
    jmethodID initDSFormat = NULL;
    jsize i = 0;

    /* get DSFormat class to instantiate some object */
    clazzDSFormat = env->FindClass("org/jitsi/impl/neomedia/jmfext/media/protocol/directshow/DSFormat");
    if(clazzDSFormat == NULL)
        return NULL;

    initDSFormat = env->GetMethodID(clazzDSFormat, "<init>", "(III)V");

    if(initDSFormat == NULL)
        return NULL;

    formats = dev->getSupportedFormats();

    ret = env->NewObjectArray(static_cast<jsize>(formats.size()), clazzDSFormat, NULL);
    for(std::list<DSFormat>::iterator it = formats.begin() ; it != formats.end() ; ++it)
    {
        DSFormat tmp = (*it);
        jobject o
            = env->NewObject(
                    clazzDSFormat,
                    initDSFormat,
                    static_cast<jint>(tmp.width),
                    static_cast<jint>(tmp.height),
                    static_cast<jint>(tmp.pixelFormat));

        if(o == NULL)
        {
            fprintf(stderr, "failed!!\n");
            fflush(stderr);
        }
        else
        {

            env->SetObjectArrayElement(ret, i, o);
            env->DeleteLocalRef(o);
            i++;
        }
    }

    return ret;
}

/**
 * \brief Set delegate.
 * \param env JNI environment
 * \param obj object
 * \param ptr native pointer on DSCaptureDevice
 * \param delegate delegate object
 */
JNIEXPORT void JNICALL Java_org_jitsi_impl_neomedia_jmfext_media_protocol_directshow_DSCaptureDevice_setDelegate
  (JNIEnv* env, jobject obj, jlong ptr, jobject delegate)
{
    SampleGrabberCB* grab = NULL;
    DSCaptureDevice* dev = reinterpret_cast<DSCaptureDevice*>(ptr);
    BasicSampleGrabberCB* prev = dev->getCallback();

    if(delegate != NULL)
    {
        delegate = env->NewGlobalRef(delegate);
        if(delegate)
        {
            JavaVM* vm = NULL;
            /* get JavaVM */
            env->GetJavaVM(&vm);
            grab = new SampleGrabberCB(vm, delegate, dev);
            dev->setCallback(grab);
        }
    }
    else
    {
        dev->setCallback(NULL);
    }

    if(prev)
    {
        jobject tmp_delegate = ((SampleGrabberCB *) prev)->_delegate;
        if(tmp_delegate)
            env->DeleteGlobalRef(tmp_delegate);
        delete prev;
    }
}

/**
 * \brief Set format of native capture device.
 * \param env JNI environment
 * \param obj DSCaptureDevice object
 * \param ptr native pointer of DSCaptureDevice
 * \param format DSFormat to set
 */
JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_directshow_DSCaptureDevice_setFormat
    (JNIEnv* env, jobject obj, jlong ptr, jobject format)
{
    DSCaptureDevice* thiz = reinterpret_cast<DSCaptureDevice*>(ptr);
    jclass clazz = env->GetObjectClass(format);
    HRESULT hr;

    if (clazz)
    {
        jfieldID heightFieldID = env->GetFieldID(clazz, "height", "I");

        if (heightFieldID)
        {
            jfieldID widthFieldID = env->GetFieldID(clazz, "width", "I");

            if (widthFieldID)
            {
                jfieldID pixelFormatFieldID
                    = env->GetFieldID(clazz, "pixelFormat", "I");

                if (pixelFormatFieldID)
                {
                    DSFormat format_;

                    format_.height = env->GetIntField(format, heightFieldID);
                    format_.pixelFormat
                        = (DWORD)
                            (env->GetIntField(format, pixelFormatFieldID));
                    format_.width = env->GetIntField(format, widthFieldID);

                    hr = thiz->setFormat(format_);
                }
                else
                    hr = E_FAIL;
            }
            else
                hr = E_FAIL;
        }
        else
            hr = E_FAIL;
    }
    else
        hr = E_FAIL;
    return hr;
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_directshow_DSCaptureDevice_start
    (JNIEnv *env, jobject obj, jlong ptr)
{
    DSCaptureDevice *thiz = reinterpret_cast<DSCaptureDevice *>(ptr);

    return (jint) (thiz->start());
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_directshow_DSCaptureDevice_stop
    (JNIEnv *env, jobject obj, jlong ptr)
{
    DSCaptureDevice *thiz = reinterpret_cast<DSCaptureDevice *>(ptr);

    return (jint) (thiz->stop());
}

#ifdef __cplusplus
} /* extern "C" { */
#endif
