/*
 * (C) Copyright IBM Corp 2001,2002
 */
//$Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.memoryManagers.vmInterface.MM_Interface;
import com.ibm.JikesRVM.classloader.*;

/**
 * Entrypoints into the runtime of the virtual machine.
 *
 * <p> These are "helper functions" called from machine code 
 * emitted by VM_Compiler.
 * They implement functionality that cannot be mapped directly 
 * into a small inline
 * sequence of machine instructions. See also: VM_Linker.
 *
 * <p> Note #1: If you add, remove, or change the signature of 
 * any of these methods,
 * you must change VM_Entrypoints to match.
 *
 * <p> Note #2: Code here must be carefully written to be gc-safe 
 * while manipulating
 * stackframe and instruction addresses.
 *
 * <p> Any time we are holding interior pointers to objects that 
 * could be moved by a garbage
 * collection cycle we must either avoid passing through gc-sites 
 * (by writing
 * straight line code with no "non-magic" method invocations) or we 
 * must turn off the
 * collector (so that a gc request inititiated by another thread will 
 * not run until we're
 * done manipulating the bare pointers). Furthermore, while 
 * the collector is turned off,
 * we must be careful not to make any allocation requests ("new").
 *
 * <p> The interior pointers that we must worry about are:
 * <ul>
 *   <li> "ip" values that point to interiors of "code" objects
 *   <li> "fp" values that point to interior of "stack" objects
 * </ul>
 *
 * @author Bowen Alpern
 * @author Derek Lieber
 */
public class VM_Runtime implements VM_Constants {
   
  // Trap codes for communication with C trap handler.
  //
  public static final int TRAP_UNKNOWN        = -1;
  public static final int TRAP_NULL_POINTER   =  0;
  public static final int TRAP_ARRAY_BOUNDS   =  1;
  public static final int TRAP_DIVIDE_BY_ZERO =  2;
  public static final int TRAP_STACK_OVERFLOW =  3;
  public static final int TRAP_CHECKCAST      =  4; // opt-compiler
  public static final int TRAP_REGENERATE     =  5; // opt-compiler
  public static final int TRAP_JNI_STACK      =  6; // jni
  public static final int TRAP_MUST_IMPLEMENT =  7; 
  public static final int TRAP_STORE_CHECK    =  8; // opt-compiler
  public static final int TRAP_STACK_OVERFLOW_FATAL = 9; // assertion checking
   
  //---------------------------------------------------------------//
  //                     Type Checking.                            //
  //---------------------------------------------------------------//

  /**
   * Test if object is instance of target class/array or 
   * implements target interface.
   * @param object object to be tested
   * @param targetID type reference id corresponding to target
   *                 class/array/interface 
   * @return true iff is object instance of target type?
   */ 
  static boolean instanceOf(Object object, int targetID)
    throws NoClassDefFoundError {

    /*  Here, LHS and RHS refer to the way we would treat these if they were
	arguments to an assignment operator and we were testing for
	assignment-compatibility.  In Java, "rhs instanceof lhs" means that 
	the operation "lhs = rhs" would succeed.   This of course is backwards
	if one is looking at it from the point of view of the "instanceof"
	operator.  */
    VM_TypeReference tRef = VM_TypeReference.getTypeRef(targetID);
    VM_Type lhsType = tRef.peekResolvedType();
    if (lhsType == null) {
      lhsType = tRef.resolve();
    }
    if (!lhsType.isResolved()) {
      lhsType.resolve(); // forces loading/resolution of super class/interfaces
    }

    /* Test for null only AFTER we have resolved the type of targetID. */
    if (object == null)
      return false; // null is not an instance of any type

    VM_Type rhsType = VM_ObjectModel.getObjectType(object);
    /* RHS must already be resolved, since we have a non-null object that is 
       an instance of RHS  */
    if (VM.VerifyAssertions)  VM._assert(rhsType.isResolved());
    if (VM.VerifyAssertions)  VM._assert(lhsType.isResolved());

    /* Short-circuit call to isAssignableWith, since we must already be
     * resolved.  */ 
    // isAssignableWith(lhsType, rhsType);
    
    return lhsType == rhsType || VM_DynamicTypeCheck.instanceOfResolved(lhsType, rhsType);
  }

  /**
   * Uninterruptible version for fully resolved proper classes.
   * @param object object to be tested
   * @param id type id corresponding to target class.
   * @return true iff is object instance of target type?
   */
  static boolean instanceOfResolvedClass(Object object, int id) throws VM_PragmaUninterruptible {
    if (object == null)
      return false; // null is not an instance of any type
    
    VM_Class lhsType = VM_Type.getType(id).asClass();
    Object[] rhsTIB = VM_ObjectModel.getTIB(object);
    return VM_DynamicTypeCheck.instanceOfClass(lhsType, rhsTIB);
  }

