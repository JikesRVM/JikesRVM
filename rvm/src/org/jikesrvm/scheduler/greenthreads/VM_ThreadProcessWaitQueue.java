/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.scheduler.greenthreads;

import org.jikesrvm.VM;
import org.jikesrvm.runtime.VM_Magic;
import static org.jikesrvm.runtime.VM_SysCall.sysCall;
import org.jikesrvm.scheduler.VM_ProcessorLock;
import org.vmmagic.pragma.Interruptible;
import org.vmmagic.pragma.Uninterruptible;

/**
 * A wait queue for threads that are waiting for a process
 * to exit.  Used to implement the <code>exitValue()</code>
 * and <code>waitFor()</code> methods of <code>java.lang.Process</code>.
 *
 * <p> The correct operation of this queue relies on the
 * fact that at most one thread in the system will wait
 * for a particular process to exit.  The reason for
 * this restriction is that Unix semantics only allow us to
 * perform a <code>waitpid()</code> once
 * for a given process id.  {@link org.jikesrvm.scheduler.greenthreads.VM_Process} uses Java synchronization
 * to enforce this property.
 *
 * <p> Note that a strange issue arises on Linux: it is only possible
 * to wait for a child process to exit from the pthread that forked
 * the process.  (This due to the threading model used by Linux, where
 * pthreads have some characteristics of processes.)
 * Because of this limitation, in order for a Java thread to wait for
 * a process to exit, it must migrate to the <code>VM_Processor</code>
 * that created the process.  A <code>VM_ProcessorLock</code>
 * in the virtual processor object protects access to this queue, since it
 * may be accessed from another <code>VM_Processor</code>.
 * When polling for exited processes, we have to be careful to ignore
 * <code>VM_GreenThread</code>s that are still being dispatched on another
 * virtual processor.
 *
 * <p> I would imagine that AIX pthreads work correctly with respect to
 * allowing arbitrary pthreads to perform a <code>waitpid()</code>.
 *
 * @see org.jikesrvm.scheduler.greenthreads.VM_Process
 */
@Uninterruptible
public class VM_ThreadProcessWaitQueue extends VM_ThreadEventWaitQueue implements VM_ThreadEventConstants {

  /**
   * Class to safely downcast from <code>VM_ThreadEventWaitData</code>
   * to <code>VM_ThreadProcessWaitData</code>.
   * We use this because an actual Java cast could result in
   * a thread switch, which is obviously bad in uninterruptible
   * code.
   */
  @Uninterruptible
  private static class WaitDataDowncaster extends VM_ThreadEventWaitDataVisitor {

    public VM_ThreadProcessWaitData waitData;

    @Override
    public void visitThreadIOWaitData(VM_ThreadIOWaitData waitData) {
      if (VM.VerifyAssertions) VM._assert(false);
    }

    @Override
    public void visitThreadProcessWaitData(VM_ThreadProcessWaitData waitData) {
      this.waitData = waitData;
    }
  }

  /**
   * This queue's private wait data downcaster object.
   * Having it avoids the need to create them repeatedly.
   */
  private final WaitDataDowncaster myDowncaster = new WaitDataDowncaster();

  /**
   * Maximum number of processes that we can wait for.
   * Hopefully this is large enough.
   */
  public static final int MAX_NUM_PIDS = 256;

  /**
   * Value to mark pids which have finished.
   */
  public static final int PROCESS_FINISHED = -99;

  /**
   * Array specifying pids to query to see if the
   * processes they represent have exited.
   */
  private int[] pidArray;

  /**
   * Array for returning exit status of pids which have exited.
   */
  private int[] exitStatusArray;

  /**
   * Number of interrupted threads.
   */
  private int numInterrupted;

  /**
   * We don't depend on <code>waitpid()</code> being thread-safe.
   */
  private static final VM_ProcessorLock waitPidLock = new VM_ProcessorLock();

  /**
   * Constructor.
   */
  public VM_ThreadProcessWaitQueue() {
    pidArray = new int[MAX_NUM_PIDS];
    exitStatusArray = new int[MAX_NUM_PIDS];
  }

