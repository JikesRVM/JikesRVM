/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp 2001,2002
 */
//$Id$
package com.ibm.jikesrvm;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

import com.ibm.jikesrvm.classloader.*;
import com.ibm.jikesrvm.util.*;

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
 *
 * @author Ian Rogers
 * @author Bowen Alpern
 * @author Dave Grove
 * @author Derek Lieber
 * @author Kris Venstermans
 */
public class VM_Statics implements VM_Constants {
  /**
   * Static data values (pointed to by jtoc register).
   * This is currently fixed-size, although at one point the system's plans
   * called for making it dynamically growable.  We could also make it
   * non-contiguous.
   */
  private static final int slots[] = new int[0x20000]; // 128K = 131072

  /**
   * Object version of the slots used during boot image creation and
   * destroyed shortly after. This is required to support conversion
   * of a slot address to its associated object during boot image
   * creation.
   */
  private static Object objectSlots[] = new Object[0x20000];

  /**
   * The middle of the table, references are slots above this and
   * numeric values below this. The JTOC points to the middle of the
   * table.
   */
  public static final int middleOfTable = slots.length / 2;

  /** Next available numeric slot number */
  private static int nextNumericSlot = middleOfTable - 1;

  /** next available reference slot number */
  private static int nextReferenceSlot = middleOfTable + (VM.BuildFor32Addr ? 1 : 2);

  /**
   * Mapping from int like literals (ints and floats) to the jtoc slot
   * that contains them.
   */
  private static final VM_HashMap<Integer,Integer> intSizeLiterals = 
    new VM_HashMap<Integer,Integer>();

  /**
   * Mapping from long like literals (longs and doubles) to the jtoc
   * slot that contains them.
   */
  private static final VM_HashMap<Long,Integer> longSizeLiterals = 
    new VM_HashMap<Long,Integer>();

  /**
   * Mapping from object literals to the jtoc slot that contains them.
   */
  private static final VM_HashMap<Object,Integer> objectLiterals = 
    new VM_HashMap<Object,Integer>();

  /**
   * A special mapping from VM_Atom objects to the jtoc slot of String
   * objects that represent the same value.
   */
  private static final VM_HashMap<VM_Atom,Integer> stringLiterals = 
    new VM_HashMap<VM_Atom,Integer>();

  /**
   * Conversion from JTOC slot index to JTOC offset.
   */
  @Uninterruptible
  public static final Offset slotAsOffset(int slot) { 
    return Offset.fromIntSignExtend((slot - middleOfTable) << LOG_BYTES_IN_INT);
  }

  /**
   * Conversion from JTOC offset to JTOC slot index.
   */
  @Uninterruptible
  public static final int offsetAsSlot(Offset offset) { 
    if (VM.VerifyAssertions) VM._assert((offset.toInt() & 3) == 0);
    return middleOfTable + (offset.toInt() >> LOG_BYTES_IN_INT);
  }

  /**
   * Return the lowest slot number in use
   */
  public static final int getLowestInUseSlot() {
    return nextNumericSlot+1;
  }

  /**
   * Return the highest slot number in use
   */
  public static final int getHighestInUseSlot() {
    return nextReferenceSlot - (VM.BuildFor32Addr ? 1 : 2);
  }

  /**
   * Find the given literal in the int like literal map, if not found
   * create a slot for the literal and place an entry in the map
   * @param literal the literal value to find or create
   * @return the offset in the JTOC of the literal
   */
  public static final int findOrCreateIntSizeLiteral(int literal) {
    Integer offsetAsInt;
    synchronized(intSizeLiterals) {
       offsetAsInt = intSizeLiterals.get(literal);
    }
    if (offsetAsInt != null) {
      return offsetAsInt.intValue();
    } else {
      Offset newOff = allocateNumericSlot(BYTES_IN_INT);
      synchronized(intSizeLiterals) {
        intSizeLiterals.put(literal, newOff.toInt());
      }
      setSlotContents(newOff, literal);
      return newOff.toInt();
    }
  }