  /**
   * quick version for final classes, array of final class or array of primitives
   * @param object object to be tested
   * @param jtoc offset of TIB of target type
   * @return true iff is object instance of target type?
   */
  static boolean instanceOfFinal(Object object, int targetTibOffset) throws VM_PragmaUninterruptible {
    if (object == null)
      return false; // null is not an instance of any type

    Object lhsTib= VM_Magic.getObjectAtOffset(VM_Magic.getJTOC(), targetTibOffset);
    Object rhsTib= VM_ObjectModel.getTIB(object);
    return lhsTib == rhsTib;
  }


  /**
   * Throw exception unless object is instance of target 
   * class/array or implements target interface.
   * @param object object to be tested
   * @param id of type reference corresponding to target class/array/interface
   */ 
  static void checkcast(Object object, int id) 
    throws ClassCastException,
	   NoClassDefFoundError {
    if (object == null)
      return; // null may be cast to any type

    VM_TypeReference tRef = VM_TypeReference.getTypeRef(id);
    VM_Type lhsType = tRef.peekResolvedType();
    if (lhsType == null) {
      lhsType = tRef.resolve();
    }
    VM_Type rhsType = VM_ObjectModel.getObjectType(object);
    if (lhsType == rhsType)
      return; // exact match

    // not an exact match, do more involved lookups
    //
    if (!isAssignableWith(lhsType, rhsType)) {
      raiseCheckcastException(lhsType, rhsType);
    }
  }

  /**
   * Throw exception unless object is instance of target resolved proper class.
   * @param object object to be tested
   * @param id of type corresponding to target class
   */ 
  static void checkcastResolvedClass(Object object, int id) throws VM_PragmaUninterruptible {
    if (object == null) return; // null can be cast to any type

    VM_Class lhsType = VM_Type.getType(id).asClass();
    Object[] rhsTIB = VM_ObjectModel.getTIB(object);
    if (!VM_DynamicTypeCheck.instanceOfClass(lhsType, rhsTIB)) {
      VM_Type rhsType = VM_ObjectModel.getObjectType(object);
      raiseCheckcastException(lhsType, rhsType);
    }
  }

  /**
   * quick version for final classes, array of final class or array of primitives
   */
  static void checkcastFinal(Object object, int targetTibOffset) throws VM_PragmaUninterruptible {
    if (object == null) return; // null can be cast to any type

    Object lhsTib= VM_Magic.getObjectAtOffset(VM_Magic.getJTOC(), targetTibOffset);
    Object rhsTib= VM_ObjectModel.getTIB(object);
    if (lhsTib != rhsTib) {
      VM_Type lhsType = VM_Magic.objectAsType(VM_Magic.getObjectAtOffset(lhsTib,TIB_TYPE_INDEX));
      VM_Type rhsType = VM_Magic.objectAsType(VM_Magic.getObjectAtOffset(rhsTib,TIB_TYPE_INDEX));
      raiseCheckcastException(lhsType, rhsType);
    }
  }

  private static final void raiseCheckcastException(VM_Type lhsType, VM_Type rhsType) 
    throws VM_PragmaLogicallyUninterruptible,
	   VM_PragmaUninterruptible {
    throw new ClassCastException("Cannot cast a(n) " + rhsType + " to a(n) " + lhsType);
  }

  /**
   * Throw exception iff array assignment is illegal.
   */
  static void checkstore(Object array, Object arrayElement) throws ArrayStoreException {
    if (arrayElement == null)
      return; // null may be assigned to any type

    VM_Type lhsType = VM_Magic.getObjectType(array);
    VM_Type elmType = lhsType.asArray().getElementType();
      
    if (elmType == VM_Type.JavaLangObjectType) 
      return; // array of Object can receive anything

    VM_Type rhsType = VM_Magic.getObjectType(arrayElement);
     
    if (elmType == rhsType)
      return; // exact type match

    if (isAssignableWith(elmType, rhsType))
      return;

    throw new ArrayStoreException();
  }


