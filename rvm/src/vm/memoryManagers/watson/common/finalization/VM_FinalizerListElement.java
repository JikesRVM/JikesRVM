/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * @author Dick Attanasio
 */
package MM;

import VM;
import VM_Constants;
import VM_Address;
import VM_Magic;
import VM_ObjectModel;
import VM_JavaHeader;
import VM_ClassLoader;
import VM_SystemClassLoader;
import VM_Atom;
import VM_Type;
import VM_Class;
import VM_Array;
import VM_Method;
import VM_PragmaInline;
import VM_PragmaNoInline;
import VM_PragmaUninterruptible;
import VM_PragmaLogicallyUninterruptible;
import VM_Scheduler;
import VM_Thread;
import VM_Memory;
import VM_Time;
import VM_Entrypoints;
import VM_Reflection;
import VM_Synchronization;
import VM_Synchronizer;
import VM_EventLogger;

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
