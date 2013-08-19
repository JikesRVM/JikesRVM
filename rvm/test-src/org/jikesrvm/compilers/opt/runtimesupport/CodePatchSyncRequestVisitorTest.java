/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.compilers.opt.runtimesupport;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.jikesrvm.junit.runners.RequiresJikesRVM;
import org.jikesrvm.junit.runners.VMRequirements;
import org.jikesrvm.scheduler.RVMThread;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

@RunWith(VMRequirements.class)
@Category(RequiresJikesRVM.class)
public class CodePatchSyncRequestVisitorTest {

  private CodePatchSyncRequestVisitor codePatchSyncReqVisitor;

  @Before
  public void createVisitor() {
    codePatchSyncReqVisitor = new CodePatchSyncRequestVisitor();
  }

  @Test
  public void normalThreadsTakePartInHandshake() throws Exception {
    RVMThread normalThread = RVMThread.getCurrentThread();
    assertTrue(codePatchSyncReqVisitor.includeThread(normalThread));
  }

  @Test
  public void checkAndSignalSetsCodePatchingFlag() throws Exception {
    Thread t = new ThreadWithTimeout();
    t.start();
    RVMThread rvmThread = JikesRVMSupport.getThread(t);
    assertFalse(rvmThread.codePatchSyncRequested);
    codePatchSyncReqVisitor.checkAndSignal(rvmThread);
    assertTrue(rvmThread.codePatchSyncRequested);
    t.interrupt();
  }

  @SuppressWarnings("deprecation")
  @Test(timeout = 100)
  public void codePatchingWorksWhenAThreadIsSuspended() throws Exception {
    Thread t = new SuspendedThread();
    t.start();
    RVMThread.softHandshake(codePatchSyncReqVisitor);
    t.interrupt();
    t.resume();
  }

  @Test(timeout = 100)
  public void codePatchingWorksWhenAThreadIsWaiting() throws Exception {
    triggerCodePatching(new WaitingThread());
  }

  @Test(timeout = 100)
  public void codePatchingWorksWhenAThreadIsSleeping() throws Exception {
    triggerCodePatching(new SleepingThread());
  }

  @Test(timeout = 100)
  public void codePatchingWorksWhenAThreadIsParked() throws Exception {
    triggerCodePatching(new ParkedThread());
  }

  private void triggerCodePatching(IdlingThread idlingThread) {
    idlingThread.start();
    RVMThread.softHandshake(codePatchSyncReqVisitor);
    idlingThread.interrupt();
  }

  private static class ThreadWithTimeout extends Thread {

    @Override
    public void run() {
      synchronized (this) {
        try {
          wait(1000);
        } catch (InterruptedException e) {
          return;
        }
      }
    }
  }

  private static class SuspendedThread extends Thread {
    @Override
    @SuppressWarnings("deprecation")
    public void run() {
      while (!interrupted()) {
        suspend();
      }
    }
  }

  private abstract static class IdlingThread extends Thread {

    @Override
    public void run() {
     while (!interrupted()) {
       try {
        synchronized (this) {
          idleImpl();
        }
      } catch (InterruptedException e) {
        return;
      }
     }
    }

    protected abstract void idleImpl() throws InterruptedException, IllegalMonitorStateException;

  }

  private static class WaitingThread extends IdlingThread {
    @Override
    protected void idleImpl() throws InterruptedException,
        IllegalMonitorStateException {
      while (true) {
        wait();
      }
    }
  }

  private static class SleepingThread extends IdlingThread {
    @Override
    protected void idleImpl() throws InterruptedException,
        IllegalMonitorStateException {
      sleep(Long.MAX_VALUE);
    }
  }

  private static class ParkedThread extends IdlingThread {
    @Override
    protected void idleImpl() throws InterruptedException,
        IllegalMonitorStateException {
      try {
        RVMThread.getCurrentThread().park(true, Long.MAX_VALUE);
      } catch (Throwable e) {
        e.printStackTrace();
      }
    }
  }

}
