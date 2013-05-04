/**
 * \file hw_decoder_dxva2.c
 * \brief Hardware decoder using DXVA2.
 * \author Sebastien Vincent
 * \date 2013
 */

/* only Windows */
#if defined(_WIN32) || defined(_WIN64)

/* the code has been inspired by VLC and contains some of its (LGPL) code */

#ifndef COBJMACROS
#define COBJMACROS
#endif
#include <libavcodec/dxva2.h>

#include "hw_decoder.h"
#include "hw_decoder_dxva2.h"

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

MS_GUID (IID_IDirectXVideoDecoderService, 0xfc51a551, 0xd5e7, 0x11d9, 0xaf,0x55,0x00,0x05,0x4e,0x43,0xff,0x02);

MS_GUID (IID_IDirectXVideoAccelerationService, 0xfc51a550, 0xd5e7, 0x11d9, 0xaf,0x55,0x00,0x05,0x4e,0x43,0xff,0x02);

//MS_GUID    (DXVA_NoEncrypt,                         0x1b81bed0, 0xa0c7, 0x11d3, 0xb9, 0x84, 0x00, 0xc0, 0x4f, 0x2e, 0x73, 0xc5);

/* Codec capabilities GUID, sorted by codec */
#if 0
MS_GUID    ((DXVA2_ModeMPEG2_MoComp),                 0xe6a9f44b, 0x61b0, 0x4563, 0x9e, 0xa4, 0x63, 0xd2, 0xa3, 0xc6, 0xfe, 0x66);
MS_GUID    (DXVA2_ModeMPEG2_IDCT,                   0xbf22ad00, 0x03ea, 0x4690, 0x80, 0x77, 0x47, 0x33, 0x46, 0x20, 0x9b, 0x7e);
MS_GUID    (DXVA2_ModeMPEG2_VLD,                    0xee27417f, 0x5e28, 0x4e65, 0xbe, 0xea, 0x1d, 0x26, 0xb5, 0x08, 0xad, 0xc9);
DEFINE_GUID(DXVA2_ModeMPEG2and1_VLD,                0x86695f12, 0x340e, 0x4f04, 0x9f, 0xd3, 0x92, 0x53, 0xdd, 0x32, 0x74, 0x60);
DEFINE_GUID(DXVA2_ModeMPEG1_VLD,                    0x6f3ec719, 0x3735, 0x42cc, 0x80, 0x63, 0x65, 0xcc, 0x3c, 0xb3, 0x66, 0x16);
#endif
MS_GUID    (DXVA2_ModeH264_A,                       0x1b81be64, 0xa0c7, 0x11d3, 0xb9, 0x84, 0x00, 0xc0, 0x4f, 0x2e, 0x73, 0xc5);
MS_GUID    (DXVA2_ModeH264_B,                       0x1b81be65, 0xa0c7, 0x11d3, 0xb9, 0x84, 0x00, 0xc0, 0x4f, 0x2e, 0x73, 0xc5);
MS_GUID    (DXVA2_ModeH264_C,                       0x1b81be66, 0xa0c7, 0x11d3, 0xb9, 0x84, 0x00, 0xc0, 0x4f, 0x2e, 0x73, 0xc5);
MS_GUID    (DXVA2_ModeH264_D,                       0x1b81be67, 0xa0c7, 0x11d3, 0xb9, 0x84, 0x00, 0xc0, 0x4f, 0x2e, 0x73, 0xc5);
MS_GUID    (DXVA2_ModeH264_E,                       0x1b81be68, 0xa0c7, 0x11d3, 0xb9, 0x84, 0x00, 0xc0, 0x4f, 0x2e, 0x73, 0xc5);
MS_GUID    (DXVA2_ModeH264_F,                       0x1b81be69, 0xa0c7, 0x11d3, 0xb9, 0x84, 0x00, 0xc0, 0x4f, 0x2e, 0x73, 0xc5);
DEFINE_GUID(DXVA_ModeH264_VLD_Multiview,            0x9901CCD3, 0xca12, 0x4b7e, 0x86, 0x7a, 0xe2, 0x22, 0x3d, 0x92, 0x55, 0xc3); // MVC
DEFINE_GUID(DXVA_ModeH264_VLD_WithFMOASO_NoFGT,     0xd5f04ff9, 0x3418, 0x45d8, 0x95, 0x61, 0x32, 0xa7, 0x6a, 0xae, 0x2d, 0xdd);
DEFINE_GUID(DXVADDI_Intel_ModeH264_A,               0x604F8E64, 0x4951, 0x4c54, 0x88, 0xFE, 0xAB, 0xD2, 0x5C, 0x15, 0xB3, 0xD6);
DEFINE_GUID(DXVADDI_Intel_ModeH264_C,               0x604F8E66, 0x4951, 0x4c54, 0x88, 0xFE, 0xAB, 0xD2, 0x5C, 0x15, 0xB3, 0xD6);
DEFINE_GUID(DXVADDI_Intel_ModeH264_E,               0x604F8E68, 0x4951, 0x4c54, 0x88, 0xFE, 0xAB, 0xD2, 0x5C, 0x15, 0xB3, 0xD6); // DXVA_Intel_H264_NoFGT_ClearVideo
DEFINE_GUID(DXVA_ModeH264_VLD_NoFGT_Flash,          0x4245F676, 0x2BBC, 0x4166, 0xa0, 0xBB, 0x54, 0xE7, 0xB8, 0x49, 0xC3, 0x80);
#if 0
MS_GUID    (DXVA2_ModeWMV8_A,                       0x1b81be80, 0xa0c7, 0x11d3, 0xb9, 0x84, 0x00, 0xc0, 0x4f, 0x2e, 0x73, 0xc5);
MS_GUID    (DXVA2_ModeWMV8_B,                       0x1b81be81, 0xa0c7, 0x11d3, 0xb9, 0x84, 0x00, 0xc0, 0x4f, 0x2e, 0x73, 0xc5);

