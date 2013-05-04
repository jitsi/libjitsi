/**
 * \file dxva2api_mingw.h
 * \brief Adds some missing DXVA2 COM interfaces.
 */

#ifndef DXVA2API_MINGW_H
#define DXVA2API_MINGW_H

#include <objbase.h>
#include <d3d9.h>
#include <dxva2api.h>

#pragma pack(push, 1)

//#define DXVA2API_USE_BITFIELD

typedef struct _DXVA2_ExtendedFormat {
#ifdef DXVA2API_USE_BITFIELDS
    union {
        struct {
            UINT SampleFormat           : 8;
            UINT VideoChromaSubsampling : 4;
            UINT NominalRange           : 3;
            UINT VideoTransferMatrix    : 3;
            UINT VideoLighting          : 4;
            UINT VideoPrimaries         : 5;
            UINT VideoTransferFunction  : 5;
        };
        UINT value;
    };
#else
    UINT value;
#endif
} DXVA2_ExtendedFormat;


typedef struct _DXVA2_Frequency {
    UINT Numerator;
    UINT Denominator;
} DXVA2_Frequency;

typedef struct _DXVA2_VideoDesc {
    UINT SampleWidth;
    UINT SampleHeight;
    DXVA2_ExtendedFormat SampleFormat;
    D3DFORMAT Format;
    DXVA2_Frequency InputSampleFreq;
    DXVA2_Frequency OutputFrameFreq;
    UINT UABProtectionLevel;
    UINT Reserved;
} DXVA2_VideoDesc;


typedef struct {
    union {
        struct {
            USHORT  Fraction;
            SHORT   Value;
        };
        LONG ll;
    };
}DXVA2_Fixed32;

typedef struct {
    UCHAR Cr;
    UCHAR Cb;
    UCHAR Y;
    UCHAR Alpha;
}DXVA2_AYUVSample8;

typedef struct {
    USHORT Cr;
    USHORT Cb;
    USHORT Y;
    USHORT Alpha;
}DXVA2_AYUVSample16;

typedef struct {
    DXVA2_Fixed32 MinValue;
    DXVA2_Fixed32 MaxValue;
    DXVA2_Fixed32 DefaultValue;
    DXVA2_Fixed32 StepSize;
}DXVA2_ValueRange;

typedef struct {
    DXVA2_Fixed32 Brightness;
    DXVA2_Fixed32 Contrast;
    DXVA2_Fixed32 Hue;
    DXVA2_Fixed32 Saturation;
}DXVA2_ProcAmpValues;

typedef struct {
    DXVA2_Fixed32 Level;
    DXVA2_Fixed32 Threshold;
    DXVA2_Fixed32 Radius;
}DXVA2_FilterValues;

typedef struct {
    UINT DeviceCaps;
    D3DPOOL InputPool;
    UINT NumForwardRefSamples;
    UINT NumBackwardRefSamples;
    UINT Reserved;
    UINT DeinterlaceTechnology;
    UINT ProcAmpControlCaps;
    UINT VideoProcessorOperations;
    UINT NoiseFilterTechnology;
    UINT DetailFilterTechnology;
}DXVA2_VideoProcessorCaps;

#ifndef _REFERENCE_TIME_
#define _REFERENCE_TIME_
typedef long long int64_t;
typedef int64_t REFERENCE_TIME;
#endif

typedef struct {
    REFERENCE_TIME Start;
    REFERENCE_TIME End;
    DXVA2_ExtendedFormat SampleFormat;
    IDirect3DSurface9 *SrcSurface;
    RECT SrcRect;
    RECT DstRect;
    DXVA2_AYUVSample8 Pal[16];
    DXVA2_Fixed32 PlanarAlpha;
    DWORD SampleData;
}DXVA2_VideoSample;


typedef struct {
    REFERENCE_TIME TargetFrame;
    RECT TargetRect;
    SIZE ConstrictionSize;
    UINT StreamingFlags;
    DXVA2_AYUVSample16 BackgroundColor;
    DXVA2_ExtendedFormat DestFormat;
    DXVA2_ProcAmpValues ProcAmpValues;
    DXVA2_Fixed32 Alpha;
    DXVA2_FilterValues NoiseFilterLuma;
    DXVA2_FilterValues NoiseFilterChroma;
    DXVA2_FilterValues DetailFilterLuma;
    DXVA2_FilterValues DetailFilterChroma;
    DWORD DestData;
} DXVA2_VideoProcessBltParams;

#pragma pack(pop)

typedef struct IDirectXVideoProcessorService IDirectXVideoProcessorService;
typedef struct IDirectXVideoProcessor IDirectXVideoProcessor;