  /**
   * May a variable of type "lhs" be assigned a value of type "rhs"?
   * @param lhs type of variable
   * @param rhs type of value
   * @return   true  --> assignment is legal
   *           false --> assignment is illegal
   * <strong>Assumption</strong>: caller has already tested "trivial" case 
   * (exact type match)
   *             so we need not repeat it here
   */ 
  public static boolean isAssignableWith(VM_Type lhs, VM_Type rhs) {
    if (!lhs.isResolved()) {
      lhs.resolve();
    }
    if (!rhs.isResolved()) {
      rhs.resolve();
    }
    return VM_DynamicTypeCheck.instanceOfResolved(lhs, rhs);
  }

      
  //---------------------------------------------------------------//
  //                     Object Allocation.                        //
  //---------------------------------------------------------------//
   
  static int countDownToGC = 500;
  static final int GCInterval  = 100; // how many GC's in a test interval

  /**
   * Allocate something like "new Foo()".
   * @param id id of type reference of class to create.
   * @return object with header installed and all fields set to zero/null
   *           (ready for initializer to be run on it)
   * See also: bytecode 0xbb ("new")
   */ 
  static Object unresolvedNewScalar(int id) 
    throws NoClassDefFoundError, 
	   OutOfMemoryError { 
    VM_TypeReference tRef = VM_TypeReference.getTypeRef(id);
    VM_Type t = tRef.peekResolvedType();
    if (t == null) {
      t = tRef.resolve();
    }
    VM_Class cls = t.asClass();
    if (!cls.isInitialized()) 
      initializeClassForDynamicLink(cls);

    int allocator = MM_Interface.pickAllocator(cls);
    return resolvedNewScalar(cls.getInstanceSize(), 
			     cls.getTypeInformationBlock(), 
			     cls.hasFinalizer(),
			     allocator);
  }
   
  /**
   * Allocate something like "new Foo()".
   * @param size size of object (including header), in bytes
   * @param tib  type information block for object
   * @return object with header installed and all fields set to zero/null
   *           (ready for initializer to be run on it)
   * See also: bytecode 0xbb ("new")
   */
  public static Object resolvedNewScalar(int size, 
					 Object[] tib, 
					 boolean hasFinalizer, 
					 int allocator) 
    throws OutOfMemoryError {

    // GC stress testing
    if (VM.ForceFrequentGC && VM_Scheduler.allProcessorsInitialized) {
      if (countDownToGC-- <= 0) {
	VM.sysWrite("FORCING GC: Countdown trigger in quickNewScalar\n");
	countDownToGC = GCInterval;
	MM_Interface.gc();
      }
    }
    
    // Allocate the object and initialize its header
    Object newObj = MM_Interface.allocateScalar(size, tib, allocator);

    // Deal with finalization
    if (hasFinalizer) MM_Interface.addFinalizer(newObj);

    return newObj;
  }
   
  /**
   * Allocate something like "new Foo[]".
   * @param numElements number of array elements
   * @param id id of type reference of array to create.
   * @return array with header installed and all fields set to zero/null
   * See also: bytecode 0xbc ("anewarray")
   */ 
  static Object unresolvedNewArray(int numElements, int id) 
    throws NoClassDefFoundError, OutOfMemoryError, NegativeArraySizeException { 
    VM_TypeReference tRef = VM_TypeReference.getTypeRef(id);
    VM_Type t = tRef.peekResolvedType();
    if (t == null) {
      t = tRef.resolve();
    }
    VM_Array array = t.asArray();
    if (!array.isInitialized()) {
      array.resolve();
      array.instantiate();
    }

    return resolvedNewArray(numElements, array);
  }

  /**
   * Allocate something like "new Foo[]".
   * @param numElements number of array elements
   * @param array VM_Array of array to create 
   * @return array with header installed and all fields set to zero/null
   * See also: bytecode 0xbc ("anewarray")
   */ 
  public static Object resolvedNewArray(int numElements, VM_Array array) 
    throws OutOfMemoryError, NegativeArraySizeException { 

    return resolvedNewArray(numElements, 
			    array.getLogElementSize(),
			    VM_ObjectModel.computeArrayHeaderSize(array),
			    array.getTypeInformationBlock(),
			    MM_Interface.pickAllocator(array));
  }
   
  /**
   * Allocate something like "new int[cnt]" or "new Foo[cnt]".
   * @param numElements number of array elements
   * @param logElementSize size in bytes of an array element, log base 2.
   * @param headerSize size in bytes of array header
   * @param tib type information block for array object
   * @param allocator int that encodes which allocator should be used
   * @return array object with header installed and all elements set 
   *         to zero/null
   * See also: bytecode 0xbc ("newarray") and 0xbd ("anewarray")
   */ 
  public static Object resolvedNewArray(int numElements, 
					int logElementSize,
					int headerSize, 
					Object[] tib,
					int allocator)
    throws OutOfMemoryError, NegativeArraySizeException {

    if (numElements < 0) raiseNegativeArraySizeException();

    // GC stress testing
    if (VM.ForceFrequentGC && VM_Scheduler.allProcessorsInitialized) {
      if (countDownToGC-- <= 0) {
	VM.sysWrite("FORCING GC: Countdown trigger in quickNewArray\n");
	countDownToGC = GCInterval;
	MM_Interface.gc();
      }
    }

    // Allocate the array and initialize its header
    return MM_Interface.allocateArray(numElements, logElementSize, 
				      headerSize, tib, allocator);
  }


