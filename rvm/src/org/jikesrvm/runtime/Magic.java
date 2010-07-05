/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.runtime;

import org.jikesrvm.ArchitectureSpecific.CodeArray;
import org.jikesrvm.ArchitectureSpecific.Registers;
import org.jikesrvm.VM;
import org.jikesrvm.classloader.RVMType;
import org.jikesrvm.mm.mminterface.CollectorThread;
import org.jikesrvm.objectmodel.TIB;
import org.jikesrvm.scheduler.RVMThread;
import org.vmmagic.Intrinsic;
import org.vmmagic.pragma.Entrypoint;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Extent;
import org.vmmagic.unboxed.Offset;
import org.vmmagic.unboxed.Word;
import org.vmmagic.unboxed.WordArray;

/**
 * Magic methods for accessing raw machine memory, registers, and
 * operating system calls.
 *
 * <p> These are "inline assembler functions" that cannot be implemented in
 * Java code. Their names are recognized by RVM's compilers
 * and cause inline machine code to be generated instead of
 * actual method calls.
 */
@SuppressWarnings({"UnusedDeclaration"})
@Intrinsic
public final class Magic {

  //---------------------------------------//
  // Register and Psuedo-Register Access.  //
  //---------------------------------------//

  /** Get contents of "stack frame pointer" register. */
  public static Address getFramePointer() {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return null;
  }

  /** Get contents of "jtoc" register. */
  public static Address getTocPointer() {
    if (VM.runningVM && VM.VerifyAssertions) {
      VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    }
    return BootRecord.the_boot_record.tocRegister;
  }

  /** Get contents of "jtoc" register */
  public static Address getJTOC() {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return null;
  }

  /** Get contents of "thread" register. */
  public static RVMThread getThreadRegister() {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return null;
  }

