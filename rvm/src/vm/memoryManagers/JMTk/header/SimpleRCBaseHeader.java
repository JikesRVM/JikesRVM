/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.BootImageInterface;
import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_Constants;
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
public abstract class SimpleRCBaseHeader implements VM_Constants {

  /**
   * How many bytes are used by all GC header fields?
   */
  public static final int NUM_BYTES_HEADER = 4;
  protected static final int RC_HEADER_OFFSET = VM_ObjectModel.JAVA_HEADER_END - NUM_BYTES_HEADER;

  /**
   * How many bits does this GC system require?
   */
  public static final int REQUESTED_BITS    = 2;
  public static final int GC_BITS_MASK      = 0x3;

  public static final int SMALL_OBJECT_MASK = 0x1;  // ...01

  public static boolean isSmallObject(Object ref)
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
    // nothing to do (no bytes of GC header)
  }

  public static boolean isLiveRC(Object obj) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    return VM_Magic.getIntAtOffset(obj, RC_HEADER_OFFSET) >= INCREMENT;
  }

  public static void incRC(Object object)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    changeRC(object, INCREMENT);
  }

  public static boolean decRC(Object object)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    return (changeRC(object, -INCREMENT) < INCREMENT);
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

  public static void print(Object object)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    VM.sysWrite(VM_Magic.getIntAtOffset(object, RC_HEADER_OFFSET)>>CYCLE_DETECTION_BITS); 
    VM.sysWrite(' ');
    switch (getRCColor(object)) {
    case BLACK: VM.sysWrite('b'); break;
    case WHITE: VM.sysWrite('w'); break;
    case PURPLE: VM.sysWrite('p'); break;
    case GREEN: VM.sysWrite('x'); break;
    case GREY: VM.sysWrite('g'); break;
    }
    if (isBuffered(object))
      VM.sysWrite('b');
    else
      VM.sysWrite('u');
  }
  public static boolean isBuffered(Object object)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    return getRCbits(object, BUFFERED_MASK) != 0;
  }
  public static void setBufferedBit(Object object) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    setOrClearRCBit(object, BUFFERED_MASK, true);
  }
  public static void clearBufferedBit(Object object) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    setOrClearRCBit(object, BUFFERED_MASK, false);
  }
  private static int getRCbits(Object object, int mask)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    return VM_Magic.getIntAtOffset(object, RC_HEADER_OFFSET) & mask;
  }
  private static void setOrClearRCBit(Object object, int mask, boolean set)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    int oldValue, newValue;
    do {
      oldValue = VM_Magic.prepare(object, RC_HEADER_OFFSET);
      newValue = (set) ? oldValue | mask : oldValue & ~mask;
    } while (!VM_Magic.attempt(object, RC_HEADER_OFFSET, oldValue, newValue));
  }

  public static boolean isBlack(Object object) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    return getRCColor(object) == BLACK;
  }
  public static boolean isWhite(Object object) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    return getRCColor(object) == WHITE;
  }
  public static boolean isGreen(Object object) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    return getRCColor(object) >= GREEN;
  }
  public static boolean isPurple(Object object) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    return getRCColor(object) == PURPLE;
  }
  public static boolean isGreenOrPurple(Object object) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    return getRCColor(object) >= PURPLE;
  }
  public static boolean isGrey(Object object) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    return getRCColor(object) == GREY;
  }
  private static int getRCColor(Object object) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    return COLOR_MASK & VM_Magic.getIntAtOffset(object, RC_HEADER_OFFSET);
  }
  public static void makeBlack(Object object) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    changeRCColor(object, BLACK);
  }
  public static void makeWhite(Object object) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    changeRCColor(object, WHITE);
  }
  public static boolean makePurple(Object object)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    if (isGreenOrPurple(object))
      return false;  // inherently acyclic or already purple, so do nothing

    int oldValue, newValue;
    boolean rtn;
    do {
      oldValue = VM_Magic.prepare(object, RC_HEADER_OFFSET);
      newValue = (oldValue & ~BASE_COLOR_MASK) | PURPLE | BUFFERED_MASK;
      if ((oldValue & BUFFERED_MASK) == 0)
	rtn = true; // need to add to buffer
      else
	rtn = false; // already buffered
    } while (!VM_Magic.attempt(object, RC_HEADER_OFFSET, oldValue, newValue));
    return rtn;
  }
  public static void makeGrey(Object object) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    changeRCColor(object, GREY);
  }
  private static void changeRCColor(Object object, int color)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    int oldValue, newValue;
    if (VM.VerifyAssertions) VM._assert(color != GREEN);
    do {
      oldValue = VM_Magic.prepare(object, RC_HEADER_OFFSET);
      newValue = (oldValue & ~BASE_COLOR_MASK) | color;
    } while (!VM_Magic.attempt(object, RC_HEADER_OFFSET, oldValue, newValue));
  }

  // See Bacon & Rajan ECOOP 2001 for notion of colors (purple, grey,
  // black, green).  See also Jones & Lins for a description of "Lins'
  // algorithm", on which Bacon & Rajan's is based.

  // The following are arranged to try to make the most common tests
  // fastest ("bufferd?", "green?" and "(green | purple)?") 
  private static final int     BUFFERED_MASK = 0x1;  //  .. 00001
  protected static final int      COLOR_MASK = 0xe;  //  .. 00110 
  // we never change the color green, hence the color mask
  protected static final int BASE_COLOR_MASK = 0x6;  //  .. 00110 
  private static final int             BLACK = 0x0;  //  .. x000x
  private static final int              GREY = 0x2;  //  .. x001x
  private static final int             WHITE = 0x4;  //  .. x010x
  // green & purple *MUST* remain the highest colors in order to
  // preseve the (green | purple) test's precondition.
  private static final int            PURPLE = 0x6;  //  .. x011x
  protected static final int           GREEN = 0x8;  //  .. x100x
  private static final int BITS_USED = 4;

  private static final int CYCLE_DETECTION_BITS = (Plan.refCountCycleDetection) ? BITS_USED : 0;
  protected static final int INCREMENT = 1<<CYCLE_DETECTION_BITS;

}