MS_GUID    (DXVA2_ModeWMV9_A,                       0x1b81be90, 0xa0c7, 0x11d3, 0xb9, 0x84, 0x00, 0xc0, 0x4f, 0x2e, 0x73, 0xc5);
MS_GUID    (DXVA2_ModeWMV9_B,                       0x1b81be91, 0xa0c7, 0x11d3, 0xb9, 0x84, 0x00, 0xc0, 0x4f, 0x2e, 0x73, 0xc5);
MS_GUID    (DXVA2_ModeWMV9_C,                       0x1b81be94, 0xa0c7, 0x11d3, 0xb9, 0x84, 0x00, 0xc0, 0x4f, 0x2e, 0x73, 0xc5);

MS_GUID    (DXVA2_ModeVC1_A,                        0x1b81beA0, 0xa0c7, 0x11d3, 0xb9, 0x84, 0x00, 0xc0, 0x4f, 0x2e, 0x73, 0xc5);
MS_GUID    (DXVA2_ModeVC1_B,                        0x1b81beA1, 0xa0c7, 0x11d3, 0xb9, 0x84, 0x00, 0xc0, 0x4f, 0x2e, 0x73, 0xc5);
MS_GUID    (DXVA2_ModeVC1_C,                        0x1b81beA2, 0xa0c7, 0x11d3, 0xb9, 0x84, 0x00, 0xc0, 0x4f, 0x2e, 0x73, 0xc5);
MS_GUID    (DXVA2_ModeVC1_D,                        0x1b81beA3, 0xa0c7, 0x11d3, 0xb9, 0x84, 0x00, 0xc0, 0x4f, 0x2e, 0x73, 0xc5);
DEFINE_GUID(DXVA2_ModeVC1_D2010,                    0x1b81beA4, 0xa0c7, 0x11d3, 0xb9, 0x84, 0x00, 0xc0, 0x4f, 0x2e, 0x73, 0xc5); // August 2010 update
DEFINE_GUID(DXVA_Intel_VC1_ClearVideo,              0xBCC5DB6D, 0xA2B6, 0x4AF0, 0xAC, 0xE4, 0xAD, 0xB1, 0xF7, 0x87, 0xBC, 0x89);
DEFINE_GUID(DXVA_Intel_VC1_ClearVideo_2,            0xE07EC519, 0xE651, 0x4CD6, 0xAC, 0x84, 0x13, 0x70, 0xCC, 0xEE, 0xC8, 0x51);
#endif
DEFINE_GUID(DXVA_nVidia_MPEG4_ASP,                  0x9947EC6F, 0x689B, 0x11DC, 0xA3, 0x20, 0x00, 0x19, 0xDB, 0xBC, 0x41, 0x84);
DEFINE_GUID(DXVA_ModeMPEG4pt2_VLD_Simple,           0xefd64d74, 0xc9e8, 0x41d7, 0xa5, 0xe9, 0xe9, 0xb0, 0xe3, 0x9f, 0xa3, 0x19);
DEFINE_GUID(DXVA_ModeMPEG4pt2_VLD_AdvSimple_NoGMC,  0xed418a9f, 0x010d, 0x4eda, 0x9a, 0xe3, 0x9a, 0x65, 0x35, 0x8d, 0x8d, 0x2e);
DEFINE_GUID(DXVA_ModeMPEG4pt2_VLD_AdvSimple_GMC,    0xab998b5b, 0x4258, 0x44a9, 0x9f, 0xeb, 0x94, 0xe5, 0x97, 0xa6, 0xba, 0xae);
DEFINE_GUID(DXVA_ModeMPEG4pt2_VLD_AdvSimple_Avivo,  0x7C74ADC6, 0xe2ba, 0x4ade, 0x86, 0xde, 0x30, 0xbe, 0xab, 0xb4, 0x0c, 0xc1);