  /**
   * clone a Scalar or Array Object
   * called from java/lang/Object.clone()
   * 
   * For simplicity, we just code this more or less in Java using
   * internal reflective operations and some magic.  
   * This is inefficient for large scalar objects, but until that 
   * is proven to be a  performance problem, we won't worry about it.
   * By keeping this in Java instead of dropping into VM_Memory.copy,
   * we avoid having to add special case code to deal with write barriers,
   * and other such things.
   * 
   * @param obj the object to clone
   * @return the cloned object
   */ 
  public static Object clone (Object obj)
    throws OutOfMemoryError, CloneNotSupportedException {
    VM_Type type = VM_Magic.getObjectType(obj);
    int allocator = MM_Interface.pickAllocator(type);
    if (type.isArrayType()) {
      VM_Array ary   = type.asArray();
      int      nelts = VM_ObjectModel.getArrayLength(obj);
      Object newObj  = resolvedNewArray(nelts, ary);
      System.arraycopy(obj, 0, newObj, 0, nelts);
      return newObj;
    } else {
      if (!(obj instanceof Cloneable))
	throw new CloneNotSupportedException();
      VM_Class cls   = type.asClass();
      int      size  = cls.getInstanceSize();
      Object[] tib   = cls.getTypeInformationBlock();
      Object newObj  = resolvedNewScalar(size, tib, cls.hasFinalizer(), allocator);
      VM_Field[] instanceFields = cls.getInstanceFields();
      for (int i=0; i<instanceFields.length; i++) {
	VM_Field f = instanceFields[i];
	VM_TypeReference ft = f.getType();
	if (ft.isReferenceType()) {
	  // Do via slower "VM-internal reflection" to enable
	  // collectors to do the right thing wrt reference counting
	  // and write barriers.
	  f.setObjectValueUnchecked(newObj, f.getObjectValueUnchecked(obj));
	} else if (ft.isLongType() || ft.isDoubleType()) {
	  int offset = f.getOffset();
	  long bits = VM_Magic.getLongAtOffset(obj, offset);
	  VM_Magic.setLongAtOffset(newObj, offset, bits);
	} else {
	  // NOTE: assumes that all other types get 32 bits.
	  //       This is currently true, but may change in the future.
	  int offset = f.getOffset();
	  int bits = VM_Magic.getIntAtOffset(obj, offset);
	  VM_Magic.setIntAtOffset(newObj, offset, bits);
	}
      }
      return newObj;
    }
  }

  /**
   * Initiate a garbage collection.
   * Called from java/lang/Runtime.
   */ 
  public static void gc () {
    MM_Interface.gc();
  }

  /**
   * Return the approximate amount of free memory available for allocation.
   * Called from /java/lang/Runtime
   */
  public static long freeMemory() {
    return MM_Interface.freeMemory();
  }


  /**
   * Return amount of total memory in the system.
   * Called from /java/lang/Runtime.
   */
  public static long totalMemory() {
    return MM_Interface.totalMemory();
  }

  /**
   * Return maximum amount of memory the system will attempt to use.
   * Called from /java/lang/Runtime.
   */
  public static long maxMemory() {
    return MM_Interface.maxMemory();
  }


  /**
   * Helper function to actually throw the required exception.
   * Keep out of line to mitigate code space when quickNewArray is inlined.
   */
  private static void raiseNegativeArraySizeException()
    throws NegativeArraySizeException, VM_PragmaNoInline {
    throw new NegativeArraySizeException();
  }

  /**
   * Get an object's "hashcode" value.
   *
   * Side effect: hash value is generated and stored into object's 
   * status word.
   * 
   * @return object's hashcode.
   * @see java.lang.Object#hashCode().
   */ 
  public static int getObjectHashCode(Object object) {
      return VM_ObjectModel.getObjectHashCode(object);
  }

  //---------------------------------------------------------------//
  //                        Dynamic linking.                       //
  //---------------------------------------------------------------//

