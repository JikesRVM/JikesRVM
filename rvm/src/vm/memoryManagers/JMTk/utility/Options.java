/*
 * (C) Copyright IBM Corp. 2002
 */
//$Id$

package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Uninterruptible;
import com.ibm.JikesRVM.VM_PragmaInterruptible;

/*
 * Options including heap sizes.
 *
 * @author Perry Cheng
 */
public class Options implements VM_Uninterruptible {

  static int initialHeapSize = 100 * (1 << 20);
  static int maxHeapSize     = 300 * (1 << 20);

  public static void process (String arg) throws VM_PragmaInterruptible {
    //VM.sysWriteln("processing arg = ", arg);
    if (arg.startsWith("initial=")) {
      String tmp = arg.substring(8);
      int size = Integer.parseInt(tmp);
      if (size <= 0) VM.sysFail("Unreasonable heap size " + tmp);
      initialHeapSize = size * (1 << 20);
      if (maxHeapSize < initialHeapSize) maxHeapSize = initialHeapSize;
    }
    else if (arg.startsWith("max=")) {
      String tmp = arg.substring(4);
      int size = Integer.parseInt(tmp);
      if (size <= 0) VM.sysFail("Unreasonable heap size " + tmp);
      maxHeapSize = size * (1 << 20);
    }
    else if (arg.startsWith("verbose=")) {
      String tmp = arg.substring(8);
      int level = Integer.parseInt(tmp);
      if (level <= 0) VM.sysFail("Unreasonable verbosity level " + tmp);
      Plan.verbose = level;
    }
    else 
      VM.sysWriteln("Ignoring unknown GC option: ", arg);
  }

}
