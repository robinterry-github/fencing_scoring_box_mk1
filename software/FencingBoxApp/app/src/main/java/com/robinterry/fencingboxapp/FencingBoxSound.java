package com.robinterry.fencingboxapp;

import android.media.AudioTrack;
import android.media.AudioFormat;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.content.Context;
import android.util.Log;
import java.lang.Thread;
import java.lang.Math;

public class FencingBoxSound implements Runnable {
    public enum waveformType { SINE, SQUARE, SAWTOOTH }

    private int level;
    private waveformType waveform;
    private short[] buffer;
    private AudioTrack audioTrack;
    private double angleStep;
    private Thread soundThread = null;
    private static final String TAG = "FencingBoxSound";
    private boolean soundDelay = false;
    private boolean soundEnable = false;
    private AudioManager audioMgr;

    public FencingBoxSound(int frequencyInHz,
                           int sampleRateInHz,
                           waveformType waveform,
                           int level,
                           Context context) {
        Log.d(TAG, "constructor start");
        Log.d(TAG, "frequency " + frequencyInHz +
                " sample rate " + sampleRateInHz +
                " waveform " + waveform +
                " level " + level);
        if (level > 32767) {
            throw new RuntimeException("level out of range (0 < L < 32767)");
        } else if (frequencyInHz >= (sampleRateInHz/2)) {
            throw new RuntimeException("frequency must be less than half of the sampling frequency");
        }
        this.level = level;
        this.waveform = waveform;

        // Work out the best size of buffer
        int minBufferSize = AudioTrack.getMinBufferSize(sampleRateInHz,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT);
        int maxSamples = sampleRateInHz/frequencyInHz;
        int bufferSize = (minBufferSize/maxSamples)*(maxSamples+1);

        // Allocate the buffer
        buffer = new short[bufferSize];

        /* Work out the step in the angular velocity (0 to 2*PI) -
           multiply this by the sample index to get the angle */
        angleStep = (2*Math.PI)/(double) maxSamples;

        // Create the AudioTrack instance
        audioTrack = new AudioTrack.Builder().
                setAudioAttributes(new AudioAttributes.Builder().
                        setUsage(AudioAttributes.USAGE_MEDIA).
                        setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).
                        build()).
                setAudioFormat(new AudioFormat.Builder().
                        setSampleRate(sampleRateInHz).
                        setEncoding(AudioFormat.ENCODING_PCM_16BIT).
                        setChannelMask(AudioFormat.CHANNEL_OUT_MONO).
                        build()).
                setBufferSizeInBytes(bufferSize).
                build();

        if (audioTrack == null) {
            throw new RuntimeException("Unable to create an audio track");
        }

        // Get the AudioManager service for checking the music volume
        audioMgr = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

        Log.d(TAG, "constructor end");
    }

    public void enable() {
        soundEnable = true;
    }

    public void disable() {
        soundEnable = false;
    }

    public boolean isMuted() {
        int volume = audioMgr.getStreamVolume(AudioManager.STREAM_MUSIC);

        return (volume == 0);
    }

    private void generateTone() {
        Log.d(TAG, "generating tone");
        int state = audioTrack.getState();
        if (state != AudioTrack.STATE_INITIALIZED) {
            audioTrack.release();
            throw new RuntimeException("Audio track not initialised");
        }
        audioTrack.play();

        double angle = -Math.PI;
        double sample = 0.0;

        // Generate the tone until the thread is killed
        while (soundThread != null) {
            // The range of 'sample' for all waveforms is -1.0 <= sample <= 1.0 */
            for (int i = 0; i < buffer.length; i++) {
                switch (waveform) {
                    case SINE:
                        // Sine wave
                        sample = Math.sin(angle);
                        break;

                    case SQUARE:
                        // 50% duty cycle square wave
                        if (angle < 0) {
                            sample = -1.0;
                        } else {
                            sample = 1.0;
                        }
                        break;

                    case SAWTOOTH:
                        // Sawtooth wave
                        sample = (angle / Math.PI);
                        break;
                }
                buffer[i] = (short) (sample * level);

                // Increment and possibly wrap the angle
                angle += angleStep;
                if (angle > Math.PI) {
                    angle -= (2.0 * Math.PI);
                }
            }
            audioTrack.write(buffer, 0, buffer.length);
        }

        // Thread is being stopped - stop the tone generation
        audioTrack.stop();
    }

    public void run() {
        generateTone();
    }

    public void soundOn() {
        // Sound the tone
        if (soundThread == null && soundEnable) {
            Log.d(TAG, "sound on");
            soundThread = new Thread(this, "FencingBoxSound");
            soundThread.start();
        }
    }

    public void soundOn(int periodMillis) {
        // Sound the tone for a period
        if (soundThread == null && soundEnable) {
            Log.d(TAG, "sound on for " + periodMillis + "ms");
            Thread t = new Thread(() -> {
                try {
                    soundDelay = true;
                    soundOn();
                    Thread.sleep(periodMillis);
                    soundOff(true);
                    soundDelay = false;
                } catch (InterruptedException e) {}
            });
            t.start();
        }
    }

    public void soundOff() {
        soundOff(false);
    }

    public void soundOff(boolean force) {
        if (soundThread != null && (!soundDelay || force)) {
            Log.d(TAG, "sound off");
            Thread thread = soundThread;
            soundThread = null;

            // Try to kill the thread
            try {
                if (thread != null && thread.isAlive()) {
                    thread.join();
                }
            } catch (Exception e) {
            }
        }
    }
}

