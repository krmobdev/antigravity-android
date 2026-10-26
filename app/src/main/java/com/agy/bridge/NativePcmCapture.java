package com.agy.bridge;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.SystemClock;
import android.util.Base64;

/**
 * Narrow transport for upstream Antigravity speech-to-text:
 * microphone -> signed 16-bit little-endian mono PCM at 16kHz.
 * Does not transcribe or persist speech; Core handles its original RPC.
 * Captures ONLY following a token-authorized user button action.
 */
public final class NativePcmCapture {
    private static final int RATE=16000;
    private static final int FRAME_BYTES=1280; // 40ms, identical to web AudioWorklet.
    private final PcmCaptureSession capture = new PcmCaptureSession(SystemClock::elapsedRealtime);

    public synchronized String start(Activity activity) {
        if (capture.isRecording()) return "ERR_ALREADY_RECORDING";
        capture.stop();
        if (activity==null || activity.isFinishing() || activity.isDestroyed()
                || !activity.hasWindowFocus()) return "ERR_NOT_FOREGROUND";
        if (activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) return "ERR_NO_PERMISSION";
        int min=AudioRecord.getMinBufferSize(RATE,AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        if(min<=0)return "ERR_PCM_UNSUPPORTED";
        AudioRecord recorder=null;
        try{
            recorder=new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,RATE,
                    AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,
                    Math.max(min,FRAME_BYTES*4));
            if(recorder.getState()!=AudioRecord.STATE_INITIALIZED){
                release(recorder);
                return "ERR_PCM_INIT";
            }
            recorder.startRecording();
            if(recorder.getRecordingState()!=AudioRecord.RECORDSTATE_RECORDING){
                release(recorder);
                return "ERR_PCM_START";
            }
            final AudioRecord source=recorder;
            return capture.start(new PcmCaptureSession.Source() {
                public int read(byte[] buffer) {
                    return source.read(buffer,0,buffer.length,AudioRecord.READ_BLOCKING);
                }
                public void stop() { source.stop(); }
                public void release() { source.release(); }
            });
        }catch(SecurityException e){
            release(recorder);
            return "ERR_NO_PERMISSION";
        }catch(RuntimeException e){
            release(recorder);
            return "ERR_PCM_START";
        }
    }

    private static void release(AudioRecord recorder) {
        if(recorder==null)return;
        try{recorder.stop();}catch(RuntimeException ignored){}
        try{recorder.release();}catch(RuntimeException ignored){}
    }

    /** Bounded 16kHz PCM transport; errors never silently discard speech. */
    public synchronized String poll(){
        String error=capture.failure();
        if(error!=null)return error;
        byte[] bytes=capture.poll();
        if(bytes==null){
            error=capture.failure();
            return error!=null?error:"ERR_PCM_NOT_RECORDING";
        }
        return bytes.length==0?"":Base64.encodeToString(bytes,Base64.NO_WRAP);
    }

    public synchronized void stop(){ capture.stop(); }

    /** Read-only; no device capture. */
    public String capability(Activity activity){
        if(activity==null||activity.isFinishing()||activity.isDestroyed())return "ERR_NO_ACTIVITY";
        if(activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED)return "ERR_NO_PERMISSION";
        return AudioRecord.getMinBufferSize(RATE,AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT)>0?"READY":"ERR_PCM_UNSUPPORTED";
    }
}
