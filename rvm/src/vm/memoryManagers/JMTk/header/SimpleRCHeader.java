/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.BootImageInterface;
import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_ObjectModel;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_PragmaNoInline;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_PragmaLogicallyUninterruptible;
import com.ibm.JikesRVM.VM_Memory;

/**
 * Defines header words used by memory manager.not used for 
 *
 * @see VM_ObjectModel
 * 
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 */
public class SimpleRCHeader {

  /**
   * How many bytes are used by all GC header fields?
   */
  public static final int NUM_BYTES_HEADER = 4;
  private static final int RC_HEADER_OFFSET = VM_ObjectModel.JAVA_HEADER_END - NUM_BYTES_HEADER;

  /**
   * How many bits does this GC system require?
   */
  public static final int REQUESTED_BITS    = 2;
  public static final int SMALL_OBJECT_MASK = 0x1;

  /**
   * Perform any required initialization of the GC portion of the header.
   * 
   * @param ref the object ref to the storage to be initialized
   * @param tib the TIB of the instance being created
   * @param size the number of bytes allocated by the GC system for this object.
   * @param isScalar are we initializing a scalar (true) or array (false) object?
   */
  public static void initializeHeader(Object ref, Object[] tib, int size,
				      boolean isScalar)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    // all objects are birthed with an RC of 1
    VM_Magic.setIntAtOffset(ref, RC_HEADER_OFFSET, 1);
    int oldValue = VM_ObjectModel.readAvailableBitsWord(ref);
    int newValue = (oldValue & ~SMALL_OBJECT_MASK) | Plan.getInitialHeaderValue(size);
    VM_ObjectModel.writeAvailableBitsWord(ref, newValue);
  }

  static public boolean isSmallObject(Object ref)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    return (VM_ObjectModel.readAvailableBitsWord(ref) & SMALL_OBJECT_MASK) == SMALL_OBJECT_MASK;
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
				      Object[] tib, int size, boolean isScalar)
    throws VM_PragmaUninterruptible {
    // nothing to do for boot image objects
  }

  /**
   * For low level debugging of GC subsystem. 
   * Dump the header word(s) of the given object reference.
   * @param ref the object reference whose header should be dumped 
   */
  public static void dumpHeader(Object ref) throws VM_PragmaUninterruptible {
//     VM.sysWrite("(");
//     VM.sysWrite(VM_Magic.objectAsAddress(ref));
//     VM.sysWrite(" ");
//     VM.sysWrite((isSmall(ref))  ? "S" : "s");
//     VM.sysWrite((isArray(ref))  ? "A" : "a");
//     VM.sysWrite((isMarked(ref)) ? "M" : "m");
//     VM.sysWrite(")\n");
    // nothing to do (no bytes of GC header)
  }

  public static boolean isLiveRC(Object obj) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    return VM_Magic.getIntAtOffset(obj, RC_HEADER_OFFSET) != 0;
  }

  public static void incRC(Object object)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    changeRC(object, 1);
  }

  public static boolean decRC(Object object)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    return (changeRC(object, -1) == 0);
  }

  private static int changeRC(Object object, int delta)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    int oldValue, newValue;
    do {
      oldValue = VM_Magic.prepare(object, RC_HEADER_OFFSET);
      newValue = oldValue + delta;
    } while (!VM_Magic.attempt(object, RC_HEADER_OFFSET, oldValue, newValue));
    return newValue;
  }
}