/* */
typedef struct
{
    const char   *name;
    const GUID   *guid;
    int          codec;
} dxva2_mode_t;

/* XXX Prefered modes must come first */
static const dxva2_mode_t dxva2_modes[] = {
#if 0
    /* MPEG-1/2 */
    { "MPEG-2 variable-length decoder",                                               &DXVA2_ModeMPEG2_VLD,                   AV_CODEC_ID_MPEG2VIDEO },
    { "MPEG-2 & MPEG-1 variable-length decoder",                                      &DXVA2_ModeMPEG2and1_VLD,               AV_CODEC_ID_MPEG2VIDEO },
    { "MPEG-2 motion compensation",                                                   &DXVA2_ModeMPEG2_MoComp,                0 },
    { "MPEG-2 inverse discrete cosine transform",                                     &DXVA2_ModeMPEG2_IDCT,                  0 },

    { "MPEG-1 variable-length decoder",                                               &DXVA2_ModeMPEG1_VLD,                   0 },
#endif
    /* H.264 */
    { "H.264 variable-length decoder, film grain technology",                         &DXVA2_ModeH264_F,                      AV_CODEC_ID_H264 },
    { "H.264 variable-length decoder, no film grain technology (Intel ClearVideo)",   &DXVADDI_Intel_ModeH264_E,              AV_CODEC_ID_H264 },
    { "H.264 variable-length decoder, no film grain technology",                      &DXVA2_ModeH264_E,                      AV_CODEC_ID_H264 },
    { "H.264 variable-length decoder, no film grain technology, FMO/ASO",             &DXVA_ModeH264_VLD_WithFMOASO_NoFGT,    AV_CODEC_ID_H264 },
    { "H.264 variable-length decoder, no film grain technology, Flash",               &DXVA_ModeH264_VLD_NoFGT_Flash,         AV_CODEC_ID_H264 },

    { "H.264 inverse discrete cosine transform, film grain technology",               &DXVA2_ModeH264_D,                      0 },
    { "H.264 inverse discrete cosine transform, no film grain technology",            &DXVA2_ModeH264_C,                      0 },
    { "H.264 inverse discrete cosine transform, no film grain technology (Intel)",    &DXVADDI_Intel_ModeH264_C,              0 },

    { "H.264 motion compensation, film grain technology",                             &DXVA2_ModeH264_B,                      0 },
    { "H.264 motion compensation, no film grain technology",                          &DXVA2_ModeH264_A,                      0 },
    { "H.264 motion compensation, no film grain technology (Intel)",                  &DXVADDI_Intel_ModeH264_A,              0 },

#if 0
    /* WMV */
    { "Windows Media Video 8 motion compensation",                                    &DXVA2_ModeWMV8_B,                      0 },
    { "Windows Media Video 8 post processing",                                        &DXVA2_ModeWMV8_A,                      0 },

    { "Windows Media Video 9 IDCT",                                                   &DXVA2_ModeWMV9_C,                      0 },
    { "Windows Media Video 9 motion compensation",                                    &DXVA2_ModeWMV9_B,                      0 },
    { "Windows Media Video 9 post processing",                                        &DXVA2_ModeWMV9_A,                      0 },

    /* VC-1 */
    { "VC-1 variable-length decoder",                                                 &DXVA2_ModeVC1_D,                       AV_CODEC_ID_VC1 },
    { "VC-1 variable-length decoder",                                                 &DXVA2_ModeVC1_D,                       AV_CODEC_ID_WMV3 },
    { "VC-1 variable-length decoder",                                                 &DXVA2_ModeVC1_D2010,                   AV_CODEC_ID_VC1 },
    { "VC-1 variable-length decoder",                                                 &DXVA2_ModeVC1_D2010,                   AV_CODEC_ID_WMV3 },
    { "VC-1 variable-length decoder 2 (Intel)",                                       &DXVA_Intel_VC1_ClearVideo_2,           0 },
    { "VC-1 variable-length decoder (Intel)",                                         &DXVA_Intel_VC1_ClearVideo,             0 },

    { "VC-1 inverse discrete cosine transform",                                       &DXVA2_ModeVC1_C,                       0 },
    { "VC-1 motion compensation",                                                     &DXVA2_ModeVC1_B,                       0 },
    { "VC-1 post processing",                                                         &DXVA2_ModeVC1_A,                       0 },

    /* Xvid/Divx: TODO */
    { "MPEG-4 Part 2 nVidia bitstream decoder",                                       &DXVA_nVidia_MPEG4_ASP,                 0 },
    { "MPEG-4 Part 2 variable-length decoder, Simple Profile",                        &DXVA_ModeMPEG4pt2_VLD_Simple,          0 },
    { "MPEG-4 Part 2 variable-length decoder, Simple&Advanced Profile, no GMC",       &DXVA_ModeMPEG4pt2_VLD_AdvSimple_NoGMC, 0 },
    { "MPEG-4 Part 2 variable-length decoder, Simple&Advanced Profile, GMC",          &DXVA_ModeMPEG4pt2_VLD_AdvSimple_GMC,   0 },
    { "MPEG-4 Part 2 variable-length decoder, Simple&Advanced Profile, Avivo",        &DXVA_ModeMPEG4pt2_VLD_AdvSimple_Avivo, 0 },
#endif
    { NULL, NULL, 0 }
};

