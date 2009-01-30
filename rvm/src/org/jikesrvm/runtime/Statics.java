/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.runtime;

import org.jikesrvm.VM;
import org.jikesrvm.Constants;
import org.jikesrvm.ArchitectureSpecific.CodeArray;
import org.jikesrvm.mm.mminterface.MemoryManagerConstants;
import org.jikesrvm.mm.mminterface.MemoryManager;
import org.jikesrvm.objectmodel.TIB;
import org.jikesrvm.util.BitVector;
import org.jikesrvm.util.ImmutableEntryIdentityHashMapRVM;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.pragma.UninterruptibleNoWarn;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Extent;
import org.vmmagic.unboxed.Offset;
import org.vmmagic.unboxed.Word;

/**
 * The static fields and methods comprising a running virtual machine image.
 *
 * <p> These fields, methods and literal constants form the "root set"
 * of all the objects in the running virtual machine. They are stored
 * in an array where the middle element is always pointed to by the
 * virtual machine's "table of contents" (jtoc) register. The slots of
 * this array hold either primitives (int, long, float, double),
 * object pointers, or array pointers. To enable the garbage collector
 * to differentiate between reference and non-reference values,
 * reference values are indexed positively and numeric values
 * negatively with respect to the middle of the table.
 *
 * <p> Consider the following declarations:
 *
 * <pre>
 *      class A { static int    i = 123;    }
 *      class B { static String s = "abc";  }
 *      class C { static double d = 4.56;   }
 *      class D { static void m() {} }
 * </pre>
 *
 * <p>Here's a picture of what the corresponding jtoc would look like
 * in memory:
 *
 * <pre>
 *                     +---------------+
 *                     |     ...       |
 *                     +---------------+
 * field            -6 |   C.d (hi)    |
 *                     +---------------+
 * field            -5 |   C.d (lo)    |
 *                     +---------------+
 * literal          -4 |  4.56 (hi)    |
 *                     +---------------+
 * literal          -3 |  4.56 (lo)    |
 *                     +---------------+
 * field            -2 |     A.i       |
 *                     +---------------+
 * literal          -1 |     123       |
 *                     +---------------+       +---------------+
 * [jtoc register]-> 0:|      0        |       |   (header)    |
 *                     +---------------+       +---------------+
 * literal           1:|  (objref)   --------->|    "abc"      |
 *                     +---------------+       +---------------+
 * field             2:|     B.s       |
 *                     +---------------+       +---------------+
 *                   3:|  (coderef)  ------+   |   (header)    |
 *                     +---------------+   |   +---------------+
 *                     |     ...       |   +-->|  machine code |
 *                     +---------------+       |    for "m"    |
 *                                             +---------------+
 * </pre>
 */
public class Statics implements Constants {
  /**
   * How many 32bit slots do we want in the JTOC to hold numeric (non-reference) values?
   */
  private static final int numNumericSlots =   0x20000; // 128k

  /**
   * How many reference-sized slots do we want in the JTOC to hold reference values?
   */
  private static final int numReferenceSlots = 0x20000; // 128k

  /**
   * Static data values (pointed to by jtoc register).
   * This is currently fixed-size, although at one point the system's plans
   * called for making it dynamically growable.  We could also make it
   * non-contiguous.
   */
  private static final int[] slots = new int[numNumericSlots + (VM.BuildFor64Addr ? 2 : 1) * numReferenceSlots];

  /**
   * Object version of the slots used during boot image creation and
   * destroyed shortly after. This is required to support conversion
   * of a slot address to its associated object during boot image
   * creation.
   */
  private static Object[] objectSlots = new Object[slots.length];

  /**
   * The logical middle of the table, references are slots above this and
   * numeric values below this. The JTOC points to the middle of the
   * table.
   */
  public static final int middleOfTable = numNumericSlots;

  /** Next available numeric slot number */
  private static volatile int nextNumericSlot = middleOfTable - 1;

  /**
   * Numeric slot hole. Holes are created to align 8byte values. We
   * allocate into a hole rather than consume another numeric slot.
   * The value of middleOfTable indicates the slot isn't in use.
   */
  private static volatile int numericSlotHole = middleOfTable;

  /** Next available reference slot number */
  private static volatile int nextReferenceSlot = middleOfTable;

  /**
   * Bit vector indicating whether a numeric slot is a field (true) or a
   * literal (false).
   */
  private static final BitVector numericFieldVector = new BitVector(middleOfTable);

  /**
   * Map of objects to their literal offsets
   */
  private static final ImmutableEntryIdentityHashMapRVM<Object, Integer> objectLiterals =
    new ImmutableEntryIdentityHashMapRVM<Object, Integer>();

