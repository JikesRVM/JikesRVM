/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * @author David Bacon
 */
public abstract class VM_RCGC
    implements VM_Constants, VM_GCConstants, VM_Uninterruptible
{
    // TRACING

    public static final boolean trace1 = false;	// trace entire recursive cycle collection procedure
    public static final boolean traceBigCycles = false;	// print information about big cycles marked gray
    public static final int bigCycleCount = 1000; // How many elements makes a "big" cycle?
    public static final boolean traceArrayCycles = false; // print info on elements in cyclic arrays

    // OPTIONS

    public static final boolean cycleCollection  = true;    // Perform cycle collection?  Yes unless debugging.
    public static final boolean acyclicVmClasses = true;    // Mark VM_Class, VM_Thread, etc acyclic at boot time?

    // NOTE: The following variable is shadowed in VM_RCBuffers to avoid excess compilation dependencies.
    //  If you change referenceCountTIBs here you must change it in VM_RCBuffers as well.
    public static final boolean referenceCountTIBs = false; // Perform inc/dec for TIB objects?  Not for now.

    // REFCOUNT WORD DEFINITIONS

    public static final int RCBITS      = 32; 		// Size of reference count information field in bits
    public static final int BUFFERSHIFT = RCBITS-1; 	// First (and only) bit for BUFFERED flag
    public static final int COLORBITS   = 3;		// Six colors require 3 bits
    public static final int CCBITS      = COLORBITS+1;	// Cycle collection requires color and buffered flag
    public static final int COLORSHIFT  = RCBITS-CCBITS; // Base bit of color field

    public static final int BUFFERED  = 1<<BUFFERSHIFT;	// Bit 31: indicates reference to object has been buffered
    public static final int COLORMASK = 7<<COLORSHIFT;	// Bits 28-30: Mask for color of object

    // Color field: 3 bits
    //   Note that except while collecting, it is sufficient to test the green or purple bits, 
    //   and no masking is needed.
    public static final int RED    = 6 << COLORSHIFT;   // red:    not reference counted; part of boot image
    public static final int GREEN  = 4 << COLORSHIFT; 	// green:  acyclic
    public static final int PURPLE = 2 << COLORSHIFT; 	// purple: possible root of dead cycle
    public static final int BLACK  = 0 << COLORSHIFT; 	// black:  potentially cyclic, but currently live
    public static final int WHITE  = 3 << COLORSHIFT; 	// white:  member of dead cycle (used during collection only)
    public static final int GRAY   = 1 << COLORSHIFT; 	// gray:   currently being traced (collection only)
    public static final int ORANGE = 5 << COLORSHIFT; 	// orange: dead cycle not yet collected (async only)
    public static final int BLUE   = 7 << COLORSHIFT; 	// blue:   part of bogus cycle


    // Value used by BootImageWriter to mark boot image objects as not collectable.
    // NOTE: If you change this value or the values of RED or COLORSHIFT (or RCBITS) you must change
    //       BootImageWriter.java to keep the value it uses synchronized.
    public static final int BOOTIMAGE_REFCOUNT = RED|1; // colored red to prevent RC updates

    // COUNT FIELD(S)

    public static final boolean TWOCOUNTS = true;	// Store one or two reference counts?

    // Number of bits assigned to a reference count: either half or all of the remaining bits
    public static final int COUNTFLDBITS   = TWOCOUNTS ? (RCBITS-CCBITS)/2 : RCBITS-CCBITS;

    public static final int COUNTFLDMASK  = (1<<COUNTFLDBITS) - 1; // Bit mask for entire count field
    public static final int COUNTOVERFLOW = 1 << (COUNTFLDBITS-1); // High bit is an overflow bit (see hash table)
    public static final int COUNTBITS     = COUNTFLDBITS-1;        // Number of bits for actual count
    public static final int COUNTMASK     = (1<<COUNTBITS) - 1;    // Bit mask for reference count itself

    public static final int SHORT_COUNT_MAX = COUNTMASK;
    public static final int LONG_COUNT_DELTA = SHORT_COUNT_MAX+1;

    // When there are two counts, the extra count (XCOUNT) is stored shifted to the left of the primary count

    public static final int XCOUNTSHIFT    = TWOCOUNTS ? COUNTFLDBITS                 : 0;
    public static final int XCOUNTOVERFLOW = TWOCOUNTS ? COUNTOVERFLOW << XCOUNTSHIFT : 0;
    public static final int XCOUNTMASK     = TWOCOUNTS ? COUNTMASK     << XCOUNTSHIFT : 0;
    public static final int XCOUNTFLDMASK  = TWOCOUNTS ? COUNTFLDMASK  << XCOUNTSHIFT : 0;
    public static final int XCOUNTUNIT     = TWOCOUNTS ? 1             << XCOUNTSHIFT : 0;

    // STATISTICS

    static final boolean RCGC_COUNT_EVENTS = VM_Allocator.RC_COUNT_EVENTS;

    // Counts for each of the different colors
    static int setGreen;
    static int setBlack;
    static int setPurple;
    static int setBlue;
    static int setGray;
    static int setOrange;
    static int setWhite;
    static int setRed;

    // DEBUG

    public static final int EMPTYMASK = TWOCOUNTS ? 0 : ~ ( BUFFERED | COLORMASK | COUNTFLDMASK);

    private static final boolean DEBUG_BIGCOUNTS = false;

    public static void printFields() {
	VM.sysWrite("\nREFCOUNT FIELD MASK VALUES");
	VM.sysWrite("\nBUFFERED ");     VM.sysWrite(BUFFERED);
	VM.sysWrite("\nCOLORMASK ");    VM.sysWrite(COLORMASK);
	VM.sysWrite("\nCOUNTFLDMASK "); VM.sysWrite(COUNTFLDMASK);
	VM.sysWrite("\nCOUNTMASK ");    VM.sysWrite(COUNTMASK);
	VM.sysWrite("\nCOUNTOVERFLOW ");VM.sysWrite(COUNTOVERFLOW);
	VM.sysWrite("\nCOUNTBITS ");    VM.sysWrite(COUNTBITS);
	VM.sysWrite("\nEMPTYMASK ");    VM.sysWrite(EMPTYMASK);
	VM.sysWrite("\n\n");
    }


    // COUNT OVERFLOW HANDLING

    public static final boolean OVERFLOW_POSSIBLE   = COUNTBITS < 25;

    public static final int     OVERFLOW_TABLE_SIZE = 524287;
    public static final int     OVERFLOW_TABLE_ELTS = OVERFLOW_TABLE_SIZE << 1;

    private static final int    HASH_MASK    = 0x3ffffff; // mask out sign bit
    private static final int    HASH_EMPTY   = 0;
    private static final int    HASH_DELETED = 1;
    private static final int    HASH_ERROR   = -2;
    private static final int    DELTA_BASE   = 1; // the simplest relative prime
    private static final int    DELTA        = DELTA_BASE << 1;

    private static final int[] overflowTable = OVERFLOW_POSSIBLE ? new int[OVERFLOW_TABLE_ELTS] : null;

    private static final int[] cyclicOverflowTable = 
	OVERFLOW_POSSIBLE && TWOCOUNTS ? new int[OVERFLOW_TABLE_ELTS] : null;



    private static final int bigCountIndex(int object) {
	return ((VM_Magic.addressAsObject(object).hashCode() & HASH_MASK) % OVERFLOW_TABLE_SIZE) << 1;
    }

    //
    // Simple hashing via open addressing.  Deletions handled by leaving behind a HASH_DELETED marker
    //

    private static final int lookupEntry (int object, int table[]) {
	int index = bigCountIndex(object);
	int i = index;
	do {
	    if (table[i] == object)
		return i;	// found
	    if (table[i] == HASH_EMPTY)
		return HASH_ERROR; // not found
	    i = (i + DELTA) % OVERFLOW_TABLE_ELTS;
	} while (i != index);
	return HASH_ERROR;	// table full and not found
    }

    private static final int createEntry (int object, int table[]) {
	int index = bigCountIndex(object);
	int i = index;
	do {
	    if (table[i] == object)
		return HASH_ERROR; // found; not supposed to be there
	    if (table[i] == HASH_EMPTY || table[i] == HASH_DELETED) {
		table[i] = object;
		table[i+1] = 0;
		return i;	// found free slot
	    }
	    i = (i + DELTA) % OVERFLOW_TABLE_ELTS;
	} while (i != index);
	return HASH_ERROR;	// table full
    }


    // Add to an object's large reference count stored in a hash table.  If none stored yet, create one.
    //   If value is positive, returns true if new hash table entry created, false otherwise.
    //   If value is negative, returns true if hash table entry deleted, false otherwise.
    private static final boolean addToBigCount(int object, int value) {
	return addToBigCount(object, value, overflowTable);
    }

    private static final boolean addToBigCyclicCount(int object, int value) {
	return addToBigCount(object, value, cyclicOverflowTable);
    }

    private static final void setBigCyclicCount(int object, int value) {
	int i = createEntry(object, cyclicOverflowTable);
	cyclicOverflowTable[i+1] = value;
    }


    private static final void clearBigCyclicCount (int object) {
	int i = lookupEntry(object, cyclicOverflowTable);
	if (i != HASH_ERROR) {
	    cyclicOverflowTable[i]   = HASH_DELETED;
	    cyclicOverflowTable[i+1] = 0;
	}
    }	


    private static final boolean addToBigCount(int object, int value, int[] table) {
	if (VM.VerifyAssertions) VM.assert(OVERFLOW_POSSIBLE);

	if (DEBUG_BIGCOUNTS && color(object) != GREEN) dumpRefcountInfo("Non-green object getting big: ", object);

	int i = lookupEntry(object, table);
	if (i == HASH_ERROR) {
	    if (VM.VerifyAssertions) VM.assert(value > 0);
	    i = createEntry(object, table);
	    table[i+1] = value;
	    return true;	// signal new entry created
	}

	int count = table[i+1] + value;
	
	if (count == 0) {
	    table[i] = HASH_DELETED;
	    if (DEBUG_BIGCOUNTS) dumpRefcountInfo("Removing big refcount for ", object);
	    return true;	// indicate entry removed
	}

	table[i+1] = count;
	return false;		// signal nothing unusual happened
    }


    private static final int getBigCount(int object) {
	if (VM.VerifyAssertions) VM.assert(OVERFLOW_POSSIBLE);

	int i = lookupEntry(object, overflowTable);
	return overflowTable[i+1];
    }


    private static final int getBigCyclicCount(int object) {
	if (VM.VerifyAssertions) VM.assert(OVERFLOW_POSSIBLE);

	int i = lookupEntry(object, cyclicOverflowTable);
	return cyclicOverflowTable[i+1];
    }


    public static void dumpHashStats () {
	int elts = 0;
	int del = 0;
	int celts = 0;
	int cdel = 0;
	for (int i = 0; i < OVERFLOW_TABLE_SIZE; i++) {
	    if (overflowTable[i*2] != HASH_EMPTY && overflowTable[i*2] != HASH_DELETED)
		elts++;
	    if (overflowTable[i*2] == HASH_DELETED)
		del++;
	    if (cyclicOverflowTable[i*2] != HASH_EMPTY && cyclicOverflowTable[i*2] != HASH_DELETED)
		celts++;
	    if (cyclicOverflowTable[i*2] == HASH_DELETED)
		cdel++;
	}

	println();
	print("Hashtable contains:        ", elts);  percentage(elts,  OVERFLOW_TABLE_SIZE, "table size");
	print("Cyclic Hashtable contains: ", celts); percentage(celts, OVERFLOW_TABLE_SIZE, "table size");
	print("Hashtable deleted entries: ", del);   percentage(del,   OVERFLOW_TABLE_SIZE, "table size");
	print("Cyclic Hashtable deleted:  ", celts); percentage(cdel,  OVERFLOW_TABLE_SIZE, "table size");
    }

    public static void dumpRefcountInfo (String message, int object) {
	VM.sysWrite("^^^^ ");  VM.sysWrite(message);  VM.sysWrite(object);
	VM.sysWrite("\n^^^^    refcount ");  VM.sysWrite(refcount(object));
	VM.sysWrite(" ["); 
	VM.sysWrite(isBuffered(object) ? "@" : "~");  VM.sysWrite(",");
	VM.sysWrite(colorName(object));
	if (TWOCOUNTS) { VM.sysWrite(","); VM.sysWrite((refcount(object) & XCOUNTFLDMASK) >> XCOUNTSHIFT, false); }
	VM.sysWrite(","); VM.sysWrite(refcount(object) & COUNTFLDMASK, false);
	VM.sysWrite("] type ");  
	VM_Allocator.printType(object);
	int x = 0;		// breakpoint
    }

    public static String colorName(int object) {
	int c = color(object);

	if (c == BLACK)  return "B";
	if (c == GREEN)  return "G";
	if (c == RED)    return "R";
	if (c == PURPLE) return "P";
	if (c == GRAY)   return "g";
	if (c == WHITE)  return "w";
	if (c == ORANGE) return "o";
	if (c == BLUE)   return "b";

	if (VM.VerifyAssertions) VM.assert(false);
	return "X";
    }


    // HIGH-LEVEL REFCOUNT ACCESSOR FUNCTIONS

    public static final int referenceCount(int object) {
	int rc = refcount(object);
	int count = rc & COUNTMASK;
	if ((rc & COUNTOVERFLOW) != 0)
	    return count + getBigCount(object);
	else
	    return count;
    }

    public static final boolean isZeroReferenceCount(int object) {
	return (refcount(object) & COUNTFLDMASK) == 0;
    }

    public static final boolean isGreaterThanOneReferenceCount(int object) {
	return (refcount(object) & COUNTFLDMASK) > 1;
    }

    public static final int color(int object) {
	return refcount(object) & COLORMASK;
    }

    public static final boolean isBuffered(int object) {
	return (refcount(object) & BUFFERED) != 0;
    }

    // HIGH-LEVEL REFCOUNT MODIFIER FUNCTIONS

    public static final void initializeReferenceCount(int object) {
	setRefcount(object, 0);	// count = 0; color = black; unbuffered
    }

    public static final void setReferenceCount(int object, int count) {
	setRefcount(object, (refcount(object) & ~ COUNTMASK) | count);
    }
	
    public static final void incReferenceCount(int object) {
	int rc = refcount(object);
	int count = rc & COUNTMASK;

	if (OVERFLOW_POSSIBLE && count == SHORT_COUNT_MAX) {
	    addToBigCount(object, LONG_COUNT_DELTA);
	    rc = (rc & ~ COUNTFLDMASK) | COUNTOVERFLOW;
	}
	else 
	    rc = ((rc & ~ BUFFERED) + 1) | (rc & BUFFERED); // rc = rc + 1, unsigned
	    
	setRefcount(object, rc);
    }
	
    // returns true if rc goes to 0
    public static final boolean decReferenceCount(int object) {
	int rc = refcount(object);
	int count = rc & COUNTMASK;
	int buffered = rc & BUFFERED;

	if (VM.VerifyAssertions && (rc & COUNTFLDMASK) == 0) {
	    dumpRefcountInfo("Trying to decrement 0 count ", object);
	}

	if (VM.VerifyAssertions) VM.assert((rc & COUNTFLDMASK) != 0); // can't dec a zero count

	if (OVERFLOW_POSSIBLE && count == 0) {
	    boolean notLong = addToBigCount(object, -LONG_COUNT_DELTA);
	    rc = rc | COUNTMASK;
	    if (notLong)
		rc = rc & ~ COUNTOVERFLOW;
	}
	else 
	    rc = ((rc & ~ BUFFERED) - 1) | (rc & BUFFERED); // rc = rc - 1, unsigned

	if (VM.VerifyAssertions) VM.assert((rc & BUFFERED) == buffered);

	setRefcount(object, rc);

	return (rc & COUNTFLDMASK) == 0;
    }
	
    public static final void setColor(int object, int color) {
	if (RCGC_COUNT_EVENTS) {
	    switch (color) {
	    case GREEN: setGreen++;  break;
	    case BLACK: setBlack++;  break;
	    case BLUE:  setBlue++;   break;
	    case GRAY:  setGray++;   break;
	    case ORANGE:setOrange++; break;
	    case WHITE: setWhite++;  break;
	    case PURPLE:setPurple++; break;
	    case RED:   setRed++;    break;
	    }
	}
	setRefcount(object, (refcount(object) & ~ COLORMASK) | color);
    }
	
    public static final void setBufferedFlag(int object) {
	setRefcount(object, refcount(object) | BUFFERED);
    }

    public static final void clearBufferedFlag(int object) {
	setRefcount(object, refcount(object) & ~BUFFERED);
    }

    // LOW-LEVEL FUNCTIONS

    public static final int refcount(int object) { 
	return VM_Magic.getMemoryWord(object + VM_AllocatorHeader.REFCOUNT_OFFSET);
    }

    public static final void setRefcount(int object, int value) { 
	if (VM.VerifyAssertions) VM.assert((value & EMPTYMASK) == 0);

	VM_Magic.setMemoryWord(object + VM_AllocatorHeader.REFCOUNT_OFFSET, value);
    }


    // XCOUNT (SECONDARY COUNT) FUNCTIONS (used by asynchronous cycle collector)

    public static final void cloneCount (int object) {
	int rc = refcount(object);
	int cloned = (rc & ~ XCOUNTFLDMASK) | ((rc & COUNTFLDMASK) << COUNTFLDBITS);

	if ((rc & COUNTOVERFLOW) != 0) 
	    setBigCyclicCount(object, getBigCount(object));

	setRefcount(object, cloned);

	if (VM.VerifyAssertions) VM.assert(cyclicReferenceCount(object) == referenceCount(object));
    }

    public static final boolean isZeroCyclicReferenceCount (int object) {
	return (refcount(object) & XCOUNTFLDMASK) == 0;
    }

    public static final void decCyclicReferenceCount (int object) {
	int rc    = refcount(object);
	int count = rc & XCOUNTMASK;

	if (count == 0) {
	    if (VM.VerifyAssertions) VM.assert((rc & XCOUNTOVERFLOW) != 0);
	    boolean notLong = addToBigCyclicCount(object, -LONG_COUNT_DELTA);
	    rc = rc | XCOUNTMASK;
	    if (notLong)
		rc = rc & ~ XCOUNTOVERFLOW;
	}
	else 
	    rc = ((rc & ~ BUFFERED) - XCOUNTUNIT) | (rc & BUFFERED); // rc = andrc - XCOUNTUNIT, unsigned

	setRefcount(object, rc);
    }

    public static final int cyclicReferenceCount (int object) {
	int rc = refcount(object);
	int count = (rc & XCOUNTMASK) >> XCOUNTSHIFT;
	if ((rc & XCOUNTOVERFLOW) != 0)
	    return count + getBigCyclicCount(object);
	else
	    return count;
    }

    static final void clearCyclicReferenceCount(int object) {
	int rc = refcount(object);
	if ((rc & XCOUNTOVERFLOW) != 0)
	    clearBigCyclicCount(object);
	rc = rc & ~ XCOUNTFLDMASK;
	setRefcount(object, rc);
    }

    static final void setCyclicReferenceCount (int object, int value) {
	if (VM.VerifyAssertions) VM.assert(value <= COUNTMASK && value >= 0);
	int rc = refcount(object);
	if ((rc & XCOUNTOVERFLOW) != 0)
	    clearBigCyclicCount(object);
	rc &= ~XCOUNTFLDMASK;
	rc |= value << XCOUNTSHIFT;
	setRefcount(object, rc);
    }

    ///////////////////////////////////////////////////////////////////////////////
    // OUTPUT FUNCTIONS 
    ///////////////////////////////////////////////////////////////////////////////

    static final void println() { VM.sysWrite("\n"); }
    static final void print(String s) { VM.sysWrite(s); }
    static final void println(String s) { print(s); println(); }
    static final void print(int i) { VM.sysWrite(i, false); }
    static final void println(int i) { print(i); println(); }
    static final void print(String s, int i) { print(s); print(i); }
    static final void println(String s, int i) { print(s,i); println(); }

    static void percentage (int numerator, int denominator, String quantity) {
	print("\t");
	if (denominator > 0) 
	    print((int) ((((double) numerator) * 100.0) / ((double) denominator)));
	else
	    print("0");
	print("% of ");
	println(quantity);
    }

    static void printStatistics () {
	int setColors = setBlack + setPurple + setOrange + setWhite + setRed + setBlue + setGray;

	print("Colorings:        ", setColors);  println();

	print("  Colored black:  ", setBlack);   percentage(setBlack,  setColors, "colorings");
	print("  Colored purple: ", setPurple);  percentage(setPurple, setColors, "colorings");
	print("  Colored blue:   ", setBlue);    percentage(setBlue,   setColors, "colorings");
	print("  Colored gray:   ", setGray);    percentage(setGray,   setColors, "colorings");
	print("  Colored orange: ", setOrange);  percentage(setOrange, setColors, "colorings");
	print("  Colored white:  ", setWhite);   percentage(setWhite,  setColors, "colorings");

	// Don't print this, because these aren't re-colorings, just initializations
	// print("Colored green:  ", setGreen);   percentage(setGreen, setColors, "colorings");

	if (VM.VerifyAssertions) VM.assert(setRed == 0);
	// print("Colored red:    ", setRed);     percentage(setRed, setColors, "colorings");
    }
}
