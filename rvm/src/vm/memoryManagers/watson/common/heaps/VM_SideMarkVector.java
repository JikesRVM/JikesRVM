/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$


package com.ibm.JikesRVM.memoryManagers.watson;

import com.ibm.JikesRVM.VM_Constants;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Memory;
import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Array;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;

/**
 * Provide a side-array of mark bits for the garbage collector, for cases
 * when it is not possible or desirable to store them directly in the objects.
 *
 * @author David Bacon
 */
final class VM_SideMarkVector implements VM_Constants {

  private VM_Address baseAddress;
  private VM_Address highAddress;
  private int[] marks;

  static final int LOG_INT_SIZE = 5;
  static final int BITS_PER_INT = 32;
  static final int LOG_WORDSIZE = 2;
  static final int ALIGNMENT    = 4; // all objects aligned on 4-byte boundaries

  private static final boolean DEBUG = false;

  public void boot(VM_MallocHeap mh,
		   VM_Address base,
		   VM_Address high) throws VM_PragmaUninterruptible {
    baseAddress = base;
    highAddress = high;
    int bytes   = high.diff(base).toInt();
    int quanta  = bytes / ALIGNMENT;
    marks       = (int[])mh.allocateArray(VM_Array.arrayOfIntType, quanta / BITS_PER_INT + 1);
  }

  private int wordIndex (Object object) throws VM_PragmaUninterruptible {
    int index = ((VM_Magic.objectAsAddress(object).diff(baseAddress).toInt()) / ALIGNMENT) / BITS_PER_INT;
    if (VM.VerifyAssertions) VM._assert(index >= 0 && index < marks.length);
    return index;
  }


  private int bitIndex (Object object, int wordIndex) throws VM_PragmaUninterruptible {
    int index = ((VM_Magic.objectAsAddress(object).diff(baseAddress).toInt()) / ALIGNMENT) % BITS_PER_INT;
    if (DEBUG) {
      VM.sysWrite(" {Length ", marks.length);
      VM.sysWrite(" base ", baseAddress);
      VM.sysWrite(" high ", highAddress);
      VM.sysWrite(";   Object ", VM_Magic.objectAsAddress(object));
      VM.sysWrite(" word ", wordIndex);
      VM.sysWrite(" bit ", index, "} ");
    }
    if (VM.VerifyAssertions) VM._assert(index >= 0 && index < BITS_PER_INT);
    return index;
  }


  private int mask (int bitIndex) throws VM_PragmaUninterruptible {
    return ~(1 << bitIndex);
  }


  private int getBit (int word, int bitIndex) throws VM_PragmaUninterruptible {
    return (word >>> bitIndex) & 0x1;
  }


  /**
   * Test to see if the mark bit has the given value
   */
  boolean testMarkBit (Object object, int value) throws VM_PragmaUninterruptible {
    if (DEBUG) VM.sysWriteln("testMarkBit ", VM_Magic.objectAsAddress(object));
    int word   = wordIndex(object);
    int bitnum = bitIndex(object, word);
    int bit    = getBit(marks[word], bitnum);
    return (bit & value) != 0;
  }
  

  /**
   * Write the given value in the mark bit.
   */
  void writeMarkBit (Object object, int b) throws VM_PragmaUninterruptible {
    if (VM.VerifyAssertions) VM._assert((b & ~0x1) == 0);
    if (DEBUG) VM.sysWriteln("writeMarkBit ", VM_Magic.objectAsAddress(object));
    int word   = wordIndex(object);
    int bitnum = bitIndex(object, word);
    int mask   = mask(bitnum);
    int newval = b << bitnum;
    marks[word] = (marks[word] & mask) | newval;
  }


  /**
   * Atomically write the given value in the mark bit.
   */
  void atomicWriteMarkBit(Object object, int value) throws VM_PragmaUninterruptible {
    if (VM.VerifyAssertions) VM._assert((value & ~0x1) == 0);
    if (DEBUG) VM.sysWriteln("atomicWriteMarkBit ", VM_Magic.objectAsAddress(object));
    int word   = wordIndex(object);
    int offset = word << LOG_WORDSIZE;
    int bitnum = bitIndex(object, word);
    int mask   = mask(bitnum);
    int newval = value << bitnum;

    int oldValue;
    int newValue;
    do {
      oldValue = VM_Magic.prepare(marks, offset);
      newValue = (oldValue & mask) | newval;
    } while (! VM_Magic.attempt(marks, offset, oldValue, newValue));
  }


  /**
   * Used to mark objects during a parallel scan of objects during GC.
   */
  boolean testAndMark(Object object, int value) throws VM_PragmaUninterruptible {
    if (VM.VerifyAssertions) VM._assert((value & ~0x1) == 0);
    if (DEBUG) VM.sysWrite("testAndMark ", VM_Magic.objectAsAddress(object));
    int word   = wordIndex(object);
    int offset = word << LOG_WORDSIZE;
    int bitnum = bitIndex(object, word);
    int bitval = 1 << bitnum;

    int oldValue;
    int newValue;
    do {
      oldValue = VM_Magic.prepare(marks, offset);
      int markBit = getBit(oldValue, bitnum);
      if (markBit == value) {
	if (DEBUG) VM.sysWriteln(" [false]");
	return false;
      }
      newValue = oldValue ^ bitval;
    } while (! VM_Magic.attempt(marks, offset, oldValue, newValue));

    if (DEBUG) VM.sysWriteln(" [true]");
    return true;
  }
}
