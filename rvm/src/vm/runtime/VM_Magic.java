/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Magic methods for accessing raw machine memory, registers, and 
 * operating system calls.
 * 
 * <p> These are "inline assembler functions" that cannot be implemented in
 * java code. Their names are recognized by RVM's compilers 
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
  public static int getFramePointer() {
    if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return -1;
  }

  /** Get contents of "jtoc" register. */
  public static int getTocPointer() {
    if (VM.runningVM && VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return VM_BootRecord.the_boot_record.tocRegister;
  }

  /** Get contents of "jtoc" register as an int[] */
  public static int[] getJTOC() {
    if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return null;
  }

  /** 
   * Get "thread id" pseduo register for currently executing thread.
   * @return shifted thread index of currently executing thread
   */
  public static int getThreadId() {
    if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return -1;
  }

  /** 
   * Set contents of "thread id" pseduo register.
   * @param ti shifted thread index
   */
  static void setThreadId(int ti) {
    if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
  }

  /** Get contents of "processor" register. */
  static VM_Processor getProcessorRegister() {
    if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return null;
  }

  /** Set contents of "processor" register. */
  static void setProcessorRegister(VM_Processor p) {
    if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
  }

  //-#if RVM_FOR_IA32
  /** Get contents of ESI, as a VM_Processor */
  static VM_Processor getESIAsProcessor() {
    if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return null;
  }

  /** Set contents of ESI to hold a reference to a processor object */
  static void setESIAsProcessor(VM_Processor p) {
    if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
  }
  //-#endif

  /**
   * Read contents of hardware time base registers.
   * Note:     we think that 1 "tick" == 4 "machine cycles", but this seems to be
   *           undocumented and may vary across processor implementations.
   * @return number of ticks (epoch undefined)
   */
  static long getTimeBase() {
    if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return -1;
  }

  /**
   * Read contents of hardware real time clock registers.
   * Note:     these registers are emulated on some processors (eg. 603, 604)
   *           so the overhead of this function may be high (~7000 cycles).
   * @param p current processor (!!TEMP)
   * @return time in seconds (epoch Jan 1 1970), to nanonsecond resolution.
   */
  static double getTime(VM_Processor p) {
    if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return -1;
  }

  /** Set bit in thread switch condition register. */ // TODO: remove me!
  static void setThreadSwitchBit() {
    if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
  }

  /** Clear bit in thread switch condition register. */
  static void clearThreadSwitchBit() {
    if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
  }
     
  //---------------------------------------//
  //       Stackframe Manipulation         //
  //---------------------------------------//

  /** 
   * Get fp for parent frame 
   * @param fp frame pointer for child frame
   */
  public static int getCallerFramePointer(int fp) {
    if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return -1;
  }

  /** 
   * Set fp for parent frame 
   * @param fp frame pointer for child frame 
   * @param newCallerFP new value for caller frame pointer
   */
  public static void setCallerFramePointer(int fp, int newCallerFP) {
    if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
  }

  /**
   * Get Compiled Method ID for a frame
   * @param fp its frame pointer).
   */ 
  public static int getCompiledMethodID(int fp) {
    if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return -1;
  }

  /**
   * Set the Compiled Method ID for a frame.
   * @param fp its frame pointer
   * @param newCMID a new cmid for the frame
   */
  public static void setCompiledMethodID(int fp, int newCMID) {
    if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
  }

  /** 
   * Get next instruction address for a frame 
   * @param fp its frame pointer.
   */
  public static int getNextInstructionAddress(int fp) {
    if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return -1;
  }

  /**
   * Set next instruction address for a frame 
   * @param fp its frame pointer 
   * @param newAddr new next instruction address
   */
  public static void setNextInstructionAddress(int fp, int newAddr) {
    if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
  }

  /**
   * Get return address for a frame
   * @param fp its frame pointer
   */
  public static int getReturnAddress(int fp) {
    if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return -1;
  }

  /**
   * Set return address for a frame 
   * @param fp its frame pointer 
   * @param newAddr a new return address
   */
  public static void setReturnAddress(int fp, int newAddr) {
    if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
  }

  //---------------------------------------//
  //           Memory Access.              //
  //---------------------------------------//

  /**
   * Get int at arbitrary (byte) offset from object.
   * Use getIntAtOffset(obj, ofs) instead of getMemoryWord(objectAsAddress(obj)+ofs)
   */
  static int getIntAtOffset(Object object, int offset) {
    if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return -1;
  }

  /**
   * Get Object at arbitrary (byte) offset from object.
   * Use getObjectAtOffset(obj, ofs) instead of addressAsObject(getMemoryWord(objectAsAddress(obj)+ofs))
   */
  static Object getObjectAtOffset(Object object, int offset) {
    if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return null;
  }

  /**
   * Get long at arbitrary (byte) offset from object.
   * Use getlongAtOffset(obj, ofs) instead of two getIntAtOffset
   */
  static long getLongAtOffset(Object object, int offset) {
    if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return -1;
  }

  /**
   * Get contents of a memory location.
   * @deprecated  Use getIntAtOffset / getObjectAtOffset where possible.
   */
  public static int getMemoryWord(int address) {
    if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return -1;
  }

  /**
   * Set int at arbitrary (byte) offset from object.
   * Use setIntAtOffset(obj, ofs, new) instead of setMemoryWord(objectAsAddress(obj)+ofs, new)
   */ 
  static void setIntAtOffset(Object object, int offset, int newvalue) {
    if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
  }

  /**
   * Set Object at arbitrary (byte) offset from object.
   * Use getObjectAtOffset(obj, ofs, new) instead of setMemoryWord(objectAsAddress(obj)+ofs, objectAsAddress(new))
   */ 
  static void setObjectAtOffset(Object object, int offset, Object newvalue) {
    if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
  }

  /**
   * Set long at arbitrary (byte) offset from object.
   * Use setlongAtOffset(obj, ofs) instead of two setIntAtOffset
   */ 
  static void setLongAtOffset(Object object, int offset, long newvalue) {
    if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
  }

  /**
   * Set contents of memory location.
   * @deprecated Use setIntAtOffset / setObjectAtOffset where possible.
   */
  public static void setMemoryWord(int address, int value) {
    if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
  }

  //---------------------------------------//
  //    Atomic Memory Access Primitives.   //
  //---------------------------------------//

  /**
   * Get contents of (object + offset) and begin conditional critical section.
   */ 
  static int prepare(Object object, int offset) {
    if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return -1;
  }

  /**
   * Sets the memory at (object + offset) to newValue if its contents are oldValue.  
   * Must be paired with a preceding prepare (which returned the oldValue)
   * Returns true if successful.
   * Ends conditional critical section.
   */ 
  static boolean attempt(Object object, int offset, int oldValue, int newValue) {
    if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
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
  static void setObjectAddressRemapper(VM_ObjectAddressRemapper x) { 
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
  public static int objectAsAddress(Object object) {
    if (VM.runningVM && VM.VerifyAssertions) VM.assert(VM.NOT_REACHED); // call site should have been hijacked by magic in compiler

    if (objectAddressRemapper == null)
      return -1;                 // tool isn't interested in remapping
      
    return objectAddressRemapper.objectAsAddress(object);
  }

  /**
   * Cast bits.
   * @param address object reference as bits
   * @return object reference
   */
  public static Object addressAsObject(int address) {
    if (VM.runningVM && VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler

    if (objectAddressRemapper == null)
      return null;               // tool isn't interested in remapping
      
    return objectAddressRemapper.addressAsObject(address);
  }

  /**
   * Cast bits.
   * @param object reference as bits
   * @return object reference as type (no checking on cast)
   * @depracated  Use objectAsType( addressAsObject (...))
   */
  public static VM_Type addressAsType(int address) {
    if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return null;
  }

  /**
   * Cast object.
   * @param object object reference
   * @return object reference as type (no checking on cast)
   */
  public static VM_Type objectAsType(Object object) {
    if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return null;
  }

  /**
   * Cast bits.
   * @param address object reference as bits
   * @return object reference as thread (no checking on cast)
   * @depracated  Use objectAsThread.
   */
  public static VM_Thread addressAsThread(int address) {
    if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return null;
  }

  /**
   * Cast object.
   * Note:     for use by gc to avoid checkcast during GC
   * @param object object reference
   * @return object reference as thread (no checking on cast)
   */
  public static VM_Thread objectAsThread(Object object) {
    if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return null;
  }

  /**
   * Cast object.
   * Note:     for use by gc to avoid checkcast during GC
   * @param object object reference
   * @return object reference as processor (no checking on cast)
   */
  public static VM_Processor objectAsProcessor(Object object) {
    if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return null;
  }

//-#if RVM_WITH_JIKESRVM_MEMORY_MANAGERS
  /**
   * Cast object.
   * To allow noncopying collectors to treat blocks array as array
   * of ints rather than VM_BlockControl, avoiding lots of wasted
   * scanning during gc.
   * @param address object reference as bits 
   * @return object reference as VM_BlockControl (no checking on cast)
   */
  public static VM_BlockControl addressAsBlockControl(int address) {
    if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler return null;
    return null;
  }

  /**
   * Cast bits.
   * @param address object reference as bits
   * @return object reference as VM_SizeControl (no checking on cast)
   */
  public static VM_SizeControl addressAsSizeControl(int address) {
    if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler return null;
    return null;
  }

  /**
   * Cast bits.
   * @param address object reference as bits
   * @return object reference as VM_SizeControl[] (no checking on cast)
   */
  public static VM_SizeControl[] addressAsSizeControlArray(int address) {
    if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler return null;
    return null;
  }

//-#if RVM_WITH_CONCURRENT_GC
  /**
  /**
   * Downcast.
   * Note:     for use by gc to avoid checkcast during GC
   * @param  t VM_Thread object reference
   * @return   VM_RCCollectorThread object reference
   */
  public static VM_RCCollectorThread threadAsRCCollectorThread(VM_Thread t) {
    if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return null;
  }
//-#endif

//-#endif

  /**
   * Cast bits. 
   * Note:     for use by gc to avoid checkcast during GC
   * @param address object reference as bits
   * @return object reference as VM_Registers (no checking on cast)
   */
  public static VM_Registers addressAsRegisters(int address) {
    if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return null;
  }

  /**
   * Cast bits.
   * Note:     for use by gc to avoid checkcast during GC
   * @param address object reference as bits
   * @return object reference as int[] (no checking on cast)
   */
  public static int[] addressAsStack(int address) {
    if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return null;
  }

  /**
   * Cast bits.
   * @param number as float
   * @param number as bits
   */
  public static int floatAsIntBits(float number) {
    if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return -1;
  }

  /**
   * Cast bits.
   * @param number as bits
   * @return number as float
   */
  public static float intBitsAsFloat(int number) {
    if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return -1;
  }

  /**
   * Cast bits.
   * @param number as double
   * @return number as bits
   */
  public static long doubleAsLongBits(double number) {
    if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return -1;
  }

  /**
   * Cast bits.
   * @param number as bits
   * @return number as double
   */
  public static double longBitsAsDouble(long number) {
    if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return -1;
  }

  /**
   * Downcast.
   * Note:     for use by gc to avoid checkcast during GC
   * @param t VM_Thread object reference
   * @return VM_CollectorThread object reference
   */
  public static VM_CollectorThread threadAsCollectorThread(VM_Thread t) {
    if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return null;
  }

  /**
   * Recast.
   * Note:     for use by gc to avoid checkcast during GC
   * @param byte_array an address 
   * Returned: byte array (byte[])  object reference
   */
  public static byte[] addressAsByteArray(int byte_array) {
    if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return null;
  }

  /**
   * Cast object.
   * Note:     for use in dynamic type checking (avoid dynamic type checking in impl. of dynamic type checking)
   * @param object
   * @return byte array (byte[])  object reference
   */
  public static byte[] objectAsByteArray(Object object) {
    if (VM.runningVM && VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return (byte[])object;
  }

  /**
   * Cast object.
   * Note:     for use in dynamic type checking (avoid dynamic type checking in impl. of dynamic type checking)
   * @param object
   * @return short array (short[])  object reference
   */
  public static short[] objectAsShortArray(Object object) {
    if (VM.runningVM && VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return (short[])object;
  }

  /**
   * To allow noncopying collectors to treat blocks array as array of ints
   */
  public static int[] addressAsIntArray(int int_array) {
    if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
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
    if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED); // call site should have been hijacked by magic in compiler
    return null;
  }

  /**
   * Get an objects status field.
   * @param object object reference
   * @return status field
   */
  public static int getObjectStatus(Object object) {
    if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED); // call site should have been hijacked by magic in compiler
    return -1;
  }

  /**
   * Get an array's length.
   * @param object object reference
   * @return array length (number of elements)
   */
  public static int getArrayLength(Object object) {
    if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED); // call site should have been hijacked by magic in compiler
    return -1;
  }

  //---------------------------------------//
  // Method Invocation And Stack Trickery. //
  //---------------------------------------//
   
  /**
   * Save current thread state.
   * Note that #args to this method must match #args to VM_Processor.dispatch()
   * The following registers are saved:
   *        - nonvolatile fpr registers
   *        - nonvolatile gpr registers
   *        - FRAME_POINTER register
   *        - THREAD_ID     "register"
   * @param registers place to save register values
   */
  public static void saveThreadState(VM_Registers registers) {
    if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
  }

  /**
   * Switch threads.
   * The following registers are saved/restored
   *        - nonvolatile fpr registers
   *        - nonvolatile gpr registers
   *        - FRAME_POINTER "register"
   *        - THREAD_ID     "register"
   * 
   * @param phantomThread thread that is currently running 
   * @param restoreRegs   registers from which we should restore 
   *                      the saved hardware state of another thread.
   */
  public static void threadSwitch(VM_Thread currentThread, 
				  VM_Registers restoreRegs) {
    if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
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
    if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
  }

  /**
   * Return to caller of current method, resuming execution on a new stack 
   * that's a copy of the original.
   * @param fp value to place into FRAME_POINTER register
   */
  public static void returnToNewStack(int fp) {
    if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
  }

  /**
   * Transfer execution to target of a dynamic bridge method.
   * The following registers are restored:  non-volatiles, volatiles
   * Note: this method must only be called from a VM_DynamicBridge method
   * never returns (target method returns to caller of dynamic bridge method)
   * @param instructions target method
   */
  public static void dynamicBridgeTo(INSTRUCTION[] instructions) {
    if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
  }

  /** Call "main" method with argument list. */
  public static void invokeMain(Object argv, INSTRUCTION[] main) {
    if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
  }

  /** Call "<clinit>" method with no argument list. */
  public static void invokeClassInitializer(INSTRUCTION[] clinit) {
    if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
  }

  /** Call arbitrary method with argument list. */
  public static void invokeMethodReturningVoid(INSTRUCTION[] code, 
					       int[] gprs, 
					       double[] fprs, 
					       int[] spills) {
    if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
  }

  /** Call arbitrary method with argument list. */
  public static int invokeMethodReturningInt(INSTRUCTION[] code, 
					     int[] gprs, 
					     double[] fprs, 
					     int[] spills) {
    if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return -1;
  }

  /** Call arbitrary method with argument list. */
  public static long invokeMethodReturningLong(INSTRUCTION[] code, 
					       int[] gprs, 
					       double[] fprs, 
					       int[] spills) {
    if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return -1;
  }

  /** Call arbitrary method with argument list. */
  public static float invokeMethodReturningFloat(INSTRUCTION[] code, 
						 int[] gprs, 
						 double[] fprs, 
						 int[] spills) {
    if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return -1;
  }

  /** Call arbitrary method with argument list. */
  public static double invokeMethodReturningDouble(INSTRUCTION[] code, 
						   int[] gprs, 
						   double[] fprs, 
						   int[] spills) {
    if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return -1;
  }

  /** Call arbitrary method with argument list. */
  public static Object invokeMethodReturningObject(INSTRUCTION[] code, 
						   int[] gprs, 
						   double[] fprs, 
						   int[] spills) {
    if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return null;
  }

  /*
   * Call a system function with 0..4 parameters.
   * Taken:    entrypoint address of function to be called
   *           value to place in TOC register before calling function
   *           parameters to pass to function
   * Returned: value returned by function
   *
   * Note: These are PowerPC or Intel specific.
   *       Do not call them directly - use architecture independent VM.sysCall() instead.
   *
   */
//-#if RVM_FOR_POWERPC
   public static int  sysCall0(int ip, int toc)                                 { if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED); return -1; }
   public static int  sysCall1(int ip, int toc, int p0)                         { if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED); return -1; }
   public static int  sysCall2(int ip, int toc, int p0, int p1)                 { if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED); return -1; }
   public static int  sysCall3(int ip, int toc, int p0, int p1, int p2)         { if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED); return -1; }
   public static int  sysCall4(int ip, int toc, int p0, int p1, int p2, int p3) { if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED); return -1; }

   public static long sysCall_L_0(int ip, int toc)                              { if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED); return -1; }
   public static long sysCall_L_I(int ip, int toc, int p0)                      { if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED); return -1; }
   public static int  sysCallAD(int ip, int toc, int p0, double p1)             { if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED); return -1; }