  /**
   * Prepare a class for use prior to first allocation, 
   * field access, or method invocation.
   * Made public so that it is accessible from java.lang.reflect.*.
   * @see VM_MemberReference#needsDynamicLink
   */ 
  public static void initializeClassForDynamicLink(VM_Class cls) 
  {
    if (VM.TraceClassLoading) 
      VM.sysWrite("VM_Runtime.initializeClassForDynamicLink: (begin) " + cls + "\n");

    cls.resolve();
    cls.instantiate();	
    cls.initialize();	// throws ExceptionInInitializerError

    if (VM.TraceClassLoading) 
      VM.sysWrite("VM_Runtime.initializeClassForDynamicLink: (end)   " + cls + "\n");
  }

  //---------------------------------------------------------------//
  //                    Implementation Errors.                     //
  //---------------------------------------------------------------//

  /**
   * Report unexpected method call: interface method 
   * (virtual machine dispatching error, shouldn't happen).
   */ 
  static void unexpectedInterfaceMethodCall() {
    VM.sysFail("interface method dispatching error");
  }
   
  /**
   * Report unexpected method call: abstract method (verification error).
   */
  static void unexpectedAbstractMethodCall() {
    VM.sysWrite("VM_Runtime.unexpectedAbstractMethodCall\n"); 
    throw new AbstractMethodError();
  }
   
  /**
   * Report unimplemented bytecode.
   */ 
  static void unimplementedBytecode(int bytecode) { 
    VM.sysWrite(bytecode);                                  
    VM.sysFail("VM_Runtime.unimplementedBytecode\n");      
  }

  //---------------------------------------------------------------//
  //                    Exception Handling.                        //
  //---------------------------------------------------------------//

  /**
   * Deliver a software exception to current java thread.
   * @param exceptionObject exception object to deliver 
   * (null --> deliver NullPointerException).
   * does not return 
   * (stack is unwound and execution resumes in a catch block)
   */
  static void athrow(Throwable exceptionObject) throws VM_PragmaNoInline {
    VM_Registers registers = new VM_Registers();
    VM.disableGC();              // VM.enableGC() is called when the exception is delivered.
    VM_Magic.saveThreadState(registers);
    registers.inuse = true;
    deliverException(exceptionObject,registers);
  }

  /**
   * Deliver a hardware exception to current java thread.
   * @param trapCode code indicating kind of exception that was trapped 
   * (see TRAP_xxx, above)
   * @param trapInfo array subscript (for array bounds trap, only)
   * does not return 
   * (stack is unwound, starting at trap site, and
   *           execution resumes in a catch block somewhere up the stack)
   *     /or/  execution resumes at instruction following trap 
   *     (for TRAP_STACK_OVERFLOW)
   * 
   * <p> Note:     Control reaches here by the actions of an 
   *           external "C" signal handler
   *           which saves the register state of the trap site into the 
   *           "hardwareExceptionRegisters" field of the current 
   *           VM_Thread object. 
   *           The signal handler also inserts a <hardware trap> frame
   *           onto the stack immediately above this frame, for use by 
   *           VM_HardwareTrapGCMapIterator during garbage collection.
   */
  static void deliverHardwareException(int trapCode, int trapInfo) {

    VM_Thread    myThread           = VM_Thread.getCurrentThread();
    VM_Registers exceptionRegisters = myThread.hardwareExceptionRegisters;

    if ((trapCode == TRAP_STACK_OVERFLOW || trapCode == TRAP_JNI_STACK) && 
	myThread.stack.length < (STACK_SIZE_MAX >> LOG_BYTES_IN_ADDRESS) && 
	!myThread.hasNativeStackFrame()) { 
      // expand stack by the size appropriate for normal or native frame 
      // and resume execution at successor to trap instruction
      // (C trap handler has set register.ip to the instruction following the trap).
      if (trapCode == TRAP_JNI_STACK) {
	VM_Thread.resizeCurrentStack(myThread.stack.length + (STACK_SIZE_JNINATIVE_GROW >> LOG_BYTES_IN_ADDRESS), exceptionRegisters);
      } else {
	VM_Thread.resizeCurrentStack(myThread.stack.length + (STACK_SIZE_GROW >> LOG_BYTES_IN_ADDRESS), exceptionRegisters);
      }
      if (VM.VerifyAssertions) VM._assert(exceptionRegisters.inuse == true); 
      exceptionRegisters.inuse = false;
      VM_Magic.restoreHardwareExceptionState(exceptionRegisters);

      if (VM.VerifyAssertions) VM._assert(NOT_REACHED);
    }

    // GC stress testing
    if (VM.ForceFrequentGC && VM_Scheduler.allProcessorsInitialized) {
      VM.sysWrite("FORCING GC: in deliverHardwareException\n");
      MM_Interface.gc();
    }
    
    Throwable exceptionObject;
    switch (trapCode) {
    case TRAP_NULL_POINTER:
      exceptionObject = new java.lang.NullPointerException();
      break;
    case TRAP_ARRAY_BOUNDS:
      exceptionObject = new java.lang.ArrayIndexOutOfBoundsException(trapInfo);
      break;
    case TRAP_DIVIDE_BY_ZERO:
      exceptionObject = new java.lang.ArithmeticException();
      break;
    case TRAP_STACK_OVERFLOW:
    case TRAP_JNI_STACK:
      exceptionObject = new java.lang.StackOverflowError();
      break;
    case TRAP_CHECKCAST:
      exceptionObject = new java.lang.ClassCastException();
      break;
    case TRAP_MUST_IMPLEMENT:
      exceptionObject = new java.lang.IncompatibleClassChangeError();
      break;
    case TRAP_STORE_CHECK:
      exceptionObject = new java.lang.ArrayStoreException();
      break;
    default:
      exceptionObject = new java.lang.UnknownError();
      VM_Scheduler.traceback("UNKNOWN ERROR");
      break;
    }
      
    VM.disableGC();  // VM.enableGC() is called when the exception is delivered.
    deliverException(exceptionObject, exceptionRegisters);
  }

     
  /**
   * Unlock an object and then deliver a software exception 
   * to current java thread.
   * @param objToUnlock object to unlock 
   * @param objToThrow exception object to deliver 
   * (null --> deliver NullPointerException).
   * does not return (stack is unwound and execution resumes in a catch block)
   */ 
  static void unlockAndThrow(Object objToUnlock, Throwable objToThrow) throws VM_PragmaNoInline {
    VM_ObjectModel.genericUnlock(objToUnlock);
    athrow(objToThrow);
  }

