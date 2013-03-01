/*
 * To change this template, choose Tools | Templates and open the template in the editor.
 */
package org.fpl.media;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Handler;
import dk.nordfalk.netradio.Log;

import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * @author j
 */
public class PcmAudioSink {

    private static final String TAG = "PcmAudioSink";
    Handler handler;
    Runnable runWhenPcmAudioSinkWrite;
    AudioTrack track;

    int bytesPerSecond;
    final int preferredBufferInSeconds = 9;
    final int maxBufferInSeconds = 40;
    LinkedBlockingQueue<byte[]> buffersInUse = new LinkedBlockingQueue<byte[]>();
    int bytesInBuffer = 0;
    ArrayList<SoftReference<byte[]>> buffersNotInUse = new ArrayList<SoftReference<byte[]>>();
    int result;

    public PcmAudioSink() {

        Log.d(TAG, "Init new PcmAudioSink");
        setSampleRateInHz(getSampleRateInHz());
        int channelConfig = AudioFormat.CHANNEL_CONFIGURATION_STEREO;
        int audioFormat = AudioFormat.ENCODING_PCM_16BIT;

        int minBufSize = AudioTrack.getMinBufferSize(getSampleRateInHz(), channelConfig, audioFormat);
        if (minBufSize <= 0)
            throw new InternalError("Buffer size error: " + minBufSize);

        int bufferSize = 176400; // minBufSize * 8
        // int bufferSize = 86016;
        track = new AudioTrack(AudioManager.STREAM_MUSIC, getSampleRateInHz(), channelConfig, audioFormat, bufferSize, AudioTrack.MODE_STREAM);

        bytesPerSecond = track.getChannelCount() * track.getSampleRate()
                * (track.getAudioFormat() == AudioFormat.ENCODING_PCM_16BIT ? 2 : 1);

        Log.d("X " + bytesPerSecond + "  " + track.getChannelCount() + "  " + track.getSampleRate() + "  " + track.getAudioFormat());
        Log.d("Manager", "Buffer size - min: " + minBufSize + "  - (" + minBufSize * 1000 / bytesPerSecond + " msecs)");
        Log.d("Manager", "Buffer size - act: " + bufferSize + "  - (" + bufferSize * 1000 / bytesPerSecond + " msecs)");

    }

    private void init() {
        Log.d(TAG, "Call PcmAudioSink Init");
        int channelConfig = AudioFormat.CHANNEL_CONFIGURATION_STEREO;
        int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
        int minBufSize = AudioTrack.getMinBufferSize(getSampleRateInHz(), channelConfig, audioFormat);
        if (minBufSize <= 0) {
            throw new InternalError("Buffer size error: " + minBufSize);
        }

        int bufferSize = 176400;
        track = new AudioTrack(AudioManager.STREAM_MUSIC, getSampleRateInHz(), channelConfig, audioFormat, bufferSize, AudioTrack.MODE_STREAM);
        bytesPerSecond = track.getChannelCount() * track.getSampleRate()
                * (track.getAudioFormat() == AudioFormat.ENCODING_PCM_16BIT ? 2 : 1);
    }

    byte[] getFreeBuffer(int length) {
        int n = buffersNotInUse.size();
        while (--n > 0) {
            byte[] b = buffersNotInUse.get(n).get();
            if (b == null) buffersNotInUse.remove(n); // Obj fjernet af garbage collector
            else if (b.length == length) {
                buffersNotInUse.remove(n);
                // Log.d("getFreeBuffer genbruger "+b);
                return b;
            }
        }
        Log.d("getFreeBuffer OPRETTER NY");
        return new byte[length];
    }

    void putData(byte[] data, int length) {
        byte[] buf = getFreeBuffer(length);
        System.arraycopy(data, 0, buf, 0, length);
        try {
            // Virker ikke: boolean taken = buffersInUse.offer(buf, 1000, TimeUnit.MILLISECONDS);
            boolean taken = buffersInUse.offer(buf);
            if (!taken)
                throw new IllegalStateException(" not taken ??!?");
        } catch (Exception ex) {
            Log.e(ex);
            result = -1;
        }
        bytesInBuffer += length;
    }

    void startPlay() {
        track.play();
        Runnable r = new Runnable() {
            public void run() {
                while (!stop)
                    try {
                        write();
                    } catch (InterruptedException ex) {
                        Log.e(ex);
                        result = -1;
                    }
            }
        };
        new Thread(r).start();
    }

    private void write() throws InterruptedException {

        long start = System.currentTimeMillis();
        byte[] buff = buffersInUse.take();
        long tage = System.currentTimeMillis();
        result = track.write(buff, 0, buff.length);
        long slut = System.currentTimeMillis();
        //Log.d("AudioTrack.write in " + (slut - tage) + " ms (wait " + (tage - start) + " ms)");

        buffersNotInUse.add(new SoftReference(buff));
        bytesInBuffer -= buff.length;

        if (handler != null && runWhenPcmAudioSinkWrite != null) {
            handler.post(runWhenPcmAudioSinkWrite);
        }
        synchronized (this) {
            this.notifyAll();
        }
    }

    boolean stop = false;
    private int sampleRateInHz = 44100; // Default sample rate

    void stopPlay() {
        stop = true;
        track.release();
    }

    public String bufferInSecs() {
        return Float.toString((10 * bytesInBuffer / bytesPerSecond) / 10f);
    }


    public int getSampleRateInHz() {
        return sampleRateInHz;
    }

    public void setSampleRateInHz(int sampleRateInHz) {
        this.sampleRateInHz = sampleRateInHz;
        init();
    }

    public void setHandler(Handler handler) {
        this.handler = handler;
    }

    public void setRunWhenPcmAudioSinkWrite(Runnable runWhenPcmAudioSinkWrite) {
        this.runWhenPcmAudioSinkWrite = runWhenPcmAudioSinkWrite;
    }

    public AudioTrack getTrack() {
        return track;
    }

}