  /**
   * Find the given literal in the long like literal map, if not found
   * create a slot for the literal and place an entry in the map
   * @param literal the literal value to find or create
   * @return the offset in the JTOC of the literal
   */
  public static final int findOrCreateLongSizeLiteral(long literal) {
    Integer offsetAsInt;
    synchronized(longSizeLiterals) {
       offsetAsInt = longSizeLiterals.get(literal);
    }
    if (offsetAsInt != null) {
      return offsetAsInt.intValue();
    } else {
      Offset newOff = allocateNumericSlot(BYTES_IN_LONG);
      synchronized(longSizeLiterals) {
        longSizeLiterals.put(literal, newOff.toInt());
      }
      setSlotContents(newOff, literal);
      return newOff.toInt();
    }
  }

  /**
   * Find or allocate a slot in the jtoc for a string literal from the
   * given VM_Atom. We register a mapping in the object and string
   * literals if not.
   * @param       literal value
   * @return offset of slot that was allocated
   * Side effect: literal value is stored into jtoc
   */ 
  public static int findOrCreateStringLiteral(VM_Atom literal) throws java.io.UTFDataFormatException {
    Integer offAsInt;
    synchronized (stringLiterals){
      offAsInt = stringLiterals.get(literal);
    }
    if (offAsInt != null) {
      return offAsInt.intValue();
    } else {
      String stringValue = literal.toUnicodeString();
      if (VM.runningVM) {
        stringValue = stringValue.intern();
      }
      Offset newOff = allocateReferenceSlot();
      synchronized(stringLiterals) {
        stringLiterals.put(literal, newOff.toInt());
        synchronized(objectLiterals) {
          objectLiterals.put(stringValue, newOff.toInt());
          setSlotContents(newOff, stringValue);
        }
      }
      return newOff.toInt();
    }
  }

 /**
   * Try to find a string literal from the given String object.
   * @param     literal value
   * @return    String literal if it exists, otherwise null.
   */ 
  public static String findStringLiteral(String literal) {
    Integer offAsInt;
    synchronized(objectLiterals) {
      offAsInt = objectLiterals.get(literal);
    }
    if (offAsInt != null) {
      Offset off = Offset.fromIntSignExtend(offAsInt.intValue());
      return (String)getSlotContentsAsObject(off);
    }
    return null;
  }

  /**
   * Find or allocate a slot in the jtoc for a class literal
   * @param typeReferenceID the type reference ID for the class
   * @return the offset of slot that was allocated
   */
  public static int findOrCreateClassLiteral(int typeReferenceID) {
    Class literalAsClass =
      VM_TypeReference.getTypeRef(typeReferenceID).resolve().getClassForType();
    Integer offAsInt;
    synchronized(objectLiterals) {
      offAsInt = objectLiterals.get(literalAsClass);
    }
    if (offAsInt != null) {
      return offAsInt.intValue();
    } else {
      Offset newOff = allocateReferenceSlot();
      synchronized(objectLiterals) {
        objectLiterals.put(literalAsClass, newOff.toInt());
        setSlotContents(newOff, literalAsClass);
      }
      return newOff.toInt();
    }
  }

  /**
   * Find or allocate a slot in the jtoc for an object literal.
   * @param       literal value
   * @return offset of slot that was allocated
   * Side effect: literal value is stored into jtoc
   */ 
  public static int findOrCreateObjectLiteral(Object literal) {
    Integer offAsInt;
    synchronized (objectLiterals){
      offAsInt = objectLiterals.get(literal);
    }
    if (offAsInt != null) {
      return offAsInt.intValue();
    } else {
      Offset newOff = allocateReferenceSlot();
      synchronized(objectLiterals) {
        objectLiterals.put(literal, newOff.toInt());
      }
      setSlotContents(newOff, literal);
      return newOff.toInt();
    }
  }

