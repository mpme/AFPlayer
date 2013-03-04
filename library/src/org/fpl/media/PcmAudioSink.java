/*
 * To change this template, choose Tools | Templates and open the template in the editor.
 */
package org.fpl.media;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Handler;
import android.util.Log;

import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * @author j
 */
public class PcmAudioSink {

    public static final Integer PREFERRED_BUFFER_IN_SECONDS = 9;
    public static final Integer MAX_BUFFER_IN_SECONDS = 40;

    private static final String TAG = "CCC";

    private Handler handler;
    private Runnable runWhenPcmAudioSinkWrite;
    private AudioTrack track;
    private Integer bytesPerSecond;
    private LinkedBlockingQueue<byte[]> buffersInUse;
    private ArrayList<SoftReference<byte[]>> buffersNotInUse;

    private Integer bytesInBuffer;
    private boolean stop;
    private Integer sampleRateInHz;
    private Integer channelsCount;

    public Integer result;

    public PcmAudioSink() {
        buffersInUse = new LinkedBlockingQueue<byte[]>();
        buffersNotInUse = new ArrayList<SoftReference<byte[]>>();
        bytesInBuffer = 0;
        stop = false;
        sampleRateInHz = 44100;
        channelsCount = 1;
        result = 0;
    }

    public void init(int sampleRate, int channels) {
        Log.d(TAG, "Call PcmAudioSink Init");

        setSampleRateInHz(sampleRate);
        setChannelsCount(channels);

        int channelConfig = channels > 1? AudioFormat.CHANNEL_OUT_STEREO : AudioFormat.CHANNEL_OUT_MONO;
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
                Log.d(TAG,"getFreeBuffer recycle "+b);
                return b;
            }
        }
        Log.d(TAG, "getFreeBuffer create new");
        return new byte[length];
    }

    void putData(byte[] data, int length) {

        byte[] buf = getFreeBuffer(length);
        bytesInBuffer += length;
        System.arraycopy(data, 0, buf, 0, length);
        try {
            // Virker ikke: boolean taken = buffersInUse.offer(buf, 1000, TimeUnit.MILLISECONDS);
            boolean taken = buffersInUse.offer(buf);
            if (!taken)
                throw new IllegalStateException(" not taken ??!?");
        } catch (Exception ex) {
            Log.e(TAG, ex.getMessage(), ex);
            result = -1;
        }

    }

    void startPlay() {
        track.play();
        Runnable r = new Runnable() {
            public void run() {
                while (!stop)
                    try {
                        write();
                    } catch (InterruptedException ex) {
                        Log.e(TAG, ex.getMessage(), ex);
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

    void stopPlay() {
        if (track != null) {
            stop = true;
            track.release();
        }
    }

    public String bufferInSecs() {
        return Float.toString((10 * bytesInBuffer / bytesPerSecond) / 10f);
    }


    public int getSampleRateInHz() {
        return sampleRateInHz;
    }

    public void setSampleRateInHz(int sampleRateInHz) {
        this.sampleRateInHz = sampleRateInHz;
    }

    public void setChannelsCount(int channels) {
        this.channelsCount = channels;
    }

    public Handler getHandler() {
        return handler;
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

    public Integer getBytesInBuffer() {
        return bytesInBuffer;
    }

    public Integer getBytesPerSecond() {
        return bytesPerSecond;
    }
}
