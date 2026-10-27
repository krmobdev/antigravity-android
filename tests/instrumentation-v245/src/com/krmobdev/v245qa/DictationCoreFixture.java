package com.krmobdev.v245qa;

import android.content.Context;
import android.media.AudioFormat;
import android.os.Bundle;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Real Core/UI transcription of a locally generated public fixture; no microphone or agent send. */
final class DictationCoreFixture {
    interface Script { JSONObject run(String body)throws Exception; }
    interface Check { void run(boolean condition,String name); }
    interface Report { void run(String key,Object value); }
    interface MainThread { void run(Runnable action); }
    interface ClearDraft { void run()throws Exception; }
    private final Context context;
    private final Script script;
    private final Check check;
    private final Report report;
    private final MainThread main;
    private final ClearDraft clear;
    DictationCoreFixture(Context c,Script s,Check k,Report r,MainThread m,ClearDraft d){
        context=c;script=s;check=k;report=r;main=m;clear=d;
    }
    private byte[] speech()throws Exception {
        CountDownLatch initialized=new CountDownLatch(1),done=new CountDownLatch(1);
        AtomicInteger init=new AtomicInteger(-99),rate=new AtomicInteger(),channels=new AtomicInteger(),format=new AtomicInteger(),error=new AtomicInteger();
        AtomicReference<TextToSpeech> holder=new AtomicReference<>();
        ByteArrayOutputStream pcm=new ByteArrayOutputStream();
        File output=File.createTempFile("agy-qa-public-speech-",".wav",context.getCacheDir());
        try{
            main.run(()->holder.set(new TextToSpeech(context,status->{init.set(status);initialized.countDown();})));
            check.run(initialized.await(15,TimeUnit.SECONDS)&&init.get()==TextToSpeech.SUCCESS,"CORE_FIXTURE_TTS_INITIALIZED");
            TextToSpeech tts=holder.get();
            Voice selected=null;
            if(tts.getVoices()!=null)for(Voice voice:tts.getVoices())
                if(!voice.isNetworkConnectionRequired()&&(voice.getFeatures()==null||!voice.getFeatures().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED))&&"en".equals(voice.getLocale().getLanguage())){
                    selected=voice;if("US".equals(voice.getLocale().getCountry()))break;
                }
            check.run(selected!=null,"CORE_FIXTURE_OFFLINE_ENGLISH_VOICE");
            check.run(tts.setVoice(selected)==TextToSpeech.SUCCESS,"CORE_FIXTURE_OFFLINE_VOICE_SELECTED");
            tts.setSpeechRate(0.85f);
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){
                public void onStart(String id){}
                public void onDone(String id){done.countDown();}
                public void onError(String id){error.set(1);done.countDown();}
                public void onBeginSynthesis(String id,int sampleRate,int audioFormat,int channelCount){
                    rate.set(sampleRate);format.set(audioFormat);channels.set(channelCount);
                }
                public void onAudioAvailable(String id,byte[] audio){
                    synchronized(pcm){
                        if(pcm.size()+audio.length>2*1024*1024){error.set(2);return;}
                        pcm.write(audio,0,audio.length);
                    }
                }
            });
            check.run(tts.synthesizeToFile("This is a test.",new Bundle(),output,"agy-qa-public-fixture")==TextToSpeech.SUCCESS,"CORE_FIXTURE_SYNTHESIS_STARTED");
            check.run(done.await(20,TimeUnit.SECONDS)&&error.get()==0,"CORE_FIXTURE_SYNTHESIS_DONE");
            check.run(format.get()==AudioFormat.ENCODING_PCM_16BIT&&rate.get()>=8000&&rate.get()<=48000&&(channels.get()==1||channels.get()==2),"CORE_FIXTURE_PCM_FORMAT");
            byte[] input; synchronized(pcm){input=pcm.toByteArray();}
            int count=input.length/(2*channels.get());
            check.run(count>rate.get()/4,"CORE_FIXTURE_PUBLIC_SPEECH_PRESENT");
            int samples=(int)((long)count*16000/rate.get());
            byte[] result=new byte[samples*2];
            for(int i=0;i<samples;i++){
                double position=(double)i*rate.get()/16000;
                int left=(int)position,right=Math.min(count-1,left+1);
                double fraction=position-left;
                int a=sample(input,left,channels.get()),b=sample(input,right,channels.get());
                int value=(int)Math.round(a+(b-a)*fraction);
                result[2*i]=(byte)value;result[2*i+1]=(byte)(value>>8);
            }
            return result;
        }finally{
            TextToSpeech tts=holder.get();if(tts!=null){tts.stop();tts.shutdown();}
            output.delete();
        }
    }
    private static int sample(byte[] data,int index,int channels){
        int value=0;
        for(int c=0;c<channels;c++){
            int offset=(index*channels+c)*2;
            value+=(short)((data[offset]&255)|(data[offset+1]<<8));
        }
        return value/channels;
    }
    private static final String METRICS=
        "const e=document.querySelector('.agy-composer [contenteditable=true]');"+
        "const raw=e?.textContent||'';const text=raw.toLowerCase().replace(/[.!?]/g,'').replace(/\\s+/g,' ').trim();"+
        "const count=(text.match(/this is a test/g)||[]).length;"+
        "return JSON.stringify({editor:!!e,empty:!raw.trim(),count,fixtureOnly:(!raw.trim()||count>0)&&/^[A-Za-z\\s.!?]*$/.test(raw)&&!text.replace(/this is a test/g,'').trim(),"+
        "idle:!!document.querySelector('button[data-tooltip-id=input-send-button-record-tooltip]:not([disabled])'),"+
        "recording:!!document.querySelector('.agy-dictation-countdown'),"+
        "error:!!document.querySelector('.agy-composer-audio-error'),"+
        "frames:window.__agyCoreFixture?.index||0,total:window.__agyCoreFixture?.frames?.length||0});";
    void run()throws Exception{
        JSONObject before=script.run(METRICS);
        check.run(before.optBoolean("editor")&&before.optBoolean("empty")&&before.optBoolean("idle")&&!before.optBoolean("recording"),"CORE_FIXTURE_EMPTY_IDLE_DRAFT_GUARD");
        JSONObject build=script.run("return JSON.stringify({current:window.__AGY_AUDIO_DIAG_BUILD__==='2026-10-27-pcm-delivery-v8'});");
        check.run(build.optBoolean("current"),"CORE_FIXTURE_CURRENT_V8_ASSET");
        byte[] audio=speech();
        JSONArray frames=new JSONArray();
        for(int offset=0;offset<audio.length;offset+=1280)
            frames.put(Base64.encodeToString(audio,offset,Math.min(1280,audio.length-offset),Base64.NO_WRAP));
        report.run("CORE_FIXTURE_PCM_FRAMES",frames.length());
        boolean armed=false;
        try{
            JSONObject state=script.run("const api=window.AndroidBridge;window.__agyCoreFixture={api,frames:"+frames.toString()+",index:0,active:false};"+
                "const f=window.__agyCoreFixture;const overrides={startNativePcm(){f.index=0;f.active=true;return 'OK'},"+
                "startNativeVoice(){return 'ERR_NATIVE_TEST_DISABLED'},readNativePcm(){return f.active?(f.frames[f.index++]||''):''},stopNativePcm(){f.active=false}};"+
                "window.AndroidBridge=new Proxy(api,{get(target,key){if(Object.prototype.hasOwnProperty.call(overrides,key))return overrides[key];"+
                "const v=target[key];return typeof v==='function'?v.bind(target):v}});return JSON.stringify({armed:window.AndroidBridge!==api});");
            armed=state.optBoolean("armed");check.run(armed,"CORE_FIXTURE_CAPTURE_PROXY_ARMED");
            for(int cycle=0;cycle<3;cycle++){
                JSONObject current=script.run(METRICS);
                check.run(current.optBoolean("fixtureOnly")&&current.optInt("count")==cycle,"CORE_FIXTURE_DRAFT_UNCHANGED_"+cycle);
                JSONObject click=script.run("const b=document.querySelector('button[data-tooltip-id=\"input-send-button-record-tooltip\"]');"+
                    "if(b&&!b.disabled)b.click();return JSON.stringify({clicked:!!b&&!b.disabled});");
                check.run(click.optBoolean("clicked"),"CORE_FIXTURE_RECORD_BUTTON_"+cycle);
                long until=SystemClock.elapsedRealtime()+15000;boolean consumed=false;
                do{
                    current=script.run(METRICS);
                    if(current.optBoolean("error"))break;
                    if(current.optBoolean("recording")&&current.optInt("frames")>=current.optInt("total")&&current.optInt("total")>0){consumed=true;break;}
                    SystemClock.sleep(70);
                }while(SystemClock.elapsedRealtime()<until);
                check.run(consumed,"CORE_FIXTURE_AUDIO_DELIVERED_"+cycle);
                script.run("document.querySelector('button[data-tooltip-id=\"input-send-button-record-tooltip\"]').click();return '{}';");
                until=SystemClock.elapsedRealtime()+35000;boolean inserted=false;
                do{
                    current=script.run(METRICS);
                    if(current.optBoolean("error"))break;
                    if(current.optBoolean("fixtureOnly")&&current.optInt("count")==cycle+1){inserted=true;break;}
                    SystemClock.sleep(100);
                }while(SystemClock.elapsedRealtime()<until);
                check.run(inserted,"CORE_FIXTURE_REAL_TRANSCRIPT_IN_EDITOR_"+cycle);
                // Wait for end acknowledgement and the real idle button before restarting.
                until=SystemClock.elapsedRealtime()+35000;
                JSONObject idle;
                do{
                    idle=script.run("const b=document.querySelector('button[data-tooltip-id=\"input-send-button-record-tooltip\"]');"+
                        "return JSON.stringify({idle:!!b&&!b.disabled&&!document.querySelector('.agy-dictation-countdown')});");
                    if(idle.optBoolean("idle"))break;
                    SystemClock.sleep(100);
                }while(SystemClock.elapsedRealtime()<until);
                check.run(idle.optBoolean("idle"),"CORE_FIXTURE_FINALIZATION_SETTLED_"+cycle);
            }
            report.run("CORE_FIXTURE_3_REAL_CORE_TRANSCRIPTIONS","PASS");
        }finally{
            if(armed){
                JSONObject after=script.run(METRICS);
                if(after.optBoolean("recording"))script.run("document.querySelector('button[data-tooltip-id=\"input-send-button-record-tooltip\"]')?.click();return '{}';");
                script.run("const f=window.__agyCoreFixture;if(f){window.AndroidBridge=f.api;delete window.__agyCoreFixture}return '{}';");
                // Only remove the exact public fixture. Never erase unexpected or user text.
                if(after.optBoolean("fixtureOnly")&&after.optInt("count")>0){
                    clear.run();
                    check.run(script.run(METRICS).optBoolean("empty"),"CORE_FIXTURE_DRAFT_RESTORED");
                }
            }
        }
    }
}
