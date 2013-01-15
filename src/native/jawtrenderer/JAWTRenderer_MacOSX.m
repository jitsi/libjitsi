/*
 * SIP Communicator, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */

#include "JAWTRenderer.h"

#include <jawt_md.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

/* sysctlbyname */
#include <sys/types.h>
#include <sys/sysctl.h>

#import <AppKit/NSView.h>
#import <Foundation/NSAutoreleasePool.h>
#import <Foundation/NSObject.h>
#import <OpenGL/gl.h>
#import <OpenGL/OpenGL.h>
#import <QuartzCore/CALayer.h>
#import <QuartzCore/CAOpenGLLayer.h>

#define JAWT_RENDERER_TEXTURE GL_TEXTURE_RECTANGLE_EXT
#define JAWT_RENDERER_TEXTURE_FORMAT GL_BGRA
#define JAWT_RENDERER_TEXTURE_TYPE GL_UNSIGNED_BYTE

@interface JAWTRendererLayer : CAOpenGLLayer
{
/*
 * XXX The fields of JAWTRendererLayer are declared public for the sake of
 * performance so that the function JAWTRenderer_process may effectively be
 * implemented as a member of the class.
 */
@public
    CGLContextObj _glContext;
    jint _height;
    CGLPixelFormatObj _pixelFormat;
    GLuint _texture;
    jint _width;
}

- (CGLContextObj)copyCGLContextForPixelFormat:(CGLPixelFormatObj)pixelFormat;
- (CGLPixelFormatObj)copyCGLPixelFormatForDisplayMask:(uint32_t)mask;
- (void)dealloc;
- (void)drawInCGLContext:(CGLContextObj)glContext
             pixelFormat:(CGLPixelFormatObj)pixelFormat
            forLayerTime:(CFTimeInterval)timeInterval
             displayTime:(const CVTimeStamp *)timeStamp;
- (id)init;
@end /* JAWTRendererLayer */

void
JAWTRenderer_close(JNIEnv *env, jclass clazz, jlong handle, jobject component)
{
    JAWTRendererLayer **thiz;
    NSAutoreleasePool *autoreleasePool;

    thiz = (JAWTRendererLayer **) (intptr_t) handle;
    autoreleasePool = [[NSAutoreleasePool alloc] init];

    if (*thiz)
        [*thiz release];
    free(thiz);

    [autoreleasePool release];
}

jlong
JAWTRenderer_open(JNIEnv *env, jclass clazz, jobject component)
{
    return (jlong) (intptr_t) calloc(1, sizeof(JAWTRendererLayer *));
}

jboolean
JAWTRenderer_paint
    (jint version, JAWT_DrawingSurfaceInfo *dsi, jclass clazz, jlong handle,
        jobject g, jint zOrder)
{
    NSAutoreleasePool *autoreleasePool;
    JAWTRendererLayer *thizLayer;
    jboolean wantsPaint = JNI_TRUE;
    JAWTRendererLayer **thiz;

    autoreleasePool = [[NSAutoreleasePool alloc] init];

    /*
     * The native peer of the AWT Canvas that is the component of this
     * JAWTRenderer should be a JAWTRendererLayer.
     */
    if (JAWT_MACOSX_USE_CALAYER == (version & JAWT_MACOSX_USE_CALAYER))
    {
        id<JAWT_SurfaceLayers> surfaceLayers
            = (id<JAWT_SurfaceLayers>) (dsi->platformInfo);
        CALayer *layer = surfaceLayers.layer;

        if (layer && [layer isKindOfClass:[JAWTRendererLayer class]])
            thizLayer = (JAWTRendererLayer *) layer;
        else
        {
            thizLayer = [JAWTRendererLayer layer];
            if (thizLayer)
                surfaceLayers.layer = thizLayer;
            else
                wantsPaint = JNI_FALSE;
        }
    }
    else
    {
        NSView *view
            = ((JAWT_MacOSXDrawingSurfaceInfo *) (dsi->platformInfo))
                ->cocoaViewRef;
        CALayer *layer = [view layer];

        if (layer && [layer isKindOfClass:[JAWTRendererLayer class]])
            thizLayer = (JAWTRendererLayer *) layer;
        else
        {
            thizLayer = [JAWTRendererLayer layer];
            if (thizLayer)
            {
                [view setLayer:thizLayer];
                [view setWantsLayer:YES];
            }
            else
                wantsPaint = JNI_FALSE;
        }
    }

    /*
     * This JAWTRenderer should paint into the JAWTRendererLayer which is the
     * native peer of the AWT Canvas that is the component of this JAWTRenderer.
     */
    thiz = (JAWTRendererLayer **) (intptr_t) handle;
    if (*thiz != thizLayer)
    {
        if (*thiz)
            [*thiz release];
        *thiz = thizLayer;
        if (thizLayer)
            [thizLayer retain];
    }

    /*
     * Forward the paint request from the AWT Canvas to the JAWTRendererLayer
     * that is the former's native peer.
     */
    if (thizLayer)
    {
        if (zOrder > -1)
            thizLayer.zPosition = zOrder;
        [thizLayer setNeedsDisplay];
    }

    [autoreleasePool release];
    return wantsPaint;
}