typedef struct
{
    const char   *name;
    D3DFORMAT    format;
    int codec;
} d3d_format_t;

/* XXX Prefered format must come first */
static const d3d_format_t d3d_formats[] = {
    { "YV12",   MAKEFOURCC('Y','V','1','2'), 0 },
    { "NV12",   MAKEFOURCC('N','V','1','2'), 0 },
/*    { "IMC3",   MAKEFOURCC('I','M','C','3'), 0 }, */
    { NULL, 0, 0 }
};

/**
 * \brief Creates a D3D device.
 * \param dll Direct3D dll instance.
 * \param d3d D3D pointer that will be created.
 * \param device device that will be created.
 * \param present_params present parameters.
 * \return 0 if success, -1 otherwise.
 */
static int hw_d3d_create_device(HINSTANCE dll, LPDIRECT3D9* d3d,
        LPDIRECT3DDEVICE9 * device, D3DPRESENT_PARAMETERS* present_params)
{
    if(!d3d || !device)
    {
        return -1;
    }

    LPDIRECT3D9 (WINAPI *CreateD3D)(UINT);
    CreateD3D = (void*)GetProcAddress(dll, "Direct3DCreate9");

    if(!CreateD3D)
    {
        return -1;
    }

    *d3d = CreateD3D(D3D_SDK_VERSION);
    if(!(*d3d))
    {
        return -1;
    }

    memset(present_params, 0x00, sizeof(D3DPRESENT_PARAMETERS));
    present_params->Flags = D3DPRESENTFLAG_VIDEO;
    present_params->Windowed = TRUE;
    present_params->hDeviceWindow = NULL;
    present_params->SwapEffect = D3DSWAPEFFECT_DISCARD;
    present_params->MultiSampleType = D3DMULTISAMPLE_NONE;
    present_params->PresentationInterval = D3DPRESENT_INTERVAL_DEFAULT;
    present_params->BackBufferCount = 0;
    present_params->BackBufferFormat = D3DFMT_X8R8G8B8;
    present_params->BackBufferWidth = 0;
    present_params->BackBufferHeight = 0;
    present_params->EnableAutoDepthStencil = FALSE;

    /* Direct3D needs a HWND to create a device, even without using ::Present
    this HWND is used to alert Direct3D when there's a change of focus window.
    For now, use GetDesktopWindow, as it looks harmless */
    if(FAILED(IDirect3D9_CreateDevice(*d3d, D3DADAPTER_DEFAULT,
                    D3DDEVTYPE_HAL, GetDesktopWindow(),
                    D3DCREATE_SOFTWARE_VERTEXPROCESSING |
                    D3DCREATE_MULTITHREADED,
                    present_params, device)))
    {
        IDirect3D9_Release(*d3d);
        *d3d = NULL;
        return -1;
    }

    return 0;
}

/**
 * \brief Closes a D3D device.
 * \param d3d D3D pointer that will be closed.
 * \param device device that will be closed.
 */
static void hw_d3d_close_device(LPDIRECT3D9* d3d, LPDIRECT3DDEVICE9* device)
{
    if(device && *device)
    {
        IDirect3DDevice9_Release(*device);
        *device = NULL;
    }
    if(d3d && *d3d)
    {
        IDirect3D9_Release(*d3d);
        *d3d = NULL;
    }
}

#if 0
/**
 * \brief Creates a Direct3D manager.
 * \param dll Direct3D dll instance.
 * \param manager manager pointer that will be created.
 * \param device existing Direct3D device.
 * \param token token that will be filled.
 * \return 0 if success, -1 otherwise.
 */
static int hw_d3d_create_manager(HINSTANCE dll,
        IDirect3DDeviceManager9** manager, LPDIRECT3DDEVICE9 device,
        unsigned int* token)
{
    HRESULT (WINAPI *CreateD3DDeviceManager)(UINT*, IDirect3DDeviceManager9**);

    if(!manager || !device || !token)
    {
        return -1;
    }

    CreateD3DDeviceManager = (void*)GetProcAddress(dll,
            "DXVA2CreateDirect3DDeviceManager9");

    if(!CreateD3DDeviceManager)
    {
        return -1;
    }

    if(FAILED(CreateD3DDeviceManager(token, manager)))
    {
        return -1;
    }

    if(FAILED(IDirect3DDeviceManager9_ResetDevice(*manager, device, *token)))
    {
        return -1;
    }

    return 0;
}

/**
 * \brief Closes a Direct3D manager.
 * \param manager manager pointer that will be closed.
 */
