import com.agy.core.CoreStartupProgress;

public final class CoreStartupProgressTest {
    private static void check(boolean value) { if (!value) throw new AssertionError(); }
    public static void main(String[] args) throws Exception {
        CoreStartupProgress state = new CoreStartupProgress();
        long first = state.begin();
        CoreStartupProgress.Snapshot initial = state.snapshot();
        check(initial.stage == CoreStartupProgress.PREPARING);
        state.advance(first, CoreStartupProgress.STARTING);
        check(state.snapshot().stage == CoreStartupProgress.STARTING);
        check(initial.stage == CoreStartupProgress.PREPARING); // immutable snapshot
        state.advance(first, CoreStartupProgress.PREPARING);
        check(state.snapshot().stage == CoreStartupProgress.STARTING);
        state.advance(first, CoreStartupProgress.CONNECTING);
        state.advance(first, 50);
        check(state.snapshot().stage == CoreStartupProgress.CONNECTING);
        long retry = state.begin();
        state.advance(first, CoreStartupProgress.CONNECTING); // delayed old callback
        check(state.snapshot().attempt == retry && state.snapshot().stage == 0);
        Thread stale = new Thread(() -> {
            for (int i = 0; i < 500; i++) state.advance(first, CoreStartupProgress.CONNECTING);
        });
        stale.start();
        state.advance(retry, CoreStartupProgress.STARTING);
        stale.join();
        check(state.snapshot().attempt == retry && state.snapshot().stage == 1);
        state.advance(retry, -1);
        check(state.snapshot().stage == 1);
        state.advance(retry, CoreStartupProgress.CONNECTING);
        state.advance(retry, CoreStartupProgress.CONNECTING); // duplicate
        check(state.snapshot().stage == 2);
        System.out.println("PASS startup milestones: real order, immutable snapshots, duplicate/stale callbacks and retry isolation");
    }
}
