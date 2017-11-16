package com.sunny.audioRecorder;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.FileNotFoundException;
import java.util.Timer;
import java.util.TimerTask;

import android.util.Log;
import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.AsyncTask;


import com.facebook.react.bridge.NativeModule;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.common.MapBuilder;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.Promise;
import com.facebook.react.modules.core.DeviceEventManagerModule;

// 头文件
import com.sunny.audioRecorder.WavHeader;

// 编译
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Environment;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.io.FileInputStream;

/**
 * Created by sunzhimin on 2017/11/3.
 */
public class RecordModule extends ReactContextBaseJavaModule {
    private Callback callback;
    private static final String TAG = RecordModule.class.getSimpleName();
    private AudioRecord auRecorder = null;
    private String audioFilePath; // 音频文件路径
    private String audioPath;  // 音频路径(文件夹)

    // 设置音频采样率，44100是目前的标准，但是某些设备仍然支持22050,16000,11025
    private final int sampleRateInHz = 44100;
    // 设置音频的录制的声道CHANNEL_IN_STEREO为双声道，CHANNEL_CONFIGURATION_MONO为单声道
    private final int channelConfig = AudioFormat.CHANNEL_IN_STEREO;
    // 音频数据格式:PCM 16位每个样本。保证设备支持。PCM 8位每个样本。不一定能得到设备支持。
    private final int audioFormat = AudioFormat.ENCODING_PCM_16BIT;

    /**
     * 录音:
     *
     * 初始化
     * -> 开始录音
     * |  暂停
     * |  结束: 开始编译文件为wav格式, 返回wav地址。
     * -- 重新录制(清除缓存,开始录音)
     * */
    /**
     * INITIALIZING : recorder is initializing;
     * RECORDING : recording
     * PAUSED : recording pause
     * ERROR : reconstruction needed
     * STOPPED: reset needed
     */
    public enum State {INITIALIZING, RECORDING, PAUSED, ERROR, STOPPED};

    // Recorder state; see State
    public State state;

    //录制音频参数
    private final int audioSource = MediaRecorder.AudioSource.MIC;

    // File writer (only in uncompressed mode)
    private RandomAccessFile randomAccessWriter;

    // buffer size
    private int bufferSize;

    private Timer timer;

    // 记录录音时间
    private int recorderSecondsElapsed = 0;

    public RecordModule(ReactApplicationContext reactContext) {
        super(reactContext);
        //this.context = reactContext;
    }

    @Override
    public String getName() { return "AudioRecorderManager"; }

