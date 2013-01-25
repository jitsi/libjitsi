/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */

#include "JAWTRenderer.h"

#include <d3d9.h>
#include <jawt_md.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

typedef struct _JAWTRenderer
{
    LPDIRECT3D9 d3d;
    LPDIRECT3DDEVICE9 device;
    jint height;
    HWND hwnd;
    BOOL lost;
    LPDIRECT3DSURFACE9 surface;
    jint width;
}
JAWTRenderer;

HRESULT _JAWTRenderer_createDevice(JAWTRenderer *thiz, jint width, jint height);

void
JAWTRenderer_close(JNIEnv *env, jclass clazz, jlong handle, jobject component)
{
    JAWTRenderer *thiz = (JAWTRenderer *) (intptr_t) handle;

    if (thiz->surface)
        IDirect3DSurface9_Release(thiz->surface);
    if (thiz->device)
        IDirect3DDevice9_Release(thiz->device);
    IDirect3D9_Release(thiz->d3d);
    free(thiz);
}

jlong
JAWTRenderer_open(JNIEnv *env, jclass clazz, jobject component)
{
    LPDIRECT3D9 d3d = Direct3DCreate9(D3D_SDK_VERSION);
    JAWTRenderer *thiz;

    if (d3d)
    {
        thiz = calloc(1, sizeof(JAWTRenderer));
        if (thiz)
            thiz->d3d = d3d;
        else
            IDirect3D9_Release(d3d);
    }
    else
        thiz = NULL;
    return (jlong) (intptr_t) thiz;
}

jboolean
JAWTRenderer_paint
    (jint version, JAWT_DrawingSurfaceInfo *dsi, jclass clazz, jlong handle,
        jobject g, jint zOrder)
{
    JAWT_Win32DrawingSurfaceInfo *win32dsi
        = (JAWT_Win32DrawingSurfaceInfo *) (dsi->platformInfo);
    JAWTRenderer *thiz = (JAWTRenderer *) (intptr_t) handle;

    LPDIRECT3DDEVICE9 device = thiz->device;
    HWND hwnd = win32dsi->hwnd ? win32dsi->hwnd : WindowFromDC(win32dsi->hdc);

    if (device && (thiz->hwnd == hwnd))
    {
        HRESULT hr;

        /*
         * Check whether Direct3D considers the device of this JAWTRenderer
         * functional/valid.
         */
        hr = IDirect3DDevice9_TestCooperativeLevel(device);
        if (SUCCEEDED(hr))
        {
            /*
             * If we do not have a Direct3D surface to present, we will make
             * sure that the display is cleared. Otherwise, we do not have to
             * clear it because we render complete frames in the whole viewport
             * with transparency.
             */
            BOOL clear = TRUE;

            /* Copy the Direct3D surface into the back buffer. */
            if (thiz->surface)
            {
                LPDIRECT3DSURFACE9 backBuffer;

                hr
                    = IDirect3DDevice9_GetBackBuffer(
                        device,
                        0, 0, D3DBACKBUFFER_TYPE_MONO, &backBuffer);
                if (SUCCEEDED(hr))
                {
                    hr = IDirect3DDevice9_BeginScene(device);
                    if (SUCCEEDED(hr))
                    {
                        IDirect3DDevice9_StretchRect(
                            device,
                            thiz->surface, NULL, backBuffer, NULL,
                            D3DTEXF_LINEAR);
                        IDirect3DDevice9_EndScene(device);
                        clear = FALSE;
                    }
                    IDirect3DSurface9_Release(backBuffer);
                }
            }
            if (clear)
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
            IDirect3DDevice9_Present(device, NULL, NULL, NULL, NULL);
        }
        else
            thiz->lost = TRUE;
    }
    else
    {
        thiz->hwnd = hwnd;
        thiz->lost = TRUE;
    }

    return JNI_TRUE;
}

jboolean
JAWTRenderer_process
    (JNIEnv *env, jclass clazz, jlong handle, jobject component, jint *data,
        jint length, jint width, jint height)
{
    JAWTRenderer *thiz = (JAWTRenderer *) (intptr_t) handle;

    if (!(thiz->device)
            || (thiz->width != width)
            || (thiz->height != height)
            || thiz->lost)
    {
        /*
         * Release the device and the surface if any because it appears we have
         * to re-create them.
         */
        thiz->lost = FALSE;
        if (thiz->surface)
        {
            IDirect3DSurface9_Release(thiz->surface);
            thiz->surface = NULL;
        }
        if (thiz->device)
        {
            IDirect3DDevice9_Release(thiz->device);
            thiz->device = NULL;
        }

        if (thiz->hwnd)
        {
            HRESULT hr;

            hr = _JAWTRenderer_createDevice(thiz, width, height);
            if (SUCCEEDED(hr))
            {
                hr
                    = IDirect3DDevice9_CreateOffscreenPlainSurface(
                        thiz->device,
                        width,
                        height,
                        D3DFMT_X8R8G8B8,
                        D3DPOOL_DEFAULT,
                        &(thiz->surface),
                        NULL);
                if (SUCCEEDED(hr))
                {
                    thiz->width = width;
                    thiz->height = height;
                }
                else
                {
                    thiz->surface = NULL;
                    /*
                     * Well, we do not know why we would fail to create the
                     * surface but such a scenario seems grave enough to us to
                     * give up this JAWTRenderer.
                     */
                    return JNI_FALSE;
                }
            }
            /*
             * If we failed to create the Direct3D device, we will try again
             * next time in the hope that we will succeed eventually.
             */
        }
    }

    if (thiz->surface)
    {
        HRESULT hr;
        D3DLOCKED_RECT lockedRect;

        hr = IDirect3DSurface9_LockRect(thiz->surface, &lockedRect, NULL, 0);
        if (SUCCEEDED(hr))
        {
            jint y;
            jbyte *dst = lockedRect.pBits;
            INT dstPitch = lockedRect.Pitch;
            size_t widthInBytes = width * sizeof(jint);

            for (y = 0; y < height; y++, dst += dstPitch, data += width)
                memcpy(dst, data, widthInBytes);
            IDirect3DSurface9_UnlockRect(thiz->surface);
        }
    }

    return JNI_TRUE;
}

HRESULT
_JAWTRenderer_createDevice(JAWTRenderer *thiz, jint width, jint height)
{
    D3DPRESENT_PARAMETERS params;
    HRESULT hr;

    ZeroMemory(&params, sizeof(D3DPRESENT_PARAMETERS));

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

    hr
        = IDirect3D9_CheckDeviceMultiSampleType(
            thiz->d3d,
            D3DADAPTER_DEFAULT,
            D3DDEVTYPE_HAL,
            params.BackBufferFormat,
            params.Windowed,
            D3DMULTISAMPLE_2_SAMPLES,
            NULL);
    params.MultiSampleQuality
        = SUCCEEDED(hr) ? D3DMULTISAMPLE_2_SAMPLES : D3DMULTISAMPLE_NONE;

    hr
        = IDirect3D9_CreateDevice(
            thiz->d3d,
            D3DADAPTER_DEFAULT,
            D3DDEVTYPE_HAL,
            thiz->hwnd,
            D3DCREATE_SOFTWARE_VERTEXPROCESSING,
            &params,
            &(thiz->device));
    if (FAILED(hr))
        thiz->device = NULL;

    return hr;
}
