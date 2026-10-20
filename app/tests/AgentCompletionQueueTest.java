package com.agy.bridge;
import java.util.*;

public final class AgentCompletionQueueTest {
    private static final class Harness implements AgentCompletionQueue.Clock, AgentCompletionQueue.Scheduler, AgentCompletionQueue.Output {
        long now=100; Runnable scheduled; long due; String visible;
        List<AgentCompletionQueue.Entry> saved=new ArrayList<>(); final List<String> delivered=new ArrayList<>();
        AgentCompletionQueue queue=new AgentCompletionQueue(this,this,this,Collections.emptyList());
        public long now(){return now;}
        public void schedule(Runnable r,long delay){scheduled=r;due=now+delay;}
        public void cancel(Runnable r){if(scheduled==r)scheduled=null;}
        public boolean isVisible(String id){return id.equals(visible);}
        public void post(AgentCompletionQueue.Entry e,boolean popup){delivered.add(e.id+":"+popup+":"+now);}
        public void save(List<AgentCompletionQueue.Entry> entries){saved=entries;}
        void add(String id){queue.enqueue(new AgentCompletionQueue.Entry(id,1234,true));}
        void until(long target){while(scheduled!=null&&due<=target){now=due;Runnable r=scheduled;scheduled=null;r.run();}now=target;}
        void check(boolean v,String message){if(!v)throw new AssertionError(message+" "+delivered);}
    }
    public static void main(String[] args){
        Harness h=new Harness();h.visible="A";h.add("A");h.add("B");h.add("C");h.until(100);
        h.check(h.delivered.equals(Arrays.asList("A:false:100","B:true:100")),"current chat is silent; another chat alerts");
        h.until(5099);h.check(h.delivered.size()==2,"no overlapping banner");
        h.until(5100);h.check(h.delivered.get(2).equals("C:true:5100"),"FIFO alert interval");
        h=new Harness();h.add("A");h.add("B");h.until(100);h.visible="B";h.queue.contextChanged();h.until(200);
        h.check(h.delivered.get(1).equals("B:false:100"),"recheck changed chat before delivery");
        h=new Harness();h.add("A");h.add("B");h.until(100);h.queue.cancel("B");h.until(6000);
        h.check(h.delivered.size()==1&&h.saved.isEmpty(),"new run cancels an old pending completion");
        h=new Harness();h.add("A");h.add("B");h.check(h.saved.size()==2,"pending alerts persisted before dispatch");
        h.queue=new AgentCompletionQueue(h,h,h,new ArrayList<>(h.saved));h.scheduled=null;h.queue.contextChanged();h.until(100);h.until(5100);
        h.check(h.delivered.size()==2&&h.saved.isEmpty(),"process-restored queue drains without losses");
        h=new Harness();h.add("A");h.queue.restoreAlertDelay(3000);h.until(3099);
        h.check(h.delivered.isEmpty(),"restored cooldown prevents overlapping banners");
        h.until(3100);h.check(h.delivered.size()==1,"restored cooldown eventually drains");
        h=new Harness();h.add("A");h.add("A");h.until(100);h.check(h.delivered.size()==1,"duplicate pending conversation coalesces");
        h=new Harness();for(int i=0;i<130;i++)h.add("C"+i);h.check(h.saved.size()==128&&h.delivered.size()==2,"bounded queue preserves overflow in shade");
        System.out.println("PASS completion context, FIFO spacing, navigation race, cancellation, persistence and overflow");
    }
}
