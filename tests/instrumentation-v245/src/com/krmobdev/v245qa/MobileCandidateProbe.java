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

/** Dev-only, no agent task requests; the Core fixture mode transcribes public synthetic speech. Outputs flags/counts only. */
public final class MobileCandidateProbe extends Instrumentation {
    private static final String A="v245_probe_a", B="v245_probe_b";
    private boolean desktopMode;
    private boolean handoffMode;
    private boolean barsMode;
    private boolean pixelsMode;
    private boolean pickerMode, routeMode, routeIntentMode, routeLegacyMode;
    private boolean fileSelectionTouch;
    private String fixtureText,fixtureImage,fixtureFolder;
    private boolean recoverMobileMode;
    private boolean capture;
    private boolean backMode;
    private boolean toolbarMode;
    private boolean startupMode;
    private boolean unifiedMode;
    private boolean polishMode;
    private boolean surfacesMode;
    private boolean micMode;
    private boolean micUiMode;
    private boolean micPcmUiMode;
    private boolean micPcmHardwareMode;
    private boolean micCoreFixtureMode,micCoreFixtureCleanupMode;
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
        notify(A,2,false,"Reading source files",0);notify(B,2,false,"Editing files",0);
        SystemClock.sleep(1100);notify(A,2,false,"Running tests",0);SystemClock.sleep(1300);
        require(text(notification(A,21001)).equals("Running tests"),"ANDROID_BACKGROUND_STAGE_A");
        require(text(notification(B,21001)).equals("Editing files"),"ANDROID_PARALLEL_STAGE_B");
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
        require(text(notification(A,21002)).startsWith("Error"),"ANDROID_ERROR_OUTCOME");
        require(text(notification(B,21002)).startsWith("Canceled"),"ANDROID_CANCEL_OUTCOME_FIFO");
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
    private void refreshDesktopHost()throws Exception{
        Object current=field(Class.forName("com.agy.ui.MainActivity",true,target.getClassLoader()),"activeActivity");
        if(current instanceof Activity)activity=(Activity)current;
        runOnMainSync(()->web=find(activity.getWindow().getDecorView()));
    }
    private void awaitDesktopProfile(boolean desktop,boolean narrow)throws Exception{
        long until=SystemClock.elapsedRealtime()+25000;boolean ready=false;
        do{
            refreshDesktopHost();
            try{
                if(web!=null&&Boolean.TRUE.equals(field(activity,"webUiReady"))){
                    JSONObject info=state("return JSON.stringify({desktop:window.__AG_DESKTOP_MODE__,narrow:innerWidth<=720,mobile:!!document.querySelector('.agy-composer-model-control')});");
                    ready=info.optBoolean("desktop")==desktop&&info.optBoolean("narrow")==narrow&&info.optBoolean("mobile")==narrow;
                    if(ready)break;
                }
            }catch(Exception ignored){}
            SystemClock.sleep(150);
        }while(SystemClock.elapsedRealtime()<until);
        require(ready,"DESKTOP_PROFILE_"+desktop+"_"+narrow);
    }
    private void setDesktopProfile(boolean value)throws Exception{
        final Activity host=activity;
        runOnMainSync(()->{try{host.getClass().getMethod("setDesktopMode",boolean.class).invoke(host,value);}catch(Exception error){throw new RuntimeException(error);}});
    }
    private void tapDesktopPoint(JSONObject point,String name)throws Exception{
        require(point.optBoolean("hit"),name+"_DOM_HIT");
        long until=SystemClock.elapsedRealtime()+6000;boolean focused=false;
        do{
            android.view.accessibility.AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();
            focused=activity.hasWindowFocus()&&root!=null&&target.getPackageName().equals(String.valueOf(root.getPackageName()));
            if(focused)break;SystemClock.sleep(150);
        }while(SystemClock.elapsedRealtime()<until);
        require(focused,name+"_TARGET_FOCUSED");
        final int[] origin=new int[2];runOnMainSync(()->web.getLocationOnScreen(origin));
        float scale=web.getWidth()/(float)point.getDouble("vw");
        float x=origin[0]+(float)point.getDouble("x")*scale,y=origin[1]+(float)point.getDouble("y")*scale;
        long now=SystemClock.uptimeMillis();
        android.view.MotionEvent down=android.view.MotionEvent.obtain(now,now,android.view.MotionEvent.ACTION_DOWN,x,y,0);
        android.view.MotionEvent up=android.view.MotionEvent.obtain(now,now+60,android.view.MotionEvent.ACTION_UP,x,y,0);
        try{sendPointerSync(down);sendPointerSync(up);}finally{down.recycle();up.recycle();}
        SystemClock.sleep(600);
    }
    private void tapDesktopSwitch()throws Exception{
        SystemClock.sleep(450);
        JSONObject point=waitState("const row=[...document.querySelectorAll('.agy-settings-row')].find(e=>e.querySelector('span')?.textContent==='Desktop Mode'),e=row?.querySelector('button[role=switch]'),r=e?.getBoundingClientRect(),x=r?r.left+r.width/2:0,y=r?r.top+r.height/2:0,h=document.elementFromPoint(x,y);return JSON.stringify({hit:!!e&&(h===e||e.contains(h)),x,y,vw:innerWidth});","hit");
        if(!point.optBoolean("hit"))report("DESKTOP_TAP_GEOMETRY",point.toString());
        tapDesktopPoint(point,"DESKTOP_SWITCH_REAL_TAP");
    }
    private void clickSettingsSection(String name)throws Exception{
        SystemClock.sleep(450);
        JSONObject point=waitState("const e=[...document.querySelectorAll('[data-testid=\"settings-nav-item-"+name+"\"]')].find(e=>e.getBoundingClientRect().width>0&&!e.closest('[inert],[aria-hidden=true]')),r=e?.getBoundingClientRect(),x=r?r.left+r.width/2:0,y=r?r.top+r.height/2:0,h=document.elementFromPoint(x,y);return JSON.stringify({hit:!!e&&(h===e||e.contains(h)),x,y,vw:innerWidth});","hit");
        if(!point.optBoolean("hit"))report("DESKTOP_TAP_GEOMETRY",point.toString());
        tapDesktopPoint(point,"DESKTOP_CLICK_"+name);
    }
    private void adaptiveDesktop()throws Exception{
        JSONObject safe=state("const editor=document.querySelector('.agy-composer [contenteditable=true]');return JSON.stringify({empty:!!editor&&!editor.textContent.trim()&&!document.querySelector('.agy-attachment-card')});");
        if(!safe.optBoolean("empty")){report("ANDROID_ADAPTIVE_DESKTOP","PENDING_DRAFT_PRESERVED");return;}
        boolean original=(Boolean)activity.getClass().getMethod("isDesktopMode").invoke(activity);
        int orientation=activity.getRequestedOrientation();
        Class<?> core=Class.forName("com.agy.core.CoreRuntimeState",true,target.getClassLoader());
        int generation=(Integer)field(core,"readyGeneration");
        try{
            runOnMainSync(()->activity.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
            SystemClock.sleep(700);refreshDesktopHost();
            if((Boolean)activity.getClass().getMethod("isDesktopMode").invoke(activity))setDesktopProfile(false);
            awaitDesktopProfile(false,true);
            openAppearance();tapDesktopSwitch();awaitDesktopProfile(true,true);
            openAppearance();sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);SystemClock.sleep(650);
            clickSettingsSection("General");
            require(stockSheets().optInt("visible")==1,"DESKTOP_PORTRAIT_GENERAL_MOBILE_PANEL");
            runOnMainSync(()->activity.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
            awaitDesktopProfile(true,false);SystemClock.sleep(650);
            clickSettingsSection("Appearance");clickSettingsSection("General");
            JSONObject popup=state("return JSON.stringify({open:window.__AGY_DESKTOP_OVERLAY_OPEN__?.()===true});");
            require(popup.optBoolean("open"),"DESKTOP_LANDSCAPE_DIALOG_ACTIVE");
            captureRegion(".agy-settings-page","DESKTOP_LANDSCAPE_GENERAL");
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);SystemClock.sleep(700);
            popup=state("return JSON.stringify({closed:window.__AGY_DESKTOP_OVERLAY_OPEN__?.()===false});");
            require(popup.optBoolean("closed"),"DESKTOP_LANDSCAPE_NATIVE_BACK_CLOSES_DIALOG");
            runOnMainSync(()->activity.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
            awaitDesktopProfile(true,true);
            openAppearance();sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);SystemClock.sleep(650);
            clickSettingsSection("General");
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);SystemClock.sleep(650);
            JSONObject root=state("const b=[...document.querySelectorAll('[data-testid=\"settings-nav-item-General\"]')].find(e=>e.getBoundingClientRect().width>0&&!e.closest('[inert],[aria-hidden=true]'));return JSON.stringify({root:!!b});");
            require(root.optBoolean("root")&&stockSheets().optInt("visible")==1,"DESKTOP_PORTRAIT_BACK_ONE_LEVEL");
            clickSettingsSection("Appearance");
            tapDesktopSwitch();
            report("DESKTOP_REAL_TOGGLE_OFF_CLICKED","PASS_NATIVE_TOUCH");
            awaitDesktopProfile(false,true);
            require(!(Boolean)activity.getClass().getMethod("isDesktopMode").invoke(activity),"DESKTOP_NATIVE_PREFERENCE_OFF");
            JSONObject centered=state("const m=document.querySelector('[data-testid=model-selector-trigger]')?.getBoundingClientRect(),s=document.querySelector('[data-testid=permissions-selector-trigger]')?.getBoundingClientRect(),v=document.querySelector('[data-tooltip-id=input-send-button-record-tooltip]')?.getBoundingClientRect();return JSON.stringify({centered:!!m&&!!s&&!!v&&Math.abs((m.left+m.width/2)-((s.left+s.width/2+v.left+v.width/2)/2))<=1,touch:!!m&&m.height>=48&&s.width>=48&&v.width>=48});");
            require(centered.optBoolean("centered")&&centered.optBoolean("touch"),"MODEL_PILL_CENTERED_BETWEEN_SHIELD_AND_MIC");
            captureRegion(".agy-composer-footer","CENTERED_MODEL");
            require(generation==(Integer)field(core,"readyGeneration"),"DESKTOP_MODE_SWITCH_CORE_PRESERVED");
            report("ANDROID_ADAPTIVE_DESKTOP","PASS_PORTRAIT_LANDSCAPE_BACK_REAL_TOGGLE_AND_CENTER");
        }finally{
            refreshDesktopHost();
            boolean current=(Boolean)activity.getClass().getMethod("isDesktopMode").invoke(activity);
            if(current!=original)setDesktopProfile(original);
            runOnMainSync(()->activity.setRequestedOrientation(orientation));
            SystemClock.sleep(700);
        }
    }



    /** Status-band pixels only; full screenshots are never saved/exported. */
    private void startupPixelFrames()throws Exception{
        final java.util.List<String> nativeFrames=java.util.Collections.synchronizedList(new java.util.ArrayList<String>());
        final String[] previous={""};
        final long start=SystemClock.elapsedRealtime();
        final boolean[] initialDark={false};
        final int[] statusBottom={0};
        runOnMainSync(()->{try{
            Object theme=field(activity,"themeManager");
            initialDark[0]=(Boolean)theme.getClass().getMethod("isDark").invoke(theme);
            android.graphics.Rect visible=new android.graphics.Rect();
            activity.getWindow().getDecorView().getWindowVisibleDisplayFrame(visible);
            statusBottom[0]=visible.top;
            if(statusBottom[0]<=0){
                int id=activity.getResources().getIdentifier("status_bar_height","dimen","android");
                if(id!=0)statusBottom[0]=activity.getResources().getDimensionPixelSize(id);
            }
        }catch(Exception e){throw new RuntimeException(e);}});
        final android.view.ViewTreeObserver.OnPreDrawListener listener=()->{
            try{
                Object theme=field(activity,"themeManager");
                int mode=(Integer)theme.getClass().getMethod("mode").invoke(theme);
                String key=String.format(java.util.Locale.ROOT,"%08x",activity.getWindow().getStatusBarColor())
                    +":"+((activity.getWindow().getDecorView().getSystemUiVisibility()&View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR)!=0)
                    +":"+mode+":"+field(activity,"webUiReady");
                if(!key.equals(previous[0])){nativeFrames.add((SystemClock.elapsedRealtime()-start)+":"+key);previous[0]=key;}
            }catch(Exception ignored){}
            return true;
        };
        runOnMainSync(()->activity.getWindow().getDecorView().getViewTreeObserver().addOnPreDrawListener(listener));
        int frames=0,blackBands=0,blackHeaders=0,whiteBands=0,unfocused=0;
        boolean ready=false,connecting=false;long readyAt=0;
        try{
            do{
                android.view.accessibility.AccessibilityNodeInfo active=getUiAutomation().getRootInActiveWindow();
                try{connecting=connecting||(Integer)field(field(activity,"splash"),"stage")==2;}catch(Exception ignored){}
                if(connecting&&activity.hasWindowFocus()&&active!=null&&target.getPackageName().equals(String.valueOf(active.getPackageName()))){
                    android.graphics.Bitmap bitmap=getUiAutomation().takeScreenshot();
                    if(bitmap!=null){
                        try{
                            int width=bitmap.getWidth(),bottom=Math.min(statusBottom[0],bitmap.getHeight());
                            if(bottom>12){
                                double[] dark=new double[3],light=new double[3];
                                float[] left={.06f,.45f,.82f},right={.18f,.55f,.94f};
                                for(int band=0;band<3;band++){
                                    int count=0,black=0,white=0;
                                    for(int y=Math.max(2,bottom-10);y<bottom-2;y+=2)
                                      for(int x=Math.round(width*left[band]);x<Math.round(width*right[band]);x+=3){
                                        int c=bitmap.getPixel(x,y);
                                        int l=(android.graphics.Color.red(c)*3+android.graphics.Color.green(c)*6+android.graphics.Color.blue(c))/10;
                                        if(l<60)black++;if(l>220)white++;count++;
                                      }
                                    dark[band]=black/(double)Math.max(1,count);light[band]=white/(double)Math.max(1,count);
                                }
                                boolean black=dark[0]>.7&&dark[1]>.7&&dark[2]>.7;
                                boolean white=light[0]>.7&&light[1]>.7&&light[2]>.7;
                                if(black)blackBands++;if(white)whiteBands++;frames++;
                                int headerY=Math.min(bitmap.getHeight()-10,bottom+Math.round(20*activity.getResources().getDisplayMetrics().density));
                                double[] headerDark=new double[3];
                                for(int band=0;band<3;band++){
                                    int count=0,blackCount=0;
                                    for(int y=headerY;y<headerY+8;y+=2)
                                      for(int x=Math.round(width*left[band]);x<Math.round(width*right[band]);x+=3){
                                        int c=bitmap.getPixel(x,y),l=(android.graphics.Color.red(c)*3+android.graphics.Color.green(c)*6+android.graphics.Color.blue(c))/10;
                                        if(l<60)blackCount++;count++;
                                      }
                                    headerDark[band]=blackCount/(double)Math.max(1,count);
                                }
                                boolean blackHeader=headerDark[0]>.7&&headerDark[1]>.7&&headerDark[2]>.7;
                                if(blackHeader)blackHeaders++;
                                report("HEADER_PIXELS",String.format(java.util.Locale.ROOT,"ms=%d,y=%d,black=%.2f/%.2f/%.2f",
                                  SystemClock.elapsedRealtime()-start,headerY,headerDark[0],headerDark[1],headerDark[2]));
                                report("STATUS_PIXELS",String.format(java.util.Locale.ROOT,
                                  "ms=%d,y=%d,black=%.2f/%.2f/%.2f,white=%.2f/%.2f/%.2f",
                                  SystemClock.elapsedRealtime()-start,bottom,dark[0],dark[1],dark[2],light[0],light[1],light[2]));
                            }
                        }finally{bitmap.recycle();}
                    }
                }else unfocused++;
                try{ready=Boolean.TRUE.equals(field(activity,"webUiReady"));}catch(Exception ignored){}
                if(ready&&readyAt==0)readyAt=SystemClock.elapsedRealtime();
                SystemClock.sleep(connecting?40:100);
            }while(SystemClock.elapsedRealtime()-start<65000&&(readyAt==0||SystemClock.elapsedRealtime()-readyAt<1500));
        }finally{
            runOnMainSync(()->activity.getWindow().getDecorView().getViewTreeObserver().removeOnPreDrawListener(listener));
        }
        for(String frame:nativeFrames)report("NATIVE_BAR_FRAME",frame);
        report("STATUS_PIXEL_COUNTS","frames="+frames+",black="+blackBands+",blackHeaders="+blackHeaders+",white="+whiteBands+",unfocused="+unfocused+",initialDark="+initialDark[0]+",ready="+ready+",activity="+activity.getClass().getSimpleName());
        if(!initialDark[0]&&frames>0&&ready){
            require(blackBands==0,"LIGHT_STARTUP_STATUS_PIXEL_BANDS");
            require(blackHeaders==0,"LIGHT_STARTUP_HEADER_PIXEL_BANDS");
        }else report("LIGHT_STARTUP_STATUS_PIXEL_BANDS","PENDING_DARK_NO_FRAMES_OR_NOT_READY");
    }


    private void clearCoreFixtureDraft()throws Exception{
        final String guard="const e=document.querySelector('.agy-composer [contenteditable=true]');const raw=e?.textContent||'';"+
            "const text=raw.toLowerCase().replace(/[.!?]/g,'').replace(/\\s+/g,' ').trim();"+
            "return JSON.stringify({owned:!!text&&/^[A-Za-z\\s.!?]*$/.test(raw)&&!text.replace(/this is a test/g,'').trim(),empty:!raw.trim()});";
        JSONObject before=state(guard);
        if(before.optBoolean("empty")){report("CORE_FIXTURE_DRAFT_ALREADY_EMPTY","PASS");return;}
        require(before.optBoolean("owned"),"CORE_FIXTURE_ONLY_PUBLIC_TEXT_GUARD");
        tapExpression("document.querySelector('.agy-composer [contenteditable=true]')","CORE_FIXTURE_FOCUS_OWN_DRAFT");
        require(state(guard).optBoolean("owned"),"CORE_FIXTURE_RECHECK_OWN_DRAFT");
        long now=SystemClock.uptimeMillis();
        sendKeySync(new KeyEvent(now,now,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_A,0,KeyEvent.META_CTRL_ON));
        sendKeySync(new KeyEvent(now,now+30,KeyEvent.ACTION_UP,KeyEvent.KEYCODE_A,0,KeyEvent.META_CTRL_ON));
        sendKeyDownUpSync(KeyEvent.KEYCODE_DEL);SystemClock.sleep(300);
        require(state(guard).optBoolean("empty"),"CORE_FIXTURE_NATIVE_DELETE_APPLIED");
    }
    private void clearOwnedRouteDraft()throws Exception{
        if(!state("const e=document.querySelector('.agy-composer [contenteditable=true]');return JSON.stringify({owned:e?.textContent.trim()==='AGY notification regression draft'});").optBoolean("owned"))return;
        tapExpression("document.querySelector('.agy-composer [contenteditable=true]')","ROUTE_CLEAR_OWN_DRAFT");
        long now=SystemClock.uptimeMillis();
        sendKeySync(new KeyEvent(now,now,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_A,0,KeyEvent.META_CTRL_ON));
        sendKeySync(new KeyEvent(now,now+30,KeyEvent.ACTION_UP,KeyEvent.KEYCODE_A,0,KeyEvent.META_CTRL_ON));
        sendKeyDownUpSync(KeyEvent.KEYCODE_DEL);SystemClock.sleep(300);
    }
    private boolean prepareRegressionChat()throws Exception{
        clearOwnedRouteDraft();
        JSONObject safe=state("const e=document.querySelector('.agy-composer [contenteditable=true]');return JSON.stringify({safe:!!e&&!e.textContent.trim()&&!document.querySelector('.agy-attachment-card'),router:typeof L5!=='undefined'&&!!L5});");
        if(!safe.optBoolean("safe")){report("REGRESSION_FIXTURE","PENDING_EXISTING_DRAFT_PRESERVED");return false;}
        require(safe.optBoolean("router"),"REGRESSION_CORE_ROUTER_PRESENT");
        state("window.__agyRegressionOriginal={path:location.pathname+location.search,index:history.state?.__TSR_index};L5.navigate({to:'/',replace:true});return '{}';");
        require(waitState("return JSON.stringify({ready:location.pathname==='/'});","ready").optBoolean("ready"),"REGRESSION_HOME_READY");
        openRegressionChat();
        return true;
    }
    private void openRegressionChat()throws Exception{
        state("L5.navigate({to:'/c/$cascadeId',params:{cascadeId:'_new'}});return '{}';");
        require(waitState("return JSON.stringify({ready:location.pathname.endsWith('/c/_new')&&!!document.querySelector('.agy-composer-add')});","ready").optBoolean("ready"),"REGRESSION_CHAT_READY");
        SystemClock.sleep(400);
    }
    private void restoreRegressionChat()throws Exception{
        state("document.removeEventListener('change',window.__agyPickerWatch,true);document.querySelectorAll('.agy-attachment-card').forEach(e=>{const n=e.querySelector('.agy-attachment-name')?.textContent;if(n===window.__agyPickerText||n===window.__agyPickerImage)e.querySelector('.agy-attachment-remove')?.click();});const o=window.__agyRegressionOriginal;if(o&&typeof o.index==='number'&&typeof history.state?.__TSR_index==='number'&&history.state.__TSR_index>o.index)history.go(o.index-history.state.__TSR_index);return '{}';");
        SystemClock.sleep(500);
        state("const o=window.__agyRegressionOriginal;if(o)L5.navigate({to:o.path,replace:true});return '{}';");
        require(waitState("return JSON.stringify({same:!window.__agyRegressionOriginal||location.pathname+location.search===window.__agyRegressionOriginal.path});","same").optBoolean("same"),"REGRESSION_ORIGINAL_ROUTE_RESTORED");
    }
    private void tapExpression(String expression,String label)throws Exception{
        JSONObject point=waitState("const e="+expression+",r=e?.getBoundingClientRect(),x=r?r.left+r.width/2:0,y=r?r.top+r.height/2:0,h=document.elementFromPoint(x,y);return JSON.stringify({hit:!!e&&(h===e||e.contains(h)),x,y,vw:innerWidth});","hit");
        tapDesktopPoint(point,label);
    }
    private boolean focusedDev(){
        android.view.accessibility.AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();
        return activity.hasWindowFocus()&&root!=null&&target.getPackageName().equals(String.valueOf(root.getPackageName()));
    }
    private android.view.MotionEvent finger(long down,long time,int action,float x,float y){
        android.view.MotionEvent.PointerProperties properties=new android.view.MotionEvent.PointerProperties();properties.id=0;properties.toolType=android.view.MotionEvent.TOOL_TYPE_FINGER;
        android.view.MotionEvent.PointerCoords point=new android.view.MotionEvent.PointerCoords();point.x=x;point.y=y;point.pressure=1;point.size=1;
        return android.view.MotionEvent.obtain(down,time,action,1,new android.view.MotionEvent.PointerProperties[]{properties},new android.view.MotionEvent.PointerCoords[]{point},0,0,1,1,-1,0,android.view.InputDevice.SOURCE_TOUCHSCREEN,0);
    }
    private boolean clickSystemText(String text,boolean longClick){
        java.util.ArrayList<android.view.accessibility.AccessibilityNodeInfo> roots=new java.util.ArrayList<>();
        android.view.accessibility.AccessibilityNodeInfo active=getUiAutomation().getRootInActiveWindow();
        if(active!=null)roots.add(active);
        for(android.view.accessibility.AccessibilityWindowInfo window:getUiAutomation().getWindows()){
            android.view.accessibility.AccessibilityNodeInfo other=window.getRoot();if(other!=null)roots.add(other);
        }
        for(android.view.accessibility.AccessibilityNodeInfo root:roots){
        if(target.getPackageName().equals(String.valueOf(root.getPackageName())))continue;
        for(android.view.accessibility.AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByText(text)){
            if(!text.contentEquals(node.getText()==null?"":node.getText())
                    &&!text.contentEquals(node.getContentDescription()==null?"":node.getContentDescription()))continue;
            if((longClick||fileSelectionTouch)&&node.isVisibleToUser()){
                try{getUiAutomation().waitForIdle(350,4000);}catch(java.util.concurrent.TimeoutException ignored){}
                node.refresh();
                android.view.accessibility.AccessibilityNodeInfo icon=null,row=node;
                String pkg=String.valueOf(root.getPackageName());
                for(int k=0;longClick&&k<5&&row!=null&&icon==null;k++,row=row.getParent())
                    for(android.view.accessibility.AccessibilityNodeInfo child:row.findAccessibilityNodeInfosByViewId(pkg+":id/icon"))
                        if(child.isVisibleToUser()){icon=child;break;}
                android.graphics.Rect bounds=new android.graphics.Rect();(icon==null?node:icon).getBoundsInScreen(bounds);
                if(!bounds.isEmpty()){
                    long now=SystemClock.uptimeMillis();
                    android.view.MotionEvent down=finger(now,now,android.view.MotionEvent.ACTION_DOWN,bounds.exactCenterX(),bounds.exactCenterY());
                    int hold=longClick&&icon==null?Math.max(1200,android.view.ViewConfiguration.getLongPressTimeout()+300):80;
                    report("PICKER_SYSTEM_LONG_PRESS_MS",hold);
                    android.view.MotionEvent up=finger(now,now+hold,android.view.MotionEvent.ACTION_UP,bounds.exactCenterX(),bounds.exactCenterY());
                    down.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);up.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
                    try{
                        boolean pressed=getUiAutomation().injectInputEvent(down,true);SystemClock.sleep(hold);
                        return getUiAutomation().injectInputEvent(up,true)&&pressed;
                    }finally{down.recycle();up.recycle();}
                }
            }
            android.view.accessibility.AccessibilityNodeInfo current=node;
            for(int i=0;i<7&&current!=null;i++,current=current.getParent()){
                if(longClick?current.isLongClickable():current.isClickable())
                    if(current.performAction(longClick?android.view.accessibility.AccessibilityNodeInfo.ACTION_LONG_CLICK:android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))return true;
            }
        }
        }
        return false;
    }
    private void scrollOwnShade(){
        android.view.accessibility.AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();
        if(root==null||!"com.android.systemui".equals(String.valueOf(root.getPackageName())))return;
        String label=target.getApplicationInfo().loadLabel(target.getPackageManager()).toString();
        for(android.view.accessibility.AccessibilityNodeInfo own:root.findAccessibilityNodeInfosByText(label)){
            if(!label.contentEquals(own.getText()==null?"":own.getText()))continue;
            android.view.accessibility.AccessibilityNodeInfo group=own;
            for(int i=0;i<5&&group!=null;i++,group=group.getParent())
                for(android.view.accessibility.AccessibilityNodeInfo expand:group.findAccessibilityNodeInfosByViewId("android:id/expand_button"))
                    if(expand.isVisibleToUser()&&expand.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))return;
        }
        for(android.view.accessibility.AccessibilityNodeInfo list:root.findAccessibilityNodeInfosByViewId("com.android.systemui:id/notification_stack_scroller")){
            if(!list.isVisibleToUser())continue;
            if(list.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD))return;
            android.graphics.Rect b=new android.graphics.Rect();list.getBoundsInScreen(b);if(b.height()<100)continue;
            long now=SystemClock.uptimeMillis();
            android.view.MotionEvent down=finger(now,now,0,b.exactCenterX(),b.top+b.height()*.8f);
            android.view.MotionEvent move=finger(now,now+250,2,b.exactCenterX(),b.top+b.height()*.25f);
            android.view.MotionEvent up=finger(now,now+280,1,b.exactCenterX(),b.top+b.height()*.25f);
            try{
                down.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);move.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);up.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
                getUiAutomation().injectInputEvent(down,true);SystemClock.sleep(250);getUiAutomation().injectInputEvent(move,true);getUiAutomation().injectInputEvent(up,true);
            }finally{down.recycle();move.recycle();up.recycle();}
            return;
        }
        for(android.view.accessibility.AccessibilityNodeInfo pager:root.findAccessibilityNodeInfosByViewId("com.android.systemui:id/panel_view_pager"))
            pager.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
    }
    private void safeShadeIds(android.view.accessibility.AccessibilityNodeInfo node,java.util.Set<String> ids){
        if(node==null||ids.size()>100)return;
        String id=node.getViewIdResourceName();if(id!=null)ids.add(id);
        for(int i=0;i<node.getChildCount();i++)safeShadeIds(node.getChild(i),ids);
    }
    private boolean clickSystemId(String id){
        android.view.accessibility.AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();
        if(root==null||target.getPackageName().equals(String.valueOf(root.getPackageName())))return false;
        for(android.view.accessibility.AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByViewId(id))
            if(node.isVisibleToUser()&&node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))return true;
        return false;
    }
    private void openMediaPicker()throws Exception{
        tapExpression("document.querySelector('.agy-composer-add')","PICKER_ADD");
        require(waitState("return JSON.stringify({ready:!!document.querySelector('dialog.agy-composer-sheet[open]')});","ready").optBoolean("ready"),"PICKER_MENU_OPEN");
        JSONObject english=state("const d=document.querySelector('dialog.agy-composer-sheet[open]');return JSON.stringify({english:d?.querySelector('strong')?.textContent==='Add to message'&&[...d.querySelectorAll('.agy-composer-action')].some(e=>e.textContent.trim()==='Photos & files')});");
        require(english.optBoolean("english"),"PICKER_MENU_ENGLISH");
        tapExpression("[...document.querySelectorAll('dialog.agy-composer-sheet[open] .agy-composer-action')].find(e=>e.textContent.trim()==='Photos & files')","PICKER_MEDIA");
    }
    private boolean hasCard(String name)throws Exception{
        return state("return JSON.stringify({found:[...document.querySelectorAll('.agy-attachment-name')].some(e=>e.textContent==="+JSONObject.quote(name)+")});").optBoolean("found");
    }
    private void removeCard(String name)throws Exception{
        tapExpression("[...document.querySelectorAll('.agy-attachment-card')].find(e=>e.querySelector('.agy-attachment-name')?.textContent==="+JSONObject.quote(name)+")?.querySelector('.agy-attachment-remove')","PICKER_REMOVE");
        require(waitState("return JSON.stringify({ready:![...document.querySelectorAll('.agy-attachment-name')].some(e=>e.textContent==="+JSONObject.quote(name)+")});","ready").optBoolean("ready"),"PICKER_REMOVE_OWN_FILE");
    }
    private void selectSystemFiles(String[] names,String pickerPackage,String pickerLabel)throws Exception{
        long until=SystemClock.elapsedRealtime()+15000;boolean selected=false;
        do{
            android.view.accessibility.AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();
            String pkg=root==null?"":String.valueOf(root.getPackageName());
            if(!pkg.equals(pickerPackage)&&pickerLabel!=null)clickSystemText(pickerLabel,false);
            if(clickSystemText(names[0],names.length>1)){selected=true;break;}
            SystemClock.sleep(200);
        }while(SystemClock.elapsedRealtime()<until);
        require(selected,"PICKER_REAL_DOCUMENT_FOUND");
        for(int i=1;i<names.length;i++){
            SystemClock.sleep(200);
            android.view.accessibility.AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();
            if(root!=null)for(android.view.accessibility.AccessibilityNodeInfo owned:root.findAccessibilityNodeInfosByText(names[0])){
                android.view.accessibility.AccessibilityNodeInfo row=owned;
                for(int k=0;k<4&&row!=null;k++,row=row.getParent()){
                    report("PICKER_OWN_ROW_"+k,String.valueOf(row.getViewIdResourceName())+":selected="+row.isSelected()+":checked="+row.isChecked()+":click="+row.isClickable()+":long="+row.isLongClickable());
                    for(int j=0;j<row.getChildCount();j++){android.view.accessibility.AccessibilityNodeInfo child=row.getChild(j);if(child!=null)report("PICKER_OWN_ROW_CHILD_"+k+"_"+j,String.valueOf(child.getViewIdResourceName())+":selected="+child.isSelected()+":checked="+child.isChecked()+":click="+child.isClickable());}
                }
                break;
            }
            fileSelectionTouch=true;try{require(clickSystemText(names[i],false),"PICKER_REAL_MULTI_SELECT_"+i);}finally{fileSelectionTouch=false;}
        }
        until=SystemClock.elapsedRealtime()+10000;
        do{
            if(focusedDev())break;
            clickSystemId(pickerPackage+":id/action_menu_select");
            clickSystemId(pickerPackage+":id/action_menu_open");
            android.view.accessibility.AccessibilityNodeInfo pickerRoot=getUiAutomation().getRootInActiveWindow();
            if(pickerRoot!=null&&pickerPackage.equals(String.valueOf(pickerRoot.getPackageName())))clickSystemId("android:id/button1");
            SystemClock.sleep(200);
        }while(SystemClock.elapsedRealtime()<until);
        if(!focusedDev()){
            java.util.Set<String> ids=new java.util.LinkedHashSet<>();safeShadeIds(getUiAutomation().getRootInActiveWindow(),ids);report("PICKER_CONFIRM_RESOURCE_IDS",ids.toString());
        }
        require(focusedDev(),"PICKER_REAL_RESULT_RETURNED");
        report("PICKER_NATIVE_FILELIST_FLAGS",state("return JSON.stringify(window.__agyPickerEvent||{count:-1});").toString());
        for(String name:names)
            require(waitState("return JSON.stringify({ready:[...document.querySelectorAll('.agy-attachment-name')].some(e=>e.textContent==="+JSONObject.quote(name)+")});","ready").optBoolean("ready"),"PICKER_REAL_ATTACHMENT_ADDED");
    }
    private void pickerRegression()throws Exception{
        require(fixtureText!=null&&fixtureImage!=null&&fixtureFolder!=null,"PICKER_FIXTURE_ARGUMENTS");
        if(!prepareRegressionChat())return;
        state("window.__agyPickerText="+JSONObject.quote(fixtureText)+";window.__agyPickerImage="+JSONObject.quote(fixtureImage)+";window.__agyPickerRoute=location.pathname;window.__agyPickerOrigin=performance.timeOrigin;return '{}';");
        final Activity originalActivity=activity;final WebView originalWeb=web;
        final java.util.concurrent.atomic.AtomicInteger requests=new java.util.concurrent.atomic.AtomicInteger();
        final java.util.concurrent.atomic.AtomicReference<String> documentsPackage=new java.util.concurrent.atomic.AtomicReference<>();
        final java.util.concurrent.atomic.AtomicReference<String> documentsLabel=new java.util.concurrent.atomic.AtomicReference<>();
        android.accessibilityservice.AccessibilityServiceInfo service=getUiAutomation().getServiceInfo();int oldFlags=service.flags;
        service.flags|=android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;getUiAutomation().setServiceInfo(service);
        ActivityMonitor monitor=new ActivityMonitor(){
            @Override public ActivityResult onStartActivity(Intent intent){
                Intent nested=Intent.ACTION_CHOOSER.equals(intent.getAction())?(Intent)intent.getParcelableExtra(Intent.EXTRA_INTENT):intent;
                if(nested!=null&&(Intent.ACTION_GET_CONTENT.equals(nested.getAction())||Intent.ACTION_OPEN_DOCUMENT.equals(nested.getAction()))){
                    requests.incrementAndGet();
                    report("PICKER_NATIVE_MULTIPLE_ALLOWED",nested.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE,false));
                    for(android.content.pm.ResolveInfo match:target.getPackageManager().queryIntentActivities(nested,0)){
                        if(match.activityInfo.packageName.contains("documentsui")){
                            nested.setComponent(new android.content.ComponentName(match.activityInfo.packageName,match.activityInfo.name));
                            nested.putExtra("android.provider.extra.INITIAL_URI",android.net.Uri.parse(fixtureFolder));
                            documentsPackage.set(match.activityInfo.packageName);documentsLabel.set(match.loadLabel(target.getPackageManager()).toString());
                            if(nested!=intent)intent.putExtra(Intent.EXTRA_INTENT,nested);break;
                        }
                    }
                }
                return null; // The real system Picker runs; no synthetic ActivityResult.
            }
        };
        addMonitor(monitor);
        try{
            state("window.__agyPickerWatch=e=>{if(e.target?.type==='file'){const fs=[...e.target.files];window.__agyPickerEvent={count:fs.length,text:fs.some(f=>f.name===window.__agyPickerText),image:fs.some(f=>f.name===window.__agyPickerImage)};}};document.addEventListener('change',window.__agyPickerWatch,true);return '{}';");
            tapExpression("document.querySelector('.agy-composer-add')","PICKER_OUTSIDE_ADD");
            waitState("return JSON.stringify({ready:!!document.querySelector('dialog.agy-composer-sheet[open]')});","ready");
            JSONObject outside=state("const d=document.querySelector('dialog.agy-composer-sheet[open]'),r=d.getBoundingClientRect();return JSON.stringify({x:innerWidth/2,y:Math.max(5,r.top-20),vw:innerWidth});");
            final int[] origin=new int[2];runOnMainSync(()->web.getLocationOnScreen(origin));
            float scale=web.getWidth()/(float)outside.getDouble("vw");
            long now=SystemClock.uptimeMillis();
            android.view.MotionEvent down=android.view.MotionEvent.obtain(now,now,0,origin[0]+(float)outside.getDouble("x")*scale,origin[1]+(float)outside.getDouble("y")*scale,0);
            android.view.MotionEvent up=android.view.MotionEvent.obtain(now,now+60,1,down.getX(),down.getY(),0);
            try{sendPointerSync(down);sendPointerSync(up);}finally{down.recycle();up.recycle();}
            require(waitState("return JSON.stringify({ready:!document.querySelector('dialog.agy-composer-sheet[open]')&&location.pathname===window.__agyPickerRoute});","ready").optBoolean("ready"),"PICKER_REAL_BACKDROP_RETAINS_CHAT");
            openMediaPicker();require(documentsPackage.get()!=null,"PICKER_REAL_DOCUMENTSUI_SELECTED");
            selectSystemFiles(new String[]{fixtureText},documentsPackage.get(),documentsLabel.get());
            tapExpression("[...document.querySelectorAll('.agy-attachment-card')].find(e=>e.querySelector('.agy-attachment-name')?.textContent==="+JSONObject.quote(fixtureText)+")?.querySelector('.agy-attachment-open')","PICKER_OPEN_PREVIEW");
            require(waitState("return JSON.stringify({ready:!!document.querySelector('[data-testid=attachment-safe-text]')});","ready").optBoolean("ready"),"PICKER_TEXT_PREVIEW");
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);SystemClock.sleep(400);
            require(state("return JSON.stringify({same:location.pathname===window.__agyPickerRoute&&!document.querySelector('.agy-attachment-dialog')});").optBoolean("same"),"PICKER_PREVIEW_BACK_RETAINS_CHAT");
            removeCard(fixtureText);
            openMediaPicker();selectSystemFiles(new String[]{fixtureText},documentsPackage.get(),documentsLabel.get());removeCard(fixtureText);
            openMediaPicker();selectSystemFiles(new String[]{fixtureText,fixtureImage},documentsPackage.get(),documentsLabel.get());
            require(state("return JSON.stringify({count:document.querySelectorAll('.agy-attachment-card').length});").optInt("count")==2,"PICKER_MULTI_RESULT_TWO_FILES");
            require(state("const cards=[...document.querySelectorAll('.agy-attachment-card')],rects=cards.map(e=>e.getBoundingClientRect());return JSON.stringify({uniform:rects.length===2&&rects.every(r=>Math.abs(r.width-r.height)<1&&Math.abs(r.width-rects[0].width)<1),touch:cards.every(e=>{const r=e.querySelector('.agy-attachment-remove').getBoundingClientRect();return r.width>=48&&r.height>=48}),metadata:cards.filter(e=>!e.classList.contains('agy-attachment-image')).every(e=>{const type=e.querySelector('.agy-attachment-type');return type&&getComputedStyle(type).display!=='none'&&type.textContent.trim()})});").optBoolean("uniform"),"PICKER_UNIFORM_PHOTO_DOCUMENT_TILES");
            require(state("return JSON.stringify({touch:[...document.querySelectorAll('.agy-attachment-remove')].every(e=>{const r=e.getBoundingClientRect();return r.width>=48&&r.height>=48})});").optBoolean("touch"),"PICKER_REMOVE_TARGETS_48PX");
            require(state("const e=document.querySelector('.agy-attachment-file .agy-attachment-type');return JSON.stringify({type:!!e&&getComputedStyle(e).display!=='none'&&!!e.textContent.trim()});").optBoolean("type"),"PICKER_DOCUMENT_TYPE_VISIBLE");
            removeCard(fixtureText);removeCard(fixtureImage);
            openMediaPicker();
            long pickerShown=SystemClock.elapsedRealtime()+8000;
            while(focusedDev()&&SystemClock.elapsedRealtime()<pickerShown)SystemClock.sleep(100);
            require(!focusedDev(),"PICKER_CANCEL_SYSTEM_WINDOW_VISIBLE");
            long until=SystemClock.elapsedRealtime()+10000;
            do{
                if(focusedDev())break;
                sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);SystemClock.sleep(400);
            }while(SystemClock.elapsedRealtime()<until);
            require(focusedDev(),"PICKER_CANCEL_RETURNED");
            require(state("return JSON.stringify({same:location.pathname===window.__agyPickerRoute&&performance.timeOrigin===window.__agyPickerOrigin&&!document.querySelector('.agy-attachment-card')});").optBoolean("same"),"PICKER_CANCEL_AND_DOCUMENT_PRESERVED");
            refreshDesktopHost();require(activity==originalActivity&&web==originalWeb,"PICKER_ACTIVITY_AND_WEBVIEW_REUSED");
            require(requests.get()>=4,"PICKER_NATIVE_CHOOSER_REQUESTS");
            report("ANDROID_REAL_FILE_PICKER","PASS_BACKDROP_SELECT_PREVIEW_REPEAT_MULTI_CANCEL_NO_SEND");
        }finally{
            removeMonitor(monitor);
            service.flags=oldFlags;getUiAutomation().setServiceInfo(service);
            if(!focusedDev()){
                for(int i=0;i<5&&!focusedDev();i++){sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);SystemClock.sleep(300);}
            }
            if(focusedDev())restoreRegressionChat();
        }
    }
    private void notificationRouteRegression()throws Exception{
        if(!prepareRegressionChat())return;
        final Activity host=activity;final WebView originalWeb=web;
        android.app.Application app=activity.getApplication();
        android.app.Application.ActivityLifecycleCallbacks lifecycle=new android.app.Application.ActivityLifecycleCallbacks(){
            private void event(Activity a,String kind){if(target.getPackageName().equals(a.getPackageName()))report("ROUTE_LIFECYCLE_"+kind,a.getClass().getName());}
            public void onActivityCreated(Activity a,Bundle b){event(a,"CREATE");}
            public void onActivityResumed(Activity a){event(a,"RESUME");}
            public void onActivityDestroyed(Activity a){event(a,"DESTROY");}
            public void onActivityStarted(Activity a){}
            public void onActivityPaused(Activity a){}
            public void onActivityStopped(Activity a){}
            public void onActivitySaveInstanceState(Activity a,Bundle b){}
        };
        runOnMainSync(()->app.registerActivityLifecycleCallbacks(lifecycle));
        android.accessibilityservice.AccessibilityServiceInfo service=getUiAutomation().getServiceInfo();int oldFlags=service.flags;
        service.flags|=android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS|android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        getUiAutomation().setServiceInfo(service);
        Class<?> core=Class.forName("com.agy.core.CoreRuntimeState",true,target.getClassLoader());
        int generation=(Integer)field(core,"readyGeneration");
        Class<?> factory=Class.forName("com.agy.ui.NotificationActivityRoute",true,target.getClassLoader());
        Method pending=factory.getMethod("pending",Context.class,String.class);
        state("const e=document.querySelector('.agy-composer [contenteditable=true]');if(e?.textContent.trim()==='AGY notification regression draft'){e.focus();const r=document.createRange();r.selectNodeContents(e);getSelection().removeAllRanges();getSelection().addRange(r);document.execCommand('delete');}return '{}';");
        tapExpression("document.querySelector('.agy-composer [contenteditable=true]')","ROUTE_DRAFT_EDITOR");
        sendStringSync("AGY notification regression draft");SystemClock.sleep(500);
        state("window.__agyRouteDraft=document.querySelector('.agy-composer [contenteditable=true]')?.textContent;window.__agyRouteOrigin=performance.timeOrigin;window.__agyRouteLength=history.length;return '{}';");
        JSONObject ownDraft=state("return JSON.stringify({draft:typeof window.__agyRouteDraft==='string'&&window.__agyRouteDraft.includes('AGY notification regression draft')&&window.__agyRouteDraft.length<100,length:window.__agyRouteDraft?.length||0});");
        report("ROUTE_FIXTURE_DRAFT_LENGTH",ownDraft.optInt("length"));
        require(ownDraft.optBoolean("draft"),"ROUTE_OWN_DRAFT_INSERTED");
        android.app.NotificationManager nm=(android.app.NotificationManager)target.getSystemService(Context.NOTIFICATION_SERVICE);
        if(Build.VERSION.SDK_INT>=33)try{getUiAutomation().grantRuntimePermission("com.agy.dev","android.permission.POST_NOTIFICATIONS");}catch(Exception ignored){}
        require(nm.areNotificationsEnabled(),"ROUTE_NOTIFICATIONS_ENABLED");
        String channel="agy_qa_route_regression_default";
        if(Build.VERSION.SDK_INT>=26){android.app.NotificationChannel c=new android.app.NotificationChannel(channel,"QA navigation",android.app.NotificationManager.IMPORTANCE_DEFAULT);c.setSound(null,null);c.enableVibration(false);nm.createNotificationChannel(c);}
        try{
            for(int cycle=0;cycle<(routeLegacyMode?3:2);cycle++){
                android.app.PendingIntent pi;
                if(cycle<2)pi=(android.app.PendingIntent)pending.invoke(null,target,"_new");
                else{
                    Intent legacy=new Intent().setClassName(target,"com.agy.ui.MainActivity");
                    legacy.setAction("com.agy.action.OPEN_AGENT_CONVERSATION").setData(android.net.Uri.parse("agy://conversation/_new"));
                    legacy.putExtra("agy_conversation_id","_new");
                    legacy.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    pi=android.app.PendingIntent.getActivity(target,99713,legacy,android.app.PendingIntent.FLAG_UPDATE_CURRENT|android.app.PendingIntent.FLAG_IMMUTABLE);
                }
                report("ROUTE_PENDING_CREATOR_"+cycle,pi.getCreatorPackage()+":"+pi.getCreatorUid());
                target.startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                SystemClock.sleep(450);
                android.app.Notification.Builder b=Build.VERSION.SDK_INT>=26?new android.app.Notification.Builder(target,channel):new android.app.Notification.Builder(target);
                nm.notify("agy_qa_route",99712,b.setSmallIcon(android.R.drawable.stat_notify_sync).setContentTitle("AGY route regression").setContentText("Open test conversation").setContentIntent(pi).setAutoCancel(true).build());
                long until=SystemClock.elapsedRealtime()+8000;boolean clicked=false;
                if(routeIntentMode){
                    android.service.notification.StatusBarNotification posted=null;
                    for(android.service.notification.StatusBarNotification item:nm.getActiveNotifications())if("agy_qa_route".equals(item.getTag()))posted=item;
                    require(posted!=null&&posted.getNotification().contentIntent!=null,"ROUTE_POSTED_PENDING_INTENT_"+cycle);
                    // SystemUI taps are allowed to foreground a background app.
                    // Scope the equivalent shell capability to this test send only.
                    getUiAutomation().adoptShellPermissionIdentity("android.permission.START_ACTIVITIES_FROM_BACKGROUND");
                    try{
                        android.app.ActivityOptions options=android.app.ActivityOptions.makeBasic();
                        try{options.getClass().getMethod("setPendingIntentBackgroundActivityStartMode",int.class).invoke(options,1);}catch(NoSuchMethodException olderAndroid){}
                        posted.getNotification().contentIntent.send(target,0,null,null,null,null,options.toBundle());clicked=true;
                    }finally{getUiAutomation().dropShellPermissionIdentity();}
                }else require(getUiAutomation().performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS),"ROUTE_OPEN_NOTIFICATION_SHADE_"+cycle);
                int searches=0;
                while(!clicked&&SystemClock.elapsedRealtime()<until){if(clickSystemText("AGY route regression",false)){clicked=true;break;}if(++searches%4==0)scrollOwnShade();SystemClock.sleep(200);}
                if(!clicked){
                    android.view.accessibility.AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();
                    report("ROUTE_SHADE_ROOT_PACKAGE",root==null?"null":root.getPackageName());
                    report("ROUTE_SHADE_OWN_TITLE_MATCHES",root==null?0:root.findAccessibilityNodeInfosByText("AGY route regression").size());
                    int active=0;for(android.service.notification.StatusBarNotification n:nm.getActiveNotifications())if("agy_qa_route".equals(n.getTag()))active++;
                    report("ROUTE_OWN_ACTIVE_NOTIFICATIONS",active);
                    java.util.Set<String> ids=new java.util.LinkedHashSet<>();safeShadeIds(root,ids);report("ROUTE_SHADE_RESOURCE_IDS",ids.toString());
                }
                require(clicked,(routeIntentMode?"ROUTE_POSTED_INTENT_SENT_":"ROUTE_REAL_NOTIFICATION_TAPPED_")+cycle);
                until=SystemClock.elapsedRealtime()+12000;
                do{refreshDesktopHost();if(focusedDev()&&Boolean.TRUE.equals(field(activity,"webUiReady")))break;SystemClock.sleep(150);}while(SystemClock.elapsedRealtime()<until);
                if(!focusedDev()){
                    android.app.ActivityManager manager=(android.app.ActivityManager)target.getSystemService(Context.ACTIVITY_SERVICE);
                    for(android.app.ActivityManager.AppTask task:manager.getAppTasks()){android.app.ActivityManager.RecentTaskInfo info=task.getTaskInfo();report("ROUTE_OWN_TASK",info.id+":"+info.baseActivity+":"+info.topActivity);}
                }
                require(focusedDev(),"ROUTE_DEV_RETURNED_TO_FOREGROUND_"+cycle);
                require(activity==host&&web==originalWeb,"ROUTE_SAME_ACTIVITY_WEBVIEW_"+cycle);
                Object splash=field(activity,"splash");View splashView=(View)splash.getClass().getMethod("view").invoke(splash);
                require(splashView.getVisibility()==View.GONE&&Boolean.TRUE.equals(field(activity,"webUiReady")),"ROUTE_NO_STARTUP_RESTART_"+cycle);
                require(state("return JSON.stringify({same:location.pathname.endsWith('/c/_new')&&performance.timeOrigin===window.__agyRouteOrigin&&history.length===window.__agyRouteLength&&document.querySelector('.agy-composer [contenteditable=true]')?.textContent===window.__agyRouteDraft});").optBoolean("same"),"ROUTE_DOCUMENT_HISTORY_DRAFT_PRESERVED_"+cycle);
                require(generation==(Integer)field(core,"readyGeneration"),"ROUTE_CORE_GENERATION_PRESERVED_"+cycle);
            }
            clearOwnedRouteDraft();
            runOnMainSync(()->((android.view.inputmethod.InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(web.getWindowToken(),0));
            SystemClock.sleep(350);sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            require(waitState("return JSON.stringify({home:location.pathname==='/'});","home").optBoolean("home"),"ROUTE_ONE_BACK_TO_HOME_NO_DUPLICATE_CHAT");
            report("ANDROID_NOTIFICATION_REUSE",routeLegacyMode?"PASS_POSTED_PENDING_INTENTS_WARM_LEGACY_SAME_HOST_DOCUMENT_DRAFT_AND_BACK":routeIntentMode?"PASS_POSTED_PENDING_INTENTS_SAME_HOST_DOCUMENT_DRAFT_AND_BACK":"PASS_REAL_SHADE_TAPS_SAME_HOST_DOCUMENT_DRAFT_AND_BACK");
        }finally{
            runOnMainSync(()->app.unregisterActivityLifecycleCallbacks(lifecycle));
            nm.cancel("agy_qa_route",99712);
            service.flags=oldFlags;getUiAutomation().setServiceInfo(service);
            if(!focusedDev()){
                getUiAutomation().performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);
                target.startActivity(new Intent().setComponent(activity.getComponentName()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));
                SystemClock.sleep(650);refreshDesktopHost();
            }
            if(focusedDev()){clearOwnedRouteDraft();restoreRegressionChat();}
        }
    }

    private void systemBarThemes()throws Exception{
        Class<?> styles=Class.forName("com.agy.R$style",true,target.getClassLoader());
        int[] attrs={android.R.attr.statusBarColor,android.R.attr.navigationBarColor,
            android.R.attr.windowLightStatusBar,android.R.attr.windowLightNavigationBar,android.R.attr.windowBackground};
        for(boolean night:new boolean[]{false,true})for(boolean light:new boolean[]{false,true}){
            android.content.res.Configuration cfg=new android.content.res.Configuration(target.getResources().getConfiguration());
            cfg.uiMode=(cfg.uiMode&~android.content.res.Configuration.UI_MODE_NIGHT_MASK)|
                (night?android.content.res.Configuration.UI_MODE_NIGHT_YES:android.content.res.Configuration.UI_MODE_NIGHT_NO);
            Context configured=target.createConfigurationContext(cfg);
            int id=styles.getField(light?"SplashThemeLight":"SplashThemeDark").getInt(null);
            android.view.ContextThemeWrapper themed=new android.view.ContextThemeWrapper(configured,id);
            android.content.res.TypedArray a=themed.obtainStyledAttributes(attrs);
            int bg=light?0xffffffff:0xff101010;
            boolean matches;
            try{
                report("SYSTEM_SPLASH_RESOURCE_"+(light?"LIGHT":"DARK")+"_"+(night?"NIGHT":"DAY"),
                    String.format(java.util.Locale.ROOT,"status=%08x,nav=%08x,lightStatus=%b,lightNav=%b,bg=%08x",
                        a.getColor(0,0),a.getColor(1,0),a.getBoolean(2,false),a.getBoolean(3,false),a.getColor(4,0)));
                matches=a.getColor(0,0)==bg&&a.getColor(1,0)==bg&&a.getBoolean(2,false)==light
                    &&a.getBoolean(3,false)==light&&a.getColor(4,0)==bg;
            }finally{a.recycle();}
            require(matches,"SYSTEM_SPLASH_"+(light?"LIGHT":"DARK")+"_"+(night?"NIGHT":"DAY"));
        }
        report("SYSTEM_SPLASH_PALETTES","PASS_REAL_ANDROID_DAY_NIGHT_RESOLUTION");
    }

    /** Observe only geometry/booleans: never log account, prompt or chat text. */
    private void startupHandoff()throws Exception{
        JSONObject safe=state("const e=document.querySelector('.agy-composer [contenteditable=true]');return JSON.stringify({empty:!!e&&!e.textContent.trim()&&!document.querySelector('.agy-attachment-card')});");
        if(!safe.optBoolean("empty")){report("STARTUP_HANDOFF","PENDING_EXISTING_DRAFT_OR_NONCOMPOSER");return;}
        Class<?> core=Class.forName("com.agy.core.CoreRuntimeState",true,target.getClassLoader());
        int generation=(Integer)field(core,"readyGeneration");
        Method load=Class.forName("com.agy.ui.MainActivity",true,target.getClassLoader()).getDeclaredMethod("loadCoreUi",int.class);load.setAccessible(true);
        runOnMainSync(()->{try{load.invoke(activity,generation);}catch(Exception error){throw new RuntimeException(error);}});
        final int[] barBefore={0},flagsBefore={0};
        runOnMainSync(()->{barBefore[0]=activity.getWindow().getStatusBarColor();flagsBefore[0]=activity.getWindow().getDecorView().getSystemUiVisibility()&View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;});
        long start=SystemClock.elapsedRealtime(),readyAt=0;boolean stable=true,barsStable=true;int samples=0;
        String previous="";
        do{
            JSONObject snap;
            try{snap=state("const e=document.querySelector('.agy-composer [contenteditable=true]'),h=document.querySelector('[data-testid=mobile-open-settings]'),r=h?.getBoundingClientRect();return JSON.stringify({composer:!!e,buttons:document.querySelectorAll('#root button').length,fonts:document.fonts?.status||'unsupported',document:document.readyState,header:r?[Math.round(r.x),Math.round(r.y),Math.round(r.width),Math.round(r.height)]:null,gate:!!window.__agStartupReady,now:Math.round(performance.now())});");
            }catch(IllegalStateException navigationSwap){
                if(Boolean.TRUE.equals(field(activity,"webUiReady")))throw navigationSwap;
                report("HANDOFF_NAVIGATION_WINDOW","RETRY_PENDING_DOCUMENT");continue;
            }
            boolean ready=Boolean.TRUE.equals(field(activity,"webUiReady"));
            View splash=(View)field(activity,"splash").getClass().getMethod("view").invoke(field(activity,"splash"));
            snap.put("ready",ready);snap.put("splash",splash.getVisibility()==View.VISIBLE);snap.put("opaque",splash.getAlpha()==1f);
            snap.put("elapsed",SystemClock.elapsedRealtime()-start);
            final int[] currentBar={0},currentFlags={0};
            runOnMainSync(()->{currentBar[0]=activity.getWindow().getStatusBarColor();currentFlags[0]=activity.getWindow().getDecorView().getSystemUiVisibility()&View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;});
            snap.put("statusBar",String.format(java.util.Locale.ROOT,"%08x",currentBar[0]));
            snap.put("darkStatusIcons",currentFlags[0]!=0);
            if(currentBar[0]!=barBefore[0]||currentFlags[0]!=flagsBefore[0])barsStable=false;
            if(!ready&&splash.getVisibility()==View.VISIBLE&&splash.getAlpha()!=1f)stable=false;
            if(ready){if(readyAt==0)readyAt=SystemClock.elapsedRealtime();if(!snap.optBoolean("composer")||!"complete".equals(snap.optString("document"))||"loading".equals(snap.optString("fonts")))stable=false;}
            String key=snap.optBoolean("composer")+":"+snap.optInt("buttons")+":"+snap.optString("fonts")+":"+snap.optString("document")+":"+snap.opt("header")+":"+ready+":"+snap.opt("statusBar")+":"+snap.optBoolean("darkStatusIcons");
            if(!key.equals(previous)){report("HANDOFF_SAMPLE",snap.toString());previous=key;}
            samples++;SystemClock.sleep(40);
        }while(SystemClock.elapsedRealtime()-start<30000&&(readyAt==0||SystemClock.elapsedRealtime()-readyAt<1800));
        require(readyAt>0,"HANDOFF_UI_READY");
        require(generation==(Integer)field(core,"readyGeneration"),"HANDOFF_CORE_UNCHANGED");
        report("HANDOFF_OBSERVED_STABLE",stable?"PASS":"FAIL_EARLY_OR_TRANSPARENT");
        require(barsStable,"HANDOFF_SYSTEM_BAR_STABLE");
        report("HANDOFF_SAMPLE_COUNT",samples);
    }

    private void nativeStartup()throws Exception {
        JSONObject readyProof=state("return JSON.stringify({current:typeof window.__agStartupReady?.signal==='function'&&window.__agStartupReady.signal.toString().includes('hasRenderedUi')});");
        require(readyProof.optBoolean("current"),"STARTUP_REAL_READY_PROOF_LOADED");
        JSONObject themeProof=state("return JSON.stringify({committed:typeof window.__agThemeSync?.releaseStartup==='function'&&window.__AG_NATIVE_UI_STARTING__===false});");
        require(themeProof.optBoolean("committed"),"STARTUP_NATIVE_THEME_RELEASED_AFTER_COMMIT");
        Object live=field(activity,"splash");
        require(Boolean.TRUE.equals(field(activity,"webUiReady")),"STARTUP_REAL_UI_READY");
        View liveView=(View)live.getClass().getMethod("view").invoke(live);
        require(!(liveView instanceof WebView)&&liveView.getVisibility()==View.GONE,"STARTUP_NATIVE_DISMISSED_ON_REAL_READY");
        Class<?> state=Class.forName("com.agy.core.CoreRuntimeState",true,target.getClassLoader());
        int before=(Integer)field(state,"readyGeneration");
        Object actualProgress=field(state,"startup");
        Object actualSnapshot=actualProgress.getClass().getMethod("snapshot").invoke(actualProgress);
        require(((Integer)field(actualSnapshot,"stage"))==2&&((Long)field(actualSnapshot,"attempt"))>0,"STARTUP_REAL_CORE_MILESTONE");

        Class<?> progressClass=Class.forName("com.agy.core.CoreStartupProgress",true,target.getClassLoader());
        Object model=progressClass.getConstructor().newInstance();
        long attempt=(Long)progressClass.getMethod("begin").invoke(model);
        final FrameLayoutBox box=new FrameLayoutBox();
        final int[] retries={0};
        runOnMainSync(()->{
            try{
                box.root=new android.widget.FrameLayout(activity);
                ((ViewGroup)activity.getWindow().getDecorView()).addView(box.root,new ViewGroup.LayoutParams(-1,-1));
                box.controller=live.getClass().getConstructor(Context.class,Class.forName("com.agy.theme.ThemeManager",true,target.getClassLoader()),android.widget.FrameLayout.class).newInstance(activity,field(activity,"themeManager"),box.root);
                box.view=(View)box.controller.getClass().getMethod("view").invoke(box.controller);
                box.root.addView(box.view,new android.widget.FrameLayout.LayoutParams(-1,-1));
                Class<?> retry=Class.forName("com.agy.ui.splash.SplashRetryListener",true,target.getClassLoader());
                Object listener=java.lang.reflect.Proxy.newProxyInstance(retry.getClassLoader(),new Class[]{retry},(proxy,method,args)->{
                    if("onRetry".equals(method.getName())){retries[0]++;box.controller.getClass().getMethod("clearFatal").invoke(box.controller);box.controller.getClass().getMethod("showConnecting").invoke(box.controller);}return null;
                });
                box.controller.getClass().getMethod("setRetryListener",retry).invoke(box.controller,listener);
                box.controller.getClass().getMethod("loadInitial").invoke(box.controller);
            }catch(Exception error){throw new RuntimeException(error);}
        });
        try{
            Method systemExit=Class.forName("com.agy.ui.splash.SplashAnim31",true,target.getClassLoader()).getDeclaredMethod("animateExit",Object.class,View.class);
            systemExit.setAccessible(true);
            runOnMainSync(()->{try{systemExit.invoke(null,null,box.view);}catch(Exception error){throw new RuntimeException(error);}});
            require(box.view.getAlpha()==1f,"STARTUP_SYSTEM_EXIT_NATIVE_OPAQUE");
            String[] labels={"Preparing environment","Starting Core","Connecting interface"};
            for(int stage=0;stage<3;stage++){
                progressClass.getMethod("advance",long.class,int.class).invoke(model,attempt,stage);
                Object snapshot=progressClass.getMethod("snapshot").invoke(model);
                runOnMainSync(()->{try{box.controller.getClass().getMethod("showProgress",snapshot.getClass()).invoke(box.controller,snapshot);}catch(Exception error){throw new RuntimeException(error);}});
                SystemClock.sleep(200);
                final int current=stage;
                runOnMainSync(()->{
                    android.widget.TextView status=box.view.findViewWithTag("startup-status");
                    android.widget.TextView step=box.view.findViewWithTag("startup-step");
                    require(labels[current].contentEquals(status.getText())&&("Stage "+(current+1)+" of 3").contentEquals(step.getText()),"STARTUP_STAGE_"+current);
                });
            }
            for(int palette=0;palette<2;palette++){
                final int bg=palette==0?0xff101010:0xffffffff,fg=palette==0?0xffffffff:0xff101010;
                runOnMainSync(()->{try{box.view.getClass().getMethod("applyPalette",int.class,int.class).invoke(box.view,bg,fg);}catch(Exception error){throw new RuntimeException(error);}});
                SystemClock.sleep(150);
                captureNativeStartup(box.view,palette==0?"STARTUP_DARK":"STARTUP_LIGHT");
            }
            runOnMainSync(()->{try{box.controller.getClass().getMethod("showFatalError",String.class).invoke(box.controller,"ui_ready_timeout");}catch(Exception error){throw new RuntimeException(error);}});
            runOnMainSync(()->{
                View bar=box.view.findViewWithTag("startup-progress"),retry=box.view.findViewWithTag("startup-retry");
                android.widget.TextView status=box.view.findViewWithTag("startup-status");
                require(bar.getVisibility()==View.GONE&&retry.getVisibility()==View.VISIBLE&&status.getText().toString().contains("interface"),"STARTUP_RETRYABLE_ERROR");
            });
            captureNativeStartup(box.view,"STARTUP_ERROR");
            runOnMainSync(()->box.view.findViewWithTag("startup-retry").performClick());
            require(retries[0]==1,"STARTUP_NATIVE_RETRY_CALLBACK");
            runOnMainSync(()->{try{box.controller.getClass().getMethod("dismiss").invoke(box.controller);}catch(Exception error){throw new RuntimeException(error);}});
            require(box.view.getVisibility()==View.GONE,"STARTUP_NATIVE_DISMISS");
            require(before==(Integer)field(state,"readyGeneration"),"STARTUP_FIXTURE_NO_CORE_RESTART");
            report("ANDROID_NATIVE_STARTUP","PASS_REAL_READY_AND_ISOLATED_NATIVE_STAGE_ERROR_RETRY");
        }finally{
            runOnMainSync(()->{try{box.controller.getClass().getMethod("destroy").invoke(box.controller);}catch(Exception ignored){}((ViewGroup)box.root.getParent()).removeView(box.root);});
        }
        JSONObject draft=state("return JSON.stringify({empty:!!document.querySelector('.agy-composer [contenteditable=true]')&&[...document.querySelectorAll('.agy-composer [contenteditable=true]')].every(e=>!e.textContent.trim())&&!document.querySelector('.agy-attachment-card')});");
        if(draft.optBoolean("empty")){
            runOnMainSync(()->{try{activity.getClass().getMethod("showFatalError",String.class).invoke(activity,"ui_ready_timeout");}catch(Exception error){throw new RuntimeException(error);}});
            require(liveView.getVisibility()==View.VISIBLE,"STARTUP_MAIN_ERROR_VISIBLE");
            runOnMainSync(()->liveView.findViewWithTag("startup-retry").performClick());
            long until=SystemClock.elapsedRealtime()+30000;
            while(!Boolean.TRUE.equals(field(activity,"webUiReady"))&&SystemClock.elapsedRealtime()<until)SystemClock.sleep(150);
            require(Boolean.TRUE.equals(field(activity,"webUiReady"))&&liveView.getVisibility()==View.GONE,"STARTUP_MAIN_RETRY_UI_READY");
            require(before==(Integer)field(state,"readyGeneration"),"STARTUP_MAIN_RETRY_PRESERVES_CORE");
        }else report("STARTUP_MAIN_RETRY","PENDING_EXISTING_DRAFT_PRESERVED");
        Activity original=activity;
        target.startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        SystemClock.sleep(550);
        Intent foreground=target.getPackageManager().getLaunchIntentForPackage("com.agy.dev");
        foreground.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        target.startActivity(foreground);
        long resumedUntil=SystemClock.elapsedRealtime()+20000;
        do{
            Object current=field(Class.forName("com.agy.ui.MainActivity",true,target.getClassLoader()),"activeActivity");
            if(current instanceof Activity)activity=(Activity)current;
            if(activity.hasWindowFocus()&&Boolean.TRUE.equals(field(activity,"webUiReady")))break;
            SystemClock.sleep(150);
        }while(SystemClock.elapsedRealtime()<resumedUntil);
        Object resumedSplash=field(activity,"splash");
        View resumedView=(View)resumedSplash.getClass().getMethod("view").invoke(resumedSplash);
        require(activity.hasWindowFocus()&&Boolean.TRUE.equals(field(activity,"webUiReady"))&&resumedView.getVisibility()==View.GONE,"STARTUP_BACKGROUND_RESUME");
        require(before==(Integer)field(state,"readyGeneration"),"STARTUP_BACKGROUND_CORE_PRESERVED");
        report("STARTUP_BACKGROUND_ACTIVITY",activity==original?"PASS_SAME_ACTIVITY":"PENDING_OS_RECREATED_ACTIVITY");
    }
    private static final class FrameLayoutBox { android.widget.FrameLayout root; Object controller; View view; }
    private void captureNativeStartup(View owned,String tag)throws Exception{
        if(!capture)return;
        final AtomicReference<android.graphics.Bitmap> output=new AtomicReference<>();
        runOnMainSync(()->{
            if(owned.getWidth()==0||owned.getHeight()==0)return;
            int width=480,height=Math.round(owned.getHeight()*480f/owned.getWidth());
            android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(width,height,android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas canvas=new android.graphics.Canvas(bitmap);canvas.scale(480f/owned.getWidth(),480f/owned.getWidth());
            owned.draw(canvas);output.set(bitmap);
        });
        android.graphics.Bitmap bitmap=output.get();if(bitmap==null){report("CAPTURE_"+tag,"PENDING_UNMEASURED");return;}
        java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG,85,bytes);bitmap.recycle();
        String data=android.util.Base64.encodeToString(bytes.toByteArray(),android.util.Base64.NO_WRAP);
        for(int i=0,part=0;i<data.length();i+=8000,part++)report("SCREENSHOT_"+tag+"_PART_"+part,data.substring(i,Math.min(data.length(),i+8000)));
    }
    private void unifiedInterface()throws Exception{
        closeOwnedSheets();SystemClock.sleep(500);
        JSONObject initial=state("window.__agyUnifiedDraft=JSON.stringify([...document.querySelectorAll('.agy-composer [contenteditable=true]')].map(e=>e.textContent));window.__agyUnifiedPermission=document.querySelector('[data-testid=permissions-selector-trigger]')?.getAttribute('aria-label');return JSON.stringify({css:!![...document.styleSheets].find(s=>s.href?.endsWith('/mobile-interface.css'))});");
        require(initial.optBoolean("css"),"UNIFIED_STYLESHEET_LOADED");
        for(int cycle=0;cycle<2;cycle++){
            state("document.querySelector('.agy-composer-add')?.click();return '{}';");SystemClock.sleep(650);
            JSONObject info=state("const d=document.querySelector('dialog.agy-composer-sheet[open]'),f=document.querySelector('.agy-composer-footer'),r=d?.getBoundingClientRect(),a=f?.getBoundingClientRect(),v=visualViewport;return JSON.stringify({fits:!!r&&r.left>=0&&r.right<=innerWidth+1&&r.top>=(v?.offsetTop||0)&&r.bottom<=a.top-4,compact:!!r&&r.width<=332&&parseFloat(getComputedStyle(d).borderBottomLeftRadius)>=24,rows:!!d&&[...d.querySelectorAll('.agy-composer-action')].every(e=>e.getBoundingClientRect().height>=48)});");
            require(info.optBoolean("fits")&&info.optBoolean("compact")&&info.optBoolean("rows"),"UNIFIED_ADD_CARD_"+cycle);
            if(cycle==0)captureRegion("dialog.agy-composer-sheet","UNIFIED_ADD");
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);SystemClock.sleep(650);
            info=state("return JSON.stringify({closed:!document.querySelector('dialog.agy-composer-sheet[open]')});");
            require(info.optBoolean("closed"),"UNIFIED_ADD_BACK_"+cycle);assertHost("UNIFIED_ADD_HOST_"+cycle);
        }
        state("document.querySelector('[data-testid=model-selector-trigger]')?.click();return '{}';");SystemClock.sleep(650);
        JSONObject info=state("const p=document.querySelector('[data-testid=model-selector-panel]'),d=p?.closest('.agy-menu-sheet'),r=d?.getBoundingClientRect();return JSON.stringify({card:!!d&&r.width<=innerWidth-14&&parseFloat(getComputedStyle(d).borderBottomLeftRadius)>=24});");
        require(info.optBoolean("card"),"UNIFIED_MODEL_CARD");captureRegion(".agy-menu-sheet:has([data-testid=model-selector-panel])","UNIFIED_MODEL");
        sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);SystemClock.sleep(650);
        state("document.querySelector('[data-testid=permissions-selector-trigger]')?.click();return '{}';");SystemClock.sleep(650);
        info=state("const p=document.querySelector('[data-testid=permissions-project-options]'),d=p?.closest('.agy-menu-sheet');return JSON.stringify({card:!!d&&parseFloat(getComputedStyle(d).borderBottomLeftRadius)>=24});");
        require(info.optBoolean("card"),"UNIFIED_PERMISSIONS_CARD");captureRegion(".agy-menu-sheet:has([data-testid=permissions-project-options])","UNIFIED_PERMISSIONS");
        sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);SystemClock.sleep(650);
        info=state("return JSON.stringify({same:window.__agyUnifiedPermission===document.querySelector('[data-testid=permissions-selector-trigger]')?.getAttribute('aria-label')&&window.__agyUnifiedDraft===JSON.stringify([...document.querySelectorAll('.agy-composer [contenteditable=true]')].map(e=>e.textContent))});");
        require(info.optBoolean("same"),"UNIFIED_DRAFT_AND_PERMISSIONS_PRESERVED");
        keyboard();
        report("ANDROID_UNIFIED_INTERFACE","PASS_ADD_MODEL_PERMISSIONS_AND_BACK");
    }
    private void greySurfaces()throws Exception{
        require(activity.hasWindowFocus(),"GREY_DEV_WINDOW_FOCUSED");
        closeOwnedSheets();SystemClock.sleep(500);captureRegion(".agy-composer-footer","GREY_TOOLBAR");
        JSONObject info=state("const e=document.querySelector('[data-testid=model-selector-trigger]'),c=e&&getComputedStyle(e).backgroundColor;return JSON.stringify({colored:!!c&&c!=='rgb(0, 0, 0)'&&c!=='rgba(0, 0, 0, 0)'});");
        require(info.optBoolean("colored"),"GREY_MODEL_SURFACE");
        state("document.querySelector('.agy-composer-add')?.click();return '{}';");SystemClock.sleep(550);
        captureRegion("dialog.agy-composer-sheet","GREY_ADD");
        state("window.__AGY_CLOSE_MOBILE_OVERLAY__?.();return '{}';");SystemClock.sleep(450);
        openAppearance();SystemClock.sleep(500);captureRegion(".agy-settings-page","SETTINGS_DARK");
        closeOwnedSheets();report("ANDROID_GREY_SURFACES","PASS_TARGET_FOCUS_NO_SETTING_CHANGE");
    }
    private void closeOwnedSheets()throws Exception{
        for(int i=0;i<8;i++){
            JSONObject info=state("const d=document.querySelector('dialog[open]');return JSON.stringify({open:!!d||[...document.querySelectorAll('[data-testid=bottom-sheet]')].some(e=>!e.closest('[aria-hidden=true]')&&getComputedStyle(e).visibility!=='hidden'&&e.getBoundingClientRect().height>0)});");
            if(!info.optBoolean("open"))return;
            state("window.__AGY_CLOSE_MOBILE_OVERLAY__?.();return '{}';");SystemClock.sleep(450);
        }
    }
    private void openAppearance()throws Exception{
        closeOwnedSheets();
        JSONObject info=state("const b=[...document.querySelectorAll('button')].find(e=>e.getBoundingClientRect().width>0&&!e.closest('[inert]')&&/^settings$/i.test((e.getAttribute('aria-label')||e.title||e.textContent).trim()));b?.click();return JSON.stringify({available:!!b});");
        require(info.optBoolean("available"),"POLISH_SETTINGS_TRIGGER");SystemClock.sleep(450);
        state("document.querySelector('[data-testid=\"settings-nav-item-Appearance\"]')?.click();return '{}';");
        info=waitState("return JSON.stringify({ready:!!document.querySelector('.agy-settings-page .agy-settings-select-trigger')});","ready");
        require(info.optBoolean("ready"),"POLISH_APPEARANCE_PRESENT");
    }
    private void settingsPolish()throws Exception{
        Object manager=field(activity,"themeManager");
        int nativeMode=(Integer)manager.getClass().getMethod("mode").invoke(manager);
        Class<?> colors=Class.forName("com.agy.theme.ThemeColors",true,target.getClassLoader());
        int dark=colors.getField("MODE_DARK").getInt(null),light=colors.getField("MODE_LIGHT").getInt(null);
        String originalNative=nativeMode==dark?"dark":nativeMode==light?"light":"system";
        state("window.__agyPolishDraft=JSON.stringify([...document.querySelectorAll('.agy-composer [contenteditable=true]')].map(x=>x.textContent));window.__agyPolishSection=t=>[...document.querySelectorAll('.agy-settings-page .space-y-2')].find(e=>e.querySelector('h3')?.textContent===t);window.__agyPolishThemeRow=()=>[...document.querySelectorAll('.agy-settings-row')].find(e=>e.querySelector('span')?.textContent==='Theme');window.__agyPolishOption=t=>[...document.querySelectorAll('.agy-settings-select-sheet [role=option]')].find(e=>e.querySelector('div span')?.textContent===t);return '{}';");
        try{
            closeOwnedSheets();
            SystemClock.sleep(450);captureRegion(".agy-composer-footer","GREY_TOOLBAR");
            state("document.querySelector('.agy-composer-add')?.click();return '{}';");SystemClock.sleep(500);
            captureRegion("dialog.agy-composer-sheet","GREY_ADD");
            state("window.__AGY_CLOSE_MOBILE_OVERLAY__?.();return '{}';");SystemClock.sleep(450);
            openAppearance();
            JSONObject info=state("const row=window.__agyPolishThemeRow(),buttons=[...(row?.querySelectorAll('button')||[])],active=buttons.find(e=>e.hasAttribute('data-pressed')||e.getAttribute('aria-pressed')==='true');window.__agyPolishThemeLabel=active?.getAttribute('aria-label');window.__agyPolishPresetStorage={light:localStorage.getItem('theme-preset-light'),dark:localStorage.getItem('theme-preset-dark')};return JSON.stringify({theme:!!active,painted:buttons.map(e=>{const c=getComputedStyle(e);return e.getBoundingClientRect().height-parseFloat(c.borderTopWidth)-parseFloat(c.borderBottomWidth)}),touch:buttons.every(e=>e.getBoundingClientRect().height>=47.5),switches:[...document.querySelectorAll('.agy-settings-page [role=switch]')].every(e=>{const r=e.getBoundingClientRect();return r.width>=36&&r.width<=52&&r.height<=28&&r.width>r.height*1.5})});");
            require(info.optBoolean("theme"),"POLISH_THEME_STATE_READABLE");
            require(info.optBoolean("touch")&&info.optBoolean("switches"),"POLISH_TOUCH_AND_SWITCH_GEOMETRY");
            org.json.JSONArray heights=info.getJSONArray("painted");boolean compact=heights.length()>0;
            for(int i=0;i<heights.length();i++)compact&=heights.getDouble(i)>=37&&heights.getDouble(i)<=40;
            require(compact,"POLISH_SETTINGS_PAINTED_HEIGHT_MINUS_20_PERCENT");
            for(String mode:new String[]{"Dark","Light"}){
                state("window.__agyPolishThemeRow()?.querySelector('[aria-label=\""+mode+"\"]')?.click();return '{}';");SystemClock.sleep(900);
                info=state("const e=window.__agyPolishSection("+JSONObject.quote(mode+" Theme")+");return JSON.stringify({ready:!!e});");
                require(info.optBoolean("ready"),"POLISH_"+mode+"_THEME_APPLIED");
                final int[] nativeBar={0},nativeIcons={0};
                runOnMainSync(()->{nativeBar[0]=activity.getWindow().getStatusBarColor();nativeIcons[0]=activity.getWindow().getDecorView().getSystemUiVisibility()&View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;});
                require(nativeBar[0]==("Dark".equals(mode)?0xff101010:0xffffffff)&&(nativeIcons[0]!=0)=="Light".equals(mode),"POLISH_"+mode+"_NATIVE_SYSTEM_BARS");
                info=state("const row=window.__agyPolishThemeRow(),group=row.closest('.agy-settings-group'),active=row.querySelector('button[data-pressed],button[aria-pressed=true]'),switches=[...document.querySelectorAll('.agy-settings-group [role=switch]')];const bg=e=>getComputedStyle(e).backgroundColor;return JSON.stringify({contrast:!!active&&bg(active)!=='rgba(0, 0, 0, 0)'&&bg(active)!==bg(group)&&switches.every(e=>bg(e)!==bg(e.closest('.agy-settings-group')))});");
                require(info.optBoolean("contrast"),"POLISH_"+mode+"_SELECTED_AND_SWITCH_CONTRAST");
                SystemClock.sleep(300);captureRegion(".agy-settings-page","SETTINGS_"+mode.toUpperCase());
                state("window.__agyPolishSection("+JSONObject.quote(mode+" Theme")+")?.querySelector('.agy-settings-select-trigger')?.click();return '{}';");
                info=waitState("return JSON.stringify({ready:!!document.querySelector('.agy-settings-select-sheet [role=listbox]'),native:[...document.querySelectorAll('.agy-settings-page select')].some(e=>e.getBoundingClientRect().width>0),selected:document.querySelectorAll('.agy-settings-select-sheet [role=option][aria-selected=true]').length});","ready");
                require(info.optBoolean("ready")&&!info.optBoolean("native")&&info.optInt("selected")==1,"POLISH_"+mode+"_THEMED_DROPDOWN");
                SystemClock.sleep(400);captureRegion(".agy-settings-select-sheet","SELECT_"+mode.toUpperCase());
                sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);SystemClock.sleep(600);
                require(stockSheets().optInt("visible")==1,"POLISH_"+mode+"_SELECT_BACK_ONE_LEVEL");
            }
            // Exercise a real preset only when current colors match that preset.
            state("window.__agyPolishThemeRow()?.querySelector('[aria-label=Dark]')?.click();return '{}';");SystemClock.sleep(850);
            info=state("const section=window.__agyPolishSection('Dark Theme'),row=section?.querySelector('.agy-settings-row'),b=row?.querySelector('.agy-settings-select-trigger');const clone=b?.cloneNode(true);clone?.querySelectorAll('.google-symbols,svg,[data-icon]').forEach(e=>e.remove());window.__agyPolishPreset=clone?.textContent.trim();window.__agyPolishColors=[...(section?.querySelectorAll('input[type=text]')||[])].map(e=>e.value.toLowerCase());return JSON.stringify({safe:!!b&&row.querySelectorAll('button').length===1});");
            if(info.optBoolean("safe")){
                state("window.__agyPolishSection('Dark Theme').querySelector('.agy-settings-select-trigger').click();return '{}';");SystemClock.sleep(450);
                info=state("return JSON.stringify({available:!!window.__agyPolishOption(window.__agyPolishPreset)&&!!window.__agyPolishOption('Catppuccin')});");
                require(info.optBoolean("available"),"POLISH_ORIGINAL_PRESET_RESTORABLE");
                state("window.__agyPolishOption('Catppuccin').click();return '{}';");SystemClock.sleep(850);
                info=state("return JSON.stringify({saved:localStorage.getItem('theme-preset-dark')==='Catppuccin'});");
                require(info.optBoolean("saved"),"POLISH_REAL_PRESET_SELECTION_SAVED");
                captureRegion(".agy-settings-page","SETTINGS_CUSTOM");
                state("window.__agyPolishSection('Dark Theme').querySelector('.agy-settings-select-trigger').click();return '{}';");SystemClock.sleep(450);
                state("window.__agyPolishOption(window.__agyPolishPreset)?.click();return '{}';");SystemClock.sleep(850);
                info=state("return JSON.stringify({same:JSON.stringify(window.__agyPolishColors)===JSON.stringify([...window.__agyPolishSection('Dark Theme').querySelectorAll('input[type=text]')].map(e=>e.value.toLowerCase()))});");
                require(info.optBoolean("same"),"POLISH_ORIGINAL_COLORS_RESTORED");
            }else report("POLISH_REAL_PRESET_SELECTION","PENDING_CUSTOM_COLOR_OVERRIDES_PRESERVED");
            report("ANDROID_SETTINGS_POLISH","PASS_REAL_LIGHT_DARK_LISTS_GEOMETRY");
        }finally{
            try{
                state("if(window.__agyPolishPreset&&window.__agyPolishSection('Dark Theme')){const label=window.__agyPolishPreset;const option=window.__agyPolishOption(label);if(option)option.click();}return '{}';");
                closeOwnedSheets();openAppearance();
                state("const label=window.__agyPolishThemeLabel;if(label)window.__agyPolishThemeRow()?.querySelector('[aria-label='+JSON.stringify(label)+']')?.click();return '{}';");SystemClock.sleep(850);
                state("const saved=window.__agyPolishPresetStorage;if(saved)for(const mode of ['light','dark']){const value=saved[mode];if(value===null)localStorage.removeItem('theme-preset-'+mode);else localStorage.setItem('theme-preset-'+mode,value);}return '{}';");
                manager.getClass().getMethod("setTheme",String.class).invoke(manager,originalNative);
                closeOwnedSheets();
                JSONObject same=state("return JSON.stringify({same:window.__agyPolishDraft===JSON.stringify([...document.querySelectorAll('.agy-composer [contenteditable=true]')].map(e=>e.textContent))});");
                require(same.optBoolean("same"),"POLISH_DRAFT_PRESERVED");
            }catch(Exception restore){report("POLISH_RESTORE","FAIL_"+restore.getClass().getSimpleName());throw restore;}
        }
    }
    private void captureRegion(String selector,String tag)throws Exception{
        if(!capture)return;
        android.view.accessibility.AccessibilityNodeInfo focused=getUiAutomation().getRootInActiveWindow();
        if(!activity.hasWindowFocus()||focused==null||!target.getPackageName().contentEquals(focused.getPackageName())){report("CAPTURE_"+tag,"PENDING_TARGET_NOT_FOCUSED");return;}
        JSONObject rect=state("const e=document.querySelector("+JSONObject.quote(selector)+"),r=e?.getBoundingClientRect();return JSON.stringify({ok:!!r,x:r?.left,y:r?.top,w:r?.width,h:r?.height,vw:innerWidth});");
        if(!rect.optBoolean("ok"))return;
        int[] location=new int[2];runOnMainSync(()->web.getLocationOnScreen(location));
        float scale=web.getWidth()/(float)rect.getDouble("vw");
        android.graphics.Bitmap full=getUiAutomation().takeScreenshot();if(full==null)return;
        int x=Math.max(0,location[0]+Math.round((float)rect.getDouble("x")*scale));
        int y=Math.max(0,location[1]+Math.round((float)rect.getDouble("y")*scale));
        int w=Math.min(full.getWidth()-x,Math.round((float)rect.getDouble("w")*scale));
        int h=Math.min(full.getHeight()-y,Math.round((float)rect.getDouble("h")*scale));
        if(w<=0||h<=0){full.recycle();return;}
        android.graphics.Bitmap crop=android.graphics.Bitmap.createBitmap(full,x,y,w,h);
        android.graphics.Bitmap small=android.graphics.Bitmap.createScaledBitmap(crop,480,Math.max(1,Math.round(h*480f/w)),true);
        java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();small.compress(android.graphics.Bitmap.CompressFormat.JPEG,85,bytes);
        String data=android.util.Base64.encodeToString(bytes.toByteArray(),android.util.Base64.NO_WRAP);
        for(int i=0,part=0;i<data.length();i+=8000,part++)report("SCREENSHOT_"+tag+"_PART_"+part,data.substring(i,Math.min(data.length(),i+8000)));
        small.recycle();if(crop!=small&&crop!=full)crop.recycle();if(full!=small)full.recycle();
    }
    private void compactToolbar()throws Exception{
        JSONObject info=state("const p=document.querySelector('[data-testid=permissions-selector-trigger]'),m=document.querySelector('[data-testid=model-selector-trigger]'),a=document.querySelector('.agy-composer-add');window.__agyPermissionBefore=p?.getAttribute('aria-label');window.__agyDraftBeforeToolbar=JSON.stringify([...document.querySelectorAll('.agy-composer [contenteditable=true]')].map(x=>x.textContent));const pr=p?.getBoundingClientRect(),mr=m?.getBoundingClientRect(),ar=a?.getBoundingClientRect();return JSON.stringify({ready:!!p&&!!m,sized:!!pr&&pr.width>=47.5&&pr.height>=47.5&&mr.width>=47.5&&mr.height>=47.5,ordered:!!pr&&pr.left>=ar.right-1&&mr.left>=pr.right-1,pill:!!m&&parseFloat(getComputedStyle(m).borderRadius)>=16,short:!!m?.querySelector('.agy-model-short-label'),once:document.querySelectorAll('[data-testid=permissions-selector-trigger]').length===1});");
        require(info.optBoolean("ready"),"TOOLBAR_CONTROLS_PRESENT");
        require(info.optBoolean("sized")&&info.optBoolean("ordered"),"TOOLBAR_ICON_MODEL_GEOMETRY");
        require(info.optBoolean("pill")&&info.optBoolean("short")&&info.optBoolean("once"),"TOOLBAR_COMPACT_MODEL_AND_SINGLE_PERMISSION");
        SystemClock.sleep(500);captureRegion(".agy-composer-footer","TOOLBAR");
        state("document.querySelector('[data-testid=permissions-selector-trigger]').click();return '{}';");
        info=waitState("return JSON.stringify({ready:document.querySelectorAll('[data-testid^=permissions-preset-option-]').length>0});","ready");
        require(info.optBoolean("ready"),"TOOLBAR_REAL_CORE_PERMISSION_OPTIONS");
        SystemClock.sleep(400);captureRegion("[data-testid=permissions-project-options]","PERMISSIONS");
        sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);SystemClock.sleep(550);
        info=state("const p=document.querySelector('[data-testid=permissions-selector-trigger]'),d=document.querySelector('[data-testid=permissions-project-options]');return JSON.stringify({same:p?.getAttribute('aria-label')===window.__agyPermissionBefore,closed:!d||!!d.closest('[aria-hidden=true]')||getComputedStyle(d).visibility==='hidden',draft:window.__agyDraftBeforeToolbar===JSON.stringify([...document.querySelectorAll('.agy-composer [contenteditable=true]')].map(x=>x.textContent))});");
        require(info.optBoolean("same")&&info.optBoolean("draft"),"TOOLBAR_EXISTING_PERMISSIONS_AND_DRAFT_PRESERVED");
        require(stockSheets().optInt("visible")==0,"TOOLBAR_BACK_CLOSES_PERMISSIONS");
        state("document.querySelector('[data-testid=model-selector-trigger]').click();return '{}';");SystemClock.sleep(550);
        require(stockSheets().optInt("visible")==1,"TOOLBAR_MODEL_MENU_OPENS");
        boolean ime=keyboardVisible();report("TOOLBAR_MODEL_IME_BEFORE_BACK",ime);
        sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);SystemClock.sleep(650);
        if(ime&&stockSheets().optInt("visible")>0){
            require(!keyboardVisible(),"TOOLBAR_BACK_DISMISSES_IME_FIRST");
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);SystemClock.sleep(650);
        }
        JSONObject stock=stockSheets();report("TOOLBAR_MODEL_REMAINING_SHEETS",stock.optInt("visible"));
        require(stock.optInt("visible")==0,"TOOLBAR_MODEL_BACK");
        assertHost("TOOLBAR_HOST");
        report("ANDROID_COMPACT_TOOLBAR","PASS_REAL_CORE_MENUS_NO_SETTING_CHANGE");
    }
    private void backdropTap()throws Exception {
        JSONObject point=state("const e=[...document.querySelectorAll('[data-testid=bottom-sheet]')].find(e=>!e.closest('[aria-hidden=true]')&&e.getBoundingClientRect().height>0&&getComputedStyle(e).visibility!=='hidden');const r=e?.getBoundingClientRect();return JSON.stringify({x:innerWidth/2,y:r?Math.max(4,r.top/2):4,width:innerWidth});");
        final int[] location=new int[2];runOnMainSync(()->web.getLocationOnScreen(location));
        float scale=web.getWidth()/(float)point.getDouble("width");
        float x=location[0]+(float)point.getDouble("x")*scale,y=location[1]+(float)point.getDouble("y")*scale;
        long now=SystemClock.uptimeMillis();
        android.view.MotionEvent down=android.view.MotionEvent.obtain(now,now,android.view.MotionEvent.ACTION_DOWN,x,y,0);
        android.view.MotionEvent up=android.view.MotionEvent.obtain(now,now+60,android.view.MotionEvent.ACTION_UP,x,y,0);
        sendPointerSync(down);sendPointerSync(up);down.recycle();up.recycle();SystemClock.sleep(600);
    }
    private void assertHost(String tag)throws Exception {
        require(!activity.isFinishing()&&!activity.isDestroyed(),tag+"_ACTIVITY");
        JSONObject info=state("return JSON.stringify({alive:!!document.querySelector('#root')});");
        require(info.optBoolean("alive"),tag+"_WEBVIEW");
    }
    private void progressiveBack()throws Exception {
        capture=false;
        state("window.__agyDraftBeforeBack=JSON.stringify([...document.querySelectorAll('.agy-composer [contenteditable=true]')].map(x=>x.textContent));return '{}';");
        for(int i=0;i<6&&stockSheets().optInt("visible")>0;i++){
            state("window.__AGY_CLOSE_MOBILE_OVERLAY__?.();return '{}';");SystemClock.sleep(450);
        }
        for(int cycle=0;cycle<4;cycle++){
            JSONObject info=state("const b=document.querySelector('[data-testid=model-selector-trigger]');b?.click();return JSON.stringify({available:!!b});");
            require(info.optBoolean("available"),"BACK_MODEL_TRIGGER_"+cycle);SystemClock.sleep(450);
            info=state("const g=document.querySelector('[data-testid=model-selector-effort-group]'),r=g?.closest('button,[role=menuitem],[role=button]');r?.click();return JSON.stringify({available:!!r});");
            require(info.optBoolean("available"),"BACK_EFFORT_TRIGGER_"+cycle);SystemClock.sleep(450);
            info=state("const e=document.querySelector('[data-testid=model-selector-effort-option]');return JSON.stringify({ready:!!e&&!e.closest('[inert]')});");
            require(info.optBoolean("ready"),"BACK_EFFORT_OPEN_"+cycle);
            if(cycle%2==0)sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);else backdropTap();
            SystemClock.sleep(600);assertHost("BACK_FROM_EFFORT_"+cycle);
            info=state("const p=document.querySelector('[data-testid=model-selector-panel]');return JSON.stringify({ready:!!p&&!p.closest('[inert]')&&!p.closest('[aria-hidden=true]')});");
            require(info.optBoolean("ready")&&stockSheets().optInt("visible")==1,"BACK_EFFORT_TO_MODEL_"+cycle);
            state("const p=document.querySelector('[data-testid=model-selector-panel]'),r=[...(p?.querySelectorAll('[role=menuitem],button')||[])].find(x=>/View Usage/.test(x.textContent));r?.click();return '{}';");
            SystemClock.sleep(600);
            info=state("return JSON.stringify({nested:[...document.querySelectorAll('.agy-mobile-sheet-header')].some(e=>!e.closest('[inert]')&&getComputedStyle(e).visibility!=='hidden')});");
            require(info.optBoolean("nested"),"BACK_USAGE_OPEN_"+cycle);
            if(cycle%2==0)backdropTap();else sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            SystemClock.sleep(600);assertHost("BACK_FROM_USAGE_"+cycle);
            info=state("const p=document.querySelector('[data-testid=model-selector-panel]');return JSON.stringify({ready:!!p&&!p.closest('[inert]')&&!p.closest('[aria-hidden=true]')});");
            require(info.optBoolean("ready")&&stockSheets().optInt("visible")==1,"BACK_USAGE_TO_MODEL_"+cycle);
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);SystemClock.sleep(650);
            require(stockSheets().optInt("visible")==0,"BACK_MODEL_TO_CHAT_"+cycle);assertHost("BACK_CHAT_"+cycle);
        }
        JSONObject info=state("const visible=e=>e.getBoundingClientRect().width>0&&!e.closest('[inert]');const b=[...document.querySelectorAll('button')].find(e=>visible(e)&&/^settings$/i.test((e.getAttribute('aria-label')||e.title||e.textContent).trim()));b?.click();return JSON.stringify({available:!!b});");
        if(info.optBoolean("available")){
            SystemClock.sleep(600);
            for(String section:new String[]{"Developer","Models"}){
                info=state("const b=document.querySelector('[data-testid=\"settings-nav-item-"+section+"\"]');b?.click();return JSON.stringify({available:!!b});");
                if(!info.optBoolean("available")){report("BACK_SETTINGS_"+section,"PENDING_UNAVAILABLE");continue;}
                SystemClock.sleep(550);if("Developer".equals(section))sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);else backdropTap();
                SystemClock.sleep(650);assertHost("BACK_SETTINGS_"+section);
                info=state("const b=document.querySelector('[data-testid=\"settings-nav-item-"+section+"\"]');return JSON.stringify({root:!!b&&!b.closest('[inert]')&&!b.closest('[aria-hidden=true]')});");
                require(info.optBoolean("root")&&stockSheets().optInt("visible")>0,"BACK_SETTINGS_ONE_LEVEL_"+section);
            }
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);SystemClock.sleep(600);assertHost("BACK_SETTINGS_TO_CHAT");
        }else report("BACK_SETTINGS","PENDING_NO_TRIGGER");
        info=state("return JSON.stringify({same:window.__agyDraftBeforeBack===JSON.stringify([...document.querySelectorAll('.agy-composer [contenteditable=true]')].map(x=>x.textContent))});");
        require(info.optBoolean("same"),"BACK_DRAFT_PRESERVED");
        report("ANDROID_PROGRESSIVE_BACK","PASS_REAL_KEYS_AND_BACKDROP_NO_SEND");
    }
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
        state("document.querySelector('[aria-label=\"Close preview\"]').click();return '{}';");SystemClock.sleep(250);
        state("document.querySelectorAll('[data-testid=input-attachment-open]')[1].click();return '{}';");
        info=waitState("const i=document.querySelector('.agy-attachment-full-media');return JSON.stringify({ready:!!i&&i.tagName==='IMG'&&i.src.startsWith('blob:')});","ready");
        require(info.optBoolean("ready"),"ANDROID_IMAGE_FULLSCREEN");
        state("document.querySelector('[aria-label=\"Close preview\"]').click();return '{}';");SystemClock.sleep(250);
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
    /** Real AudioRecord cycles; discard PCM locally, never send to Core or log it. */
    private void microphonePcmHardwareRestart()throws Exception {
        JSONObject before=state("return JSON.stringify({idle:!document.querySelector('.agy-dictation-countdown,button[aria-label=\\\"Stop recording\\\"]'),build:window.__AGY_AUDIO_DIAG_BUILD__});");
        require(before.optBoolean("idle"),"PCM_HARDWARE_EXISTING_CAPTURE_IDLE");
        require("2026-10-27-pcm-delivery-v8".equals(before.optString("build")),"PCM_HARDWARE_CURRENT_WEB_ASSET");
        Class<?> cl=Class.forName("com.agy.bridge.NativePcmCapture",true,target.getClassLoader());
        Object capture=cl.getConstructor().newInstance();
        Method start=cl.getMethod("start",Activity.class),poll=cl.getMethod("poll"),stop=cl.getMethod("stop");
        int successful=0;
        try {
            for(int cycle=0;cycle<12;cycle++){
                final Activity host=activity;
                long focusDeadline=SystemClock.elapsedRealtime()+3000;
                while(!host.hasWindowFocus()&&SystemClock.elapsedRealtime()<focusDeadline)SystemClock.sleep(50);
                require("OK".equals(start.invoke(capture,host)),"PCM_HARDWARE_START_"+cycle);
                boolean gotFrame=false;
                long deadline=SystemClock.elapsedRealtime()+1500;
                do {
                    String frame=(String)poll.invoke(capture);
                    require(!frame.startsWith("ERR_"),"PCM_HARDWARE_NO_READ_ERROR_"+cycle);
                    if(!frame.isEmpty()){gotFrame=true;break;}
                    SystemClock.sleep(40);
                }while(SystemClock.elapsedRealtime()<deadline);
                require(gotFrame,"PCM_HARDWARE_RECEIVES_FRAME_"+cycle);
                stop.invoke(capture);
                stop.invoke(capture);
                require("ERR_PCM_NOT_RECORDING".equals(poll.invoke(capture)),"PCM_HARDWARE_STOPPED_"+cycle);
                successful++;
                // No artificial cooldown: the next cycle starts immediately.
            }
        } finally { stop.invoke(capture); }
        require(successful==12,"PCM_HARDWARE_12_IMMEDIATE_RESTARTS");
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
        JSONObject sw=state("const host=document.createElement('div');host.dataset.testid='bottom-sheet';const track=document.createElement('button');track.type='button';track.setAttribute('role','switch');track.className='relative inline-flex items-center rounded-full shrink-0 h-6 w-11 bg-secondary';const action=document.createElement('button');action.type='button';action.className='h-6 w-11';host.append(track,action);document.body.appendChild(host);const t=getComputedStyle(track),a=getComputedStyle(action);const out={trackWidth:track.getBoundingClientRect().width,trackHeight:track.getBoundingClientRect().height,actionMinHeight:a.minHeight,actionMinWidth:a.minWidth};host.remove();return JSON.stringify(out);");
        report("SETTINGS_SWITCH_SIZE",sw.optDouble("trackWidth")+"x"+sw.optDouble("trackHeight"));
        report("SETTINGS_ACTION_MINIMUM",sw.optString("actionMinWidth")+"x"+sw.optString("actionMinHeight"));
        require(Math.abs(sw.optDouble("trackWidth")-44)<1&&Math.abs(sw.optDouble("trackHeight")-24)<1,"SETTINGS_SWITCH_NATIVE_SIZE");
        require("48px".equals(sw.optString("actionMinHeight")),"SETTINGS_ACTION_TOUCH_SIZE");

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
    @Override public void onCreate(Bundle b){super.onCreate(b);pickerMode="picker".equals(b.getString("mode"));routeLegacyMode="route-legacy".equals(b.getString("mode"));routeIntentMode=routeLegacyMode||"route-intent".equals(b.getString("mode"));routeMode=routeIntentMode||"route-reuse".equals(b.getString("mode"));fixtureText=b.getString("fixtureText");fixtureImage=b.getString("fixtureImage");fixtureFolder=b.getString("fixtureFolder");pixelsMode="pixels".equals(b.getString("mode"));barsMode="bars".equals(b.getString("mode"));handoffMode="handoff".equals(b.getString("mode"));desktopMode="desktop".equals(b.getString("mode"));recoverMobileMode="recover-mobile".equals(b.getString("mode"));capture="yes".equals(b.getString("capture"));backMode="back".equals(b.getString("mode"));toolbarMode="toolbar".equals(b.getString("mode"));startupMode="startup".equals(b.getString("mode"));unifiedMode="unified".equals(b.getString("mode"));polishMode="polish".equals(b.getString("mode"));surfacesMode="surfaces".equals(b.getString("mode"));micMode="mic".equals(b.getString("mode"));micUiMode="mic-ui".equals(b.getString("mode"));micPcmUiMode="mic-pcm-ui".equals(b.getString("mode"));micPcmHardwareMode="mic-pcm-hardware".equals(b.getString("mode"));micCoreFixtureMode="mic-core-fixture".equals(b.getString("mode"));micCoreFixtureCleanupMode="mic-core-fixture-cleanup".equals(b.getString("mode"));micVisualMode="mic-visual".equals(b.getString("mode"));start();}
    @Override public void onStart(){
        boolean ok=false;
        try{
            target=getTargetContext();require("com.agy.dev".equals(target.getPackageName()),"TARGET_DEV_ONLY");
            if(recoverMobileMode){
                require(target.getSharedPreferences("agy_prefs",Context.MODE_PRIVATE).edit().putBoolean("desktop_mode",false).commit(),"RECOVER_MOBILE_PREFERENCE");
                report("RECOVER_MOBILE","PASS_ONLY_DESKTOP_PREF_CHANGED");
                ok=true;return;
            }
            Intent launch=target.getPackageManager().getLaunchIntentForPackage("com.agy.dev");launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity=startActivitySync(launch);
            if(pixelsMode){startupPixelFrames();ok=true;return;}
            // Dismiss first-launch settings/permission surfaces without granting broad storage access.
            for(int i=0;i<12&&!activity.hasWindowFocus();i++){
                android.view.accessibility.AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();
                String pkg=root!=null?String.valueOf(root.getPackageName()):"";
                if("com.android.settings".equals(pkg))sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
                SystemClock.sleep(250);
            }
            long until=SystemClock.elapsedRealtime()+45000;
            do{
                Object current=field(Class.forName("com.agy.ui.MainActivity",true,target.getClassLoader()),"activeActivity");
                if(current instanceof Activity)activity=(Activity)current;
                final Activity host=activity;runOnMainSync(()->web=find(host.getWindow().getDecorView()));
                if(web!=null&&Boolean.TRUE.equals(field(activity,"webUiReady")))break;
                SystemClock.sleep(250);
            }while(SystemClock.elapsedRealtime()<until);
            require(web!=null,"ANDROID_WEBVIEW_PRESENT");
            if(pickerMode){pickerRegression();ok=true;return;}
            if(routeMode){notificationRouteRegression();ok=true;return;}
            if(barsMode){systemBarThemes();ok=true;return;}
            if(handoffMode){startupHandoff();ok=true;return;}
            if(desktopMode){adaptiveDesktop();ok=true;return;}
            if(startupMode){nativeStartup();ok=true;return;}
            if(unifiedMode){unifiedInterface();ok=true;return;}
            if(surfacesMode){greySurfaces();ok=true;return;}
            if(polishMode){settingsPolish();ok=true;return;}
            if(toolbarMode){compactToolbar();ok=true;return;}
            if(backMode){progressiveBack();ok=true;return;}
            if(micMode){microphoneCapabilities();ok=true;return;}
            if(micUiMode){microphoneButtonRouting();ok=true;return;}
            if(micPcmUiMode){microphonePcmButtonRouting();ok=true;return;}
            if(micPcmHardwareMode){microphonePcmHardwareRestart();ok=true;return;}
            if(micCoreFixtureCleanupMode){clearCoreFixtureDraft();ok=true;return;}
            if(micCoreFixtureMode){new DictationCoreFixture(target,this::state,this::require,this::report,this::runOnMainSync,this::clearCoreFixtureDraft).run();ok=true;return;}
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
