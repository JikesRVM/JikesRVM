/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$


package com.ibm.JikesRVM.memoryManagers;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Constants;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_ObjectModel;
import com.ibm.JikesRVM.VM_JavaHeader;
import com.ibm.JikesRVM.VM_ClassLoader;
import com.ibm.JikesRVM.VM_SystemClassLoader;
import com.ibm.JikesRVM.VM_Atom;
import com.ibm.JikesRVM.VM_Type;
import com.ibm.JikesRVM.VM_Class;
import com.ibm.JikesRVM.VM_Array;
import com.ibm.JikesRVM.VM_Method;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_PragmaNoInline;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_PragmaLogicallyUninterruptible;
import com.ibm.JikesRVM.VM_Scheduler;
import com.ibm.JikesRVM.VM_Thread;
import com.ibm.JikesRVM.VM_Memory;
import com.ibm.JikesRVM.VM_Time;
import com.ibm.JikesRVM.VM_Entrypoints;
import com.ibm.JikesRVM.VM_Reflection;
import com.ibm.JikesRVM.VM_Synchronization;
import com.ibm.JikesRVM.VM_Synchronizer;
import com.ibm.JikesRVM.VM_EventLogger;

/**
 * @author Dick Attanasio
 */
class VM_FinalizerListElement {

  VM_Address value;
  Object pointer;
  VM_FinalizerListElement next;

  VM_FinalizerListElement(Object input) throws VM_PragmaUninterruptible {
    value = VM_Magic.objectAsAddress(input);
  }

  // Do not provide interface for Object 

  void move(VM_Address newAddr) throws VM_PragmaUninterruptible {
    value = newAddr;
  }

  void finalize (VM_Address newAddr) throws VM_PragmaUninterruptible {
    value = VM_Address.zero();
    pointer = VM_Magic.addressAsObject(newAddr);
  }
}
