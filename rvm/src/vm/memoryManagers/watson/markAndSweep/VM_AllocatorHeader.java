/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$


package com.ibm.JikesRVM.memoryManagers.watson;

import com.ibm.JikesRVM.BootImageInterface;
import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_ObjectModel;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_PragmaNoInline;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_PragmaLogicallyUninterruptible;
import com.ibm.JikesRVM.VM_Scheduler;
import com.ibm.JikesRVM.VM_Memory;
import com.ibm.JikesRVM.VM_Time;
import com.ibm.JikesRVM.VM_Entrypoints;
import com.ibm.JikesRVM.VM_Reflection;
import com.ibm.JikesRVM.VM_Synchronization;
import com.ibm.JikesRVM.VM_EventLogger;

/**
 * Defines header words used by memory manager.not used for 
 *
 * @see VM_ObjectModel
 * 
 * @author David Bacon
 * @author Steve Fink
 * @author Dave Grove
 */
public final class VM_AllocatorHeader extends VM_CommonAllocatorHeader {

  /**
   * Perform any required initialization of the GC portion of the header.
   * 
   * @param ref the object ref to the storage to be initialized
   * @param tib the TIB of the instance being created
   * @param size the number of bytes allocated by the GC system for this object.
   * @param isScalar are we initializing a scalar (true) or array (false) object?
   */
  public static void initializeHeader(Object ref, Object[] tib, int size, boolean isScalar) throws VM_PragmaUninterruptible {
    // nothing to do (no bytes of GC header)
  }

  /**
   * Perform any required initialization of the GC portion of the header.
   * 
   * @param bootImage the bootimage being written
   * @param ref the object ref to the storage to be initialized
   * @param tib the TIB of the instance being created
   * @param size the number of bytes allocated by the GC system for this object.
   * @param isScalar are we initializing a scalar (true) or array (false) object?
   */
  public static void initializeHeader(BootImageInterface bootImage, int ref, 
				      Object[] tib, int size, boolean isScalar) throws VM_PragmaUninterruptible {
    // nothing to do (no bytes of GC header)
  }

  /**
   * For low level debugging of GC subsystem. 
   * Dump the header word(s) of the given object reference.
   * @param ref the object reference whose header should be dumped 
   */
  public static void dumpHeader(Object ref) throws VM_PragmaUninterruptible {
    // nothing to do (no bytes of GC header)
  }

}
