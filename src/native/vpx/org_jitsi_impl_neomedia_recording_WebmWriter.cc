#include <assert.h>
#include <jni.h>
#include <limits.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <new>

#include "vpx/vpx_codec.h"
#include "vpx/vpx_encoder.h"

extern "C" {
#include "libmkv/EbmlWriter.h"
#include "libmkv/EbmlIDs.h"
}
#define LITERALU64(n) n##LLU

#ifdef NDEBUG
# define printf(fmt, ...)
#else
# ifdef ANDROID_NDK
#  include <android/log.h>
#  define printf(fmt, ...) \
   __android_log_print(ANDROID_LOG_DEBUG, "LIBVPX_WEBM", fmt, ##__VA_ARGS__)
# else
#  define printf(fmt, ...) \
   printf(fmt "\n", ##__VA_ARGS__)
# endif
#endif

#define FUNC(RETURN_TYPE, NAME, ...) \
  extern "C" { \
  JNIEXPORT RETURN_TYPE Java_org_jitsi_impl_neomedia_recording_WebmWriter_ ## NAME \
                      (JNIEnv * env, jobject thiz, ##__VA_ARGS__);\
  } \
  JNIEXPORT RETURN_TYPE Java_org_jitsi_impl_neomedia_recording_WebmWriter_ ## NAME \
                      (JNIEnv * env, jobject thiz, ##__VA_ARGS__)\

#define STRING_RETURN(JNI_NAME, LIBVPX_NAME) \
  FUNC(jstring, JNI_NAME) { \
    printf(#JNI_NAME); \
    return env->NewStringUTF(LIBVPX_NAME()); \
  }

/* Stereo 3D packed frame format */
typedef enum stereo_format {
  STEREO_FORMAT_MONO       = 0,
  STEREO_FORMAT_LEFT_RIGHT = 1,
  STEREO_FORMAT_BOTTOM_TOP = 2,
  STEREO_FORMAT_TOP_BOTTOM = 3,
  STEREO_FORMAT_RIGHT_LEFT = 11
} stereo_format_t;

typedef off_t EbmlLoc;

struct cue_entry {
  unsigned int time;
  uint64_t     loc;
};

extern "C" {
struct EbmlGlobal {
  FILE *stream;
  int64_t last_pts_ms;

  /* These pointers are to the start of an element */
  off_t    position_reference;
  off_t    seek_info_pos;
  off_t    segment_info_pos;
  off_t    track_pos;
  off_t    cue_pos;
  off_t    cluster_pos;

  /* This pointer is to a specific element to be serialized */
  off_t    track_id_pos;

  /* These pointers are to the size field of the element */
  EbmlLoc  startSegment;
  EbmlLoc  startCluster;

  uint32_t cluster_timecode;
  int      cluster_open;

  struct cue_entry *cue_list;
  unsigned int      cues;
};
}

FUNC(jlong, allocCfg) {
  EbmlGlobal *glob = new (std::nothrow) EbmlGlobal;

  if (glob) {
    memset(glob, 0, sizeof(*glob));
    glob->last_pts_ms = -1;
  }

  return (intptr_t)glob;
}

FUNC(void, freeCfg, jlong jglob) {
  const EbmlGlobal *glob = reinterpret_cast<EbmlGlobal*>(jglob);

  if (glob != NULL) {
    if (glob->stream)
      fclose(glob->stream);

    free(glob->cue_list);

    delete glob;

    glob = 0;
  }
}

FUNC(jboolean, openFile, jlong jglob, jstring fileName) {
  EbmlGlobal *glob = reinterpret_cast<EbmlGlobal*>(jglob);
  const char *mfile = env->GetStringUTFChars(fileName, 0);

  glob->stream = fopen(mfile, "wb");

  env->ReleaseStringUTFChars(fileName, mfile);

  if (!glob->stream)
    return JNI_TRUE;

  return JNI_FALSE;
}

extern "C" {
void Ebml_Write(EbmlGlobal *glob, const void *buffer_in, unsigned long len) {
  if (fwrite(buffer_in, 1, len, glob->stream))
    ;
}
}

#define WRITE_BUFFER(s) \
  for (i = len - 1; i >= 0; i--) { \
    x = *(const s *)buffer_in >> (i * CHAR_BIT); \
    Ebml_Write(glob, &x, 1ULL); \
  }
extern "C" {
void Ebml_Serialize(EbmlGlobal *glob, const void *buffer_in,
                    int buffer_size, unsigned long len) {
  char x;
  int i;

  /* buffer_size:
    * 1 - int8_t;
    * 2 - int16_t;
    * 3 - int32_t;
    * 4 - int64_t;
    */
  switch (buffer_size) {
    case 1:
      WRITE_BUFFER(int8_t)
      break;
    case 2:
      WRITE_BUFFER(int16_t)
      break;
    case 4:
      WRITE_BUFFER(int32_t)
      break;
    case 8:
      WRITE_BUFFER(int64_t)
      break;
    default:
      break;
  }
}
}
#undef WRITE_BUFFER

/* Need a fixed size serializer for the track ID. libmkv provides a 64 bit
 * one, but not a 32 bit one.
 */
static void Ebml_SerializeUnsigned32(EbmlGlobal *glob,
                                     uint64_t class_id, uint64_t ui) {
  unsigned char sizeSerialized = 4 | 0x80;
  Ebml_WriteID(glob, class_id);
  Ebml_Serialize(glob, &sizeSerialized, sizeof(sizeSerialized), 1ULL);
  Ebml_Serialize(glob, &ui, sizeof(ui), 4ULL);
}

static void Ebml_StartSubElement(EbmlGlobal *glob, EbmlLoc *ebmlLoc,
                                 uint64_t class_id) {
  // todo this is always taking 8 bytes, this may need later optimization
  // this is a key that says length unknown
  uint64_t unknownLen =  LITERALU64(0x01FFFFFFFFFFFFFF);

  Ebml_WriteID(glob, class_id);
  *ebmlLoc = ftello(glob->stream);
  Ebml_Serialize(glob, &unknownLen, sizeof(unknownLen), 8ULL);
}

static void Ebml_EndSubElement(EbmlGlobal *glob, EbmlLoc *ebmlLoc) {
  off_t pos;
  uint64_t size;

  /* Save the current stream pointer */
  pos = ftello(glob->stream);

  /* Calculate the size of this element */
  size = pos - *ebmlLoc - 8;
  size |=  LITERALU64(0x0100000000000000);

  /* Seek back to the beginning of the element and write the new size */
  fseeko(glob->stream, *ebmlLoc, SEEK_SET);
  Ebml_Serialize(glob, &size, sizeof(size), 8ULL);

  /* Reset the stream pointer */
  fseeko(glob->stream, pos, SEEK_SET);
}

static void write_webm_seek_element(EbmlGlobal *ebml, uint64_t id, off_t pos) {
  uint64_t offset = pos - ebml->position_reference;
  EbmlLoc start;
  Ebml_StartSubElement(ebml, &start, Seek);
  Ebml_SerializeBinary(ebml, SeekID, id);
  Ebml_SerializeUnsigned64(ebml, SeekPosition, offset);
  Ebml_EndSubElement(ebml, &start);
}

static void write_webm_seek_info(EbmlGlobal *ebml) {
  off_t pos;

  /* Save the current stream pointer */
  pos = ftello(ebml->stream);

  if (ebml->seek_info_pos)
    fseeko(ebml->stream, ebml->seek_info_pos, SEEK_SET);
  else
    ebml->seek_info_pos = pos;

  {
    EbmlLoc start;

    Ebml_StartSubElement(ebml, &start, SeekHead);
    write_webm_seek_element(ebml, Tracks, ebml->track_pos);
    write_webm_seek_element(ebml, Cues,   ebml->cue_pos);
    write_webm_seek_element(ebml, Info,   ebml->segment_info_pos);
    Ebml_EndSubElement(ebml, &start);
  }
  {
    // segment info
    EbmlLoc startInfo;
    uint64_t duration;
    uint64_t frame_time = 45; //approx. the duration of a single frame (in ms).
    char version_string[64];

    /* Assemble version string */
    strcpy(version_string, "vpxenc ");
    strncat(version_string,
            vpx_codec_version_str(),
            sizeof(version_string) - 1 - strlen(version_string));

    if (ebml->last_pts_ms > 0)
      duration = ebml->last_pts_ms + frame_time;
    else
      duration = 0;

    ebml->segment_info_pos = ftello(ebml->stream);
    Ebml_StartSubElement(ebml, &startInfo, Info);
    Ebml_SerializeUnsigned(ebml, TimecodeScale, 1000000);
    Ebml_SerializeFloat(ebml, Segment_Duration, duration);
    Ebml_SerializeString(ebml, MuxingApp, version_string);
    Ebml_SerializeString(ebml, WritingApp, version_string);
    Ebml_EndSubElement(ebml, &startInfo);
  }
}

FUNC(void, writeWebmFileHeader, jlong jglob,
                                            jint width, jint height) {
  EbmlGlobal *glob = reinterpret_cast<EbmlGlobal*>(jglob);
  stereo_format_t stereo_fmt = STEREO_FORMAT_MONO;

  EbmlLoc start;
  Ebml_StartSubElement(glob, &start, EBML);
  Ebml_SerializeUnsigned(glob, EBMLVersion, 1);
  Ebml_SerializeUnsigned(glob, EBMLReadVersion, 1);  // EBML Read Version
  Ebml_SerializeUnsigned(glob, EBMLMaxIDLength, 4);  // EBML Max ID Length
  Ebml_SerializeUnsigned(glob, EBMLMaxSizeLength, 8);  // EBML Max Size Length
  Ebml_SerializeString(glob, DocType, "webm");  // Doc Type
  Ebml_SerializeUnsigned(glob, DocTypeVersion, 2);  // Doc Type Version
  Ebml_SerializeUnsigned(glob, DocTypeReadVersion, 2);  // Doc Type Read Version
  Ebml_EndSubElement(glob, &start);

  {
    Ebml_StartSubElement(glob, &glob->startSegment, Segment);  // segment
    glob->position_reference = ftello(glob->stream);
    write_webm_seek_info(glob);

    {
      EbmlLoc trackStart;
      glob->track_pos = ftello(glob->stream);
      Ebml_StartSubElement(glob, &trackStart, Tracks);
      {
        unsigned int trackNumber = 1;
        uint64_t     trackID = 0;

        EbmlLoc start;
        Ebml_StartSubElement(glob, &start, TrackEntry);
        Ebml_SerializeUnsigned(glob, TrackNumber, trackNumber);
        glob->track_id_pos = ftello(glob->stream);
        Ebml_SerializeUnsigned32(glob, TrackUID, trackID);
        Ebml_SerializeUnsigned(glob, TrackType, 1);  // video is always 1
        Ebml_SerializeString(glob, CodecID, "V_VP8");
        {
          unsigned int pixelWidth = width;
          unsigned int pixelHeight = height;

          EbmlLoc videoStart;
          Ebml_StartSubElement(glob, &videoStart, Video);
          Ebml_SerializeUnsigned(glob, PixelWidth, pixelWidth);
          Ebml_SerializeUnsigned(glob, PixelHeight, pixelHeight);
          Ebml_SerializeUnsigned(glob, StereoMode, stereo_fmt);
          //Ebml_SerializeFloat(glob, FrameRate, frameRate);
          Ebml_EndSubElement(glob, &videoStart);  // Video
        }
        Ebml_EndSubElement(glob, &start);  // Track Entry
      }
      Ebml_EndSubElement(glob, &trackStart);
    }
    // segment element is open
  }
}

FUNC(void, writeWebmBlock, jlong jglob, jobject jfd) {
  EbmlGlobal *glob = reinterpret_cast<EbmlGlobal*>(jglob);

  jclass frameDescriptor = env->FindClass("org/jitsi/impl/neomedia/recording/WebmWriter$FrameDescriptor");
  assert(frameDescriptor != NULL);

  jfieldID bufferId = env->GetFieldID(frameDescriptor, "buffer", "[B");
  assert(bufferId != NULL);

  jfieldID offsetId = env->GetFieldID(frameDescriptor, "offset", "I");
  assert(offsetId != NULL);

  jfieldID lengthId = env->GetFieldID(frameDescriptor, "length", "J");
  assert(lengthId != NULL);

  jfieldID flagsId = env->GetFieldID(frameDescriptor, "flags", "I");
  assert(flagsId != NULL);

  jfieldID ptsId = env->GetFieldID(frameDescriptor, "pts", "J");
  assert(ptsId != NULL);

  jobject jba = env->GetObjectField(jfd, bufferId);
  assert(jba != NULL);

  jint offset = env->GetIntField(jfd, offsetId);

  jint frameFlags = env->GetIntField(jfd, flagsId);

  uint64_t       block_length;
  unsigned char  track_number;
  uint16_t       block_timecode = 0;
  unsigned char  flags;
  int64_t        pts_ms;
  int            start_cluster = 0, is_keyframe;

  /* Calculate the PTS of this frame in milliseconds */
  pts_ms = env->GetLongField(jfd, ptsId);
  if (pts_ms <= glob->last_pts_ms)
    pts_ms = glob->last_pts_ms + 1;
  glob->last_pts_ms = pts_ms;

  /* Calculate the relative time of this block */
  if (pts_ms - glob->cluster_timecode > SHRT_MAX)
    start_cluster = 1;
  else
    block_timecode = pts_ms - glob->cluster_timecode;

  is_keyframe = (frameFlags & VPX_FRAME_IS_KEY);
  if (start_cluster || is_keyframe) {
    if (glob->cluster_open)
      Ebml_EndSubElement(glob, &glob->startCluster);

    /* Open the new cluster */
    block_timecode = 0;
    glob->cluster_open = 1;
    glob->cluster_timecode = pts_ms;
    glob->cluster_pos = ftello(glob->stream);
    Ebml_StartSubElement(glob, &glob->startCluster, Cluster);  // cluster
    Ebml_SerializeUnsigned(glob, Timecode, glob->cluster_timecode);

    /* Save a cue point if this is a keyframe. */
    if (is_keyframe) {
      struct cue_entry *cue, *new_cue_list;

      new_cue_list = reinterpret_cast<cue_entry*>(realloc(glob->cue_list,
                     (glob->cues+1) * sizeof(struct cue_entry)));
      if (new_cue_list) {
        glob->cue_list = new_cue_list;
      } else {
        // TODO(frkoenig) : Handle this error better/correctly
        fprintf(stderr, "\nFailed to realloc cue list.\n");
        exit(EXIT_FAILURE);
      }

      cue = &glob->cue_list[glob->cues];
      cue->time = glob->cluster_timecode;
      cue->loc = glob->cluster_pos;
      glob->cues++;
    }
  }

  /* Write the Simple Block */
  Ebml_WriteID(glob, SimpleBlock);

  jlong frameSz =  env->GetLongField(jfd, lengthId);

  block_length = frameSz + 4;
  block_length |= 0x10000000;
  Ebml_Serialize(glob, &block_length, sizeof(block_length), 4ULL);

  track_number = 1;
  track_number |= 0x80;
  Ebml_Write(glob, &track_number, 1ULL);

  Ebml_Serialize(glob, &block_timecode, sizeof(block_timecode), 2ULL);

  flags = 0;
  if (is_keyframe)
    flags |= 0x80;
  if (frameFlags & VPX_FRAME_IS_INVISIBLE)
    flags |= 0x08;
  Ebml_Write(glob, &flags, 1ULL);

  jbyte *frameBuf = env->GetByteArrayElements((jbyteArray)jba, 0);

  Ebml_Write(glob, frameBuf + offset, static_cast<uint64_t>(frameSz));

  env->ReleaseByteArrayElements((jbyteArray)jba, frameBuf, 0);
}

FUNC(void, writeWebmFileFooter, jlong jglob, jlong hash) {
  EbmlGlobal *glob = reinterpret_cast<EbmlGlobal*>(jglob);

  //1
  if (glob->cluster_open)
    Ebml_EndSubElement(glob, &glob->startCluster);

  {
    EbmlLoc start;
    unsigned int i;

    glob->cue_pos = ftello(glob->stream);
    Ebml_StartSubElement(glob, &start, Cues);
  //2
    for (i = 0; i < glob->cues; i++) {
      struct cue_entry *cue = &glob->cue_list[i];
      EbmlLoc start;

      Ebml_StartSubElement(glob, &start, CuePoint);
      {
        EbmlLoc start;

        Ebml_SerializeUnsigned(glob, CueTime, cue->time);

        Ebml_StartSubElement(glob, &start, CueTrackPositions);
        Ebml_SerializeUnsigned(glob, CueTrack, 1);
        Ebml_SerializeUnsigned64(glob, CueClusterPosition,
                                  cue->loc - glob->position_reference);
        // Ebml_SerializeUnsigned(glob, CueBlockNumber, cue->blockNumber);
        Ebml_EndSubElement(glob, &start);
      }
      Ebml_EndSubElement(glob, &start);
    }
    Ebml_EndSubElement(glob, &start);
  }

  Ebml_EndSubElement(glob, &glob->startSegment);

  /* Patch up the seek info block */
  write_webm_seek_info(glob);

  /* Patch up the track id */
  fseeko(glob->stream, glob->track_id_pos, SEEK_SET);
  Ebml_SerializeUnsigned32(glob, TrackUID, hash);

  fseeko(glob->stream, 0, SEEK_END);
}
