/*
 * (C) Copyright IBM Corp 2001,2002
 */
//$Id$
package com.ibm.JikesRVM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;
import com.ibm.JikesRVM.memoryManagers.mmInterface.VM_CollectorThread;
import com.ibm.JikesRVM.classloader.VM_Type;

/**
 * Magic methods for accessing raw machine memory, registers, and 
 * operating system calls.
 * 
 * <p> These are "inline assembler functions" that cannot be implemented in
 * Java code. Their names are recognized by RVM's compilers 
 * and cause inline machine code to be generated instead of 
 * actual method calls.
 *
 * @author Bowen Alpern
 * @author Derek Lieber
 */
public class VM_Magic {

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
    if (VM.runningVM && VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return VM_BootRecord.the_boot_record.tocRegister;
  }

  /** Set contents of "jtoc" register. */
  public static void setTocPointer(Address addr) {
    if (VM.runningVM && VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    //TODOreturn Address.fromInt(VM_BootRecord.the_boot_record.tocRegister);
  }

  /** Get contents of "jtoc" register as an int[] */
  public static int[] getJTOC() {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return null;
  }

  /** Get contents of "processor" register. */
  public static VM_Processor getProcessorRegister() {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return null;
  }

  /** Set contents of "processor" register. */
  public static void setProcessorRegister(VM_Processor p) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
  }

  //-#if RVM_FOR_IA32
  /** Get contents of ESI, as a VM_Processor */
  public static VM_Processor getESIAsProcessor() {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return null;
  }

