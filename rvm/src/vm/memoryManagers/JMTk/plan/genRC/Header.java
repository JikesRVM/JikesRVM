/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

package org.mmtk.plan;

import org.mmtk.vm.VM_Interface;

import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Word;
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
  public final static VM_Word GC_BARRIER_BIT_MASK = VM_Word.fromIntSignExtend(-1);  // must be defined even though unused


  static final int      BARRIER_BIT = 1;
  static final VM_Word BARRIER_BIT_MASK  = VM_Word.one().lsh(BARRIER_BIT);  // ...10

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
    VM_Word old = VM_Interface.readAvailableBitsWord(ref);
    boolean rtn = old.and(BARRIER_BIT_MASK).isZero();
    if (rtn) {
      do {
        old = VM_Interface.prepareAvailableBits(ref);
        rtn = old.and(BARRIER_BIT_MASK).isZero();
      } while(!VM_Interface.attemptAvailableBits(ref, old, old.or(BARRIER_BIT_MASK))
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

  static final VM_Word LOGGING_MASK = VM_Word.one().lsh(2).sub(VM_Word.one()); //...00011
  static final int      LOG_BIT = 0;
  static final VM_Word       LOGGED = VM_Word.zero();
  static final VM_Word     UNLOGGED = VM_Word.one();
  static final VM_Word BEING_LOGGED = VM_Word.one().lsh(2).sub(VM_Word.one()); //...00011

  static boolean needsToBeLogged(VM_Address object) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    VM_Word value = VM_Interface.readAvailableBitsWord(object);
    return value.and(LOGGING_MASK).EQ(UNLOGGED);
  }

  static boolean attemptToLog(VM_Address object)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    VM_Word oldValue;
    do {
      oldValue = VM_Interface.prepareAvailableBits(object);
      if (oldValue.and(LOGGING_MASK).EQ(LOGGED)) return false;
    } while ((oldValue.and(LOGGING_MASK).EQ(BEING_LOGGED)) ||
             !VM_Interface.attemptAvailableBits(object, oldValue, 
						oldValue.or(BEING_LOGGED)));
    if (VM_Interface.VerifyAssertions) {
      VM_Word value = VM_Interface.readAvailableBitsWord(object);
      VM_Interface._assert(value.and(LOGGING_MASK).EQ(BEING_LOGGED));
    }
    return true;
  }

  static void makeLogged(VM_Address object)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    VM_Word value = VM_Interface.readAvailableBitsWord(object);
    if (VM_Interface.VerifyAssertions)
      VM_Interface._assert(value.and(LOGGING_MASK).NE(LOGGED));
    VM_Interface.writeAvailableBitsWord(object, value.and(LOGGING_MASK.not()));
  }

  static void makeUnlogged(VM_Address object)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    VM_Word value = VM_Interface.readAvailableBitsWord(object);
    if (VM_Interface.VerifyAssertions)
      VM_Interface._assert(value.and(LOGGING_MASK).EQ(LOGGED));
    VM_Interface.writeAvailableBitsWord(object, value.or(UNLOGGED));
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
  public static VM_Word getBootTimeAvailableBits(int ref, Object[] tib, 
                                                 int size, boolean isScalar,
                                                 VM_Word status)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    if (Plan.WITH_COALESCING_RC) 
      status = status.or(UNLOGGED);
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