#undef INTERFACE
#define INTERFACE IDirectXVideoProcessor
DECLARE_INTERFACE_( IDirectXVideoProcessor, IUnknown )
{
    STDMETHOD( QueryInterface ) ( THIS_ REFIID, PVOID* ) PURE;
    STDMETHOD_( ULONG, AddRef ) ( THIS ) PURE;
    STDMETHOD_( ULONG, Release ) ( THIS ) PURE;
    STDMETHOD( GetVideoProcessorService ) ( THIS_ IDirectXVideoProcessorService** ) PURE;
    STDMETHOD( GetCreationParameters ) ( THIS_ GUID*, DXVA2_VideoDesc*, D3DFORMAT*, UINT* ) PURE;
    STDMETHOD( GetVideoProcessorCaps ) ( THIS_ DXVA2_VideoProcessorCaps* ) PURE;
    STDMETHOD( GetProcAmpRange ) ( THIS_ UINT, DXVA2_ValueRange* ) PURE;
    STDMETHOD( GetFilterPropertyRange ) ( THIS_ UINT, DXVA2_ValueRange* ) PURE;
    STDMETHOD( VideoProcessBlt ) ( THIS_ IDirect3DSurface9*, DXVA2_VideoProcessBltParams*, DXVA2_VideoSample*, UINT, HANDLE* ) PURE;
};

#if !defined(__cplusplus) || defined(CINTERFACE)
#define IDirectXVideoProcessor_QueryInterface( p, a, b ) (p)->lpVtbl->QueryInterface( p, a, b )
#define IDirectXVideoProcessor_AddRef( p )  (p)->lpVtbl->AddRef( p )
#define IDirectXVideoProcessor_Release( p ) (p)->lpVtbl->Release( p )
#define IDirectXVideoProcessor_GetVideoProcessorService( p, a ) (p)->lpVtbl->GetVideoProcessorService( p, a )
#define IDirectXVideoProcessor_GetCreationParameters( p, a, b, c, d ) (p)->lpVtbl->GetCreationParameters( p, a, b, c, d )
#define IDirectXVideoProcessor_GetVideoProcessorCaps( p, a ) (p)->lpVtbl->GetVideoProcessorCaps( p, a )
#define IDirectXVideoProcessor_GetProcAmpRange( p, a, b ) (p)->lpVtbl->GetProcAmpRange( p, a, b )
#define IDirectXVideoProcessor_GetFilterPropertyRange( p, a, b ) (p)->lpVtbl->GetFilterPropertyRange( p, a, b )
#define IDirectXVideoProcessor_VideoProcessBlt( p, a, b, c, d, e ) (p)->lpVtbl->VideoProcessBlt( p, a, b, c, d, e )
#else
#define IDirectXVideoProcessor_QueryInterface( p, a, b ) (p)->QueryInterface( a, b )
#define IDirectXVideoProcessor_AddRef( p )  (p)->AddRef()
#define IDirectXVideoProcessor_Release( p ) (p)->Release()
#define IDirectXVideoProcessor_GetVideoProcessorService( p, a ) (p)->GetVideoProcessorService( a )
#define IDirectXVideoProcessor_GetCreationParameters( p, a, b, c, d ) (p)->GetCreationParameters( a, b, c, d )
#define IDirectXVideoProcessor_GetVideoProcessorCaps( p, a ) (p)->GetVideoProcessorCaps( a )
#define IDirectXVideoProcessor_GetProcAmpRange( p, a, b ) (p)->GetProcAmpRange( a, b )
#define IDirectXVideoProcessor_GetFilterPropertyRange( p, a, b ) (p)->GetFilterPropertyRange( a, b )
#define IDirectXVideoProcessor_VideoProcessBlt( p, a, b, c, d, e ) (p)->VideoProcessBlt( a, b, c, d, e )
#endif

#undef INTERFACE
#define INTERFACE IDirectXVideoProcessorService
DECLARE_INTERFACE_( IDirectXVideoProcessorService, IDirectXVideoAccelerationService )
{
    STDMETHOD( QueryInterface ) ( THIS_ REFIID, PVOID* ) PURE;
    STDMETHOD_( ULONG, AddRef ) ( THIS ) PURE;
    STDMETHOD_( ULONG, Release ) ( THIS ) PURE;
    STDMETHOD( CreateSurface ) ( THIS_ UINT, UINT, UINT, D3DFORMAT, D3DPOOL, DWORD, DWORD, IDirect3DSurface9**, HANDLE* ) PURE;
    STDMETHOD( RegisterVideoProcessorSoftwareDevice ) ( THIS_ void* ) PURE;
    STDMETHOD( GetVideoProcessorDeviceGuids ) ( THIS_ DXVA2_VideoDesc*, UINT, GUID** ) PURE;
    STDMETHOD( GetVideoProcessorRenderTargets ) ( THIS_ REFGUID, DXVA2_VideoDesc*, UINT*, D3DFORMAT** ) PURE;
    STDMETHOD( GetVideoProcessorSubStreamFormats ) ( THIS_ REFGUID, DXVA2_VideoDesc*, D3DFORMAT, UINT*, D3DFORMAT** ) PURE;
    STDMETHOD( GetVideoProcessorCaps ) ( THIS_ REFGUID, DXVA2_VideoDesc*, D3DFORMAT, DXVA2_VideoProcessorCaps* ) PURE;
    STDMETHOD( GetProcAmpRange ) ( THIS_ REFGUID, DXVA2_VideoDesc*, D3DFORMAT, UINT, DXVA2_ValueRange* ) PURE;
    STDMETHOD( GetFilterPropertyRange ) ( THIS_ REFGUID, DXVA2_VideoDesc*, D3DFORMAT, UINT, DXVA2_ValueRange* ) PURE;
    STDMETHOD( CreateVideoProcessor ) ( THIS_ REFGUID, DXVA2_VideoDesc*, D3DFORMAT, UINT, IDirectXVideoProcessor** ) PURE;
};

