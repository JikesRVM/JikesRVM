/*
 * (C) Copyright IBM Corp 2001,2002
 */
//$Id$
package com.ibm.JikesRVM.librarySupport;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.classloader.VM_Array;
import com.ibm.JikesRVM.VM_Callbacks;
import com.ibm.JikesRVM.VM_Lock;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_ObjectModel;
import com.ibm.JikesRVM.VM_Process;
import com.ibm.JikesRVM.VM_Runtime;
import com.ibm.JikesRVM.VM_Time;
import com.ibm.JikesRVM.FinalizerThread;

/**
 * This class provides a set of static method entrypoints used in the
 * implementation of standard library system operations.
 *
 * @author Stephen Fink
 */
public class SystemSupport {
  public static Process createProcess(String program, String[] args, String[] env, java.io.File dir) {
    String dirPath = (dir != null) ? dir.getPath() : null;
    return new VM_Process(program, args, env, dirPath);
  }
}
	
