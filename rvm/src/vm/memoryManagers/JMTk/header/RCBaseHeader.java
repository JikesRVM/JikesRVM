/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

package org.mmtk.plan;

import org.mmtk.policy.RefCountLocal;
import org.mmtk.policy.RefCountSpace;
import org.mmtk.utility.Log;
import org.mmtk.vm.VM_Interface;
import org.mmtk.vm.Constants;

import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Word;
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
  public static final int NUM_BYTES_HEADER = (RefCountSpace.RC_SANITY_CHECK) ? 2*BYTES_IN_ADDRESS : BYTES_IN_ADDRESS;
  protected static final int RC_HEADER_OFFSET = VM_Interface.GC_HEADER_OFFSET();
  protected static final int RC_SANITY_HEADER_OFFSET = VM_Interface.GC_HEADER_OFFSET() + BYTES_IN_ADDRESS;

  /**
   * How many bits does this GC system require?
   */
  public static final int REQUESTED_BITS    = 2;
  static final VM_Word GC_BITS_MASK      = VM_Word.one().lsh(REQUESTED_BITS).sub(VM_Word.one()); //...00011

  public static final int DEC_KILL = 0;    // dec to zero RC --> reclaim obj
  public static final int DEC_PURPLE = 1;  // dec to non-zero RC, already buf'd
  public static final int DEC_BUFFER = -1; // dec to non-zero RC, need to bufr

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
  public static void dumpHeader(VM_Address ref) 
    throws VM_PragmaUninterruptible {
    // nothing to do (no bytes of GC header)
  }


  /****************************************************************************
   * 
   * Core ref counting support
   */

  /**
   * Return true if given object is live
   *
   * @param object The object whose liveness is to be tested
   * @return True if the object is alive
   */
  public static boolean isLiveRC(VM_Address object) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    return VM_Magic.getIntAtOffset(object, RC_HEADER_OFFSET) >= LIVE_THRESHOLD;
  }

  /**
   * Return true if given object is unreachable from roots or other
   * objects (i.e. ignoring the finalizer list).  Mark the object as a
   * finalizer object.
   *
   * @param object The object whose finalizability is to be tested
   * @return True if the object is finalizable
   */
  static boolean isFinalizable(VM_Address object) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    setFinalizer(object);
    return VM_Magic.getIntAtOffset(object, RC_HEADER_OFFSET) < HARD_THRESHOLD;
  }

  static void incRCOOL(VM_Address object) 
    throws VM_PragmaUninterruptible, VM_PragmaNoInline {
    incRC(object);
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
  public static void incRC(VM_Address object)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    int oldValue, newValue;
    do {
      oldValue = VM_Magic.prepareInt(object, RC_HEADER_OFFSET);
      newValue = oldValue + INCREMENT;
      if (VM_Interface.VerifyAssertions)
        VM_Interface._assert(newValue <= INCREMENT_LIMIT);
      if (Plan.REF_COUNT_CYCLE_DETECTION) newValue = (newValue & ~PURPLE);
    } while (!VM_Magic.attemptInt(object, RC_HEADER_OFFSET, oldValue, newValue));
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
  public static int decRC(VM_Address object)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    int oldValue, newValue;
    int rtn;
    do {
      oldValue = VM_Magic.prepareInt(object, RC_HEADER_OFFSET);
      newValue = oldValue - INCREMENT;
      if (newValue < LIVE_THRESHOLD)
        rtn = DEC_KILL;
      else if (Plan.REF_COUNT_CYCLE_DETECTION && 
               ((newValue & COLOR_MASK) < PURPLE)) { // if not purple or green
        rtn = ((newValue & BUFFERED_MASK) == 0) ? DEC_BUFFER : DEC_PURPLE;
        newValue = (newValue & ~COLOR_MASK) | PURPLE | BUFFERED_MASK;
      } else
        rtn = DEC_PURPLE;
    } while (!VM_Magic.attemptInt(object, RC_HEADER_OFFSET, oldValue, newValue));
    return rtn;
  }
  
  public static boolean isBuffered(VM_Address object)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    return  (VM_Magic.getIntAtOffset(object, RC_HEADER_OFFSET) & BUFFERED_MASK) == BUFFERED_MASK;
  }


  /****************************************************************************
   *
   * Sanity check support
   */
 
  /**
   * Increment the sanity reference count of an object.
   *
   * @param object The object whose sanity RC is to be incremented.
   * @return <code>true</code> if this is the first increment to this object
   */
  static boolean incSanityRC(VM_Address object, boolean root)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    int oldValue, newValue;
    do {
      oldValue = VM_Magic.prepareInt(object, RC_SANITY_HEADER_OFFSET);
      if (root) {
        newValue = (oldValue | ROOT_REACHABLE);
      } else {
        newValue = oldValue + INCREMENT;
      }
    } while (!VM_Magic.attemptInt(object, RC_SANITY_HEADER_OFFSET, oldValue,
                                  newValue));
    return (oldValue == 0);
  }

  /**
   * Check the ref count and sanity ref count for an object that
   * should have just died.
   *
   * @param object The object to be checked.
   */
  public static void checkOldObject(VM_Address object)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    int rcv = (VM_Magic.getIntAtOffset(object, RC_HEADER_OFFSET)>>>(INCREMENT_SHIFT-1));
    int sanityRCV = (VM_Magic.getIntAtOffset(object, RC_SANITY_HEADER_OFFSET)>>>(INCREMENT_SHIFT-1));
    if (VM_Interface.VerifyAssertions)
      VM_Interface._assert(sanityRCV == 0);
    if (sanityRCV == 0 && rcv != 0) {
      VM_Interface.sysFail("");
    }
  }

  /**
   * Check the sanity reference count and actual reference count for
   * an object.
   *
   * @param object The object whose RC is to be checked.
   * @return <code>true</code> if this is the first check of this object
   */
  static boolean checkAndClearSanityRC(VM_Address object) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    int value = VM_Magic.getIntAtOffset(object, RC_HEADER_OFFSET);
    int sanityValue = VM_Magic.getIntAtOffset(object, RC_SANITY_HEADER_OFFSET);
    int sanityRC = sanityValue >>> INCREMENT_SHIFT;
    boolean sanityRoot = (sanityValue & ROOT_REACHABLE) == ROOT_REACHABLE;
    boolean root = (value & ROOT_REACHABLE) == ROOT_REACHABLE;
    if (sanityValue == 0) {
      return false;
    } else {
      RefCountLocal.sanityLiveObjects++;
      int rc = value >>> INCREMENT_SHIFT;
      if (sanityRC != rc) {
        Log.write("RC mismatch for object: ");
        Log.write(object);
        Log.write(" "); Log.write(rc);
        Log.write(" (rc) != "); Log.write(sanityRC);
        Log.write(" (sanityRC)");
        if (root) Log.write(" r");
        if (sanityRoot) Log.write(" sr");
        Log.writeln("");
        if (((sanityRC == 0) && !sanityRoot) || ((rc == 0) && !root)) {
          VM_Interface.sysFail("");
        }
      }
      VM_Magic.setIntAtOffset(object, RC_SANITY_HEADER_OFFSET, 0);
      return true;
    }
  }

  /**
   * Set the mark bit within the sanity RC word for an object as part
   * of a transitive closure of live objects in the sanity trace.
   * This is only used for non-reference counted objects (immortal
   * etc).
   *
   * @param object The object to be marked.
   * @return <code>true</code> if the mark bit had not already been
   * cleared.
   */
  static boolean markSanityRC(VM_Address object)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    int sanityValue = VM_Magic.getIntAtOffset(object, RC_SANITY_HEADER_OFFSET);
    if (sanityValue == 1)
      return false;
    else {
      VM_Magic.setIntAtOffset(object, RC_SANITY_HEADER_OFFSET, 1);
      return true;
    }
  }

  /**
   * Clear the mark bit within the sanity RC word for an object as
   * part of a transitive closure of live objects in the sanity trace.
   * This is only used for non-reference counted objects (immortal
   * etc).
   *
   * @param object The object to be unmarked.
   * @return <code>true</code> if the mark bit had not already been cleared.
   */
  static boolean unmarkSanityRC(VM_Address object)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    int sanityValue = VM_Magic.getIntAtOffset(object, RC_SANITY_HEADER_OFFSET);
    if (sanityValue == 0)
      return false;
    else {
      VM_Magic.setIntAtOffset(object, RC_SANITY_HEADER_OFFSET, 0);
      return true;
    }
  }  

  /****************************************************************************
   * 
   * Finalization and dealing with roots
   */

  /**
   * Set the <code>ROOT_REACHABLE</code> bit for an object if it is
   * not already set.  Return true if it was not already set, false
   * otherwise.
   *
   * @param object The object whose <code>ROOT_REACHABLE</code> bit is
   * to be set.
   * @return <code>true</code> if it was set by this call,
   * <code>false</code> if the bit was already set.
   */
  public static boolean setRoot(VM_Address object) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    int oldValue, newValue;
    do {
      oldValue = VM_Magic.prepareInt(object, RC_HEADER_OFFSET);
      if ((oldValue & ROOT_REACHABLE) == ROOT_REACHABLE)
        return false;
      newValue = oldValue | ROOT_REACHABLE;
    } while (!VM_Magic.attemptInt(object, RC_HEADER_OFFSET, oldValue, newValue));
    return true;
  }

  /**
   * Clear the <code>ROOT_REACHABLE</code> bit for an object.
   *
   * @param object The object whose <code>ROOT_REACHABLE</code> bit is
   * to be cleared.
   */
  public static void unsetRoot(VM_Address object) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    int oldValue, newValue;
    do {
      oldValue = VM_Magic.prepareInt(object, RC_HEADER_OFFSET);
      newValue = oldValue & ~ROOT_REACHABLE;
    } while (!VM_Magic.attemptInt(object, RC_HEADER_OFFSET, oldValue, newValue));
  }

  /**
   * Set the <code>FINALIZABLE</code> bit for an object.
   *
   * @param object The object whose <code>FINALIZABLE</code> bit is
   * to be set.
   */
  static void setFinalizer(VM_Address object) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    int oldValue, newValue;
    do {
      oldValue = VM_Magic.prepareInt(object, RC_HEADER_OFFSET);
      newValue = oldValue | FINALIZABLE;
    } while (!VM_Magic.attemptInt(object, RC_HEADER_OFFSET, oldValue, newValue));
  }

  /**
   * Clear the <code>FINALIZABLE</code> bit for an object.
   *
   * @param object The object whose <code>FINALIZABLE</code> bit is
   * to be cleared.
   */
  static void clearFinalizer(VM_Address object) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    int oldValue, newValue;
    do {
      oldValue = VM_Magic.prepareInt(object, RC_HEADER_OFFSET);
      newValue = oldValue & ~FINALIZABLE;
    } while (!VM_Magic.attemptInt(object, RC_HEADER_OFFSET, oldValue, newValue));
  }

  /****************************************************************************
   * 
   * Trial deletion support
   */

  /**
   * Decrement the reference count of an object. This is unsychronized.
   *
   * @param object The object whose RC is to be decremented.
   */
  public static void unsyncDecRC(VM_Address object)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    int oldValue, newValue;
    int rtn;
    oldValue = VM_Magic.getIntAtOffset(object, RC_HEADER_OFFSET);
    newValue = oldValue - INCREMENT;
    VM_Magic.setIntAtOffset(object, RC_HEADER_OFFSET, newValue);
  }

  /**
   * Increment the reference count of an object. This is unsychronized.
   *
   * @param object The object whose RC is to be incremented.
   */
  public static void unsyncIncRC(VM_Address object)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    int oldValue, newValue;
    oldValue = VM_Magic.getIntAtOffset(object, RC_HEADER_OFFSET);
    newValue = oldValue + INCREMENT;
    VM_Magic.setIntAtOffset(object, RC_HEADER_OFFSET, newValue);
  }

  public static void print(VM_Address object)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    Log.write(' ');
    Log.write(VM_Magic.getIntAtOffset(object, RC_HEADER_OFFSET)>>INCREMENT_SHIFT); 
    Log.write(' ');
    switch (getHiRCColor(object)) {
    case PURPLE: Log.write('p'); break;
    case GREEN: Log.write('g'); break;
    }
    switch (getLoRCColor(object)) {
    case BLACK: Log.write('b'); break;
    case WHITE: Log.write('w'); break;
    case GREY: Log.write('g'); break;
    }
    if (isBuffered(object))
      Log.write('b');
    else
      Log.write('u');
  }
  public static void clearBufferedBit(VM_Address object) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    int oldValue = VM_Magic.getIntAtOffset(object, RC_HEADER_OFFSET);
    int newValue = oldValue & ~BUFFERED_MASK;
    VM_Magic.setIntAtOffset(object, RC_HEADER_OFFSET, newValue);
  }
  public static boolean isBlack(VM_Address object) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    return getLoRCColor(object) == BLACK;
  }
  public static boolean isWhite(VM_Address object) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    return getLoRCColor(object) == WHITE;
  }
  public static boolean isGreen(VM_Address object) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    return getHiRCColor(object) == GREEN;
  }
  public static boolean isPurple(VM_Address object) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    return getHiRCColor(object) == PURPLE;
  }
  public static boolean isPurpleNotGrey(VM_Address object) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    return (VM_Magic.getIntAtOffset(object, RC_HEADER_OFFSET) & (PURPLE | GREY)) == PURPLE;
  }
  public static boolean isGrey(VM_Address object) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    return getLoRCColor(object) == GREY;
  }
  private static int getRCColor(VM_Address object) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    return COLOR_MASK & VM_Magic.getIntAtOffset(object, RC_HEADER_OFFSET);
  }
  private static int getLoRCColor(VM_Address object) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    return LO_COLOR_MASK & VM_Magic.getIntAtOffset(object, RC_HEADER_OFFSET);
  }
  private static int getHiRCColor(VM_Address object) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    return HI_COLOR_MASK & VM_Magic.getIntAtOffset(object, RC_HEADER_OFFSET);
  }
  public static void makeBlack(VM_Address object) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    changeRCLoColor(object, BLACK);
  }
  public static void makeWhite(VM_Address object) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    changeRCLoColor(object, WHITE);
  }
  public static void makeGrey(VM_Address object) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    if (VM_Interface.VerifyAssertions) 
      VM_Interface._assert(getHiRCColor(object) != GREEN);
    changeRCLoColor(object, GREY);
  }
  private static void changeRCLoColor(VM_Address object, int color)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    int oldValue = VM_Magic.getIntAtOffset(object, RC_HEADER_OFFSET);
    int newValue = (oldValue & ~LO_COLOR_MASK) | color;
    VM_Magic.setIntAtOffset(object, RC_HEADER_OFFSET, newValue);
  }

  // See Bacon & Rajan ECOOP 2001 for notion of colors (purple, grey,
  // black, green).  See also Jones & Lins for a description of "Lins'
  // algorithm", on which Bacon & Rajan's is based.

  // The following are arranged to try to make the most common tests
  // fastest ("bufferd?", "green?" and "(green | purple)?") 
  private static final int     BUFFERED_MASK = 0x1;  //  .. xx0001
  private static final int        COLOR_MASK = 0x1e;  //  .. x11110 
  private static final int     LO_COLOR_MASK = 0x6;  //  .. x00110 
  private static final int     HI_COLOR_MASK = 0x18; //  .. x11000 
  private static final int             BLACK = 0x0;  //  .. xxxx0x
  private static final int              GREY = 0x2;  //  .. xxxx1x
  private static final int             WHITE = 0x4;  //  .. xx010x
  // green & purple *MUST* remain the highest colors in order to
  // preserve the (green | purple) test's precondition.
  private static final int            PURPLE = 0x8;  //  .. x01xxx
  protected static final int           GREEN = 0x10;  // .. x10xxx

  // bits used to ensure retention of objects with zero RC
  private static final int       FINALIZABLE = 0x20; //  .. 100000
  private static final int    ROOT_REACHABLE = 0x40; //  .. x10000
  private static final int    HARD_THRESHOLD = ROOT_REACHABLE;
  private static final int    LIVE_THRESHOLD = FINALIZABLE;
  private static final int         BITS_USED = 7;

  protected static final int INCREMENT_SHIFT = BITS_USED;
  protected static final int INCREMENT = 1<<INCREMENT_SHIFT;
  protected static final int AVAILABLE_BITS = BITS_IN_ADDRESS - BITS_USED;
  protected static final int INCREMENT_LIMIT = ~(1<<(BITS_IN_ADDRESS-1));
}
