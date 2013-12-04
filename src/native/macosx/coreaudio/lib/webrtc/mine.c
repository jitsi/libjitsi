#include "webrtc/modules/audio_processing/include/audio_processing.h"
#include "webrtc/modules/interface/module_common_types.h"

#include <math.h>

// README webrtc/modules/audio_processing/include/audio_processing.h

using namespace webrtc;

void gen_sin(int16_t * buff, int length);

int pos = 0;

int main(int arc, char **argv)
{
    int id = 0;

    AudioFrame * render_frame = new AudioFrame();
    AudioFrame * capture_frame = new AudioFrame();

    int nb_channels = 1;
    int nb_ms_sample_length = 10; //10 ms
    int sample_rate = 32000;
    //int sample_rate = 44100;
    int samples_per_channel = (nb_ms_sample_length  * sample_rate) / 1000;
    int data_length = samples_per_channel * nb_channels;
    int nb_decal = (data_length * 7) / nb_ms_sample_length;
    int16_t render_data[data_length];
    int16_t capture_data[data_length];
    //memset(render_data, 0, data_length);
    //memset(capture_data, 0, data_length);


    int analog_level = 10;
    //int has_voice = 0;

    //memcpy(render_data, capture_data, data_length * sizeof(int16_t));

    /*gen_sin(render_data, data_length);

    for(int i = 0; i < nb_decal; ++i)
    {
        capture_data[i] = render_data[data_length - nb_decal + i];
    }
    for(int i = nb_decal; i < data_length; ++i)
    {
        capture_data[i] = render_data[i - nb_decal];
    }*/

    AudioProcessing * audioProcessing = AudioProcessing::Create(id);
    
    audioProcessing->set_sample_rate_hz(sample_rate);

    // // Mono capture and stereo render.
    audioProcessing->set_num_channels(1, 1);
    audioProcessing->set_num_reverse_channels(1);
    //audioProcessing->set_num_reverse_channels(2);
    
        audioProcessing->high_pass_filter()->Enable(true);

        audioProcessing->echo_cancellation()->set_stream_drift_samples(100);
    
    audioProcessing->echo_cancellation()->enable_drift_compensation(false);
    audioProcessing->echo_cancellation()->Enable(true);

        //audioProcessing->noise_reduction()->set_level(kHighSuppression);
        //audioProcessing->noise_reduction()->Enable(true);
    
        audioProcessing->gain_control()->set_analog_level_limits(0, 255);
        //audioProcessing->gain_control()->set_mode(kAdaptiveAnalog);
        audioProcessing->gain_control()->Enable(true);
    
        audioProcessing->voice_detection()->Enable(true);
    
    // Start a voice call...
    
    for(int j = 0; j < 5; ++j)
    {
        for(int i = 0; i < nb_decal; ++i)
        {
            capture_data[i] = render_data[data_length - nb_decal + i];
        }
        gen_sin(render_data, data_length);
        for(int i = nb_decal; i < data_length; ++i)
        {
            capture_data[i] = render_data[i - nb_decal];
        }

        render_frame->UpdateFrame(
                -1, // id
                -1,
                //j * nb_ms_sample_length, // timestamp
                render_data,
                samples_per_channel,
                sample_rate,
                AudioFrame::kNormalSpeech,
                AudioFrame::kVadActive,
                nb_channels);
        capture_frame->UpdateFrame(
                -1, // id
                -1,
                //j * nb_ms_sample_length, // timestamp
                capture_data,
                samples_per_channel,
                sample_rate,
                AudioFrame::kNormalSpeech,
                AudioFrame::kVadActive,
                nb_channels);



    // ... Render frame arrives bound for the audio HAL ...
    audioProcessing->AnalyzeReverseStream(render_frame);
    
    // // ... Capture frame arrives from the audio HAL ...
    // // Call required set_stream_ functions.
        audioProcessing->set_stream_delay_ms(100);
        //audioProcessing->set_stream_delay_ms(delay_ms);
        audioProcessing->gain_control()->set_stream_analog_level(analog_level);

    audioProcessing->ProcessStream(capture_frame);
    
    // // Call required stream_ functions.
    analog_level = audioProcessing->gain_control()->stream_analog_level();
    //has_voice = audioProcessing->stream_has_voice();
    
    // Repeate render and capture processing for the duration of the call...
    // Start a new call...
    //audioProcessing->Initialize();



    for(int i = 0; i < nb_decal; ++i)
    {
        if(capture_data[i] != render_data[data_length - nb_decal + i])
        {
            fprintf(stderr, "render/capture[%d]: %d/%d\n",
                    i,
                    render_data[i],
                    capture_data[i]);
            fflush(stderr);
        }
    }
    for(int i = nb_decal; i < data_length; ++i)
    {
        if(capture_data[i] != render_data[i - nb_decal])
        {
            fprintf(stderr, "render/capture[%d]: %d/%d\n",
                    i,
                    render_data[i],
                    capture_data[i]);
            fflush(stderr);
        }
    }
    fprintf(stderr, "analog_level: %d\n", analog_level);
    //fprintf(stderr, "has_voice: %d\n", has_voice);
    fprintf(stderr, "\n\n\n");
    fflush(stderr);





    /*for(int i = 0; i < data_length; ++i)
    {
        if(render_data[i] != capture_data[i])
        {
            fprintf(stderr, "render/capture[%d]: %d/%d\n",
                    i,
                    render_data[i],
                    capture_data[i]);
            fflush(stderr);
        }
    }
    fprintf(stderr, "\n\n\n");
    fflush(stderr);*/
    }
    
    // Close the application...
    delete audioProcessing;
    delete render_frame;
    delete capture_frame;

    return 0;
}


void gen_sin(int16_t * buff, int length)
{
    int nb_channels = 1;
    //int nb_ms_sample_length = 10; //10 ms
    int sample_rate = 32000;
    double freq = 440.0;

    //int kHz = sample_rate / 1000;
    //int nbToneSamples = kHz * nb_ms_sample_length;

    /*
     * The leading nbInterDigitSamples should be zeroes. They are because we
     * have just allocated the array.
     */
    for(int sampleNumber = 0;
            sampleNumber < length; 
            sampleNumber += nb_channels)
    {
        for(int i = 0; i < nb_channels; ++i)
        {
            buff[sampleNumber + i]
                = isinf(pos + sampleNumber * 2.0 * M_PI * freq / sample_rate) *
                3;
        }
    }
    pos += length / nb_channels;
}
