/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

/**
 * The static fields and methods comprising a running virtual machine image.
 *
 * <p> These fields and methods form the "root set" of all the objects in the
 * running virtual machine. They are stored in an array whose first element
 * is always pointed to by the virtual machine's "table of contents" (jtoc)
 * register. The slots of this array hold either primitives (byte, int,
 * long, float, etc), object pointers, or array pointers. A second
 * table, co-indexed with the array, describes the contents of each
 * slot in the jtoc.
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
 * <p>Here's a picture of what the corresponding jtoc and descriptive
 * table would look like in memory:
 *
 * <pre>
 *                     +---------------+
 * jtoc:               |   (header)    |
 *                     +---------------+
 * [jtoc register]-> 0:|      0        |
 *                     +---------------+       +---------------+
 *                   1:|     123       |       |   (header)    |
 *                     +---------------+       +---------------+
 *                   2:|  (objref)   --------->|    "abc"      |
 *                     +---------------+       +---------------+
 *                   3:|   4.56 (hi)   |
 *                     +---------------+
 *                   4:|   4.56 (lo)   |
 *                     +---------------+       +---------------+
 *                   5:|  (coderef)  -----+    |   (header)    |
 *                     +---------------+  |    +---------------+
 *                   6:|     ...       |  +--->|  machine code |
 *                     +---------------+       |    for "m"    |
 *                                             +---------------+
 *                     +--------------------+
 * descriptions:       |     (header)       |
 *                     +--------------------+
 *                   0:|      EMPTY         |  ( unused )
 *                     +--------------------+
 *                   1:|    NUMERIC_FIELD   |  ( A.i )
 *                     +--------------------+
 *                   2:|   REFERENCE_FIELD  |  ( B.s )
 *                     +--------------------+
 *                   3:| WIDE_NUMERIC_FIELD |  ( C.d )
 *                     +--------------------+
 *                     |     (unused)       |
 *                     +--------------------+
 *                   4:|      METHOD        |  ( D.m )
 *                     +--------------------+
 * </pre>
 *
 * @author Bowen Alpern
 * @author Derek Lieber
 */
  public class VM_Statics implements VM_Constants {
    //-----------//
    // interface //
    //-----------//

    // Kinds of statics that can appear in slots of the jtoc.
    //
    public static final byte REFERENCE_TAG        = 0x40;
    public static final byte WIDE_TAG             = 0x20;

    public static final byte EMPTY                = 0x0;

    public static final byte INT_LITERAL          = 0x01;
    public static final byte FLOAT_LITERAL        = 0x02;
    public static final byte LONG_LITERAL         = 0x03 | WIDE_TAG;
    public static final byte DOUBLE_LITERAL       = 0x04 | WIDE_TAG;
    public static final byte STRING_LITERAL       = 0x05 | REFERENCE_TAG;

    public static final byte NUMERIC_FIELD        = 0x06;
    public static final byte WIDE_NUMERIC_FIELD   = 0x07 | WIDE_TAG;
    public static final byte REFERENCE_FIELD      = 0x08 | REFERENCE_TAG;

    public static final byte METHOD               = 0x09 | REFERENCE_TAG;
    public static final byte TIB                  = 0x0a | REFERENCE_TAG;

    /**
     * Find or allocate a slot in the jtoc for an int literal.
     * @param       literal value (as bits)
     * @return    slot number that was allocated
     * Side effect: literal value is stored into jtoc
     */ 
    static int findOrCreateIntLiteral(int literal) {
      int id   = VM_IntLiteralDictionary.findOrCreateId(literal, nextSlot);
      int slot = VM_IntLiteralDictionary.getValue(id);
      if (slot == nextSlot)
      { // new literal
        allocateSlot(INT_LITERAL);
        slots[slot] = literal;
      }
      return slot;
    }

    /**
     * Find or allocate a slot in the jtoc for a float literal.
     * @param       literal value (as bits)
     * @return    slot number that was allocated
     * Side effect: literal value is stored into jtoc
     */ 
    static int findOrCreateFloatLiteral(int literal) {
      int id   = VM_FloatLiteralDictionary.findOrCreateId(literal, nextSlot);
      int slot = VM_FloatLiteralDictionary.getValue(id);
      if (slot == nextSlot)
      { // new literal
        allocateSlot(FLOAT_LITERAL);
        slots[slot] = literal;
      }
      return slot;
    }

    /**
     * Find or allocate a slot in the jtoc for a long literal.
     * @param       literal value (as bits)
     * @return    slot number of first of two slots that were allocated
     * Side effect: literal value is stored into jtoc
     */ 
    static int findOrCreateLongLiteral(long literal) {
      int id   = VM_LongLiteralDictionary.findOrCreateId(literal, nextSlot);
      int slot = VM_LongLiteralDictionary.getValue(id);
      if (slot == nextSlot)
      { // new literal
        allocateSlot(LONG_LITERAL);
        setSlotContents(slot, literal);
      }
      return slot;
    }

    /**
     * Find or allocate a slot in the jtoc for a double literal.
     * @param       literal value (as bits)
     * @return    slot number of first of two slots that were allocated
     * Side effect: literal value is stored into jtoc
     */ 
    static int findOrCreateDoubleLiteral(long literal) {
      int id   = VM_DoubleLiteralDictionary.findOrCreateId(literal, nextSlot);
      int slot = VM_DoubleLiteralDictionary.getValue(id);
      if (slot == nextSlot)
      { // new literal
        allocateSlot(DOUBLE_LITERAL);
        setSlotContents(slot, literal);
      }
      return slot;
    }

    /**
     * Find or allocate a slot in the jtoc for a string literal.
     * @param       literal value
     * @return    slot number that was allocated
     * Side effect: literal value is stored into jtoc
     */ 
    public static int findOrCreateStringLiteral(VM_Atom literal) throws java.io.UTFDataFormatException {
      int id   = VM_StringLiteralDictionary.findOrCreateId(literal, nextSlot);
      int slot = VM_StringLiteralDictionary.getValue(id);
      if (slot == nextSlot)
      { // new literal
        allocateSlot(STRING_LITERAL);
	VM_Address slotContent = VM_Magic.objectAsAddress(literal.toUnicodeString());
        slots[slot] = slotContent.toInt();
        if (VM.BuildForConcurrentGC && VM.runningVM) // enque increment for new ptr stored into jtoc (no decrement since a new entry)
        {
          //-#if RVM_WITH_CONCURRENT_GC // because VM_RCBuffers only available for concurrent memory managers
          VM_RCBuffers.addIncrement(slotContent, VM_Processor.getCurrentProcessor());
          //-#endif
        }
      }
      return slot;
    }

    /**
     * Allocate a slot in the jtoc.
     * @param    description of a static field or method (see "kinds", above)
     * @return slot number that was allocated 
     * (two slots are allocated for longs and doubles)
     */ 
    static int allocateSlot(byte description) {
      int slot = nextSlot;

      if (slot > descriptions.length - 2)
      {
        // !!TODO: enlarge slots[] and descriptions[], and modify jtoc register to
        // point to newly enlarged slots[]
        VM.sysFail("VM_Statics.allocateSlot: jtoc is full");
      }

      descriptions[slot] = description;
      if (VM.TraceStatics) VM.sysWrite("VM_Statics: allocated jtoc slot " + slot + " for " + getSlotDescriptionAsString(slot) + "\n");

      // allocate two slots for long or double
      //
      if ((description & WIDE_TAG) != 0)
        nextSlot += 2;
      else
        nextSlot += 1;

      return slot;
    }

    /**
     * Fetch number of jtoc slots currently allocated.
     */ 
    public static int getNumberOfSlots() throws VM_PragmaUninterruptible {
      return nextSlot;
    }

    /**
     * Fetch total number of slots comprising the jtoc.
     */ 
    public static int getTotalNumberOfSlots() throws VM_PragmaUninterruptible {
      return slots.length;
    }

    /**
     * Does specified jtoc slot contain a reference?
     * @param    slot number obtained from allocateSlot()
     * @return true --> slot contains a reference
     */ 
    public static boolean isReference(int slot) throws VM_PragmaUninterruptible {
      return (descriptions[slot] & VM_Statics.REFERENCE_TAG) != 0;
    }

    /**
     * Fetch description of specified jtoc slot.
     * @param    slot number obtained from allocateSlot()
     * @return description of slot contents (see "kinds", above)
     */
    public static byte getSlotDescription(int slot) throws VM_PragmaUninterruptible {
      return descriptions[slot];
    }

    /**
     * Fetch description of specified jtoc slot as a string.
     * @param    slot number obtained from allocateSlot()
     * @return description of slot contents (see "kinds", above)
     */ 
    public static String getSlotDescriptionAsString(int slot) {
      String kind = null;
      switch (getSlotDescription(slot))
      {
        case INT_LITERAL        : kind = "INT_LITERAL";        break;
        case FLOAT_LITERAL      : kind = "FLOAT_LITERAL";      break;
        case LONG_LITERAL       : kind = "LONG_LITERAL";       break;
        case DOUBLE_LITERAL     : kind = "DOUBLE_LITERAL";     break;
        case STRING_LITERAL     : kind = "STRING_LITERAL";     break;
        case NUMERIC_FIELD      : kind = "NUMERIC_FIELD";      break;
        case WIDE_NUMERIC_FIELD : kind = "WIDE_NUMERIC_FIELD"; break;
        case REFERENCE_FIELD    : kind = "REFERENCE_FIELD";    break;
        case METHOD             : kind = "METHOD";             break;
        case TIB                : kind = "TIB";                break;
        case EMPTY              : kind = "EMPTY SLOT";         break;
      }
      return kind;
    }

    /**
     * Fetch jtoc object (for JNI environment).
     */ 
    public static int[] getSlots() throws VM_PragmaUninterruptible {
      return slots;
    }

    /**
     * Fetch contents of a slot, as an integer
     */ 
    public static int getSlotContentsAsInt(int slot) throws VM_PragmaUninterruptible {
      return slots[slot];
    }

    /**
     * Fetch contents of a slot-pair, as a long integer.
     */ 
    public static long getSlotContentsAsLong(int slot) throws VM_PragmaUninterruptible {	
      //-#if RVM_FOR_IA32
      long result = (((long) slots[slot+1]) << 32); // hi
      result |= ((long) slots[slot]) & 0xFFFFFFFFL; // lo
      //-#else
      long result = (((long) slots[slot]) << 32);   // hi
      result |= ((long) slots[slot+1]) & 0xFFFFFFFFL; // lo
      //-#endif
      return result;
    }

    /**
     * Fetch contents of a slot, as an object.
     */ 
    public static Object getSlotContentsAsObject(int slot) throws VM_PragmaUninterruptible {
	return VM_Magic.addressAsObject(VM_Address.fromInt(slots[slot]));
    }

    /**
     * Fetch contents of a slot, as an object array.
     */ 
    public static Object[] getSlotContentsAsObjectArray(int slot) throws VM_PragmaUninterruptible {
      return VM_Magic.addressAsObjectArray(VM_Address.fromInt(slots[slot]));
    }

    /**
     * Set contents of a slot, as an integer.
     */
    public static void setSlotContents(int slot, int value) throws VM_PragmaUninterruptible {
      slots[slot] = value;
      if (VM.BuildForConcurrentGC && VM.runningVM && isReference(slot)) 
      {
        VM.sysWrite("WARNING - setSlotContents of int for reference slot, value = ");
        VM.sysWrite(value);
        VM.sysWrite("\n");
      }
    }

    /**
     * Set contents of a slot, as a long integer.
     */
    public static void setSlotContents(int slot, long value) throws VM_PragmaUninterruptible {
      //-#if RVM_FOR_IA32
      slots[slot + 1] = (int)(value >>> 32); // hi
      slots[slot    ] = (int)(value       ); // lo
      //-#else
      slots[slot    ] = (int)(value >>> 32); // hi
      slots[slot + 1] = (int)(value       ); // lo
      //-#endif
    }

    /**
     * Set contents of a slot, as an object.
     */ 
    static void setSlotContents(int slot, Object object) throws VM_PragmaUninterruptible {
      VM_Address newContent = VM_Magic.objectAsAddress(object);
      if (VM.BuildForConcurrentGC && VM.runningVM) 
      {
	VM_Address oldContent = VM_Address.fromInt(slots[slot]);
        slots[slot] = newContent.toInt();
        //-#if RVM_WITH_CONCURRENT_GC // because VM_RCBuffers only available for concurrent memory managers
        VM_RCBuffers.addIncrementAndDecrement(newContent, oldContent, VM_Processor.getCurrentProcessor());
        //-#endif
      }
      else
        slots[slot] = newContent.toInt();
    }

    /**
     * static data values (pointed to by jtoc register)
     */
    private static int  slots[];         
    /**
     * corresponding descriptions (see "kinds", above)
     */
    private static byte descriptions[];  
    /**
     * next available slot number
     */
    private static int  nextSlot;        
    /**
     * initial size of slots[] and descriptions[]
     */
    private static final int INITIAL_SLOTS = 32768; 

    static void init() {
      slots        = new int[INITIAL_SLOTS];
      descriptions = new byte[INITIAL_SLOTS];
      nextSlot     = 1; // slot 0 unused
    }

    /**
     * Hash VM_Dictionary keys.
     */ 
    static int dictionaryHash(int n) { return n; }
    /**
     * Hash VM_Dictionary keys.
     */ 
    static int dictionaryHash(long n) { return (int)n; }

    /**
     * Compare VM_Dictionary keys.
     */ 
    static int dictionaryCompare(long l, long r) { if (l == 0) return 0; if (l == r) return 1; return -1; }
    /**
     * Compare VM_Dictionary keys.
     */ 
    static int dictionaryCompare(int l, int r) { if (l == 0) return 0; if (l == r) return 1; return -1; }
  }