jboolean
JAWTRenderer_process
    (JNIEnv *env, jclass clazz, jlong handle, jobject component, jint *data,
        jint length, jint width, jint height)
{
    JAWTRendererLayer *thiz = *((JAWTRendererLayer **) (intptr_t) handle);

    if (thiz && data && length)
    {
        NSAutoreleasePool *autoreleasePool = [[NSAutoreleasePool alloc] init];
        CGLContextObj glContext = thiz->_glContext;

        if (glContext && (kCGLNoError == CGLLockContext(glContext)))
        {
            GLuint texture = thiz->_texture;

            CGLSetCurrentContext(glContext);

            if (texture
                    && ((width != thiz->_width) || (height != thiz->_height)))
            {
                glDeleteTextures(1, &texture);
                thiz->_texture = texture = 0;
            }
            if (texture)
            {
                glBindTexture(JAWT_RENDERER_TEXTURE, texture);
                glTexSubImage2D(
                    JAWT_RENDERER_TEXTURE,
                    0,
                    0, 0, width, height,
                    JAWT_RENDERER_TEXTURE_FORMAT,
                    JAWT_RENDERER_TEXTURE_TYPE,
                    data);
            }
            else
            {
                glGenTextures(1, &texture);
                thiz->_texture = texture;

                glBindTexture(JAWT_RENDERER_TEXTURE, texture);
                glTexParameterf(
                    JAWT_RENDERER_TEXTURE,
                    GL_TEXTURE_PRIORITY,
                    1.0);
                glTexParameteri(
                    JAWT_RENDERER_TEXTURE,
                    GL_TEXTURE_WRAP_S,
                    GL_CLAMP_TO_EDGE);
                glTexParameteri(
                    JAWT_RENDERER_TEXTURE,
                    GL_TEXTURE_WRAP_T,
                    GL_CLAMP_TO_EDGE);
                glTexParameteri(
                    JAWT_RENDERER_TEXTURE,
                    GL_TEXTURE_MAG_FILTER,
                    GL_LINEAR);
                glTexParameteri(
                    JAWT_RENDERER_TEXTURE,
                    GL_TEXTURE_MIN_FILTER,
                    GL_LINEAR);

                glTexEnvf(GL_TEXTURE_ENV, GL_TEXTURE_ENV_MODE, GL_MODULATE);

                glTexParameteri(
                    JAWT_RENDERER_TEXTURE,
                    GL_TEXTURE_STORAGE_HINT_APPLE,
                    GL_STORAGE_SHARED_APPLE);

                glTexImage2D(
                    JAWT_RENDERER_TEXTURE,
                    0,
                    4,
                    width, height,
                    0,
                    JAWT_RENDERER_TEXTURE_FORMAT,
                    JAWT_RENDERER_TEXTURE_TYPE,
                    data);
            }
            thiz->_width = width;
            thiz->_height = height;

            CGLUnlockContext(glContext);
        }

        [autoreleasePool release];
    }

    return JNI_TRUE;
}

jstring
JAWTRenderer_sysctlbyname(JNIEnv *env, jstring name)
{
    const char *_name;
    jstring value = NULL;

    _name = (*env)->GetStringUTFChars(env, name, NULL);
    if (_name)
    {
        size_t valueLength;
        char *_value;

        if ((0 == sysctlbyname(_name, NULL, &valueLength, NULL, 0))
                && valueLength)
        {
            _value = malloc(sizeof(char) * (valueLength + 1));
            if (_value)
            {
                if ((0 == sysctlbyname(_name, _value, &valueLength, NULL, 0))
                        && valueLength)
                    _value[valueLength] = 0;
                else
                {
                    free(_value);
                    _value = NULL;
                }
            }
        }
        else
            _value = NULL;
        (*env)->ReleaseStringUTFChars(env, name, _name);

        if (_value)
        {
            value = (*env)->NewStringUTF(env, _value);
            free(_value);
        }
    }
    return value;
}

