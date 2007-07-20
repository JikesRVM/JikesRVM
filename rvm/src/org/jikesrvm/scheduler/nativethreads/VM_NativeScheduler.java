package org.jikesrvm.scheduler.nativethreads;

import org.jikesrvm.classloader.VM_TypeReference;
import org.jikesrvm.scheduler.VM_Lock;
import org.jikesrvm.scheduler.VM_Scheduler;
import org.jikesrvm.scheduler.VM_Thread;
import org.vmmagic.pragma.Interruptible;

public class VM_NativeScheduler extends VM_Scheduler {

  @Override
  protected int availableProcessorsInternal() {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override
  protected void dumpVirtualMachineInternal() {
    // TODO Auto-generated method stub

  }

  @Override
  public boolean gcEnabledInternal() {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  protected void initInternal() {
    // TODO Auto-generated method stub
  }

  @Override
  protected void bootInternal() {
    // TODO Auto-generated method stub
  }

  @Override
  protected void lockOutputInternal() {
    // TODO Auto-generated method stub

  }

  @Override
  protected void scheduleFinalizerInternal() {
    // TODO Auto-generated method stub

  }

  @Override
  protected void startDebuggerThreadInternal() {
    // TODO Auto-generated method stub

  }

  @Override
  protected void suspendDebuggerThreadInternal() {
    // TODO Auto-generated method stub

  }

  @Override
  protected void suspendFinalizerThreadInternal() {
    // TODO Auto-generated method stub

  }

  @Override
  protected void sysExitInternal() {
    // TODO Auto-generated method stub

  }

  @Override
  protected void unlockOutputInternal() {
    // TODO Auto-generated method stub

  }

  @Override
  protected void yieldToOtherThreadWaitingOnLockInternal(VM_Lock l) {
    // TODO Auto-generated method stub
  }

  /**
   *  Number of VM_Processors
   */
  @Override
  protected int getNumberOfProcessorsInternal() {
    // TODO Auto-generated method stub
    return 0;
  }

  /**
   * Is it safe to start forcing garbage collects for stress testing?
   */
  @Override
  protected boolean safeToForceGCsInternal() {
    // TODO Auto-generated method stub
    return false;
  }
  
  /**
   * Schedule another thread
   */
  @Override
  protected void yieldInternal() {
    // TODO Auto-generated method stub    
  }
  
  /**
   * Set up the initial thread and processors as part of boot image writing
   * @return the boot thread
   */
  @Interruptible
  @Override
  protected VM_Thread setupBootThreadInternal() {
    // TODO Auto-generated method stub
    return null;
  }
  /**
   * Get the type of the processor (to avoid guarded inlining..)
   */
  @Override
  @Interruptible
  protected VM_TypeReference getProcessorTypeInternal() {
    return VM_TypeReference.findOrCreate(VM_NativeProcessor.class);
  }
}
