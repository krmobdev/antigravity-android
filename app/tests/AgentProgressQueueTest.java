package com.agy.bridge;
import java.util.*;

public final class AgentProgressQueueTest {
    private static final class Harness implements AgentProgressQueue.Clock, AgentProgressQueue.Scheduler, AgentProgressQueue.Output {
        long now=1000;
        Map<Runnable,Long> pending=new LinkedHashMap<>();
        List<String> posts=new ArrayList<>();
        AgentProgressQueue queue=new AgentProgressQueue(this,this,this,1000);
        public long now(){return now;}
        public void schedule(Runnable task,long delay){pending.put(task,now+delay);}
        public void cancel(Runnable task){pending.remove(task);}
        public void post(AgentTaskTracker.Change c){posts.add(c.conversationId+":"+c.stage+":"+now);}
        void until(long target){
            while(true){
                Runnable chosen=null; long at=Long.MAX_VALUE;
                for(Map.Entry<Runnable,Long> e:pending.entrySet())if(e.getValue()<at){chosen=e.getKey();at=e.getValue();}
                if(chosen==null||at>target)break;
                pending.remove(chosen);now=at;chosen.run();
            }
            now=target;
        }
    }
    private static AgentTaskTracker.Change c(String id,String stage){
        return new AgentTaskTracker.Change(AgentTaskTracker.Event.PROGRESS,id,stage,1000);
    }
    private static void check(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}
    public static void main(String[] args){
        Harness h=new Harness();
        h.queue.started(c("a","read"));h.queue.started(c("b","edit"));
        h.queue.progress(c("a","command"));h.queue.progress(c("a","test"));
        h.queue.progress(c("b","read"));
        h.until(1999);check(h.posts.size()==2,"rate limit");
        h.until(2000);check(h.posts.contains("a:test:2000")&&h.posts.contains("b:read:2000"),"latest per conversation retained");
        h.queue.progress(c("a","late"));h.queue.finished("a");
        h.until(4000);check(h.posts.size()==4,"completion cancels queued progress");
        h.queue.progress(c("a","stale"));h.until(5000);check(h.posts.size()==4,"finished task cannot resurrect");
        h.queue.started(c("a","new"));h.queue.progress(c("a","newer"));
        h.queue.finished("a");h.queue.started(c("a","next"));
        h.until(9000);check(h.posts.size()==6,"old generation cannot update a new run");
        check(h.pending.isEmpty(),"no timer without a real pending event");
        System.out.println("PASS AgentProgressQueue: latest-stage coalescing, parallel tasks, terminal/new-run races");
    }
}
