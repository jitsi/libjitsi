/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */

/**
 * \file hw_renderer_dxva2.c
 * \brief Hardware renderer for dxva2.
 * \author Sebastien Vincent
 * \date 2013
 */

#if defined(_WIN32) || defined(_WIN64)

#include <dxva2api.h>
#include "dxva2api_mingw.h"

#include <jawt_md.h>
#include <libavcodec/avcodec.h>

#include "hw_renderer.h"
#include "../ffmpeg/hw_decoder.h"
#include "../ffmpeg/hw_decoder_dxva2.h"

#include <initguid.h>

#define MS_GUID(name, l, w1, w2, b1, b2, b3, b4, b5, b6, b7, b8)

#ifdef __MINGW32__
# include <_mingw.h>

# if !defined(__MINGW64_VERSION_MAJOR)
#  undef MS_GUID
#  define MS_GUID DEFINE_GUID /* dxva2api.h fails to declare those, redefine as static */
#  define DXVA2_E_NEW_VIDEO_DEVICE MAKE_HRESULT(1, 4, 4097)
# else
#  include <dxva.h>
# endif

#endif /* __MINGW32__ */

/* XXX */
#undef MS_GUID
#define MS_GUID DEFINE_GUID

/* this code is greatly inspired by VLC and XBMC */

MS_GUID (IID_IDirectXVideoProcessorService, 0xfc51a552,0xd5e7,0x11d9,0xaf,0x55,0x00,0x05,0x4e,0x43,0xff,0x02);
MS_GUID (IID_IDirectXVideoProcessor, 0x8c3a39f0,0x916e,0x4690,0x80,0x4f,0x4c,0x80,0x01,0x35,0x5d,0x25);
MS_GUID (DXVA2_VideoProcProgressiveDevice, 0x5a54a0c9,0xc7ec,0x4bd9,0x8e,0xde,0xf3,0xc7,0x5d,0xc4,0x39,0x3b);

/**
 * \struct hw_renderer_dxva2
 * \brief Hardware renderer for dxva2.
 */
struct hw_renderer_dxva2
{
    AVFrame* avframe; /**< FFmpeg frame. */
    struct hw_decoder* decoder; /**< Hardware decoder used. */
    LPDIRECT3D9 d3d; /**< Direct3D. */
    LPDIRECT3DDEVICE9 device; /**< Direct3D device. */
    HWND hwnd; /**< Handle of the window. */
    BOOL lost; /**< If the device is lost. */
    jint width; /**< Width of the surface to display. */
    jint height; /**< Height of the surface to display. */
    /* rendering stuff */
    IDirectXVideoProcessorService* processor_service; /**< DXVA2 processor service. */
    IDirectXVideoProcessor* processor; /**< DXVA2 processor. */
    DXVA2_ValueRange brightness; /**< Brigthness of the device. */
    DXVA2_ValueRange contrast; /**< Contrast of the device. */
    DXVA2_ValueRange hue; /**< Hue of the device. */
    DXVA2_ValueRange saturation; /**< Saturation of the device. */
};

/**
 * \brief Creates Direct3D device.
 * \param obj DXVA2 renderer.
 * \return S_OK if success, other value otherwise
 */
static HRESULT hw_renderer_create_d3d_device(struct hw_renderer_dxva2 *obj,
        jint width, jint height)
{
    D3DPRESENT_PARAMETERS params;
    HRESULT hr;

    memset(&params, 0x00, sizeof(D3DPRESENT_PARAMETERS));

    params.AutoDepthStencilFormat = D3DFMT_D16;
    params.BackBufferCount = 1;
    params.BackBufferFormat = D3DFMT_UNKNOWN;
    params.BackBufferHeight = height;
    params.BackBufferWidth = width;
    params.EnableAutoDepthStencil = FALSE;
    params.Flags = D3DPRESENTFLAG_LOCKABLE_BACKBUFFER;
    params.FullScreen_RefreshRateInHz = 0;
    params.PresentationInterval = D3DPRESENT_INTERVAL_IMMEDIATE;
    params.SwapEffect = D3DSWAPEFFECT_DISCARD;
    params.Windowed = TRUE;

    hr = IDirect3D9_CheckDeviceMultiSampleType(
                obj->d3d,
                D3DADAPTER_DEFAULT,
                D3DDEVTYPE_HAL,
                params.BackBufferFormat,
                params.Windowed,
                D3DMULTISAMPLE_2_SAMPLES,
                NULL);
    params.MultiSampleQuality
        = SUCCEEDED(hr) ? D3DMULTISAMPLE_2_SAMPLES : D3DMULTISAMPLE_NONE;

    hr = IDirect3D9_CreateDevice(
                obj->d3d,
                D3DADAPTER_DEFAULT,
                D3DDEVTYPE_HAL,
                obj->hwnd,
                D3DCREATE_SOFTWARE_VERTEXPROCESSING,
                &params,
                &obj->device);

    if(FAILED(hr))
    {
        obj->device = NULL;
    }

    return hr;
}

