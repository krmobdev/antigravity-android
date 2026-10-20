package com.agy.v24readiness;
import android.app.Instrumentation;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;
import org.json.JSONArray;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.*;

/** Time-window evidence. Never reports conversation IDs, tokens, titles, or message text. */
public final class HistoricalCompletionEvidence extends Instrumentation {
  private void emit(String k,Object v){
    Bundle b=new Bundle();b.putString("stream",k+"="+v);sendStatus(1,b);
  }
  private static String find(String s,String k){
    Matcher m=Pattern.compile("(?:^|\\s)"+Pattern.quote(k)+"=([^\\s]+)").matcher(s);
    return m.find()?m.group(1):"";
  }
  private static String clock(long t){
    return new SimpleDateFormat("HH:mm:ss.SSS",Locale.ROOT).format(new Date(t));
  }
  private String windowStart="21:15",windowEnd="21:20";
  @Override public void onCreate(Bundle b){
    super.onCreate(b);
    if(b!=null) {
      String a=b.getString("start"), z=b.getString("end");
      if(a!=null&&a.matches("(?:[01][0-9]|2[0-3]):[0-5][0-9]"))windowStart=a;
      if(z!=null&&z.matches("(?:[01][0-9]|2[0-3]):[0-5][0-9]"))windowEnd=z;
    }
    start();
  }
  @Override public void onStart(){
    super.onStart();
    try{
      Context ctx=getTargetContext();
      Calendar c=Calendar.getInstance();
      int sh=Integer.parseInt(windowStart.substring(0,2)),
          sm=Integer.parseInt(windowStart.substring(3,5)),
          eh=Integer.parseInt(windowEnd.substring(0,2)),
          em=Integer.parseInt(windowEnd.substring(3,5));
      c.set(2026,Calendar.OCTOBER,20,sh,sm,0);c.set(Calendar.MILLISECOND,0);
      long from=c.getTimeInMillis();
      c.set(2026,Calendar.OCTOBER,20,eh,em,59);
      long until=c.getTimeInMillis()+999;
      if(until<from)throw new IllegalArgumentException("End before start");
      emit("WINDOW","2026-10-20 "+windowStart+" to "+windowEnd+" device local");
      Map<String,String> aliases=new LinkedHashMap<>();
      ArrayList<String> result=new ArrayList<>();
      int states=0,completions=0,finishTracker=0,posts=0,nav=0,errors=0;
      for(String filename:new String[]{"agy-diag.prev.log","agy-diag.log"}){
        File file=new File(new File(ctx.getFilesDir(),"logs"),filename);
        if(!file.isFile()||file.length()>2*1024*1024)continue;
        try(BufferedReader r=new BufferedReader(new InputStreamReader(new FileInputStream(file),"UTF-8"))){
          String line;
          while((line=r.readLine())!=null){
            int sep=line.indexOf(' ');if(sep<1)continue;
            long t;try{t=Long.parseLong(line.substring(0,sep));}catch(Exception e){continue;}
            if(t<from||t>until)continue;
            String s=line.substring(sep+1),kind="";
            for(String k:new String[]{"native_observer","agent_state","notification","notification_posted","navigation","activity"}){
              if(s.startsWith(k+" ")){kind=k;break;}
            }
            if(kind.isEmpty())continue;
            String data=s.substring(kind.length()).trim();
            String id=find(data,"id"),tag="";
            if(!id.isEmpty()){
              if(!aliases.containsKey(id))aliases.put(id,"dialog_"+(aliases.size()+1));
              tag=aliases.get(id);
            }
            String e=clock(t)+" "+kind+(tag.isEmpty()?"":" "+tag);
            if(kind.equals("native_observer")){
              if(data.startsWith("state ")){states++;e+=" STATE status="+find(data,"status")+" idle="+find(data,"idle");}
              else if(data.startsWith("finished ")){completions++;e+=" FINISHED";}
              else if(data.startsWith("watch "))e+=" WATCH";
              else if(data.startsWith("subscribe "))e+=" SUBSCRIBED";
              else if(data.startsWith("unwatch "))e+=" UNWATCH";
              else if(data.startsWith("stream_error=")){errors++;e+=" STREAM_ERROR";}
              else continue;
            }else if(kind.equals("notification")){
              String event=find(data,"type");
              if(event.equals("FINISHED"))finishTracker++;
              e+=" type="+event+" source="+find(data,"source");
            }else if(kind.equals("notification_posted")){
              posts++;
              e+=" POSTED popup="+find(data,"popup")+" source_time="+find(data,"detected_at_ms")
                  +" post_time="+find(data,"posted_at_ms");
            }else if(kind.equals("agent_state")){
              e+=" status="+find(data,"status")+" idle="+find(data,"idle");
            }else if(kind.equals("navigation")){
              if(data.startsWith("accepted")){nav++;e+=" ACCEPTED";}
              else if(data.startsWith("requested"))e+=" REQUESTED";
              else if(data.startsWith("dispatch"))e+=" DISPATCH";
              else continue;
            }else if(kind.equals("activity")){
              if(data.startsWith("onResume"))e+=" RESUMED";
              else if(data.startsWith("onPause"))e+=" PAUSED";
              else if(data.startsWith("onStop"))e+=" STOPPED";
              else if(data.startsWith("onDestroy"))e+=" DESTROYED";
              else continue;
            }
            result.add(e);
          }
        }
      }
      emit("ANON_CONVERSATIONS",aliases.size());
      emit("NATIVE_STATE_EVENTS",states);
      emit("NATIVE_FINISHED_EVENTS",completions);
      emit("TRACKER_FINISHED_EVENTS",finishTracker);
      emit("NOTIFICATION_POSTED_EVENTS",posts);
      emit("NAVIGATION_ACCEPTED_EVENTS",nav);
      emit("NATIVE_STREAM_ERRORS",errors);
      for(int i=0;i<Math.min(55,result.size());i++)emit("EVENT_"+(i+1),result.get(i));
      if(result.size()>55)emit("TRUNCATED",true);
      NotificationManager nm=(NotificationManager)ctx.getSystemService(Context.NOTIFICATION_SERVICE);
      if(nm!=null){
        int active=0;
        String workingTag=null,finishedTag=null;
        for(StatusBarNotification n:nm.getActiveNotifications()){
          String channel=n.getNotification().getChannelId();
          if(channel!=null&&channel.startsWith("agy_agent_")){
            active++;
            if(channel.equals("agy_agent_working_v1"))workingTag=n.getTag();
            if(channel.startsWith("agy_agent_finished_"))finishedTag=n.getTag();
            emit("ACTIVE_CHANNEL_"+active,channel);
            emit("ACTIVE_NOTIFICATION_POST_TIME_"+active,clock(n.getPostTime()));
          }
        }
        emit("ACTIVE_AGENT_NOTIFICATIONS",active);
        emit("WORKING_AND_FINISHED_SHARE_CONVERSATION",workingTag!=null&&workingTag.equals(finishedTag));
        for(String id:aliases.keySet()) {
          emit("ACTIVE_WORKING_MATCHES_EVENT",workingTag!=null&&workingTag.endsWith(id));
          emit("ACTIVE_FINISHED_MATCHES_EVENT",finishedTag!=null&&finishedTag.endsWith(id));
        }
      }
      String saved=ctx.getSharedPreferences("agy_completion_queue",Context.MODE_PRIVATE)
        .getString("pending","[]");
      emit("FIFO_PENDING",new JSONArray(saved).length());
      emit("RESULT","READ_ONLY_COMPLETE");
    }catch(Exception e){emit("EVIDENCE_ERROR",e.getClass().getSimpleName());}
    finally{finish(0,new Bundle());}
  }
}
