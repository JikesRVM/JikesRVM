/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Entrypoints into the runtime of the virtual machine.
 *
 * These are "helper functions" called from machine code 
 * emitted by VM_Compiler.
 * They implement functionality that cannot be mapped directly 
 * into a small inline
 * sequence of machine instructions. See also: VM_Linker.
 *
 * Note #1: If you add, remove, or change the signature of 
 * any of these methods,
 * you must change VM_Entrypoints.init() to match.
 *
 * Note #2: Code here must be carefully written to be gc-safe 
 * while manipulating
 * stackframe and instruction addresses.
 *
 * Any time we are holding interior pointers to objects that 
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
 * The interior pointers that we must worry about are:
 *   - "ip" values that point to interiors of "code" objects
 *   - "fp" values that point to interior of "stack" objects
 *
 * @author Bowen Alpern
 * @author Derek Lieber
 */
public class VM_Runtime implements VM_Constants {
   
  // Trap codes for communication with C trap handler.
  //
  static final int TRAP_UNKNOWN        = -1;
  static final int TRAP_NULL_POINTER   =  0;
  static final int TRAP_ARRAY_BOUNDS   =  1;
  static final int TRAP_DIVIDE_BY_ZERO =  2;
  static final int TRAP_STACK_OVERFLOW =  3;
  static final int TRAP_CHECKCAST      =  4; // opt-compiler
  static final int TRAP_REGENERATE     =  5; // opt-compiler
  static final int TRAP_JNI_STACK      =  6; // jni
   
  //-----------//
  // interface //
  //-----------//
   
  //---------------------------------------------------------------//
  //                     Type Checking.                            //
  //---------------------------------------------------------------//

  // Test if object is instance of target class/array or 
  // implements target interface.
  // Taken:    object to be tested
  //           jtoc offset of "tib" corresponding to target 
  //           class/array/interface
  // Returned: is object instance of target type?
  //
  static boolean instanceOf(Object object, int targetTibOffset)
    throws VM_ResolutionException {
    if (object == null)
      return false; // null is not an instance of any type

    Object lhsTib = VM_Magic.getObjectAtOffset(VM_Magic.getJTOC(), 
                                               targetTibOffset);
    Object rhsTib = VM_Magic.getObjectAtOffset(object, OBJECT_TIB_OFFSET);
    if (lhsTib == rhsTib)
      return true; // exact match
         
    // not an exact match, do more involved lookups
    //
    VM_Type lhsType = VM_Magic.objectAsType(VM_Magic.getObjectAtOffset
                                            (lhsTib,TIB_TYPE_INDEX));
    VM_Type rhsType = VM_Magic.objectAsType(VM_Magic.getObjectAtOffset
                                            (rhsTib,TIB_TYPE_INDEX));
    return isAssignableWith(lhsType, rhsType);
  }

  // quick version for final classes or array of primitives
  static boolean instanceOfFinal(Object object, int targetTibOffset) {
    if (object == null)
      return false; // null is not an instance of any type

    Object lhsTib= VM_Magic.getObjectAtOffset(VM_Magic.getJTOC(), 
                                              targetTibOffset);
    Object rhsTib= VM_Magic.getObjectAtOffset(object, OBJECT_TIB_OFFSET);
    return (lhsTib == rhsTib);
  }

  // Throw exception unless object is instance of target 
  // class/array or implements target interface.
  // Taken:    object to be tested
  //           jtoc offset of "tib" corresponding to 
  //           target class/array/interface
  // Returned: nothing
  //
  static void checkcast(Object object, int targetTibOffset)
    throws VM_ResolutionException, ClassCastException {
    if (object == null)
      return; // null may be cast to any type
      
    Object lhsTib = VM_Magic.getObjectAtOffset(VM_Magic.getJTOC(), 
                                               targetTibOffset);
    Object rhsTib = VM_Magic.getObjectAtOffset(object, OBJECT_TIB_OFFSET);
    if (lhsTib == rhsTib)
      return; // exact match

    // not an exact match, do more involved lookups
    //
    VM_Type lhsType = VM_Magic.objectAsType(VM_Magic.getObjectAtOffset
                                            (lhsTib,TIB_TYPE_INDEX));
    VM_Type rhsType = VM_Magic.objectAsType(VM_Magic.getObjectAtOffset
                                            (rhsTib,TIB_TYPE_INDEX));
    if (isAssignableWith(lhsType, rhsType))
      return;
    throw new ClassCastException("Cannot cast a(n) " + rhsType + " to a(n) " + lhsType);
  }


