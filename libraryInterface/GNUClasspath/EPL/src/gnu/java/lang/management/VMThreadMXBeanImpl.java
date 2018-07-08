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
package gnu.java.lang.management;

import java.lang.management.LockInfo;
import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import org.jikesrvm.scheduler.JMXSupport;
import org.jikesrvm.scheduler.RVMThread;
import org.jikesrvm.util.UnimplementedError;

final class VMThreadMXBeanImpl {

  /**
   * Returns the ids of deadlocked threads occurring due
   * to either monitor or ownable synchronizer ownership.
   * Only called if ownable synchronizer monitoring is
   * supported.
   *
   * @return an array of thread identifiers.
   */
  static long[] findDeadlockedThreads() {
    throw new UnimplementedError();
  }

  /**
   * Returns the ids of deadlocked threads occurring due
   * to monitor ownership.
   *
   * @return an array of thread identifiers.
   */
  static long[] findMonitorDeadlockedThreads() {
    throw new UnimplementedError();
  }

  /**
   * Returns the identifiers of all live threads.
   *
   * @return an array of thread identifiers.
   */
  static long[] getAllThreadIds() {
    return JMXSupport.getAllLiveThreadIds();
  }

  /**
   * Returns the number of nanoseconds of CPU time
   * the current thread has used, if supported.
   *
   * @return the number of nanoseconds.
   */
  static long getCurrentThreadCpuTime() {
    return getThreadCpuTime(getIDForCurrentThread());
  }

  private static long getIDForCurrentThread() {
    return RVMThread.getCurrentThread().getJavaLangThread().getId();
  }

  /**
   * Returns the number of nanoseconds of user time
   * the current thread has used, if supported.
   *
   * @return the number of nanoseconds.
   */
  static long getCurrentThreadUserTime() {
    return getThreadUserTime(getIDForCurrentThread());
  }

  /**
   * Returns the number of live daemon threads.
   *
   * @return the number of live daemon threads.
   */
  static int getDaemonThreadCount() {
    return JMXSupport.getLiveDaemonCount();
  }

  /**
   * Fills out the information on ownable synchronizers
   * in the given {@link java.lang.management.ThreadInfo}
   * object if supported.
   *
   * @param info the object to fill in.
   */
  static void getLockInfo(ThreadInfo info) {
    throw new UnimplementedError();
  }

  /**
   * Fills out the information on monitor usage
   * in the given {@link java.lang.management.ThreadInfo}
   * object if supported.
   *
   * @param info the object to fill in.
   */
  static void getMonitorInfo(ThreadInfo info) {
    throw new UnimplementedError();
  }

  /**
   * Returns the current peak number of live threads.
   *
   * @return the current peak.
   */
  static int getPeakThreadCount() {
    return JMXSupport.getPeakThreadCount();
  }

  /**
   * Returns the current number of live threads.
   *
   * @return the current number of live threads.
   */
  static int getThreadCount() {
    return JMXSupport.getLiveThreadCount();
  }

  /**
   * Returns the number of nanoseconds of CPU time
   * the given thread has used, if supported.
   *
   * @param id the id of the thread to probe.
   * @return the number of nanoseconds.
   */
  static long getThreadCpuTime(long id) {
    throw new UnimplementedError();
  }

  /**
   * Returns a {@link java.lang.management.ThreadInfo}
   * object for the given thread id with a stack trace to
   * the given depth (0 for empty, Integer.MAX_VALUE for
   * full).
   *
   * @param id the id of the thread whose info should be returned.
   * @param maxDepth the depth of the stack trace.
   * @return a {@link java.lang.management.ThreadInfo} instance.
   */
  static ThreadInfo getThreadInfoForId(long id, int maxDepth) {
    Thread thread = getThreadForId(id);
    if (thread == null) return null;
    Constructor<ThreadInfo> cons = null;
    try {
      // ensure class is at least resolved
      Class.forName("java.lang.management.ThreadInfo");

      cons = ThreadInfo.class.getDeclaredConstructor(Long.TYPE, String.class,
                Thread.State.class, Long.TYPE,
                Long.TYPE, String.class,
                Long.TYPE, String.class,
                Long.TYPE, Long.TYPE,
                Boolean.TYPE,Boolean.TYPE,
                StackTraceElement[].class, MonitorInfo[].class,
                LockInfo[].class);


      cons.setAccessible(true);
      RVMThread rvmThread = JikesRVMSupport.getThread(thread);
      long blockedCount = 0; // TODO number of times blocked for Java monitors
      long blockedTime = 0; // TODO total time blocked for Java monitors
      long waitingCount = JMXSupport.getWaitingCount(rvmThread);
      long waitingTime = JMXSupport.getWaitingTime(rvmThread);
      boolean inNative = JMXSupport.isInNative(rvmThread);
      boolean suspended = JMXSupport.isSuspended(rvmThread);

      StackTraceElement[] stackTrace;
      if (maxDepth == 0) {
        stackTrace = null;
      } else {
        stackTrace = JMXSupport.getStackTraceForThread(rvmThread);
        int newMax = Math.min(stackTrace.length, maxDepth);
        StackTraceElement[] reducedStackTrace = new StackTraceElement[newMax];
        int srcPos = stackTrace.length - newMax;
        System.arraycopy(stackTrace, srcPos, reducedStackTrace,
            0, newMax);
        stackTrace = reducedStackTrace;
      }

      MonitorInfo[] emptyMonitorInfo = new MonitorInfo[0];
      LockInfo[] emptyLockInfo = new LockInfo[0];
      return cons.newInstance(id, thread.getName(), thread.getState(),
           blockedCount, blockedTime,
           null, -1, null, waitingCount,
           waitingTime,
           inNative,
           suspended, stackTrace, emptyMonitorInfo, emptyLockInfo);
    } catch (NoSuchMethodException e) {
      throw (Error) new InternalError("Couldn't get ThreadInfo constructor").initCause(e);
    } catch (InstantiationException e) {
      throw (Error) new InternalError("Couldn't create ThreadInfo").initCause(e);
    } catch (IllegalAccessException e) {
      throw (Error) new InternalError("Couldn't access ThreadInfo").initCause(e);
    } catch (InvocationTargetException e) {
      throw (Error) new InternalError("ThreadInfo's constructor threw an exception").initCause(e);
    } catch (ClassNotFoundException e) {
      throw (Error) new InternalError("Problem resolving ThreadInfo").initCause(e);
    }
  }

  /**
   * Returns the Thread instance for the given
   * thread id.
   *
   * @param id the id of the thread to find.
   * @return the Thread.
   */
  private static Thread getThreadForId(long id) {
    return JMXSupport.getThreadForId(id);
  }

  /**
   * Returns the number of nanoseconds of user time
   * the given thread has used, if supported.
   *
   * @param id the id of the thread to probe.
   * @return the number of nanoseconds.
   */
  static long getThreadUserTime(long id) {
    throw new UnimplementedError();
  }

  /**
   * Returns the number of threads started.
   *
   * @return the number of started threads.
   */
  static long getTotalStartedThreadCount() {
    return JMXSupport.getStartedThreadCount();
  }

  /**
   * Resets the peak thread count.
   */
  static void resetPeakThreadCount() {
    JMXSupport.resetPeakThreadCount();
  }

}