static void hw_d3d_close_manager(IDirect3DDeviceManager9** manager)
{
    if(manager && *manager)
    {
        IDirect3DDeviceManager9_Release(*manager);
        *manager = NULL;
    }
}
#endif

/**
 * \brief Creates a DXVA2 decoder service.
 * \param dll DXVA2 dll instance.
 * \param service DXVA2 decoder service pointer that will be created.
 * \param manager Direct3D manager.
 * \param handle service handle pionter that will be created.
 * \return 0 if success, -1 otherwise.
 */
#if 0
static int hw_dxva2_create_video_service(HINSTANCE dll,
        IDirectXVideoDecoderService** service,
        IDirect3DDeviceManager9* manager,
        HANDLE* handle)
#endif
static int hw_dxva2_create_video_service(HINSTANCE dll,
        IDirectXVideoDecoderService** service,
        IDirect3DDevice9* device)
{
    HRESULT (WINAPI *CreateVideoService)(IDirect3DDevice9*, REFIID,
            void**);

    if(!service)
    {
        return -1;
    }

    CreateVideoService = (void*)GetProcAddress(dll, "DXVA2CreateVideoService");

    if(!CreateVideoService)
    {
        return -1;
    }

    if(FAILED(CreateVideoService(device, &IID_IDirectXVideoDecoderService,
                    (void**)service)))
    {
        return -1;
    }

#if 0
    if(FAILED(IDirect3DDeviceManager9_OpenDeviceHandle(manager, handle)))
    {
        return -1;
    }

    if(FAILED(IDirect3DDeviceManager9_GetVideoService(manager, *handle,
                    &IID_IDirectXVideoDecoderService, (void**)service)))
    {
        IDirect3DDeviceManager9_CloseDeviceHandle(manager, *handle);
        *handle = NULL;
        return -1;
    }
#endif

    return 0;
}

/**
 * \brief Closes a DXVA2 video service.
 * \param service DXVA2 video service pointer that will be closed.
 * \param manager Direct3D manager.
 * \param device device handle that will be closed.
 */
static void hw_dxva2_close_video_service(
        IDirectXVideoDecoderService** service)
#if 0
        IDirect3DDeviceManager9* manager, HANDLE* device)
#endif
{
    if(service && *service)
    {
        IDirectXVideoDecoderService_Release(*service);
        *service = NULL;
    }

#if 0
    if(device && *device)
    {
        IDirect3DDeviceManager9_CloseDeviceHandle(manager, *device);
        *device = NULL;
    }
#endif
}

/**
 * \brief Retrieve input a render format for a specific codec.
 * \param decoder_service decoder service.
 * \param input input GUID that will be filled.
 * \param output render format that will be filled.
 * \return 0 if success, -1 otherwise.
 */
static int hw_dxva2_find_video_service_conversion(
        int codec_id,
        IDirectXVideoDecoderService* decoder_service, GUID* input,
        D3DFORMAT* output)
{
    /* Retreive supported modes from the decoder service */
    UINT input_count = 0;
    GUID* input_list = NULL;
    if(FAILED(IDirectXVideoDecoderService_GetDecoderDeviceGuids(
                    decoder_service,
                    &input_count,
                    &input_list)))
    {
        return -1;
    }

    /* Try all supported mode by our priority */
    for(size_t i = 0 ; dxva2_modes[i].name ; i++)
    {
        const dxva2_mode_t* mode = &dxva2_modes[i];

        if(!mode->codec || mode->codec != codec_id)
            continue;

        int is_suported = 0;
        for(const GUID*g = &input_list[0] ; !is_suported && g < &input_list[input_count] ; g++)
        {
            is_suported = IsEqualGUID(mode->guid, g);
        }

        if(!is_suported)
            continue;

        UINT output_count = 0;
        D3DFORMAT* output_list = NULL;
        if(FAILED(IDirectXVideoDecoderService_GetDecoderRenderTargets(
                        decoder_service,
                        mode->guid,
                        &output_count,
                        &output_list)))
        {
            continue;
        }

        for(size_t j = 0 ; d3d_formats[j].name ; j++)
        {
            const d3d_format_t *format = &d3d_formats[j];
            int is_suported = 0;

            for(size_t k = 0 ; !is_suported && k < output_count; k++)
            {
                is_suported = format->format == output_list[k];
            }

            if(!is_suported)
                continue;

            /* We have our solution */
            *input  = *mode->guid;
            *output = format->format;
            CoTaskMemFree(output_list);
            CoTaskMemFree(input_list);
            return 0;
        }
        CoTaskMemFree(output_list);
    }
    CoTaskMemFree(input_list);
    return -1;
}

