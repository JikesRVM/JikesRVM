/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.VM_Uninterruptible;
import com.ibm.JikesRVM.VM_PragmaInterruptible;

/**
 * A VM_ThreadSwitchProducer object is executed at thread switch time.
 * When it needs to perform any complicated operations that are not 
 * allowed at thread switch time, it calls a VM_ThreadSwitchConsumer 
 * to perform the operation for it.
 *
 * CONSTRAINTS:
 * Classes that are derived from VM_ThreadSwitchProducer 
 * must inherit directly from VM_Uninterruptible to ensure that they
 * are not interrupted by a thread switch.  
 * Since thread switching is disabled, listeners are 
 * expected to complete execution quickly, and therefore, 
 * must do a minimal amount of work.
 *
 * @author Peter F. Sweeney
 * @date  2/6/2003
 */
abstract class VM_ThreadSwitchProducer implements VM_Uninterruptible
{
  /*
   * My consumer.
   */
  protected VM_ThreadSwitchConsumer consumer;

  /**
   * Consumer associated with this producer.
   */
  final public void setConsumer(VM_ThreadSwitchConsumer consumer) {
    this.consumer = consumer;
  }

  /**
   * Wake up the consumer thread (if any) associated with the producer
   */
  final public void activateConsumer() {
    if (consumer != null) {
      consumer.activate();
    }
  }

  /*
   * The field active (manipulated by consumer) determines when we produce
   */
  protected boolean active = false;

  /*
   * Start producing.
   */
  public  void   activate() { 
    if(VM_HardwarePerformanceMonitors.verbose>=1)VM.sysWriteln("VM_ThreadSwitchProducer.activate()");
    active = true;  
  }
  /*
   * Stop producing.
   */
  public  void passivate() { 
    if(VM_HardwarePerformanceMonitors.verbose>=1)VM.sysWriteln("VM_ThreadSwitchProducer.passivate()");
    active = false; 
  }
}