/**
 * \brief Creates DXVA2 processor.
 * \param obj DXVA2 renderer.
 * \return S_OK if success, other value otherwise.
 */
static HRESULT hw_renderer_create_dxva2_processor(struct hw_renderer_dxva2* obj)
{
    HRESULT hr = S_OK;
    GUID dev;

    hr = DXVA2CreateVideoService(obj->device,
            &IID_IDirectXVideoProcessorService,
            (void**)&obj->processor_service);

    if(SUCCEEDED(hr))
    {
        /* find GUID for processor device */
        GUID* guids = NULL;
        UINT guids_count = 0;


        hr = IDirectXVideoProcessorService_GetVideoProcessorDeviceGuids(
                obj->processor_service,
                &obj->decoder->context.video_desc,
                &guids_count, &guids);

        if(SUCCEEDED(hr))
        {
            if(guids_count > 0)
            {
                dev = guids[0];
                for(size_t i = 0 ; i< guids_count ; i++)
                {
                    GUID* g = &guids[i];
    
                    if(IsEqualGUID(g,
                                &DXVA2_VideoProcProgressiveDevice))
                    {
                        dev = *g;
                        break;
                    }
                }        
            }
            CoTaskMemFree(guids);
        }
        else
        {
            IDirectXVideoProcessorService_Release(obj->processor_service);
            obj->processor_service = NULL;
            return hr;
        }

        const D3DFORMAT output = D3DFMT_X8R8G8B8;

        IDirectXVideoProcessorService_GetProcAmpRange(
                obj->processor_service, &dev,
                &obj->decoder->context.video_desc, output,
                DXVA2_ProcAmp_Brightness, &obj->brightness);
        IDirectXVideoProcessorService_GetProcAmpRange(
                obj->processor_service, &dev,
                &obj->decoder->context.video_desc, output,
                DXVA2_ProcAmp_Contrast, &obj->contrast);
        IDirectXVideoProcessorService_GetProcAmpRange(
                obj->processor_service, &dev,
                &obj->decoder->context.video_desc, output,
                DXVA2_ProcAmp_Hue, &obj->hue);
        IDirectXVideoProcessorService_GetProcAmpRange(
                obj->processor_service, &dev,
                &obj->decoder->context.video_desc, output,
                DXVA2_ProcAmp_Saturation, &obj->saturation);

        hr = IDirectXVideoProcessorService_CreateVideoProcessor(
                obj->processor_service, &dev,
                &obj->decoder->context.video_desc,
                output, 0, &obj->processor);
    }

    return hr;
}