/**
 * \brief Creates a DXVA2 decoder and associated surfaces.
 * \param dll DXVA2 dll instance.
 * \param decoder DXVA2 decoder pointer that will be created.
 * \param service DXVA2 decoder service.
 * \param d3d_surfaces associated surfaces that will be created.
 * \param width width of image.
 * \param height height of image.
 * \param nb_surfaces number of surfaces to allocate.
 * \param codec_id ID of the codec.
 * \param input input GUID.
 * \param render render format.
 * \return 0 if success, -1 otherwise.
 */
static int hw_dxva2_create_decoder(HINSTANCE dll,
        IDirectXVideoDecoder** decoder,
        DXVA2_ConfigPictureDecode* config,
        IDirectXVideoDecoderService* service,
        LPDIRECT3DSURFACE9* d3d_surfaces, int width, int height,
        size_t nb_surfaces, int codec_id, GUID* input, D3DFORMAT* render)
{
    int w_surface = 0;
    int h_surface = 0;

    if(!decoder || !d3d_surfaces || !nb_surfaces)
    {
        return -1;
    }

    w_surface = width;
    h_surface = height;

    /* creates the surfaces */
    w_surface = (width  + 15) & ~15;
    h_surface = (height + 15) & ~15;

    LPDIRECT3DSURFACE9 surface_list[nb_surfaces];
    if(FAILED(IDirectXVideoDecoderService_CreateSurface(service,
                    w_surface,
                    h_surface,
                    nb_surfaces - 1,
                    *render,
                    D3DPOOL_DEFAULT,
                    0,
                    DXVA2_VideoDecoderRenderTarget,
                    surface_list,
                    NULL)))
    {
        return -1; 
    }

    DXVA2_VideoDesc dsc;
    memset(&dsc, 0x00, sizeof(DXVA2_VideoDesc));
    dsc.SampleWidth = width;
    dsc.SampleHeight = height;
    dsc.Format = *render;
    dsc.InputSampleFreq.Numerator   = 0;
    dsc.InputSampleFreq.Denominator = 0;
    dsc.OutputFrameFreq = dsc.InputSampleFreq;
    dsc.UABProtectionLevel = FALSE;
    dsc.Reserved = 0;

    DXVA2_ExtendedFormat *ext = &dsc.SampleFormat;
    ext->SampleFormat = 0; //DXVA2_SampleUnknown;
    ext->VideoChromaSubsampling = 0; //DXVA2_VideoChromaSubsampling_Unknown;
    ext->NominalRange = 0; //DXVA2_NominalRange_Unknown;
    ext->VideoTransferMatrix = 0; //DXVA2_VideoTransferMatrix_Unknown;
    ext->VideoLighting = 0; //DXVA2_VideoLighting_Unknown;
    ext->VideoPrimaries = 0; //DXVA2_VideoPrimaries_Unknown;
    ext->VideoTransferFunction = 0; //DXVA2_VideoTransFunc_Unknown;

    /* List all configurations available for the decoder */
    UINT cfg_count = 0;
    DXVA2_ConfigPictureDecode *cfg_list = NULL;
    if(FAILED(IDirectXVideoDecoderService_GetDecoderConfigurations(
                    service,
                    input,
                    &dsc,
                    NULL,
                    &cfg_count,
                    &cfg_list)))
    {
        return -1;
    }

    /* Select the best decoder configuration */
    int cfg_score = 0;
    for(size_t i = 0 ; i < cfg_count ; i++)
    {
        const DXVA2_ConfigPictureDecode *cfg = &cfg_list[i];

        int score = 0;
        if(cfg->ConfigBitstreamRaw == 1)
            score = 1;
        else if(codec_id == AV_CODEC_ID_H264 && cfg->ConfigBitstreamRaw == 2)
            score = 2;
        else
            continue;

        if(IsEqualGUID(&cfg->guidConfigBitstreamEncryption, &DXVA_NoEncrypt))
            score += 16;

        if(cfg_score < score)
        {
            *config = *cfg;
            cfg_score = score;
        }
    }
    CoTaskMemFree(cfg_list);

    if(cfg_score <= 0)
    {
        return -1;
    }

    if(FAILED(IDirectXVideoDecoderService_CreateVideoDecoder(
                    service,
                    input,
                    &dsc,
                    config,
                    surface_list,
                    nb_surfaces,
                    decoder)))
    {
        return -1;
    }

    return 0;
}

/**
 * \brief Closes a DXVA2 decoder and associated surfaces.
 * \param decoder DXVA2 decoder pointer that will be closed.
 * \param d3d_surfaces associated surfaces that will be closed.
 * \param nb_surfaces number of surfaces to deallocate.
 */

static void hw_dxva2_close_decoder(IDirectXVideoDecoder** decoder,
        LPDIRECT3DSURFACE9* d3d_surfaces, size_t nb_surfaces)
{
    if(decoder && *decoder)
    {
        IDirectXVideoDecoder_Release(*decoder);
        *decoder = NULL;
    }

    for(size_t i = 0 ; i < nb_surfaces ; i++)
    {
        IDirect3DSurface9_Release(d3d_surfaces[i]);
    }
}

