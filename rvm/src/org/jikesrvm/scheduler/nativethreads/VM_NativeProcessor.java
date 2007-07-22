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

import org.jikesrvm.scheduler.VM_Processor;

public class VM_NativeProcessor extends VM_Processor {

  public VM_NativeProcessor(int id) {
    super(id);
  }

  @Override
  public void disableThreadSwitching(String s) {
    // TODO Auto-generated method stub

  }

  @Override
  public void dispatch(boolean timerTick) {
    // TODO Auto-generated method stub

  }

  @Override
  public void enableThreadSwitching() {
    // TODO Auto-generated method stub

  }

  @Override
  public boolean threadSwitchingEnabled() {
    // TODO Auto-generated method stub
    return false;
  }

  /**
   * Fail if thread switching is disabled on this processor
   */
  @Override
  public void failIfThreadSwitchingDisabled() {
    // TODO Auto-generated method stub
  }
}