  /**
   * Create and throw a java.lang.ArrayIndexOutOfBoundsException
   * Only used in some configurations where it is easier to make a call
   * then recover the array index from a trap instruction.
   */
  static void raiseArrayIndexOutOfBoundsException(int index) throws VM_PragmaNoInline {
    throw new java.lang.ArrayIndexOutOfBoundsException(index);
  }

  /**
   * Create and throw a java.lang.ArrayIndexOutOfBoundsException
   * Used (rarely) by the opt compiler when it has determined that
   * an array access will unconditionally raise an array bounds check
   * error, but it has lost track of exactly what the index is going to be.
   */
  static void raiseArrayIndexOutOfBoundsException() throws VM_PragmaNoInline {
    throw new java.lang.ArrayIndexOutOfBoundsException();
  }

  /**
   * Create and throw a java.lang.NullPointerException
   * Used in a few circumstances to reduce code space costs
   * of inlining (see java.lang.System.arraycopy()).  Could also
   * be used to raise a null pointer exception without going through
   * the hardware trap handler; currently this is only done when the
   * opt compiler has determined that an instruction will unconditionally
   * raise a null pointer exception.
   */
  public static void raiseNullPointerException() throws VM_PragmaNoInline {
    throw new java.lang.NullPointerException();
  }

  /**
   * Create and throw a java.lang.ArrayStoreException
   * Used in a few circumstances to reduce code space costs
   * of inlining (see java.lang.System.arraycopy()). 
   */
  public static void raiseArrayStoreException() throws VM_PragmaNoInline {
    throw new java.lang.ArrayStoreException();
  }

  /**
   * Create and throw a java.lang.ArithmeticException
   * Used to raise an arithmetic exception without going through
   * the hardware trap handler; currently this is only done when the
   * opt compiler has determined that an instruction will unconditionally
   * raise an arithmetic exception.
   */
  static void raiseArithmeticException() throws VM_PragmaNoInline {
    throw new java.lang.ArithmeticException();
  }

  /**
   * Create and throw a java.lang.AbstractMethodError.
   * Used to handle error cases in invokeinterface dispatching.
   */
  static void raiseAbstractMethodError() throws VM_PragmaNoInline {
    throw new java.lang.AbstractMethodError();
  }

  /**
   * Create and throw a java.lang.IllegalAccessError.
   * Used to handle error cases in invokeinterface dispatching.
   */
  static void raiseIllegalAccessError() throws VM_PragmaNoInline {
    throw new java.lang.IllegalAccessError();
  }



  //----------------//
  // implementation //
  //----------------//
   
