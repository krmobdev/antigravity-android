package com.agy.notificationtests;

import android.app.*;
import android.content.*;
import android.os.*;
import android.service.notification.StatusBarNotification;
import android.view.*;
import android.view.accessibility.*;
import android.webkit.WebView;
import android.accessibilityservice.AccessibilityServiceInfo;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.io.*;

/** Real notification delivery/navigation; controlled native transitions, no agent requests. */
public final class NotificationInstrumentation extends Instrumentation {
    private Context context; private Activity activity; private WebView web;
    private Object notifier; private Class<?> type; private NotificationManager manager;
    private final StringBuilder report=new StringBuilder();
    private String a,b; private int checks;
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    private void log(String s){report.append(System.currentTimeMillis()).append(' ').append(s).append('\n');Bundle x=new Bundle();x.putString("stream",s+"\n");sendStatus(1,x);}
    private void check(boolean ok,String s){if(!ok)throw new AssertionError(s);checks++;log("PASS "+s);}
    @Override public void onStart(){
        Bundle result=new Bundle();boolean passed=false;
        try{
            context=getTargetContext();check("com.agy.dev".equals(context.getPackageName()),"Dev target only");
            manager=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
            check(manager.areNotificationsEnabled(),"notification permission enabled");
            AccessibilityServiceInfo config=getUiAutomation().getServiceInfo();config.flags|=AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;getUiAutomation().setServiceInfo(config);
            launch();
            JSONArray rows=new JSONArray(stringEval("JSON.stringify([...document.querySelectorAll('[data-cascade-id]')].filter(x=>x.getClientRects().length).map(x=>x.getAttribute('data-cascade-id')).filter((v,i,a)=>v&&a.indexOf(v)===i))"));
            check(rows.length()>=2,"two existing conversations available");a=rows.getString(0);b=rows.getString(1);
            type=context.getClassLoader().loadClass("com.agy.bridge.AgentNotifications");notifier=type.getConstructor(Context.class).newInstance(context);
            log("INITIAL_ROUTE "+stringEval("location.pathname"));
            open(a);Object original=activity,originalWeb=web;
            check(visible(a),"committed chat A visible to native host");
            complete(a);StatusBarNotification quiet=awaitNotification(a,3000);
            check(quiet!=null&&"agy_agent_finished_in_chat_v1".equals(quiet.getNotification().getChannelId()),"same chat completion uses non-banner channel");
            check(manager.getNotificationChannel(quiet.getNotification().getChannelId()).getImportance()==NotificationManager.IMPORTANCE_DEFAULT,"same chat channel DEFAULT");
            check(!bannerContains(a,1200),"same chat has no SystemUI heads-up");
            clear(a);SystemClock.sleep(4200);
            complete(b);StatusBarNotification other=awaitNotification(b,3000);
            check(other!=null&&"agy_agent_finished_v1".equals(other.getNotification().getChannelId()),"another chat completion uses alert channel");
            check(bannerContains(b,2000),"another chat produces actual SystemUI heads-up");
            SystemClock.sleep(5200);clear(b);
            returnToList();
            check(!stringEval("location.pathname").startsWith("/c/"),"conversation list before backgrounding");
            getUiAutomation().performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME);SystemClock.sleep(700);
            complete(a);StatusBarNotification background=awaitNotification(a,3000);
            check(background!=null&&"agy_agent_finished_v1".equals(background.getNotification().getChannelId()),"background completion uses alert channel");
            check(bannerContains(a,2000),"background produces actual SystemUI heads-up");
            tapNotification(background);SystemClock.sleep(1600);refresh();
            check(stringEval("location.pathname").endsWith("/c/"+a),"real SystemUI notification tap opens matching chat");
            // Returning from the notification-created Activity must expose the retained original page.
            returnToList();
            log("RETURN originalActivity="+(original==activity)+" originalWebView="+(originalWeb==web));
            check(original==activity&&originalWeb==web,"original Activity and WebView retained after notification Back");
            check(!stringEval("location.pathname").startsWith("/c/"),"Back returns to conversation list");
            SystemClock.sleep(5200);clear(a);clear(b);
            complete(a);complete(b);
            StatusBarNotification first=awaitNotification(a,2500);check(first!=null,"FIFO first completion delivered");
            check(findNotification(b)==null,"FIFO next completion waits instead of overlapping");
            StatusBarNotification second=awaitNotification(b,6500);check(second!=null,"FIFO second completion delivered");
            check(second.getPostTime()-first.getPostTime()>=4900,"FIFO actual Android posts separated by five seconds");
            SystemClock.sleep(5200);clear(a);clear(b);
            complete(a);awaitNotification(a,2500);complete(b);open(b);
            StatusBarNotification changed=awaitNotification(b,2500);
            check(changed!=null&&"agy_agent_finished_in_chat_v1".equals(changed.getNotification().getChannelId()),"waiting completion becomes quiet when its chat is opened");
            returnToList();
            passed=true;
        }catch(Throwable e){log("FAIL "+e.getClass().getSimpleName()+": "+e.getMessage());}
        finally{
            if(manager!=null){if(a!=null)clear(a);if(b!=null)clear(b);}
            log("RESULT passed="+passed+" checks="+checks+" controlledNativeTransitions=true realAgentRequests=0");
            try{File dir=new File(context.getFilesDir(),"logs");dir.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(dir,"notification-policy-results.txt"))){out.write(report.toString().getBytes("UTF-8"));}}catch(Exception ignored){}
            result.putString("summary","passed="+passed+" checks="+checks);finish(passed?-1:0,result);
        }
    }
    private void complete(String id)throws Exception{type.getMethod("observeNative",String.class,int.class,boolean.class,String.class).invoke(notifier,id,2,false,"Notification policy regression");type.getMethod("observeNative",String.class,int.class,boolean.class,String.class).invoke(notifier,id,1,true,"");}
    private void tapNotification(StatusBarNotification notification)throws Exception{
        String title=String.valueOf(notification.getNotification().extras.getCharSequence(Notification.EXTRA_TITLE));
        for(AccessibilityWindowInfo w:getUiAutomation().getWindows()){
            AccessibilityNodeInfo root=w.getRoot();if(root==null||!"com.android.systemui".contentEquals(root.getPackageName()==null?"":root.getPackageName()))continue;
            AccessibilityNodeInfo target=findTitle(root,title);
            if(target!=null){android.graphics.Rect rect=new android.graphics.Rect();target.getBoundsInScreen(rect);long t=SystemClock.uptimeMillis();
                MotionEvent down=MotionEvent.obtain(t,t,MotionEvent.ACTION_DOWN,rect.exactCenterX(),rect.exactCenterY(),0),up=MotionEvent.obtain(t,t+30,MotionEvent.ACTION_UP,rect.exactCenterX(),rect.exactCenterY(),0);
                down.setSource(InputDevice.SOURCE_TOUCHSCREEN);up.setSource(InputDevice.SOURCE_TOUCHSCREEN);
                try{check(getUiAutomation().injectInputEvent(down,true)&&getUiAutomation().injectInputEvent(up,true),"tap actual SystemUI banner");}finally{down.recycle();up.recycle();}return;}
        }
        throw new Exception("Visible completion banner not found for tap");
    }
    private AccessibilityNodeInfo findTitle(AccessibilityNodeInfo node,String title){if(node.isVisibleToUser()&&node.getText()!=null&&node.getText().toString().contains(title))return node;for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo c=node.getChild(i);if(c!=null){AccessibilityNodeInfo found=findTitle(c,title);if(found!=null)return found;}}return null;}
    private void clear(String id){manager.cancel(id,21001);manager.cancel(id,21002);}
    private StatusBarNotification findNotification(String id){for(StatusBarNotification n:manager.getActiveNotifications())if(id.equals(n.getTag())&&n.getId()==21002)return n;return null;}
    private StatusBarNotification awaitNotification(String id,long duration){long end=SystemClock.uptimeMillis()+duration;while(SystemClock.uptimeMillis()<end){StatusBarNotification n=findNotification(id);if(n!=null)return n;SystemClock.sleep(70);}return null;}
    private boolean visible(String id)throws Exception{AtomicReference<Boolean> v=new AtomicReference<>(false);runOnMainSync(()->{try{v.set((Boolean)activity.getClass().getMethod("isConversationVisible",String.class).invoke(activity,id));}catch(Exception ignored){}});return v.get();}
    private boolean bannerContains(String id,long duration){StatusBarNotification n=findNotification(id);String title=n==null?"":String.valueOf(n.getNotification().extras.getCharSequence(Notification.EXTRA_TITLE));long end=SystemClock.uptimeMillis()+duration;while(SystemClock.uptimeMillis()<end){for(AccessibilityWindowInfo w:getUiAutomation().getWindows()){AccessibilityNodeInfo root=w.getRoot();if(root!=null&&"com.android.systemui".contentEquals(root.getPackageName()==null?"":root.getPackageName())&&hasBanner(root,title,false)){return true;}}SystemClock.sleep(100);}log("BANNER_ENV filter="+manager.getCurrentInterruptionFilter()+" importance="+manager.getNotificationChannel("agy_agent_finished_v1").getImportance()+" interactive="+((PowerManager)context.getSystemService(Context.POWER_SERVICE)).isInteractive()+" locked="+((KeyguardManager)context.getSystemService(Context.KEYGUARD_SERVICE)).isKeyguardLocked());for(AccessibilityWindowInfo w:getUiAutomation().getWindows()){AccessibilityNodeInfo root=w.getRoot();android.graphics.Rect rect=new android.graphics.Rect();w.getBoundsInScreen(rect);log("BANNER_WINDOW package="+(root==null?"none":root.getPackageName())+" type="+w.getType()+" bounds="+rect);}return false;}
    private boolean hasBanner(AccessibilityNodeInfo node,String title,boolean head){String id=node.getViewIdResourceName();head=head||(id!=null&&(id.contains("heads_up")||id.contains("headsup")));if(!title.isEmpty()&&node.isVisibleToUser()&&node.getText()!=null&&node.getText().toString().contains(title)){android.graphics.Rect rect=new android.graphics.Rect();node.getBoundsInScreen(rect);log("SYSTEMUI_COMPLETION id="+id+" bounds="+rect+" headsUpAncestor="+head);if(head||(rect.bottom>0&&rect.top<context.getResources().getDisplayMetrics().heightPixels/3))return true;}for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo c=node.getChild(i);if(c!=null&&hasBanner(c,title,head))return true;}return false;}
    private void launch()throws Exception{Intent intent=context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());String component=intent.getComponent().flattenToString();try(ParcelFileDescriptor fd=getUiAutomation().executeShellCommand("am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n "+component);InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(fd)){byte[] x=new byte[1024];while(in.read(x)!=-1){}}long end=SystemClock.uptimeMillis()+45000;while(SystemClock.uptimeMillis()<end){if(refresh()&&uiReady()&&"true".equals(eval("document.readyState==='complete'&&document.querySelectorAll('[data-cascade-id]').length>=2"))){SystemClock.sleep(1000);return;}SystemClock.sleep(150);}throw new Exception("Dev sidebar not ready");}
    private boolean uiReady(){AtomicReference<Boolean> ready=new AtomicReference<>(false);runOnMainSync(()->{try{Class<?> c=context.getClassLoader().loadClass("com.agy.ui.MainActivity");java.lang.reflect.Field f=c.getDeclaredField("webUiReady"),s=c.getDeclaredField("splash");f.setAccessible(true);s.setAccessible(true);Object splash=s.get(activity);View overlay=(View)splash.getClass().getMethod("view").invoke(splash);ready.set(f.getBoolean(activity)&&overlay.getVisibility()!=View.VISIBLE);}catch(Exception ignored){}});return ready.get();}
    private void open(String id)throws Exception{
        JSONObject point=new JSONObject(stringEval("(()=>{let row=[...document.querySelectorAll('[data-cascade-id]')].find(x=>x.getAttribute('data-cascade-id')==="+JSONObject.quote(id)+"&&x.getClientRects().length),link=row?.querySelector('a[href]');if(!link)throw Error('No chat link');let r=link.getBoundingClientRect();return JSON.stringify({x:(r.left+r.right)/2,y:(r.top+r.bottom)/2,width:innerWidth,height:innerHeight,rectWidth:r.width})})()"));
        int[] origin=new int[2];AtomicReference<Integer> width=new AtomicReference<>(0);runOnMainSync(()->{web.getLocationOnScreen(origin);width.set(web.getWidth());});
        check(point.getDouble("rectWidth")>0&&point.getDouble("y")>0&&point.getDouble("y")<point.getDouble("height"),"sidebar link lies in visible viewport");
        float scale=(float)(width.get()/point.getDouble("width"));
        log("TOUCH_POINT css="+point+" nativeOrigin="+Arrays.toString(origin)+" scale="+scale);
        tapAt(origin[0]+(float)point.getDouble("x")*scale,origin[1]+(float)point.getDouble("y")*scale,"tap real sidebar link");
        long end=SystemClock.uptimeMillis()+5000;
        while(SystemClock.uptimeMillis()<end){refresh();if(visible(id))return;SystemClock.sleep(100);}
        log("CONTEXT_DEBUG route="+stringEval("location.pathname")+" focused="+activity.hasWindowFocus()+" fields="+hostFields());throw new Exception("committed chat context not updated");
    }
    private void tapAt(float x,float y,String label){long t=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(t,t,MotionEvent.ACTION_DOWN,x,y,0),up=MotionEvent.obtain(t,t+30,MotionEvent.ACTION_UP,x,y,0);down.setSource(InputDevice.SOURCE_TOUCHSCREEN);up.setSource(InputDevice.SOURCE_TOUCHSCREEN);try{check(getUiAutomation().injectInputEvent(down,true)&&getUiAutomation().injectInputEvent(up,true),label);}finally{down.recycle();up.recycle();}}
    private String hostFields(){try{Class<?> c=context.getClassLoader().loadClass("com.agy.ui.MainActivity");java.lang.reflect.Field f=c.getDeclaredField("visibleConversation"),r=c.getDeclaredField("notificationUiResumed");f.setAccessible(true);r.setAccessible(true);return "visible="+f.get(activity)+" resumed="+r.get(activity);}catch(Exception ex){return ex.toString();}}
    private void returnToList()throws Exception{
        for(int i=0;i<4;i++){
            if(!refresh())throw new Exception("Activity lost while returning to list");
            String route=stringEval("location.pathname");AtomicReference<String> state=new AtomicReference<>("");
            runOnMainSync(()->{WindowInsets insets=activity.getWindow().getDecorView().getRootWindowInsets();boolean ime=Build.VERSION.SDK_INT>=30&&insets!=null&&insets.isVisible(WindowInsets.Type.ime());state.set("ime="+ime+" canGoBack="+web.canGoBack()+" history="+web.copyBackForwardList().getCurrentIndex());});
            log("BACK_STATE route="+route+" "+state.get());
            if(!route.startsWith("/c/"))return;
            back();SystemClock.sleep(900);
        }
        throw new Exception("List not reached using system Back");
    }
    private void back(){getUiAutomation().performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);}
    private boolean refresh()throws Exception{Activity current=(Activity)context.getClassLoader().loadClass("com.agy.ui.MainActivity").getField("activeActivity").get(null);if(current==null||current.isDestroyed()||current.isFinishing())return false;AtomicReference<WebView> found=new AtomicReference<>();runOnMainSync(()->found.set(find(current.getWindow().getDecorView())));if(found.get()==null)return false;activity=current;web=found.get();return true;}
    private WebView find(View v){if(v instanceof WebView)return(WebView)v;if(v instanceof ViewGroup){for(int i=0;i<((ViewGroup)v).getChildCount();i++){WebView w=find(((ViewGroup)v).getChildAt(i));if(w!=null)return w;}}return null;}
    private String eval(String js)throws Exception{CountDownLatch done=new CountDownLatch(1);AtomicReference<String> value=new AtomicReference<>();WebView target=web;runOnMainSync(()->target.evaluateJavascript(js,v->{value.set(v);done.countDown();}));if(!done.await(7,TimeUnit.SECONDS))throw new Exception("JS timeout");return value.get();}
    private String stringEval(String js)throws Exception{return new JSONArray("["+eval(js)+"]").getString(0);}
}
