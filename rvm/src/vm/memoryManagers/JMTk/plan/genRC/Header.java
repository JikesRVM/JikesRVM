/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;

import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_PragmaNoInline;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_PragmaLogicallyUninterruptible;

/**
 * Chooses the appropriate collector-specific header model.
 *
 * @see VM_ObjectModel
 * 
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */
public class Header extends RCHybridHeader {
  public final static String Id = "$Id$"; 

  // Merges all the headers together.  In this case, we have only one.

  public final static int GC_BARRIER_BIT_MASK = -1;  // must be defined even though unused


  static final int      BARRIER_BIT = 1;
  static final int BARRIER_BIT_MASK  = 1<<BARRIER_BIT;  // ...10

  ////////////////////////////////////////////////////////////////////////////
  //
  // 
  //
  public static boolean isBeingForwarded(VM_Address base) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    if (Plan.isNurseryObject(base))
      return CopyingHeader.isBeingForwarded(base);
    else
      return false;
  }

  public static boolean isForwarded(VM_Address base) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    if (Plan.isNurseryObject(base))
      return CopyingHeader.isForwarded(base);
    else
      return false;
  }
  
  static boolean attemptBarrierBitSet(VM_Address ref)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    int old = VM_Interface.readAvailableBitsWord(ref);
    boolean rtn = ((old & BARRIER_BIT_MASK) == 0);
    if (rtn) {
      do {
        old = VM_Interface.prepareAvailableBits(ref);
        rtn = ((old & BARRIER_BIT_MASK) == 0);
      } while(!VM_Interface.attemptAvailableBits(ref, old, 
                                                 old | BARRIER_BIT_MASK)
              && rtn);
    }
    return rtn;
  }
  
  static void clearBarrierBit(VM_Address ref) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    VM_Interface.setAvailableBit(ref, BARRIER_BIT, false);
   }

  public static void setBarrierBit(VM_Address ref)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    VM_Interface._assert(false);
  }

  static final int LOGGING_MASK = 0x3;
  static final int      LOG_BIT = 0;
  static final int       LOGGED = 0x0;
  static final int     UNLOGGED = 0x1;
  static final int BEING_LOGGED = 0x3;
  static final int     LOG_MASK = ~0x3;

  static boolean needsToBeLogged(VM_Address object) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    int value = VM_Interface.readAvailableBitsWord(object);
    return (value & LOGGING_MASK) == UNLOGGED;
  }

  static boolean attemptToLog(VM_Address object)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    int oldValue;
    do {
      oldValue = VM_Interface.prepareAvailableBits(object);
      if ((oldValue & LOGGING_MASK) == LOGGED) return false;
    } while (((oldValue & LOGGING_MASK) == BEING_LOGGED) ||
             !VM_Interface.attemptAvailableBits(object, oldValue, 
                                                oldValue | BEING_LOGGED));
    if (VM_Interface.VerifyAssertions) {
      int value = VM_Interface.readAvailableBitsWord(object);
      VM_Interface._assert((value & LOGGING_MASK) == BEING_LOGGED);
    }
    return true;
  }

  static void makeLogged(VM_Address object)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    int value = VM_Interface.readAvailableBitsWord(object);
    if (VM_Interface.VerifyAssertions)
      VM_Interface._assert((value & LOGGING_MASK) != LOGGED);
    VM_Interface.writeAvailableBitsWord(object, value & LOG_MASK);
  }

  static void makeUnlogged(VM_Address object)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    int value = VM_Interface.readAvailableBitsWord(object);
    if (VM_Interface.VerifyAssertions)
      VM_Interface._assert((value & LOGGING_MASK) == LOGGED);
    VM_Interface.writeAvailableBitsWord(object, value | UNLOGGED);
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
    if (Plan.WITH_COALESCING_RC) 
      status |= UNLOGGED;
    return status;
  }

  /**
   * Perform any required initialization of the GC portion of the header.
   * 
   * @param ref the object ref to the storage to be initialized
   * @param tib the TIB of the instance being created
   * @param size the number of bytes allocated by the GC system for
   * this object.
   * @param isScalar are we initializing a scalar (true) or array
   * (false) object?
   * @param initialInc do we want to initialize this header with an
   * initial increment?
   */
  public static void initializeRCHeader(VM_Address ref, Object[] tib, int size,
                                        boolean isScalar, boolean initialInc)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    int initialValue = (initialInc) ? INCREMENT : 0;
    if (Plan.REF_COUNT_CYCLE_DETECTION && VM_Interface.isAcyclic(tib))
      initialValue |= GREEN;
    VM_Magic.setIntAtOffset(ref, RC_HEADER_OFFSET, initialValue);
  }
}