  /** Set contents of "thread" register. */
  public static void setThreadRegister(RVMThread p) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
  }

  /** Get contents of ESI, as a RVMThread. NOTE: IA-specific */
  public static RVMThread getESIAsThread() {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return null;
  }

  /** Set contents of ESI to hold a reference to a thread object. NOTE: IA-specific */
  public static void setESIAsThread(RVMThread p) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
  }

  /**
   * Read contents of hardware time base registers.
   * Note:     we think that 1 "tick" == 4 "machine cycles", but this seems to be
   *           undocumented and may vary across processor implementations.
   * @return number of ticks (epoch undefined)
   */
  public static long getTimeBase() {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return -1;
  }

  //---------------------------------------//
  //       Stackframe Manipulation         //
  //---------------------------------------//

  /**
   * Get fp for parent frame
   * @param fp frame pointer for child frame
   */
  public static Address getCallerFramePointer(Address fp) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return null;
  }

  /**
   * Set fp for parent frame
   * @param fp frame pointer for child frame
   * @param newCallerFP new value for caller frame pointer
   */
  public static void setCallerFramePointer(Address fp, Address newCallerFP) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
  }

  /**
   * Get Compiled Method ID for a frame
   * @param fp its frame pointer).
   */
  public static int getCompiledMethodID(Address fp) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return -1;
  }

  /**
   * Set the Compiled Method ID for a frame.
   * @param fp its frame pointer
   * @param newCMID a new cmid for the frame
   */
  public static void setCompiledMethodID(Address fp, int newCMID) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
  }

  /**
   * Get next instruction address for a frame
   * @param fp its frame pointer.
   */
  public static Address getNextInstructionAddress(Address fp) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return null;
  }

  /**
   * Get location containing return address for a frame
   * @param fp its frame pointer
   */
  public static Address getReturnAddressLocation(Address fp) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return null;
  }

  /**
   * Get return address for a frame
   * @param fp its frame pointer
   */
  @Uninterruptible
  public static Address getReturnAddress(Address fp) {
    return getReturnAddressLocation(fp).loadAddress();
  }

  /**
   * Get return address for a frame
   * @param fp its frame pointer
   */
  @Uninterruptible
  public static void setReturnAddress(Address fp, Address v) {
    getReturnAddressLocation(fp).store(v);
  }

  //---------------------------------------//
  //           Memory Access.              //
  //---------------------------------------//

  /**
   * Get unsigned byte at arbitrary (byte) offset from object. The
   * most significant 24bits of the result will be 0.
   */
  public static byte getUnsignedByteAtOffset(Object object, Offset offset) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return -1;
  }

  /**
   * Get byte at arbitrary (byte) offset from object. The most
   * significant 24bits of the result will be the same as the most
   * significant bit in the byte.
   */
  public static byte getByteAtOffset(Object object, Offset offset) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return -1;
  }

  /**
   * Get char at arbitrary (byte) offset from object. The most
   * significant 16bits will be 0.
   */
  public static char getCharAtOffset(Object object, Offset offset) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return (char) -1;
  }

  /**
   * Get short at arbitrary (byte) offset from object. The most
   * significant 16bits will be the same as the most significant bit
   * in the short.
   */
  public static short getShortAtOffset(Object object, Offset offset) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return (short) -1;
  }

  /**
   * Get int at arbitrary (byte) offset from object.
   * Use getIntAtOffset(obj, ofs) instead of getMemoryInt(objectAsAddress(obj)+ofs)
   */
  public static int getIntAtOffset(Object object, Offset offset) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return -1;
  }

  /**
   * Get long at arbitrary (byte) offset from object.
   * Use getlongAtOffset(obj, ofs) instead of two getIntAtOffset
   */
  public static long getLongAtOffset(Object object, Offset offset) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return -1;
  }

  /**
   * Get float at arbitrary (byte) offset from object.
   */
  public static float getFloatAtOffset(Object object, Offset offset) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return -1;
  }

  /**
   * Get double at arbitrary (byte) offset from object.
   * Use getDoubleAtOffset(obj, ofs) instead of two getIntAtOffset
   */
  public static double getDoubleAtOffset(Object object, Offset offset) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return -1;
  }

  /**
   * Get Object at arbitrary (byte) offset from object.
   * Use getObjectAtOffset(obj, ofs) instead of
   * addressAsObject(getMemoryAddress(objectAsAddress(obj)+ofs))
   */
  public static Object getObjectAtOffset(Object object, Offset offset) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return null;
  }

  /**
   * Get Object at arbitrary (byte) offset from object.
   * Use getObjectAtOffset(obj, ofs) instead of
   * addressAsObject(getMemoryAddress(objectAsAddress(obj)+ofs))
   */
  public static Object getObjectAtOffset(Object object, Offset offset, int locationMetadata) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return null;
  }

  /**
   * Get Word at arbitrary (byte) offset from object.
   * Use getWordAtOffset(obj, ofs) instead of getMemoryWord(objectAsAddress(obj)+ofs)
   */
  public static Word getWordAtOffset(Object object, Offset offset) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return Word.max();
  }

  /**
   * Get Word at arbitrary (byte) offset from object.
   */
  public static Word getWordAtOffset(Object object, Offset offset, int locationMetadata) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return null;
  }

  /**
   * Get Address at arbitrary (byte) offset from object.
   * Use getAddressAtOffset(obj, ofs) instead of getMemoryWord(objectAsAddress(obj)+ofs)
   */
  public static Address getAddressAtOffset(Object object, Offset offset) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return null;
  }

  /**
   * Get Address at arbitrary (byte) offset from object.
   */
  public static Address getAddressAtOffset(Object object, Offset offset, int locationMetadata) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return null;
  }

  /**
   * Get Extent at arbitrary (byte) offset from object.
   * Use getExtentAtOffset(obj, ofs) instead of getMemoryWord(objectAsAddress(obj)+ofs)
   */
  public static Extent getExtentAtOffset(Object object, Offset offset) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return null;
  }

  /**
   * Get Extent at arbitrary (byte) offset from object.
   */
  public static Extent getExtentAtOffset(Object object, Offset offset, int locationMetadata) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return null;
  }

  /**
   * Get Offset at arbitrary (byte) offset from object.
   * Use getOffsetAtOffset(obj, ofs) instead of getMemoryWord(objectAsAddress(obj)+ofs)
   */
  public static Offset getOffsetAtOffset(Object object, Offset offset) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return null;
  }

  /**
   * Get Offset at arbitrary (byte) offset from object.
   */
  public static Offset getOffsetAtOffset(Object object, Offset offset, int locationMetadata) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return null;
  }

  /**
   * Get TIB at arbitrary (byte) offset from object.
   * Use getTIBAtOffset(obj, ofs) instead of
   * (TIB])addressAsObject(getMemoryAddr(objectAsAddress(obj)+ofs))
   */
  public static TIB getTIBAtOffset(Object object, Offset offset) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return null;
  }

  /**
   * Set boolean at arbitrary (byte) offset from object.
   */
  public static void setBooleanAtOffset(Object object, Offset offset, boolean newvalue) {
    if (VM.VerifyAssertions)
      VM._assert(VM.NOT_REACHED); // call site should have been hijacked by magic in compiler
  }

  /**
   * Set boolean at arbitrary (byte) offset from object.
   */
  public static void setBooleanAtOffset(Object object, Offset offset, boolean newvalue, int locationMetadata) {
    if (VM.VerifyAssertions)
      VM._assert(VM.NOT_REACHED); // call site should have been hijacked by magic in compiler
  }

  /**
   * Set byte at arbitrary (byte) offset from object.
   */
  public static void setByteAtOffset(Object object, Offset offset, byte newvalue) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
  }

  /**
   * Set byte at arbitrary (byte) offset from object.
   */
  public static void setByteAtOffset(Object object, Offset offset, byte newvalue, int locationMetadata) {
    if (VM.VerifyAssertions)
      VM._assert(VM.NOT_REACHED); // call site should have been hijacked by magic in compiler
  }

  /**
   * Set char at arbitrary (byte) offset from object.
   */
  public static void setCharAtOffset(Object object, Offset offset, char newvalue) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
  }

  /**
   * Set char at arbitrary (byte) offset from object.
   */
  public static void setCharAtOffset(Object object, Offset offset, char newvalue, int locationMetadata) {
    if (VM.VerifyAssertions)
      VM._assert(VM.NOT_REACHED); // call site should have been hijacked by magic in compiler
  }

  /**
   * Set short at arbitrary (byte) offset from object.
   */
  public static void setShortAtOffset(Object object, Offset offset, short newvalue) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
  }

  /**
   * Set short at arbitrary (byte) offset from object.
   */
  public static void setShortAtOffset(Object object, Offset offset, short newvalue, int locationMetadata) {
    if (VM.VerifyAssertions)
      VM._assert(VM.NOT_REACHED); // call site should have been hijacked by magic in compiler
  }

  /**
   * Set int at arbitrary (byte) offset from object.
   * Use setIntAtOffset(obj, ofs, new) instead of setMemoryWord(objectAsAddress(obj)+ofs, new)
   */
  public static void setIntAtOffset(Object object, Offset offset, int newvalue) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
  }

  /**
   * Set int at arbitrary (byte) offset from object.
   */
  public static void setIntAtOffset(Object object, Offset offset, int newvalue, int locationMetadata) {
    if (VM.VerifyAssertions)
      VM._assert(VM.NOT_REACHED); // call site should have been hijacked by magic in compiler
  }

  /**
   * Set long at arbitrary (byte) offset from object.
   * Use setlongAtOffset(obj, ofs) instead of two setIntAtOffset
   */
  public static void setLongAtOffset(Object object, Offset offset, long newvalue) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
  }

  /**
   * Set long at arbitrary (byte) offset from object. Use setlongAtOffset(obj,
   * ofs) instead of two setIntAtOffset
   */
  public static void setLongAtOffset(Object object, Offset offset, long newvalue, int locationMetadata) {
    if (VM.VerifyAssertions)
      VM._assert(VM.NOT_REACHED); // call site should have been hijacked by magic in compiler
  }

  /**
   * Set float at arbitrary (byte) offset from object.
   */
  public static void setFloatAtOffset(Object object, Offset offset, float newvalue) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
  }

  /**
   * Set float at arbitrary (byte) offset from object.
   */
  public static void setFloatAtOffset(Object object, Offset offset, float newvalue, int locationMetadata) {
    if (VM.VerifyAssertions)
      VM._assert(VM.NOT_REACHED); // call site should have been hijacked by magic in compiler
  }

  /**
   * Set double at arbitrary (byte) offset from object.
   * Use setDoubleAtOffset(obj, ofs) instead of two setIntAtOffset
   */
  public static void setDoubleAtOffset(Object object, Offset offset, double newvalue) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
  }

  /**
   * Set double at arbitrary (byte) offset from object. Use
   * setDoubleAtOffset(obj, ofs) instead of two setIntAtOffset
   */
  public static void setDoubleAtOffset(Object object, Offset offset, double newvalue, int locationMetadata) {
    if (VM.VerifyAssertions)
      VM._assert(VM.NOT_REACHED); // call site should have been hijacked by magic in compiler
  }

  /**
   * Set Word at arbitrary (byte) offset from object.
   * Use setWordAtOffset(obj, ofs, new) instead of setMemoryWord(objectAsAddress(obj)+ofs, new)
   */
  public static void setWordAtOffset(Object object, Offset offset, Word newvalue) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
  }

  /**
   * Set Word at arbitrary (byte) offset from object.
   * Use setWordAtOffset(obj, ofs, new) instead of setMemoryWord(objectAsAddress(obj)+ofs, new)
   */
  public static void setWordAtOffset(Object object, Offset offset, Word newvalue, int locationMetadata) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
  }

  /**
   * Set Address at arbitrary (byte) offset from object.
   * Use setAddressAtOffset(obj, ofs, new) instead of setMemoryWord(objectAsAddress(obj)+ofs, new)
   */
  public static void setAddressAtOffset(Object object, Offset offset, Address newvalue) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
  }

  /**
   * Set Address at arbitrary (byte) offset from object.
   * Use setAddressAtOffset(obj, ofs, new) instead of setMemoryWord(objectAsAddress(obj)+ofs, new)
   */
  public static void setAddressAtOffset(Object object, Offset offset, Address newvalue, int locationMetadata) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
  }

  /**
   * Set Extent at arbitrary (byte) offset from object.
   * Use setExtentAtOffset(obj, ofs, new) instead of setMemoryWord(objectAsAddress(obj)+ofs, new)
   */
  public static void setExtentAtOffset(Object object, Offset offset, Extent newvalue) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
  }

  /**
   * Set Extent at arbitrary (byte) offset from object.
   * Use setExtenttOffset(obj, ofs, new) instead of setMemoryWord(objectAsAddress(obj)+ofs, new)
   */
  public static void setExtentAtOffset(Object object, Offset offset, Extent newvalue, int locationMetadata) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
  }

  /**
   * Set Offset at arbitrary (byte) offset from object.
   * Use setOffsetAtOffset(obj, ofs, new) instead of setMemoryWord(objectAsAddress(obj)+ofs, new)
   */
  public static void setOffsetAtOffset(Object object, Offset offset, Offset newvalue) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
  }

  /**
   * Set Offset at arbitrary (byte) offset from object.
   * Use setOffsetAtOffset(obj, ofs, new) instead of setMemoryWord(objectAsAddress(obj)+ofs, new)
   */
  public static void setOffsetAtOffset(Object object, Offset offset, Offset newvalue, int locationMetadata) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
  }

  /**
   * Set Object at arbitrary (byte) offset from object.
   * Use setObjectAtOffset(obj, ofs, new) instead of setMemoryWord(objectAsAddress(obj)+ofs, objectAsAddress(new))
   */
  public static void setObjectAtOffset(Object object, Offset offset, Object newvalue) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
  }

  /**
   * Set Object at arbitrary (byte) offset from object.
   */
  public static void setObjectAtOffset(Object object, Offset offset, Object newvalue, int locationMetadata) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
  }


  //---------------------------------------//
  //    Atomic Memory Access Primitives.   //
  //---------------------------------------//

  /**
   * Get contents of (object + offset) and begin conditional critical section.
   */
  public static int prepareInt(Object object, Offset offset) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return -1;
  }

  /**
   * Get contents of (object + offset) and begin conditional critical section.
   */
  public static Object prepareObject(Object object, Offset offset) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return null;
  }

  /**
   * Get contents of (object + offset) and begin conditional critical section.
   */
  public static Address prepareAddress(Object object, Offset offset) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return Address.max();
  }

  /**
   * Get contents of (object + offset) and begin conditional critical section.
   */
  public static Word prepareWord(Object object, Offset offset) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return Word.max();
  }

  /**
   * Get contents of (object + offset) and begin conditional critical section.
   */
  @Uninterruptible
  public static long prepareLong(Object object, Offset offset) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return -1;
  }

  /**
   * Sets the memory at (object + offset) to newValue if its contents are oldValue.
   * Must be paired with a preceding prepare (which returned the oldValue)
   * Returns true if successful.
   * Ends conditional critical section.
   */
  public static boolean attemptInt(Object object, Offset offset, int oldValue, int newValue) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return false;
  }

  /**
   * Sets the memory at (object + offset) to newValue if its contents are oldValue.
   * Must be paired with a preceding prepare (which returned the oldValue)
   * Returns true if successful.
   * Ends conditional critical section.
   */
  public static boolean attemptObject(Object object, Offset offset, Object oldValue, Object newValue) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return false;
  }

  /**
   * Sets the memory at (object + offset) to newValue if its contents are oldValue.
   * Must be paired with a preceding prepare (which returned the oldValue)
   * Returns true if successful.
   * Ends conditional critical section.
   */
  public static boolean attemptAddress(Object object, Offset offset, Address oldValue, Address newValue) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return false;
  }

  /**
   * Sets the memory at (object + offset) to newValue if its contents are oldValue.
   * Must be paired with a preceding prepare (which returned the oldValue)
   * Returns true if successful.
   * Ends conditional critical section.
   */
  public static boolean attemptWord(Object object, Offset offset, Word oldValue, Word newValue) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return false;
  }

  /**
   * Sets the memory at (object + offset) to newValue if its contents are oldValue.
   * Must be paired with a preceding prepare (which returned the oldValue)
   * Returns true if successful.
   * Ends conditional critical section.
   */
  public static boolean attemptLong(Object object, Offset offset, long oldValue,
      long newValue) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return false;
  }

  //---------------------------------------//
  //             Type Conversion.          //
  //---------------------------------------//

  @Entrypoint
  private static ObjectAddressRemapper objectAddressRemapper;

  /**
   * Specify how to handle "objectAsAddress" and "addressAsObject" casts.
   * Used by debugger and boot image writer.
   */
  public static void setObjectAddressRemapper(ObjectAddressRemapper x) {
    objectAddressRemapper = x;
  }

  /**
   * Cast bits.
   * Note: the returned integer is only valid until next garbage collection
   *   cycle (thereafter the referenced object might have moved and
   *   its address changed)
   * @param object object reference
   * @return object reference as bits
   */
  public static <T> Address objectAsAddress(T object) {
    if (VM.runningVM && VM.VerifyAssertions) {
      VM._assert(VM.NOT_REACHED); // call site should have been hijacked by magic in compiler
    }

    if (objectAddressRemapper == null) {
      return Address.zero();                 // tool isn't interested in remapping
    }

    return objectAddressRemapper.objectAsAddress(object);
  }

  /**
   * Certain objects aren't replicated in the boot image to save space.
   * @param object to intern
   * @return interned object
   */
  public static <T> T bootImageIntern(T object) {
    if (VM.runningVM && VM.VerifyAssertions) {
      VM._assert(VM.NOT_REACHED); // call site should have been hijacked by magic in compiler
    }

    if (objectAddressRemapper == null) {
      return object;                 // tool isn't interested in remapping
    }

    return objectAddressRemapper.intern(object);
  }

  /**
   * Certain objects aren't replicated in the boot image to save space.
   * @param object to intern
   * @return interned object
   */
  public static int bootImageIdentityHashCode(Object object) {
    if (VM.runningVM && VM.VerifyAssertions) {
      VM._assert(VM.NOT_REACHED); // call site should have been hijacked by magic in compiler
    }

    if (objectAddressRemapper == null) {
      // shouldn't create identity hash codes when we cannot record the effect, ignore if we're running a tool
      if (VM.VerifyAssertions) VM._assert(VM.runningTool || VM.writingImage);
      return System.identityHashCode(object);
    }

    return objectAddressRemapper.identityHashCode(object);
  }

  /**
   * Cast bits.
   * @param address object reference as bits
   * @return object reference
   */
  public static Object addressAsObject(Address address) {
    if (VM.runningVM && VM.VerifyAssertions) {
      VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    }

    if (objectAddressRemapper == null) {
      return null;               // tool isn't interested in remapping
    }

    return objectAddressRemapper.addressAsObject(address);
  }

  /**
   * Cast bits of code array into an object
   * Note: for use by Statics when assigning slots to static method pointers
   * @param code the code array to convert
   * @return object reference
   */
  public static Object codeArrayAsObject(CodeArray code) {
    if (VM.runningVM && VM.VerifyAssertions) {
      VM._assert(VM.NOT_REACHED); // call site should have been hijacked by magic in compiler
    }

    return code;
  }

  /**
   * Cast bits of tib into an object
   * Note: for use by Statics when assigning slots
   * @param tib the tib to convert
   * @return object reference
   */
  public static Object tibAsObject(TIB tib) {
    if (VM.runningVM && VM.VerifyAssertions) {
      VM._assert(VM.NOT_REACHED); // call site should have been hijacked by magic in compiler
    }

    return tib;
  }

  /**
   * Cast bits.
   * @param address object array reference as bits
   * @return object array reference
   */
  public static TIB addressAsTIB(Address address) {
    if (VM.runningVM && VM.VerifyAssertions) {
      VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    }
    return null;
  }

  /**
   * Cast object.
   * @param object object reference
   * @return object reference as type (no checking on cast)
   */
  public static RVMType objectAsType(Object object) {
    if (VM.runningVM && VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler

    return (RVMType)object;
  }

  /**
   * Cast object.
   * Note:     for use by gc to avoid checkcast during GC
   * @param object object reference
   * @return object reference as thread (no checking on cast)
   */
  public static RVMThread objectAsThread(Object object) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return null;
  }
  /**
   * Cast bits.
   * @param number A floating point number
   * @return   <code>number</code> as bits
   */
  public static int floatAsIntBits(float number) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return -1;
  }

  /**
   * Cast bits.
   * @param number as bits
   * @return <code>number</code> as a <code>float</code>
   */
  public static float intBitsAsFloat(int number) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return -1;
  }

  /**
   * Cast bits.
   * @param number as double
   * @return number as bits
   */
  public static long doubleAsLongBits(double number) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return -1;
  }

  /**
   * Cast bits.
   * @param number as bits
   * @return number as double
   */
  public static double longBitsAsDouble(long number) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return -1;
  }

  /**
   * Downcast.
   * Note:     for use by gc to avoid checkcast during GC
   * @param t Thread object reference
   * @return CollectorThread object reference
   */
  public static CollectorThread threadAsCollectorThread(RVMThread t) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return null;
  }

  /**
   * Recast.
   * Note:     for use by gc to avoid checkcast during GC
   * @param byte_array an address
   * Returned: byte array (byte[])  object reference
   */
  public static byte[] addressAsByteArray(Address byte_array) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return null;
  }

  /**
   * Cast object.
   * Note:     for use in dynamic type checking (avoid dynamic type checking in impl. of dynamic type checking)
   * @param object
   * @return short array (short[])  object reference
   */
  public static short[] objectAsShortArray(Object object) {
    if (VM.runningVM && VM.VerifyAssertions) {
      VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    }
    return (short[]) object;
  }

  /**
   * Cast object.
   * Note:     for use in dynamic type checking (avoid dynamic type checking in impl. of dynamic type checking)
   * @param object
   * @return int array (int[])  object reference
   */
  public static int[] objectAsIntArray(Object object) {
    if (VM.runningVM && VM.VerifyAssertions) {
      VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    }
    return (int[]) object;
  }

  //---------------------------------------//
  //          Object Header Access.        //
  //---------------------------------------//

  /**
   * Get an object's type.
   * @param object object reference
   * @return object type
   */
  public static RVMType getObjectType(Object object) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED); // call site should have been hijacked by magic in compiler
    return null;
  }

  /**
   * Get an array's length.
   * @param object object reference
   * @return array length (number of elements)
   */
  public static int getArrayLength(Object object) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED); // call site should have been hijacked by magic in compiler
    return -1;
  }

  //---------------------------------------//
  // Method Invocation And Stack Trickery. //
  //---------------------------------------//

  /**
   * Save current thread state.  Stores the values in the hardware registers
   * into a Registers object, @param registers
   *
   * We used to use this to implement thread switching, but we have a
   * threadSwitch magic now that does both of these in a single step as that
   * is less error-prone.  saveThreadState is now only used in the
   * implementation of athrow (RuntimeEntrypoints.athrow).
   *
   * The following registers are saved:
   *        - nonvolatile fpr registers
   *        - nonvolatile gpr registers
   *        - FRAME_POINTER register
   *        - THREAD_ID     "register"
   * @param registers place to save register values
   */
  // PNT: make a version of this that implicitly uses contextRegisters.
  public static void saveThreadState(Registers registers) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
  }

  /**
   * Switch threads.
   * The following registers are saved/restored
   *        - nonvolatile fpr registers
   *        - nonvolatile gpr registers
   *        - FRAME_POINTER "register"
   *        - THREAD_ID     "register"
   *
   * @param currentThread thread that is currently running
   * @param restoreRegs   registers from which we should restore
   *                      the saved hardware state of another thread.
   */
  public static void threadSwitch(RVMThread currentThread, Registers restoreRegs) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
  }

  /**
   * Resume execution with specified thread exception state.
   * Restores virtually all registers (details vary by architecutre).
   * But, the following are _NOT_ restored
   *        - JTOC_POINTER
   *        - THREAD_REGISTER
   * does not return (execution resumes at new IP)
   * @param registers register values to be used
   */
  public static void restoreHardwareExceptionState(Registers registers) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
  }

  /**
   * Return to caller of current method, resuming execution on a new stack
   * that's a copy of the original.
   * @param fp value to place into FRAME_POINTER register
   */
  public static void returnToNewStack(Address fp) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
  }

  /**
   * Transfer execution to target of a dynamic bridge method.
   * The following registers are restored:  non-volatiles, volatiles
   * Note: this method must only be called from a DynamicBridge method
   * never returns (target method returns to caller of dynamic bridge method)
   * @param instructions target method
   */
  public static void dynamicBridgeTo(CodeArray instructions) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
  }

  /** Call "<clinit>" method with no argument list. */
  public static void invokeClassInitializer(CodeArray clinit) throws Exception {
    // Since the real method passes exceptions up. Constructor might throw an arbitrary exception.
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    throw new Exception("UNREACHED");
  }

  /** Call arbitrary method with argument list. */
  public static void invokeMethodReturningVoid(CodeArray code, WordArray gprs, double[] fprs, byte[] fprmeta, WordArray spills) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
  }

  /** Call arbitrary method with argument list. */
  public static int invokeMethodReturningInt(CodeArray code, WordArray gprs, double[] fprs, byte[] fprmeta, WordArray spills) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return -1;
  }

  /** Call arbitrary method with argument list. */
  public static long invokeMethodReturningLong(CodeArray code, WordArray gprs, double[] fprs, byte[] fprmeta, WordArray spills) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return -1;
  }

  /** Call arbitrary method with argument list. */
  public static float invokeMethodReturningFloat(CodeArray code, WordArray gprs, double[] fprs, byte[] fprmeta, WordArray spills) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return -1;
  }

  /** Call arbitrary method with argument list. */
  public static double invokeMethodReturningDouble(CodeArray code, WordArray gprs, double[] fprs, byte[] fprmeta, WordArray spills) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return -1;
  }

  /** Call arbitrary method with argument list. */
  public static Object invokeMethodReturningObject(CodeArray code, WordArray gprs, double[] fprs, byte[] fprmeta, WordArray spills) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return null;
  }

  //---------------------------------------//
  //            Cache Management.          //
  //---------------------------------------//

  /**** NOTE: all per-address operations now live in vmmagic.Address *****/

  /**
   * Wait for preceeding cache flush/invalidate instructions to
   * complete on all processors.
   */
  public static void sync() {
    if (VM.runningVM && VM.VerifyAssertions) {
      VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    }
  }

  /**
   * Wait for all preceeding instructions to complete and discard any
   * prefetched instructions on this processor.
   */
  public static void isync() {
    if (VM.runningVM && VM.VerifyAssertions) {
      VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    }
  }

  /****************************************************************
   *
   *    Misc
   *
   */

  /**
   * On IA32, emit a PAUSE instruction, to optimize spin-wait loops.
   */
  public static void pause() {
    if (VM.runningVM && VM.VerifyAssertions) {
      VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    }
  }

  /**
   * A hardware SQRT instruction
   */
  public static float sqrt(float value) {
    if (VM.runningVM && VM.VerifyAssertions) {
      VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    }
    return -1.0f; // which should upset them even if assertions aren't enabled ...
  }

  /**
   * A hardware SQRT instruction
   */
  public static double sqrt(double value) {
    if (VM.runningVM && VM.VerifyAssertions) {
      VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    }
    return -1.0d; // which should upset them even if assertions aren't enabled ...
  }

  /**
   * How deeply inlined is this method (0 means no inlining).
   */
  public static int getInlineDepth() {
    if (VM.runningVM && VM.VerifyAssertions) {
      VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    }
    return 0;
  }

  /**
   * Is the specified parameter constant (due to either inlining or specialization).
   * Count starts at zero and includes the 'this' parameter for instance methods.
   */
  public static boolean isConstantParameter(int index) {
    if (VM.runningVM && VM.VerifyAssertions) {
      VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    }
    return false;
  }
}
