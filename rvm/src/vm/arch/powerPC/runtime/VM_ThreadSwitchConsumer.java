/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.VM_Thread;
import com.ibm.JikesRVM.VM_ThreadQueue;
import com.ibm.JikesRVM.VM_ThreadSwitchProducer;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
/**
 * An VM_ThreadSwitchConsumer performs complicated operations at thread switch time
 * for the thread that is in the middle of a thread switch.
 * When a thread is in the middle of a thread switch, Jikes RVM is in a delicate state
 * where memory can't be allocated, interruptible code can't be called, an interface
 * call can't be made, and ...
 * 
 * @author Peter F. Sweeney
 * @date  2/6/2003
 */
abstract class VM_ThreadSwitchConsumer extends VM_Thread 
{

  /*
   * debugging
   */
  static private int debug = 1;
  /**
   * The producer associated with this consumer.
   * May be null if the consumer has no associated producer.
   */
  protected VM_ThreadSwitchProducer producer;

  /**
   * A queue to hold the consumer thread when it isn't executing
   */
  private VM_ThreadQueue tq = new VM_ThreadQueue(0);

  /**
   * Called when thread is scheduled.
   */
  public void run() {
    initialize();
    while (true) {
      passivate(); // wait until externally scheduled to run
      try {
	thresholdReached();       // we've been scheduled; do our job!
      } catch (Exception e) {
	e.printStackTrace();
	VM.sysFail("Exception in VM_ThreadSwitchConsumer "+this);
      }
    } 
  }

  /**
   * Method that is called when the sampling threshold is reached
   */
  abstract void thresholdReached();

  /**
   * Consumer specific setup.  
   * A good place to install and activate any producers.
   */
  protected void initialize() {}

  /**
   * The field active (manipulated by producer) determines when we consume
   */
  protected boolean active = false;
  /*
   * Let the outside world know if I am active?
   */
  public final boolean isActive() throws VM_PragmaUninterruptible
  { 
    return active; 
  }
  /**
   * Start consuming.
   * Called (by producer) to activate the consumer thread (i.e. schedule it for execution).
   */
  public void activate() throws VM_PragmaUninterruptible 
  {
    if (active == true) {
      VM.sysWriteln("***VM_ThreadSwitchConsumer.activate() active == true!  PID ",
		    ((VM_TraceWriter)this).getPid(),"***");
      VM.shutdown(-1);
    }
    if(debug>=2)VM.sysWriteln("VM_ThreadSwitchConsumer.activate()");
    active = true;
    VM_Thread org = tq.dequeue();
    if (VM.VerifyAssertions) VM._assert(org != null);
    org.schedule();
  }
  /*
   * Stop consuming.
   * Called (by consumer in run()) to stop consuming.
   * Can access the thread queue without locking because 
   * only producer and consumer operate on the thread queue and the
   * producer uses its own protocol to ensure that exactly 1 
   * thread will attempt to activate the consumer.
   */
  private void passivate() throws VM_PragmaUninterruptible 
  {
    if(debug>=2)VM.sysWriteln("VM_ThreadSwitchConsumer.passivate()");
    active = false;
    VM_Thread.yield(tq);
  }


  public String toString() throws VM_PragmaUninterruptible {
    return "VM_ThreadSwitchConsumer";
  }
}
