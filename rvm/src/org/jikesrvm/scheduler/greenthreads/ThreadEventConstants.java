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
 * Constants specifying the state of a {@link RVMThread} waiting
 * on a {@link ThreadEventWaitQueue}.
 *
 *
 * @see ThreadEventWaitQueue
 */
public interface ThreadEventConstants {
  /**
   * Used to specify that a wait should block indefinitely
   * (i.e., no timeout).
   */
  long WAIT_INFINITE = -1;
}
