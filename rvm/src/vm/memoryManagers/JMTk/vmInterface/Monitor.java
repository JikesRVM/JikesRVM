/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */
package com.ibm.JikesRVM.memoryManagers.mmInterface;

import org.mmtk.plan.Plan;
import org.mmtk.vm.Constants;
import org.mmtk.vm.VM_Interface;

import com.ibm.JikesRVM.VM_Callbacks;
import com.ibm.JikesRVM.VM_PragmaInterruptible;
import com.ibm.JikesRVM.VM_Uninterruptible;

/**
 * This class allows JMTk to register call backs with VM_Callbacks.
 * 
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 *
 * @version $Revision$
 * @date $Date$
 */
public class Monitor 
  implements Constants, VM_Uninterruptible, VM_Callbacks.ExitMonitor {
  public final static String Id = "$Id$"; 

  /**
   * Register the exit monitor at boot time.
   */
  public static void boot() throws VM_PragmaInterruptible {
    VM_Callbacks.addExitMonitor(new Monitor());
  }

  /**
   * The VM is about to exit.  Notify the plan.
   *
   * @param value The exit value
   */
  public void notifyExit(int value) {
    VM_Interface.getPlan().notifyExit(value);
  }
}
