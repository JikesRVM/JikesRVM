/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Defines utility routines for manipulating the various GC header bits.  
 * NOTE: Not all of these routines are meaningful for every collector!
 * 
 * @see VM_ObjectModel
 * @see VM_AllocatorHeader
 * @see VM_AllocatorHeaderConstants
 * 
 * @author David Bacon
 * @author Steve Fink
 * @author Dave Grove
 */
public class VM_CommonAllocatorHeader 
  implements VM_Uninterruptible,
	     VM_AllocatorHeaderConstants {
  
  static final int GC_MARK_BIT_MASK    = 0x1;
  static final int GC_BARRIER_BIT_MASK = (1 << GC_BARRIER_BIT_IDX);

  /*
   * Barrier Bit -- only used when VM_Allocator.NEEDS_WRITE_BARRIER.
   */

  /**
   * test to see if the barrier bit is set
   */
  static boolean testBarrierBit(Object ref) {
    return VM_ObjectModel.testAvailableBit(ref, GC_BARRIER_BIT_IDX);
  }

  /**
   * clear the barrier bit (indicates that object is in write buffer)
   */
  static void clearBarrierBit(Object ref) {
    VM_ObjectModel.setAvailableBit(ref, GC_BARRIER_BIT_IDX, false);
  }

  /**
   * set the barrier bit (indicates that object needs to be put in write buffer
   * if a reference is stored into it).
   */
  static void setBarrierBit(Object ref) {
    VM_ObjectModel.setAvailableBit(ref, GC_BARRIER_BIT_IDX, true);
  }


  /*
   * Mark Bit
   */

  protected static final VM_SideMarkVector markVector = new VM_SideMarkVector();

  /**
   * test to see if the mark bit has the given value
   */
  static boolean testMarkBit(Object ref, int value) {
    if (USE_SIDE_MARK_VECTOR) {
      return markVector.testMarkBit(ref, value);
    } else {
      return (VM_ObjectModel.readAvailableBitsWord(ref) & value) != 0;
    }
  }

  /**
   * write the given value in the mark bit.
   */
  static void writeMarkBit(Object ref, int value) {
    if (USE_SIDE_MARK_VECTOR) {
      markVector.writeMarkBit(ref, value);
    } else {
      int oldValue = VM_ObjectModel.readAvailableBitsWord(ref);
      int newValue = (oldValue & ~GC_MARK_BIT_MASK) | value;
      VM_ObjectModel.writeAvailableBitsWord(ref, newValue);
    }
  }

  /**
   * atomically write the given value in the mark bit.
   */
  static void atomicWriteMarkBit(Object ref, int value) {
    if (USE_SIDE_MARK_VECTOR) {
      markVector.atomicWriteMarkBit(ref, value);
    } else {
      while (true) {
	int oldValue = VM_ObjectModel.prepareAvailableBits(ref);
	int newValue = (oldValue & ~GC_MARK_BIT_MASK) | value;
	if (VM_ObjectModel.attemptAvailableBits(ref, oldValue, newValue)) break;
      }
    }
  }

  /**
   * used to mark boot image objects during a parallel scan of objects during GC
   */
  static boolean testAndMark(Object ref, int value) {
    if (USE_SIDE_MARK_VECTOR) {
      return markVector.testAndMark(ref, value);
    } else {
      int oldValue;
      do {
	oldValue = VM_ObjectModel.prepareAvailableBits(ref);
	int markBit = oldValue & GC_MARK_BIT_MASK;
	if (markBit == value) return false;
      } while (!VM_ObjectModel.attemptAvailableBits(ref, oldValue, oldValue ^ GC_MARK_BIT_MASK));
      return true;
    }
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
  static ADDRESS attemptToForward(Object base) {
    VM_Magic.pragmaInline();
    int oldValue;
    do {
      oldValue = VM_ObjectModel.prepareAvailableBits(base);
      if ((oldValue & GC_FORWARDING_MASK) == GC_FORWARDED) return oldValue;
    } while (!VM_ObjectModel.attemptAvailableBits(base, oldValue, oldValue | GC_BEING_FORWARDED));
    return oldValue;
  }

  /**
   * Non-atomic read of forwarding pointer word
   */
  static ADDRESS getForwardingWord(Object base) {
    return VM_ObjectModel.readAvailableBitsWord(base);
  }

  /**
   * Has the object been forwarded?
   */
  static boolean isForwarded(Object base) {
    return stateIsForwarded(getForwardingWord(base));
  }

  /**
   * Has the object been forwarded?
   */
  static boolean isBeingForwarded(Object base) {
    return stateIsBeingForwarded(getForwardingWord(base));
  }

  /**
   * is the state of the forwarding word forwarded?
   */
  static boolean stateIsForwarded(int fw) {
    return (fw & GC_FORWARDING_MASK) == GC_FORWARDED;
  }

  /**
   * is the state of the forwarding word being forwarded?
   */
  static boolean stateIsBeingForwarded(int fw) {
    return (fw & GC_FORWARDING_MASK) == GC_BEING_FORWARDED;
  }

  /**
   * is the state of the forwarding word being forwarded?
   */
  static boolean stateIsForwardedOrBeingForwarded(int fw) {
    return (fw & GC_FORWARDED) != 0;
  }

  /**
   * Non-atomic read of forwarding pointer word
   */
  static Object getForwardingPointer(Object base) {
    return VM_Magic.addressAsObject(getForwardingWord(base) & ~GC_FORWARDING_MASK);
  }

  /**
   * Non-atomic write of forwarding pointer word
   * (assumption, thread doing the set has done attempt to forward
   *  and owns the right to copy the object)
   */
  static void setForwardingPointer(Object base, Object ptr) {
    VM_ObjectModel.writeAvailableBitsWord(base, VM_Magic.objectAsAddress(ptr) | GC_FORWARDED);
  }
}
