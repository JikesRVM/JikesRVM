/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_PragmaNoInline;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_PragmaLogicallyUninterruptible;

/**
 * Defines header words used by memory manager.not used for 
 *
 * @see VM_ObjectModel
 * 
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @author Perry Cheng
 */
import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;
public class MarkSweepHeader {

  /**
   * How many bytes are used by all GC header fields?
   */
  public static final int NUM_BYTES_HEADER = 0;

  /**
   * How many bits does this GC system require?
   */
  public static final int REQUESTED_BITS    = 2;
  public static final int GC_BITS_MASK      = 0x3;

  public static final int MARK_BIT_MASK     = 0x1;  // ...01
  public static final int SMALL_OBJECT_MASK = 0x2;  // ...10

  /**
   * Perform any required initialization of the GC portion of the header.
   * 
   * @param ref the object ref to the storage to be initialized
   * @param tib the TIB of the instance being created
   * @param size the number of bytes allocated by the GC system for this object.
   * @param isScalar are we initializing a scalar (true) or array (false) object?
   */
  public static void initializeHeader(VM_Address ref, Object[] tib, int size,
                                      boolean isScalar)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    int oldValue = VM_Interface.readAvailableBitsWord(VM_Magic.objectAsAddress(ref));
    int newValue = (oldValue & ~GC_BITS_MASK) | Plan.getInitialHeaderValue(size);
    VM_Interface.writeAvailableBitsWord(VM_Magic.objectAsAddress(ref),newValue);
  }

  /**
   * Perform any required initialization of the GC portion of the header.
   * 
   * @param ref the object ref to the storage to be initialized
   * @param tib the TIB of the instance being created
   * @param size the number of bytes allocated by the GC system for this object.
   * @param isScalar are we initializing a scalar (true) or array (false) object?
   */
  public static void initializeLOSHeader(VM_Address ref, Object[] tib, int size,
                                         boolean isScalar)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    int oldValue = VM_Interface.readAvailableBitsWord(VM_Magic.objectAsAddress(ref));
    int newValue = (oldValue & ~GC_BITS_MASK) | Plan.getInitialHeaderValue(size);
    VM_Interface.writeAvailableBitsWord(VM_Magic.objectAsAddress(ref), newValue);
  }

  /**
   * Perform any required initialization of the GC portion of the header.
   * Called for objects created at boot time.
   * 
   * @param ref the object ref to the storage to be initialized
   * @param tib the TIB of the instance being created
   * @param size the number of bytes allocated by the GC system for
   * this object.
   * @param isScalar are we initializing a scalar (true) or array
   * (false) object?
   */
  public static int getBootTimeAvailableBits(int ref, Object[] tib, int size,
                                             boolean isScalar, int status)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    return status; // nothing to do (no bytes of GC header)
  }

  /**
   * For low level debugging of GC subsystem. 
   * Dump the header word(s) of the given object reference.
   * @param ref the object reference whose header should be dumped 
   */
  public static void dumpHeader(VM_Address ref) throws VM_PragmaUninterruptible {
//     Log.write("(");
//     Log.write(VM_Magic.objectAsAddress(ref));
//     Log.write(" ");
//     Log.write((isSmall(ref))  ? "S" : "s");
//     Log.write((isArray(ref))  ? "A" : "a");
//     Log.write((isMarked(ref)) ? "M" : "m");
//     Log.writeln(")");
    // nothing to do (no bytes of GC header)
  }

  /**
   * Return true if the mark bit for an object has the given value.
   *
   * @param ref The object whose mark bit is to be tested
   * @param value The value against which the mark bit will be tested
   * @return True if the mark bit for the object has the given value.
   */
  static public boolean testMarkBit(VM_Address ref, int value)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    return (VM_Interface.readAvailableBitsWord(ref) & MARK_BIT_MASK) == value;
  }

  static public boolean isSmallObject(VM_Address ref)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    return (VM_Interface.readAvailableBitsWord(ref) & SMALL_OBJECT_MASK) == SMALL_OBJECT_MASK;
  }

  /**
   * Write a given value in the mark bit of an object
   *
   * @param ref The object whose mark bit is to be written
   * @param value The value to which the mark bit will be set
   */
  public static void writeMarkBit(VM_Address ref, int value)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    int oldValue = VM_Interface.readAvailableBitsWord(ref);
    int newValue = (oldValue & ~MARK_BIT_MASK) | value;
    VM_Interface.writeAvailableBitsWord(ref,newValue);
  }

  /**
   * Atomically write a given value in the mark bit of an object
   *
   * @param ref The object whose mark bit is to be written
   * @param value The value to which the mark bit will be set
   */
  public static void atomicWriteMarkBit(VM_Address ref, int value)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    int oldValue, newValue;
    do {
      oldValue = VM_Interface.prepareAvailableBits(ref);
      newValue = (oldValue & ~MARK_BIT_MASK) | value;
    } while (!VM_Interface.attemptAvailableBits(ref,oldValue,newValue));
  }

  /**
   * Atomically attempt to set the mark bit of an object.  Return true
   * if successful, false if the mark bit was already set.
   *
   * @param ref The object whose mark bit is to be written
   * @param value The value to which the mark bit will be set
   */
  public static boolean testAndMark(VM_Address ref, int value)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    int oldValue, markBit;
    do {
      oldValue = VM_Interface.prepareAvailableBits(ref);
      markBit = oldValue & MARK_BIT_MASK;
      if (markBit == value) return false;
    } while (!VM_Interface.attemptAvailableBits(ref,oldValue,oldValue ^ MARK_BIT_MASK));
    return true;
  }
}