  static void init() {
    // tell "RunBootImage.C" to pass control to 
    // "VM_Runtime.deliverHardwareException()"
    // whenever the host operating system detects a hardware trap
    //
    VM_BootRecord.the_boot_record.hardwareTrapMethodId = 
      VM_CompiledMethods.createHardwareTrapCompiledMethod().getId();
    VM_BootRecord.the_boot_record.deliverHardwareExceptionOffset = 
      VM_Entrypoints.deliverHardwareExceptionMethod.getOffset();

    // tell "RunBootImage.C" to set "VM_Scheduler.debugRequested" flag
    // whenever the host operating system detects a debug request signal
    //
    VM_BootRecord.the_boot_record.debugRequestedOffset = 
      VM_Entrypoints.debugRequestedField.getOffset();
  }

  /**
   * Build a multi-dimensional array.
   * @param numElements number of elements to allocate for each dimension
   * @param dimIndex current dimension to build
   * @param arrayType type of array that will result
   * @return array object
   */ 
  public static Object buildMultiDimensionalArray(int methodId,
						  int[] numElements, 
						  VM_Array arrayType) {
    VM_Method method = VM_MemberReference.getMemberRef(methodId).asMethodReference().peekResolvedMethod();
    if (VM.VerifyAssertions) VM._assert(method != null);
    return buildMDAHelper(method, numElements, 0, arrayType);
  }

  public static Object buildMDAHelper (VM_Method method,
				       int[] numElements, 
				       int dimIndex, 
				       VM_Array arrayType) {

    if (!arrayType.isInstantiated()) {
      arrayType.resolve();
      arrayType.instantiate();
    }

    int    nelts     = numElements[dimIndex];
    Object newObject = resolvedNewArray(nelts, arrayType);

    if (++dimIndex == numElements.length)
      return newObject; // all dimensions have been built

    Object[] newArray     = (Object[]) newObject;
    VM_Array newArrayType = arrayType.getElementType().asArray();
   
    for (int i = 0; i < nelts; ++i) {
      newArray[i] = buildMDAHelper(method, numElements, dimIndex, newArrayType);
    }

    return newArray;
  }
   
  /**
   * Deliver an exception to current java thread.
   * <STRONG> Precondition: </STRONG> VM.disableGC has already been called. 
   * --dave
   *  <ol>
   *   <li> exceptionRegisters may not match any reasonable stack 
   *          frame at this point.
   *   <li> we're going to be playing with raw addresses (fp, ip).
   *  </ol> 
   * @param exceptionObject exception object to deliver
   * @param exceptionRegisters register state corresponding to exception site
   * does not return
   * <ul>
   *  <li> stack is unwound and execution resumes in a catch block
   *  <li> <em> or </em> current thread is terminated if no catch block is found
   * </ul>
   */
  private static void deliverException(Throwable exceptionObject, 
				       VM_Registers exceptionRegisters) {
    if (VM.debugOOM) {
      VM.sysWriteln("VM_Runtime.deliverException() entered; just got an exception object.");
    }
    //-#if RVM_FOR_IA32
    VM_Magic.clearFloatingPointState();
    //-#endif
    
    // walk stack and look for a catch block
    //
    if(VM.debugOOM)
      VM.sysWrite("Hunting for a catch block...");
    VM_Type exceptionType = VM_Magic.getObjectType(exceptionObject);
    VM_Address fp = exceptionRegisters.getInnermostFramePointer();
    while (VM_Magic.getCallerFramePointer(fp).NE(STACKFRAME_SENTINEL_FP) ){
      int compiledMethodId = VM_Magic.getCompiledMethodID(fp);
      if (compiledMethodId != INVISIBLE_METHOD_ID) { 
	  VM_CompiledMethod compiledMethod = VM_CompiledMethods.getCompiledMethod(compiledMethodId);
	  VM_ExceptionDeliverer exceptionDeliverer = compiledMethod.getExceptionDeliverer();
	  VM_Address ip = exceptionRegisters.getInnermostInstructionAddress();
	  int ipOffset = compiledMethod.getInstructionOffset(ip);
	  int catchBlockOffset = compiledMethod.findCatchBlockForInstruction(ipOffset, exceptionType);

	  if (catchBlockOffset >= 0  ){ 
	    // found an appropriate catch block
	    if (VM.debugOOM)
	      VM.sysWriteln("found one; delivering.");
	    VM_Address methodStartAddress = VM_Magic.objectAsAddress(compiledMethod.getInstructions());
	    exceptionDeliverer.deliverException(compiledMethod, 
						methodStartAddress.add(catchBlockOffset), 
						exceptionObject, 
						exceptionRegisters);
	    if (VM.VerifyAssertions) VM._assert(NOT_REACHED);
	  }
	  
	  exceptionDeliverer.unwindStackFrame(compiledMethod, exceptionRegisters);
      } else {
	unwindInvisibleStackFrame(exceptionRegisters);
      }
      fp = exceptionRegisters.getInnermostFramePointer();
    }

    if (VM.debugOOM) {
      VM.sysWriteln("Nope.");
      VM.sysWriteln("VM_Runtime.deliverException() found no catch block.");
    }
    /* No appropriate catch block found. */

    VM_Thread.getCurrentThread().dyingWithUncaughtException = true;
    
    /* This should be (but isn't) undoable; ugh.  The heap is shared but the
     * thread isn't.  No way to just give the memory to this particular
     * thread, I think. */
    if (VM.doEmergencyGrowHeap && exceptionObject instanceof OutOfMemoryError)
      MM_Interface.emergencyGrowHeap(5 * (1<<20)); // ask for 5 megs and pray
    handlePossibleRecursiveException();
    VM.enableGC();    
    exceptionObject.printStackTrace();
    VM_Thread.terminate();
    if (VM.VerifyAssertions) VM._assert(NOT_REACHED);
  }

