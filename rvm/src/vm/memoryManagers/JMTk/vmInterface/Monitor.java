/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */

package com.ibm.JikesRVM.memoryManagers.vmInterface;

import com.ibm.JikesRVM.VM_Callbacks;
import com.ibm.JikesRVM.VM_PragmaInterruptible;
import com.ibm.JikesRVM.VM_Uninterruptible;
import com.ibm.JikesRVM.memoryManagers.JMTk.Plan;

public class Monitor implements Constants, VM_Uninterruptible, VM_Callbacks.ExitMonitor {
  public final static String Id = "$Id$"; 
  public static void boot() throws VM_PragmaInterruptible {
    VM_Callbacks.addExitMonitor(new Monitor());
  }
  public void notifyExit(int value) {
    Plan.notifyExit(value);
  }
}
