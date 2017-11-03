package com.sunny.audioRecorder;

import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import android.os.AsyncTask;


/**
 * Created by sunzhimin on 2017/11/3.
 */
public class AudioEncode {
    // 转为 m4a
    private static final String COMPRESSED_AUDIO_FILE_MIME_TYPE = MediaFormat.MIMETYPE_AUDIO_AAC; //音频类型 aac m4a
    private static final int OUTPUT_FORMAT = MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4; //音频类型 aac m4a
    private static final int COMPRESSED_AUDIO_FILE_BIT_RATE = 64000; // 64kbps
    private static final int BUFFER_SIZE = 48000;
    private static final int CODEC_TIMEOUT_IN_MS = 5000;

    static String LOGTAG = "CONVERT AUDIO";

    private String _inputFile;
    private String _outputFile;
    private int _channels;
    private int _sampleRateInHz;

    public static AudioEncode getInstanse (String inputFile, String outputFile, int audioFormat, int sampleRateInHz)
    {
        return new AudioEncode(inputFile,outputFile, audioFormat, sampleRateInHz);
    }

    public AudioEncode(String inputFile, String outputFile, int audioFormat, int sampleRateInHz)
    {
        _inputFile = inputFile;
        _outputFile = outputFile;
        _channels = (audioFormat == AudioFormat.CHANNEL_IN_MONO ? 1 : 2);
        _sampleRateInHz = sampleRateInHz;
    }

    /*开始编译*/
    public void startEncode ()
    {
        new AudioEncodeTask().execute();
    }

    class AudioEncodeTask extends AsyncTask<Void, Void, Void>{
        @Override
        protected Void doInBackground(Void... params) {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
            try {
                File inputFile = new File(_inputFile);
                FileInputStream fis = new FileInputStream(inputFile);

                File outputFile = new File(_outputFile);
                if (outputFile.exists()) outputFile.delete();

                MediaMuxer mux = new MediaMuxer(outputFile.getAbsolutePath(), OUTPUT_FORMAT);

                MediaFormat outputFormat = MediaFormat.createAudioFormat(COMPRESSED_AUDIO_FILE_MIME_TYPE,_sampleRateInHz, _channels);
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
                int percentComplete = 0;
                do {
                    int inputBufIndex = 0;
                    while (inputBufIndex != -1 && hasMoreData) {
                        inputBufIndex = codec.dequeueInputBuffer(CODEC_TIMEOUT_IN_MS);

                        if (inputBufIndex >= 0) {
                            ByteBuffer dstBuf = codecInputBuffers[inputBufIndex];
                            dstBuf.clear();

                            int bytesRead = fis.read(tempBuffer, 0, dstBuf.limit());
                            Log.e("bytesRead","Readed "+bytesRead);
                            if (bytesRead == -1) { // -1 implies EOS
                                hasMoreData = false;
                                codec.queueInputBuffer(inputBufIndex, 0, 0, (long) presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            } else {
                                totalBytesRead += bytesRead;
                                dstBuf.put(tempBuffer, 0, bytesRead);
                                codec.queueInputBuffer(inputBufIndex, 0, bytesRead, (long) presentationTimeUs, 0);
                                presentationTimeUs = 1000000l * (totalBytesRead / 2) / _sampleRateInHz;
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
                            Log.v(LOGTAG, "Output format changed - " + outputFormat);
                            audioTrackIdx = mux.addTrack(outputFormat);
                            mux.start();
                        } else if (outputBufIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                            Log.e(LOGTAG, "Output buffers changed during encode!");
                        } else if (outputBufIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                            // NO OP
                        } else {
                            Log.e(LOGTAG, "Unknown return code from dequeueOutputBuffer - " + outputBufIndex);
                        }
                    }
                    percentComplete = (int) Math.round(((float) totalBytesRead / (float) inputFile.length()) * 100.0);
                    Log.v(LOGTAG, "Conversion % - " + percentComplete);
                } while (outBuffInfo.flags != MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                fis.close();
                mux.stop();
                mux.release();
                Log.v(LOGTAG, "Compression done ...");
            } catch (FileNotFoundException e) {
                Log.e(LOGTAG, "File not found!", e);
            } catch (IOException e) {
                Log.e(LOGTAG, "IO exception!", e);
            }

            // todo 通知编译完成~
            return null;
        }
    }
}

