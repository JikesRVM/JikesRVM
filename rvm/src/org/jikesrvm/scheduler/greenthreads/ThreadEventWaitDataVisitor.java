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

import org.vmmagic.pragma.Uninterruptible;

/**
 * Visitor class for <code>ThreadEventWaitData</code> objects.
 * Subclasses can recover the actual type of an object from a
 * <code>ThreadEventWaitData</code> reference.
 */
@Uninterruptible
abstract class ThreadEventWaitDataVisitor {

  /**
   * Visit a ThreadIOWaitData object.
   */
  abstract void visitThreadIOWaitData(ThreadIOWaitData waitData);

  /**
   * Visit a ThreadProcessWaitData object.
   */
  abstract void visitThreadProcessWaitData(ThreadProcessWaitData waitData);

}
