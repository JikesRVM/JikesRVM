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
package org.jikesrvm.scheduler;

import org.vmmagic.pragma.Uninterruptible;

/**
 * Visitor class for <code>VM_ThreadEventWaitData</code> objects.
 * Subclasses can recover the actual type of an object from a
 * <code>VM_ThreadEventWaitData</code> reference.
 */
@Uninterruptible
public abstract class VM_ThreadEventWaitDataVisitor {

  /**
   * Visit a VM_ThreadIOWaitData object.
   */
  public abstract void visitThreadIOWaitData(VM_ThreadIOWaitData waitData);

  /**
   * Visit a VM_ThreadProcessWaitData object.
   */
  public abstract void visitThreadProcessWaitData(VM_ThreadProcessWaitData waitData);

}
