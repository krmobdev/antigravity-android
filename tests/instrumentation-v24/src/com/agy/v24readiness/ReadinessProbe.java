package com.agy.v24readiness;
import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.webkit.WebView;
import android.view.View;
import android.view.ViewGroup;
import org.json.JSONObject;
import org.json.JSONTokener;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** V2.4 independent readiness probe; reports flags only, never auth or chat data. */
public final class ReadinessProbe extends Instrumentation {
  private boolean passed=false;
  private void report(String name,Object value){
    Bundle b=new Bundle();b.putString("stream",name+"="+String.valueOf(value));sendStatus(1,b);
  }
  private static Object member(Object object,String name)throws Exception {
    Class<?> cl=object instanceof Class ?(Class<?>)object:object.getClass();
    Field f=null;
    for(Class<?> current=cl;current!=null;current=current.getSuperclass()){
      try {f=current.getDeclaredField(name);break;}
      catch(NoSuchFieldException ignored){}
    }
    if(f==null)throw new NoSuchFieldException(name);
    f.setAccessible(true);
    return f.get(object instanceof Class?null:object);
  }
  private static boolean truth(Object v){return Boolean.TRUE.equals(v);}
  private static WebView find(View v){
    if(v instanceof WebView)return (WebView)v;
    if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;
      for(int i=0;i<g.getChildCount();i++){WebView w=find(g.getChildAt(i));if(w!=null)return w;}
    }return null;
  }
  @Override public void onCreate(Bundle b){super.onCreate(b);start();}
  @Override public void onStart(){
    super.onStart();
    try {
      Context ctx=getTargetContext();
      Intent intent=ctx.getPackageManager().getLaunchIntentForPackage("com.agy.dev");
      if(intent==null)throw new IllegalStateException("Missing dev launcher");
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
      Activity a=startActivitySync(intent);
      report("TARGET","com.agy.dev");
      boolean oauth=false;
      try{
        Class<?> cl=Class.forName("com.agy.oauth.OAuthTokenStore");
        Method method=cl.getMethod("hasToken",Context.class);
        oauth=truth(method.invoke(null,ctx));
      }catch(Exception e){report("AUTH_CHECK","unavailable");}
      report("OAUTH_TOKEN_PRESENT",oauth);
      boolean core=false,web=false,appLoaded=false;
      long until=SystemClock.elapsedRealtime()+18000;
      while(SystemClock.elapsedRealtime()<until){
        try{
          core=member(Class.forName("com.agy.core.CoreRuntimeState"),"serverUrl")!=null;
          Object current=member(Class.forName("com.agy.ui.MainActivity"),"activeActivity");
          if(current instanceof Activity)a=(Activity)current;
          web=truth(member(a,"webUiReady"));
          appLoaded=truth(member(a,"appLoaded"));
          if(core&&web)break;
        }catch(Exception e){}
        SystemClock.sleep(250);
      }
      report("CORE_READY",core);report("WEB_APP_LOADED",appLoaded);report("ON_UI_READY",web);
      report("ACTIVITY_RESUMED",truth(member(a,"activityResumed")));
      report("ACTIVITY_WINDOW_FOCUS",a.hasWindowFocus());
      report("ACTIVITY_DESTROYED",a.isDestroyed());
      Activity host=a;
      CountDownLatch latch=new CountDownLatch(1);
      AtomicReference<String> raw=new AtomicReference<>("");
      host.runOnUiThread(()->{
        WebView w=find(host.getWindow().getDecorView());
        if(w==null){latch.countDown();return;}
        String expression="(function(){try{var b=document.body,t=b&&b.innerText||'',r=document.getElementById('root');return JSON.stringify({ready:document.readyState,root:!!r,bodyChildren:b?b.childElementCount:0,buttons:document.querySelectorAll('button').length,authSurface:/sign in|log in|sign up|authenticate|onboarding|continue with/i.test(t),sidebarCandidate:!!document.querySelector('[data-testid*=sidebar],[role=navigation],aside'),startupInstalled:!!(window.__agStartupReady&&window.__agStartupReady.__installed),nativeBridgePresent:!!window.Android,onUiReadyMethod:typeof(window.Android&&window.Android.onUiReady)==='function',tokenInjected:typeof window.__AG_NATIVE_BRIDGE_TOKEN__==='string'})}catch(e){return '{}'}})()";
        w.evaluateJavascript(expression,s->{raw.set(s);latch.countDown();});
      });
      latch.await(5,TimeUnit.SECONDS);
      boolean shell=false;
      boolean tokenBridge=false;
      try{
        String value=raw.get();
        if(value.startsWith("\""))value=new JSONTokener(value).nextValue().toString();
        JSONObject info=new JSONObject(value);
        report("DOM_READY",info.optString("ready","unknown"));
        report("ROOT_PRESENT",info.optBoolean("root"));
        report("BODY_CHILDREN",info.optInt("bodyChildren"));
        report("BUTTONS",info.optInt("buttons"));
        report("AUTH_SCREEN",info.optBoolean("authSurface"));
        report("SIDEBAR_CANDIDATE",info.optBoolean("sidebarCandidate"));
        report("STARTUP_HOOK_INSTALLED",info.optBoolean("startupInstalled"));
        report("ANDROID_BRIDGE_VISIBLE",info.optBoolean("nativeBridgePresent"));
        report("ON_UI_READY_METHOD",info.optBoolean("onUiReadyMethod"));
        report("BRIDGE_TOKEN_INJECTED",info.optBoolean("tokenInjected"));
        shell=info.optBoolean("root") && info.optBoolean("sidebarCandidate")
            && info.optInt("bodyChildren")>0;
        tokenBridge=info.optBoolean("startupInstalled") && info.optBoolean("nativeBridgePresent")
            && info.optBoolean("onUiReadyMethod") && info.optBoolean("tokenInjected");
      }catch(Exception e){report("DOM_PROBE","unavailable");}
      passed=core && web && appLoaded && shell && tokenBridge;
      report("DIAGNOSIS",!core?"CORE_NOT_READY":!web?"WEB_UI_NOT_READY":!shell?
          "SIDEBAR_SHELL_MISSING":!tokenBridge?"NATIVE_BRIDGE_MISSING":!oauth?
          "UI_READY_BUT_AUTH_REQUIRED":"READY_FOR_REAL_AGENT_TESTS");
      report("UI_READINESS",passed?"PASS":"FAIL");
    }catch(Throwable err){report("TEST_ERROR",err.getClass().getSimpleName());}
    finally{
      Bundle result=new Bundle();
      result.putString("summary","uiReadiness="+(passed?"PASS":"FAIL"));
      finish(passed?0:1,result);
    }
  }
}