  static {
    // allocate a slot to be null - offset zero should map to null
    int offset = allocateReferenceSlot(false).toInt();
    if (VM.VerifyAssertions) VM._assert(offset == 0);
  }

  /**
   * Conversion from JTOC slot index to JTOC offset.
   */
  @Uninterruptible
  public static Offset slotAsOffset(int slot) {
    return Offset.fromIntSignExtend((slot - middleOfTable) << LOG_BYTES_IN_INT);
  }

  /**
   * Conversion from JTOC offset to JTOC slot index.
   */
  @Uninterruptible
  public static int offsetAsSlot(Offset offset) {
    if (VM.VerifyAssertions) VM._assert((offset.toInt() & 3) == 0);
    return middleOfTable + (offset.toInt() >> LOG_BYTES_IN_INT);
  }

  /**
   * Return the lowest slot number in use
   */
  public static int getLowestInUseSlot() {
    return nextNumericSlot + 1;
  }

  /**
   * Return the highest slot number in use
   */
  public static int getHighestInUseSlot() {
    return nextReferenceSlot - (VM.BuildFor32Addr ? 1 : 2);
  }

  /**
   * Find the given literal in the int like literal map, if not found
   * create a slot for the literal and place an entry in the map
   * @param literal the literal value to find or create
   * @return the offset in the JTOC of the literal
   */
  public static int findOrCreateIntSizeLiteral(int literal) {
    final int bottom = getLowestInUseSlot();
    final int top = middleOfTable;
    for (int i=top; i >= bottom; i--) {
      if ((slots[i] == literal) && !numericFieldVector.get(i) && (i != numericSlotHole)) {
        return slotAsOffset(i).toInt();
      }
    }
    Offset newOff = allocateNumericSlot(BYTES_IN_INT, false);
    setSlotContents(newOff, literal);
    return newOff.toInt();
  }

  /**
   * Find the given literal in the long like literal map, if not found
   * create a slot for the literal and place an entry in the map
   * @param literal the literal value to find or create
   * @return the offset in the JTOC of the literal
   */
  public static int findOrCreateLongSizeLiteral(long literal) {
    final int bottom = getLowestInUseSlot();
    final int top = middleOfTable & 0xFFFFFFFE;
    for (int i=top; i >= bottom; i-=2) {
      Offset off = slotAsOffset(i);
      if ((getSlotContentsAsLong(off) == literal) &&
          !numericFieldVector.get(i) && !(numericFieldVector.get(i+1)) &&
          (i != numericSlotHole) && (i+1 != numericSlotHole)) {
        return slotAsOffset(i).toInt();
      }
    }
    Offset newOff = allocateNumericSlot(BYTES_IN_LONG, false);
    setSlotContents(newOff, literal);
    return newOff.toInt();
  }

  /**
   * Find the given literal in the 16byte like literal map, if not found
   * create a slot for the literal and place an entry in the map
   * @param literal_high the high part of the literal value to find or create
   * @param literal_low the low part of the literal value to find or create
   * @return the offset in the JTOC of the literal
   */
  public static int findOrCreate16ByteSizeLiteral(long literal_high, long literal_low) {
    final int bottom = getLowestInUseSlot();
    final int top = middleOfTable & 0xFFFFFFFC;
    for (int i=top; i >= bottom; i-=4) {
      Offset off = slotAsOffset(i);
      if ((getSlotContentsAsLong(off) == literal_low) &&
          (getSlotContentsAsLong(off.plus(8)) == literal_high) &&
          !numericFieldVector.get(i) && !(numericFieldVector.get(i+1)) &&
          !numericFieldVector.get(i+2) && !(numericFieldVector.get(i+3)) &&
          (i != numericSlotHole) && (i+1 != numericSlotHole) &&
          (i+2 != numericSlotHole) && (i+3 != numericSlotHole)) {
        return slotAsOffset(i).toInt();
      }
    }
    Offset newOff = allocateNumericSlot(16, false);
    setSlotContents(newOff, literal_low);
    setSlotContents(newOff.plus(8), literal_high);
    return newOff.toInt();
  }

  /**
   * Find or allocate a slot in the jtoc for an object literal.
   * @param       literal value
   * @return offset of slot that was allocated
   * Side effect: literal value is stored into jtoc
   */
  public static int findOrCreateObjectLiteral(Object literal) {
    int off = findObjectLiteral(literal);
    if (off != 0) {
      return off;
    } else {
      Offset newOff = allocateReferenceSlot(false);
      setSlotContents(newOff, literal);
      synchronized(objectLiterals) {
        objectLiterals.put(literal, newOff.toInt());
      }
      return newOff.toInt();
    }
  }