  /**
   * Find a slot in the jtoc with this object literal in else return 0
   * @param  literal value
   * @return offset containing literal or 0
   */ 
  public static int findObjectLiteral(Object literal) {
    Integer offAsInt;
    synchronized (objectLiterals){
      offAsInt = objectLiterals.get(literal);
    }
    if (offAsInt != null) {
      return offAsInt.intValue();
    } else {
      return 0;
    }
  }

  /**
   * Allocate a numeric slot in the jtoc.
   * @param size of slot
   * @return offset of slot that was allocated as int
   * (two slots are allocated for longs and doubles)
   */ 
  public static synchronized Offset allocateNumericSlot(int size) {
    // Allocate two slots for wide items after possibly blowing
    // another slot for alignment.  Wide things are longs or doubles
    if (size == BYTES_IN_LONG) {
      // widen for a wide
      nextNumericSlot--;
      // check alignment
      if((nextNumericSlot & 1) == 1) {
        nextNumericSlot--;
      }
    }
    int slot = nextNumericSlot;
    nextNumericSlot--;
    if (nextNumericSlot < 0) {
      enlargeTable();
    }
    return slotAsOffset(slot);
  }

  /**
   * Allocate a reference slot in the jtoc.
   * @return offset of slot that was allocated as int
   * (two slots are allocated on 64bit architectures)
   */ 
  public static synchronized Offset allocateReferenceSlot() {
    int slot = nextReferenceSlot;
    if(VM.BuildFor64Addr) {
      nextReferenceSlot += 2;
    } else {
      nextReferenceSlot++;
    }
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
    VM.sysFail("VM_Statics.enlargeTable: jtoc is full");    
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
    return slot > middleOfTable;
  }

  /**
   * Does specified jtoc slot contain an int sized literal?
   * @param  slot obtained from offsetAsSlot()
   * @return true --> slot contains a reference
   */ 
  public static boolean isIntSizeLiteral(int slot) {
    if(isReference(slot)) {
      return false;
    } else {
      int ival = getSlotContentsAsInt(slotAsOffset(slot));
      Integer offsetAsInt;
      synchronized(intSizeLiterals) {
         offsetAsInt = intSizeLiterals.get(ival);
      }
      if (offsetAsInt == null) {
        return false;
      } else {
        return slotAsOffset(slot).toInt() == offsetAsInt.intValue();
      }
    }
  }

  /**
   * Does specified jtoc slot contain a long sized literal?
   * @param  slot obtained from offsetAsSlot()
   * @return true --> slot contains a reference
   */ 
  public static boolean isLongSizeLiteral(int slot) {
    if(isReference(slot)) {
      return false;
    } else {
      long lval = getSlotContentsAsLong(slotAsOffset(slot));
      Integer offsetAsInt;
      synchronized(longSizeLiterals) {
         offsetAsInt = longSizeLiterals.get(lval);
      }
      if (offsetAsInt == null) {
        return false;
      } else {
        return slotAsOffset(slot).toInt() == offsetAsInt.intValue();
      }
    }
  }

  /**
   * Get size occupied by a reference
   */
  @Uninterruptible
  public static int getReferenceSlotSize () { 
      return VM.BuildFor64Addr ? 2 : 1;
  }

  /**
   * Fetch jtoc object (for JNI environment and GC).
   */ 
  @Uninterruptible
  public static Address getSlots() { 
    return VM_Magic.objectAsAddress(slots).plus(middleOfTable << LOG_BYTES_IN_INT);
  }

  /**
   * Fetch jtoc object (for JNI environment and GC).
   */ 
  @Uninterruptible
  public static int [] getSlotsAsIntArray() { 
    return slots;
  }

