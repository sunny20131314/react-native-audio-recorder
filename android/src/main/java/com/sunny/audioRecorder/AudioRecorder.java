package com.sunny.audioRecorder;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import java.io.FileNotFoundException;
import android.os.AsyncTask;

import com.sunny.audioRecorder.WavHeader;

// js 交互
import android.content.Context;
//import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableMap;
//import com.facebook.react.bridge.ReadableMap;
//import com.facebook.react.bridge.Promise;
import com.facebook.react.modules.core.DeviceEventManagerModule;

// 计时
import java.util.Timer;
import java.util.TimerTask;
/**
 * 初始化
 * -> 开始录音
 * |  暂停
 * |  结束: 开始编译文件为wav格式, 返回wav地址。
 * -- 重新录制(清除缓存,开始录音)
 * */
public class AudioRecorder {
    private Boolean hasAddHead = false;  // 作用: 标志添加过头文件...

    /**
     * INITIALIZING : recorder is initializing;
     * RECORDING : recording
     * PAUSED : recording pause
     * ERROR : reconstruction needed
     * STOPPED: reset needed
     */
    public enum State {INITIALIZING, RECORDING, PAUSED, ERROR, STOPPED};

    // Recorder state; see State
    public State              state;

    // Recorder used for uncompressed recording
    private AudioRecord     audioRecorder = null;

    //录制音频参数
    private final int audioSource = MediaRecorder.AudioSource.MIC;

    // Output file path
    private String          filePath = null;

    // File writer (only in uncompressed mode)
    private RandomAccessFile randomAccessWriter;

    // buffer size
    private int                      bufferSize;

    private Timer timer;

    private int recorderSecondsElapsed = 0;

    public static AudioRecorder getInstanse(int sampleRateInHz, int channelConfig, int audioFormat, RecordModule context) //
    {
        return new AudioRecorder(sampleRateInHz, channelConfig, audioFormat, context.getReactApplicationContext());
    }

    private int sampleRateInHz;
    private int channelConfig;
    private int audioFormat;
    private Context context;
    /**
     * Default constructor
     */
    public AudioRecorder(int _sampleRateInHz, int _channelConfig, int _audioFormat, Context _context)
    {
        sampleRateInHz = _sampleRateInHz;
        channelConfig = _channelConfig;
        audioFormat = _audioFormat;
        context = _context;

        try
        {
            bufferSize = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat);
            audioRecorder = new AudioRecord(audioSource, sampleRateInHz, channelConfig, audioFormat, bufferSize);

            Log.i("ccc", "new AudioRecord");

            if ( audioRecorder != null ) {
                state = State.INITIALIZING;
            }
            else {
                state = State.ERROR;
            }

        } catch (Exception e)
        {
            Log.e(AudioRecorder.class.getName(), e.getMessage());
            state = State.ERROR;
        }
    }

    /**
     * 设置语音文件路径
     */
    public void setOutputFile(String path)
    {
        filePath = path;
    }

    /**
     *  返回当前状态
     */
    public State getState() {return state;}

    /**
     * 重置
     */
    public void reset()
    {
        try
        {
            if ( state == State.PAUSED || state == State.RECORDING ) {
                state = State.INITIALIZING; // 中止录音
                recorderSecondsElapsed = 0;

//              停止录音
                if (audioRecorder != null)
                {
                    audioRecorder.stop();
                    audioRecorder.release();
                }

                try
                {
                    randomAccessWriter.close(); // Remove prepared file
                }
                catch (IOException e)
                {
                    Log.e(AudioRecorder.class.getName(), "I/O exception occured while closing output file");
                }


//              删除缓存文件
                if (filePath != null)
                {
                    (new File(filePath)).delete();
                }

            }

            audioRecorder = new AudioRecord(audioSource, sampleRateInHz, channelConfig, audioFormat, bufferSize);

            Log.i("ccc", "new AudioRecord");

            if ( audioRecorder != null ) {
                state = State.INITIALIZING;
            }
            else {
                state = State.ERROR;
            }

            start();
        }
        catch (Exception e)
        {
            Log.e(AudioRecorder.class.getName(), e.getMessage());
            state = State.ERROR;
        }
    }

    public void start() {
        if (state == State.ERROR) {
            return;
        }
        try {
            if( !hasAddHead ) {
                writeWavHeader(); // 添加头文件, 录音后直接生成 wav 格式文件
            }
        } catch (IOException e) {
            Log.e(AudioRecorder.class.getName(), "I/O exception occured while closing output file or Error in applying wav header");
            state = State.ERROR;
            return;
        }
        state = State.RECORDING;
        new AudioRecordTask().execute();
        startTimer();
    }

    public void pause() {
        state = State.PAUSED;
    }

    /**
     *
     *
     *  Stops the recording, and sets the state to STOPPED.
     * In case of further usage, a reset is needed.
     * Also finalizes the wave file in case of uncompressed recording.
     *
     */
    public void stop()
    {
        state = State.STOPPED;
    }

    class AudioRecordTask extends AsyncTask<Void, Void, Void>{
        @Override
        protected Void doInBackground(Void... params) {
            try {
                randomAccessWriter = randomAccessFile(filePath);
                //定义缓冲
                byte[] b = new byte[bufferSize];

                //开始录制音频
                audioRecorder.startRecording();

                //定义循环，根据 State.RECORDING 的值来判断是否继续录制
                while (state == State.RECORDING) {
                    //从bufferSize中读取字节。
                    int bufferReadResult = audioRecorder.read(b, 0, b.length);
                    Log.i("ccc bufferReadResult~1: ",bufferReadResult + "");

                    //获取字节流
                    if (bufferReadResult > 0 && AudioRecord.ERROR_INVALID_OPERATION != bufferReadResult) {
                        randomAccessWriter.seek(randomAccessWriter.length());
                        randomAccessWriter.write(b, 0, b.length);
                    }
                }

                System.out.println("ccc State:" + state);

                //停止录制
                audioRecorder.stop();
                stopTimer();
                if (state == State.STOPPED) {
                    audioRecorder.release();
                    randomAccessWriter.close();
                    writeWavHeader();
                    recorderSecondsElapsed = 0;
                    // todo 录音结束
//                    RecordModule.instance.sendEvent("recordingFinished", true);
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
        hasAddHead = true;
        final RandomAccessFile wavFile = randomAccessFile(filePath);
        wavFile.seek(0); // to the beginning
        wavFile.write(new WavHeader(sampleRateInHz, channelConfig, audioFormat, wavFile.length()).toBytes());
//        wavFile.write(toBytes(sampleRateInHz, channelConfig, audioFormat, wavFile.length()));
        wavFile.close();
    }

    private void startTimer(){
        stopTimer();
        timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                WritableMap body = Arguments.createMap();
                body.putString("message", "录音进度");
                body.putInt("currentTime", recorderSecondsElapsed);
                // todo  显示时间 < 录音时间
//                RecordModule.instance.sendEvent("recordingProgress", body);
                recorderSecondsElapsed++;
            }
        }, 0, 1000);
    }

    private void stopTimer(){
        if (timer != null) {
            timer.cancel();
            timer.purge();
            timer = null;
        }
    }

    private void sendEvent(String eventName, Object params) {
//        context.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
//                .emit(eventName, params);
    }
}