  /** Set contents of ESI to hold a reference to a processor object */
  public static void setESIAsProcessor(VM_Processor p) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
  }
  //-#endif

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
   * Set next instruction address for a frame 
   * @param fp its frame pointer 
   * @param newAddr new next instruction address
   */
  public static void setNextInstructionAddress(Address fp, Address newAddr) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
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
  public static Address getReturnAddress(Address fp) throws UninterruptiblePragma {
    return getReturnAddressLocation(fp).loadAddress();
  }

  /**
   * Get return address for a frame
   * @param fp its frame pointer
   */
  public static void setReturnAddress(Address fp, Address v) throws UninterruptiblePragma {
    getReturnAddressLocation(fp).store(v);
  }

  //---------------------------------------//
  //           Memory Access.              //
  //---------------------------------------//

  /**
   * Get byte at arbitrary (byte) offset from object. 
   * Clients must not depend on whether or not the byte is zero or sign extended as it is loaded.
   * (In other words, mask off all but the lower 8 bits before using the value).
   */
  public static byte getByteAtOffset(Object object, Offset offset) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return -1;
  }

  /**
   * Get char at arbitrary (byte) offset from object.  Clients must
   * not depend on whether or not the char is zero or sign extended as
   * it is loaded.  (In other words, mask off all but the lower 16
   * bits before using the value).
   */
  public static char getCharAtOffset(Object object, Offset offset) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return (char)-1;
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
   * Get Word at arbitrary (byte) offset from object.
   * Use getWordAtOffset(obj, ofs) instead of getMemoryWord(objectAsAddress(obj)+ofs)
   */
  public static Word getWordAtOffset(Object object, Offset offset) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return Word.max();
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
   * Get Object[] at arbitrary (byte) offset from object.
   * Use getObjectArrayAtOffset(obj, ofs) instead of 
   * (Object[])addressAsObject(getMemoryAddr(objectAsAddress(obj)+ofs))
   */
  public static Object[] getObjectArrayAtOffset(Object object, Offset offset) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return null;
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
   * Get double at arbitrary (byte) offset from object.
   * Use getDoubleAtOffset(obj, ofs) instead of two getIntAtOffset
   */
  public static double getDoubleAtOffset(Object object, Offset offset) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return -1;
  }

  /**
   * Set byte at arbitrary (byte) offset from object.
   */ 
  public static void setByteAtOffset(Object object, Offset offset, byte newvalue) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
  }

  /**
   * Set char at arbitrary (byte) offset from object.
   */ 
  public static void setCharAtOffset(Object object, Offset offset, char newvalue) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
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
   * Use setIntAtOffset(obj, ofs, new) instead of setMemoryWord(objectAsAddress(obj)+ofs, new)
   */ 
  public static void setIntAtIntOffset(Object object, int offset, int newvalue) {
    setIntAtOffset(object, Offset.fromIntSignExtend(offset), newvalue);
  }

  /**
   * Set word at arbitrary (byte) offset from object.
   * Use setWordAtOffset(obj, ofs, new) instead of setMemoryWord(objectAsAddress(obj)+ofs, new)
   */ 
  public static void setWordAtOffset(Object object, Offset offset, Word newvalue) {
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

  /**
   * Set long at arbitrary (byte) offset from object.
   * Use setlongAtOffset(obj, ofs) instead of two setIntAtOffset
   */ 
  public static void setLongAtOffset(Object object, Offset offset, long newvalue) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
  }

  /**
   * Set double at arbitrary (byte) offset from object.
   * Use setDoubleAtOffset(obj, ofs) instead of two setIntAtOffset
   */ 
  public static void setDoubleAtOffset(Object object, Offset offset, double newvalue) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
  }

  /**
   * Set contents of memory location.
   * @deprecated Use setIntAtOffset / setObjectAtOffset where possible.
   */
  public static void setMemoryAddress(Address address, Address value, int locationMetadata) {
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
  public static boolean attemptWord(Object object, Offset offset,
                                    Word oldValue, Word newValue) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return false;
  }

  //---------------------------------------//
  //             Type Conversion.          //
  //---------------------------------------//

   private static VM_ObjectAddressRemapper objectAddressRemapper;

  /**
   * Specify how to handle "objectAsAddress" and "addressAsObject" casts.
   * Used by debugger and boot image writer.
   */
  public static void setObjectAddressRemapper(VM_ObjectAddressRemapper x) { 
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
  public static Address objectAsAddress(Object object) {
    if (VM.runningVM && VM.VerifyAssertions) VM._assert(VM.NOT_REACHED); // call site should have been hijacked by magic in compiler

    if (objectAddressRemapper == null)
      return Address.zero();                 // tool isn't interested in remapping
      
    return objectAddressRemapper.objectAsAddress(object);
  }


  /**
   * Cast bits.
   * @param address object reference as bits
   * @return object reference
   */
  public static Object addressAsObject(Address address) {
    if (VM.runningVM && VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler

    if (objectAddressRemapper == null)
      return null;               // tool isn't interested in remapping
      
    return objectAddressRemapper.addressAsObject(address);
  }

  /**
   * Cast bits.
   * @param address object array reference as bits
   * @return object array reference
   */
  public static Object[] addressAsObjectArray(Address address) {
    if (VM.runningVM && VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return null;
  }

  /**
   * Cast bits.
   * @param address  Object reference as bits
   * @return object reference as type (no checking on cast)
   * @deprecated  Use <code>{@link #objectAsType}( {@link #addressAsObject} (</code>...<code>))</code>
   */
  public static VM_Type addressAsType(Address address) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return null;
  }

  /**
   * Cast object.
   * @param object object reference
   * @return object reference as type (no checking on cast)
   */
  public static VM_Type objectAsType(Object object) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return null;
  }

  /**
   * Cast bits.
   * @param address object reference as bits
   * @return object reference as thread (no checking on cast)
   * @deprecated  Use objectAsThread.
   */
  public static VM_Thread addressAsThread(Address address) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return null;
  }

  /**
   * Cast object.
   * Note:     for use by gc to avoid checkcast during GC
   * @param object object reference
   * @return object reference as thread (no checking on cast)
   */
  public static VM_Thread objectAsThread(Object object) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return null;
  }

  /**
   * Cast object.
   * Note:     for use by gc to avoid checkcast during GC
   * @param object object reference
   * @return object reference as processor (no checking on cast)
   */
  public static VM_Processor objectAsProcessor(Object object) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return null;
  }

  /**
   * Cast bits. 
   * Note:     for use by gc to avoid checkcast during GC
   * @param address object reference as bits
   * @return object reference as VM_Registers (no checking on cast)
   */
  public static VM_Registers addressAsRegisters(Address address) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return null;
  }

  /**
   * Cast bits.
   * Note:     for use by gc to avoid checkcast during GC
   * @param address object reference as bits
   * @return object reference as <code>int[]</code> (no checking on cast)
   */
  public static int[] addressAsStack(Address address) {
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
   * @param t VM_Thread object reference
   * @return VM_CollectorThread object reference
   */
  public static VM_CollectorThread threadAsCollectorThread(VM_Thread t) {
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
   * @return byte array (byte[])  object reference
   */
  public static byte[] objectAsByteArray(Object object) {
    if (VM.runningVM && VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return (byte[])object;
  }

  /**
   * Cast object.
   * Note:     for use in dynamic type checking (avoid dynamic type checking in impl. of dynamic type checking)
   * @param object
   * @return short array (short[])  object reference
   */
  public static short[] objectAsShortArray(Object object) {
    if (VM.runningVM && VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return (short[])object;
  }

  /**
   * Cast object.
   * Note:     for use in dynamic type checking (avoid dynamic type checking in impl. of dynamic type checking)
   * @param object
   * @return int array (int[])  object reference
   */
  public static int[] objectAsIntArray(Object object) {
    if (VM.runningVM && VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return (int[])object;
  }

  /**
   * To allow noncopying collectors to treat blocks array as array of ints
   */
  public static int[] addressAsIntArray(Address int_array) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return null;
  }

  //---------------------------------------//
  //          Object Header Access.        //
  //---------------------------------------//
  
  /**
   * Get an object's type.
   * @param object object reference
   * @return object type
   */
  public static VM_Type getObjectType(Object object) {
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
   * into a VM_Registers object, @param registers
   *
   * We used to use this to implement thread switching, but we have a
   * threadSwitch magic now that does both of these in a single step as that
   * is less error-prone.  saveThreadState is now only used in the
   * implementation of athrow (VM_Runtime.athrow). 
   * 
   * Note that #args to this method must match #args to VM_Processor.dispatch()
   *
   * The following registers are saved:
   *        - nonvolatile fpr registers
   *        - nonvolatile gpr registers
   *        - FRAME_POINTER register
   *        - THREAD_ID     "register"
   * @param registers place to save register values
   */
  public static void saveThreadState(VM_Registers registers) {
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
  public static void threadSwitch(VM_Thread currentThread, 
                                  VM_Registers restoreRegs) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
  }

  /**
   * Resume execution with specified thread exception state.
   * Restores virtually all registers (details vary by architecutre).
   * But, the following are _NOT_ restored 
   *        - JTOC_POINTER
   *        - PROCESSOR_REGISTER
   * does not return (execution resumes at new IP)
   * @param registers register values to be used
   */
  public static void restoreHardwareExceptionState(VM_Registers registers) {
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
   * Note: this method must only be called from a VM_DynamicBridge method
   * never returns (target method returns to caller of dynamic bridge method)
   * @param instructions target method
   */
  public static void dynamicBridgeTo(VM_CodeArray instructions) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
  }

  /** Call "main" method with argument list. */
  public static void invokeMain(Object argv, VM_CodeArray main) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
  }

  /** Call "<clinit>" method with no argument list. */
  public static void invokeClassInitializer(VM_CodeArray clinit) 
    throws Exception            // Since the real method passes exceptions
                                // up.  Constructor might throw an arbitrary
                                // exception. 
  {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    throw new Exception("UNREACHED");
  }

  /** Call arbitrary method with argument list. */
  public static void invokeMethodReturningVoid(VM_CodeArray code, 
                                               WordArray gprs, 
                                               double[] fprs, 
                                               WordArray spills) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
  }

  /** Call arbitrary method with argument list. */
  public static int invokeMethodReturningInt(VM_CodeArray code, 
                                             WordArray gprs, 
                                             double[] fprs, 
                                             WordArray spills) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return -1;
  }

  /** Call arbitrary method with argument list. */
  public static long invokeMethodReturningLong(VM_CodeArray code, 
                                               WordArray gprs, 
                                               double[] fprs, 
                                               WordArray spills) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return -1;
  }

  /** Call arbitrary method with argument list. */
  public static float invokeMethodReturningFloat(VM_CodeArray code, 
                                                 WordArray gprs, 
                                                 double[] fprs, 
                                                 WordArray spills) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return -1;
  }

  /** Call arbitrary method with argument list. */
  public static double invokeMethodReturningDouble(VM_CodeArray code, 
                                                   WordArray gprs, 
                                                   double[] fprs, 
                                                   WordArray spills) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return -1;
  }

  /** Call arbitrary method with argument list. */
  public static Object invokeMethodReturningObject(VM_CodeArray code, 
                                                   WordArray gprs, 
                                                   double[] fprs, 
                                                   WordArray spills) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return null;
  }

  //-#if RVM_FOR_IA32
   /**
    * Set the floating-point rounding mode to round-towards-zero
    */
   public static void roundToZero() {
     if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
   }

   /**
    * Clear the hardware floating point state
    */
   public static void clearFloatingPointState() {
     if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
   }
  //-#endif
  
  //---------------------------------------//
  //            Cache Management.          //
  //---------------------------------------//
   
  //-#if RVM_FOR_POWERPC
  /** 
   * Write contents of this processor's modified data cache back to main storage.
   */
  public static void dcbst(Address address) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
  }
  //-#endif

  //-#if RVM_FOR_POWERPC
  /**
   * Invalidate copy of main storage held in any processor's instruction cache.
   */
  public static void icbi(Address address) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
  }
  //-#endif

  /**
   * Wait for preceeding cache flush/invalidate instructions to complete on all processors.
   */
  public static void sync() {
    if (VM.runningVM && VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
  }

  /**
   * Wait for all preceeding instructions to complete and discard any prefetched instructions on this processor.
   */ 
  public static void isync() {
    if (VM.runningVM && VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
  }

}

