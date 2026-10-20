package com.agy.v24readiness;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.webkit.WebView;
import android.view.View;
import android.view.ViewGroup;
import android.service.notification.StatusBarNotification;
import org.json.JSONTokener;
import java.io.File;
import java.io.FileInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * One short real Core task in a freshly created dev-only conversation.
 * No synthetic terminal-state injection; completion proof is read from the
 * native Core observer, not WebView visual state.
 * Refuses to send unless a new-conversation view and unique composer are found.
 */
public final class LiveCoreSmoke extends Instrumentation {
    private void status(String name, Object value) {
        Bundle b = new Bundle();
        b.putString("stream", name + "=" + String.valueOf(value));
        sendStatus(1, b);
    }
    private static WebView find(View v) {
        if (v instanceof WebView) return (WebView) v;
        if (v instanceof ViewGroup) {
            ViewGroup g=(ViewGroup)v;
            for (int i=0;i<g.getChildCount();i++) {
                WebView w=find(g.getChildAt(i));
                if (w!=null) return w;
            }
        }
        return null;
    }
    private static String eval(Activity host, String script) throws Exception {
        CountDownLatch ready=new CountDownLatch(1);
        AtomicReference<String> value=new AtomicReference<>("TIMEOUT");
        host.runOnUiThread(()->{
            WebView web=find(host.getWindow().getDecorView());
            if (web == null) { ready.countDown(); return; }
            web.evaluateJavascript(script, raw -> {
                try {
                    Object unwrapped=new JSONTokener(raw).nextValue();
                    value.set(String.valueOf(unwrapped));
                } catch (Exception ignored) { value.set("PARSE_ERROR"); }
                ready.countDown();
            });
        });
        ready.await(6,TimeUnit.SECONDS);
        return value.get();
    }
    private static String getConversationId(Activity host) throws Exception {
        String path=eval(host,"location.pathname");
        if (path.startsWith("/c/")) {
            String id=path.substring(3);
            if(id.matches("[A-Za-z0-9_+:-]{1,128}")) return id;
        }
        return null;
    }
    private static boolean isNativeFinished(Context context,String conversation) {
        if(conversation==null) return false;
        try {
            Class<?> observer=Class.forName("com.agy.core.NativeAgentStateObserver");
            Method m=observer.getMethod("wasRecentlyFinished",Context.class,String.class);
            return Boolean.TRUE.equals(m.invoke(null,context,conversation));
        } catch(Exception e) {return false;}
    }
    private static int countCoreEvents(Context ctx,String phrase){
        File log=new File(ctx.getFilesDir(),"logs/agy-diag.log");
        if(!log.isFile())return 0;
        try(FileInputStream in=new FileInputStream(log);ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] chunk=new byte[4096];int n;while((n=in.read(chunk))>0)out.write(chunk,0,n);
            String text=new String(out.toByteArray(),"UTF-8");
            int count=0;for(String line:text.split("\\n"))if(line.contains("native_observer "+phrase))count++;
            return count;
        }catch(Exception ignored){return 0;}
    }
    private static int posted(Context ctx){
        try{
            NotificationManager manager=(NotificationManager)ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            StatusBarNotification[] notes=manager.getActiveNotifications();
            return notes==null?0:notes.length;
        }catch(Exception ignored){return -1;}
    }
    private boolean executeRequested;
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        // Explicit opt-in only; default execution cannot burn model quota.
        executeRequested=b!=null && "yes".equalsIgnoreCase(b.getString("execute"));
        start();
    }
    @Override public void onStart(){
        super.onStart();
        boolean success=false,sendClicked=false,routeObserved=false,dryRunPassed=false;
        try{
            Context context=getTargetContext();
            Intent launch=context.getPackageManager().getLaunchIntentForPackage("com.agy.dev");
            if(launch==null)throw new IllegalStateException("Missing dev launcher");
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            Activity host=startActivitySync(launch);
            status("TARGET","com.agy.dev");
            java.lang.reflect.Field readyField=Class.forName("com.agy.ui.MainActivity")
                    .getDeclaredField("webUiReady");
            readyField.setAccessible(true);
            boolean ready=false;
            long readyUntil=SystemClock.elapsedRealtime()+25000;
            while(SystemClock.elapsedRealtime()<readyUntil){
                ready=Boolean.TRUE.equals(readyField.get(host));
                if(ready)break;
                SystemClock.sleep(250);
            }
            status("CONFIRMED_ON_UI_READY",ready);
            if(!ready)throw new IllegalStateException("dev WebView not ready: no model request sent");
            SystemClock.sleep(450);
            int coreBefore=countCoreEvents(context,"state ");
            int doneBefore=countCoreEvents(context,"finished ");
            int notificationsBefore=posted(context);
            String clickNew="(function(){var f=[...document.querySelectorAll('button,[role=button]')].filter(x=>{var r=x.getBoundingClientRect();return r.width>0&&r.height>0&&!x.disabled;}).filter(x=>/new conversation/i.test([x.getAttribute('aria-label'),x.title,x.textContent].filter(Boolean).join(' ')));if(f.length!==1)return 'AMBIGUOUS_NEW_'+f.length;f[0].click();return 'CLICKED_NEW';})()";
            String newResult=eval(host,clickNew);
            status("CREATE_NEW",newResult);
            if(!"CLICKED_NEW".equals(newResult))throw new IllegalStateException("No unambiguous new conversation control");
            SystemClock.sleep(1500);
            String fresh=eval(host,"!/^\\/c\\//.test(location.pathname)");
            status("FRESH_CONVERSATION",fresh);
            if(!"true".equals(fresh))throw new IllegalStateException("Refusing to send into an existing conversation");
            if(!executeRequested){
                dryRunPassed=true;
                status("DRY_RUN","PASS_NEW_DEV_CONVERSATION_NO_REQUEST");
                return;
            }

            // Use actual Android keystrokes, not programmatic DOM value
            // mutation: React/Lexical controls may ignore synthetic input.
            String focus="(function(){var a=[...document.querySelectorAll('textarea,[contenteditable=true]')].filter(x=>{var r=x.getBoundingClientRect();return r.width>0&&r.height>0;});if(a.length!==1)return 'AMBIGUOUS_COMPOSER_'+a.length;a[0].focus();return 'FOCUSED';})()";
            String focused=eval(host,focus);
            status("COMPOSER",focused);
            if(!"FOCUSED".equals(focused))throw new IllegalStateException("No unique composer");
            sendStringSync("Reply OK. No tools.");
            SystemClock.sleep(550);
            String enabled=eval(host,"(function(){var a=[...document.querySelectorAll('button')].filter(x=>{var r=x.getBoundingClientRect();return r.width>0&&r.height>0&&!x.disabled;});return a.some(x=>/send message|send immediately/i.test([x.getAttribute('aria-label'),x.title].filter(Boolean).join(' ')))})()");
            status("SEND_CONTROL_ENABLED",enabled);
            if(!"true".equals(enabled))
                throw new IllegalStateException("Send control disabled; no attempt made");
            // Execute the actual enabled React button's click handler exactly
            // once. KEYCODE_ENTER can insert a newline / select a popup.
            String clickSend="(function(){var a=[...document.querySelectorAll('button')].filter(x=>{var r=x.getBoundingClientRect();return r.width>0&&r.height>0&&!x.disabled;}).filter(x=>/send message|send immediately/i.test([x.getAttribute('aria-label'),x.title].filter(Boolean).join(' ')));if(a.length!==1)return 'AMBIGUOUS_SEND_'+a.length;if(window.__agyDevSmokeSendClicked)return 'DUPLICATE_REFUSED';window.__agyDevSmokeSendClicked=true;a[0].click();return 'CLICKED_SEND';})()";
            String sendResult=eval(host,clickSend);
            sendClicked="CLICKED_SEND".equals(sendResult);
            status("SEND_CLICKED_ONCE",sendClicked);
            if(!sendClicked)
                throw new IllegalStateException("Send handler not uniquely found; no attempt made");
            String conversation=null;
            for(int i=0;i<8;i++){
                SystemClock.sleep(1000);
                conversation=getConversationId(host);
                if(conversation!=null)break;
            }
            routeObserved=conversation!=null;
            status("NEW_CONVERSATION_ROUTE_OBSERVED",routeObserved);
            if(!routeObserved)
                throw new IllegalStateException("No verified new conversation route after click; never re-submit");
            long expires=SystemClock.elapsedRealtime()+65000;
            boolean finished=false;
            while(SystemClock.elapsedRealtime()<expires){
                SystemClock.sleep(1500);
                if(conversation==null)conversation=getConversationId(host);
                if(isNativeFinished(context,conversation)){finished=true;break;}
            }
            int coreStates=countCoreEvents(context,"state ")-coreBefore;
            int coreCompleted=countCoreEvents(context,"finished ")-doneBefore;
            int notificationsAfter=posted(context);
            status("CONVERSATION_ID_CAPTURED",conversation!=null);
            status("CORE_NATIVE_STREAM_EVENTS",Math.max(0,coreStates));
            status("CORE_NATIVE_COMPLETION_EVENTS",Math.max(0,coreCompleted));
            status("NATIVE_FINISH_PROOF",finished);
            status("ACTIVE_NOTIFICATIONS_BEFORE",notificationsBefore);
            status("ACTIVE_NOTIFICATIONS_AFTER",notificationsAfter);
            success=finished&&coreCompleted>0;
            status("RESULT",success?"PASS_REAL_CORE_COMPLETE":"INCOMPLETE_REAL_CORE_PROOF");
        }catch(Throwable err) {
            status("ERROR",err.getClass().getSimpleName());
            status("SEND_CLICKED_ONCE",sendClicked);
            status("NEW_CONVERSATION_ROUTE_OBSERVED",routeObserved);
        }finally{
            Bundle b=new Bundle();
            b.putString("summary",success?"liveCore=PASS":
                    dryRunPassed?"dryRun=PASS;liveCore=NOT_TESTED":
                    "liveCore=NOT_PROVEN;NO_RETRY");
            finish(success||dryRunPassed?0:1,b);
        }
    }
}
