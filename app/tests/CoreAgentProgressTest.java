import com.agy.core.CoreAgentProgress;
import com.agy.bridge.AgentTaskTracker;

public final class CoreAgentProgressTest {
    private static int checks;
    private static void check(boolean value, String reason) {
        checks++;
        if (!value) throw new AssertionError(reason);
    }
    private static CoreAgentProgress.Update u(int status, Boolean idle, int count, int type, boolean error, boolean killed) {
        return new CoreAgentProgress.Update("trajectory", status, idle, count, type, error, killed);
    }
    public static void main(String[] args) {
        CoreAgentProgress a = new CoreAgentProgress(), b = new CoreAgentProgress();
        check(a.accept(u(2,false,3,8,false,false)).stage.equals("Читает исходники"),"confirmed read");
        check(b.accept(u(2,false,10,5,false,false)).stage.equals("Изменяет файлы"),"parallel task independent");
        check(a.accept(u(0,null,-1,0,false,false)).stage.equals("Читает исходники"),"status-only delta preserves stage");
        check(a.accept(u(2,false,3,8,false,false)).stage.equals("Читает исходники"),"reconnect duplicate");
        check(a.accept(u(2,false,2,5,false,false)) == null,"old page cannot regress stage");
        check(a.accept(u(2,false,3,5,false,false)).stage.equals("Читает исходники"),"equal-index conflict ignored");
        check(a.accept(u(2,false,4,21,false,false)).stage.equals("Выполняет команду"),"command never guessed as test");
        check(a.accept(u(2,false,5,107,false,false)).stage.equals("Запускает тесты"),"explicit test enum");
        check(a.accept(u(2,false,6,9999,false,false)).stage.equals(CoreAgentProgress.GENERIC),"unknown new step neutral");
        check(a.accept(u(1,true,6,0,false,false)).outcome == 0,"completion");
        check(a.accept(u(2,false,6,8,false,false)) == null,"terminal tombstone blocks replay");
        check(a.accept(u(2,false,-1,8,false,false)) == null,"unversioned replay cannot reopen");
        check(a.accept(u(2,false,7,8,false,false)).stage.equals("Читает исходники"),"new user turn appends steps");
        check(a.accept(u(1,true,7,0,true,false)).outcome == 1,"Core lastStepError");
        check(b.accept(u(3,false,10,0,false,false)).stage.equals("Отменяет задачу"),"explicit cancelling");
        check(b.accept(u(1,true,10,0,false,false)).outcome == 2,"cancel terminal outcome");
        check(b.accept(u(2,false,11,5,false,false)).outcome == 0,"new run clears cancellation");
        check(b.accept(u(1,true,11,0,false,true)).outcome == 2,"Core killed");
        CoreAgentProgress c = new CoreAgentProgress();
        c.accept(new CoreAgentProgress.Update("old",2,false,8,8,false,false));
        check(c.accept(new CoreAgentProgress.Update("new",2,false,1,5,false,false)).stage.equals("Изменяет файлы"),"new trajectory");
        check(c.accept(new CoreAgentProgress.Update("old",2,false,9,8,false,false)) == null,"retired trajectory replay");
        for (Object value : new Object[]{2, "2", "CASCADE_RUN_STATUS_RUNNING"})
            check(CoreAgentProgress.status(value)==2,"protobuf enum formats");
        check(CoreAgentProgress.status("private_RUNNING")==0,"no suffix guessing");
        check(CoreAgentProgress.stepType("CORTEX_STEP_TYPE_VIEW_FILE")==8,"exact enum");
        check(CoreAgentProgress.publicStage("token=private file contents").equals(CoreAgentProgress.GENERIC),"no private text");
        AgentTaskTracker t = new AgentTaskTracker(0);
        t.observe("a",2,false,"Читает исходники",1000,true);
        check(t.observe("a",2,false,null,1001,false).stage.equals("Читает исходники"),"UI cannot erase native stage");
        check(t.observe("a",2,false,"Изменяет файлы",1002,true).event==AgentTaskTracker.Event.PROGRESS,"native progress");
        check(t.observe("a",2,false,"Изменяет файлы",1003,true).event==AgentTaskTracker.Event.NONE,"native duplicate");
        check(t.observe("b",2,false,"private command",1004,true).stage.equals(CoreAgentProgress.GENERIC),"tracker allowlist");
        CoreAgentProgress delta = new CoreAgentProgress();
        delta.accept(u(2,false,3,8,false,false));
        delta.accept(u(1,true,3,0,false,false));
        check(delta.accept(u(0,null,4,5,false,false)).status == 0,"new step cannot inherit terminal status");
        check(!delta.accept(u(2,false,4,5,false,false)).fullyIdle,"new run not idle");
        System.out.println("PASS CoreAgentProgress: " + checks + " assertions");
    }
}
