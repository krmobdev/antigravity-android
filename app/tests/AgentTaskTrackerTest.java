import com.agy.bridge.AgentTaskTracker;
public final class AgentTaskTrackerTest {
 public static void main(String[] args) {
  AgentTaskTracker t = new AgentTaskTracker(5000);
  if(t.observe("x",1,true,"",1000).event != AgentTaskTracker.Event.NONE) throw new AssertionError("initial idle");
  if(t.observe("x",2,false,"Build",2000).event != AgentTaskTracker.Event.STARTED) throw new AssertionError("start");
  if(t.observe("x",2,false,"Build",2001).event != AgentTaskTracker.Event.NONE) throw new AssertionError("duplicate start");
  if(t.observe("x",4,false,"Tests",8000).event != AgentTaskTracker.Event.PROGRESS) throw new AssertionError("progress");
  if(t.observe("x",1,false,"",8500).event != AgentTaskTracker.Event.NONE) throw new AssertionError("not fully idle");
  if(t.observe("x",1,true,"",9000).event != AgentTaskTracker.Event.FINISHED) throw new AssertionError("completion");
  if(t.observe("x",1,true,"",10000).event != AgentTaskTracker.Event.NONE) throw new AssertionError("repeated idle");
  System.out.println("PASS: 7 tracker transitions");
 }
}
