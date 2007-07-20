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
