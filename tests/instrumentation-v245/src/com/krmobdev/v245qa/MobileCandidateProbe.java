package com.krmobdev.v245qa;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.Notification;
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
        notify(A,1,true,null,1);notify(B,1,true,null,2);SystemClock.sleep(6500);
        require(notification(A,21001)==null&&notification(B,21001)==null,"ANDROID_TERMINAL_CLEARS_PROGRESS");
        require(text(notification(A,21002)).startsWith("Ошибка"),"ANDROID_ERROR_OUTCOME");
        require(text(notification(B,21002)).startsWith("Отменено"),"ANDROID_CANCEL_OUTCOME_FIFO");
        report("ANDROID_NOTIFICATION_FIXTURES","PASS_SYNTHETIC_NATIVE_EVENTS");
    }
    private void captureComposer(){
        if(!capture||!activity.hasWindowFocus())return;
        try{
            android.graphics.Bitmap full=getUiAutomation().takeScreenshot();
            if(full==null)return;
            android.graphics.Bitmap small=android.graphics.Bitmap.createScaledBitmap(full,480,Math.round(full.getHeight()*480f/full.getWidth()),true);
            java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();small.compress(android.graphics.Bitmap.CompressFormat.JPEG,78,bytes);
            String data=android.util.Base64.encodeToString(bytes.toByteArray(),android.util.Base64.NO_WRAP);
            for(int i=0,part=0;i<data.length();i+=8000,part++)report("SCREENSHOT_PART_"+part,data.substring(i,Math.min(data.length(),i+8000)));
            small.recycle();if(small!=full)full.recycle();
        }catch(Exception e){report("SCREENSHOT","UNAVAILABLE");}
    }
    private void ui()throws Exception{
        JSONObject info=state("return JSON.stringify({composer:!!document.querySelector('.agy-composer')});");
        if(!info.optBoolean("composer")){
            state("const b=[...document.querySelectorAll('button')].filter(x=>/new conversation/i.test((x.getAttribute('aria-label')||'')+' '+x.title));if(b.length===1)b[0].click();return '{}';");
            info=waitState("return JSON.stringify({ready:!!document.querySelector('.agy-composer')});","ready");
            if(!info.optBoolean("ready")){report("ANDROID_MOBILE_UI","PENDING_ONBOARDING_OR_AUTH");return;}
        }
        require(activity.hasWindowFocus(),"ANDROID_DEV_WINDOW_FOCUS");
        captureComposer();
        info=state("const p=document.querySelector('.agy-composer'),b=[...p.querySelectorAll('button')].map(x=>x.getBoundingClientRect()).filter(r=>r.width&&r.height);return JSON.stringify({controls:b.length>0&&b.every(r=>r.width>=47.5&&r.height>=47.5),multiline:parseFloat(getComputedStyle(p.querySelector('[contenteditable=true]')).minHeight)>=36&&parseFloat(getComputedStyle(p.querySelector('[contenteditable=true]')).minHeight)<=48});");
        require(info.optBoolean("controls"),"ANDROID_48DP_CONTROLS");
        require(info.optBoolean("multiline"),"ANDROID_MULTILINE_SURFACE");
        info=state("const p=document.querySelector('.agy-composer-surface'),f=document.querySelector('.agy-composer-footer'),a=document.querySelector('.agy-composer-add'),b=document.querySelector('[data-testid=send-button]');const r=p.getBoundingClientRect(),fr=f.getBoundingClientRect(),ar=a.getBoundingClientRect(),br=b?.getBoundingClientRect();return JSON.stringify({height:r.height,footerHeight:fr.height,compact:r.height<=144&&fr.height<=56,sameRow:!!br&&Math.abs((ar.top+ar.height/2)-(br.top+br.height/2))<5});");
        report("ANDROID_EMPTY_COMPOSER_HEIGHT",info.optDouble("height"));
        report("ANDROID_FOOTER_HEIGHT",info.optDouble("footerHeight"));
        require(info.optBoolean("compact")&&info.optBoolean("sameRow"),"ANDROID_COMPACT_SINGLE_ROW");
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
        sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);SystemClock.sleep(500);
        info=state("return JSON.stringify({closed:!document.querySelector('dialog.agy-composer-sheet[open]')});");
        require(info.optBoolean("closed"),"ANDROID_BACK_CLOSES_SHEET");
        report("ANDROID_MOBILE_UI","PASS_LOCAL_FIXTURES_NO_SEND");
    }
    @Override public void onCreate(Bundle b){super.onCreate(b);capture="yes".equals(b.getString("capture"));start();}
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
            boolean uiOk=true;
            try{ui();}catch(Throwable e){uiOk=false;report("UI_PROBE_ERROR",e.getClass().getSimpleName());}
            notifications();ok=uiOk;
        }catch(Throwable e){report("PROBE_ERROR",e.getClass().getSimpleName());}
        finally{
            if(target!=null)cleanupNotifications();
            Bundle result=new Bundle();result.putString("summary",ok?"Probe completed; inspect PASS/PENDING flags":"FAIL; inspect flags");
            finish(ok?0:1,result);
        }
    }
}