  /**
   * Check whether processes waited for by threads
   * on this queue have exited.
   */
  @Override
  public boolean pollForEvents() {
    VM_GreenThread thread;

    int numPids = 0;

    numInterrupted = 0;

    // Build array of pids to query
    thread = head;
    while (thread != null) {

      // Under no circumstances do we want to activate a thread
      // that is still being dispatched on another virtual processor.
      if (!thread.beingDispatched) {

        // Was the thread interrupted?
        if (thread.isInterrupted()) {
          ++numInterrupted;
        }

        if (numInterrupted == 0) {
          // Safe downcast from VM_ThreadEventWaitData to VM_ThreadProcessWaitData
          thread.waitData.accept(myDowncaster);
          VM_ThreadProcessWaitData waitData = myDowncaster.waitData;
          if (VM.VerifyAssertions) VM._assert(waitData == thread.waitData);

          // Add pid to array of pids to query
          pidArray[numPids] = waitData.pid;
        }

      } // !thread.beingDispatched

      ++numPids;
      thread = (VM_GreenThread)thread.getNext();
    } // while

    // If any threads are interrupted, then they're ready now,
    // so don't bother querying pids
    if (numInterrupted > 0) {
      return true;
    }

    waitPidLock.lock("mutex while reading pids");

    // Call sysWaitPids() to see which (if any) have finished
    sysCall.sysWaitPids(VM_Magic.objectAsAddress(pidArray), VM_Magic.objectAsAddress(exitStatusArray), numPids);

    waitPidLock.unlock();

    numPids = 0;

    // Mark threads whose processes have finished
    thread = head;
    while (thread != null) {
      if (pidArray[numPids] == PROCESS_FINISHED) {
        // Safe downcast from VM_ThreadEventWaitData to VM_ThreadProcessWaitData
        thread.waitData.accept(myDowncaster);
        VM_ThreadProcessWaitData waitData = myDowncaster.waitData;
        if (VM.VerifyAssertions) VM._assert(waitData == thread.waitData);

        waitData.finished = true;
        waitData.exitStatus = exitStatusArray[numPids];
      }

      ++numPids;
      thread = (VM_GreenThread)thread.getNext();
    }

    return true;
  }

  /**
   * Is given thread ready to wake up (either because the
   * process it was waiting for finished, or it was
   * interrupted)?
   */
  @Override
  public boolean isReady(VM_GreenThread thread) {
    // Do not wake up threads being dispatched on another processor!
    if (thread.beingDispatched) {
      return false;
    }

    // Wake up the thread if it has been interrupted
    if (thread.isInterrupted()) {
      thread.waitData.setFinishedAndInterrupted();
      return true;
    }

    // Safe downcast from VM_ThreadEventWaitData to VM_ThreadProcessWaitData
    thread.waitData.accept(myDowncaster);
    VM_ThreadProcessWaitData waitData = myDowncaster.waitData;
    if (VM.VerifyAssertions) VM._assert(waitData == thread.waitData);

    // See if this thread's process has finished
    boolean ready = waitData.finished;
    if (ready) {
      waitData.setFinished();
    }
    return ready;
  }

  /**
   * Dump text description of what given thread is waiting for.
   * For debugging.
   */
  @Interruptible
  @Override
  void dumpWaitDescription(VM_GreenThread thread) {
    // Safe downcast from VM_ThreadEventWaitData to VM_ThreadProcessWaitData.
    // Because this method may be called by other VM_Processors without
    // locking (and thus execute concurrently with other methods), do NOT
    // use the queue's private downcaster object.  Instead, create one
    // from scratch.
    WaitDataDowncaster downcaster = new WaitDataDowncaster();
    thread.waitData.accept(downcaster);
    VM_ThreadProcessWaitData waitData = downcaster.waitData;
    if (VM.VerifyAssertions) VM._assert(waitData == thread.waitData);

    VM.sysWrite("pid=", waitData.pid);
  }

  /**
   * Get string describing what given thread is waiting for.
   * This method must be interruptible!
   */
  @Interruptible
  @Override
  String getWaitDescription(VM_GreenThread thread) {
    // Safe downcast from VM_ThreadEventWaitData to VM_ThreadProcessWaitData.
    WaitDataDowncaster downcaster = new WaitDataDowncaster();
    thread.waitData.accept(downcaster);
    VM_ThreadProcessWaitData waitData = downcaster.waitData;
    if (VM.VerifyAssertions) VM._assert(waitData == thread.waitData);
    return "pid=" + waitData.pid;
  }
}