  /**
   * Find a slot in the jtoc with this object literal in else return 0
   * @param  literal value
   * @return offset containing literal or 0
   */
  public static int findObjectLiteral(Object literal) {
    synchronized (objectLiterals) {
      Integer result = objectLiterals.get(literal);
      return result == null ? 0 : result.intValue();
    }
  }

  /**
   * Mark a slot that was previously a field as being a literal as its value is
   * final
   */
  public static synchronized void markAsNumericLiteral(int size, Offset fieldOffset) {
    int slot = offsetAsSlot(fieldOffset);
    if (size == BYTES_IN_LONG) {
      numericFieldVector.clear(slot);
      numericFieldVector.clear(slot+1);
    } else {
      numericFieldVector.clear(slot);
    }
  }

  /**
   * Mark a slot that was previously a field as being a literal as its value is
   * final
   */
  public static synchronized void markAsReferenceLiteral(Offset fieldOffset) {
    Object literal = getSlotContentsAsObject(fieldOffset);
    if (!VM.runningVM && literal instanceof TIB) {
      // TIB is just a wrapper for the boot image, so don't place the wrapper
      // in objectLiterals
      return;
    } else if (literal != null) {
      if (findObjectLiteral(literal) == 0) {
        synchronized(objectLiterals) {
          objectLiterals.put(literal, fieldOffset.toInt());
        }
      }
    }
  }

  /**
   * Allocate a numeric slot in the jtoc.
   * @param size of slot
   * @param field is the slot for a field
   * @return offset of slot that was allocated as int
   * (two slots are allocated for longs and doubles)
   */
  public static synchronized Offset allocateNumericSlot(int size, boolean field) {
    // Result slot
    int slot;
    // Allocate 2 or 4 slots for wide items after possibly blowing
    // other slots for alignment.
    if (size == 16) {
      // widen for a wide
      nextNumericSlot-=3;
      // check alignment
      if ((nextNumericSlot & 1) != 0) {
        // slot isn't 8byte aligned so increase by 1 and record hole
        nextNumericSlot--;
        numericSlotHole = nextNumericSlot + 2;
      }
      if ((nextNumericSlot & 3) != 0) {
        // slot not 16byte aligned, ignore any holes
        nextNumericSlot-=2;
      }
      // Remember the slot and adjust the next available slot
      slot = nextNumericSlot;
      nextNumericSlot--;
      if (field) {
        numericFieldVector.set(slot);
        numericFieldVector.set(slot+1);
        numericFieldVector.set(slot+2);
        numericFieldVector.set(slot+3);
      }
    } else if (size == BYTES_IN_LONG) {
      // widen for a wide
      nextNumericSlot--;
      // check alignment
      if ((nextNumericSlot & 1) != 0) {
        // slot isn't 8byte aligned so increase by 1 and record hole
        nextNumericSlot--;
        numericSlotHole = nextNumericSlot + 2;
      }
      // Remember the slot and adjust the next available slot
      slot = nextNumericSlot;
      nextNumericSlot--;
      if (field) {
        numericFieldVector.set(slot);
        numericFieldVector.set(slot+1);
      }
    } else {
      // 4byte quantity, try to reuse hole if one is available
      if (numericSlotHole != middleOfTable) {
        slot = numericSlotHole;
        numericSlotHole = middleOfTable;
      } else {
        slot = nextNumericSlot;
        nextNumericSlot--;
      }
      if (field) {
        numericFieldVector.set(slot);
      }
    }
    if (nextNumericSlot < 0) {
      enlargeTable();
    }
    return slotAsOffset(slot);
  }

  /**
   * Allocate a reference slot in the jtoc.
   * @param field is the slot for a field
   * @return offset of slot that was allocated as int
   * (two slots are allocated on 64bit architectures)
   */
  public static synchronized Offset allocateReferenceSlot(boolean field) {
    int slot = nextReferenceSlot;
    nextReferenceSlot += getReferenceSlotSize();
    if (nextReferenceSlot >= slots.length) {
      enlargeTable();
    }
    return slotAsOffset(slot);
  }

  /**
   * Grow the statics table
   */
  private static void enlargeTable() {
    // !!TODO: enlarge slots[] and descriptions[], and modify jtoc register to
    // point to newly enlarged slots[]
    // NOTE: very tricky on IA32 because opt uses 32 bit literal address to access jtoc.
    VM.sysFail("Statics.enlargeTable: jtoc is full");
  }

