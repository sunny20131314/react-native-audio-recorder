'use strict';

import React,{
    NativeModules,
    Platform,
} from 'react-native';

const Recorder = NativeModules.AudioRecorderManager;

// 事件：
//     recordingProgress,
//     recordingFinished,
//     encodingFinished
export default {
    prepareRecordingAtPath: function(path, options) {
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
        return new Promise((resolve, reject) => {
            Recorder.startRecording(saveFilePath, fileName, (args) => resolve(args));
        });
    },

    pauseRecording() {
        return new Promise((resolve, reject) => {
            Recorder.pauseRecording((args) => resolve(args));
        });
    },

    stopRecording() {
        return new Promise((resolve, reject) => {
            Recorder.stopRecording((args) => resolve(args));
        });
    },

    resetRecording() {
        return new Promise((resolve, reject) => {
            Recorder.resetRecording((args) => resolve(args));
        });
    },

    clearCache(){
        return new Promise((resolve, reject) => {
            Recorder.clearCache((args) => resolve(args));
        });
    },

    encodeToM4a() {
        Recorder.encodeToM4a();
    }
};