'use strict';

import React,{
    NativeModules,
    Platform,
    NativeAppEventEmitter,
} from 'react-native';

const Recorder = NativeModules.AudioRecorderManager;
const events = {
    recordingProgress: 'recordingProgress',
    recordingFinished: 'recordingFinished',
    encodingFinished: 'encodingFinished',
};
export default {
    prepareRecordingAtPath: function(path, options) {

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

        if (Platform.OS === 'ios') {
            Recorder.prepareRecordingAtPath(
              path,
              recordingOptions.SampleRate,
              recordingOptions.Channels,
              recordingOptions.AudioQuality,
              recordingOptions.AudioEncoding,
              recordingOptions.MeteringEnabled,
              recordingOptions.MeasurementMode
            );
        } else {
            return Recorder.prepareRecordingAtPath(path, recordingOptions);
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

    onProgress(cb) {
        if (this.progressSubscription) this.progressSubscription.remove();
        this.progressSubscription = NativeAppEventEmitter.addListener(events.recordingProgress, cb);
    },

    onFinished(cb) {
        if (this.finishedSubscription) this.finishedSubscription.remove();
        this.finishedSubscription = NativeAppEventEmitter.addListener(events.recordingFinished, cb);
    },

    onEncodeFinished(cb) {
        if (this.encodeFinishedSubscription) this.encodeFinishedSubscription.remove();
        this.encodeFinishedSubscription = NativeAppEventEmitter.addListener(events.encodingFinished, cb);
    },

    removeListeners() {
        if (this.progressSubscription) this.progressSubscription.remove();
        if (this.finishedSubscription) this.finishedSubscription.remove();
        if (this.encodeFinishedSubscription) this.encodeFinishedSubscription.remove();
    },

    encodeToM4a() {

    }

};