struct hw_decoder* hw_decoder_new(enum CodecID codec_id)
{
    struct hw_decoder* obj = NULL;
    HINSTANCE d3d_dll = NULL;
    HINSTANCE dxva2_dll = NULL; 
    LPDIRECT3D9 d3d = NULL;
    D3DPRESENT_PARAMETERS present_params;
    LPDIRECT3DDEVICE9 device = NULL;
    IDirectXVideoDecoderService* decoder_service = NULL;
#if 0
    IDirect3DDeviceManager9* manager = NULL;
    unsigned int manager_token = 0;
    HANDLE device_handle = NULL;
#endif
    GUID decoder_input;
    D3DFORMAT render_format;

    /* open DLLs */
    d3d_dll = LoadLibrary(TEXT("D3D9.DLL"));

    if(!d3d_dll)
    {
        return NULL;
    }

    dxva2_dll = LoadLibrary(TEXT("DXVA2.DLL"));

    if(!dxva2_dll)
    {
        FreeLibrary(d3d_dll);
        return NULL;
    }

    /* open D3D and DXVA2 stuff */
    if(hw_d3d_create_device(d3d_dll, &d3d, &device, &present_params) != 0)
    {
        FreeLibrary(d3d_dll);
        FreeLibrary(dxva2_dll);
        return NULL;
    }

#if 0
    if(hw_d3d_create_manager(dxva2_dll, &manager, device, &manager_token) != 0)
    {
        hw_d3d_close_device(&d3d, &device);
        FreeLibrary(dxva2_dll);
        FreeLibrary(d3d_dll);
        return NULL;
    }
#endif

    if(hw_dxva2_create_video_service(dxva2_dll, &decoder_service, device) != 0 
            || hw_dxva2_find_video_service_conversion(codec_id, decoder_service,
                &decoder_input, &render_format) == -1)
    {
        /* hw_d3d_close_manager(&manager); */
        hw_d3d_close_device(&d3d, &device);
        FreeLibrary(dxva2_dll);
        FreeLibrary(d3d_dll);
        return NULL;
    }

    obj = malloc(sizeof(struct hw_decoder));

    if(!obj)
    {
        hw_dxva2_close_video_service(&decoder_service);
/*        hw_d3d_close_manager(&manager); */
        hw_d3d_close_device(&d3d, &device);
        FreeLibrary(dxva2_dll);
        FreeLibrary(d3d_dll);
        return NULL;
    }

    memset(obj, 0x00, sizeof(struct hw_decoder));

    /* some initializations */
    obj->width = 0;
    obj->height = 0;
    obj->codec_id = codec_id;

    obj->context.d3d_dll = d3d_dll;
    obj->context.dxva2_dll = dxva2_dll;
    obj->context.d3d = d3d;
    obj->context.device = device;
    obj->context.present_params = present_params;
#if 0
    obj->context.manager = manager;
    obj->context.manager_token = manager_token;
    obj->context.device_handle = device_handle;
#endif
    obj->context.decoder_service = decoder_service;
    obj->context.render_format = render_format;
    obj->context.decoder_input = decoder_input;

    if(codec_id == AV_CODEC_ID_H264)
    {
        obj->context.nb_surfaces = 21;
    }
    else
    {
        obj->context.nb_surfaces = 3;
    }

    for(size_t i = 0 ; i < obj->context.nb_surfaces ; i++)
    {
        obj->context.surfaces[i].surface = NULL;
        obj->context.surfaces[i].is_used = 0;
    }

    return obj;
}

void hw_decoder_free(struct hw_decoder** obj)
{
    struct hw_decoder* o = *obj;
    struct hw_dxva2_context* context = &o->context;

    /* release DXVA2 decoder and surfaces */
    hw_dxva2_close_decoder(&context->decoder, context->d3d_surfaces,
            context->nb_surfaces);

    /* release DXVA2 and decoder service */
    hw_dxva2_close_video_service(&context->decoder_service);

    /* release D3D device */
/*    hw_d3d_close_manager(&context->manager); */
    hw_d3d_close_device(&context->d3d, &context->device);

    /* release DLLs */
    if(o->context.dxva2_dll)
    {
        FreeLibrary(o->context.dxva2_dll);
    }

    if(o->context.d3d_dll)
    {
        FreeLibrary(o->context.d3d_dll);
    }

    free(o);
    *obj = NULL;
}

