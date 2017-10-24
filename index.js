'use strict';

import React,{
    NativeModules
} from 'react-native';

const Recorder = NativeModules.AudioRecorder;

export default {
    startRecording(isAudio = true, saveFileName) {
        return new Promise((resolve, reject) => {
            Recorder.startRecord(isAudio, saveFileName, (args) => resolve(args));
        });
    },

    stopRecording() {
        return new Promise((resolve, reject) => {
            Recorder.stopRecord((args) => resolve(args));
        });
    },

    clearCache(){
        return new Promise((resolve, reject) => {
            Recorder.clearCache((args) => resolve(args));
        });
    }
};