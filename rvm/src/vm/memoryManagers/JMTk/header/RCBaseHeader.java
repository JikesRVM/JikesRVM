/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;
import com.ibm.JikesRVM.memoryManagers.vmInterface.Constants;

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
 */
public abstract class RCBaseHeader implements Constants {

  /**
   * How many bytes are used by all GC header fields?
   */
  public static final int NUM_BYTES_HEADER = BYTES_IN_WORD;
  protected static final int RC_HEADER_OFFSET = VM_Interface.GC_HEADER_OFFSET();

  /**
   * How many bits does this GC system require?
   */
  public static final int REQUESTED_BITS    = 2;
  public static final int GC_BITS_MASK      = 0x3;

  public static final int SMALL_OBJECT_MASK = 0x1;  // ...01
  private static final int      BARRIER_BIT = 1;
  public static final int BARRIER_BIT_MASK  = 1<<BARRIER_BIT;  // ...10

  public static final int DEC_KILL = 0;    // dec to zero RC --> reclaim obj
  public static final int DEC_PURPLE = 1;  // dec to non-zero RC, already buf'd
  public static final int DEC_BUFFER = -1; // dec to non-zero RC, need to bufr

  public static boolean isSmallObject(VM_Address ref)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    return (VM_Interface.readAvailableBitsWord(ref) & SMALL_OBJECT_MASK) == SMALL_OBJECT_MASK;
  }

  public static boolean attemptBarrierBitSet(VM_Address ref)
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

  public static void clearBarrierBit(VM_Address ref) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    VM_Interface.setAvailableBit(ref, BARRIER_BIT, false);
  }

  /**
   * Perform any required initialization of the GC portion of the header.
   * Called for objects allocated at boot time.
   * 
   * @param ref the object ref to the storage to be initialized
   * @param tib the TIB of the instance being created
   * @param size the number of bytes allocated by the GC system for this object.
   * @param isScalar are we initializing a scalar (true) or array (false) object?
   */
  public static void initializeHeaderBootTime(int ref, Object[] tib, 
	                                      int size, boolean isScalar)
    throws VM_PragmaUninterruptible {
    // nothing to do for boot image objects
  }

  /**
   * For low level debugging of GC subsystem. 
   * Dump the header word(s) of the given object reference.
   * @param ref the object reference whose header should be dumped 
   */
  public static void dumpHeader(VM_Address ref) throws VM_PragmaUninterruptible {
    // nothing to do (no bytes of GC header)
  }

  /**
   * Return true if given object is live
   *
   * @param object The object whose liveness is to be tested
   * @return True if the object is alive
   */
  public static boolean isLiveRC(VM_Address object) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    if (Plan.REF_COUNT_SANITY_TRACING) {
      return (VM_Magic.getIntAtOffset(object, RC_HEADER_OFFSET) & INCREMENT_MASK) >= INCREMENT;
    } else
      return VM_Magic.getIntAtOffset(object, RC_HEADER_OFFSET) >= INCREMENT;
  }

  /**
   * Increment the reference count of an object, clearing the "purple"
   * status of the object (if it were already purple).  An object is
   * marked purple if it is a potential root of a garbage cycle.  If
   * an object's RC is incremented, it must be live and therefore
   * should not be considered as a potential garbage cycle.  This must
   * be an atomic operation if parallel GC is supported.
   *
   * @param object The object whose RC is to be incremented.
   */
  public static void incRC(VM_Address object, boolean makeBlack)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    int oldValue, newValue;
    if (Plan.SUPPORTS_PARALLEL_GC) {
      do {
	oldValue = VM_Magic.prepareInt(object, RC_HEADER_OFFSET);
	newValue = oldValue + INCREMENT;
	if (Plan.REF_COUNT_CYCLE_DETECTION && makeBlack)
	  newValue = (newValue & ~PURPLE);
      } while (!VM_Magic.attemptInt(object, RC_HEADER_OFFSET, oldValue, newValue));
    } else {
      oldValue = VM_Magic.getIntAtOffset(object, RC_HEADER_OFFSET);
      newValue = oldValue + INCREMENT;
      if (Plan.REF_COUNT_CYCLE_DETECTION && makeBlack)
	newValue = (newValue & ~PURPLE);
      VM_Magic.setIntAtOffset(object, RC_HEADER_OFFSET, newValue);
    }
  }

  /**
   * Decrement the reference count of an object.  Return either
   * <code>DEC_KILL</code> if the count went to zero,
   * <code>DEC_BUFFER</code> if the count did not go to zero and the
   * object was not already in the purple buffer, and
   * <code>DEC_PURPLE</code> if the count did not go to zero and the
   * object was already in the purple buffer.  This must be an atomic
   * operation if parallel GC is supported.
   *
   * @param object The object whose RC is to be decremented.
   * @return <code>DEC_KILL</code> if the count went to zero,
   * <code>DEC_BUFFER</code> if the count did not go to zero and the
   * object was not already in the purple buffer, and
   * <code>DEC_PURPLE</code> if the count did not go to zero and the
   * object was already in the purple buffer.
   */
  public static int decRC(VM_Address object, boolean makePurple)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    int oldValue, newValue;
    int rtn;
    if (Plan.SUPPORTS_PARALLEL_GC)
      do {
	oldValue = VM_Magic.prepareInt(object, RC_HEADER_OFFSET);
	newValue = oldValue - INCREMENT;
	if (newValue < INCREMENT)
	  rtn = DEC_KILL;
	else if (Plan.REF_COUNT_CYCLE_DETECTION && makePurple &&
		 ((newValue & COLOR_MASK) < PURPLE)) {
	  rtn = ((newValue & BUFFERED_MASK) == 0) ? DEC_BUFFER : DEC_PURPLE;
	  newValue = (newValue & ~COLOR_MASK) | PURPLE | CYCLIC_MATURE | BUFFERED_MASK;
	} else
	  rtn = DEC_PURPLE;
      } while (!VM_Magic.attemptInt(object, RC_HEADER_OFFSET, oldValue, newValue));
    else {
      oldValue = VM_Magic.getIntAtOffset(object, RC_HEADER_OFFSET);
      newValue = oldValue - INCREMENT;
      if (newValue < INCREMENT)
	rtn = DEC_KILL;
      else if (Plan.REF_COUNT_CYCLE_DETECTION && makePurple &&
	       ((newValue & COLOR_MASK) < PURPLE)) {
	rtn = ((newValue & BUFFERED_MASK) == 0) ? DEC_BUFFER : DEC_PURPLE;
	newValue = (newValue & ~COLOR_MASK) | PURPLE | CYCLIC_MATURE | BUFFERED_MASK;
      } else
	rtn = DEC_PURPLE;
      VM_Magic.setIntAtOffset(object, RC_HEADER_OFFSET, newValue);
    }
    return rtn;
  }
  
  public static int getRC(VM_Address object)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    if (Plan.REF_COUNT_SANITY_TRACING) {
      int rc = VM_Magic.getIntAtOffset(object, RC_HEADER_OFFSET) & INCREMENT_MASK;
      return rc>>INCREMENT_SHIFT;
    } else {
      return VM_Magic.getIntAtOffset(object, RC_HEADER_OFFSET)>>INCREMENT_SHIFT;
    }
  }

  public static boolean incTraceRC(VM_Address object)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    return (changeRC(object, SANITY_INCREMENT) >> SANITY_SHIFT) == 1;
  }
  public static int getTracingRC(VM_Address object)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    if (VM_Interface.VerifyAssertions) VM_Interface._assert(Plan.REF_COUNT_SANITY_TRACING);
    return VM_Magic.getIntAtOffset(object, RC_HEADER_OFFSET)>>SANITY_SHIFT;
  }
  public static void clearTracingRC(VM_Address object)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    if (VM_Interface.VerifyAssertions) VM_Interface._assert(Plan.REF_COUNT_SANITY_TRACING);
    int old = VM_Magic.getIntAtOffset(object, RC_HEADER_OFFSET);
    VM_Magic.setIntAtOffset(object, RC_HEADER_OFFSET, old & ~SANITY_MASK);
  }

  private static int changeRC(VM_Address object, int delta)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    int oldValue, newValue;
    do {
      oldValue = VM_Magic.prepareInt(object, RC_HEADER_OFFSET);
      newValue = oldValue + delta;
    } while (!VM_Magic.attemptInt(object, RC_HEADER_OFFSET, oldValue, newValue));
    return newValue;
  }

  public static void print(VM_Address object)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    Log.write(VM_Magic.getIntAtOffset(object, RC_HEADER_OFFSET)>>CYCLE_DETECTION_BITS); 
    Log.write(' ');
    switch (getRCColor(object)) {
    case BLACK: Log.write('b'); break;
    case WHITE: Log.write('w'); break;
    case PURPLE: Log.write('p'); break;
    case GREEN: Log.write('x'); break;
    case GREY: Log.write('g'); break;
    }
    if (isBuffered(object))
      Log.write('b');
    else
      Log.write('u');
  }
  public static boolean isBuffered(VM_Address object)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    return getRCbits(object, BUFFERED_MASK) != 0;
  }
  public static void setBufferedBit(VM_Address object) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    setOrClearRCBit(object, BUFFERED_MASK, true);
  }
  public static void clearBufferedBit(VM_Address object) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    setOrClearRCBit(object, BUFFERED_MASK, false);
  }
  private static int getRCbits(VM_Address object, int mask)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    return VM_Magic.getIntAtOffset(object, RC_HEADER_OFFSET) & mask;
  }
  private static void setOrClearRCBit(VM_Address object, int mask, boolean set)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    int oldValue, newValue;
    do {
      oldValue = VM_Magic.prepareInt(object, RC_HEADER_OFFSET);
      newValue = (set) ? oldValue | mask : oldValue & ~mask;
    } while (!VM_Magic.attemptInt(object, RC_HEADER_OFFSET, oldValue, newValue));
  }

  public static boolean isBlack(VM_Address object) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    return getRCColor(object) == BLACK;
  }
  public static boolean isWhite(VM_Address object) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    return getRCColor(object) == WHITE;
  }
  public static boolean isGreen(VM_Address object) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    return getRCColor(object) >= GREEN;
  }
  public static boolean isPurple(VM_Address object) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    return getRCColor(object) == PURPLE;
  }
  public static boolean isGreenOrPurple(VM_Address object) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    return getRCColor(object) >= PURPLE;
  }
  public static boolean isGrey(VM_Address object) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    return getRCColor(object) == GREY;
  }
  public static boolean isMature(VM_Address object)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    return getRCbits(object, CYCLIC_MATURE) != 0;
  }
  public static boolean isGreyOrGreen(VM_Address object) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    int color = getRCColor(object);
    return (color == GREY) || (color == GREEN);
  }
  private static int getRCColor(VM_Address object) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    return COLOR_MASK & VM_Magic.getIntAtOffset(object, RC_HEADER_OFFSET);
  }
  public static void makeBlack(VM_Address object) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    if (VM_Interface.VerifyAssertions) VM_Interface._assert(getRCColor(object) != GREEN);
    changeRCColor(object, BLACK);
  }
  public static void makeWhite(VM_Address object) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    if (VM_Interface.VerifyAssertions) VM_Interface._assert(getRCColor(object) != GREEN);
    changeRCColor(object, WHITE);
  }
  public static boolean makePurple(VM_Address object)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    if (isGreenOrPurple(object))
      return false;  // inherently acyclic or already purple, so do nothing

    int oldValue, newValue;
    boolean rtn;
    do {
      oldValue = VM_Magic.prepareInt(object, RC_HEADER_OFFSET);
      newValue = (oldValue & ~COLOR_MASK) | PURPLE | CYCLIC_MATURE | BUFFERED_MASK;
    } while (!VM_Magic.attemptInt(object, RC_HEADER_OFFSET, oldValue, newValue));

    return ((oldValue & BUFFERED_MASK) == 0);
  }
  public static void makeGrey(VM_Address object) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    if (VM_Interface.VerifyAssertions) VM_Interface._assert(getRCColor(object) != GREEN);
    changeRCColor(object, GREY);
  }
  private static void changeRCColor(VM_Address object, int color)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    int oldValue, newValue;
    if (VM_Interface.VerifyAssertions) VM_Interface._assert(color != GREEN);
    do {
      oldValue = VM_Magic.prepareInt(object, RC_HEADER_OFFSET);
      newValue = (oldValue & ~COLOR_MASK) | color;
    } while (!VM_Magic.attemptInt(object, RC_HEADER_OFFSET, oldValue, newValue));
  }

  // See Bacon & Rajan ECOOP 2001 for notion of colors (purple, grey,
  // black, green).  See also Jones & Lins for a description of "Lins'
  // algorithm", on which Bacon & Rajan's is based.

  // The following are arranged to try to make the most common tests
  // fastest ("bufferd?", "green?" and "(green | purple)?") 
  private static final int     BUFFERED_MASK = 0x1;  //  .. 00001
  protected static final int      COLOR_MASK = 0xe;  //  .. 01110 
  private static final int             BLACK = 0x0;  //  .. x000x
  private static final int              GREY = 0x2;  //  .. x001x
  private static final int             WHITE = 0x4;  //  .. x010x
  // green & purple *MUST* remain the highest colors in order to
  // preserve the (green | purple) test's precondition.
  private static final int            PURPLE = 0x6;  //  .. x011x
  protected static final int           GREEN = 0x8;  //  .. x100x
  private static final int     CYCLIC_MATURE = 0x10; //  .. 1xxxx
  private static final int BITS_USED = 5;

  private static final int CYCLE_DETECTION_BITS = (Plan.REF_COUNT_CYCLE_DETECTION) ? BITS_USED : 0;
  protected static final int INCREMENT_SHIFT = CYCLE_DETECTION_BITS;
  protected static final int INCREMENT = 1<<INCREMENT_SHIFT;
  protected static final int AVAILABLE_BITS = WORD_BITS - CYCLE_DETECTION_BITS;
  protected static final int INCREMENT_BITS = (Plan.REF_COUNT_SANITY_TRACING) ? AVAILABLE_BITS>>1 : AVAILABLE_BITS;
  protected static final int INCREMENT_MASK = ((1<<INCREMENT_BITS)-1)<<INCREMENT_SHIFT;
  protected static final int SANITY_SHIFT = INCREMENT_SHIFT + INCREMENT_BITS;
  protected static final int SANITY_INCREMENT = 1<<SANITY_SHIFT;
  protected static final int SANITY_MASK = ((1<<INCREMENT_BITS)-1)<<SANITY_SHIFT;
}
