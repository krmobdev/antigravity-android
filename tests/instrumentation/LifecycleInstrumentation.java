package com.agy.diagtests;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.service.notification.StatusBarNotification;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.KeyEvent;
import android.webkit.WebView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Controlled, test-only state sequence through actual React Redux store,
 * Android lifecycle and native PendingIntent. No message is sent to Core.
 * Target package com.agy.dev MUST be built with AGY_SIDEBAR_E2E=1.
 */
public final class LifecycleInstrumentation extends Instrumentation {
    private static final String TAG="AGY_LIFECYCLE_TEST";
    private static final String PREFS="agy_native_task_observer";
    private static final String PREFIX="native_completed_at:";
    private final StringBuilder log=new StringBuilder();
    private Activity activity;
    private WebView web;
    private String targetId;
    private boolean oldSaved;
    private long oldFinished;
    private int failed, inconclusive;

    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){
        super.onStart();
        Bundle result=new Bundle();
        try {
            info("START_REAL_E2E target=com.agy.dev fixture=Redux+native_notification");
            Context ctx=getTargetContext();
            Intent launch=ctx.getPackageManager().getLaunchIntentForPackage(ctx.getPackageName());
            if(launch==null)throw new Exception("Dev app launch not found");
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
            activity=startActivitySync(launch);
            waitForIdleSync();
            web=find(activity.getWindow().getDecorView());
            if(web==null)throw new Exception("No real WebView");
            String ready=null;
            for(int i=0;i<45;i++){
                ready=eval("Boolean(window.__AGY_E2E_FIXTURE__ && document.querySelector('[data-testid=conversation-row-sidebar]'))");
                if("true".equals(ready))break;
                SystemClock.sleep(400);
            }
            if(!"true".equals(ready)){
                info("NOT_RUN fixture not present in installed APK; do not claim E2E");
                inconclusive++;
                returnResult(result);
                return;
            }
            String idRaw=eval("document.querySelector('[data-testid=conversation-row-sidebar]')?.getAttribute('data-cascade-id') || ''");
            targetId=new JSONArray("["+idRaw+"]").getString(0);
            if(!targetId.matches("[A-Za-z0-9_+:-]{1,128}"))throw new Exception("bad fixture conversationId");
            oldFinished=ctx.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getLong(PREFIX+targetId,0);
            oldSaved=true;
            info("TARGET suffix="+shortId()+" saved_native_completion="+(oldFinished>0));
            String enable=eval("window.__AGY_E2E_ENABLED__=true; window.__AGY_E2E_FIXTURE__.snapshot("+jsId()+")");
            info("INITIAL_STORE "+clip(enable));
            // Stable run, no genuine agent or network: normal Redux dispatch,
            // native saved completion and the app's own AgentNotifications code.
            for(int cycle=0;cycle<9;cycle++){
                int order=cycle%3;
                clearFinished();
                callObserve(2,false);
                String setRunning=inject(2,true);
                if(!"ok".equals(setRunning))throw new Exception("fixture RUNNING "+setRunning);
                SystemClock.sleep(220);
                String runSnapshot=dom("RUNNING cycle="+cycle+" order="+order);
                if(!runSnapshot.contains("\"spinner\":true")){
                    info("NOT_REPRODUCED_RUNNING_SPINNER cycle="+cycle+" DOM="+clip(runSnapshot));
                    inconclusive++;
                }

                // Opening a real existing chat, via the actual sidebar link.
                String clicked=eval("(function(){var row=document.querySelector('[data-testid=conversation-row-sidebar]');var a=row?.querySelector('a[href]');if(!a)return 'no_link';a.click();return 'clicked'})()");
                SystemClock.sleep(360);
                String route=eval("location.pathname");
                info("OPEN cycle="+cycle+" click="+clicked+" route="+route);
                if(!route.contains("/c/")){
                    inconclusive++;
                    info("INCONCLUSIVE_NO_CHAT_ROUTE");break;
                }

                // Pause/resume repeatedly while Core (simulated) can finish.
                runOnMainSync(()->activity.moveTaskToBack(true));
                SystemClock.sleep(350+(order*175));
                if(order==0) finishNative(cycle);
                // Actual notification arrival must happen while app is suspended
                // for the first mode; in the other modes finish is delayed.
                runOnMainSync(()->activity.startActivity(launch));
                SystemClock.sleep(320);
                if(order==1)finishNative(cycle);

                if(cycle%3==0){
                    // Same immutable PendingIntent used by user notification tap,
                    // triggered via NotificationManager rather than fake router JS.
                    clickNativeFinishedNotification();
                }
                if(order==2)finishNative(cycle);
                SystemClock.sleep(250);
                dom("CHAT_AFTER_NOTIFICATION cycle="+cycle);

                // The app uses SPA links; browser-history and Android Back are
                // both attempted, neither is silently replaced by a fake route.
                eval("window.history.back(); true");
                SystemClock.sleep(420);
                String backRoute=eval("location.pathname");
                if(backRoute.contains("/c/")){
                    sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
                    SystemClock.sleep(470);
                    backRoute=eval("location.pathname");
                }
                info("BACK cycle="+cycle+" route="+backRoute);
                if(backRoute.contains("/c/")){
                    inconclusive++;
                    info("INCONCLUSIVE_BACK_NOT_TO_LIST cycle="+cycle);
                    break;
                }

                String before=dom("LIST_FIRST_RETURN cycle="+cycle);
                SystemClock.sleep(210);
                // Different deterministic stream orderings relative to navigation.
                if(order==0) {
                    inject(1,false); // late summary confirms idle
                }else if(order==1) {
                    inject(2,true); // deliberately stale summary after native finish
                }else{
                    inject(1,false);
                    inject(2,true); // reversed same-batch ordering
                }
                SystemClock.sleep(290);
                String after=dom("LIST_AFTER_ORDERED_EVENT cycle="+cycle);
                if(after.contains("\"spinner\":true") && nativeFinished()){
                    failed++;
                    info("REPRODUCED_STALE_SPINNER cycle="+cycle+" saved_native_idle=1");
                    saveScreenshot("sidebar-lifecycle-stale-"+cycle+".png");
                }
                if(cycle==0||cycle==3||cycle==6) {
                    runOnMainSync(()->activity.moveTaskToBack(true));
                    SystemClock.sleep(350);
                    runOnMainSync(()->activity.startActivity(launch));
                    SystemClock.sleep(350);
                    dom("LIST_AFTER_RESUME cycle="+cycle);
                }
                info("CYCLE_END cycle="+cycle+" first="+clip(before)+" second="+clip(after));
            }
        }catch(Throwable e){
            failed++;
            info("EXCEPTION "+e.getClass().getSimpleName()+" "+e.getMessage());
        }finally{
            // Restore test-controlled Redux summary and native-cache timestamp.
            try{
                if(targetId!=null)eval("window.__AGY_E2E_FIXTURE__?.restore("+jsId()+")");
                if(targetId!=null&&oldSaved) {
                    SharedPreferences.Editor editor=getTargetContext()
                        .getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit();
                    if(oldFinished>0)editor.putLong(PREFIX+targetId,oldFinished);
                    else editor.remove(PREFIX+targetId);
                    editor.commit();
                }
                eval("window.__AGY_E2E_ENABLED__=false;true");
            }catch(Throwable ex){info("CLEANUP_WARNING "+ex.getClass().getSimpleName());}
            try {
                if(targetId!=null){
                    NotificationManager nm=(NotificationManager)getTargetContext().getSystemService(Context.NOTIFICATION_SERVICE);
                    nm.cancel(targetId,21001);nm.cancel(targetId,21002);
                }
            }catch(Throwable cleanup){info("NOTIFICATION_CLEANUP_WARNING "+cleanup.getClass().getSimpleName());}
            saveReport();
            info("DONE reproduced="+failed+" inconclusive="+inconclusive);
            result.putString("summary","reproduced="+failed+" inconclusive="+inconclusive);
            finish(failed>0?0:-1,result);
        }
    }
    private String jsId(){return JSONObject.quote(targetId);}
    private String shortId(){return targetId.substring(Math.max(0,targetId.length()-6));}
    private void clearFinished(){
        getTargetContext().getSharedPreferences(PREFS,Context.MODE_PRIVATE)
            .edit().remove(PREFIX+targetId).commit();
        try {eval("window.__AGY_VERIFIED_AGENT_OBSERVE__?.({conversationId:"+jsId()+",status:2,fullyIdle:false});true");}
        catch(Exception ignored){}
    }
    private boolean nativeFinished(){
        return getTargetContext().getSharedPreferences(PREFS,Context.MODE_PRIVATE)
            .getLong(PREFIX+targetId,0)>0;
    }
    private void callObserve(int status,boolean fullyIdle)throws Exception{
        Class<?> c=getTargetContext().getClassLoader().loadClass("com.agy.bridge.AgentNotifications");
        Object obj=c.getConstructor(Context.class).newInstance(getTargetContext());
        Method m=c.getMethod("observeNative",String.class,int.class,boolean.class,String.class);
        m.invoke(obj,targetId,status,fullyIdle,"SIDEBAR E2E TEST");
        info("NATIVE_NOTIFICATION status="+status+" idle="+fullyIdle);
    }
    private void finishNative(int cycle)throws Exception{
        getTargetContext().getSharedPreferences(PREFS,Context.MODE_PRIVATE)
            .edit().putLong(PREFIX+targetId,System.currentTimeMillis()).commit();
        callObserve(1,true);
        info("NATIVE_FINISHED cycle="+cycle);
    }
    private void clickNativeFinishedNotification()throws Exception{
        NotificationManager nm=(NotificationManager)getTargetContext().getSystemService(Context.NOTIFICATION_SERVICE);
        StatusBarNotification[] notifications=nm.getActiveNotifications();
        for(StatusBarNotification n:notifications){
            if(targetId.equals(n.getTag())&&n.getId()==21002){
                PendingIntent tap=n.getNotification().contentIntent;
                if(tap==null)throw new Exception("finished notification missing PendingIntent");
                tap.send();
                SystemClock.sleep(450);
                info("NOTIFICATION_TAPPED_BY_SENDING_REAL_PENDING_INTENT");
                return;
            }
        }
        info("NOTIFICATION_PENDING_INTENT_NOT_AVAILABLE");
        inconclusive++;
    }
    private String inject(int status,boolean active)throws Exception{
        String out=eval("window.__AGY_E2E_FIXTURE__.set("+jsId()+","+status+","+active+")");
        return new JSONArray("["+out+"]").getString(0);
    }
    private String dom(String label)throws Exception{
        String script="(function(){var id="+jsId()+";var rows=[...document.querySelectorAll('[data-testid=conversation-row-sidebar]')];"+
            "var row=rows.find(x=>x.getAttribute('data-cascade-id')===id);"+
            "var visible=x=>!!(x&&x.getClientRects().length);"+
            "return JSON.stringify({page:window.__AGY_DIAG_PAGE_ID__,route:location.pathname,"+
            "spinner:visible(row?.querySelector('[data-testid=status-loading-spinner]')),"+
            "unread:visible(row?.querySelector('[data-testid=status-unread-dot]')),"+
            "anySpinner:!!document.querySelector('[data-testid=status-loading-spinner]'),"+
            "fixture:window.__AGY_E2E_FIXTURE__?.snapshot(id)})})()";
        String x=new JSONArray("["+eval(script)+"]").getString(0);
        info(label+" "+clip(x));return x;
    }
    private static String clip(String x){return x==null?"null":x.substring(0,Math.min(210,x.length()));}
    private String eval(String script)throws Exception{
        AtomicReference<String> out=new AtomicReference<>();
        CountDownLatch done=new CountDownLatch(1);
        runOnMainSync(()->web.evaluateJavascript(script,result->{out.set(result);done.countDown();}));
        if(!done.await(7,TimeUnit.SECONDS))throw new Exception("Javascript callback timeout");
        return out.get();
    }
    private static WebView find(View v){
        if(v instanceof WebView)return (WebView)v;
        if(v instanceof ViewGroup){
            ViewGroup g=(ViewGroup)v;
            for(int i=0;i<g.getChildCount();i++){
                WebView child=find(g.getChildAt(i));if(child!=null)return child;
            }
        }
        return null;
    }
    private void info(String message){
        String line=System.currentTimeMillis()+" "+message;
        log.append(line).append('\n');
        Log.i(TAG,line);
        Bundle b=new Bundle();b.putString("stream",message);sendStatus(1,b);
    }
    private void returnResult(Bundle b){b.putString("summary","INCONCLUSIVE fixture missing");}
    private void saveReport(){
        try {
            File dir=new File(getTargetContext().getFilesDir(),"logs");dir.mkdirs();
            try(FileOutputStream out=new FileOutputStream(new File(dir,"sidebar-lifecycle-e2e.txt"))){
                out.write(log.toString().getBytes("UTF-8"));
            }
        }catch(Exception e){Log.e(TAG,"saveReport",e);}
    }
    private void saveScreenshot(String name){
        try {
            Bitmap b=getUiAutomation().takeScreenshot();
            File dir=new File(getTargetContext().getFilesDir(),"logs");dir.mkdirs();
            if(b!=null){
                try(FileOutputStream out=new FileOutputStream(new File(dir,name))){
                    b.compress(Bitmap.CompressFormat.PNG,100,out);
                }
                b.recycle();
            }
        }catch(Exception e){Log.w(TAG,"screenshot",e);}
    }
}
