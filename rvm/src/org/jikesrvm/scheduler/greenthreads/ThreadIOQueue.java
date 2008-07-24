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
import static org.jikesrvm.runtime.SysCall.sysCall;
import org.jikesrvm.scheduler.ProcessorLock;
import org.vmmagic.pragma.Interruptible;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.pragma.Unpreemptible;

/**
 * A list of threads waiting for i/o data to become available.
 *
 * To avoid blocking a virtual processor on an i/o operation, and to
 * avoid polling, we maintain a list of file/socket descriptors (fd's) that
 * need to be checked for data availability. When data becomes available
 * on an fd, as indicated by the unix "select()" system call, we allow the
 * corresponding thread to resume execution.
 *
 * At the moment we only use this technique for network i/o. The same could be
 * done for disk i/o, but we currently don't bother: we use blocking disk i/o
 * and assume that it will complete immediately.
 */
@Uninterruptible
public final class ThreadIOQueue extends ThreadEventWaitQueue
    implements ThreadEventConstants, ThreadIOConstants {

  /**
   * Class to safely downcast from <code>ThreadEventWaitData</code>
   * to <code>ThreadIOWaitData</code>.
   * We use this because an actual Java cast could result in
   * a thread switch, which is obviously bad in uninterruptible
   * code.
   */
  @Uninterruptible
  private static final class WaitDataDowncaster extends ThreadEventWaitDataVisitor {

    private ThreadIOWaitData waitData;

    @Override
    void visitThreadIOWaitData(ThreadIOWaitData waitData) {
      this.waitData = waitData;
    }

    @Override
    void visitThreadProcessWaitData(ThreadProcessWaitData waitData) {
      if (VM.VerifyAssertions) VM._assert(false);
    }
  }

  /**
   * Private downcaster object for this queue.
   * Avoids having to create them repeatedly.
   */
  private final WaitDataDowncaster myDowncaster = new WaitDataDowncaster();

  private static final int FD_SETSIZE = 2048;

  /**
   * Array containing read, write, and exception file descriptor sets.
   * Used by sysNetSelect().
   */
  private int[] allFds = new int[3 * FD_SETSIZE];

  /** Offset of read file descriptors in allFds. */
  public static final int READ_OFFSET = 0 * FD_SETSIZE;

  /** Offset of write file descriptors in allFds. */
  public static final int WRITE_OFFSET = 1 * FD_SETSIZE;

  /** Offset of exception file descriptors in allFds. */
  public static final int EXCEPT_OFFSET = 2 * FD_SETSIZE;

  /**
   * Count of threads observed to be killed while executing
   * Java code (not native C code).
   */
  private int numKilledInJava;

  /** Guard for updating "selectInProgress" flag. */
  public static final ProcessorLock selectInProgressMutex = new ProcessorLock();

  /**
   * Copy file descriptors from source array to destination array
   * starting at given offset. The size of the source array is used
   * to determine the number of descriptors to copy.
   * @param dest the destination file descriptor array
   * @param offset offset in destination array
   * @param src the source file descriptor array
   * @return number of descriptors added
   */
  private static int addFileDescriptors(int[] dest, int offset, int[] src) {
    //TODO: Remove manual System.arraycopy
    for (int aSrc : src) {
      dest[offset++] = aSrc;
    }
    return src.length;
  }

  /**
   * Determine if thread should be woken up by an interrupt.
   * We only allow this for threads killed using
   * java.lang.Thread.stop(), which is deprecated and stupid.
   * So, this method should generally never return true.
   */
  private static boolean isKilled(GreenThread thread) {
    return thread.waitData.isNative() &&
           thread.isInterrupted() &&
           thread.getCauseOfThreadDeath() != null;
  }

  /**
   * Update array of event data file descriptors to mark the
   * ones that have become ready or invalid, based on the given array of select
   * file descriptors.
   * @param waitDataFds array of file descriptors from the wait data object
   * @param waitDataOffset offset of the wait data's entries
   * @param selectFds array of file descriptors returned from the select
   *     syscall
   * @param setOffset offset of the particular set (read, write, exception)
   *     within the select file descriptor array
   * @return the number of file descriptors which became ready
   *     or invalid
   */
  private int updateStatus(int[] waitDataFds, int waitDataOffset, int[] selectFds, int setOffset) {

    if (waitDataFds == null) {
      return 0;
    }

    int numReady = 0;
    int selectIndex = setOffset + waitDataOffset;
    for (int i = 0; i < waitDataFds.length; ++i) {
      //boolean ready = selectFds[selectIndex++] == FD_READY;
      int fd = selectFds[selectIndex++];
      switch (fd) {
        case FD_READY:
          waitDataFds[i] |= FD_READY_BIT;
          ++numReady;
          break;

        case FD_INVALID:
          waitDataFds[i] |= FD_INVALID_BIT;
          ++numReady;
          break;

        default:
          waitDataFds[i] &= FD_MASK;
      }
    }

    return numReady;
  }

  //-----------//
  // Interface //
  //-----------//

  /**
   * Poll file descriptors to see which ones have become ready.
   * Called from superclass's {@link ThreadEventWaitQueue#isReady()} method.
   * We also check for threads that have been killed while
   * blocked in Java code, since they should be woken up as well.
   * @return true if poll was successful, false if not
   */
  @Override
  public boolean pollForEvents() {

    numKilledInJava = 0;

    // FIXME: no checking is done to ensure that sets don't overflow.

    // Interrogate all threads in the queue to determine
    // which file descriptors they are waiting for
    GreenThread thread = head;
    int readCount = 0, writeCount = 0, exceptCount = 0;
    while (thread != null) {
      // Was the thread killed while in Java code (i.e., not native?)
      // If so, it's ready.
      if (isKilled(thread)) {
        ++numKilledInJava;
      }

      // Add to set of file descriptors to poll using select().
      // But don't bother if there are threads killed while in Java,
      // since we won't actually do the select() in that case.
      if (numKilledInJava == 0) {
        // Safe downcast from ThreadEventWaitData to ThreadIOWaitData.
        thread.waitData.accept(myDowncaster);
        ThreadIOWaitData waitData = myDowncaster.waitData;
        if (VM.VerifyAssertions) VM._assert(waitData == thread.waitData);

        // Add file descriptors from wait data to the array of
        // fds that we pass to select().
        if (waitData.readFds != null) {
          waitData.readOffset = readCount;
          readCount += addFileDescriptors(allFds, READ_OFFSET + readCount, waitData.readFds);
        }
        if (waitData.writeFds != null) {
          waitData.writeOffset = writeCount;
          writeCount += addFileDescriptors(allFds, WRITE_OFFSET + writeCount, waitData.writeFds);
        }
        if (waitData.exceptFds != null) {
          waitData.exceptOffset = exceptCount;
          exceptCount += addFileDescriptors(allFds, EXCEPT_OFFSET + exceptCount, waitData.exceptFds);
        }
      }

      thread = (GreenThread)thread.getNext();
    }

    // If there are killed non-native threads, just wake up one of
    // those and don't bother with the select().
    if (numKilledInJava > 0) {
      return true;
    }

    // Do the select()
    GreenProcessor.getCurrentProcessor().isInSelect = true;
    selectInProgressMutex.lock("select in progress mutex");
    int ret = sysCall.sysNetSelect(allFds, readCount, writeCount, exceptCount);
    selectInProgressMutex.unlock();
    GreenProcessor.getCurrentProcessor().isInSelect = false;

    // Did the select() succeed?
    return ret != -1;
  }

  /**
   * Determine whether or not given thread has become ready
   * to run, i.e., because a file descriptor it was waiting for
   * became ready.  If the thread is ready, update its
   * wait flags appropriately.
   */
  @Override
  public boolean isReady(GreenThread thread) {
    // Safe downcast from ThreadEventWaitData to ThreadIOWaitData.
    thread.waitData.accept(myDowncaster);
    ThreadIOWaitData waitData = myDowncaster.waitData;
    if (VM.VerifyAssertions) VM._assert(waitData == thread.waitData);

    // Any threads killed while blocked in Java
    // are woken up.
    if (isKilled(thread)) {
      waitData.setFinishedAndInterrupted();
      return true;
    }

    // Check read, write, and exception file descriptor sets,
    // and set the FD_READY_BIT in any of them that are now ready.
    // Also, set FD_INVALID_BIT in any fds that are invalid.
    int numReady = 0;
    numReady += updateStatus(waitData.readFds, waitData.readOffset, allFds, READ_OFFSET);
    numReady += updateStatus(waitData.writeFds, waitData.writeOffset, allFds, WRITE_OFFSET);
    numReady += updateStatus(waitData.exceptFds, waitData.exceptOffset, allFds, EXCEPT_OFFSET);

    // If any fds became ready, then this thread is a candidate for
    // being scheduled.
    boolean ready = (numReady > 0);
    if (ready) {
      waitData.setFinished();
    }
    return ready;
  }

  private void dumpFds(int[] fds) {
    if (fds == null) {
      return;
    }
    for (int i = 0; i < fds.length; ++i) {
      VM.sysWrite(fds[i] & FD_MASK);
      if ((fds[i] & FD_READY_BIT) != 0) {
        VM.sysWrite('+');
      }
      if ((fds[i] & FD_INVALID_BIT) != 0) {
        VM.sysWrite('X');
      }
      if (i != fds.length - 1) {
        VM.sysWrite(',');
      }
    }
  }

  /** Downcaster for dumping */
  private static final WaitDataDowncaster downcaster = new WaitDataDowncaster();

  /**
   * Dump text description of what given thread is waiting for.
   * For debugging.
   */
  @Unpreemptible
  @Override
  void dumpWaitDescription(GreenThread thread) {
    // Safe downcast from ThreadEventWaitData to ThreadIOWaitData.
    // Because this method may be called by other Processors without
    // locking (and thus execute concurrently with other methods), do NOT
    // use the queue's private downcaster object.  Instead, create one
    // from scratch.
    ThreadIOWaitData waitData;
    synchronized(downcaster) {
      thread.waitData.accept(downcaster);
      waitData = downcaster.waitData;
    }
    if (VM.VerifyAssertions) VM._assert(waitData == thread.waitData);

    VM.sysWrite("(R");
    dumpFds(waitData.readFds);
    VM.sysWrite(";W");
    dumpFds(waitData.writeFds);
    VM.sysWrite(";E");
    dumpFds(waitData.exceptFds);
    VM.sysWrite(')');
  }

  @Interruptible
  private void appendFds(StringBuffer buffer, int[] fds) {
    if (fds == null) {
      return;
    }
    for (int i = 0; i < fds.length; ++i) {
      buffer.append(fds[i] & FD_MASK);
      if ((fds[i] & FD_READY_BIT) != 0) {
        buffer.append('+');
      }
      if ((fds[i] & FD_INVALID_BIT) != 0) {
        buffer.append('X');
      }
      if (i != fds.length - 1) {
        buffer.append(',');
      }
    }
  }

  /**
   * Get string describing what given thread is waiting for.
   * This method must be interruptible!
   */
  @Interruptible
  @Override
  String getWaitDescription(GreenThread thread) {
    // Safe downcast from ThreadEventWaitData to ThreadIOWaitData.
    WaitDataDowncaster downcaster = new WaitDataDowncaster();
    thread.waitData.accept(downcaster);
    ThreadIOWaitData waitData = downcaster.waitData;
    if (VM.VerifyAssertions) VM._assert(waitData == thread.waitData);

    StringBuffer buffer = new StringBuffer();

    buffer.append("(R");
    appendFds(buffer, waitData.readFds);
    buffer.append(";W");
    appendFds(buffer, waitData.writeFds);
    buffer.append(";E");
    appendFds(buffer, waitData.exceptFds);
    buffer.append(')');

    return buffer.toString();
  }
}
