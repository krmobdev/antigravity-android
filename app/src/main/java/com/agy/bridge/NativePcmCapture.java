package com.agy.bridge;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.SystemClock;
import android.util.Base64;
import java.util.ArrayDeque;
import java.util.Arrays;

/**
 * Narrow transport for upstream Antigravity speech-to-text:
 * microphone -> signed 16-bit little-endian mono PCM at 16kHz.
 * Does not transcribe or persist speech; Core handles its original RPC.
 * Captures ONLY following a token-authorized user button action.
 */
public final class NativePcmCapture {
    private static final int RATE=16000;
    private static final int FRAME_BYTES=1280; // 40ms, identical to web AudioWorklet.
    private static final int MAX_BUFFERED_FRAMES=20;
    private static final long LIMIT_MS=4*60*1000L;
    private final Object queueLock=new Object();
    private final ArrayDeque<byte[]> frames=new ArrayDeque<>();
    private volatile AudioRecord active;
    private volatile boolean capturing;
    private volatile String failure;
    private volatile long startedAt;
    private Thread worker;

    public synchronized String start(Activity activity) {
        if (capturing) return "ERR_ALREADY_RECORDING";
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
                recorder.release();
                return "ERR_PCM_INIT";
            }
            recorder.startRecording();
            if(recorder.getRecordingState()!=AudioRecord.RECORDSTATE_RECORDING){
                recorder.release();
                return "ERR_PCM_START";
            }
            synchronized(queueLock){frames.clear();}
            failure=null;
            startedAt=SystemClock.elapsedRealtime();
            active=recorder;
            capturing=true;
            final AudioRecord source=recorder;
            worker=new Thread(()->pump(source),"agy-native-pcm-capture");
            worker.setDaemon(true);
            worker.start();
            return "OK";
        }catch(SecurityException e){
            if(recorder!=null)recorder.release();
            return "ERR_NO_PERMISSION";
        }catch(RuntimeException e){
            if(recorder!=null)try{recorder.release();}catch(RuntimeException ignored){}
            return "ERR_PCM_START";
        }
    }

    private void pump(AudioRecord source){
        byte[] buffer=new byte[FRAME_BYTES];
        while(capturing && source==active){
            if(SystemClock.elapsedRealtime()-startedAt>LIMIT_MS){
                failure="ERR_PCM_LIMIT";
                break;
            }
            int read;
            try{read=source.read(buffer,0,buffer.length,AudioRecord.READ_BLOCKING);}
            catch(RuntimeException e){failure="ERR_PCM_READ";break;}
            if(read<0){failure="ERR_PCM_READ";break;}
            if(read==0)continue;
            if((read&1)!=0)read--;
            if(read>0){
                synchronized(queueLock){
                    if(frames.size()>=MAX_BUFFERED_FRAMES)frames.removeFirst();
                    frames.addLast(Arrays.copyOf(buffer,read));
                }
            }
        }
        if(failure!=null)capturing=false;
    }

    /** Poll only a bounded portion of captured PCM. Never logs audio bytes. */
    public String poll(){
        if(failure!=null)return failure;
        if(!capturing)return "ERR_PCM_NOT_RECORDING";
        byte[] bytes;
        synchronized(queueLock){
            int n=0;
            for(byte[] frame:frames)n+=frame.length;
            if(n==0)return "";
            bytes=new byte[Math.min(n,FRAME_BYTES*8)];
            int offset=0;
            while(!frames.isEmpty() && offset+frames.peekFirst().length<=bytes.length){
                byte[] frame=frames.removeFirst();
                System.arraycopy(frame,0,bytes,offset,frame.length);
                offset+=frame.length;
            }
            if(offset!=bytes.length)bytes=Arrays.copyOf(bytes,offset);
        }
        return Base64.encodeToString(bytes,Base64.NO_WRAP);
    }

    public synchronized void stop(){
        capturing=false;
        AudioRecord prior=active;
        active=null;
        worker=null;
        if(prior!=null){
            try{prior.stop();}catch(RuntimeException ignored){}
            try{prior.release();}catch(RuntimeException ignored){}
        }
        failure=null;
        synchronized(queueLock){frames.clear();}
    }

    /** Read-only; no device capture. */
    public String capability(Activity activity){
        if(activity==null||activity.isFinishing()||activity.isDestroyed())return "ERR_NO_ACTIVITY";
        if(activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED)return "ERR_NO_PERMISSION";
        return AudioRecord.getMinBufferSize(RATE,AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT)>0?"READY":"ERR_PCM_UNSUPPORTED";
    }
}
