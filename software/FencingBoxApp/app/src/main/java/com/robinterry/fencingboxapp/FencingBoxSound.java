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
    private int sampleLimit;
    private int durationMs;
    private Thread soundThread = null;
    private boolean soundDelay = false;
    private boolean soundEnable = false;
    private AudioManager audioMgr;

    public FencingBoxSound(int frequencyInHz,
                           int sampleRateInHz,
                           waveformType waveform,
                           int level,
                           Context context) {
        if (level > 32767) {
            throw new RuntimeException("level out of range (0 < L < 32767)");
        } else if (frequencyInHz >= (sampleRateInHz / 2)) {
            throw new RuntimeException("frequency must be less than half of the sampling frequency");
        }
        this.level = level;
        this.waveform = waveform;
        this.sampleLimit = 0;

        // Work out the best size of buffer
        int minBufferSize = AudioTrack.getMinBufferSize(sampleRateInHz,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        int maxSamplesPerSec = sampleRateInHz / frequencyInHz;
        int bufferSize = (minBufferSize / maxSamplesPerSec) * (maxSamplesPerSec + 1);

        createTrack(context, maxSamplesPerSec, sampleRateInHz, bufferSize);
    }

    public FencingBoxSound(int frequencyInHz,
                           int sampleRateInHz,
                           waveformType waveform,
                           int level,
                           int durationMs,
                           Context context) {
        if (level > 32767) {
            throw new RuntimeException("level out of range (0 < L < 32767)");
        } else if (frequencyInHz >= (sampleRateInHz / 2)) {
            throw new RuntimeException("frequency must be less than half of the sampling frequency");
        }
        this.level = level;
        this.waveform = waveform;
        this.durationMs = durationMs;

        // Work out the best size of buffer
        int minBufferSize = AudioTrack.getMinBufferSize(sampleRateInHz,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        int maxSamplesPerCycle = sampleRateInHz / frequencyInHz;

        // This is a time-limited sound, so work out the total number of samples
        this.sampleLimit = (int) ((sampleRateInHz * durationMs) / 1000);

        int bufferSize;
        if (this.sampleLimit < minBufferSize) {
            bufferSize = minBufferSize;
        } else {
            bufferSize = this.sampleLimit;
        }
        createTrack(context, maxSamplesPerCycle, sampleRateInHz, bufferSize);
    }

    private void createTrack(Context context,
                             int maxSamplesPerCycle,
                             int sampleRateInHz,
                             int bufferSize) {
        // Allocate the buffer
        buffer = new short[bufferSize];

        /* Work out the step in the angular velocity (0 to 2*PI) -
           multiply this by the sample index to get the angle */
        angleStep = (2*Math.PI)/(double) maxSamplesPerCycle;

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
    }

    public void enable() {
        soundEnable = true;
    }

    public void disable() {
        soundEnable = false;
    }

    public boolean isMuted() {
        return (audioMgr.getStreamVolume(AudioManager.STREAM_MUSIC) == 0);
    }

    private synchronized void generateTone() {
        int state = audioTrack.getState();
        if (state != AudioTrack.STATE_INITIALIZED) {
            audioTrack.release();
            throw new RuntimeException("Audio track not initialised");
        }
        if (audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
            audioTrack.flush();
            audioTrack.stop();
        }
        double angle = -Math.PI;
        double sample = 0.0;

        // Generate the tone until the thread is killed
        audioTrack.play();
        while (soundThread != null) {
            // The range of 'sample' for all waveforms is -1.0 <= sample <= 1.0 */
            for (int i = 0; i < buffer.length; i++) {
                // Is there a time limit to this sound? If so, flush the data out and stop
                if (sampleLimit > 0 && i > sampleLimit) {
                    audioTrack.write(buffer, 0, i, AudioTrack.WRITE_BLOCKING);
                    // Wait for the sound to play out before stopping
                    try {
                        Thread.sleep(durationMs);
                    } catch (InterruptedException e) {}
                    audioTrack.stop();
                    return;
                } else {
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
                }

                // Increment and possibly wrap the angle
                angle += angleStep;
                if (angle > Math.PI) {
                    angle -= (2.0 * Math.PI);
                }
            }
            audioTrack.write(buffer, 0, buffer.length, AudioTrack.WRITE_BLOCKING);
        }

        // Thread is being stopped - stop the tone generation
        audioTrack.stop();
    }

    public void run() {
        generateTone();
        soundThread = null;
    }

    public void soundOn() {
        // Sound the tone
        if (soundThread == null && soundEnable) {
            soundThread = new Thread(this, "FencingBoxSound");
            soundThread.start();
        }
    }

    public void soundOn(int periodMillis) {
        // Sound the tone for a period
        if (soundThread == null && soundEnable) {
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