//-#endif

//-#if RVM_FOR_IA32
   public static int  sysCall0(int ip) { 
     if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED); return -1; 
   }
   public static int  sysCall1(int ip, int p0) { 
     if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED); return -1; 
   }
   public static int  sysCall2(int ip, int p0, int p1) { 
     if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED); return -1; 
   }
   public static int  sysCall3(int ip, int p0, int p1, int p2) { 
     if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED); return -1; 
   }
   public static int  sysCall4(int ip, int p0, int p1, int p2, int p3) { 
     if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED); return -1; 
   }
   public static long sysCall_L_0(int ip) { 
     if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED); return -1; 
   }
   public static long sysCall_L_I(int ip, int p0) { 
     if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED); return -1; 
   }
   public static int  sysCallAD(int ip, int p0, double p1) { 
     if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED); return -1; 
   }
//-#endif
   public static int  sysCallSigWait(int ip, int toc, int p0, 
                                     int p1, VM_Registers r){ 
     if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED); return -1; 
   }

//-#if RVM_FOR_IA32
   /**
    * Set the floating-point rounding mode to round-towards-zero
    */
   public static void roundToZero() {
     if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);
   }

   /**
    * Clear the hardware floating point state
    */
   public static void clearFloatingPointState() {
     if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);
   }

