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

import org.jikesrvm.ArchitectureSpecific.ArchConstants;
import org.vmmagic.pragma.NonMoving;

/**
 */
@NonMoving
public final class BootThread extends SystemThread {

  public BootThread() {
    super(new byte[ArchConstants.STACK_SIZE_BOOT], "Jikes_RBoot_Thread");
  }

  @Override
  public void run() {
    // Not reached.
  }
}