    @ReactMethod
    public void startRecording(String filePath, String fileName, Callback callback) {
        WritableMap callbackMap = Arguments.createMap();

        // 开始录音
        if (  state == State.ERROR || state == State.STOPPED || auRecorder == null) {
            audioFilePath = filePath + fileName;
            audioPath = filePath;

            // 已存在的删除，重新实例化
            if ( auRecorder != null ) {
                auRecorder.stop();
                auRecorder.release();
                auRecorder = null;
            }
            try {
                // 文件存在则创建
                File outputPath = new File(audioPath);
                outputPath.mkdirs();

                File outputFile = new File(audioFilePath);
                if (outputFile.exists() && outputFile.isFile()) {
                    outputFile.delete();
                }
            } catch (Exception ex) {
                callbackMap.putBoolean("status", false);
                callbackMap.putString("message","文件操作失败");
                callback.invoke(callbackMap);
                return;
            }

            try
            {
                bufferSize = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat);
                auRecorder = new AudioRecord(audioSource, sampleRateInHz, channelConfig, audioFormat, bufferSize);

                writeWavHeader(); // 添加头文件, 录音后直接生成 wav 格式文件
                if ( auRecorder != null ) {
                    state = State.INITIALIZING;
                    start();
                    callbackMap.putBoolean("status", true);
                    callbackMap.putString("message","开始录音");
                }
                else {
                    state = State.ERROR;
                }
            }
            catch (IOException e) {
                callbackMap.putBoolean("status", false);
                callbackMap.putString("message","初始化失败: " + e.getMessage());
                state = State.ERROR;
            }
            catch (Exception e)
            {
                callbackMap.putBoolean("status", false);
                callbackMap.putString("message","初始化失败: " + e.getMessage());
                state = State.ERROR;
            }
        }
        // 继续录音
        else if (state == State.INITIALIZING || state == State.PAUSED) {
            start();
            callbackMap.putBoolean("status", true);
            callbackMap.putString("message","继续录音");
        }
        else if (state == State.RECORDING) {
            callbackMap.putBoolean("status", true);
            callbackMap.putString("message","录音已开始");
        }
        else {
            callbackMap.putBoolean("status", false);
            callbackMap.putString("message","初始化失败");
        }
        callback.invoke(callbackMap);
    }

    @ReactMethod
    public void pauseRecording(Callback callback) {
        WritableMap callbackMap = Arguments.createMap();
        if (auRecorder == null || state != State.RECORDING){
            callbackMap.putBoolean("status", false);
            callbackMap.putString("message","暂停操作失败, 未正确开始录音,或发生错误.");
        }
        else if (state == State.PAUSED) {
            callbackMap.putBoolean("status", true);
            callbackMap.putString("message","录音已暂停");
        }
        else {
            pause();
            callbackMap.putBoolean("status", true);
            callbackMap.putString("message", "暂停录音成功");
        }

        callback.invoke(callbackMap);
    }

    @ReactMethod
    public void stopRecording(Callback callback) {
        if (auRecorder == null){
            return;
        }
        else if (state == State.PAUSED) {
            WritableMap body = Arguments.createMap();
            body.putBoolean("status", true);
            body.putString("message", "录音结束");
            sendEvent("recordingFinished", body);
        }
        else if (state != State.STOPPED) {
            stop();
        }

        WritableMap callbackMap = Arguments.createMap();
        callbackMap.putBoolean("status", true);
        callbackMap.putString("message","录音已结束");
        callbackMap.putString("param", audioFilePath);
        callback.invoke(callbackMap);
    }

    @ReactMethod
    public void resetRecording(Callback callback) {
        WritableMap callbackMap = Arguments.createMap();
        try
        {
            if ( state == State.PAUSED || state == State.RECORDING ) {
                stop();
                state = State.INITIALIZING; // 中止录音

                randomAccessWriter.close(); // Remove prepared file

//              删除缓存文件
                if (audioFilePath != null)
                {
                    (new File(audioFilePath)).delete();
                }
            }

            recorderSecondsElapsed = 0;
            auRecorder = new AudioRecord(audioSource, sampleRateInHz, channelConfig, audioFormat, bufferSize);

            if ( auRecorder != null ) {
                state = State.INITIALIZING;
                callbackMap.putBoolean("status", true);
                callbackMap.putString("message","录音已重置");
                callbackMap.putString("param", audioFilePath);
                start();
            }
            else {
                state = State.ERROR;
                callbackMap.putBoolean("status", false);
                callbackMap.putString("message","初始化失败");
            }
        }
        catch (IOException e)
        {
            callbackMap.putString("message","初始化失败" + e.getMessage());
            callbackMap.putBoolean("status", false);
            state = State.ERROR;
        }
        catch (Exception e)
        {
            callbackMap.putBoolean("status", false);
            callbackMap.putString("message","初始化失败: " + e.getMessage());
            state = State.ERROR;
        }
        callback.invoke(callbackMap);
    }

    @ReactMethod
    public void clearCache(Callback callback) {
        try {
            File file = new File(audioPath);
            if (!file.exists()) {
                callback.invoke(true);
                return;
            }
            boolean status = DeleteRecursive(file);
            callback.invoke(status);
        } catch (Exception ex) {
            ex.printStackTrace();
            callback.invoke(false);
        }
    }

    private boolean DeleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            for (File child : fileOrDirectory.listFiles()) {
                DeleteRecursive(child);
            }
        }
        return fileOrDirectory.delete();
    }

    @ReactMethod
    public void encodeToM4a() {
        new AudioEncodeTask().execute();
    }

    private void sendEvent(String eventName, Object params) {
        this.getReactApplicationContext()
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(eventName, params);
    }

    /**
     * 录音
     */
    public void start() {
        state = State.RECORDING;
        new AudioRecordTask().execute();
        startTimer();

        // 开始录音
    }

    public void pause() {
        state = State.PAUSED;

        //停止录制
        auRecorder.stop();

        stopTimer();
    }

    public void stop()
    {
        state = State.STOPPED;

        //停止录制
        auRecorder.stop();
        auRecorder.release();
        auRecorder = null;

        stopTimer();
        recorderSecondsElapsed = 0;
    }

    class AudioRecordTask extends AsyncTask<Void, Void, Void>{
        @Override
        protected Void doInBackground(Void... params) {
            try {
                randomAccessWriter = randomAccessFile(audioFilePath);
                //定义缓冲
                byte[] b = new byte[bufferSize];

                //开始录制音频
                auRecorder.startRecording();

                //定义循环，根据 State.RECORDING 的值来判断是否继续录制
                while (state == State.RECORDING) {
                    //从bufferSize中读取字节。
                    int bufferReadResult = auRecorder.read(b, 0, b.length);

                    //获取字节流
                    if (bufferReadResult > 0 && AudioRecord.ERROR_INVALID_OPERATION != bufferReadResult) {
                        randomAccessWriter.seek(randomAccessWriter.length());
                        randomAccessWriter.write(b, 0, b.length);
                    }
                }

                System.out.println("ccc State:" + state);

                if (state == State.STOPPED) {
                    randomAccessWriter.close();
                    writeWavHeader();
                    WritableMap body = Arguments.createMap();
                    body.putBoolean("status", true);
                    body.putString("message", "录音结束");
                    sendEvent("recordingFinished", body);
                }
                else if ( state == State.PAUSED ) {
                    writeWavHeader();
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }
    }

    /** 生成文件 */
    private static RandomAccessFile randomAccessFile(String file) {
        RandomAccessFile randomAccessFile;
        try {
            randomAccessFile = new RandomAccessFile(file, "rw");
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        return randomAccessFile;
    }

    private void writeWavHeader() throws IOException {
        final RandomAccessFile wavFile = randomAccessFile(audioFilePath);
        wavFile.seek(0); // to the beginning
        wavFile.write(new WavHeader(sampleRateInHz, channelConfig, audioFormat, wavFile.length()).toBytes());
        wavFile.close();
    }

    private void startTimer(){
        stopTimer();
        timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                // 显示时间缩小到.1s
                if ( recorderSecondsElapsed%10 == 0 ) {
                    WritableMap body = Arguments.createMap();
                    body.putBoolean("status", true);
                    body.putString("message", "录音进度");
                    body.putInt("currentTime", recorderSecondsElapsed / 10);
                    sendEvent("recordingProgress", body);
                }
                recorderSecondsElapsed++;
            }
        }, 0, 100);
    }

    private void stopTimer(){
        if (timer != null) {
            timer.cancel();
            timer.purge();
            timer = null;
        }
    }

    /**
     * 转换格式
     */

    // 转为 m4a
    private static final String COMPRESSED_AUDIO_FILE_MIME_TYPE = MediaFormat.MIMETYPE_AUDIO_AAC; //音频类型 aac m4a
    private static final int OUTPUT_FORMAT = MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4; //音频类型 aac m4a
    private static final int COMPRESSED_AUDIO_FILE_BIT_RATE = 64000; // 64kbps
    private static final int BUFFER_SIZE = 48000;
    private static final int CODEC_TIMEOUT_IN_MS = 5000;
    static String LOGENCODE = "CONVERT AUDIO";

    class AudioEncodeTask extends AsyncTask<Void, Void, Void>{
        @Override
        protected Void doInBackground(Void... params) {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);

            WritableMap body = Arguments.createMap();

            try {
                File inputFile = new File(audioFilePath);
                FileInputStream fis = new FileInputStream(inputFile);

                File outputFile = new File(audioFilePath + ".m4a");
                if (outputFile.exists()) outputFile.delete();

                MediaMuxer mux = new MediaMuxer(outputFile.getAbsolutePath(), OUTPUT_FORMAT);
                MediaFormat outputFormat = MediaFormat.createAudioFormat(COMPRESSED_AUDIO_FILE_MIME_TYPE,sampleRateInHz, audioFormat == AudioFormat.CHANNEL_IN_MONO ? 1 : 2);
                outputFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
                outputFormat.setInteger(MediaFormat.KEY_BIT_RATE, COMPRESSED_AUDIO_FILE_BIT_RATE);
                outputFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384);

                MediaCodec codec = MediaCodec.createEncoderByType(COMPRESSED_AUDIO_FILE_MIME_TYPE);
                codec.configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
                codec.start();

                ByteBuffer[] codecInputBuffers = codec.getInputBuffers(); // Note: Array of buffers
                ByteBuffer[] codecOutputBuffers = codec.getOutputBuffers();

                MediaCodec.BufferInfo outBuffInfo = new MediaCodec.BufferInfo();
                byte[] tempBuffer = new byte[BUFFER_SIZE];
                boolean hasMoreData = true;
                double presentationTimeUs = 0;
                int audioTrackIdx = 0;
                int totalBytesRead = 0;
                //int percentComplete = 0;
                do {
                    int inputBufIndex = 0;
                    while (inputBufIndex != -1 && hasMoreData) {
                        inputBufIndex = codec.dequeueInputBuffer(CODEC_TIMEOUT_IN_MS);

                        if (inputBufIndex >= 0) {
                            ByteBuffer dstBuf = codecInputBuffers[inputBufIndex];
                            dstBuf.clear();

                            int bytesRead = fis.read(tempBuffer, 0, dstBuf.limit());
                            if (bytesRead == -1) { // -1 implies EOS
                                hasMoreData = false;
                                codec.queueInputBuffer(inputBufIndex, 0, 0, (long) presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            } else {
                                totalBytesRead += bytesRead;
                                dstBuf.put(tempBuffer, 0, bytesRead);
                                codec.queueInputBuffer(inputBufIndex, 0, bytesRead, (long) presentationTimeUs, 0);
                                presentationTimeUs = 1000000l * (totalBytesRead / 2) / sampleRateInHz;
                            }
                        }
                    }
                    // Drain audio
                    int outputBufIndex = 0;
                    while (outputBufIndex != MediaCodec.INFO_TRY_AGAIN_LATER) {
                        outputBufIndex = codec.dequeueOutputBuffer(outBuffInfo, CODEC_TIMEOUT_IN_MS);
                        if (outputBufIndex >= 0) {
                            ByteBuffer encodedData = codecOutputBuffers[outputBufIndex];
                            encodedData.position(outBuffInfo.offset);
                            encodedData.limit(outBuffInfo.offset + outBuffInfo.size);
                            if ((outBuffInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0 && outBuffInfo.size != 0) {
                                codec.releaseOutputBuffer(outputBufIndex, false);
                            }else{
                                mux.writeSampleData(audioTrackIdx, codecOutputBuffers[outputBufIndex], outBuffInfo);
                                codec.releaseOutputBuffer(outputBufIndex, false);
                            }
                        } else if (outputBufIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                            outputFormat = codec.getOutputFormat();
                            Log.v(LOGENCODE, "Output format changed - " + outputFormat);
                            audioTrackIdx = mux.addTrack(outputFormat);
                            mux.start();
                        } else if (outputBufIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                            Log.e(LOGENCODE, "Output buffers changed during encode!");
                        } else if (outputBufIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                            // NO OP
                        } else {
                            Log.e(LOGENCODE, "Unknown return code from dequeueOutputBuffer - " + outputBufIndex);
                        }
                    }
                    // 编译进度
                    //percentComplete = (int) Math.round(((float) totalBytesRead / (float) inputFile.length()) * 100.0);
                    //Log.v(LOGENCODE, "Conversion % - " + percentComplete);
                } while (outBuffInfo.flags != MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                fis.close();
                mux.stop();
                mux.release();
                body.putBoolean("status", true);
                body.putString("message", "编译音频成功");
            } catch (FileNotFoundException e) {
                body.putBoolean("status", false);
                body.putString("message", "找不到该音频文件");
            } catch (IOException e) {
                body.putBoolean("status", false);
                body.putString("message", "编译音频失败");
            }

            sendEvent("recordingProgress", body);
            return null;
        }
    }
}