  /**
   * Fetch number of numeric jtoc slots currently allocated.
   */
  @Uninterruptible
  public static int getNumberOfNumericSlots() {
    return middleOfTable - nextNumericSlot;
  }

  /**
   * Fetch number of reference jtoc slots currently allocated.
   */
  @Uninterruptible
  public static int getNumberOfReferenceSlots() {
    return nextReferenceSlot - middleOfTable;
  }

  /**
   * Fetch total number of slots comprising the jtoc.
   */
  @Uninterruptible
  public static int getTotalNumberOfSlots() {
    return slots.length;
  }

  /**
   * Does specified jtoc slot contain a reference?
   * @param  slot obtained from offsetAsSlot()
   * @return true --> slot contains a reference
   */
  @Uninterruptible
  public static boolean isReference(int slot) {
    return slot >= middleOfTable;
  }

  /**
   * Does specified jtoc slot contain an int sized literal?
   * @param  slot obtained from offsetAsSlot()
   * @return true --> slot contains a reference
   */
  public static boolean isIntSizeLiteral(int slot) {
    if (isReference(slot) || slot < getLowestInUseSlot()) {
      return false;
    } else {
      return !numericFieldVector.get(slot);
    }
  }

  /**
   * Does specified jtoc slot contain a long sized literal?
   * @param  slot obtained from offsetAsSlot()
   * @return true --> slot contains a reference
   */
  public static boolean isLongSizeLiteral(int slot) {
    if (isReference(slot) || slot < getLowestInUseSlot() || ((slot & 1) != 0)) {
      return false;
    } else {
      return !numericFieldVector.get(slot) && !numericFieldVector.get(slot+1);
    }
  }

  /**
   * Does specified jtoc slot contain a reference literal?
   * @param  slot obtained from offsetAsSlot()
   * @return true --> slot contains a reference
   */
  public static boolean isReferenceLiteral(int slot) {
    if (!isReference(slot) || slot > getHighestInUseSlot()) {
      return false;
    } else {
      return (slotAsOffset(slot).toInt() == 0) ||
        (findObjectLiteral(getSlotContentsAsObject(slotAsOffset(slot))) != 0);
    }
  }

  /**
   * Get size occupied by a reference
   */
  @Uninterruptible
  public static int getReferenceSlotSize() {
    return VM.BuildFor64Addr ? 2 : 1;
  }

  /**
   * Fetch jtoc object (for JNI environment and GC).
   */
  @Uninterruptible
  public static Address getSlots() {
    return Magic.objectAsAddress(slots).plus(middleOfTable << LOG_BYTES_IN_INT);
  }

  /**
   * Fetch jtoc object (for JNI environment and GC).
   */
  @Uninterruptible
  public static int[] getSlotsAsIntArray() {
    return slots;
  }

  /**
   * Fetch contents of a slot, as an integer
   */
  @Uninterruptible
  public static int getSlotContentsAsInt(Offset offset) {
    if (VM.runningVM) {
      return Magic.getIntAtOffset(slots, offset.plus(middleOfTable << LOG_BYTES_IN_INT));
    } else {
      int slot = offsetAsSlot(offset);
      return slots[slot];
    }
  }

  /**
   * Fetch contents of a slot-pair, as a long integer.
   */
  @Uninterruptible
  public static long getSlotContentsAsLong(Offset offset) {
    if (VM.runningVM) {
      return Magic.getLongAtOffset(slots, offset.plus(middleOfTable << LOG_BYTES_IN_INT));
    } else {
      int slot = offsetAsSlot(offset);
      long result;
      if (VM.LittleEndian) {
        result = (((long) slots[slot + 1]) << BITS_IN_INT); // hi
        result |= ((long) slots[slot]) & 0xFFFFFFFFL; // lo
      } else {
        result = (((long) slots[slot]) << BITS_IN_INT);     // hi
        result |= ((long) slots[slot + 1]) & 0xFFFFFFFFL; // lo
      }
      return result;
    }
  }

  /**
   * Fetch contents of a slot, as an object.
   */
  @Uninterruptible
  public static Object getSlotContentsAsObject(Offset offset) {
    if (VM.runningVM) {
      return Magic.getObjectAtOffset(slots, offset.plus(middleOfTable << LOG_BYTES_IN_INT));
    } else {
      return objectSlots[offsetAsSlot(offset)];
    }
  }

