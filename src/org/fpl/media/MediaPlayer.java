package org.fpl.media;

import android.net.Uri;
import dk.nordfalk.netradio.Log;

// import android.util.Log;

public class MediaPlayer {

    private final static String TAG = "MediaPlayer";

    static {
        System.loadLibrary("player");
    }

    public boolean addBuzzTone = false;
    private boolean isPlaying;
    private Thread pcmProducerThread;
    private Runnable runWhenstreamCallback;
    public PcmAudioSink sink = new PcmAudioSink();
    private boolean stopRequested;

    /**
     * Create a new instance of MediaPlayer to play stream back
     *
     * @param uri URI of the media resource, fx, a stream
     * @return MediaPlayer
     */
    public static MediaPlayer create(Uri uri) {
        Log.d(TAG, "Create Stream");

        MediaPlayer mp = new MediaPlayer();
        mp.setDataSource(uri);
        mp.prepare();// Not needed yet. Is here for compatibility

        return mp;

    }

    /**
     * Create a new instance of MediaPlayer to play stream with format given. When the engine doesn't have to autodetect
     * stream format the playback starts faster
     *
     * @param uri    URI of the media resource, fx, a stream
     * @param format Format of the stream (tested with "mp3", "applehttp")
     * @return MediaPlayer
     */
    public static MediaPlayer create(Uri uri, String format) {
        Log.d(TAG, "Create Stream");

        MediaPlayer mp = new MediaPlayer();
        mp.setDataSource(uri, format);
        mp.prepare();// Not needed yet. Is here for compatibility

        return mp;

    }

    public MediaPlayer() {
        Log.d(TAG, "Create new MediaPlayer");

        n_createEngine();

    }

    public boolean isPlaying() {
        return isPlaying;
    }

    /**
     * Set up what needs to be set up in JNI
     */
    public native void n_createEngine();

    /**
     * Start playing stream back
     */
    public native void n_playStream();

    /**
     * Set data source - stream url for now
     *
     * @param path Stream URL
     */
    public native void n_setDataSource(String path);

    /**
     * Set data source - stream url for now
     *
     * @param path   Stream URL
     */
    public native void n_setDataSource(String path, String format);

    /**
     * Shutdown decoder engine
     */
    public native void n_shutdownEngine();

    /**
     * Stop playing stream back
     */
    public native void n_stopStream();

    /**
     * Not needed. Is here for compatibility
     */
    public void prepare() throws IllegalStateException {

    }

    /**
     * Shutdown engine and release all variables
     *
     * @throws IllegalStateException
     */
    public void release() throws IllegalStateException {
        n_shutdownEngine();
    }

    /**
     * Set data source to be played back
     *
     * @param uri URI of the media resource
     * @throws IllegalStateException
     */
    public void setDataSource(Uri uri) throws IllegalStateException {

        String scheme = uri.getScheme();
        if (scheme == null || scheme.equals("file")) {
            // TODO Implement file playback
            throw new IllegalArgumentException("File given " + uri);
        }

        n_setDataSource(uri.toString()); // Play path of stream
        return;

    }

    /**
     * Set data source to be played back
     *
     * @param uri     URI of the media resource
     * @throws IllegalStateException
     */
    public void setDataSource(Uri uri, String format) throws IllegalStateException {

        String scheme = uri.getScheme();
        if (scheme == null || scheme.equals("file")) {
            throw new IllegalArgumentException("File given " + uri);
        }

        n_setDataSource(uri.toString(), format); // Play path of stream
        return;

    }

    private void setPlaying(boolean status) {
        isPlaying = status;
    }

    /**
     * Start playing stream back
     *
     * @throws IllegalStateException
     */
    public void start() throws IllegalStateException {
        stopRequested = false;

        Runnable r = new Runnable() {
            public void run() {
                Log.d(TAG, "PlayThread: invoking n_playStream... ");
                n_playStream();
                Log.d(TAG, "PlayThread: n_playStream finished.");
                isPlaying = false;
                sink.stopPlay();
            }
        };

        pcmProducerThread = new Thread(r);
        pcmProducerThread.start();

    }

    /**
     * Stop playback
     *
     * @throws IllegalStateException
     */
    public void stop() throws IllegalStateException {
        // n_stopStream();
        if (isPlaying)
            sink.getTrack().stop();
        stopRequested = true;

    }

    public int streamSetupCallback(int sampleRate, int channels) {
        sink.init(sampleRate, channels);
        return 0;
    }

    /**
     * Method called from JNI
     *
     * @param data   Byte Array with decompressed data
     * @param length Length of the data in the array
     */
    public int streamCallback(byte[] data, int length) {
        Log.d(TAG, "data:" + length + " buffer " + sink.getBytesInBuffer() + " b (" + sink.bufferInSecs() + " sek)");
        try {
            if (stopRequested) {
                isPlaying = false;
                return 1;
            }

            if (addBuzzTone) {
                for (int i = 0; i < length; i += 151)
                    data[i] += i % 5 * 15;
            }

            if (sink.getHandler() != null && getRunWhenstreamCallback() != null) {
                sink.getHandler().post(getRunWhenstreamCallback());
            }

            sink.putData(data, length);
            //Log.d(TAG, "data,"+length+ " buffer " + sink.bytesInBuffer + " b (" + sink.bufferInSecs() + " sek)");

            if (!isPlaying()) {

                if (sink.getBytesInBuffer() < PcmAudioSink.PREFERRED_BUFFER_IN_SECONDS * sink.getBytesPerSecond()) {
                    return 0; // Not enough data, still bufferring
                }

                // Enough data - start playing
                sink.startPlay();
                setPlaying(true);
            } else try {

                // Wait if too much data
                while (sink.getBytesInBuffer() > PcmAudioSink.MAX_BUFFER_IN_SECONDS * sink.getBytesPerSecond()) {
                    synchronized (sink) {
                        sink.wait(1000);
                    }
                }
            } catch (InterruptedException e) {
            }

            if (sink.result < 0) {
                Log.e(TAG, "Cannot write to AudioTrack. Ret Code: " + sink.result);
                return 1;
            }

            return 0;

        } finally {
        }
    }

    // Signature: (Ljava/lang/String;I)V
    public void streamErrorCallback(String message, int errCode) {
        Log.e(TAG, "MediaPlayer Error: (" + errCode + ") " + message);
    }

    public Runnable getRunWhenstreamCallback() {
        return runWhenstreamCallback;
    }

    public void setRunWhenstreamCallback(Runnable runWhenstreamCallback) {
        this.runWhenstreamCallback = runWhenstreamCallback;
    }

}
