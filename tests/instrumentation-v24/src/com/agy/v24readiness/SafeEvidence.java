package com.agy.v24readiness;
import android.app.Instrumentation;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;
import java.io.File;
import java.io.FileInputStream;
import java.io.ByteArrayOutputStream;

/** Count only recent native Core events. Never print stored IDs, texts or tokens. */
public final class SafeEvidence extends Instrumentation {
  private void report(String k,Object v){
    Bundle b=new Bundle();b.putString("stream",k+"="+String.valueOf(v));sendStatus(1,b);
  }
  @Override public void onCreate(Bundle b){super.onCreate(b);start();}
  @Override public void onStart(){
    super.onStart();
    try{
      Context c=getTargetContext();
      File log=new File(c.getFilesDir(),"logs/agy-diag.log");
      int watches=0,states=0,finished=0,ui=0,errors=0;
      long since=System.currentTimeMillis()-10*60*1000L;
      if(log.isFile()){
        try(FileInputStream in=new FileInputStream(log);ByteArrayOutputStream buffer=new ByteArrayOutputStream()){
          byte[] part=new byte[4096];int n;while((n=in.read(part))>0)buffer.write(part,0,n);
          String all=new String(buffer.toByteArray(),"UTF-8");
          for(String line:all.split("\\n")){
            int space=line.indexOf(' ');if(space<0)continue;
            long when;
            try{when=Long.parseLong(line.substring(0,space));}
            catch(NumberFormatException ignored){continue;}
            if(when<since)continue;
            if(line.contains("native_observer watch "))watches++;
            if(line.contains("native_observer state "))states++;
            if(line.contains("native_observer finished "))finished++;
            if(line.contains("agent_state "))ui++;
            if(line.contains("native_observer stream_error="))errors++;
          }
        }
      }
      report("WINDOW_MINUTES",10);
      report("NATIVE_WATCH_ADDED",watches);
      report("NATIVE_CORE_STATES",states);
      report("NATIVE_CORE_COMPLETIONS",finished);
      report("UI_AGENT_STATE_REPORTS",ui);
      report("NATIVE_STREAM_ERRORS",errors);
      try{
        NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        StatusBarNotification[] active=nm.getActiveNotifications();
        report("CURRENT_NOTIFICATION_COUNT",active==null?0:active.length);
      }catch(Exception ignored){report("CURRENT_NOTIFICATION_COUNT","unknown");}
    }catch(Exception e){report("CHECK_ERROR",e.getClass().getSimpleName());}
    finally{Bundle b=new Bundle();b.putString("summary","recent native event counts only");finish(0,b);}
  }
}