#if !defined(__cplusplus) || defined(CINTERFACE)
#define IDirectXVideoProcessorService_QueryInterface( p, a, b ) (p)->lpVtbl->QueryInterface( p, a, b )
#define IDirectXVideoProcessorService_AddRef( p )  (p)->lpVtbl->AddRef( p )
#define IDirectXVideoProcessorService_Release( p ) (p)->lpVtbl->Release( p )
#define IDirectXVideoProcessorService_CreateSurface( p, a, b, c, d, e, f, g, h, i ) (p)->lpVtbl->CreateSurface( p, a, b, c, d, e, f, g, h, i )
#define IDirectXVideoProcessorService_RegisterVideoProcessorSoftwareDevice( p, a ) (p)->lpVtbl->RegisterVideoProcessorSoftwareDevice( p, a )
#define IDirectXVideoProcessorService_GetVideoProcessorDeviceGuids( p, a, b, c ) (p)->lpVtbl->GetVideoProcessorDeviceGuids( p, a, b, c )
#define IDirectXVideoProcessorService_GetVideoProcessorRenderTargets( p, a, b, c, d ) (p)->lpVtbl->GetVideoProcessorRenderTargets( p, a, b, c, d )
#define IDirectXVideoProcessorService_GetVideoProcessorSubStreamFormats( p, a, b, c, d, e ) (p)->lpVtbl->GetVideoProcessorSubStreamFormats( p, a, b, c, d, e )
#define IDirectXVideoProcessorService_GetVideoProcessorCaps( p, a, b, c, d ) (p)->lpVtbl->GetVideoProcessorCaps( p, a, b, c, d )
#define IDirectXVideoProcessorService_GetProcAmpRange( p, a, b, c, d, e ) (p)->lpVtbl->GetProcAmpRange( p, a, b, c, d, e )
#define IDirectXVideoProcessorService_GetFilterPropertyRange( p, a, b, c, d, e ) (p)->lpVtbl->GetFilterPropertyRange( p, a, b, c, d, e )
#define IDirectXVideoProcessorService_CreateVideoProcessor( p, a, b, c, d, e ) (p)->lpVtbl->CreateVideoProcessor( p, a, b, c, d, e )
#else
#define IDirectXVideoProcessorService_QueryInterface( p, a, b ) (p)->QueryInterface( a, b )
#define IDirectXVideoProcessorService_AddRef( p )  (p)->AddRef()
#define IDirectXVideoProcessorService_Release( p ) (p)->Release()
#define IDirectXVideoProcessorService_CreateSurface( p, a, b, c, d, e, f, g, h, i ) (p)->CreateSurface( a, b, c, d, e, f, g, h, i )
#define IDirectXVideoProcessorService_RegisterVideoProcessorSoftwareDevice( p, a ) (p)->RegisterVideoProcessorSoftwareDevice( a )
#define IDirectXVideoProcessorService_GetVideoProcessorDeviceGuids( p, a, b, c ) (p)->GetVideoProcessorDeviceGuids( a, b, c )
#define IDirectXVideoProcessorService_GetVideoProcessorRenderTargets( p, a, b, c, d ) (p)->GetVideoProcessorRenderTargets( a, b, c, d )
#define IDirectXVideoProcessorService_GetVideoProcessorSubStreamFormats( p, a, b, c, d, e ) (p)->GetVideoProcessorSubStreamFormats( a, b, c, d, e )
#define IDirectXVideoProcessorService_GetVideoProcessorCaps( p, a, b, c, d ) (p)->GetVideoProcessorCaps( a, b, c, d )
#define IDirectXVideoProcessorService_GetProcAmpRange( p, a, b, c, d, e ) (p)->GetProcAmpRange( a, b, c, d, e )
#define IDirectXVideoProcessorService_GetFilterPropertyRange( p, a, b, c, d, e ) (p)->GetFilterPropertyRange( a, b, c, d, e )
#define IDirectXVideoProcessorService_CreateVideoProcessor( p, a, b, c, d, e ) (p)->CreateVideoProcessor( a, b, c, d, e )
#endif

#endif /* DXVA2API_MINGW_H */

