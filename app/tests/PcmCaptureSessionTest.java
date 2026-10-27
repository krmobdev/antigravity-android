package com.agy.bridge;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class PcmCaptureSessionTest {
    static void require(boolean ok,String name) { if(!ok)throw new AssertionError(name); }
    static void await(CountDownLatch latch) {
        try { require(latch.await(3,TimeUnit.SECONDS),"worker timeout"); }
        catch(InterruptedException e) { throw new AssertionError(e); }
    }
    static class Fake implements PcmCaptureSession.Source {
        final CountDownLatch entered=new CountDownLatch(1), proceed=new CountDownLatch(1), released=new CountDownLatch(1);
        final AtomicInteger closes=new AtomicInteger(), stops=new AtomicInteger(), reads=new AtomicInteger();
        int size=1280;
        boolean fail;
        boolean flooding;
        public int read(byte[] bytes) {
            entered.countDown();
            if(!flooding)await(proceed);
            reads.incrementAndGet();
            if(fail)throw new IllegalStateException("fixture");
            java.util.Arrays.fill(bytes,(byte)7);
            return size;
        }
        public void stop() { stops.incrementAndGet(); }
        public void release() { closes.incrementAndGet(); released.countDown(); }
    }
    static void once(Fake f) {
        await(f.released);
        require(f.closes.get()==1&&f.stops.get()==1,"release exactly once");
    }
    static void releaseCompletionBarrier() throws Exception {
        PcmCaptureSession capture = new PcmCaptureSession(() -> 0);
        CountDownLatch releasing = new CountDownLatch(1), allowRelease = new CountDownLatch(1);
        CountDownLatch stopped = new CountDownLatch(1);
        Fake source = new Fake() {
            public void release() {
                releasing.countDown();
                await(allowRelease);
                super.release();
            }
        };
        source.fail = true;
        source.proceed.countDown();
        capture.start(source);
        await(releasing); // Worker has claimed close, but AudioRecord.release has not finished.
        Thread stopper = new Thread(() -> { capture.stop(); stopped.countDown(); });
        stopper.start();
        boolean returnedBeforeRelease;
        try { returnedBeforeRelease = stopped.await(200, TimeUnit.MILLISECONDS); }
        finally { allowRelease.countDown(); stopper.join(3000); }
        await(stopped);
        require(!returnedBeforeRelease, "stop must wait for concurrent hardware release completion");
        once(source);
        Fake next = new Fake();
        require("OK".equals(capture.start(next)), "restart after completed release");
        capture.stop(); next.proceed.countDown(); once(next);
    }
    public static void main(String[] args) throws Exception {
        releaseCompletionBarrier();
        PcmCaptureSession capture=new PcmCaptureSession(()->0);
        Fake failed=new Fake();failed.fail=true;failed.proceed.countDown();
        require("OK".equals(capture.start(failed)),"start error fixture");
        once(failed);require("ERR_PCM_READ".equals(capture.failure()),"read failure retained");
        capture.stop();once(failed);

        Fake overflow=new Fake();overflow.flooding=true;
        capture.start(overflow);once(overflow);
        require("ERR_PCM_OVERFLOW".equals(capture.failure()),"overflow explicit");
        require(overflow.reads.get()==21,"buffer bounded to 20 frames");
        capture.stop();once(overflow);

        for(int size:new int[]{3,-1,1282}) {
            Fake invalid=new Fake();invalid.size=size;invalid.proceed.countDown();
            capture.start(invalid);once(invalid);
            require("ERR_PCM_READ".equals(capture.failure()),"invalid frame rejected");
            capture.stop();
        }

        Fake old=new Fake();old.fail=true;capture.start(old);await(old.entered);
        capture.stop();once(old);
        Fake fresh=new Fake();capture.start(fresh);await(fresh.entered);
        old.proceed.countDown(); // Old read fails after the new capture started.
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
        while(old.reads.get()==0&&System.nanoTime()<until)Thread.yield();
        Thread.sleep(25);
        require(capture.isRecording()&&capture.failure()==null,"late worker cannot poison new capture");
        require(capture.poll().length==0,"no stale frames");
        Fake duplicate=new Fake();require("ERR_ALREADY_RECORDING".equals(capture.start(duplicate)),"no double capture");
        once(duplicate);require(capture.isRecording(),"duplicate cannot stop active");
        capture.stop();fresh.proceed.countDown();once(fresh);

        AtomicLong clock=new AtomicLong(0);
        capture=new PcmCaptureSession(clock::get);
        Fake limited=new Fake();capture.start(limited);await(limited.entered);
        clock.set(210000);limited.proceed.countDown();once(limited);
        require("ERR_PCM_LIMIT".equals(capture.failure()),"hard time limit");
        capture.stop();once(limited);
        require("ERR_PCM_NOT_RECORDING".equals(capture.failure()),"stopped poll state");
        System.out.println("PASS PCM lifecycle: read failure, overflow, malformed frames, exactly-once close, late worker/restart, double start, time limit");
    }
}