  /**
   * Fetch contents of a slot, as an integer
   */ 
  @Uninterruptible
  public static int getSlotContentsAsInt(Offset offset) { 
    if (VM.runningVM) {
      return VM_Magic.getIntAtOffset(slots, offset.plus(middleOfTable << LOG_BYTES_IN_INT));
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
      return VM_Magic.getLongAtOffset(slots, offset.plus(middleOfTable  << LOG_BYTES_IN_INT));
    } else {
      int slot = offsetAsSlot(offset);
      long result;
      if (VM.LittleEndian) {
        result = (((long) slots[slot+1]) << BITS_IN_INT); // hi
        result |= ((long) slots[slot]) & 0xFFFFFFFFL; // lo
      } else {
        result = (((long) slots[slot]) << BITS_IN_INT);     // hi
        result |= ((long) slots[slot+1]) & 0xFFFFFFFFL; // lo
      }
      return result;
    }
  }

  /**
   * Fetch contents of a slot, as an object.
   */ 
  @Uninterruptible
  public static Object getSlotContentsAsObject(Offset offset) { 
    if(VM.runningVM) {
      if (VM.BuildFor32Addr)
        return VM_Magic.addressAsObject(Address.fromIntSignExtend(getSlotContentsAsInt(offset)));
      else
        return VM_Magic.addressAsObject(Address.fromLong(getSlotContentsAsLong(offset)));
    } else {
      return objectSlots[offsetAsSlot(offset)];
    }
  }

  /**
   * Set contents of a slot, as an integer.
   */
  @Uninterruptible
  public static void setSlotContents(Offset offset, int value) { 
    if (VM.runningVM) {
      VM_Magic.setIntAtOffset(slots, offset.plus(middleOfTable << LOG_BYTES_IN_INT), value);
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
      VM_Magic.setLongAtOffset(slots, offset.plus(middleOfTable << LOG_BYTES_IN_INT), value);
    } else {
      int slot = offsetAsSlot(offset);
      if (VM.LittleEndian) {
        slots[slot + 1] = (int)(value >>> BITS_IN_INT); // hi
        slots[slot    ] = (int)(value       ); // lo
      } else {
        slots[slot    ] = (int)(value >>> BITS_IN_INT); // hi
        slots[slot + 1] = (int)(value       ); // lo
      }
    }
  }

  /**
   * Set contents of a slot, as an object.
   */ 
  @UninterruptibleNoWarn
  public static void setSlotContents(Offset offset, Object object) { 
	 // NB uninterruptible warnings are disabled for this method due to
	 // the array store which could cause a fault - this can't actually
	 // happen as the fault would only ever occur when not running the
	 // VM. We suppress the warning as we know the error can't happen.

    setSlotContents(offset, VM_Magic.objectAsAddress(object).toWord());
    if (VM.VerifyAssertions) VM._assert(offset.toInt() > 0);
    if (!VM.runningVM && objectSlots != null){
      // When creating the boot image objectSlots is populated as
      // VM_Magic won't work in the bootstrap JVM.
      objectSlots[offsetAsSlot(offset)] = object;
    }
  }

  /**
   * Set contents of a slot, as a Word.
   */ 
  @Uninterruptible
  public static void setSlotContents(Offset offset, Word word) { 
    if (VM.runningVM) {
      VM_Magic.setWordAtOffset(slots, offset.plus(middleOfTable << LOG_BYTES_IN_INT), word);
    } else {
      if (VM.BuildFor32Addr)
        setSlotContents(offset, word.toInt());
      else
        setSlotContents(offset, word.toLong());
    }
  }

  /**
   * Inform VM_Statics that boot image instantiation is over and that
   * unnecessary data structures, for runtime, can be released
   */
  public static void bootImageInstantiationFinished() {
    objectSlots = null;
  }

  /**
   * Search for a type that this TIB
   * @param tibOff offset of TIB in JTOC
   * @return type of TIB or null
   */
  public static VM_Type findTypeOfTIBSlot (Offset tibOff) {
    VM_Type[] types = VM_Type.getTypes();
    for (int i = 0; i < types.length; ++i) {
      if (types[i] != null && types[i].getTibOffset().EQ(tibOff)) 
        return types[i];
    }
    return null;
  }
}