  /**
   * Fetch contents of a slot, as an Address.
   */
  @UninterruptibleNoWarn("Interruptible code only reachable during boot image creation")
  public static Address getSlotContentsAsAddress(Offset offset) {
    if (VM.runningVM) {
      if (VM.BuildFor32Addr) {
        return Address.fromIntSignExtend(getSlotContentsAsInt(offset));
      } else {
        return Address.fromLong(getSlotContentsAsLong(offset));
      }
    } else {
      // Addresses are represented by objects in the tools building the VM
      Object unboxed = objectSlots[offsetAsSlot(offset)];
      if (unboxed instanceof Address) {
        return (Address) unboxed;
      } else if (unboxed instanceof Word) {
        return ((Word) unboxed).toAddress();
      } else if (unboxed instanceof Extent) {
        return ((Extent) unboxed).toWord().toAddress();
      } else if (unboxed instanceof Offset) {
        return ((Offset) unboxed).toWord().toAddress();
      } else {
        if (VM.VerifyAssertions) VM._assert(false);
        return Address.zero();
      }
    }
  }

  /**
   * Set contents of a slot, as an integer.
   */
  @Uninterruptible
  public static void setSlotContents(Offset offset, int value) {
    if (VM.runningVM) {
      Magic.setIntAtOffset(slots, offset.plus(middleOfTable << LOG_BYTES_IN_INT), value);
    } else {
      slots[offsetAsSlot(offset)] = value;
    }
  }

  /**
   * Set contents of a slot, as a long integer.
   */
  @Uninterruptible
  public static void setSlotContents(Offset offset, long value) {
    if (VM.runningVM) {
      Magic.setLongAtOffset(slots, offset.plus(middleOfTable << LOG_BYTES_IN_INT), value);
    } else {
      int slot = offsetAsSlot(offset);
      if (VM.LittleEndian) {
        slots[slot + 1] = (int) (value >>> BITS_IN_INT); // hi
        slots[slot] = (int) (value); // lo
      } else {
        slots[slot] = (int) (value >>> BITS_IN_INT); // hi
        slots[slot + 1] = (int) (value); // lo
      }
    }
  }

  /**
   * Set contents of a slot, as an object.
   */
  @UninterruptibleNoWarn("Interruptible code only reachable during boot image creation")
  public static void setSlotContents(Offset offset, Object object) {
    // NB uninterruptible warnings are disabled for this method due to
    // the array store which could cause a fault - this can't actually
    // happen as the fault would only ever occur when not running the
    // VM. We suppress the warning as we know the error can't happen.

    if (VM.runningVM && MemoryManagerConstants.NEEDS_PUTSTATIC_WRITE_BARRIER) {
      MemoryManager.putstaticWriteBarrier(object, offset, 0);
    } else {
      setSlotContents(offset, Magic.objectAsAddress(object).toWord());
    }
    if (VM.VerifyAssertions) VM._assert(offset.toInt() > 0);
    if (!VM.runningVM && objectSlots != null) {
      // When creating the boot image objectSlots is populated as
      // Magic won't work in the bootstrap JVM.
      objectSlots[offsetAsSlot(offset)] = Magic.bootImageIntern(object);
    }
  }

  /**
   * Set contents of a slot, as a CodeArray.
   */
  @Uninterruptible
  public static void setSlotContents(Offset offset, CodeArray code) {
    setSlotContents(offset, Magic.codeArrayAsObject(code));
  }

  /**
   * Set contents of a slot, as a CodeArray.
   */
  @Uninterruptible
  public static void setSlotContents(Offset offset, TIB tib) {
    setSlotContents(offset, Magic.tibAsObject(tib));
  }

  /**
   * Set contents of a slot, as a Word.
   */
  @Uninterruptible
  public static void setSlotContents(Offset offset, Word word) {
    if (VM.runningVM) {
      Magic.setWordAtOffset(slots, offset.plus(middleOfTable << LOG_BYTES_IN_INT), word);
    } else {
      if (VM.BuildFor32Addr) {
        setSlotContents(offset, word.toInt());
      } else {
        setSlotContents(offset, word.toLong());
      }
    }
  }

  /**
   * Inform Statics that boot image instantiation is over and that
   * unnecessary data structures, for runtime, can be released.
   * @return information that may later be restored to help generate
   * the boot image report
   */
  public static Object bootImageInstantiationFinished() {
    Object t = objectSlots;
    objectSlots = null;
    return t;
  }

  /**
   * After serializing Statics the boot image writer generates
   * a report. This method is called to restore data lost by the call
   * to bootImageInstantiationFinished.
   * @param slots object slots to restore
   */
  public static void bootImageReportGeneration(Object slots) {
    objectSlots = (Object[])slots;
  }
}
