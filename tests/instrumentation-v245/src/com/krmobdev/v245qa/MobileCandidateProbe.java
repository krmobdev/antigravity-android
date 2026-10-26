package com.krmobdev.v245qa;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.service.notification.StatusBarNotification;
import org.json.JSONObject;
import org.json.JSONTokener;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Dev-only, no model requests; outputs flags/counts, never account/chat content. */
public final class MobileCandidateProbe extends Instrumentation {
    private static final String A="v245_probe_a", B="v245_probe_b";
    private boolean capture;
    private boolean micMode;
    private boolean micUiMode;
    private boolean micPcmUiMode;
    private boolean micVisualMode;
    private Context target;
    private Activity activity;
    private WebView web;
    private Object notifier;
    private Method observe;
    private void report(String key,Object value){Bundle b=new Bundle();b.putString("stream",key+"="+value);sendStatus(1,b);}
    private void require(boolean ok,String name){report(name,ok?"PASS":"FAIL");if(!ok)throw new AssertionError(name);}
    private Object field(Object object,String name)throws Exception {
        Class<?> cl=object instanceof Class?(Class<?>)object:object.getClass();
        for(Class<?> c=cl;c!=null;c=c.getSuperclass())try{
            Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(object instanceof Class?null:object);
        }catch(NoSuchFieldException ignored){}
        throw new NoSuchFieldException(name);
    }
    private WebView find(View v){
        if(v instanceof WebView)return(WebView)v;
        if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){WebView w=find(((ViewGroup)v).getChildAt(i));if(w!=null)return w;}
        return null;
    }
    private JSONObject js(String expression)throws Exception {
        CountDownLatch latch=new CountDownLatch(1);AtomicReference<String> result=new AtomicReference<>("");
        runOnMainSync(()->web.evaluateJavascript(expression,s->{result.set(s);latch.countDown();}));
        if(!latch.await(6,TimeUnit.SECONDS))throw new IllegalStateException("JS timeout");
        Object value=new JSONTokener(result.get()).nextValue();
        return value instanceof String?new JSONObject((String)value):new JSONObject(String.valueOf(value));
    }
    private JSONObject state(String body)throws Exception{return js("(function(){"+body+"})()");}
    private JSONObject waitState(String body,String key)throws Exception{
        JSONObject result=null;long until=SystemClock.elapsedRealtime()+10000;
        do{result=state(body);if(result.optBoolean(key))return result;SystemClock.sleep(150);}while(SystemClock.elapsedRealtime()<until);
        return result;
    }
    private void notify(String id,int status,boolean idle,String stage,int outcome)throws Exception{
        observe.invoke(notifier,id,status,idle,stage,outcome);
    }
    private StatusBarNotification notification(String id,int number){
        NotificationManager nm=(NotificationManager)target.getSystemService(Context.NOTIFICATION_SERVICE);
        for(StatusBarNotification n:nm.getActiveNotifications())if(id.equals(n.getTag())&&n.getId()==number)return n;
        return null;
    }
    private String text(StatusBarNotification n){return n==null?"":n.getNotification().extras.getString(Notification.EXTRA_TEXT,"");}
    private void cleanupNotifications(){
        try{
            if(notifier!=null){
                notify(A,1,true,null,0);notify(B,1,true,null,0);
                Object dispatcher=field(notifier.getClass(),"dispatcher");
                if(dispatcher!=null){Object queue=field(dispatcher,"queue");Method cancel=queue.getClass().getMethod("cancel",String.class);
                    runOnMainSync(()->{try{cancel.invoke(queue,A);cancel.invoke(queue,B);}catch(Exception ignored){}});
                }
            }
            NotificationManager nm=(NotificationManager)target.getSystemService(Context.NOTIFICATION_SERVICE);
            for(String id:new String[]{A,B})for(int n:new int[]{21001,21002})nm.cancel(id,n);
        }catch(Exception ignored){}
    }
    private void notifications()throws Exception{
        NotificationManager nm=(NotificationManager)target.getSystemService(Context.NOTIFICATION_SERVICE);
        if(Build.VERSION.SDK_INT>=33)try{getUiAutomation().grantRuntimePermission("com.agy.dev","android.permission.POST_NOTIFICATIONS");}catch(Exception ignored){}
        if(!nm.areNotificationsEnabled()){report("ANDROID_NOTIFICATION_FIXTURES","PENDING_PERMISSION");return;}
        Class<?> cl=Class.forName("com.agy.bridge.AgentNotifications",true,target.getClassLoader());
        notifier=cl.getConstructor(Context.class).newInstance(target);
        observe=cl.getMethod("observeNative",String.class,int.class,boolean.class,String.class,int.class);
        Intent home=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        target.startActivity(home);SystemClock.sleep(500);
        notify(A,2,false,"Читает исходники",0);notify(B,2,false,"Изменяет файлы",0);
        SystemClock.sleep(1100);notify(A,2,false,"Запускает тесты",0);SystemClock.sleep(1300);
        require(text(notification(A,21001)).equals("Запускает тесты"),"ANDROID_BACKGROUND_STAGE_A");
        require(text(notification(B,21001)).equals("Изменяет файлы"),"ANDROID_PARALLEL_STAGE_B");
        int count=0;for(StatusBarNotification n:nm.getActiveNotifications())if(A.equals(n.getTag())&&n.getId()==21001)count++;
        require(count==1,"ANDROID_SAME_NOTIFICATION_ID");
        if(Build.VERSION.SDK_INT>=26){
            StatusBarNotification active=notification(A,21001);
            NotificationChannel channel=active==null?null:nm.getNotificationChannel(active.getNotification().getChannelId());
            require(channel!=null&&channel.getImportance()<=NotificationManager.IMPORTANCE_LOW
                &&channel.getSound()==null&&!channel.shouldVibrate(), "ANDROID_PROGRESS_SILENT_CHANNEL");
        }
        notify(A,1,true,null,1);notify(B,1,true,null,2);SystemClock.sleep(6500);
        require(notification(A,21001)==null&&notification(B,21001)==null,"ANDROID_TERMINAL_CLEARS_PROGRESS");
        require(text(notification(A,21002)).startsWith("Ошибка"),"ANDROID_ERROR_OUTCOME");
        require(text(notification(B,21002)).startsWith("Отменено"),"ANDROID_CANCEL_OUTCOME_FIFO");
        report("ANDROID_NOTIFICATION_FIXTURES","PASS_SYNTHETIC_NATIVE_EVENTS");
    }
    private void captureComposer(String stage){
        if(!capture||!activity.hasWindowFocus())return;
        try{
            android.graphics.Bitmap full=getUiAutomation().takeScreenshot();
            if(full==null)return;
            android.graphics.Bitmap small=android.graphics.Bitmap.createScaledBitmap(full,480,Math.round(full.getHeight()*480f/full.getWidth()),true);
            java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();small.compress(android.graphics.Bitmap.CompressFormat.JPEG,78,bytes);
            String data=android.util.Base64.encodeToString(bytes.toByteArray(),android.util.Base64.NO_WRAP);
            for(int i=0,part=0;i<data.length();i+=8000,part++)report("SCREENSHOT_"+stage+"_PART_"+part,data.substring(i,Math.min(data.length(),i+8000)));
            small.recycle();if(small!=full)full.recycle();
        }catch(Exception e){report("SCREENSHOT","UNAVAILABLE");}
    }
    // Stock drawer primitives keep DOM nodes mounted even when closed.
    // A nonzero rectangle alone must never be treated as an open sheet.
    private JSONObject stockSheets()throws Exception{
        return state("const a=[...document.querySelectorAll('[data-testid=bottom-sheet]')];const v=a.filter(e=>{const r=e.getBoundingClientRect(),c=getComputedStyle(e);return c.display!=='none'&&c.visibility!=='hidden'&&Number(c.opacity)>0&&!e.closest('[aria-hidden=true]')&&r.height>0&&r.bottom>0&&r.top<innerHeight;});return JSON.stringify({total:a.length,visible:v.length});");
    }
    private void modelMenus()throws Exception{
        JSONObject info=state("const b=document.querySelector('[data-testid=model-selector-trigger]');b?.click();return JSON.stringify({available:!!b});");
        if(!info.optBoolean("available")){report("ANDROID_MODEL_MENUS","PENDING_UNAVAILABLE");return;}
        info=waitState("const p=document.querySelector('[data-testid=model-selector-panel]'),d=p?.closest('[data-testid=bottom-sheet]');return JSON.stringify({ready:!!d&&d.getBoundingClientRect().height>0,rounded:!!d&&parseFloat(getComputedStyle(d).borderTopLeftRadius)>=24,rows:!!p&&[...p.querySelectorAll('[role=menuitem]')].filter(x=>x.getBoundingClientRect().height>0).every(x=>x.getBoundingClientRect().height>=47.5)});","ready");
        require(info.optBoolean("ready")&&info.optBoolean("rounded")&&info.optBoolean("rows"),"ANDROID_MODEL_SHARED_STYLE");
        SystemClock.sleep(500);captureComposer("MODEL");
        info=state("const g=document.querySelector('[data-testid=model-selector-effort-group]'),r=g?.closest('button,[role=menuitem],[role=button]');r?.click();return JSON.stringify({available:!!r});");
        if(info.optBoolean("available")){
            info=waitState("const rows=[...document.querySelectorAll('[data-testid=model-selector-effort-option]')].filter(x=>x.getBoundingClientRect().height>0);return JSON.stringify({ready:rows.length>0,sized:rows.every(x=>(x.closest('button,[role=menuitem],[role=menuitemradio]')||x).getBoundingClientRect().height>=47.5)});","ready");
            require(info.optBoolean("ready")&&info.optBoolean("sized"),"ANDROID_EFFORT_SHARED_STYLE");
            SystemClock.sleep(400);captureComposer("EFFORT");
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);SystemClock.sleep(450);
        }
        // Close only visible sheets; stock drawers can remain in the DOM.
        for(int i=0;i<5;i++){
            info=stockSheets();
            report("ANDROID_STOCK_BEFORE_USAGE_"+i,info.optInt("visible")+"/"+info.optInt("total"));
            if(info.optInt("visible")==0)break;
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);SystemClock.sleep(550);
        }
        report("ANDROID_AFTER_STOCK_BACK_FOCUS",activity.hasWindowFocus());
        state("document.querySelector('[data-testid=model-selector-trigger]')?.click();return '{}';");SystemClock.sleep(500);
        info=state("const p=document.querySelector('[data-testid=model-selector-panel]'),r=[...(p?.querySelectorAll('[role=menuitem],button')||[])].find(x=>/View Usage/.test(x.textContent));r?.click();return JSON.stringify({available:!!r});");
        if(info.optBoolean("available")){
            SystemClock.sleep(700);
            info=state("const d=document.querySelector('[data-testid=bottom-sheet]');return JSON.stringify({rounded:!!d&&parseFloat(getComputedStyle(d).borderTopLeftRadius)>=24});");
            require(info.optBoolean("rounded"),"ANDROID_USAGE_SHARED_STYLE");captureComposer("USAGE");
        }else report("ANDROID_USAGE_SHARED_STYLE","PENDING_UNAVAILABLE");
        for(int i=0;i<5;i++){
            info=stockSheets();
            report("ANDROID_STOCK_AFTER_USAGE_"+i,info.optInt("visible")+"/"+info.optInt("total"));
            if(info.optInt("visible")==0)break;
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);SystemClock.sleep(550);
        }
        info=stockSheets();
        report("ANDROID_STOCK_FINAL",info.optInt("visible")+"/"+info.optInt("total"));
        require(info.optInt("visible")==0,"ANDROID_BACK_CLOSES_STOCK_SHEETS");
        info=state("return JSON.stringify({secure:window.isSecureContext,audio:typeof navigator.mediaDevices?.getUserMedia==='function',recorder:typeof MediaRecorder==='function'});");
        require(info.optBoolean("secure")&&info.optBoolean("audio")&&info.optBoolean("recorder"),"ANDROID_MICROPHONE_BROWSER_CAPABILITY");
        require(target.getPackageManager().getPackageInfo(target.getPackageName(),android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions!=null,"ANDROID_MANIFEST_READABLE");
        String[] permissions=target.getPackageManager().getPackageInfo(target.getPackageName(),android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions;
        require(java.util.Arrays.asList(permissions).contains("android.permission.RECORD_AUDIO"),"ANDROID_MICROPHONE_DECLARED");
        report("ANDROID_LIVE_VOICE_RECORDING","PENDING_USER_TAP_NO_AUDIO_CAPTURED_BY_PROBE");
    }
    private boolean keyboardVisible(){
        AtomicReference<Boolean> visible=new AtomicReference<>(false);
        runOnMainSync(()->{
            if(Build.VERSION.SDK_INT>=30){android.view.WindowInsets insets=activity.getWindow().getDecorView().getRootWindowInsets();visible.set(insets!=null&&insets.isVisible(android.view.WindowInsets.Type.ime()));}
        });return visible.get();
    }
    private void keyboard()throws Exception{
        state("document.querySelector('.agy-composer [contenteditable=true]').focus();return '{}';");
        runOnMainSync(()->{web.requestFocus();((android.view.inputmethod.InputMethodManager)target.getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(web,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);});
        long until=SystemClock.elapsedRealtime()+3000;
        while(!keyboardVisible()&&SystemClock.elapsedRealtime()<until)SystemClock.sleep(150);
        if(!keyboardVisible()){report("ANDROID_IME_LAYOUT","PENDING_IME_NOT_SHOWN");return;}
        SystemClock.sleep(350);
        JSONObject info=state("const r=document.querySelector('.agy-composer-surface').getBoundingClientRect(),v=window.visualViewport;return JSON.stringify({fits:!!v&&r.bottom<=v.offsetTop+v.height+2&&r.top>=v.offsetTop-2});");
        require(info.optBoolean("fits"),"ANDROID_IME_LAYOUT");
        sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);SystemClock.sleep(400);
        require(!keyboardVisible(),"ANDROID_BACK_DISMISSES_IME");
    }
    private void ui()throws Exception{
        JSONObject info=state("return JSON.stringify({composer:!!document.querySelector('.agy-composer')});");
        if(!info.optBoolean("composer")){
            state("const b=[...document.querySelectorAll('button')].filter(x=>/new conversation/i.test((x.getAttribute('aria-label')||'')+' '+x.title));if(b.length===1)b[0].click();return '{}';");
            info=waitState("return JSON.stringify({ready:!!document.querySelector('.agy-composer')});","ready");
            if(!info.optBoolean("ready")){report("ANDROID_MOBILE_UI","PENDING_ONBOARDING_OR_AUTH");return;}
        }
        require(activity.hasWindowFocus(),"ANDROID_DEV_WINDOW_FOCUS");
        info=state("const p=document.querySelector('.agy-composer'),b=[...p.querySelectorAll('button')].map(x=>x.getBoundingClientRect()).filter(r=>r.width&&r.height);return JSON.stringify({controls:b.length>0&&b.every(r=>r.width>=47.5&&r.height>=47.5),multiline:parseFloat(getComputedStyle(p.querySelector('[contenteditable=true]')).minHeight)>=36&&parseFloat(getComputedStyle(p.querySelector('[contenteditable=true]')).minHeight)<=48});");
        require(info.optBoolean("controls"),"ANDROID_48DP_CONTROLS");
        require(info.optBoolean("multiline"),"ANDROID_MULTILINE_SURFACE");
        info=state("const p=document.querySelector('.agy-composer-surface'),f=document.querySelector('.agy-composer-footer'),a=document.querySelector('.agy-composer-add'),b=document.querySelector('[data-testid=send-button]');const r=p.getBoundingClientRect(),fr=f.getBoundingClientRect(),ar=a.getBoundingClientRect(),br=b?.getBoundingClientRect();return JSON.stringify({height:r.height,footerHeight:fr.height,compact:r.height<=144&&fr.height<=56,sameRow:!!br&&Math.abs((ar.top+ar.height/2)-(br.top+br.height/2))<5});");
        report("ANDROID_EMPTY_COMPOSER_HEIGHT",info.optDouble("height"));
        report("ANDROID_FOOTER_HEIGHT",info.optDouble("footerHeight"));
        require(info.optBoolean("compact")&&info.optBoolean("sameRow"),"ANDROID_COMPACT_SINGLE_ROW");
        info=state("const b=document.querySelector('.agy-composer button[data-tooltip-id=input-send-button-record-tooltip]');const r=b?.getBoundingClientRect();return JSON.stringify({micVisible:!!r&&r.width>=40&&r.height>=40,secure:window.isSecureContext,webMic:typeof navigator.mediaDevices?.getUserMedia==='function',recorder:typeof MediaRecorder==='function',webm:typeof MediaRecorder==='function'&&MediaRecorder.isTypeSupported?.('audio/webm;codecs=opus')});");
        report("ANDROID_MICROPHONE_UI_VISIBLE",info.optBoolean("micVisible")?"PASS":"PENDING_UNAVAILABLE");
        report("ANDROID_MICROPHONE_CAPABILITIES","secure="+info.optBoolean("secure")+" capture="+info.optBoolean("webMic")+" recorder="+info.optBoolean("recorder")+" webm="+info.optBoolean("webm"));
        // Never turn on the microphone or access actual audio in automated UI tests.

        info=state("const p=document.querySelector('.agy-composer');return JSON.stringify({busy:!!p.querySelector('[contenteditable=true]')?.textContent.trim()||!!p.querySelector('.agy-attachment-card')});");
        boolean busy=info.optBoolean("busy");if(busy)capture=false;
        if(busy){report("ANDROID_MUTATING_UI_FIXTURES","PENDING_USER_DRAFT_PRESERVED");return;}
        info=state("const p=document.querySelector('.agy-composer input[type=file]'),d=new DataTransfer();d.items.add(new File(['<script>window.__agyProbeExecuted=true</script>'], 'v245-probe.md',{type:'text/markdown'}));d.items.add(new File([Uint8Array.from(atob('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aPZ0AAAAASUVORK5CYII='),c=>c.charCodeAt(0))],'v245-probe.png',{type:'image/png'}));if(p){p.files=d.files;p.dispatchEvent(new Event('change',{bubbles:true}));}return JSON.stringify({selected:!!p});");
        require(info.optBoolean("selected"),"ANDROID_ATTACHMENT_INPUT");
        info=waitState("return JSON.stringify({ready:document.querySelectorAll('.agy-attachment-card').length===2});","ready");
        require(info.optBoolean("ready"),"ANDROID_MULTIPLE_ATTACHMENTS");
        state("document.querySelector('[data-testid=input-attachment-open]').click();return '{}';");
        info=waitState("const p=document.querySelector('[data-testid=attachment-safe-text]');return JSON.stringify({ready:!!p&&p.textContent.includes('<script>'),safe:!window.__agyProbeExecuted,cards:document.querySelectorAll('.agy-attachment-card').length});","ready");
        require(info.optBoolean("ready")&&info.optBoolean("safe")&&info.optInt("cards")==2,"ANDROID_SAFE_TEXT_PREVIEW");
        state("document.querySelector('[aria-label=\"Закрыть просмотр\"]').click();return '{}';");SystemClock.sleep(250);
        state("document.querySelectorAll('[data-testid=input-attachment-open]')[1].click();return '{}';");
        info=waitState("const i=document.querySelector('.agy-attachment-full-media');return JSON.stringify({ready:!!i&&i.tagName==='IMG'&&i.src.startsWith('blob:')});","ready");
        require(info.optBoolean("ready"),"ANDROID_IMAGE_FULLSCREEN");
        state("document.querySelector('[aria-label=\"Закрыть просмотр\"]').click();return '{}';");SystemClock.sleep(250);
        state("document.querySelector('[data-testid=input-attachment-remove]').click();return '{}';");
        info=waitState("return JSON.stringify({ready:document.querySelectorAll('.agy-attachment-card').length===1});","ready");
        require(info.optBoolean("ready"),"ANDROID_REMOVE_ONE_ATTACHMENT");
        state("document.querySelector('[data-testid=input-attachment-remove]').click();return '{}';");
        state("document.querySelector('.agy-composer-add').click();return '{}';");
        info=waitState("return JSON.stringify({ready:!!document.querySelector('dialog.agy-composer-sheet[open]')});","ready");
        require(info.optBoolean("ready"),"ANDROID_PLUS_SHEET");
        SystemClock.sleep(500);captureComposer("SHEET");
        info=state("return JSON.stringify({count:document.querySelectorAll('.agy-composer-extra-controls button,.agy-composer-extra-controls [role=button]').length});");
        report("ANDROID_EXTRA_SETTINGS_CONTROLS",info.optInt("count"));
        info=state("const e=document.querySelector('.agy-composer-extra-controls'),body=e?.lastElementChild;const empty=!body||!body.textContent.trim()&&!body.querySelector('button,input,[role=button]');return JSON.stringify({ok:!empty||!e||getComputedStyle(e).display==='none'});");
        require(info.optBoolean("ok"),"ANDROID_NO_EMPTY_SETTINGS_SECTION");
        sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);SystemClock.sleep(500);
        info=state("return JSON.stringify({closed:!document.querySelector('dialog.agy-composer-sheet[open]')});");
        require(info.optBoolean("closed"),"ANDROID_BACK_CLOSES_SHEET");
        keyboard();
        report("ANDROID_MOBILE_UI","PASS_LOCAL_FIXTURES_NO_SEND");
        SystemClock.sleep(800);captureComposer("COMPOSER");
        // Inspect stock menus last so a Back/navigation fault cannot hide attachment results.
        modelMenus();
    }
    /**
     * Verify real button routing with both microphone-access paths stubbed
     * BEFORE click. No real audio input is ever opened by this test.
     */
    private void microphoneButtonRouting()throws Exception {
        JSONObject before=state("const b=document.querySelector('button[data-tooltip-id=\\\"input-send-button-record-tooltip\\\"]');return JSON.stringify({exists:!!b,disabled:!!b?.disabled});");
        report("VOICE_MIC_BUTTON_EXISTS",before.optBoolean("exists"));
        report("VOICE_MIC_BUTTON_DISABLED",before.optBoolean("disabled"));
        require(before.optBoolean("exists")&&!before.optBoolean("disabled"),"VOICE_MIC_UI_BUTTON_READY");
        JSONObject armed=state("window.__agyMicTest={api:window.AndroidBridge,original:navigator.mediaDevices?.getUserMedia,calls:0,webCalls:0};const t=window.__agyMicTest;window.AndroidBridge={startNativeVoice(){t.calls++;return 'ERR_TEST_ROUTE'}};navigator.mediaDevices.getUserMedia=()=>{t.webCalls++;return Promise.reject({name:'StubbedByTest'})};return JSON.stringify({patched:window.AndroidBridge!==t.api&&navigator.mediaDevices.getUserMedia!==t.original});");
        require(armed.optBoolean("patched"),"VOICE_MIC_TEST_INTERCEPTS_ALL_CAPTURE");
        try {
            state("document.querySelector('button[data-tooltip-id=\\\"input-send-button-record-tooltip\\\"]').click();return '{}';");
            SystemClock.sleep(900);
            JSONObject result=state("return JSON.stringify({nativeCalls:window.__agyMicTest.calls,webCalls:window.__agyMicTest.webCalls,visible:document.body.innerText.includes('ERR_TEST_ROUTE')});");
            report("VOICE_BUTTON_NATIVE_CALLS",result.optInt("nativeCalls",-1));
            report("VOICE_BUTTON_BROWSER_CALLS",result.optInt("webCalls",-1));
            report("VOICE_BUTTON_NATIVE_ERROR_VISIBLE",result.optBoolean("visible"));
            require(result.optInt("nativeCalls")==1&&result.optInt("webCalls")==0&&result.optBoolean("visible"),"VOICE_BUTTON_NATIVE_ROUTE");
        } finally {
            state("const t=window.__agyMicTest;if(t){window.AndroidBridge=t.api;navigator.mediaDevices.getUserMedia=t.original;delete window.__agyMicTest;}return '{}';");
        }
    }
    /** Test the original speech-to-text button without recording audio. */
    private void microphonePcmButtonRouting()throws Exception {
        JSONObject before=state("const b=document.querySelector('button[data-tooltip-id=\\\"input-send-button-record-tooltip\\\"]');return JSON.stringify({exists:!!b,disabled:!!b?.disabled});");
        require(before.optBoolean("exists")&&!before.optBoolean("disabled"),"VOICE_STT_BUTTON_VISIBLE");
        JSONObject armed=state("const api=window.AndroidBridge,orig=navigator.mediaDevices?.getUserMedia;window.__agyPcmTest={api,orig,pcm:0,file:0,browser:0};const t=window.__agyPcmTest;window.AndroidBridge={startNativePcm(){t.pcm++;return 'ERR_NATIVE_TEST'},readNativePcm(){return ''},stopNativePcm(){},startNativeVoice(){t.file++;return 'ERR_NATIVE_TEST'}};navigator.mediaDevices.getUserMedia=()=>{t.browser++;return Promise.reject({name:'StubbedByTest'})};return JSON.stringify({armed:window.AndroidBridge!==api&&navigator.mediaDevices.getUserMedia!==orig});");
        require(armed.optBoolean("armed"),"VOICE_STT_MOCK_INTERCEPTS");
        try{
            state("document.querySelector('button[data-tooltip-id=\\\"input-send-button-record-tooltip\\\"]').click();return '{}';");
            SystemClock.sleep(800);
            JSONObject data=state("const t=window.__agyPcmTest;return JSON.stringify({pcm:t.pcm,file:t.file,browser:t.browser});");
            report("VOICE_STT_NATIVE_PCM_CALLS",data.optInt("pcm",-1));
            report("VOICE_STT_ATTACHMENT_CALLS",data.optInt("file",-1));
            report("VOICE_STT_WEB_CAPTURE_CALLS",data.optInt("browser",-1));
            require(data.optInt("pcm")==1&&data.optInt("file")==0&&data.optInt("browser")==0,"VOICE_STT_NATIVE_ROUTING");
        }finally{
            state("const t=window.__agyPcmTest;if(t){window.AndroidBridge=t.api;navigator.mediaDevices.getUserMedia=t.orig;delete window.__agyPcmTest;}return '{}';");
        }
    }
    /** Inspect actual WebView CSS states; do not access any microphone. */
    private void microphoneVisualStyles()throws Exception {
        JSONObject r=state("const mic=document.querySelector('button[data-tooltip-id=\\\"input-send-button-record-tooltip\\\"]'),root=mic?.closest('.agy-composer');if(!mic||!root)return JSON.stringify({found:false,mic:!!mic,root:!!root});const sample=document.createElement('button');sample.type='button';sample.setAttribute('data-tooltip-id','input-send-button-record-tooltip');sample.className='bg-red-500 text-white';sample.setAttribute('aria-label','Stop recording');root.appendChild(sample);const normal=getComputedStyle(mic),glyph=getComputedStyle(mic,'::after'),active=getComputedStyle(sample),stop=getComputedStyle(sample,'::after');const out={found:true,width:normal.width,height:normal.height,mask:glyph.webkitMaskImage||glyph.maskImage||'',bg:active.backgroundColor,stopBg:stop.backgroundColor,realClass:mic.className.includes('bg-red-500')};sample.remove();return JSON.stringify(out);");
        report("VOICE_STYLE_STRUCTURE",r.toString());
        require(r.optBoolean("found"),"VOICE_STYLE_BUTTON_FOUND");
        report("VOICE_STYLE_MIC_SIZE",r.optString("width")+"x"+r.optString("height"));
        report("VOICE_STYLE_STOP_BACKGROUND",r.optString("bg"));
        require("48px".equals(r.optString("width"))&&"48px".equals(r.optString("height")),"VOICE_STYLE_TOUCH_SIZE");
        require(r.optString("mask").contains("svg"),"VOICE_STYLE_MIC_GLYPH");
        require(!"rgb(239, 68, 68)".equals(r.optString("bg")),"VOICE_STYLE_NO_RED_DISK");
        require("rgb(220, 69, 75)".equals(r.optString("stopBg")),"VOICE_STYLE_STOP_ICON");
    }
    private void microphoneCapabilities()throws Exception {
        JSONObject build=state("return JSON.stringify({build:window.__AGY_AUDIO_DIAG_BUILD__||'STALE_BUNDLE'});");
        report("VOICE_WEBVIEW_BUNDLE",build.optString("build","STALE_BUNDLE"));
        require("2026-10-26-native-primary-v6".equals(build.optString("build")),"VOICE_WEBVIEW_FRESH_BUNDLE");
        JSONObject v=state("return JSON.stringify({secure:window.isSecureContext,hasGetUserMedia:typeof navigator.mediaDevices?.getUserMedia==='function',hasMediaRecorder:typeof MediaRecorder==='function',webm:typeof MediaRecorder==='function'&&MediaRecorder.isTypeSupported('audio/webm;codecs=opus'),mp4:typeof MediaRecorder==='function'&&MediaRecorder.isTypeSupported('audio/mp4')});");
        JSONObject nativeBridge=state("const api=window.AndroidBridge||window.Android;return JSON.stringify({token:typeof window.__AG_NATIVE_BRIDGE_TOKEN__==='string'&&window.__AG_NATIVE_BRIDGE_TOKEN__.length===64,bridge:!!api,start:typeof api?.startNativeVoice==='function',stop:typeof api?.stopNativeVoice==='function',cancel:typeof api?.cancelNativeVoice==='function',helper:typeof agyNativeMicStart==='function'});");
        report("VOICE_NATIVE_BRIDGE",nativeBridge.optBoolean("bridge"));
        report("VOICE_NATIVE_TOKEN",nativeBridge.optBoolean("token"));
        report("VOICE_NATIVE_START_METHOD",nativeBridge.optBoolean("start"));
        report("VOICE_NATIVE_STOP_METHOD",nativeBridge.optBoolean("stop"));
        report("VOICE_NATIVE_CANCEL_METHOD",nativeBridge.optBoolean("cancel"));
        report("VOICE_NATIVE_HELPER",nativeBridge.optBoolean("helper"));
        require(nativeBridge.optBoolean("token")&&nativeBridge.optBoolean("bridge")&&nativeBridge.optBoolean("start")&&nativeBridge.optBoolean("stop")&&nativeBridge.optBoolean("cancel")&&nativeBridge.optBoolean("helper"),"VOICE_NATIVE_BRIDGE_READY");
        JSONObject ready=state("const api=window.AndroidBridge||window.Android;return JSON.stringify({status:api.nativeVoiceReadiness(window.__AG_NATIVE_BRIDGE_TOKEN__)});");
        report("VOICE_NATIVE_READINESS",ready.optString("status","MISSING"));
        JSONObject pcm=state("const api=window.AndroidBridge||window.Android;return JSON.stringify({start:typeof api.startNativePcm==='function',read:typeof api.readNativePcm==='function',stop:typeof api.stopNativePcm==='function',status:api.nativePcmReadiness(window.__AG_NATIVE_BRIDGE_TOKEN__)});");
        report("VOICE_NATIVE_PCM_START",pcm.optBoolean("start"));
        report("VOICE_NATIVE_PCM_READ",pcm.optBoolean("read"));
        report("VOICE_NATIVE_PCM_STOP",pcm.optBoolean("stop"));
        report("VOICE_NATIVE_PCM_READINESS",pcm.optString("status","MISSING"));
        require(pcm.optBoolean("start")&&pcm.optBoolean("read")&&pcm.optBoolean("stop")
            &&"READY".equals(pcm.optString("status")),"VOICE_NATIVE_PCM_READY");
        report("VOICE_SECURE_CONTEXT",v.optBoolean("secure"));
        report("VOICE_GET_USER_MEDIA",v.optBoolean("hasGetUserMedia"));
        report("VOICE_MEDIA_RECORDER",v.optBoolean("hasMediaRecorder"));
        report("VOICE_WEBM_OPUS",v.optBoolean("webm"));
        report("VOICE_MP4",v.optBoolean("mp4"));
        AtomicReference<String> currentUrl=new AtomicReference<>("");
        runOnMainSync(()->currentUrl.set(web.getUrl()));
        java.net.URI u=new java.net.URI(currentUrl.get());
        String origin=u.getScheme()+"://"+u.getHost()+":"+u.getPort();
        ClassLoader runtime=target.getClassLoader();
        Class<?> state=Class.forName("com.agy.core.CoreRuntimeState",true,runtime);
        String actualServer=String.valueOf(state.getField("serverUrl").get(null));
        Class<?> allowed=Class.forName("com.agy.ui.webview.WebAudioOrigin",true,runtime);
        boolean trusted=(Boolean)allowed.getMethod("allowed",String.class,String.class).invoke(null,origin,actualServer);
        report("VOICE_TRUSTED_ORIGIN",trusted);
        report("VOICE_ANDROID_RECORD_AUDIO_GRANTED",activity.checkSelfPermission("android.permission.RECORD_AUDIO")==android.content.pm.PackageManager.PERMISSION_GRANTED);
        // Probe MediaRecorder on a synthetic, silent output-only audio stream.
        // Never request microphone permission or access an audioinput device.
        state("window.__agySynth={done:false,result:'pending',bytes:0};try{const C=window.AudioContext||window.webkitAudioContext;const ctx=new C(),osc=ctx.createOscillator(),dest=ctx.createMediaStreamDestination();osc.connect(dest);const mime=MediaRecorder.isTypeSupported('audio/webm;codecs=opus')?'audio/webm;codecs=opus':'';const rec=new MediaRecorder(dest.stream,mime?{mimeType:mime}:{});rec.ondataavailable=e=>{window.__agySynth.bytes+=e.data.size};rec.onerror=e=>{window.__agySynth.done=true;window.__agySynth.result=e?.error?.name||'RecorderError'};rec.onstop=()=>{window.__agySynth.done=true;window.__agySynth.result=window.__agySynth.bytes>0?'PASS':'EMPTY';osc.stop();ctx.close()};osc.start();rec.start(100);setTimeout(()=>{if(rec.state!=='inactive')rec.stop()},1000)}catch(e){window.__agySynth.done=true;window.__agySynth.result=e?.name||'UnknownError'}return '{}';");
        JSONObject synthetic=waitState("return JSON.stringify(window.__agySynth||{done:false})","done");
        report("VOICE_SYNTHETIC_RECORDER",synthetic.optString("result","PENDING"));
    }
    @Override public void onCreate(Bundle b){super.onCreate(b);capture="yes".equals(b.getString("capture"));micMode="mic".equals(b.getString("mode"));micUiMode="mic-ui".equals(b.getString("mode"));micPcmUiMode="mic-pcm-ui".equals(b.getString("mode"));micVisualMode="mic-visual".equals(b.getString("mode"));start();}
    @Override public void onStart(){
        boolean ok=false;
        try{
            target=getTargetContext();require("com.agy.dev".equals(target.getPackageName()),"TARGET_DEV_ONLY");
            Intent launch=target.getPackageManager().getLaunchIntentForPackage("com.agy.dev");launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity=startActivitySync(launch);
            // Dismiss first-launch settings/permission surfaces without granting broad storage access.
            for(int i=0;i<3&&!activity.hasWindowFocus();i++){sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);SystemClock.sleep(700);}
            long until=SystemClock.elapsedRealtime()+45000;
            do{
                Object current=field(Class.forName("com.agy.ui.MainActivity",true,target.getClassLoader()),"activeActivity");
                if(current instanceof Activity)activity=(Activity)current;
                final Activity host=activity;runOnMainSync(()->web=find(host.getWindow().getDecorView()));
                if(web!=null&&Boolean.TRUE.equals(field(activity,"webUiReady")))break;
                SystemClock.sleep(250);
            }while(SystemClock.elapsedRealtime()<until);
            require(web!=null,"ANDROID_WEBVIEW_PRESENT");
            if(micMode){microphoneCapabilities();ok=true;return;}
            if(micUiMode){microphoneButtonRouting();ok=true;return;}
            if(micPcmUiMode){microphonePcmButtonRouting();ok=true;return;}
            if(micVisualMode){microphoneVisualStyles();ok=true;return;}
            boolean uiOk=true;
            try{ui();}catch(Throwable e){uiOk=false;report("UI_PROBE_ERROR",e.getClass().getSimpleName());
                report("UI_PROBE_DETAIL",String.valueOf(e.getMessage()).replaceAll("[^a-zA-Z0-9_ .:-]","").substring(0,Math.min(100,String.valueOf(e.getMessage()).replaceAll("[^a-zA-Z0-9_ .:-]","").length()))); }
            notifications();ok=uiOk;
        }catch(Throwable e){report("PROBE_ERROR",e.getClass().getSimpleName());}
        finally{
            if(target!=null)cleanupNotifications();
            Bundle result=new Bundle();result.putString("summary",ok?"Probe completed; inspect PASS/PENDING flags":"FAIL; inspect flags");
            finish(ok?0:1,result);
        }
    }
}
