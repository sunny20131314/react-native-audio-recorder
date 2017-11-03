package com.sunny.audioRecorder;

import java.io.File;
import android.widget.Toast;
import android.util.Log;
import android.content.Context;
import android.media.AudioFormat;


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

//import com.sunny.audioRecorder.AudioEncode;


public class RecordModule extends ReactContextBaseJavaModule {
    private Callback callback;
    private static final String TAG = RecordModule.class.getSimpleName();
    private AudioRecorder exRecorder = null;
    private Context context;
    private String audioFilePath; // 音频文件路径
    private String audioPath;  // 音频路径(文件夹)

    public static RecordModule instance; // 保存

    // 设置音频采样率，44100是目前的标准，但是某些设备仍然支持22050,16000,11025
    private final int sampleRateInHz = 44100;
    // 设置音频的录制的声道CHANNEL_IN_STEREO为双声道，CHANNEL_CONFIGURATION_MONO为单声道
    private final int channelConfig = AudioFormat.CHANNEL_IN_STEREO;
    // 音频数据格式:PCM 16位每个样本。保证设备支持。PCM 8位每个样本。不一定能得到设备支持。
    private final int audioFormat = AudioFormat.ENCODING_PCM_16BIT;

    public RecordModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.context = reactContext;
        this.context = reactContext;
        instance = this;
    }

    @Override
    public String getName() { return "AudioRecorderManager"; }

    @ReactMethod
    public void prepareRecordingAtPath(String recordingPath, ReadableMap recordingSettings, Promise promise) {
//        if (isRecording){
//            logAndRejectPromise(promise, "INVALID_STATE", "Please call stopRecording before starting recording");
//        }
//
//        recorder = new MediaRecorder();
//        try {
//            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
//            int outputFormat = getOutputFormatFromString(recordingSettings.getString("OutputFormat"));
//            recorder.setOutputFormat(outputFormat);
//            int audioEncoder = getAudioEncoderFromString(recordingSettings.getString("AudioEncoding"));
//            recorder.setAudioEncoder(audioEncoder);
//            recorder.setAudioSamplingRate(recordingSettings.getInt("SampleRate"));
//            recorder.setAudioChannels(recordingSettings.getInt("Channels"));
//            recorder.setAudioEncodingBitRate(recordingSettings.getInt("AudioEncodingBitRate"));
//            recorder.setOutputFile(recordingPath);
//        }
//        catch(final Exception e) {
//            logAndRejectPromise(promise, "COULDNT_CONFIGURE_MEDIA_RECORDER" , "Make sure you've added RECORD_AUDIO permission to your AndroidManifest.xml file "+e.getMessage());
//            return;
//        }
//
//        currentOutputFile = recordingPath;
//        try {
//            recorder.prepare();
//            promise.resolve(currentOutputFile);
//        } catch (final Exception e) {
//            logAndRejectPromise(promise, "COULDNT_PREPARE_RECORDING_AT_PATH "+recordingPath, e.getMessage());
//        }
    }

    @ReactMethod
    public void startRecording(String filePath, String fileName, Callback callback) {
        sendEvent("recordingProgress", "dddd1");

        WritableMap callbackMap = Arguments.createMap();
        if ( exRecorder == null || exRecorder.getState() == AudioRecorder.State.INITIALIZING || exRecorder.getState() == AudioRecorder.State.STOPPED ) {
            audioFilePath = filePath + fileName;
            audioPath = filePath;
            try {
                // 文件存在则创建
                File outputPath = new File(audioPath);
                outputPath.mkdirs();

                File outputFile = new File(audioFilePath);
                if (outputFile.exists() && outputFile.isFile()) {
                    outputFile.delete();
                }
            } catch (Exception ex) {
                callbackMap.putBoolean("success", false);
                callbackMap.putString("message","文件夹创建失败");
                callback.invoke(callbackMap);
                return;
            }
            exRecorder = AudioRecorder.getInstanse(sampleRateInHz, channelConfig, audioFormat, this);
            exRecorder.setOutputFile(audioFilePath);
            exRecorder.start();

            callbackMap.putBoolean("success", true);
            callbackMap.putString("message","开始录音");
        }
        else if (exRecorder.getState() == AudioRecorder.State.PAUSED) {
            exRecorder.start();

            callbackMap.putBoolean("success", true);
            callbackMap.putString("message","继续录音");
        }
        else if (exRecorder.getState() == AudioRecorder.State.RECORDING) {
            callbackMap.putBoolean("success", true);
            callbackMap.putString("message","录音已开始");
        }
        else {
            callbackMap.putBoolean("success", false);
            callbackMap.putString("message","初始化失败");
        }
        callback.invoke(callbackMap);
    }

    @ReactMethod
    public void pauseRecording(Callback callback) {
        WritableMap callbackMap = Arguments.createMap();
        if (exRecorder == null || exRecorder.getState() != AudioRecorder.State.RECORDING){
            callbackMap.putBoolean("success", false);
            callbackMap.putString("message","暂停操作失败, 未正确开始录音,或发生错误.");
        }
        else if (exRecorder.getState() == AudioRecorder.State.PAUSED) {
            callbackMap.putBoolean("success", true);
            callbackMap.putString("message","录音已暂停");
        }
        else {
            exRecorder.pause();

            callbackMap.putBoolean("success", true);
            callbackMap.putString("message", "暂停录音成功");
        }

        callback.invoke(callbackMap);
    }

    @ReactMethod
    public void stopRecording(Callback callback) {
        WritableMap callbackMap = Arguments.createMap();
//        if (exRecorder == null || exRecorder.getState() != AudioRecorder.State.RECORDING || exRecorder.getState() != AudioRecorder.State.PAUSED ){
//            callbackMap.putBoolean("success", false);
//            callbackMap.putString("message","未正确开始录音,或发生错误.");
//        }
//        else
        if (exRecorder.getState() == AudioRecorder.State.STOPPED) {
            callbackMap.putBoolean("success", true);
            callbackMap.putString("message","录音已结束");
        }
        else {
            exRecorder.stop();
//            exRecorder.release();
            exRecorder = null;

            callbackMap.putBoolean("success", true);
            callbackMap.putString("message", audioFilePath);
        }

        callback.invoke(callbackMap);
    }

    @ReactMethod
    public void resetRecording(Callback callback) {
        exRecorder.reset();
    }

    @ReactMethod
    public void clearCache(Callback callback) {
        try {
            File file = new File(audioPath);
            if (!file.exists()) {
                callback.invoke(true);
                return;
            }
            boolean success = DeleteRecursive(file);
            callback.invoke(success);
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
    public void encodeToM4a(Callback callback) {
        Log.i("ccc State.STOPPED:", "开始编译");
        // todo 开始编译
        AudioEncode audioEncode = AudioEncode.getInstanse(audioFilePath, audioFilePath + ".m4a", audioFormat, sampleRateInHz);
        audioEncode.startEncode();

        // todo 结束后返回地址~
    }

    private void sendEvent(String eventName, Object params) {
        this.getReactApplicationContext()
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(eventName, params);
    }
}