package com.agy.diagtests;
import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Exercises the installed real com.agy.dev WebView. Never sends agent prompts. */
public final class SidebarInstrumentation extends Instrumentation {
    private final StringBuilder report=new StringBuilder();
    private int failures=0;
    private Activity activity;
    private WebView web;

    @Override public void onCreate(Bundle b){super.onCreate(b);start();}
    @Override public void onStart(){
        super.onStart(); Bundle result=new Bundle();
        try {
            mark("START target=com.agy.dev");
            Intent launch=getTargetContext().getPackageManager().getLaunchIntentForPackage("com.agy.dev");
            if(launch==null) throw new IllegalStateException("missing launch intent");
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
            activity=startActivitySync(launch);
            if(activity==null) throw new IllegalStateException("activity missing");
            waitForIdleSync();
            web=findWebView(activity.getWindow().getDecorView());
            if(web==null) throw new IllegalStateException("webview missing");
            long end=SystemClock.elapsedRealtime()+35000;
            while(SystemClock.elapsedRealtime()<end){
                if("true".equals(eval("!!(window.__AGY_DIAG_PAGE_ID__ && document.querySelectorAll('[data-screenshot-volatile]').length)")))break;
                SystemClock.sleep(500);
            }
            JSONObject initial=sample("INITIAL");
            if(!initial.optBoolean("diag") || initial.optInt("timestamps")==0)
                throw new IllegalStateException("sidebar not loaded");

            mark("WAIT_70S");
            SystemClock.sleep(70000);
            JSONObject later=sample("AFTER_70S");
            if(initial.optString("page").equals(later.optString("page"))){
                JSONArray old=initial.optJSONArray("times"), now=later.optJSONArray("times");
                if(old!=null&&now!=null&&old.length()==now.length()){
                    for(int i=0;i<old.length();i++){
                        String a=old.optString(i), b=now.optString(i);
                        if((a.equals("now")||a.matches("\\d+m"))&&a.equals(b)){
                            fail("TIME_STALE index="+i+" text="+a);break;
                        }
                    }
                }else mark("TIME_INCONCLUSIVE_ROW_CHANGE");
            }else mark("TIME_INCONCLUSIVE_PAGE_CHANGED");

            mark("PAUSE_RESUME_START");
            runOnMainSync(()->activity.moveTaskToBack(true));
            SystemClock.sleep(1800);
            runOnMainSync(()->activity.startActivity(launch));
            SystemClock.sleep(2500);
            JSONObject resumed=sample("AFTER_RESUME");
            mark("PAUSE_RESUME_DONE");
            if(!resumed.optBoolean("diag"))fail("DIAG_LOST_ON_RESUME");

            // Click a REAL React row, without typing, sending prompts, or
            // changing server state. Then use the actual Android Back path.
            mark("NAV_START");
            String js="(function(){var rows=[...document.querySelectorAll('[data-screenshot-volatile]')];"+
                "for(var e of rows){var p=e;for(var d=0;p&&d<12;p=p.parentElement,d++){"+
                "var k=Object.keys(p).find(x=>x.indexOf('__reactProps')===0);"+
                "if(k&&typeof p[k].onClick==='function'){p.click();return 'clicked'}}}"+
                "return 'no_clickable_row'})()";
            String click=eval(js);
            mark("NAV_CLICK="+click);
            if(click!=null&&click.contains("clicked")){
                SystemClock.sleep(1400);
                JSONObject opened=sample("AFTER_NAV_OPEN");
                if("chat".equals(opened.optString("route"))){
                    sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
                    SystemClock.sleep(1400);
                    JSONObject returned=sample("AFTER_NAV_BACK");
                    if("chat".equals(returned.optString("route")))fail("ANDROID_BACK_STILL_IN_CHAT");
                    JSONArray a=resumed.optJSONArray("times"),b=returned.optJSONArray("times");
                    if(a!=null&&b!=null&&a.length()>0&&b.length()>0)
                        mark("NAV_TIME_COMPARISON before="+a.optString(0)+" after="+b.optString(0));
                }else mark("NAV_INCONCLUSIVE_ROUTE="+opened.optString("route"));
            }else mark("NAV_INCONCLUSIVE_NO_ROW");
            mark("END failures="+failures);
        } catch(Throwable e){
            fail("TEST_EXCEPTION "+e.getClass().getSimpleName()+" "+e.getMessage());
        } finally {
            if(failures>0) saveScreenshot();
            saveReport();
            result.putString("summary",failures==0?"PASS_OR_INCONCLUSIVE":"FAILURES="+failures);
            finish(failures==0?-1:0,result);
        }
    }
    private static WebView findWebView(View v){
        if(v instanceof WebView)return (WebView)v;
        if(v instanceof ViewGroup){
            ViewGroup g=(ViewGroup)v;
            for(int i=0;i<g.getChildCount();i++){
                WebView w=findWebView(g.getChildAt(i));if(w!=null)return w;
            }
        }
        return null;
    }
    private String eval(String js)throws Exception{
        AtomicReference<String> result=new AtomicReference<>();
        CountDownLatch done=new CountDownLatch(1);
        runOnMainSync(()->web.evaluateJavascript(js,v->{result.set(v);done.countDown();}));
        if(!done.await(9,TimeUnit.SECONDS))throw new Exception("evaluateJavascript timeout");
        return result.get();
    }
    private JSONObject sample(String label)throws Exception{
        String js="(function(){var v=[...document.querySelectorAll('[data-screenshot-volatile]')]"+
            ".filter(x=>x.getClientRects().length).slice(0,18);"+
            "return JSON.stringify({diag:!!window.__AGY_DIAG_PAGE_ID__,"+
            "page:String(window.__AGY_DIAG_PAGE_ID__||'missing'),"+
            "route:location.pathname.startsWith('/c/')?'chat':'list',"+
            "timestamps:v.length,times:v.map(x=>x.textContent.trim().slice(0,12)),"+
            "spinners:document.querySelectorAll('[data-testid=\"status-loading-spinner\"]').length,"+
            "unread:document.querySelectorAll('[data-testid=\"status-unread-dot\"]').length});})()";
        String raw=eval(js);
        if(raw==null||"null".equals(raw))throw new Exception("JS null");
        JSONObject obj=new JSONObject(new JSONArray("["+raw+"]").getString(0));
        mark(label+" page="+obj.optString("page")+" route="+obj.optString("route")+
            " times="+obj.optJSONArray("times")+" spinners="+obj.optInt("spinners")+
            " unread="+obj.optInt("unread"));
        return obj;
    }
    private void mark(String msg){
        String m=System.currentTimeMillis()+" "+msg;
        report.append(m).append('\n');
        Log.i("AGY_SIDEBAR_TEST",m);
        Bundle b=new Bundle();b.putString("stream",msg);sendStatus(1,b);
    }
    private void fail(String s){failures++;mark("FAIL "+s);}
    private void saveReport(){
        try{
            File f=new File(getTargetContext().getFilesDir(),"logs");f.mkdirs();
            try(FileOutputStream o=new FileOutputStream(new File(f,"sidebar-instrumentation.txt"))){
                o.write(report.toString().getBytes("UTF-8"));
            }
        }catch(Exception e){Log.e("AGY_SIDEBAR_TEST","report",e);}
    }
    private void saveScreenshot(){
        try{
            Bitmap b=getUiAutomation().takeScreenshot();
            if(b!=null){
                File f=new File(getTargetContext().getFilesDir(),"logs");f.mkdirs();
                try(FileOutputStream o=new FileOutputStream(new File(f,"sidebar-instrumentation-failure.png"))){
                    b.compress(Bitmap.CompressFormat.PNG,100,o);
                }
                b.recycle();
            }
        }catch(Exception e){Log.w("AGY_SIDEBAR_TEST","screenshot",e);}
    }
}
