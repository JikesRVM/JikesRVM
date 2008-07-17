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

import org.jikesrvm.runtime.Time;
import org.jikesrvm.scheduler.Processor;
import org.jikesrvm.scheduler.Scheduler;

/**
 * A collection of static methods for waiting on some type of event.
 * These methods may allocate objects for recordkeeping purposes,
 * and thus may not be called from within the scheduler proper
 * (e.g., <code>Thread</code>, which is uninterruptible).
 */
class Wait {
  private static boolean noIoWait = false;

  /**
   * Called by VM.sysExit() to disable IO waits while the VM is
   * shutting down (and flushing its output streams).
   * The reason is that we can't be sure that thread switching
   * is possible during shutdown.
   */
  public static void disableIoWait() {
    noIoWait = true;
  }

  /**
   * Given a total number of seconds to wait, computes timestamp
   * of time when the wait should time out.
   * Leaves negative times unchanged, since these indicate infinite waits.
   */
  private static long getMaxWaitNano(double totalWaitTimeInSeconds) {
    // If a non-negative wait time was specified, it specifies the total
    // number of seconds to wait, so convert it to a timestamp (number of
    // seconds past the epoch).  Negative value indicates indefinite wait,
    // in which case nothing needs to be done.
    long maxWaitNano = (long)(totalWaitTimeInSeconds * 1e9);
    if (maxWaitNano >= 0) {
      maxWaitNano += Time.nanoTime();
    }
    return maxWaitNano;
  }

  /**
   * Suspend execution of current thread until "fd" can be read without
   * blocking.
   * @param fd the native file descriptor to wait on
   * @param totalWaitTime the number of seconds to wait; negative values
   *   indicate an infinite wait time
   * @return the wait data object indicating the result of the wait
   */
  public static ThreadIOWaitData ioWaitRead(int fd, double totalWaitTime) {
    // Create wait data to represent the event the thread is
    // waiting for
    long maxWaitNano = getMaxWaitNano(totalWaitTime);
    ThreadIOWaitData waitData = new ThreadIOWaitData(maxWaitNano);
    waitData.readFds = new int[]{fd};

    if (noIoWait) {
      waitData.markAllAsReady();
    } else {
      // Put the thread on the ioQueue
      GreenThread.ioWaitImpl(waitData);
    }

    return waitData;
  }

  /**
   * Infinite wait for a read file descriptor to become ready.
   */
  public static ThreadIOWaitData ioWaitRead(int fd) {
    return ioWaitRead(fd, ThreadEventConstants.WAIT_INFINITE);
  }

  /**
   * Suspend execution of current thread until "fd" can be written without
   * blocking.
   * @param fd the native file descriptor to wait on
   * @param totalWaitTime the number of seconds to wait; negative values
   *   indicate an infinite wait time
   * @return the wait data object indicating the result of the wait
   */
  public static ThreadIOWaitData ioWaitWrite(int fd, double totalWaitTime) {
    // Create wait data to represent the event the thread is
    // waiting for
    long maxWaitNano = getMaxWaitNano(totalWaitTime);
    ThreadIOWaitData waitData = new ThreadIOWaitData(maxWaitNano);
    waitData.writeFds = new int[]{fd};

    if (noIoWait) {
      waitData.markAllAsReady();
    } else {
      // Put the thread on the ioQueue
      GreenThread.ioWaitImpl(waitData);
    }

    return waitData;
  }

  /**
   * Infinite wait for a write file descriptor to become ready.
   */
  public static ThreadIOWaitData ioWaitWrite(int fd) {
    return ioWaitWrite(fd, ThreadEventConstants.WAIT_INFINITE);
  }

  /**
   * Suspend execution of current thread until any of the given
   * file descriptors have become ready, or the wait times out.
   * When this method returns, the file descriptors which are
   * ready will have
   * {@link ThreadIOConstants#FD_READY_BIT FD_READY_BIT} set,
   * and file descriptors which are invalid will have
   * {@link ThreadIOConstants#FD_INVALID_BIT FD_INVALID_BIT} set.
   *
   * @param readFds array of read file descriptors
   * @param writeFds array of write file descriptors
   * @param exceptFds array of exception file descriptors
   * @param totalWaitTime amount of time to wait, in seconds; if
   *   negative, wait indefinitely
   * @param fromNative true if this select is being called
   *   from native code
   * @return 0 no error, -1 failure due to threading/GC disabled
   */
  public static int ioWaitSelect(int[] readFds, int[] writeFds, int[] exceptFds, double totalWaitTime,
                                  boolean fromNative) {
    // Check if we're entering from native we can sensibly wait
    if (fromNative) {
      if(!Processor.getCurrentProcessor().threadSwitchingEnabled() ||
         Scheduler.getCurrentThread().getDisallowAllocationsByThisThread()) {
        return -1;
      }
    }
    // Create wait data to represent the event that the thread is
    // waiting for
    long maxWaitNano = getMaxWaitNano(totalWaitTime);
    ThreadIOWaitData waitData = new ThreadIOWaitData(maxWaitNano);
    waitData.readFds = readFds;
    waitData.writeFds = writeFds;
    waitData.exceptFds = exceptFds;

    if (fromNative) {
      waitData.setNative();
    }

    // Put the thread on the ioQueue
    if (noIoWait) {
      waitData.markAllAsReady();
    } else {
      GreenThread.ioWaitImpl(waitData);
    }
    return 0;
  }

  /**
   * Suspend execution of current thread until process whose
   * pid is given has finished, or the thread is interrupted.
   * @param process the object representing the process to wait for
   * @param totalWaitTime number of seconds to wait, or negative if
   *   caller wants to wait indefinitely
   * @return the <code>ThreadProcessWaitData</code> representing
   *   the state of the process
   */
  public static ThreadProcessWaitData processWait(VMProcess process, double totalWaitTime)
      throws InterruptedException {

    // Create wait data to represent the event the thread is
    // waiting for
    long maxWaitNano = getMaxWaitNano(totalWaitTime);
    ThreadProcessWaitData waitData = new ThreadProcessWaitData(process.getPid(), maxWaitNano);

    // Put the thread on the processWaitQueue
    GreenThread.processWaitImpl(waitData, process);

    return waitData;
  }
}
