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
package org.jikesrvm.mm.mminterface;

import org.jikesrvm.VM;
import org.jikesrvm.scheduler.Monitor;
import org.jikesrvm.scheduler.RVMThread;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.pragma.Interruptible;

/**
 * This class implements barrier synchronization.
 * The mechanism handles proper resetting by usnig 3 underlying counters
 * and supports unconditional blocking until the number of participants
 * can be determined.
 */
@Uninterruptible
final class Barrier {

  public static final int VERBOSE = 0;
  private Monitor lock;
  private int target;
  private int[] counters=new int[2]; // are two counters enough?
  private int[] modes=new int[2];
  private int countIdx;
  public Barrier() {}
  @Interruptible
  public void boot(int target) {
    lock=new Monitor();
    this.target=target;
    countIdx=0;
  }
  public boolean arrive(int mode) {
    if (false) {
      VM.sysWriteln("thread ",RVMThread.getCurrentThreadSlot(),
                    " entered ",RVMThread.getCurrentThread().barriersEntered++,
                    " barriers");
    }
    lock.lockNoHandshake();
    int myCountIdx=countIdx;
    boolean result;
    if (VM.VerifyAssertions) {
      if (counters[myCountIdx]==0) {
        modes[myCountIdx]=mode;
      } else {
        int oldMode=modes[myCountIdx];
        if (oldMode!=mode) {
          VM.sysWriteln("Thread ",RVMThread.getCurrentThreadSlot()," encountered "+
                        "incorrect mode entering barrier.");
          VM.sysWriteln("Thread ",RVMThread.getCurrentThreadSlot(),"'s mode: ",mode);
          VM.sysWriteln("Thread ",RVMThread.getCurrentThreadSlot()," saw others in mode: ",oldMode);
          VM._assert(modes[myCountIdx]==mode);
          VM._assert(oldMode==mode);
        }
      }
    }
    counters[myCountIdx]++;
    if (counters[myCountIdx]==target) {
      counters[myCountIdx]=0;
      countIdx^=1;
      lock.broadcast();
      if (false) {
        VM.sysWriteln("waking everyone");
      }
      result=true;
    } else {
      while (counters[myCountIdx]!=0) {
        lock.waitNoHandshake();
      }
      result=false;
    }
    lock.unlock();
    if (false) {
      VM.sysWriteln("thread ",RVMThread.getCurrentThreadSlot(),
                    " exited ",RVMThread.getCurrentThread().barriersExited++,
                    " barriers");
    }
    return result;
  }
}
