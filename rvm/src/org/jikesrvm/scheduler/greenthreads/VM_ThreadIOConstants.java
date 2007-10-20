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

/**
 * Constants associated with I/O waits.
 *
 *
 * @see VM_ThreadIOWaitData
 * @see VM_ThreadIOQueue
 */
public interface VM_ThreadIOConstants {

  /**
   * Flag used to represent fd on which operation can proceed without
   * blocking.  Used by <code>sysNetSelect()</code> to mark file
   * descriptors which are ready.
   */
  int FD_READY = -99;

  /**
   * Flag used by <code>getNetSelect()</code> to mark a file descriptor
   * which has been found to be invalid.
   */
  int FD_INVALID = -100;

  /**
   * Position of bit used to mark fds that have become ready.
   */
  int FD_READY_SHIFT = 29;

  /**
   * If an IO wait is successful, this bit will be set in
   * all file descriptors that are ready (in the event data object).
   */
  int FD_READY_BIT = 1 << FD_READY_SHIFT;

  /**
   * Position of bit used to mark fds that were found to be invalid
   * by select().
   */
  int FD_INVALID_SHIFT = 30;

  /**
   * If an IO wait is terminiated because select() noticed that
   * the file descriptor was invalid, this bit will be set.
   */
  int FD_INVALID_BIT = 1 << FD_INVALID_SHIFT;

  /**
   * Mask to get value of a file descriptor.
   */
  int FD_MASK = ~(FD_READY_BIT | FD_INVALID_BIT);
}
