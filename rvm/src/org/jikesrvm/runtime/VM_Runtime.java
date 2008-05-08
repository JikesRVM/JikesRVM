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

import org.jikesrvm.ArchitectureSpecific;
import org.jikesrvm.ArchitectureSpecific.VM_Registers;
import org.jikesrvm.VM;
import org.jikesrvm.VM_Constants;
import org.jikesrvm.classloader.VM_Array;
import org.jikesrvm.classloader.VM_Class;
import org.jikesrvm.classloader.VM_DynamicTypeCheck;
import org.jikesrvm.classloader.VM_Field;
import org.jikesrvm.classloader.VM_MemberReference;
import org.jikesrvm.classloader.VM_Method;
import org.jikesrvm.classloader.VM_Type;
import org.jikesrvm.classloader.VM_TypeReference;
import org.jikesrvm.compilers.common.VM_CompiledMethod;
import org.jikesrvm.compilers.common.VM_CompiledMethods;
import org.jikesrvm.memorymanagers.mminterface.MM_Interface;
import org.jikesrvm.objectmodel.VM_ObjectModel;
import org.jikesrvm.objectmodel.VM_TIB;
import org.jikesrvm.scheduler.VM_Scheduler;
import org.jikesrvm.scheduler.VM_Thread;
import org.vmmagic.pragma.Entrypoint;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.LogicallyUninterruptible;
import org.vmmagic.pragma.NoInline;
import org.vmmagic.pragma.Pure;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;

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
 * any of these methods you may need to change VM_Entrypoints to match.
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
 * collector (so that a gc request initiated by another thread will
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
 */
public class VM_Runtime implements VM_Constants, ArchitectureSpecific.VM_StackframeLayoutConstants {

  // Trap codes for communication with C trap handler.
  //
  public static final int TRAP_UNKNOWN = -1;
  public static final int TRAP_NULL_POINTER = 0;
  public static final int TRAP_ARRAY_BOUNDS = 1;
  public static final int TRAP_DIVIDE_BY_ZERO = 2;
  public static final int TRAP_STACK_OVERFLOW = 3;
  public static final int TRAP_CHECKCAST = 4; // opt-compiler
  public static final int TRAP_REGENERATE = 5; // opt-compiler
  public static final int TRAP_JNI_STACK = 6; // jni
  public static final int TRAP_MUST_IMPLEMENT = 7;
  public static final int TRAP_STORE_CHECK = 8; // opt-compiler
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
  @Entrypoint
  static boolean instanceOf(Object object, int targetID) throws NoClassDefFoundError {

    /*  Here, LHS and RHS refer to the way we would treat these if they were
        arguments to an assignment operator and we were testing for
        assignment-compatibility.  In Java, "rhs instanceof lhs" means that
        the operation "lhs = rhs" would succeed.   This of course is backwards
        if one is looking at it from the point of view of the "instanceof"
        operator.  */
    VM_TypeReference tRef = VM_TypeReference.getTypeRef(targetID);
    VM_Type lhsType = tRef.peekType();
    if (lhsType == null) {
      lhsType = tRef.resolve();
    }
    if (!lhsType.isResolved()) {
      lhsType.resolve(); // forces loading/resolution of super class/interfaces
    }

    /* Test for null only AFTER we have resolved the type of targetID. */
    if (object == null) {
      return false; // null is not an instance of any type
    }

    VM_Type rhsType = VM_ObjectModel.getObjectType(object);
    /* RHS must already be resolved, since we have a non-null object that is
       an instance of RHS  */
    if (VM.VerifyAssertions) VM._assert(rhsType.isResolved());
    if (VM.VerifyAssertions) VM._assert(lhsType.isResolved());

    return lhsType == rhsType || VM_DynamicTypeCheck.instanceOfResolved(lhsType, rhsType);
  }

  /**
   * Uninterruptible version for fully resolved proper classes.
   * @param object object to be tested
   * @param id type id corresponding to target class.
   * @return true iff is object instance of target type?
   */
  @Uninterruptible
  @Entrypoint
  static boolean instanceOfResolvedClass(Object object, int id) {
    if (object == null) {
      return false; // null is not an instance of any type
    }

    VM_Class lhsType = VM_Type.getType(id).asClass();
    VM_TIB rhsTIB = VM_ObjectModel.getTIB(object);
    return VM_DynamicTypeCheck.instanceOfClass(lhsType, rhsTIB);
  }

  /**
   * Quick version for final classes, array of final class or array of
   * primitives
   *
   * @param object Object to be tested
   * @param targetTibOffset  JTOC offset of TIB of target type
   *
   * @return <code>true</code> iff  <code>object</code> is instance of the
   *         target type
   */
  @Uninterruptible
  @Entrypoint
  static boolean instanceOfFinal(Object object, Offset targetTibOffset) {
    if (object == null) {
      return false; // null is not an instance of any type
    }

    Object lhsTib = VM_Magic.getObjectAtOffset(VM_Magic.getJTOC(), targetTibOffset);
    Object rhsTib = VM_ObjectModel.getTIB(object);
    return lhsTib == rhsTib;
  }

  /**
   * Throw exception unless object is instance of target
   * class/array or implements target interface.
   * @param object object to be tested
   * @param id of type reference corresponding to target class/array/interface
   */
  @Entrypoint
  static void checkcast(Object object, int id) throws ClassCastException, NoClassDefFoundError {
    if (object == null) {
      return; // null may be cast to any type
    }

    VM_TypeReference tRef = VM_TypeReference.getTypeRef(id);
    VM_Type lhsType = tRef.peekType();
    if (lhsType == null) {
      lhsType = tRef.resolve();
    }
    VM_Type rhsType = VM_ObjectModel.getObjectType(object);
    if (lhsType == rhsType) {
      return; // exact match
    }

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
  @Uninterruptible
  @Entrypoint
  static void checkcastResolvedClass(Object object, int id) {
    if (object == null) return; // null can be cast to any type

    VM_Class lhsType = VM_Type.getType(id).asClass();
    VM_TIB rhsTIB = VM_ObjectModel.getTIB(object);
    if (VM.VerifyAssertions) {
      VM._assert(rhsTIB != null);
    }
    if (!VM_DynamicTypeCheck.instanceOfClass(lhsType, rhsTIB)) {
      VM_Type rhsType = VM_ObjectModel.getObjectType(object);
      raiseCheckcastException(lhsType, rhsType);
    }
  }

  /**
   * quick version for final classes, array of final class or array of primitives
   */
  @Uninterruptible
  @Entrypoint
  static void checkcastFinal(Object object, Offset targetTibOffset) {
    if (object == null) return; // null can be cast to any type

    Object lhsTib = VM_Magic.getObjectAtOffset(VM_Magic.getJTOC(), targetTibOffset);
    Object rhsTib = VM_ObjectModel.getTIB(object);
    if (lhsTib != rhsTib) {
      VM_Type lhsType =
          VM_Magic.objectAsType(VM_Magic.getObjectAtOffset(lhsTib,
                                                           Offset.fromIntZeroExtend(TIB_TYPE_INDEX <<
                                                                                    LOG_BYTES_IN_ADDRESS)));
      VM_Type rhsType =
          VM_Magic.objectAsType(VM_Magic.getObjectAtOffset(rhsTib,
                                                           Offset.fromIntZeroExtend(TIB_TYPE_INDEX <<
                                                                                    LOG_BYTES_IN_ADDRESS)));
      raiseCheckcastException(lhsType, rhsType);
    }
  }

  @LogicallyUninterruptible
  @Uninterruptible
  private static void raiseCheckcastException(VM_Type lhsType, VM_Type rhsType) {
    throw new ClassCastException("Cannot cast a(n) " + rhsType + " to a(n) " + lhsType);
  }

  /**
   * Throw exception iff array assignment is illegal.
   */
  @Entrypoint
  static void checkstore(Object array, Object arrayElement) throws ArrayStoreException {
    if (arrayElement == null) {
      return; // null may be assigned to any type
    }

    VM_Type lhsType = VM_Magic.getObjectType(array);
    VM_Type elmType = lhsType.asArray().getElementType();

    if (elmType == VM_Type.JavaLangObjectType) {
      return; // array of Object can receive anything
    }

    VM_Type rhsType = VM_Magic.getObjectType(arrayElement);

    if (elmType == rhsType) {
      return; // exact type match
    }

    if (isAssignableWith(elmType, rhsType)) {
      return;
    }

    throw new ArrayStoreException();
  }

  /**
   * May a variable of type "lhs" be assigned a value of type "rhs"?
   * @param lhs type of variable
   * @param rhs type of value
   * @return true  --> assignment is legal
   *           false --> assignment is illegal
   * <strong>Assumption</strong>: caller has already tested "trivial" case
   * (exact type match)
   *             so we need not repeat it here
   */
  @Pure
  @Inline(value=Inline.When.AllArgumentsAreConstant)
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

  /**
   * Allocate something like "new Foo()".
   * @param id id of type reference of class to create.
   * @return object with header installed and all fields set to zero/null
   *           (ready for initializer to be run on it)
   * See also: bytecode 0xbb ("new")
   */
  @Entrypoint
  static Object unresolvedNewScalar(int id, int site) throws NoClassDefFoundError, OutOfMemoryError {
    VM_TypeReference tRef = VM_TypeReference.getTypeRef(id);
    VM_Type t = tRef.peekType();
    if (t == null) {
      t = tRef.resolve();
    }
    VM_Class cls = t.asClass();
    if (!cls.isInitialized()) {
      initializeClassForDynamicLink(cls);
    }

    int allocator = MM_Interface.pickAllocator(cls);
    int align = VM_ObjectModel.getAlignment(cls);
    int offset = VM_ObjectModel.getOffsetForAlignment(cls);
    return resolvedNewScalar(cls.getInstanceSize(),
                             cls.getTypeInformationBlock(),
                             cls.hasFinalizer(),
                             allocator,
                             align,
                             offset,
                             site);
  }

  /**
   * Allocate something like "new Foo()".
   * @param cls VM_Class of array to create
   * @return object with header installed and all fields set to zero/null
   *           (ready for initializer to be run on it)
   * See also: bytecode 0xbb ("new")
   */
  public static Object resolvedNewScalar(VM_Class cls) {

    int allocator = MM_Interface.pickAllocator(cls);
    int site = MM_Interface.getAllocationSite(false);
    int align = VM_ObjectModel.getAlignment(cls);
    int offset = VM_ObjectModel.getOffsetForAlignment(cls);
    return resolvedNewScalar(cls.getInstanceSize(),
                             cls.getTypeInformationBlock(),
                             cls.hasFinalizer(),
                             allocator,
                             align,
                             offset,
                             site);
  }

  /**
   * Allocate something like "new Foo()".
   * @param size size of object (including header), in bytes
   * @param tib  type information block for object
   * @param hasFinalizer does this type have a finalizer?
   * @param allocator int that encodes which allocator should be used
   * @param align the alignment requested; must be a power of 2.
   * @param offset the offset at which the alignment is desired.
   * @param site the site id of the calling allocation site
   * @return object with header installed and all fields set to zero/null
   *           (ready for initializer to be run on it)
   * See also: bytecode 0xbb ("new")
   */
  @Entrypoint
  public static Object resolvedNewScalar(int size, VM_TIB tib, boolean hasFinalizer, int allocator, int align,
                                         int offset, int site) throws OutOfMemoryError {

    // GC stress testing
    if (VM.ForceFrequentGC) checkAllocationCountDownToGC();

    // Allocate the object and initialize its header
    Object newObj = MM_Interface.allocateScalar(size, tib, allocator, align, offset, site);

    // Deal with finalization
    if (hasFinalizer) MM_Interface.addFinalizer(newObj);

    return newObj;
  }

  /**
   * Allocate something like "new Foo[]".
   * @param numElements number of array elements
   * @param id id of type reference of array to create.
   * @param site the site id of the calling allocation site
   * @return array with header installed and all fields set to zero/null
   * See also: bytecode 0xbc ("anewarray")
   */
  @Entrypoint
  public static Object unresolvedNewArray(int numElements, int id, int site)
      throws NoClassDefFoundError, OutOfMemoryError, NegativeArraySizeException {
    VM_TypeReference tRef = VM_TypeReference.getTypeRef(id);
    VM_Type t = tRef.peekType();
    if (t == null) {
      t = tRef.resolve();
    }
    VM_Array array = t.asArray();
    if (!array.isInitialized()) {
      array.resolve();
      array.instantiate();
    }

    return resolvedNewArray(numElements, array, site);
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
    return resolvedNewArray(numElements, array, MM_Interface.getAllocationSite(false));
  }

  public static Object resolvedNewArray(int numElements, VM_Array array, int site)
      throws OutOfMemoryError, NegativeArraySizeException {

    return resolvedNewArray(numElements,
                            array.getLogElementSize(),
                            VM_ObjectModel.computeArrayHeaderSize(array),
                            array.getTypeInformationBlock(),
                            MM_Interface.pickAllocator(array),
                            VM_ObjectModel.getAlignment(array),
                            VM_ObjectModel.getOffsetForAlignment(array),
                            site);
  }

  /**
   * Allocate something like "new int[cnt]" or "new Foo[cnt]".
   * @param numElements number of array elements
   * @param logElementSize size in bytes of an array element, log base 2.
   * @param headerSize size in bytes of array header
   * @param tib type information block for array object
   * @param allocator int that encodes which allocator should be used
   * @param align the alignment requested; must be a power of 2.
   * @param offset the offset at which the alignment is desired.
   * @return array object with header installed and all elements set
   *         to zero/null
   * See also: bytecode 0xbc ("newarray") and 0xbd ("anewarray")
   */
  @Entrypoint
  public static Object resolvedNewArray(int numElements, int logElementSize, int headerSize, VM_TIB tib,
                                        int allocator, int align, int offset, int site)
      throws OutOfMemoryError, NegativeArraySizeException {

    if (numElements < 0) raiseNegativeArraySizeException();

    // GC stress testing
    if (VM.ForceFrequentGC) checkAllocationCountDownToGC();

    // Allocate the array and initialize its header
    return MM_Interface.allocateArray(numElements, logElementSize, headerSize, tib, allocator, align, offset, site);
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
   * This method calls specific cloning routines based on type to help
   * guide the inliner (which won't inline a single large method).
   *
   * @param obj the object to clone
   * @return the cloned object
   */
  public static Object clone(Object obj) throws OutOfMemoryError, CloneNotSupportedException {
    VM_Type type = VM_Magic.getObjectType(obj);
    if (type.isArrayType()) {
      return cloneArray(obj, type);
    } else {
      return cloneClass(obj, type);
    }
  }

  /**
   * Clone an array
   *
   * @param obj the array to clone
   * @param type the type information for the array
   * @return the cloned object
   */
  private static Object cloneArray(Object obj, VM_Type type) throws OutOfMemoryError {
    VM_Array ary = type.asArray();
    int nelts = VM_ObjectModel.getArrayLength(obj);
    Object newObj = resolvedNewArray(nelts, ary);
    System.arraycopy(obj, 0, newObj, 0, nelts);
    return newObj;
  }

  /**
   * Clone an object implementing a class - check that the class is cloneable
   * (we make this a small method with just a test so that the inliner will
   * inline it and hopefully eliminate the instanceof test).
   *
   * @param obj the object to clone
   * @param type the type information for the class
   * @return the cloned object
   */
  private static Object cloneClass(Object obj, VM_Type type) throws OutOfMemoryError, CloneNotSupportedException {
    if (!(obj instanceof Cloneable)) {
      throw new CloneNotSupportedException();
    } else {
      return cloneClass2(obj, type);
    }
  }

  /**
   * Clone an object implementing a class - the actual clone
   *
   * @param obj the object to clone
   * @param type the type information for the class
   * @return the cloned object
   */
  private static Object cloneClass2(Object obj, VM_Type type) throws OutOfMemoryError {
    VM_Class cls = type.asClass();
    Object newObj = resolvedNewScalar(cls);
    for (VM_Field f : cls.getInstanceFields()) {
      int size = f.getSize();
      if (VM.BuildFor32Addr) {
        if (size == BYTES_IN_INT) {
          if (f.isReferenceType()) {
            // Do via slower "VM-internal reflection" to enable
            // collectors to do the right thing wrt reference counting
            // and write barriers.
            f.setObjectValueUnchecked(newObj, f.getObjectValueUnchecked(obj));
          } else {
            Offset offset = f.getOffset();
            int bits = VM_Magic.getIntAtOffset(obj, offset);
            VM_Magic.setIntAtOffset(newObj, offset, bits);
          }
          continue;
        } else if (size == BYTES_IN_LONG) {
          Offset offset = f.getOffset();
          long bits = VM_Magic.getLongAtOffset(obj, offset);
          VM_Magic.setLongAtOffset(newObj, offset, bits);
          continue;
        }
      } else {
        // BuildFor64Addr
        if (size == BYTES_IN_LONG) {
          if (f.isReferenceType()) {
            // Do via slower "VM-internal reflection" to enable
            // collectors to do the right thing wrt reference counting
            // and write barriers.
            f.setObjectValueUnchecked(newObj, f.getObjectValueUnchecked(obj));
          } else {
            Offset offset = f.getOffset();
            long bits = VM_Magic.getLongAtOffset(obj, offset);
            VM_Magic.setLongAtOffset(newObj, offset, bits);
          }
          continue;
        } else if (size == BYTES_IN_INT) {
          Offset offset = f.getOffset();
          int bits = VM_Magic.getIntAtOffset(obj, offset);
          VM_Magic.setIntAtOffset(newObj, offset, bits);
          continue;
        }
      }
      if (size == BYTES_IN_CHAR) {
        Offset offset = f.getOffset();
        char bits = VM_Magic.getCharAtOffset(obj, offset);
        VM_Magic.setCharAtOffset(newObj, offset, bits);
      } else {
        if (VM.VerifyAssertions) VM._assert(size == BYTES_IN_BYTE);
        Offset offset = f.getOffset();
        byte bits = VM_Magic.getByteAtOffset(obj, offset);
        VM_Magic.setByteAtOffset(newObj, offset, bits);
      }
    }
    return newObj;
  }

  /**
   * Helper function to actually throw the required exception.
   * Keep out of line to mitigate code space when quickNewArray is inlined.
   */
  @NoInline
  private static void raiseNegativeArraySizeException() throws NegativeArraySizeException {
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
  public static void initializeClassForDynamicLink(VM_Class cls) {
    if (VM.TraceClassLoading) {
      VM.sysWrite("VM_Runtime.initializeClassForDynamicLink: (begin) " + cls + "\n");
    }

    cls.resolve();
    cls.instantiate();
    cls.initialize();   // throws ExceptionInInitializerError

    if (VM.TraceClassLoading) {
      VM.sysWrite("VM_Runtime.initializeClassForDynamicLink: (end)   " + cls + "\n");
    }
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
  @Entrypoint
  static void unexpectedAbstractMethodCall() {
    VM.sysWrite("VM_Runtime.unexpectedAbstractMethodCall\n");
    throw new AbstractMethodError();
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
   *
   * This method is public so that it can be invoked by java.lang.VMClass.
   */
  @NoInline
  @Entrypoint
  public static void athrow(Throwable exceptionObject) {
    VM_Thread myThread = VM_Scheduler.getCurrentThread();
    VM_Registers exceptionRegisters = myThread.getExceptionRegisters();
    VM.disableGC();              // VM.enableGC() is called when the exception is delivered.
    VM_Magic.saveThreadState(exceptionRegisters);
    exceptionRegisters.inuse = true;
    deliverException(exceptionObject, exceptionRegisters);
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
   *           "exceptionRegisters" field of the current
   *           VM_Thread object.
   *           The signal handler also inserts a <hardware trap> frame
   *           onto the stack immediately above this frame, for use by
   *           VM_HardwareTrapGCMapIterator during garbage collection.
   */
  @Entrypoint
  static void deliverHardwareException(int trapCode, int trapInfo) {

    VM_Thread myThread = VM_Scheduler.getCurrentThread();
    VM_Registers exceptionRegisters = myThread.getExceptionRegisters();

    if ((trapCode == TRAP_STACK_OVERFLOW || trapCode == TRAP_JNI_STACK) &&
        myThread.getStack().length < (STACK_SIZE_MAX >> LOG_BYTES_IN_ADDRESS) &&
        !myThread.hasNativeStackFrame()) {
      // expand stack by the size appropriate for normal or native frame
      // and resume execution at successor to trap instruction
      // (C trap handler has set register.ip to the instruction following the trap).
      if (trapCode == TRAP_JNI_STACK) {
        VM_Thread.resizeCurrentStack(myThread.getStackLength() + STACK_SIZE_JNINATIVE_GROW, exceptionRegisters);
      } else {
        VM_Thread.resizeCurrentStack(myThread.getStackLength() + STACK_SIZE_GROW, exceptionRegisters);
      }
      if (VM.VerifyAssertions) VM._assert(exceptionRegisters.inuse);
      exceptionRegisters.inuse = false;
      VM_Magic.restoreHardwareExceptionState(exceptionRegisters);

      if (VM.VerifyAssertions) VM._assert(NOT_REACHED);
    }

    // GC stress testing
    if (canForceGC()) {
      //VM.sysWrite("FORCING GC: in deliverHardwareException\n");
      System.gc();
    }

    // Sanity checking.
    // Hardware traps in uninterruptible code should be considered hard failures.
    if (!VM.sysFailInProgress()) {
      Address fp = exceptionRegisters.getInnermostFramePointer();
      int compiledMethodId = VM_Magic.getCompiledMethodID(fp);
      if (compiledMethodId != INVISIBLE_METHOD_ID) {
        VM_CompiledMethod compiledMethod = VM_CompiledMethods.getCompiledMethod(compiledMethodId);
        Address ip = exceptionRegisters.getInnermostInstructionAddress();
        Offset instructionOffset = compiledMethod.getInstructionOffset(ip);
        if (compiledMethod.isWithinUninterruptibleCode(instructionOffset)) {
          switch (trapCode) {
          case TRAP_NULL_POINTER:
            VM.sysWriteln("\nFatal error: NullPointerException within uninterruptible region.");
            break;
          case TRAP_ARRAY_BOUNDS:
            VM.sysWriteln("\nFatal error: ArrayIndexOutOfBoundsException within uninterruptible region (index was ", trapInfo, ").");
            break;
          case TRAP_DIVIDE_BY_ZERO:
            VM.sysWriteln("\nFatal error: DivideByZero within uninterruptible region.");
            break;
          case TRAP_STACK_OVERFLOW:
          case TRAP_JNI_STACK:
            VM.sysWriteln("\nFatal error: StackOverflowError within uninterruptible region.");
            break;
          case TRAP_CHECKCAST:
            VM.sysWriteln("\nFatal error: ClassCastException within uninterruptible region.");
            break;
          case TRAP_MUST_IMPLEMENT:
            VM.sysWriteln("\nFatal error: IncompatibleClassChangeError within uninterruptible region.");
            break;
          case TRAP_STORE_CHECK:
            VM.sysWriteln("\nFatal error: ArrayStoreException within uninterruptible region.");
            break;
          default:
            VM.sysWriteln("\nFatal error: Unknown hardware trap within uninterruptible region.");
          break;
          }
          VM.sysFail("Exiting virtual machine due to uninterruptibility violation.");
        }
      }
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
  @NoInline
  @Entrypoint
  static void unlockAndThrow(Object objToUnlock, Throwable objToThrow) {
    VM_ObjectModel.genericUnlock(objToUnlock);
    athrow(objToThrow);
  }

  /**
   * Create and throw a java.lang.ArrayIndexOutOfBoundsException
   * Only used in some configurations where it is easier to make a call
   * then recover the array index from a trap instruction.
   */
  @NoInline
  @Entrypoint
  static void raiseArrayIndexOutOfBoundsException(int index) {
    throw new java.lang.ArrayIndexOutOfBoundsException(index);
  }

  /**
   * Create and throw a java.lang.ArrayIndexOutOfBoundsException
   * Used (rarely) by the opt compiler when it has determined that
   * an array access will unconditionally raise an array bounds check
   * error, but it has lost track of exactly what the index is going to be.
   */
  @NoInline
  static void raiseArrayIndexOutOfBoundsException() {
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
  @NoInline
  @Entrypoint
  public static void raiseNullPointerException() {
    throw new java.lang.NullPointerException();
  }

  /**
   * Create and throw a java.lang.ArrayStoreException
   * Used in a few circumstances to reduce code space costs
   * of inlining (see java.lang.System.arraycopy()).
   */
  @NoInline
  public static void raiseArrayStoreException() {
    throw new java.lang.ArrayStoreException();
  }

  /**
   * Create and throw a java.lang.ArithmeticException
   * Used to raise an arithmetic exception without going through
   * the hardware trap handler; currently this is only done when the
   * opt compiler has determined that an instruction will unconditionally
   * raise an arithmetic exception.
   */
  @NoInline
  @Entrypoint
  static void raiseArithmeticException() {
    throw new java.lang.ArithmeticException();
  }

  /**
   * Create and throw a java.lang.AbstractMethodError.
   * Used to handle error cases in invokeinterface dispatching.
   */
  @NoInline
  @Entrypoint
  static void raiseAbstractMethodError() {
    throw new java.lang.AbstractMethodError();
  }

  /**
   * Create and throw a java.lang.IllegalAccessError.
   * Used to handle error cases in invokeinterface dispatching.
   */
  @NoInline
  @Entrypoint
  static void raiseIllegalAccessError() {
    throw new java.lang.IllegalAccessError();
  }

  //----------------//
  // implementation //
  //----------------//

  public static void init() {
    // tell "RunBootImage.C" to pass control to
    // "VM_Runtime.deliverHardwareException()"
    // whenever the host operating system detects a hardware trap
    //
    VM_BootRecord.the_boot_record.hardwareTrapMethodId = VM_CompiledMethods.createHardwareTrapCompiledMethod().getId();
    VM_BootRecord.the_boot_record.deliverHardwareExceptionOffset =
        VM_Entrypoints.deliverHardwareExceptionMethod.getOffset();

    // tell "RunBootImage.C" to set "VM_Scheduler.debugRequested" flag
    // whenever the host operating system detects a debug request signal
    //
    VM_BootRecord.the_boot_record.debugRequestedOffset = VM_Entrypoints.debugRequestedField.getOffset();
  }

  /**
   * Build a multi-dimensional array.
   * @param methodId  Apparently unused (!)
   * @param numElements number of elements to allocate for each dimension
   * @param arrayType type of array that will result
   * @return array object
   */
  public static Object buildMultiDimensionalArray(int methodId, int[] numElements, VM_Array arrayType) {
    VM_Method method = VM_MemberReference.getMemberRef(methodId).asMethodReference().peekResolvedMethod();
    if (VM.VerifyAssertions) VM._assert(method != null);
    return buildMDAHelper(method, numElements, 0, arrayType);
  }

  /**
   * Build a two-dimensional array.
   * @param methodId  Apparently unused (!)
   * @param numElements number of elements to allocate for each dimension
   * @param arrayType type of array that will result
   * @return array object
   */
  public static Object buildTwoDimensionalArray(int methodId, int dim0, int dim1, VM_Array arrayType) {
    VM_Method method = VM_MemberReference.getMemberRef(methodId).asMethodReference().peekResolvedMethod();
    if (VM.VerifyAssertions) VM._assert(method != null);

    if (!arrayType.isInstantiated()) {
      arrayType.resolve();
      arrayType.instantiate();
    }

    Object[] newArray = (Object[])resolvedNewArray(dim0, arrayType);

    VM_Array innerArrayType = arrayType.getElementType().asArray();
    if (!innerArrayType.isInstantiated()) {
      innerArrayType.resolve();
      innerArrayType.instantiate();
    }

    for (int i=0; i<dim0; i++) {
      newArray[i] = resolvedNewArray(dim1, innerArrayType);
    }

    return newArray;
  }

  /**
   * @param method Apparently unused (!)
   * @param numElements Number of elements to allocate for each dimension
   * @param dimIndex Current dimension to build
   * @param arrayType type of array that will result
   */
  public static Object buildMDAHelper(VM_Method method, int[] numElements, int dimIndex, VM_Array arrayType) {

    if (!arrayType.isInstantiated()) {
      arrayType.resolve();
      arrayType.instantiate();
    }

    int nelts = numElements[dimIndex];
    Object newObject = resolvedNewArray(nelts, arrayType);

    if (++dimIndex == numElements.length) {
      return newObject; // all dimensions have been built
    }

    Object[] newArray = (Object[]) newObject;
    VM_Array newArrayType = arrayType.getElementType().asArray();

    for (int i = 0; i < nelts; ++i) {
      newArray[i] = buildMDAHelper(method, numElements, dimIndex, newArrayType);
    }

    return newArray;
  }

  /**
   * Deliver an exception to current java thread.
   * <STRONG> Precondition: </STRONG> VM.disableGC has already been called.
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
  private static void deliverException(Throwable exceptionObject, VM_Registers exceptionRegisters) {
    if (VM.TraceExceptionDelivery) {
      VM.sysWriteln("VM_Runtime.deliverException() entered; just got an exception object.");
    }

    // walk stack and look for a catch block
    //
    if (VM.TraceExceptionDelivery) {
      VM.sysWrite("Hunting for a catch block...");
    }
    VM_Type exceptionType = VM_Magic.getObjectType(exceptionObject);
    Address fp = exceptionRegisters.getInnermostFramePointer();
    while (VM_Magic.getCallerFramePointer(fp).NE(STACKFRAME_SENTINEL_FP)) {
      int compiledMethodId = VM_Magic.getCompiledMethodID(fp);
      if (compiledMethodId != INVISIBLE_METHOD_ID) {
        VM_CompiledMethod compiledMethod = VM_CompiledMethods.getCompiledMethod(compiledMethodId);
        VM_ExceptionDeliverer exceptionDeliverer = compiledMethod.getExceptionDeliverer();
        Address ip = exceptionRegisters.getInnermostInstructionAddress();
        Offset ipOffset = compiledMethod.getInstructionOffset(ip);
        int catchBlockOffset = compiledMethod.findCatchBlockForInstruction(ipOffset, exceptionType);

        if (catchBlockOffset >= 0) {
          // found an appropriate catch block
          if (VM.TraceExceptionDelivery) {
            VM.sysWriteln("found one; delivering.");
          }
          Address catchBlockStart = compiledMethod.getInstructionAddress(Offset.fromIntSignExtend(catchBlockOffset));
          exceptionDeliverer.deliverException(compiledMethod, catchBlockStart, exceptionObject, exceptionRegisters);
          if (VM.VerifyAssertions) VM._assert(NOT_REACHED);
        }

        exceptionDeliverer.unwindStackFrame(compiledMethod, exceptionRegisters);
      } else {
        unwindInvisibleStackFrame(exceptionRegisters);
      }
      fp = exceptionRegisters.getInnermostFramePointer();
    }

    if (VM.TraceExceptionDelivery) {
      VM.sysWriteln("Nope.");
      VM.sysWriteln("VM_Runtime.deliverException() found no catch block.");
    }
    /* No appropriate catch block found. */

    VM_Scheduler.getCurrentThread().handleUncaughtException(exceptionObject);
  }


  /**
   * Skip over all frames below currfp with saved code pointers outside of heap
   * (C frames), stopping at the native frame immediately preceding the glue
   * frame which contains the method ID of the native method (this is necessary
   * to allow retrieving the return address of the glue frame)
   *
   * @param currfp The current frame is expected to be one of the JNI functions
   *            called from C, below which is one or more native stack frames
   */
  @Uninterruptible
  public static Address unwindNativeStackFrame(Address currfp) {
    // Remembered address of previous FP
    Address callee_fp;
    // Address of native frame
    Address fp = VM_Magic.getCallerFramePointer(currfp);
    // Instruction pointer for current native frame
    Address ip;

    // Loop until either we fall off the stack or we find an instruction address
    // in one of our heaps
    do {
      callee_fp = fp;
      ip = VM_Magic.getReturnAddress(fp);
      fp = VM_Magic.getCallerFramePointer(fp);
    } while (!MM_Interface.addressInVM(ip) && fp.NE(STACKFRAME_SENTINEL_FP));

    if (VM.BuildForPowerPC) {
      // We want to return fp, not callee_fp because we want the stack walkers
      // to see the "mini-frame" which has the RVM information, not the "main frame"
      // pointed to by callee_fp which is where the saved ip was actually stored.
      return fp;
    } else {
      return callee_fp;
    }
  }

  /**
   * The current frame is expected to be one of the JNI functions
   * called from C,
   * below which is one or more native stack frames
   * Skip over all frames below which do not contain any object
   * references.
   */
  @Uninterruptible
  public static Address unwindNativeStackFrameForGC(Address currfp) {
     return unwindNativeStackFrame(currfp);
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

  /**
   * Number of allocations left before a GC is forced. Only used if VM.StressGCAllocationInterval is not 0.
   */
  static int allocationCountDownToGC = VM.StressGCAllocationInterval;

  /**
   * Number of c-to-java jni calls left before a GC is forced. Only used if VM.StressGCAllocationInterval is not 0.
   */
  static int jniCountDownToGC = VM.StressGCAllocationInterval;

  /**
   * Check to see if we are stress testing garbage collector and if another JNI call should
   * trigger a gc then do so.
   */
  @Inline
  public static void checkJNICountDownToGC() {
    // Temporarily disabled as it will causes nightly to take too long to run
    // There should be a mechanism to optionally enable this countdown in VM_Configuration
    if (false && canForceGC()) {
      if (jniCountDownToGC-- <= 0) {
        jniCountDownToGC = VM.StressGCAllocationInterval;
        System.gc();
      }
    }
  }

  /**
   * Check to see if we are stress testing garbage collector and if another allocation should
   * trigger a gc then do so.
   */
  @Inline
  private static void checkAllocationCountDownToGC() {
    if (canForceGC()) {
      if (allocationCountDownToGC-- <= 0) {
        allocationCountDownToGC = VM.StressGCAllocationInterval;
        System.gc();
      }
    }
  }

  /**
   * Return true if we are stress testing garbage collector and the system is in state where we
   * can force a garbage collection.
   */
  @Inline
  private static boolean canForceGC() {
    return VM.ForceFrequentGC && VM_Scheduler.safeToForceGCs();
  }
}
