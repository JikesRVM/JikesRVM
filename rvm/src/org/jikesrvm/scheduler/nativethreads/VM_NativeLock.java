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
package org.jikesrvm.scheduler.nativethreads;

import org.jikesrvm.scheduler.VM_Lock;
import org.jikesrvm.scheduler.VM_Thread;

public class VM_NativeLock extends VM_Lock {

  @Override
  public boolean lockHeavy(Object o) {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public void unlockHeavy(Object o) {
    // TODO Auto-generated method stub
  }

  @Override
  public boolean isBlocked(VM_Thread t) {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public boolean isWaiting(VM_Thread t) {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public void dumpWaitingThreads() {
    // TODO Auto-generated method stub
  }

  @Override
  public void dumpBlockedThreads() {
    // TODO Auto-generated method stub
  }
}