void hw_renderer_display(struct hw_renderer_dxva2* obj, void* hwnd,
        void* drawable, void* surface)
{
    HRESULT hr;

    /* basic checks */
    if(!obj || !surface || !hwnd || !drawable)
    {
        return;
    }

    DXVA2_VideoSample vs;

    memset(&vs, 0x00, sizeof(DXVA2_VideoSample));
    vs.Start = 2;
    vs.End = 0;
    vs.SampleFormat = obj->decoder->context.video_desc.SampleFormat;
    vs.SrcRect.left = 0;
    vs.SrcRect.right = obj->decoder->context.video_desc.SampleWidth;
    vs.SrcRect.top = 0;
    vs.SrcRect.bottom = obj->decoder->context.video_desc.SampleHeight;
    vs.DstRect.left = 0;
    vs.DstRect.right = obj->decoder->context.video_desc.SampleWidth;
    vs.DstRect.top = 0;
    vs.DstRect.bottom = obj->decoder->context.video_desc.SampleHeight;
    vs.PlanarAlpha = DXVA2_Fixed32OpaqueAlpha();
    vs.SampleData = 0;
    vs.SrcSurface = surface;
    IDirect3DSurface9_AddRef(vs.SrcSurface);

    DXVA2_VideoProcessBltParams blt;
    memset(&blt, 0x00, sizeof(DXVA2_VideoProcessBltParams));

    blt.TargetFrame = vs.Start;
    blt.TargetRect  = vs.DstRect;
    blt.DestFormat.VideoTransferFunction = DXVA2_VideoTransFunc_sRGB;
    blt.DestFormat.SampleFormat = DXVA2_SampleProgressiveFrame;
    blt.DestFormat.NominalRange = DXVA2_NominalRange_0_255;
    blt.Alpha = DXVA2_Fixed32OpaqueAlpha();

    blt.ProcAmpValues.Brightness = obj->brightness.DefaultValue;
    blt.ProcAmpValues.Contrast = obj->contrast.DefaultValue;
    blt.ProcAmpValues.Hue = obj->hue.DefaultValue;
    blt.ProcAmpValues.Saturation = obj->saturation.DefaultValue;

    blt.BackgroundColor.Y     = 0x1000;
    blt.BackgroundColor.Cb    = 0x8000;
    blt.BackgroundColor.Cr    = 0x8000;
    blt.BackgroundColor.Alpha = 0xffff;

    /* HACK to kickstart certain DXVA drivers (poulsbo) which oddly  *
     * won't render anything until someting else have been rendered. */
    IDirect3DDevice9_SetFVF(obj->device, D3DFVF_XYZ);
    float verts[2][3]= {};
    IDirect3DDevice9_DrawPrimitiveUP(obj->device, D3DPT_TRIANGLEFAN, 1, verts,
        3 * sizeof(float));

    hr = IDirectXVideoProcessor_VideoProcessBlt(obj->processor,
            drawable, &blt, &vs, 1, NULL);

    if(FAILED(hr))
    {
        printf("videoprocessblt failed: %x\n", (uint32_t)hr);fflush(stdout);
    }
    IDirect3DSurface9_Release(vs.SrcSurface);
}

void hw_renderer_close(JNIEnv* env, jclass clazz, jlong handle,
        jobject component)
{
    struct hw_renderer_dxva2* renderer =
        (struct hw_renderer_dxva2*)(intptr_t)handle;

    (void)env;
    (void)clazz;
    (void)component;

    if(renderer)
    {
        if(renderer->device)
        {
            IDirect3DDevice9_Release(renderer->device);
        }
        IDirect3D9_Release(renderer->d3d);
        free(renderer);
    }
}

jlong hw_renderer_open(JNIEnv* env, jclass clazz, jobject component)
{
    struct hw_renderer_dxva2* renderer = NULL;
    LPDIRECT3D9 d3d = Direct3DCreate9(D3D_SDK_VERSION);

    (void)env;
    (void)clazz;
    (void)component;

    if(d3d)
    {
        renderer = malloc(sizeof(struct hw_renderer_dxva2));

        if(!renderer)
        {
            IDirect3D9_Release(d3d);
            return 0;
        }

        memset(renderer, 0x00, sizeof(struct hw_renderer_dxva2));
        renderer->d3d = d3d;
        renderer->device = NULL;
        renderer->width = 0;
        renderer->height = 0;
        renderer->processor_service = NULL;
        renderer->processor = NULL;
        renderer->avframe = NULL;
        renderer->decoder = NULL;

        return (jlong)(intptr_t)renderer;
    }

    return 0;
}

