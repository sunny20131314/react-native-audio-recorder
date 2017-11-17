'use strict';

'use strict';

import React,{
  NativeModules,
  Platform,
} from 'react-native';

const Recorder = NativeModules.AudioRecorderManager;

// 事件：
//     recordingProgress: 录音进度
//     recordingFinished: 录音结束
//     encodingFinished:  编码结束
export default {
    prepareRecordingAtPath: function(path, options = {}) {
        if (Platform.OS === 'ios') {
            var defaultOptions = {
                SampleRate: 44100.0,
                Channels: 2,
                AudioQuality: 'High',
                AudioEncoding: 'ima4',
                OutputFormat: 'mpeg_4',
                MeteringEnabled: false,
                MeasurementMode: false,
                AudioEncodingBitRate: 32000
            };
            var recordingOptions = {...defaultOptions, ...options};

            Recorder.prepareRecordingAtPath(
              path,
              recordingOptions.SampleRate,
              recordingOptions.Channels,
              recordingOptions.AudioQuality,
              recordingOptions.AudioEncoding,
              recordingOptions.MeteringEnabled,
              recordingOptions.MeasurementMode
            );
        }
    },

    startRecording(saveFilePath, fileName) {
        if (Platform.OS === 'ios'){
            return Recorder.startRecording();
        }
        return new Promise((resolve, reject) => {
            Recorder.startRecording(saveFilePath, fileName, (args) => resolve(args));
        });
    },

    pauseRecording() {
        if (Platform.OS === 'ios'){
            return Recorder.pauseRecording();
        }
        return new Promise((resolve, reject) => {
            Recorder.pauseRecording((args) => resolve(args));
        });
    },

    stopRecording() {
        if (Platform.OS === 'ios'){
            return Recorder.stopRecording();
        }
        return new Promise((resolve, reject) => {
            Recorder.stopRecording((args) => resolve(args));
        });
    },

    // android
    resetRecording() {
        if (Platform.OS === 'ios'){
            return;
        }
        return new Promise((resolve, reject) => {
            Recorder.resetRecording((args) => resolve(args));
        });
    },

    // android
    clearCache(){
        return new Promise((resolve, reject) => {
            Recorder.clearCache((args) => resolve(args));
        });
    },

    // android
    encodeToM4a() {
        Recorder.encodeToM4a();
    }
};