@implementation JAWTRendererLayer
- (CGLContextObj)copyCGLContextForPixelFormat:(CGLPixelFormatObj)pixelFormat
{
    CGLContextObj glContext = CGLRetainContext(_glContext);

    /*
     * Perform any one-time configuration/preparation of the newly-initialized
     * OpenGL context which this CAOpenGLLayer will be drawing into
     * (in the future).
     */
    if (glContext && (kCGLNoError == CGLLockContext(glContext)))
    {
        GLint param;

        CGLSetCurrentContext(glContext);

        param = 1;
        CGLSetParameter(glContext, kCGLCPSurfaceOpacity, &param);
        param = 0;
        CGLSetParameter(glContext, kCGLCPSwapInterval, &param);

        glDisable(GL_ALPHA_TEST);
        glDisable(GL_BLEND);
        glDisable(GL_CULL_FACE);
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_DITHER);
        glDisable(GL_LIGHTING);
        glDisable(GL_SCISSOR_TEST);
        glColorMask(GL_TRUE, GL_TRUE, GL_TRUE, GL_TRUE);
        glDepthMask(GL_FALSE);
        glStencilMask(0);
        glHint(GL_TRANSFORM_HINT_APPLE, GL_FASTEST);
        glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT);

        CGLUnlockContext(glContext);
    }

    return glContext;
}

- (CGLPixelFormatObj)copyCGLPixelFormatForDisplayMask:(uint32_t)mask
{
    return CGLRetainPixelFormat(_pixelFormat);
}

- (void)dealloc
{
    if (_glContext)
        CGLReleaseContext(_glContext);
    if (_pixelFormat)
        CGLReleasePixelFormat(_pixelFormat);

    [super dealloc];
}

- (void)drawInCGLContext:(CGLContextObj)glContext
             pixelFormat:(CGLPixelFormatObj)pixelFormat
            forLayerTime:(CFTimeInterval)timeInterval
             displayTime:(const CVTimeStamp *)timeStamp
{
    if (kCGLNoError == CGLLockContext(glContext))
    {
        glClear(GL_COLOR_BUFFER_BIT);

        if (_texture)
        {
            /*
             * It may be a misunderstanding of OpenGL context sharing but
             * JAWT_RENDERER_TEXTURE does not seem to work in glContext unless
             * it is explicitly bound to texture while glContext is current.
             */
            glBindTexture(JAWT_RENDERER_TEXTURE, _texture);
            glEnable(JAWT_RENDERER_TEXTURE);
            glBegin(GL_QUADS);
            glTexCoord2f(0, 0);
            glVertex2f(-1.0, 1.0);
            glTexCoord2f(_width, 0);
            glVertex2f(1.0, 1.0);
            glTexCoord2f(_width, _height);
            glVertex2f(1.0, -1.0);
            glTexCoord2f(0, _height);
            glVertex2f(-1.0, -1.0);
            glEnd();
            glDisable(JAWT_RENDERER_TEXTURE);
        }

        CGLUnlockContext(glContext);
    }

    [super drawInCGLContext:glContext
                pixelFormat:pixelFormat
               forLayerTime:timeInterval
                displayTime:timeStamp];
}

- (id)init
{
    if ((self = [super init]))
    {
        CGLPixelFormatAttribute attribs[]
            = { kCGLPFAAccelerated, kCGLPFAWindow, 0 };
        GLint npix;

        _glContext = NULL;
        _height = 0;
        _pixelFormat = NULL;
        _texture = 0;
        _width = 0;

        if ((kCGLNoError == CGLChoosePixelFormat(attribs, &_pixelFormat, &npix))
                && (kCGLNoError
                        == CGLCreateContext(_pixelFormat, NULL, &_glContext)))
        {
            self.asynchronous = YES;
            /*
             * The AWT Canvas that corresponds to this CAOpenGLLayer will ensure
             * that the latter will be displayed on bounds changes.
             */
            self.needsDisplayOnBoundsChange = NO;
        }
        else
        {
            [self release];
            self = nil;
        }
    }
    return self;
}
@end /* JAWTRendererLayer */