//-#endif
  
  //---------------------------------------//
  //            Cache Management.          //
  //---------------------------------------//
   
  //-#if RVM_FOR_POWERPC
  /** 
   * Write contents of this processor's modified data cache back to main storage.
   */
  static void dcbst(int address) {
    if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
  }
  //-#endif

  //-#if RVM_FOR_POWERPC
  /**
   * Invalidate copy of main storage held in any processor's instruction cache.
   */
  static void icbi(int address) {
    if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
  }
  //-#endif

  /**
   * Wait for preceeding cache flush/invalidate instructions to complete on all processors.
   */
  static void sync() {
    if (VM.runningVM && VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
  }

  /**
   * Wait for all preceeding instructions to complete and discard any prefetched instructions on this processor.
   */ 
  static void isync() {
    if (VM.runningVM && VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
  }

  //----------------------//
  // Pragmas              //
  //----------------------//

  /**
   * The method containing this pragma is either unsafe, or undesirable to inline.
   */
  public static void pragmaNoInline() {
    if (VM.runningVM && VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
  }

  /**
   * User directive that the method containing this pragma should be 
   * inlined regardless of code space heuristics.
   */
  public static void pragmaInline() {
    if (VM.runningVM && VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
  }

  /**
   * User directive that the method containing this pragma should be not be
   * compiled by the optimizing compiler.  Only used by VM_NativeIdleThread.
   */
  public static void pragmaNoOptCompile() {
    if (VM.runningVM && VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
  }
  
}