int hw_decoder_init(struct hw_decoder* obj, void* profile, int width,
        int height)
{
    if(obj->width != width && obj->height != height)
    {
        hw_dxva2_close_decoder(&obj->context.decoder, obj->context.d3d_surfaces,
                obj->context.nb_surfaces);
    }
    else
    {
        /* configuration has not changed so no init! */
        return 0;
    }

    /* create decoder */
    if(hw_dxva2_create_decoder(obj->context.dxva2_dll, &obj->context.decoder,
                &obj->context.config, obj->context.decoder_service,
                obj->context.d3d_surfaces, width, height,
                obj->context.nb_surfaces, obj->codec_id,
                &obj->context.decoder_input, &obj->context.render_format) != 0)
    {
        return -1;
    }

    for(size_t i = 0 ; i < obj->context.nb_surfaces ; i++)
    {
        obj->context.surfaces[i].surface = obj->context.d3d_surfaces[i];
        obj->context.surfaces[i].is_used = 0;
    }

    obj->width = width;
    obj->height = height;
    return 0;
}

void* hw_decoder_get_surface(struct hw_decoder* obj)
{
    struct hw_dxva2_context* context = &obj->context;
    static size_t idx = 0;
    size_t i = 0;
    void* ret = NULL;

    for(i = idx ; i < context->nb_surfaces ; i++)
    {
        if(!context->surfaces[i].is_used)
        {
            context->surfaces[i].is_used = 1;
            ret = (void*)(intptr_t)context->surfaces[i].surface;
            break;
        }
    }

    if(i >= context->nb_surfaces)
    {
        i = 0;
        /* all is busy so force to take the first one! */
        context->surfaces[0].is_used = 1;
        ret = context->surfaces[0].surface;
    }
    idx = i + 1;
    return ret;
}

void hw_decoder_release_surface(struct hw_decoder* obj, void* surface)
{
    struct hw_dxva2_context* context = &obj->context;

    for(size_t i = 0 ; i < context->nb_surfaces ; i++)
    {
        if(context->surfaces[i].surface == surface)
        {
            context->surfaces[i].is_used = 0;
            break;
        }
    }
}

void hw_decoder_init_hwaccel_context(struct hw_decoder* obj, void* hwaccel_context)
{
    struct dxva_context* dxva2 = hwaccel_context;

    dxva2->decoder = obj->context.decoder;
    dxva2->cfg = &obj->context.config;
    dxva2->surface_count = obj->context.nb_surfaces;
    dxva2->surface = obj->context.d3d_surfaces;
}

int hw_decoder_is_codec_supported(int codec_id)
{
    struct hw_decoder* obj = hw_decoder_new(codec_id);

    if(obj)
    {
        hw_decoder_free(&obj);
        return 1;
    }
    return 0;
}

enum PixelFormat hw_ffmpeg_get_format(struct AVCodecContext *avctx,
        const enum PixelFormat *fmt)
{
    int profile = -1;

    for(int i = 0; fmt[i] != PIX_FMT_NONE; i++)
    {
        if(fmt[i] != PIX_FMT_DXVA2_VLD)
        {
            continue;
        }

        if(profile >= 0)
        {
            struct hw_decoder* obj = hw_decoder_new(avctx->codec_id);

            if(!obj)
            {
                continue;
            }

            if(hw_decoder_init(obj, (void*)(intptr_t)profile, avctx->width,
                        avctx->height) == 0)
            {
                struct dxva_context* hwaccel = malloc(sizeof(struct dxva_context));

                if(!hwaccel)
                {
                    hw_decoder_free(&obj);
                    continue;
                }

                memset(hwaccel, 0x00, sizeof(struct dxva_context));
                hw_decoder_init_hwaccel_context(obj, hwaccel);
                avctx->hwaccel_context = hwaccel;
                avctx->opaque = obj;

                fprintf(stdout, "Use DXVA2 decoding!\n");
                fflush(stdout);
                return fmt[i];
            }
            else
            {
                hw_decoder_free(&obj);
            }
        }
    }

    return avcodec_default_get_format(avctx, fmt);
}

int hw_ffmpeg_get_buffer(struct AVCodecContext* avctx, AVFrame* avframe)
{
    if(avctx->hwaccel_context)
    {
        struct hw_decoder* obj = avctx->opaque;
        void *surface = NULL;

        surface = hw_decoder_get_surface(obj);

        avframe->type = FF_BUFFER_TYPE_USER;
        avframe->data[0] = surface;
        avframe->data[1] = NULL;
        avframe->data[2] = NULL;
        avframe->data[3] = surface;
        avframe->linesize[0] = 0;
        avframe->linesize[1] = 0;
        avframe->linesize[2] = 0;
        avframe->linesize[3] = 0;
        return 0;
    }

    return avcodec_default_get_buffer(avctx, avframe);
}

void hw_ffmpeg_release_buffer(struct AVCodecContext* avctx, AVFrame* avframe)
{
    if(avctx->hwaccel_context)
    {
        struct hw_decoder* obj = avctx->hwaccel_context;

        hw_decoder_release_surface(obj, avframe->data[3]);
        avframe->data[3] = NULL;

        for(size_t i = 0 ; i < 4 ; i++)
        {
            avframe->data[i] = NULL;
            avframe->linesize[i] = 0;
        }
        return;
    }

    avcodec_default_release_buffer(avctx, avframe);
}

#endif