  private static int handlingUncaughtException = 0; // used by handlePossibleRecursiveException()
    

  /** Handle the case of exception handling triggering new exceptions. */
  private static void handlePossibleRecursiveException() {
    ++handlingUncaughtException;
    if (handlingUncaughtException > 1 
	&& handlingUncaughtException <= VM.maxSystemTroubleRecursionDepth + VM.maxSystemTroubleRecursionDepthBeforeWeStopVMSysWrite) {
      VM.sysWrite("We got an uncaught exception while (recursively) handling ");
      VM.sysWrite(handlingUncaughtException - 1);
      VM.sysWrite(" uncaught exception");
      if (handlingUncaughtException - 1 != 1)
	VM.sysWrite("s");
      VM.sysWriteln(".");
    }
    if (handlingUncaughtException > VM.maxSystemTroubleRecursionDepth) {
      VM.dieAbruptlyRecursiveSystemTrouble();
      if (VM.VerifyAssertions) VM._assert(NOT_REACHED);
    }
  }

  /**
   * The current frame is expected to be one of the JNI functions 
   * called from C, 
   * below which is one or more native stack frames
   * Skip over all frames below with saved code pointers outside of heap 
   * (C frames), 
   * stopping at the native frame immediately preceding the glue frame which 
   * contains
   * the method ID of the native method 
   * (this is necessary to allow retrieving the 
   * return address of the glue frame)
   * Ton Ngo 7/30/01
   */
  public static VM_Address unwindNativeStackFrame(VM_Address currfp) throws VM_PragmaUninterruptible {
    VM_Address ip, callee_fp;
    VM_Address fp = VM_Magic.getCallerFramePointer(currfp);

    do {
      callee_fp = fp;
      ip = VM_Magic.getReturnAddress(fp);
      fp = VM_Magic.getCallerFramePointer(fp);
    } while ( !MM_Interface.refInVM(ip) && fp.NE(STACKFRAME_SENTINEL_FP)) ;

	//-#if RVM_FOR_POWERPC
  //-#if RVM_FOR_LINUX || RVM_FOR_OSX
	// for SVR4 convention, a Java-to-C frame has two mini frames,
	// stop before the mini frame 1 whose ip is in VM (out of line machine
	// code), in the case of sentinel fp, it has to return the callee's fp
	// because GC ScanThread uses it to get return address and so on.
	if (MM_Interface.refInVM(ip)) {
      return fp;
	} else {
	  return callee_fp;
	}
	//-#else
	return callee_fp;
  //-#endif // RVM_FOR_LINUX || RVM_FOR_OSX
	//-#else
	return callee_fp;
	//-#endif // RVM_FOR_POWERPC
  }

  /**
   * Unwind stack frame for an <invisible method>.
   * See also: VM_ExceptionDeliverer.unwindStackFrame()
   * 
   * !!TODO: Could be a reflective method invoker frame.
   *        Does it clobber any non-volatiles?
   *        If so, how do we restore them?
   * (I don't think our current implementations of reflective method
   *  invokers save/restore any nonvolatiles, so we're probably ok. 
   *  --dave 6/29/01
   */
  private static void unwindInvisibleStackFrame(VM_Registers registers) {
    registers.unwindStackFrame();
  }
}
