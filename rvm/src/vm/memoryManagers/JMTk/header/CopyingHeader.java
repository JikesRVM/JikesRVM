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
 * XXX Above line is incomplete.
 * @see VM_ObjectModel
 * 
 * @author David Bacon
 * @author Steve Fink
 * @author Dave Grove
 */
import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;
public class CopyingHeader {

  /**
   * How many bytes are used by all GC header fields?
   */
  public static final int NUM_BYTES_HEADER = 0;

  /**
   * How many bits does this GC system require?
   */
  public static final int REQUESTED_BITS = 2;
  public static final int GC_FORWARDED        = 0x2;  // ...10
  public static final int GC_BEING_FORWARDED  = 0x3;  // ...11

  /**
   * We don't require a side mark array for bootimage objects.
   */
  public static final boolean USE_SIDE_MARK_VECTOR = false;

  /**
   * Perform any required initialization of the GC portion of the header.
   * 
   * @param ref the object ref to the storage to be initialized
   * @param tib the TIB of the instance being created
   * @param size the number of bytes allocated by the GC system for this object.
   * @param isScalar are we initializing a scalar (true) or array (false) object?
   */
  public static void initializeHeader(VM_Address ref, Object[] tib, int size, boolean isScalar) throws VM_PragmaUninterruptible {
    // nothing to do (no bytes of GC header)
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
    // nothing to do (no bytes of GC header)
  }


  /*
   * Forwarding pointers
   * Only used if VM_Collector.MOVES_OBJECTS.
   */
  static final int GC_FORWARDING_MASK  = GC_FORWARDED | GC_BEING_FORWARDED;

  /**
   * Either return the forwarding pointer 
   * if the object is already forwarded (or being forwarded)
   * or write the bit pattern that indicates that the object is being forwarded
   */
  static int attemptToForward(VM_Address base) throws VM_PragmaInline, VM_PragmaUninterruptible {
    int oldValue;
    do {
      oldValue = VM_Interface.prepareAvailableBits(base);
      if ((oldValue & GC_FORWARDING_MASK) == GC_FORWARDED) return oldValue;
    } while (!VM_Interface.attemptAvailableBits(base,oldValue,oldValue | GC_BEING_FORWARDED));
    return oldValue;
  }

  /**
   * Non-atomic read of forwarding pointer word
   */
  static int getForwardingWord(VM_Address base) throws VM_PragmaUninterruptible, VM_PragmaInline {
    return VM_Interface.readAvailableBitsWord(base);
  }

  /**
   * Non-atomic read of forwarding pointer
   */
  static VM_Address getForwardingPtr(VM_Address base) throws VM_PragmaUninterruptible, VM_PragmaInline {
    return VM_Address.fromInt(VM_Interface.readAvailableBitsWord(base) & ~GC_FORWARDING_MASK);
  }

  /**
   * Has the object been forwarded?
   */
  public static boolean isForwarded(VM_Address base) throws VM_PragmaUninterruptible, VM_PragmaInline {
    return stateIsForwarded(getForwardingWord(base));
  }

  /**
   * Has the object been forwarded?
   */
  public static boolean isBeingForwarded(VM_Address base) throws VM_PragmaUninterruptible, VM_PragmaInline {
    return stateIsBeingForwarded(getForwardingWord(base));
  }

  /**
   * is the state of the forwarding word forwarded?
   */
  static boolean stateIsForwarded(int fw) throws VM_PragmaUninterruptible, VM_PragmaInline {
    return (fw & GC_FORWARDING_MASK) == GC_FORWARDED;
  }

  /**
   * is the state of the forwarding word being forwarded?
   */
  static boolean stateIsBeingForwarded(int fw) throws VM_PragmaUninterruptible, VM_PragmaInline {
    return (fw & GC_FORWARDING_MASK) == GC_BEING_FORWARDED;
  }

  /**
   * is the state of the forwarding word being forwarded?
   */
  static boolean stateIsForwardedOrBeingForwarded(int fw) throws VM_PragmaUninterruptible, VM_PragmaInline {
    return (fw & GC_FORWARDED) != 0;
  }

  /**
   * Non-atomic read of forwarding pointer word
   */
  static VM_Address getForwardingPointer(VM_Address base) throws VM_PragmaUninterruptible, VM_PragmaInline {
    int forwarded = getForwardingWord(base);
    return VM_Address.fromInt(forwarded & ~GC_FORWARDING_MASK);
  }

  /**
   * Non-atomic write of forwarding pointer word
   * (assumption, thread doing the set has done attempt to forward
   *  and owns the right to copy the object)
   */
  static void setForwardingPointer(VM_Address base, VM_Address ptr) throws VM_PragmaUninterruptible, VM_PragmaInline {
    VM_Interface.writeAvailableBitsWord(base,ptr.toInt() | GC_FORWARDED);
  }

  static void setBarrierBit(VM_Address ref) throws VM_PragmaUninterruptible, VM_PragmaInline {
    VM_Interface._assert(false);
  }

}
