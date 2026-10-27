import com.agy.bridge.AgentTaskTracker;

public final class AgentTaskTrackerTest {
    private static int checks = 0;
    private static void expect(AgentTaskTracker.Event actual,
                               AgentTaskTracker.Event expected, String reason) {
        if (actual != expected) throw new AssertionError(reason
                + ": expected " + expected + ", got " + actual);
        checks++;
    }
    private static void event(AgentTaskTracker t, String id, int status,
                              boolean fullyIdle, long clock, boolean nativeUpdate,
                              AgentTaskTracker.Event expected, String reason) {
        expect(t.observe(id,status,fullyIdle,"Reading source files",clock,nativeUpdate).event,expected,reason);
    }
    public static void main(String[] args) {
        AgentTaskTracker t = new AgentTaskTracker(5000);
        event(t,"x",1,true,1000,false,AgentTaskTracker.Event.NONE,"initial idle");
        event(t,"x",2,false,2000,false,AgentTaskTracker.Event.STARTED,"initial start");
        event(t,"x",2,false,2001,false,AgentTaskTracker.Event.NONE,"duplicate running");
        expect(t.observe("x",4,false,"Editing files",8000).event,
               AgentTaskTracker.Event.PROGRESS,"progress");
        event(t,"x",1,false,8500,false,AgentTaskTracker.Event.NONE,"partial idle");
        event(t,"x",1,true,9000,true,AgentTaskTracker.Event.FINISHED,"native finish");
        event(t,"x",2,false,9004,false,AgentTaskTracker.Event.NONE,
              "stale UI RUNNING cannot resurrect finished native task");
        event(t,"x",1,true,9006,false,AgentTaskTracker.Event.NONE,
              "late UI idle does not duplicate notification");
        event(t,"other",2,false,9010,false,AgentTaskTracker.Event.STARTED,
              "different conversation remains independent");
        event(t,"x",2,false,9012,true,AgentTaskTracker.Event.STARTED,
              "authoritative native new task starts immediately");
        event(t,"x",1,true,9020,true,AgentTaskTracker.Event.FINISHED,
              "second native task can finish");
        event(t,"x",2,false,13025,false,AgentTaskTracker.Event.STARTED,
              "UI new task can start after short grace");
        event(t,"x",1,true,13030,false,AgentTaskTracker.Event.FINISHED,
              "UI fallback completion remains working");
        System.out.println("PASS AgentTaskTracker: " + checks + " assertions");
    }
}
