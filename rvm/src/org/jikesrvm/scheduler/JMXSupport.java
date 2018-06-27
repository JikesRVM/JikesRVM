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
package org.jikesrvm.scheduler;

import java.util.HashMap;

import org.jikesrvm.VM;
import org.jikesrvm.runtime.StackTrace;
import org.jikesrvm.runtime.StackTrace.Element;
import org.vmmagic.pragma.Uninterruptible;

public class JMXSupport {

  private static HashMap<Long, Thread> threadIdToThread;

  private static final org.jikesrvm.mm.mmtk.Lock peakThreadCountLock =
      new org.jikesrvm.mm.mmtk.Lock("peakThreadCount");

  private static int peakThreadCount;

  private static final org.jikesrvm.mm.mmtk.Lock startedThreadCountLock =
      new org.jikesrvm.mm.mmtk.Lock("startedThreadCount");

  private static long startedThreadCount;

  /**
   * Updates the current peak thread count.
   * <p>
   * Note: this must be uninterruptible because it's called from the
   * {@link RVMThread#start()} and that method must not have yieldpoints.
   *
   * @param liveThreadCount the current count of live threads
   * @param numActiveSystemThreads the current count of live system threads
   */
  @Uninterruptible
  static void updatePeakThreadCount(int liveThreadCount, int numActiveSystemThreads) {
    int currentThreadCount = liveThreadCount - numActiveSystemThreads;
    peakThreadCountLock.acquire();
    if (currentThreadCount > peakThreadCount) {
      peakThreadCount = currentThreadCount;
    }
    peakThreadCountLock.release();
  }

  public static int getPeakThreadCount() {
    int currentCount = 0;
    peakThreadCountLock.acquire();
    currentCount = peakThreadCount;
    peakThreadCountLock.release();
    return currentCount;
  }

  public static void resetPeakThreadCount() {
    peakThreadCountLock.acquire();
    peakThreadCount = getLiveThreadCount();
    peakThreadCountLock.release();
  }

  /**
   * Increases the number of started threads
   * <p>
   * Note: this must be uninterruptible because it's called from the
   * {@link RVMThread#start()} and that method must not have yieldpoints.
   */
  @Uninterruptible
  static void increaseStartedThreadCount() {
    startedThreadCountLock.acquire();
    startedThreadCount++;
    startedThreadCountLock.release();
  }

  public static long getStartedThreadCount() {
    long startedThreadCountTemp = 0;
    startedThreadCountLock.acquire();
    startedThreadCountTemp = startedThreadCount;
    startedThreadCountLock.release();
    return startedThreadCountTemp;
  }


  public static int getLiveThreadCount() {
    return RVMThread.getNumActiveThreads() - RVMThread.getNumActiveSystemThreads();
  }

  public static int getLiveDaemonCount() {
    return RVMThread.getNumActiveDaemons();
  }

  /**
   * @return thread ids (those of java.lang.Thread and not of our internal
   *  threads!)
   */
  public static synchronized long[] getAllLiveThreadIds() {
    Thread[] liveThreads = RVMThread.getLiveThreadsForJMX();
    int liveThreadCount = liveThreads.length;

    int mapSizeEstimate = (int) (liveThreadCount * 1.5);
    threadIdToThread = new HashMap<Long, Thread>(mapSizeEstimate);

    long[] ids = new long[liveThreadCount];
    for (int i = 0; i < liveThreadCount; i++) {
      Thread liveThread = liveThreads[i];
      if (liveThread == null) {
        // Once a null thread was found, all following threads must also be null
        if (VM.VerifyAssertions) {
          for (int j = i + 1; j < liveThreadCount; j++) {
            liveThread = liveThreads[j];
            VM._assert(liveThread == null);
          }
        }
        break;
      }
      long threadId = liveThread.getId();
      ids[i] = threadId;
      threadIdToThread.put(Long.valueOf(threadId), liveThread);
    }
    return ids;
  }

  public static synchronized Thread getThreadForId(long id) {
    getAllLiveThreadIds();
    Thread thread = threadIdToThread.get(id);
    return thread;
  }


  /**
   * Checks whether the thread is in native according to JMX.
   * @param t a thread
   * @return whether the thread is executing JNI code
   */
  public static boolean isInNative(RVMThread t) {
    t.monitor().lockNoHandshake();
    boolean inNative = t.isInNativeAccordingToJMX();
    t.monitor().unlock();
    return inNative;
  }

  /**
   * Checks whether the thread is currently suspended
   * according to JMX.
   * @param t a thread
   * @return whether {@code Thread.suspend()} was called on the
   *  thread
   */
  public static boolean isSuspended(RVMThread t) {
    t.monitor().lockNoHandshake();
    boolean isSuspended = t.blockedFor(RVMThread.suspendBlockAdapter);
    t.monitor().unlock();
    return isSuspended;
  }

  public static long getWaitingCount(RVMThread rvmThread) {
    return rvmThread.getTotalWaitingCount();
  }

  public static long getWaitingTime(RVMThread rvmThread) {
    return rvmThread.getTotalWaitedTime();
  }

  public static StackTraceElement[] getStackTraceForThread(RVMThread rvmThread) {
    RVMThread currentThread = RVMThread.getCurrentThread();

    Element[] elements = null;
    if (rvmThread == currentThread) {
      StackTrace st = new StackTrace();
      // Skip 1 frame (the frame of this call)
      elements = st.stackTraceNoException(1);
    } else {
        // Wait until other thread is blocked
        while (true) {
          rvmThread.safeBlock(RVMThread.stackTraceBlockAdapter);
          rvmThread.monitor().lockNoHandshake();
          if (rvmThread.blockedFor(RVMThread.stackTraceBlockAdapter)) {
            rvmThread.monitor().unlock();
            break;
          }
          rvmThread.monitor().unlock();
        }
        StackTrace st = new StackTrace(rvmThread);
        // Skip 2 frames: the frames for yieldpointFrom* and yieldpoint
        // TODO this assumes that the thread is blocked in Java (and not in JNI)
        elements = st.stackTraceNoException(2);
        rvmThread.unblock(RVMThread.stackTraceBlockAdapter);
    }

    return StackTrace.convertToJavaClassLibraryStackTrace(elements);
  }

}