jboolean hw_renderer_paint(JAWT_DrawingSurfaceInfo* dsi, jclass clazz,
        jlong handle, jobject graphic)
{
    struct hw_renderer_dxva2* obj =
        (struct hw_renderer_dxva2*)(intptr_t)handle;
    JAWT_Win32DrawingSurfaceInfo* win32dsi =
        (JAWT_Win32DrawingSurfaceInfo*)dsi->platformInfo;
    HWND hwnd = win32dsi->hwnd ? win32dsi->hwnd : WindowFromDC(win32dsi->hdc);
    LPDIRECT3DDEVICE9 device = obj->device;

    (void)clazz;
    (void)graphic;

    if(device && (obj->hwnd == hwnd))
    {
        HRESULT hr;

        /*
         * Check whether Direct3D considers the device of this HWRenderer
         * functional/valid.
         */
        hr = IDirect3DDevice9_TestCooperativeLevel(device);
        if(SUCCEEDED(hr))
        {
            /*
             * If we do not have a Direct3D surface to present, we will make
             * sure that the display is cleared. Otherwise, we do not have to
             * clear it because we render complete frames in the whole viewport
             * with transparency.
             */
            BOOL clear = TRUE;
            LPDIRECT3DSURFACE9 target;

            if(obj->avframe)
            {
                /*
                hr = IDirect3DDevice9_GetBackBuffer(device, 0, 0,
                        D3DBACKBUFFER_TYPE_MONO, &target);
                */
                hr = IDirect3DDevice9_GetRenderTarget(device, 0, &target);

                if(SUCCEEDED(hr))
                {
                    hw_renderer_display(obj, (void*)hwnd,
                            target, (void*)obj->avframe->data[3]);
                    IDirect3DSurface9_Release(target);
                    clear = FALSE;
                }
                else
                {
                    printf("render target failed\n");fflush(stdout);
                }
            }

            if(clear)
            {
                /* Clear the Direct3D back buffer. */
                IDirect3DDevice9_Clear(
                        device,
                        0,
                        0,
                        D3DCLEAR_TARGET,
                        D3DCOLOR_XRGB(0xff, 0xff, 0xff),
                        0.0f,
                        0);
            }

            /* Present the Direct3D back buffer. */
            /* Direct3DDevice9_Present(device, NULL, NULL, NULL, NULL); */
        }
        else
        {
            obj->lost = TRUE;
        }
    }
    else
    {
        obj->hwnd = hwnd;
        obj->lost = TRUE;
    }

    return JNI_TRUE;
}

jboolean hw_renderer_process(JNIEnv* env, jclass clazz, jlong handle,
        jobject component, jlong data, jint offset, jint length, jint width,
        jint height)
{
    struct hw_renderer_dxva2* obj =
        (struct hw_renderer_dxva2*)(intptr_t)handle;
    AVFrame* avframe = (AVFrame*)(intptr_t)data;

    (void)env;
    (void)clazz;
    (void)component;
    (void)offset;
    (void)length;
    
    if(avframe)
    {
        obj->avframe = avframe;
        obj->decoder = avframe->opaque;
    }

    if(!(obj->device)
            || (obj->width != width)
            || (obj->height != height)
            || obj->lost)
    {
        /*
         * Release the device and the surface if any because it appears we have
         * to re-create them.
         */
        obj->lost = FALSE;
        if(obj->processor)
        {
            IDirectXVideoProcessor_Release(obj->processor);
            obj->processor = NULL;
        }
        if(obj->processor_service)
        {
            IDirectXVideoProcessorService_Release(obj->processor_service);
            obj->processor_service = NULL;
        }
        if(obj->device)
        {
            IDirect3DDevice9_Release(obj->device);
            obj->device = NULL;
        }

        if(obj->hwnd)
        {
            HRESULT hr;

            hr = hw_renderer_create_d3d_device(obj, width, height);
            if(SUCCEEDED(hr))
            {
                obj->width = width;
                obj->height = height;

                hr = hw_renderer_create_dxva2_processor(obj);
                if(FAILED(hr))
                {
                    /* TODO adds debug here */
                    printf("create_dxva2_processor\n");fflush(stdout);
                }
            }
            else
            {
                printf("create_d3d_device\n");fflush(stdout);
            }
            /*
             * If we failed to create the Direct3D device, we will try again
             * next time in the hope that we will succeed eventually.
             */
        }
    }

    return JNI_TRUE;
}

#endif