  // Throw exception iff array assignment is illegal.
  // Taken:    objects to be tested
  // Returned: nothing
  //
  static void checkstore(Object array, Object arrayElement)
    throws VM_ResolutionException, ArrayStoreException {
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

  // May a variable of type "lhs" be assigned a value of type "rhs"?
  // Taken:    type of variable
  //           type of value
  // Returned: true  --> assignment is legal
  //           false --> assignment is illegal
  // Assumption: caller has already tested "trivial" case 
  // (exact type match)
  //             so we need not repeat it here
  //
  public static boolean isAssignableWith(VM_Type lhs, VM_Type rhs) 
    throws VM_ResolutionException {
    if (VM.BuildForFastDynamicTypeCheck) {
      return VM_DynamicTypeCheck.instanceOf(lhs, rhs);
    } else {
      Object[] rhsTib = rhs.getTypeInformationBlock();
      if (lhs.isResolved()) {
	Object[] lhsTib = lhs.getTypeInformationBlock();
	if (rhsTib[TIB_TYPE_CACHE_TIB_INDEX] == lhsTib) {
	  return true;
	}
      }
      
      boolean rc; 
      synchronized (VM_ClassLoader.lock) {
	rc = lhs.isAssignableWith(rhs);
      }
      
      // update cache of successful type comparisions 
      // (no synchronization required)
      if (rc) {
	Object[] lhsTib = lhs.getTypeInformationBlock();
	rhsTib[TIB_TYPE_CACHE_TIB_INDEX] = lhsTib;
      }

      return rc;
    }
  }

      
  //---------------------------------------------------------------//
  //                     Object Allocation.                        //
  //---------------------------------------------------------------//
   
  // Allocate something like "new Foo()".
  // Taken:    type of object (VM_TypeDictionary id)
  // Returned: object with header installed and all fields set to zero/null
  //           (ready for initializer to be run on it)
  // See also: bytecode 0xbb ("new")
  //
  static Object newScalar(int dictionaryId) 
    throws VM_ResolutionException, OutOfMemoryError { 

    VM_Class cls = VM_TypeDictionary.getValue(dictionaryId).asClass();
    if (VM.VerifyAssertions) VM.assert(cls.isClassType());
    if (!cls.isInitialized())
      initializeClassForDynamicLink(cls);

    Object ret =  VM_Allocator.allocateScalar(cls.getInstanceSize(), 
                                              cls.getTypeInformationBlock(), 
					      cls.hasFinalizer());

    return ret;

  }
   
  // Allocate something like "new Foo()".
  // Taken:    size of object (including header), in bytes
  //           type information block for object
  // Returned: object with header installed and all fields set to zero/null
  //           (ready for initializer to be run on it)
  // See also: bytecode 0xbb ("new")
  //
  static int countDownToGC = 500;
  static final int GCInterval  = 100; // how many GC's in a test interval
  public static Object quickNewScalar(int size, Object[] tib, 
                                      boolean hasFinalizer) 
    throws OutOfMemoryError {
    if (VM.ForceFrequentGC && VM_Scheduler.allProcessorsInitialized) {
      if (countDownToGC-- <= 0) {
	VM.sysWrite("FORCING GC: Countdown trigger in quickNewScalar\n");
	countDownToGC = GCInterval;
	VM_Collector.gc();
      }
    }
    Object ret = VM_Allocator.allocateScalar(size, tib, hasFinalizer);
    return ret;
  }
   
  // Allocate something like "new int[cnt]" or "new Foo[cnt]".
  // Taken:    number of array elements
  //           size of array object (including header), in bytes
  //           type information block for array object
  // Returned: array object with header installed and all elements set 
  // to zero/null
  // See also: bytecode 0xbc ("newarray") and 0xbd ("anewarray")
  //
  public static Object quickNewArray(int numElements, int size, 
                                     Object[] tib)
    throws OutOfMemoryError, NegativeArraySizeException {
    if (numElements < 0) raiseNegativeArraySizeException();
    if (VM.ForceFrequentGC && VM_Scheduler.allProcessorsInitialized) {
      if (countDownToGC-- <= 0) {
	VM.sysWrite("FORCING GC: Countdown trigger in quickNewArray\n");
	countDownToGC = GCInterval;
	VM_Collector.gc();
      }
    }
    Object ret = VM_Allocator.allocateArray(numElements, size, tib);
    return ret;
  }

  // clone a Scalar or Array Object
  // called from java/lang/Object.clone()
  //
  public static Object clone ( Object obj )
    throws OutOfMemoryError, CloneNotSupportedException {
      VM_Type type = VM_Magic.getObjectType(obj);
      if (type.isArrayType()) {
	VM_Array ary   = type.asArray();
	int      nelts = VM_Magic.getArrayLength(obj);
	int      size  = ary.getInstanceSize(nelts);
	Object[] tib   = ary.getTypeInformationBlock();
	return VM_Allocator.allocateArrayClone(nelts, size, tib, obj);
      }
      else {
	if (!(obj instanceof Cloneable))
	  throw new CloneNotSupportedException();
	VM_Class cls   = type.asClass();
	int      size  = cls.getInstanceSize();
	Object[] tib   = cls.getTypeInformationBlock();
	return VM_Allocator.allocateScalarClone(size, tib, obj);
      }
  }


  // initiate a garbage collection
  // called from java/lang/Runtime
  //
  public static void gc () {
    VM_Collector.gc();
  }

  // return amout of free memory available for allocation (approx.)
  // called from /java/lang/Runtime
  //
  public static long freeMemory() {
    return VM_Collector.freeMemory();
  }


  // return amout of total memory in the system
  // called from /java/lang/Runtime
  //
  public static long totalMemory() {
    return VM_Collector.totalMemory();
  }

  //-#if RVM_WITH_GCTk_ALLOC_ADVICE
  //---------------------------------------------------------------//
  //           Object Allocation with "allocator" argument.        //
  //---------------------------------------------------------------//
  private static final boolean debug_alloc_advice = false;

  // alloc advice versions of corresponding calls with allocator argument
  static Object newScalar(int dictionaryId, int allocator) throws VM_ResolutionException, 
  OutOfMemoryError
  { 
    if (debug_alloc_advice) VM.sysWrite("newScalar alloc advised to " + allocator + "\n");
    // Disable profiling during allocation
    if (VM.BuildForProfiling) VM_Profiler.disableProfiling();
    
    VM_Class cls = VM_TypeDictionary.getValue(dictionaryId).asClass();
    if (VM.VerifyAssertions) VM.assert(cls.isClassType());
    if (!cls.isInitialized())
      initializeClassForDynamicLink(cls);

    Object ret =  VM_Allocator.allocateScalar(cls.getInstanceSize(), cls.getTypeInformationBlock(), allocator, cls.hasFinalizer());
    if (VM.BuildForProfiling) VM_Profiler.enableProfiling();
    return ret;
  }

   public static Object quickNewScalar(int size, Object[] tib, boolean hasFinalizer, int allocator)
       throws OutOfMemoryError
   {
     // Disable profiling during allocation
     if (VM.BuildForProfiling) VM_Profiler.disableProfiling();

     Object ret = VM_Allocator.allocateScalar(size, tib, allocator, hasFinalizer);
     if (VM.BuildForProfiling) VM_Profiler.enableProfiling();
     return ret;
   }

   public static Object quickNewScalar(int size, Object[] tib, int allocator) 
       throws OutOfMemoryError
   {
     if (VM.CompileForFinalization)
     {
       boolean hasFinalizer = VM_Magic.addressAsType(VM_Magic.getMemoryWord(VM_Magic.objectAsAddress(tib))).hasFinalizer();
       return quickNewScalar(size, tib, hasFinalizer, allocator);
     } 
     else 
	 return quickNewScalar(size, tib, false, allocator);
   }

   public static Object quickNewArray(int numElements, int size, Object[] tib, int allocator)
       throws OutOfMemoryError, NegativeArraySizeException
   {
     if (numElements < 0) raiseNegativeArraySizeException();
     // Disable profiling during allocation
     if (VM.BuildForProfiling) VM_Profiler.disableProfiling();

     Object ret = VM_Allocator.allocateArray(numElements, size, tib, allocator);

     if (VM.BuildForProfiling) VM_Profiler.enableProfiling();
     return ret;
   }

   public static Object buildMultiDimensionalArray(int[] numElements, int dimIndex, 
						   VM_Array arrayType, int allocator)
   {
     arrayType.load();
     arrayType.resolve();
     arrayType.instantiate();
     
     int    nelts     = numElements[dimIndex];
     int    size      = ARRAY_HEADER_SIZE + (nelts << arrayType.getLogElementSize());
     Object newObject = VM_Allocator.allocateArray(nelts, size, arrayType.getTypeInformationBlock(), allocator);
     
     if (++dimIndex == numElements.length)
       return newObject; // all dimensions have been built
     
     Object[] newArray     = (Object[]) newObject;
     VM_Array newArrayType = arrayType.getElementType().asArray();
   
     for (int i = 0; i < nelts; ++i)
       newArray[i] = buildMultiDimensionalArray(numElements, dimIndex, newArrayType, allocator);
     
     return newArray;
   }
  //-#endif RVM_WITH_GCTk_ALLOC_ADVICE

  // Helper function to actually throw the required exception.
  // Keep out of line to mitigate code space when quickNewArray is inlined.
  private static void raiseNegativeArraySizeException()
    throws NegativeArraySizeException {
    VM_Magic.pragmaNoInline();
    throw new NegativeArraySizeException();
  }


  // Get an object's "hashcode" value.
  // Taken:       object
  // Returned:    object's hashcode
  // Side effect: hash value is generated and stored into object's 
  // status word
  // See also:    java.lang.Object.hashCode()
  //
  public static int getObjectHashCode(Object object) {
    int hashCode = VM_Magic.getIntAtOffset(object, OBJECT_STATUS_OFFSET) 
      & OBJECT_HASHCODE_MASK;
    if (hashCode != 0)
      return hashCode; // object already has a hashcode
       
    // Generate a non-0 hashcode and read it into a local variable, to
    // ensure that we return the same hashcode that we put into 
    // the object's
    // status word. Note that the hashcode generator is not guarded 
    // by a serialization
    // lock, but that's ok because we're not required to guarantee 
    // unique hashcodes.
    //
    do {
      hashCodeGenerator += OBJECT_HASHCODE_UNIT;
      hashCode = hashCodeGenerator & OBJECT_HASHCODE_MASK;
    } while (hashCode == 0);
      
    // Install hashcode.
    //
    while (true) {
      int statusWord = VM_Magic.prepare(object, OBJECT_STATUS_OFFSET);
      if ((statusWord & OBJECT_HASHCODE_MASK) != 0)
	return statusWord & OBJECT_HASHCODE_MASK; // another thread 
                                            // installed a hashcode

      if (VM_Magic.attempt(object, OBJECT_STATUS_OFFSET, statusWord, 
                           statusWord | hashCode))
	return hashCode; // hashcode installed successfully
    }
  }

  //---------------------------------------------------------------//
  //                        Dynamic linking.                       //
  //---------------------------------------------------------------//
   
  // Prepare a class for use prior to first allocation, 
  // field access, or method invocation.
  // See also: VM_Member.needsDynamicLink()
  //
  static void initializeClassForDynamicLink(VM_Class cls) 
    throws VM_ResolutionException {
    if (VM.TraceClassLoading) 
      VM.sysWrite("VM_Runtime.initializeClassForDynamicLink: (begin) " 
                  + cls + "\n");
    synchronized(VM_ClassLoader.lock) {
      cls.load();
      cls.resolve();
      cls.instantiate();
    }
    // class initialization invokes the static init method, which 
    // cannot execute while holding the classloader lock.  
    // Therefore, we release the lock before initializing the class 
    cls.initialize();
    if (VM.TraceClassLoading) 
      VM.sysWrite("VM_Runtime.initializeClassForDynamicLink: (end)   " 
                  + cls + "\n");
  }

  //---------------------------------------------------------------//
  //                        Interface invocation.                  //
  //---------------------------------------------------------------//

  
  // Resolve an interface method call.
  // Taken:    object to which interface method is to be applied
  //           interface method sought (VM_MethodDictionary id)
  // Returned: machine code corresponding to desired interface method
  // See also: bytecode 0xb9 ("invokeinterface")
  //           VM_DynamicTypeCheck.populateITable
  static INSTRUCTION[] invokeInterface(Object target, int dictionaryId) 
    throws IncompatibleClassChangeError, VM_ResolutionException {
    
    VM_Method sought = 
      VM_MethodDictionary.getValue(dictionaryId).resolveInterfaceMethod(true);
    VM_Class I = sought.getDeclaringClass();
    VM_Class C = VM_Magic.getObjectType(target).asClass(); 
    if (VM.BuildForITableInterfaceInvocation) {
      Object[] tib = C.getTypeInformationBlock();
      Object[] iTable = findITable(tib, I.getDictionaryId());
      return (INSTRUCTION[])iTable[I.getITableIndex(sought)];
    } else { 
      if (!isAssignableWith(I, C)) throw new IncompatibleClassChangeError();
      VM_Method found  = C.findVirtualMethod(sought.getName(), 
                                             sought.getDescriptor());
      if (found == null) throw new IncompatibleClassChangeError();
      if (!found.isCompiled()) 
        synchronized(VM_ClassLoader.lock) { found.compile(); }
      INSTRUCTION[] instructions = 
	found.getMostRecentlyGeneratedInstructions();
      return instructions;
    }
  }
  
  // Return a reference to the itable for a given class, interface pair
  // If no itable is found, this version performs a dynamic type check
  // and instantiates the itable.
  // Taken:    the TIB for the class
  //           id of the interface sought
  // Returned: iTable for desired interface
  // See also: bytecode 0xb9 ("invokeinterface")
  //           VM_DynamicTypeCheck.populateITable
  public static Object[] findITable(Object[] tib, int id) 
    throws IncompatibleClassChangeError, VM_ResolutionException {
    Object[] iTables = 
      (Object[])tib[VM_ObjectLayoutConstants.TIB_ITABLES_TIB_INDEX];
    if (VM.DirectlyIndexedITables) {
      // ITable is at fixed offset
      return (Object[])iTables[id];
    } else {
      // Search for the right ITable
      VM_Type I = VM_TypeDictionary.getValue(id);
      if (iTables != null) {
	// check the cache at slot 0
	Object[] iTable = (Object[])iTables[0];
	if (iTable[0] == I) { 
	  return iTable; // cache hit :)
	}
	  
	// cache miss :(
	// Have to search the 'real' entries for the iTable
	for (int i=1; i<iTables.length; i++) {
	  iTable = (Object[])iTables[i];
	  if (iTable[0] == I) { 
	    // found it; update cache
	    iTables[0] = iTable;
	    return iTable;
	  }
        }
      }

      // Didn't find the itable, so we don't yet know if 
      // the class implements the interface. :((( 
      // Therefore, we need to establish that and then 
      // look for the iTable again.
      VM_Class C = (VM_Class)tib[0];
      if (VM.BuildForFastDynamicTypeCheck) {
        VM_DynamicTypeCheck.mandatoryInstanceOfInterface((VM_Class)I, tib);
      } else {
        if (!isAssignableWith(I, C)) throw new IncompatibleClassChangeError();
        VM_DynamicTypeCheck.populateITable(C, (VM_Class)I);
      }
      Object[] iTable = findITable(tib, id);
      if (VM.VerifyAssertions) VM.assert(iTable != null);
      return iTable;
    }
  }

  //---------------------------------------------------------------//
  //                    Implementation Errors.                     //
  //---------------------------------------------------------------//

  // Report unexpected method call: interface method 
  // (virtual machine dispatching error, shouldn't happen).
  //
  static void unexpectedInterfaceMethodCall() {
    VM.sysFail("interface method dispatching error");
  }
   
  // Report unexpected method call: abstract method (verification error).
  //
  static void unexpectedAbstractMethodCall() {
    VM.sysWrite("VM_Runtime.unexpectedAbstractMethodCall\n"); 
    throw new AbstractMethodError();
  }
   
  // Report unexpected method call: native method 
  // (unimplemented virtual machine functionality).
  //
  static void unexpectedNativeMethodCall() {
    VM.sysWrite("VM_Runtime.unexpectedNativeMethodCall\n"); 
    VM_StackTrace.print(VM_StackTrace.create(), System.err);
    throw new UnsatisfiedLinkError();
  }

  // Report unimplemented bytecode.
  //
  static void unimplementedBytecode(int bytecode) { 
    VM.sysWrite(bytecode);                                  
    VM.sysFail("VM_Runtime.unimplementedBytecode\n");      
  }

  //---------------------------------------------------------------//
  //                    Exception Handling.                        //
  //---------------------------------------------------------------//

  // Deliver a software exception to current java thread.
  // Taken:    exception object to deliver 
  // (null --> deliver NullPointerException).
  // Returned: does not return 
  // (stack is unwound and execution resumes in a catch block)
  //
  static void athrow(Throwable exceptionObject) {
    VM_Magic.pragmaNoInline();
    VM_Registers registers = new VM_Registers();
    registers.inuse = true;
    VM.disableGC();              // VM.enableGC() is called when the exception is delivered.
    VM_Magic.saveThreadState(registers);
    deliverException(exceptionObject,registers);
  }

  // Deliver a hardware exception to current java thread.
  // Taken:    code indicating kind of exception that was trapped 
  // (see TRAP_xxx, above)
  //           array subscript (for array bounds trap, only)
  // Returned: does not return 
  // (stack is unwound, starting at trap site, and
  //           execution resumes in a catch block somewhere up the stack)
  //     /or/  execution resumes at instruction following trap 
  //     (for TRAP_STACK_OVERFLOW)
  //
  // Note:     Control reaches here by the actions of an 
  // external "C" signal handler
  //           which saves the register state of the trap site into the 
  //           "hardwareExceptionRegisters" field of the current 
  //           VM_Thread object. 
  //           The signal handler also inserts a <hardware trap> frame
  //           onto the stack immediately above this frame, for use by 
  //           VM_HardwareTrapGCMapIterator during garbage collection.
  //
  static void deliverHardwareException(int trapCode, int trapInfo) {

    VM_Thread    myThread           = VM_Thread.getCurrentThread();
    VM_Registers exceptionRegisters = myThread.hardwareExceptionRegisters;

    if ((trapCode == TRAP_STACK_OVERFLOW || trapCode == TRAP_JNI_STACK) && 
	myThread.stack.length < (STACK_SIZE_MAX >> 2) && 
	!myThread.hasNativeStackFrame()) { 
      // determine whether the method causing the overflow is native
      VM.disableGC();   // because we're holding raw addresses (ip)
      int ip                 = exceptionRegisters.getInnermostInstructionAddress();
      VM_CompiledMethod	compiledMethod = VM_ClassLoader.findMethodForInstruction(ip);
      VM.enableGC();
      VM_CompilerInfo	compilerInfo	= compiledMethod.getCompilerInfo();
      VM_Method 	myMethod	= compiledMethod.getMethod();

      // expand stack by the size appropriate for normal or native frame 
      // and resume execution at successor to trap instruction
      // (C trap handler has set register.ip to the instruction following the trap).
      if (trapCode == TRAP_JNI_STACK) {
	VM_Thread.resizeCurrentStack(myThread.stack.length + (STACK_SIZE_JNINATIVE_GROW >> 2), exceptionRegisters);
	VM.sysWrite("Growing native stack before entry\n");
      } else {
	VM_Thread.resizeCurrentStack(myThread.stack.length + (STACK_SIZE_GROW >> 2), exceptionRegisters); // grow
      }
      if (VM.VerifyAssertions) VM.assert(exceptionRegisters.inuse == true); 
      exceptionRegisters.inuse = false;
      VM_Magic.restoreHardwareExceptionState(exceptionRegisters);

      if (VM.VerifyAssertions) VM.assert(NOT_REACHED);
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
    default:
      exceptionObject = new java.lang.UnknownError();
      VM_Scheduler.traceback("UNKNOWN ERROR");
      break;
    }
      
    VM.disableGC();              // VM.enableGC() is called when the exception is delivered.
    deliverException(exceptionObject, exceptionRegisters);
  }

     
  // Unlock an object and then deliver a software exception to current java thread.
  // Taken:    object to unlock and exception object to deliver (null --> deliver NullPointerException).
  // Returned: does not return (stack is unwound and execution resumes in a catch block)
  //
  static void unlockAndThrow (Object objToUnlock, Throwable objToThrow) {
    VM_Magic.pragmaNoInline();
    VM_Lock.inlineUnlock(objToUnlock);
    athrow(objToThrow);
  }

  // Create and throw a java.lang.ArrayIndexOutOfBoundsException
  // Only used in some configurations where it is easier to make a call
  // then recover the array index from a trap instruction.
  static void raiseArrayIndexOutOfBoundsException(int index) {
    VM_Magic.pragmaNoInline();
    throw new java.lang.ArrayIndexOutOfBoundsException(index);
  }

  // Create and throw a java.lang.ArrayIndexOutOfBoundsException
  // Used (rarely) by the opt compiler when it has determined that
  // an array access will unconditionally raise an array bounds check
  // error, but it has lost track of exactly what the index is going to be.
  static void raiseArrayIndexOutOfBoundsException() {
    VM_Magic.pragmaNoInline();
    throw new java.lang.ArrayIndexOutOfBoundsException();
  }

  // Create and throw a java.lang.NullPointerException
  // Used in a few circumstances to reduce code space costs
  // of inlining (see java.lang.System.arraycopy()).  Could also
  // be used to raise a null pointer exception without going through
  // the hardware trap handler; currently this is only done when the
  // opt compiler has determined that an instruction will unconditionally
  // raise a null pointer exception.
  public static void raiseNullPointerException() {
    VM_Magic.pragmaNoInline();
    throw new java.lang.NullPointerException();
  }

  // Create and throw a java.lang.ArithmeticException
  // Used to raise an arithmetic exception without going through
  // the hardware trap handler; currently this is only done when the
  // opt compiler has determined that an instruction will unconditionally
  // raise an arithmetic exception.
  static void raiseArithmeticException() {
    VM_Magic.pragmaNoInline();
    throw new java.lang.ArithmeticException();
  }


  //----------------//
  // implementation //
  //----------------//
   
  private static int hashCodeGenerator; // seed for generating Object hash codes

  static void init() {
    // tell "RunBootImage.C" to pass control to "VM_Runtime.deliverHardwareException()"
    // whenever the host operating system detects a hardware trap
    //
    VM_BootRecord.the_boot_record.hardwareTrapMethodId = VM_ClassLoader.createHardwareTrapCompiledMethodId();
    VM_BootRecord.the_boot_record.deliverHardwareExceptionOffset = VM.getMember("LVM_Runtime;", "deliverHardwareException", "(II)V").getOffset();

    // tell "RunBootImage.C" to set "VM_Scheduler.debugRequested" flag
    // whenever the host operating system detects a debug request signal
    //
    VM_BootRecord.the_boot_record.debugRequestedOffset = VM.getMember("LVM_Scheduler;", "debugRequested", "Z").getOffset();

    // for "libjni.C" to make AttachCurrentThread request
    VM_BootRecord.the_boot_record.attachThreadRequestedOffset = VM.getMember("LVM_Scheduler;", "attachThreadRequested", "I").getOffset();
  }

  // Build a multi-dimensional array.
  // Taken:    number of elements to allocate for each dimension
  //           current dimension to build
  //           type of array that will result
  // Returned: array object
  //
  public static Object buildMultiDimensionalArray(int[] numElements, 
						  int dimIndex, 
						  VM_Array arrayType) {
    if (!arrayType.isInstantiated()) {
      synchronized(VM_ClassLoader.lock) {
	arrayType.load();
	arrayType.resolve();
	arrayType.instantiate();
      }
    }

    int    nelts     = numElements[dimIndex];
    int    size      = ARRAY_HEADER_SIZE + (nelts << arrayType.getLogElementSize());
    Object newObject = VM_Allocator.allocateArray(nelts, size, arrayType.getTypeInformationBlock());

    if (++dimIndex == numElements.length)
      return newObject; // all dimensions have been built

    Object[] newArray     = (Object[]) newObject;
    VM_Array newArrayType = arrayType.getElementType().asArray();
   
    for (int i = 0; i < nelts; ++i)
      newArray[i] = buildMultiDimensionalArray(numElements, dimIndex, newArrayType);

    return newArray;
  }
   
  // Deliver an exception to current java thread.
  // Precondition: VM.disableGC has already been called. --dave
  //      (1) exceptionRegisters may not match any reasonable stack 
  //          frame at this point.
  //      (2) we're going to be playing with raw addresses (fp, ip).
  // Taken:    exception object to deliver
  //           register state corresponding to exception site
  // Returned: does not return
  //           - stack is unwound and execution resumes in a catch block
  //    /or/   - current thread is terminated if no catch block is found
  //
  private static void deliverException(Throwable exceptionObject, 
				       VM_Registers exceptionRegisters) {
    if (VM.TraceTimes) VM_Timer.start(VM_Timer.EXCEPTION_HANDLING);

    // walk stack and look for a catch block
    //
    VM_Type exceptionType = VM_Magic.getObjectType(exceptionObject);
    int fp = exceptionRegisters.getInnermostFramePointer();
    while (VM_Magic.getCallerFramePointer(fp) != STACKFRAME_SENTINAL_FP) {
      int compiledMethodId = VM_Magic.getCompiledMethodID(fp);
      if (compiledMethodId != INVISIBLE_METHOD_ID) { 
	VM_CompiledMethod     compiledMethod     = VM_ClassLoader.getCompiledMethod(compiledMethodId);
	VM_CompilerInfo       compilerInfo       = compiledMethod.getCompilerInfo();
	VM_ExceptionDeliverer exceptionDeliverer = compilerInfo.getExceptionDeliverer();
	int ip                 = exceptionRegisters.getInnermostInstructionAddress();
	int methodStartAddress = VM_Magic.objectAsAddress(compiledMethod.getInstructions());
	int catchBlockOffset   = compilerInfo.findCatchBlockForInstruction(ip - methodStartAddress, exceptionType);

	if (catchBlockOffset >= 0) { 
	  // found an appropriate catch block
	  if (VM.TraceTimes) VM_Timer.stop(VM_Timer.EXCEPTION_HANDLING);
	  exceptionDeliverer.deliverException(compiledMethod, methodStartAddress + catchBlockOffset, exceptionObject, exceptionRegisters);
	  if (VM.VerifyAssertions) VM.assert(NOT_REACHED);
	}

	exceptionDeliverer.unwindStackFrame(compiledMethod, exceptionRegisters);
      } else {
	unwindInvisibleStackFrame(exceptionRegisters);
      }
      fp = exceptionRegisters.getInnermostFramePointer();
    }

    // no appropriate catch block found
    //
    if (VM.TraceTimes) VM_Timer.stop(VM_Timer.EXCEPTION_HANDLING);
    VM.enableGC();
    exceptionObject.printStackTrace();
    VM_Thread.terminate();
    if (VM.VerifyAssertions) VM.assert(NOT_REACHED);
  }

  /**
   * The current frame is expected to be one of the JNI functions called from C, 
   * below which is one or more native stack frames
   * Skip over all frames below with saved code pointers outside of heap (C frames), 
   * stopping at the native frame immediately preceding the glue frame which contains
   * the method ID of the native method (this is necessary to allow retrieving the 
   * return address of the glue frame)
   * Ton Ngo 7/30/01
   */
  public static int unwindNativeStackFrame(int currfp) {
    int ip, callee_fp;
    int fp = VM_Magic.getCallerFramePointer(currfp);
    int vmStart = VM_BootRecord.the_boot_record.startAddress;
    int vmEnd = VM_BootRecord.the_boot_record.largeStart + VM_BootRecord.the_boot_record.largeSize;
    do {
      callee_fp = fp;
      ip = VM_Magic.getReturnAddress(fp);
      fp = VM_Magic.getCallerFramePointer(fp);
    } while ( ( ip < vmStart || ip >= vmEnd ) && fp != STACKFRAME_SENTINAL_FP);
    return callee_fp;
  }

  // Unwind stack frame for an <invisible method>.
  // See also: VM_ExceptionDeliverer.unwindStackFrame()
  //
  //!!TODO: Could be a reflective method invoker frame.
  //        Does it clobber any non-volatiles?
  //        If so, how do we restore them?
  // (I don't think our current implementations of reflective method
  //  invokers save/restore any nonvolatiles, so we're probably ok. --dave 6/29/01
  private static void unwindInvisibleStackFrame(VM_Registers registers) {
    registers.unwindStackFrame();
  }
}
