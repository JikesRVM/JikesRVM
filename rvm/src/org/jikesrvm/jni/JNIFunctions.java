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
package org.jikesrvm.jni;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.Buffer;
import org.jikesrvm.ArchitectureSpecific.JNIHelpers;
import org.jikesrvm.VM;
import org.jikesrvm.Properties;
import org.jikesrvm.SizeConstants;
import org.jikesrvm.classloader.RVMArray;
import org.jikesrvm.classloader.Atom;
import org.jikesrvm.classloader.RVMClass;
import org.jikesrvm.classloader.RVMClassLoader;
import org.jikesrvm.classloader.RVMField;
import org.jikesrvm.classloader.MemberReference;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.classloader.NativeMethod;
import org.jikesrvm.classloader.RVMType;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.classloader.UTF8Convert;
import org.jikesrvm.mm.mminterface.MemoryManager;
import org.jikesrvm.objectmodel.ObjectModel;
import org.jikesrvm.runtime.BootRecord;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.runtime.Memory;
import org.jikesrvm.runtime.Reflection;
import org.jikesrvm.runtime.RuntimeEntrypoints;
import org.jikesrvm.util.AddressInputStream;

import static org.jikesrvm.runtime.SysCall.sysCall;
import org.vmmagic.pragma.NativeBridge;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.AddressArray;
import org.vmmagic.unboxed.Offset;

/**
 * This class implements the 232 JNI functions.
 * All methods here will be specially compiled with the necessary prolog to
 * perform the transition from native code (Linux/AIX/OSX convention) to RVM.
 * For this reason, no Java methods (including the JNI methods here) can call
 * any methods in this class from within Java.  These JNI methods are to
 * be invoked from native C or C++.   They're all declared private to enforce
 * this discipline.  <br>
 *
 * The first argument for all the functions is the JNIEnvironment object
 * of the thread. <br>
 *
 * The second argument is a JREF index for either the RVMClass object
 * or the object instance itself.  To get the actual object, we use
 * the access method in JNIEnvironment and cast the reference as
 * needed. <br>
 *
 * NOTE:
 * <ol>
 * <li> JREF index refers to the index into the side table of references
 * maintained by the JNIEnvironment for each thread. Except for some cases
 * of array access, no references are passed directly to the native code;
 * rather, the references are kept in the table and the index is passed to the
 * native procedure.  The JREF index are used by the JNI code to retrieve the
 * corresponding reference. </li>
 *
 * <li> Strings from C are seen as raw address (int) and need to be cloned as
 * Java Strings </li>
 *
 * <li> Because of many of the transformation above, the method signature of the
 * JNI functions may not match its definition in the jni.h file </li>
 *
 * <li> For exception handling, all JNI functions are wrapped in Try/Catch block
 * to catch all exception generated during JNI call, then these exceptions
 * or the appropriate exception to be thrown according to the spec is recorded
 * in JNIEnvironment.pendingException.  When the native code returns to the
 * the Java caller, the epilogue in the glue code will check for the pending
 * exception and deliver it to the caller as if executing an athrow bytecode
 * in the caller. </li>
 * </ol>
 *
 * Known Problems with our JNI implementation:
 * <ol>
 * <li>We can not return a global reference (whether weak
 *     or strong) from a JNI function.  We can only return local refs.
 * <li>We do not implement all of the invocation API; we don't support the
 *      concept of a regular native program that links with "libjava" and
 *      creates and destroys virtual machines.
 * <li>Similarly, we can not attach and detach a native threads to and from
 *     the VM.
 * <li>We don't really free local refs when we call the
 *      {@link #PopLocalFrame} method.
 * </ol>
 */
@SuppressWarnings({"unused", "UnusedDeclaration"})
// methods are called from native code
@NativeBridge
public class JNIFunctions implements SizeConstants {
  // one message for each JNI function called from native
  public static final boolean traceJNI = Properties.verboseJNI;

  // number of JNI function entries
  public static final int FUNCTIONCOUNT = 232; // JNI 1.4

  /**
   * GetVersion: the version of the JNI
   * @param env A JREF index for the JNI environment object
   * @return 0x00010004 for JNI 1.4, 0x00010002 for JNI 1.2,
   *        0x00010001 for JNI 1.1,
   */
  private static int GetVersion(JNIEnvironment env) {
    if (traceJNI) VM.sysWrite("JNI called: GetVersion  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    return 0x00010004;          // JNI 1.4
  }

  /**
   * DefineClass:  Loads a class from a buffer of raw class data.
   * @param env A JREF index for the JNI environment object
   * @param classNameAddress a raw address to a null-terminated string in C for the class name
   * @param classLoader a JREF index for the class loader assigned to the defined class
   * @param data buffer containing the <tt>.class</tt> file
   * @param dataLen buffer length
   * @return a JREF index for the Java Class object, or 0 if not found
   * @exception ClassFormatError if the class data does not specify a valid class
   * @exception ClassCircularityError (not implemented)
   * @exception OutOfMemoryError (not implemented)
   */
  private static int DefineClass(JNIEnvironment env, Address classNameAddress, int classLoader, Address data,
                                 int dataLen) {
    if (traceJNI) VM.sysWrite("JNI called: DefineClass  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      String classString = null;
      if (!classNameAddress.isZero()) {
        JNIHelpers.createStringFromC(classNameAddress);
      }
      ClassLoader cl;
      if (classLoader == 0) {
        cl = RVMClass.getClassLoaderFromStackFrame(1);
      } else {
        cl = (ClassLoader) env.getJNIRef(classLoader);
      }
      AddressInputStream reader = new AddressInputStream(data, Offset.fromIntZeroExtend(dataLen));

      final RVMType vmType = RVMClassLoader.defineClassInternal(classString, reader, cl);
      return env.pushJNIRef(vmType.getClassForType());
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }

  }

  /**
   * FindClass:  given a class name, find its RVMClass, or 0 if not found
   * @param env A JREF index for the JNI environment object
   * @param classNameAddress a raw address to a null-terminated string in C for the class name
   * @return a JREF index for the Java Class object, or 0 if not found
   * @exception ClassFormatError (not implemented)
   * @exception ClassCircularityError (not implemented)
   * @exception NoClassDefFoundError if the class cannot be found
   * @exception OutOfMemoryError (not implemented)
   * @exception ExceptionInInitializerError (not implemented)
   */
  private static int FindClass(JNIEnvironment env, Address classNameAddress) {
    if (traceJNI) VM.sysWrite("JNI called: FindClass  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    String classString = null;
    try {
      classString = JNIHelpers.createStringFromC(classNameAddress);
      if (traceJNI) VM.sysWriteln(classString);
      ClassLoader cl = RVMClass.getClassLoaderFromStackFrame(1);
      Class<?> matchedClass = Class.forName(classString.replace('/', '.'), true, cl);
      int result = env.pushJNIRef(matchedClass);
      if (traceJNI) VM.sysWriteln("FindClass returning ",result);
      return result;
    } catch (ClassNotFoundException e) {
      if (traceJNI) e.printStackTrace(System.err);
      env.recordException(new NoClassDefFoundError(classString));
      return 0;
    } catch (Throwable unexpected) {
      if (traceJNI) {
        if (VM.fullyBooted) {
          unexpected.printStackTrace(System.err);
        } else {
          VM.sysWrite("Unexpected exception ", unexpected.getClass().toString());
          VM.sysWriteln(" to early in VM boot up to print ", unexpected.getMessage());
        }
      }
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * GetSuperclass: find the superclass given a class
   * @param env A JREF index for the JNI environment object
   * @param classJREF a JREF index for the class object
   * @return a JREF index for the super class object, or 0 if the given class
   *         is java.lang.Object or an interface
   */
  private static int GetSuperclass(JNIEnvironment env, int classJREF) {
    if (traceJNI) VM.sysWrite("JNI called: GetSuperclass  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Class<?> cls = (Class<?>) env.getJNIRef(classJREF);
      Class<?> supercls = cls.getSuperclass();
      return supercls == null ? 0 : env.pushJNIRef(supercls);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * IsAssignableFrom:  determine if an an object of class or interface cls1
   * can be cast to the class or interface cls2
   * @param env A JREF index for the JNI environment object
   * @param firstClassJREF a JREF index for the first class object
   * @param secondClassJREF a JREF index for the second class object
   * @return true if cls1 can be assigned to cls2
   */
  private static boolean IsAssignableFrom(JNIEnvironment env, int firstClassJREF, int secondClassJREF) {
    if (traceJNI) VM.sysWrite("JNI called: IsAssignableFrom  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Class<?> cls1 = (Class<?>) env.getJNIRef(firstClassJREF);
      Class<?> cls2 = (Class<?>) env.getJNIRef(secondClassJREF);
      return !(cls1 == null || cls2 == null) && cls2.isAssignableFrom(cls1);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return false;
    }
  }

  /**
   * Throw:  register a {@link Throwable} object as a pending exception, to be
   *         delivered on return to the Java caller
   * @param env A JREF index for the JNI environment object
   * @param exceptionJREF A JREF index for the {@link Throwable} object to be
   *        thrown
   * @return 0 if successful, -1 if not
   */
  private static int Throw(JNIEnvironment env, int exceptionJREF) {
    if (traceJNI) VM.sysWrite("JNI called: Throw  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      env.recordException((Throwable) env.getJNIRef(exceptionJREF));
      return 0;
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return -1;
    }
  }

  /**
   * ThrowNew
   * @param env A JREF index for the JNI environment object
   * @param throwableClassJREF a JREF index for the class object of the exception
   * @param exceptionNameAddress an address of the string in C
   * @return 0 if successful, -1 otherwise
   */
  private static int ThrowNew(JNIEnvironment env, int throwableClassJREF, Address exceptionNameAddress) {
    if (traceJNI) VM.sysWrite("JNI called: ThrowNew  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Class<?> cls = (Class<?>) env.getJNIRef(throwableClassJREF);
      // find the constructor that has a string as a parameter
      Class<?>[] argClasses = new Class[1];
      argClasses[0] = RVMType.JavaLangStringType.getClassForType();
      Constructor<?> constMethod = cls.getConstructor(argClasses);
      // prepare the parameter list for reflective invocation
      Object[] argObjs = new Object[1];
      argObjs[0] = JNIHelpers.createStringFromC(exceptionNameAddress);

      // invoke the constructor to obtain a new Throwable object
      env.recordException((Throwable) constMethod.newInstance(argObjs));
      return 0;
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return -1;
    }

  }

  /**
   * ExceptionOccurred
   * @param env A JREF index for the JNI environment object
   * @return a JREF index for the pending exception or null if nothing pending
   */
  private static int ExceptionOccurred(JNIEnvironment env) {
    if (traceJNI) VM.sysWrite("JNI called: ExceptionOccurred  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Throwable e = env.getException();
      if (e == null) {
        return 0;
      } else {
        if (traceJNI) System.err.println(e.toString());
        return env.pushJNIRef(e);
      }
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return env.pushJNIRef(unexpected);
    }
  }

  /**
   * ExceptionDescribe: print the exception description and the stack trace back,
   *                    then clear the exception
   * @param env A JREF index for the JNI environment object
   */
  private static void ExceptionDescribe(JNIEnvironment env) {
    if (traceJNI) VM.sysWrite("JNI called: ExceptionDescribe  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Throwable e = env.getException();
      if (e != null) {
        env.recordException(null);
        e.printStackTrace(System.err);
      }
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(null);            // clear old exception and register new one
      env.recordException(unexpected);
    }
  }

  /**
   * ExceptionClear
   * @param env A JREF index for the JNI environment object
   */
  private static void ExceptionClear(JNIEnvironment env) {
    if (traceJNI) VM.sysWrite("JNI called: ExceptionClear  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      env.recordException(null);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(null);            // clear old exception and register new one
      env.recordException(unexpected);
    }
  }

  /**
   * FatalError: print a message and terminate the VM
   * @param env A JREF index for the JNI environment object
   * @param messageAddress an address of the string in C
   */
  private static void FatalError(JNIEnvironment env, Address messageAddress) {
    if (traceJNI) VM.sysWrite("JNI called: FatalError  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      VM.sysWrite(JNIHelpers.createStringFromC(messageAddress));
      System.exit(VM.EXIT_STATUS_JNI_TROUBLE);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      System.exit(VM.EXIT_STATUS_RECURSIVELY_SHUTTING_DOWN);
    }
  }

  private static int NewGlobalRef(JNIEnvironment env, int objectJREF) {
    if (traceJNI) VM.sysWrite("JNI called: NewGlobalRef\n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object obj1 = env.getJNIRef(objectJREF);
      return JNIGlobalRefTable.newGlobalRef(obj1);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  private static void DeleteGlobalRef(JNIEnvironment env, int refJREF) {
    if (traceJNI) VM.sysWrite("JNI called: DeleteGlobalRef\n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      JNIGlobalRefTable.deleteGlobalRef(refJREF);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  private static void DeleteLocalRef(JNIEnvironment env, int objJREF) {
    if (traceJNI) VM.sysWrite("JNI called: DeleteLocalRef\n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      env.deleteJNIRef(objJREF);
    } catch (ArrayIndexOutOfBoundsException e) {
      VM.sysFail("JNI refs array confused, or DeleteLocalRef gave us a bad JREF argument:", objJREF);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * IsSameObject: determine if two references point to the same object
   * @param env A JREF index for the JNI environment object
   * @param obj1JREF A JREF index for the first object
   * @param obj2JREF A JREF index for the second object
   * @return <code>true</code> if it's the same object, false otherwise
   */
  private static boolean IsSameObject(JNIEnvironment env, int obj1JREF, int obj2JREF) {
    if (traceJNI) VM.sysWrite("JNI called: IsSameObject  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object obj1 = env.getJNIRef(obj1JREF);
      Object obj2 = env.getJNIRef(obj2JREF);
      return obj1 == obj2;
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(null);            // clear old exception and register new one
      env.recordException(unexpected);
      return false;
    }
  }

  /**
   * AllocObject:  allocate the space for an object without running any constructor
   *               the header is filled and the fields are initialized to null
   * @param env A JREF index for the JNI environment object
   * @param classJREF a JREF index for the class object
   * @return a JREF index for the uninitialized object
   * @exception InstantiationException if the class is abstract or is an interface
   * @exception OutOfMemoryError if no more memory to allocate
   */
  private static int AllocObject(JNIEnvironment env, int classJREF) throws InstantiationException, OutOfMemoryError {
    if (traceJNI) VM.sysWrite("JNI called: AllocObject  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Class<?> javaCls = (Class<?>) env.getJNIRef(classJREF);
      RVMType type = java.lang.JikesRVMSupport.getTypeForClass(javaCls);
      if (type.isArrayType() || type.isPrimitiveType() || type.isUnboxedType()) {
        env.recordException(new InstantiationException());
        return 0;
      }
      RVMClass cls = type.asClass();
      if (cls.isAbstract() || cls.isInterface()) {
        env.recordException(new InstantiationException());
        return 0;
      }
      Object newObj = RuntimeEntrypoints.resolvedNewScalar(cls);
      return env.pushJNIRef(newObj);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * NewObject: create a new object instance
   * NOTE:  the vararg's are not visible in the method signature here;
   *        they are saved in the caller frame and the glue frame
   * @param env A JREF index for the JNI environment object
   * @param classJREF a JREF index for the class object
   * @param methodID id of a MethodReference
   * @return the new object instance
   * @exception InstantiationException if the class is abstract or is an interface
   * @exception OutOfMemoryError if no more memory to allocate
   */
  private static int NewObject(JNIEnvironment env, int classJREF, int methodID) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: NewObject  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Class<?> cls = (Class<?>) env.getJNIRef(classJREF);
      RVMClass vmcls = java.lang.JikesRVMSupport.getTypeForClass(cls).asClass();

      if (vmcls.isAbstract() || vmcls.isInterface()) {
        env.recordException(new InstantiationException());
        return 0;
      }

      Object newobj = JNIHelpers.invokeInitializer(cls, methodID, Address.zero(), false, true);

      return env.pushJNIRef(newobj);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * NewObjectV: create a new object instance
   * @param env A JREF index for the JNI environment object
   * @param classJREF a JREF index for the class object
   * @param methodID id of a MethodReference
   * @param argAddress a raw address to a variable argument list, each element is 1-word or
   *                   2-words of the appropriate type for the constructor invocation
   * @return the new object instance
   * @exception InstantiationException if the class is abstract or is an interface
   * @exception OutOfMemoryError if no more memory to allocate
   */
  private static int NewObjectV(JNIEnvironment env, int classJREF, int methodID, Address argAddress)
      throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: NewObjectV  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Class<?> cls = (Class<?>) env.getJNIRef(classJREF);
      RVMClass vmcls = java.lang.JikesRVMSupport.getTypeForClass(cls).asClass();
      if (vmcls.isAbstract() || vmcls.isInterface()) {
        env.recordException(new InstantiationException());
        return 0;
      }

      Object newobj = JNIHelpers.invokeInitializer(cls, methodID, argAddress, false, false);

      return env.pushJNIRef(newobj);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * NewObjectA: create a new object instance
   * @param env A JREF index for the JNI environment object
   * @param classJREF a JREF index for the class object
   * @param methodID id of a MethodReference
   * @param argAddress a raw address to an array of unions in C, each element is 2-word and
   *                   hold an argument of the appropriate type for the constructor invocation
   * @exception InstantiationException if the class is abstract or is an interface
   * @exception OutOfMemoryError if no more memory to allocate
   * @return the new object instance
   */
  private static int NewObjectA(JNIEnvironment env, int classJREF, int methodID, Address argAddress)
      throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: NewObjectA  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Class<?> cls = (Class<?>) env.getJNIRef(classJREF);
      RVMClass vmcls = java.lang.JikesRVMSupport.getTypeForClass(cls).asClass();

      if (vmcls.isAbstract() || vmcls.isInterface()) {
        env.recordException(new InstantiationException());
        return 0;
      }

      Object newobj = JNIHelpers.invokeInitializer(cls, methodID, argAddress, true, false);

      return env.pushJNIRef(newobj);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * GetObjectClass
   * @param env A JREF index for the JNI environment object
   * @param objJREF a JREF index for the object to check
   * @return a JREF index for the Class object
   */
  private static int GetObjectClass(JNIEnvironment env, int objJREF) {
    if (traceJNI) VM.sysWrite("JNI called: GetObjectClass  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object obj = env.getJNIRef(objJREF);
      return env.pushJNIRef(obj.getClass());
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * IsInstanceOf: determine if an object is an instance of the class
   * @param env A JREF index for the JNI environment object
   * @param objJREF a JREF index for the object to check
   * @param classJREF a JREF index for the class to check
   * @return true if the object is an instance of the class
   */
  private static int IsInstanceOf(JNIEnvironment env, int objJREF, int classJREF) {
    if (traceJNI) VM.sysWrite("JNI called: IsInstanceOf  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Class<?> cls = (Class<?>) env.getJNIRef(classJREF);
      Object obj = env.getJNIRef(objJREF);
      if (obj == null) return 0; // null instanceof T is always false
      RVMType RHStype = ObjectModel.getObjectType(obj);
      RVMType LHStype = java.lang.JikesRVMSupport.getTypeForClass(cls);
      return (LHStype == RHStype || RuntimeEntrypoints.isAssignableWith(LHStype, RHStype)) ? 1 : 0;
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * GetMethodID:  get the virtual method ID given the name and the signature
   * @param env A JREF index for the JNI environment object
   * @param classJREF a JREF index for the class object
   * @param methodNameAddress a raw address to a null-terminated string in C for the method name
   * @param methodSigAddress a raw address to a null-terminated string in C for the method signature
   * @return id of a MethodReference
   * @exception NoSuchMethodError if the method cannot be found
   * @exception ExceptionInInitializerError if the class or interface static initializer fails
   * @exception OutOfMemoryError if the system runs out of memory
   */
  private static int GetMethodID(JNIEnvironment env, int classJREF, Address methodNameAddress,
                                 Address methodSigAddress) {
    if (traceJNI) VM.sysWrite("JNI called: GetMethodID  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      // obtain the names as String from the native space
      String methodString = JNIHelpers.createStringFromC(methodNameAddress);
      Atom methodName = Atom.findOrCreateAsciiAtom(methodString);
      String sigString = JNIHelpers.createStringFromC(methodSigAddress);
      Atom sigName = Atom.findOrCreateAsciiAtom(sigString);

      // get the target class
      Class<?> jcls = (Class<?>) env.getJNIRef(classJREF);
      RVMType type = java.lang.JikesRVMSupport.getTypeForClass(jcls);
      if (!type.isClassType()) {
        env.recordException(new NoSuchMethodError());
        return 0;
      }

      RVMClass klass = type.asClass();
      if (!klass.isInitialized()) {
        RuntimeEntrypoints.initializeClassForDynamicLink(klass);
      }

      // Find the target method
      final RVMMethod meth;
      if (methodString.equals("<init>")) {
        meth = klass.findInitializerMethod(sigName);
      } else {
        meth = klass.findVirtualMethod(methodName, sigName);
      }

      if (meth == null) {
        env.recordException(new NoSuchMethodError(klass + ": " + methodName + " " + sigName));
        return 0;
      }

      if (traceJNI) VM.sysWrite("got method " + meth + "\n");
      return meth.getId();
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallObjectMethod:  invoke a virtual method that returns an object
   *                           arguments passed using the vararg ... style
   * NOTE:  the vararg's are not visible in the method signature here;
   *        they are saved in the caller frame and the glue frame
   * @param env A JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param methodID id of a MethodReference
   * @return the JREF index for the object returned from the method invocation
   */
  private static int CallObjectMethod(JNIEnvironment env, int objJREF, int methodID) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallObjectMethod  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object obj = env.getJNIRef(objJREF);
      Object returnObj = JNIHelpers.invokeWithDotDotVarArg(obj, methodID, null, false);
      return env.pushJNIRef(returnObj);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallObjectMethodV:  invoke a virtual method that returns an object
   * @param env A JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param methodID id of a MethodReference
   * @param argAddress a raw address to a variable argument list, each element is
   *              1-word or 2-words of the appropriate type for the method invocation
   * @return the JREF index for the object returned from the method invocation
   */
  private static int CallObjectMethodV(JNIEnvironment env, int objJREF, int methodID, Address argAddress)
      throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallObjectMethodV  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object obj = env.getJNIRef(objJREF);
      Object returnObj = JNIHelpers.invokeWithVarArg(obj, methodID, argAddress, null, false);
      return env.pushJNIRef(returnObj);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallObjectMethodA:  invoke a virtual method that returns an object value
   * @param env A JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param methodID id of a MethodReference
   * @param argAddress a raw address to an array of unions in C, each element is 2-word
   *        and hold an argument of the appropriate type for the method invocation
   * @return the JREF index for the object returned from the method invocation
   */
  private static int CallObjectMethodA(JNIEnvironment env, int objJREF, int methodID, Address argAddress)
      throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallObjectMethodA  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object obj = env.getJNIRef(objJREF);
      Object returnObj = JNIHelpers.invokeWithJValue(obj, methodID, argAddress, null, false);
      return env.pushJNIRef(returnObj);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallBooleanMethod:  invoke a virtual method that returns a boolean value
   *                           arguments passed using the vararg ... style
   * NOTE:  the vararg's are not visible in the method signature here;
   *        they are saved in the caller frame and the glue frame
   * @param env A JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param methodID id of a MethodReference
   * @return the boolean value returned from the method invocation
   */
  private static boolean CallBooleanMethod(JNIEnvironment env, int objJREF, int methodID) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallBooleanMethod  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object obj = env.getJNIRef(objJREF);
      Object returnObj = JNIHelpers.invokeWithDotDotVarArg(obj, methodID, TypeReference.Boolean, false);
      return Reflection.unwrapBoolean(returnObj);     // should be a wrapper for a boolean value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return false;
    }
  }

  /**
   * CallBooleanMethodV:  invoke a virtual method that returns a boolean value
   * @param env A JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param methodID id of a MethodReference
   * @param argAddress a raw address to a variable argument list, each element is
   *              1-word or 2-words of the appropriate type for the method invocation
   * @return the boolean value returned from the method invocation
   */
  private static boolean CallBooleanMethodV(JNIEnvironment env, int objJREF, int methodID, Address argAddress)
      throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallBooleanMethodV  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object obj = env.getJNIRef(objJREF);
      Object returnObj = JNIHelpers.invokeWithVarArg(obj, methodID, argAddress, TypeReference.Boolean, false);
      return Reflection.unwrapBoolean(returnObj);     // should be a wrapper for a boolean value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return false;
    }
  }

  /**
   * CallBooleanMethodA:  invoke a virtual method that returns a boolean value
   * @param env A JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param methodID id of a MethodReference
   * @param argAddress a raw address to an array of unions in C, each element is 2-word
   *        and hold an argument of the appropriate type for the method invocation
   * @return the boolean value returned from the method invocation
   */
  private static boolean CallBooleanMethodA(JNIEnvironment env, int objJREF, int methodID, Address argAddress)
      throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallBooleanMethodA  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object obj = env.getJNIRef(objJREF);
      Object returnObj = JNIHelpers.invokeWithJValue(obj, methodID, argAddress, TypeReference.Boolean, false);
      return Reflection.unwrapBoolean(returnObj);     // should be a wrapper for a boolean value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return false;
    }
  }

  /**
   * CallByteMethod:  invoke a virtual method that returns a byte value
   *                           arguments passed using the vararg ... style
   * NOTE:  the vararg's are not visible in the method signature here;
   *        they are saved in the caller frame and the glue frame
   * @param env A JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param methodID id of a MethodReference
   * @return the byte value returned from the method invocation
   */
  private static byte CallByteMethod(JNIEnvironment env, int objJREF, int methodID) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallByteMethod  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object obj = env.getJNIRef(objJREF);
      Object returnObj = JNIHelpers.invokeWithDotDotVarArg(obj, methodID, TypeReference.Byte, false);
      return Reflection.unwrapByte(returnObj);     // should be a wrapper for a byte value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallByteMethodV:  invoke a virtual method that returns a byte value
   * @param env A JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param methodID id of a MethodReference
   * @param argAddress a raw address to a variable argument list, each element is
   *              1-word or 2-words of the appropriate type for the method invocation
   * @return the byte value returned from the method invocation
   */
  private static byte CallByteMethodV(JNIEnvironment env, int objJREF, int methodID, Address argAddress)
      throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallByteMethodV  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object obj = env.getJNIRef(objJREF);
      Object returnObj = JNIHelpers.invokeWithVarArg(obj, methodID, argAddress, TypeReference.Byte, false);
      return Reflection.unwrapByte(returnObj);     // should be a wrapper for a byte value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallByteMethodA:  invoke a virtual method that returns a byte value
   * @param env A JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param methodID id of a MethodReference
   * @param argAddress a raw address to an array of unions in C, each element is 2-word
   *        and hold an argument of the appropriate type for the method invocation
   * @return the byte value returned from the method invocation
   */
  private static byte CallByteMethodA(JNIEnvironment env, int objJREF, int methodID, Address argAddress)
      throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallByteMethodA  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object obj = env.getJNIRef(objJREF);
      Object returnObj = JNIHelpers.invokeWithJValue(obj, methodID, argAddress, TypeReference.Byte, false);
      return Reflection.unwrapByte(returnObj);     // should be a wrapper for a byte value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallCharMethod:  invoke a virtual method that returns a char value
   *                           arguments passed using the vararg ... style
   * NOTE:  the vararg's are not visible in the method signature here;
   *        they are saved in the caller frame and the glue frame
   * @param env A JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param methodID id of a MethodReference
   * @return the char value returned from the method invocation
   */
  private static char CallCharMethod(JNIEnvironment env, int objJREF, int methodID) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallCharMethod  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object obj = env.getJNIRef(objJREF);
      Object returnObj = JNIHelpers.invokeWithDotDotVarArg(obj, methodID, TypeReference.Char, false);
      return Reflection.unwrapChar(returnObj);     // should be a wrapper for a char value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallCharMethodV:  invoke a virtual method that returns a char value
   * @param env A JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param methodID id of a MethodReference
   * @param argAddress a raw address to a variable argument list, each element is
   *              1-word or 2-words of the appropriate type for the method invocation
   * @return the char value returned from the method invocation
   */
  private static char CallCharMethodV(JNIEnvironment env, int objJREF, int methodID, Address argAddress)
      throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallCharMethodV  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object obj = env.getJNIRef(objJREF);
      Object returnObj = JNIHelpers.invokeWithVarArg(obj, methodID, argAddress, TypeReference.Char, false);
      return Reflection.unwrapChar(returnObj);     // should be a wrapper for a char value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallCharMethodA:  invoke a virtual method that returns a char value
   * @param env A JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param methodID id of a MethodReference
   * @param argAddress a raw address to an array of unions in C, each element is 2-word
   *        and hold an argument of the appropriate type for the method invocation
   * @return the char value returned from the method invocation
   */
  private static char CallCharMethodA(JNIEnvironment env, int objJREF, int methodID, Address argAddress)
      throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallCharMethodA  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object obj = env.getJNIRef(objJREF);
      Object returnObj = JNIHelpers.invokeWithJValue(obj, methodID, argAddress, TypeReference.Char, false);
      return Reflection.unwrapChar(returnObj);     // should be a wrapper for a char value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallShortMethod:  invoke a virtual method that returns a short value
   *                           arguments passed using the vararg ... style
   * NOTE:  the vararg's are not visible in the method signature here;
   *        they are saved in the caller frame and the glue frame
   * @param env A JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param methodID id of a MethodReference
   * @return the short value returned from the method invocation
   */
  private static short CallShortMethod(JNIEnvironment env, int objJREF, int methodID) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallShortMethod  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object obj = env.getJNIRef(objJREF);
      Object returnObj = JNIHelpers.invokeWithDotDotVarArg(obj, methodID, TypeReference.Short, false);
      return Reflection.unwrapShort(returnObj);     // should be a wrapper for a short value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallShortMethodV:  invoke a virtual method that returns a short value
   * @param env A JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param methodID id of a MethodReference
   * @param argAddress a raw address to a variable argument list, each element is
   *              1-word or 2-words of the appropriate type for the method invocation
   * @return the short value returned from the method invocation
   */
  private static short CallShortMethodV(JNIEnvironment env, int objJREF, int methodID, Address argAddress)
      throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallShortMethodV  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object obj = env.getJNIRef(objJREF);
      Object returnObj = JNIHelpers.invokeWithVarArg(obj, methodID, argAddress, TypeReference.Short, false);
      return Reflection.unwrapShort(returnObj);     // should be a wrapper for a short value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallShortMethodA:  invoke a virtual method that returns a short value
   * @param env A JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param methodID id of a MethodReference
   * @param argAddress a raw address to an array of unions in C, each element is 2-word
   *        and hold an argument of the appropriate type for the method invocation
   * @return the short value returned from the method invocation
   */
  private static short CallShortMethodA(JNIEnvironment env, int objJREF, int methodID, Address argAddress)
      throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallShortMethodA  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object obj = env.getJNIRef(objJREF);
      Object returnObj = JNIHelpers.invokeWithJValue(obj, methodID, argAddress, TypeReference.Short, false);
      return Reflection.unwrapShort(returnObj);     // should be a wrapper for a short value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallIntMethod:  invoke a virtual method that returns a int value
   *                           arguments passed using the vararg ... style
   * NOTE:  the vararg's are not visible in the method signature here;
   *        they are saved in the caller frame and the glue frame
   * @param env A JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param methodID id of a MethodReference
   * @return the int value returned from the method invocation
   */
  private static int CallIntMethod(JNIEnvironment env, int objJREF, int methodID) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallIntMethod  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object obj = env.getJNIRef(objJREF);
      Object returnObj = JNIHelpers.invokeWithDotDotVarArg(obj, methodID, TypeReference.Int, false);
      return Reflection.unwrapInt(returnObj);     // should be a wrapper for an integer value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallIntMethodV:  invoke a virtual method that returns an int value
   * @param env A JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param methodID id of a MethodReference
   * @param argAddress a raw address to a variable argument list, each element is
   *              1-word or 2-words of the appropriate type for the method invocation
   * @return the int value returned from the method invocation
   */
  private static int CallIntMethodV(JNIEnvironment env, int objJREF, int methodID, Address argAddress)
      throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallIntMethodV  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object obj = env.getJNIRef(objJREF);
      Object returnObj = JNIHelpers.invokeWithVarArg(obj, methodID, argAddress, TypeReference.Int, false);
      return Reflection.unwrapInt(returnObj);     // should be a wrapper for an integer value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallIntMethodA:  invoke a virtual method that returns an integer value
   * @param env A JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param methodID id of a MethodReference
   * @param argAddress a raw address to an array of unions in C, each element is 2-word
   *        and hold an argument of the appropriate type for the method invocation
   * @return the integer value returned from the method invocation
   */
  private static int CallIntMethodA(JNIEnvironment env, int objJREF, int methodID, Address argAddress)
      throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallIntMethodA  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object obj = env.getJNIRef(objJREF);
      Object returnObj = JNIHelpers.invokeWithJValue(obj, methodID, argAddress, TypeReference.Int, false);
      return Reflection.unwrapInt(returnObj);     // should be a wrapper for an integer value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallLongMethod:  invoke a virtual method that returns a long value
   *                           arguments passed using the vararg ... style
   * NOTE:  the vararg's are not visible in the method signature here;
   *        they are saved in the caller frame and the glue frame
   * @param env A JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param methodID id of a MethodReference
   * @return the long value returned from the method invocation
   */
  private static long CallLongMethod(JNIEnvironment env, int objJREF, int methodID) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallLongMethod  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object obj = env.getJNIRef(objJREF);
      Object returnObj = JNIHelpers.invokeWithDotDotVarArg(obj, methodID, TypeReference.Long, false);
      return Reflection.unwrapLong(returnObj);     // should be a wrapper for a long value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallLongMethodV:  invoke a virtual method that returns a long value
   * @param env A JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param methodID id of a MethodReference
   * @param argAddress a raw address to a variable argument list, each element is
   *              1-word or 2-words of the appropriate type for the method invocation
   * @return the long value returned from the method invocation
   */
  private static long CallLongMethodV(JNIEnvironment env, int objJREF, int methodID, Address argAddress)
      throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallLongMethodV  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object obj = env.getJNIRef(objJREF);
      Object returnObj = JNIHelpers.invokeWithVarArg(obj, methodID, argAddress, TypeReference.Long, false);
      return Reflection.unwrapLong(returnObj);     // should be a wrapper for a long value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallLongMethodA:  invoke a virtual method that returns a long value
   * @param env A JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param methodID id of a MethodReference
   * @param argAddress a raw address to an array of unions in C, each element is 2-word
   *        and hold an argument of the appropriate type for the method invocation
   * @return the long value returned from the method invocation
   */
  private static long CallLongMethodA(JNIEnvironment env, int objJREF, int methodID, Address argAddress)
      throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallLongMethodA  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object obj = env.getJNIRef(objJREF);
      Object returnObj = JNIHelpers.invokeWithJValue(obj, methodID, argAddress, TypeReference.Long, false);
      return Reflection.unwrapLong(returnObj);     // should be a wrapper for a long value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallFloatMethod:  invoke a virtual method that returns a float value
   *                           arguments passed using the vararg ... style
   * NOTE:  the vararg's are not visible in the method signature here;
   *        they are saved in the caller frame and the glue frame
   * @param env A JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param methodID id of a MethodReference
   * @return the float value returned from the method invocation
   */
  private static float CallFloatMethod(JNIEnvironment env, int objJREF, int methodID) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallFloatMethod  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object obj = env.getJNIRef(objJREF);
      Object returnObj = JNIHelpers.invokeWithDotDotVarArg(obj, methodID, TypeReference.Float, false);
      return Reflection.unwrapFloat(returnObj);     // should be a wrapper for a float value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallFloatMethodV:  invoke a virtual method that returns a float value
   * @param env A JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param methodID id of a MethodReference
   * @param argAddress a raw address to a variable argument list, each element is
   *              1-word or 2-words of the appropriate type for the method invocation
   * @return the float value returned from the method invocation
   */
  private static float CallFloatMethodV(JNIEnvironment env, int objJREF, int methodID, Address argAddress)
      throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallFloatMethodV  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object obj = env.getJNIRef(objJREF);
      Object returnObj = JNIHelpers.invokeWithVarArg(obj, methodID, argAddress, TypeReference.Float, false);
      return Reflection.unwrapFloat(returnObj);     // should be a wrapper for a float value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallFloatMethodA:  invoke a virtual method that returns a float value
   * @param env A JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param methodID id of a MethodReference
   * @param argAddress a raw address to an array of unions in C, each element is 2-word
   *        and hold an argument of the appropriate type for the method invocation
   * @return the float value returned from the method invocation
   */
  private static float CallFloatMethodA(JNIEnvironment env, int objJREF, int methodID, Address argAddress)
      throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallFloatMethodA  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object obj = env.getJNIRef(objJREF);
      Object returnObj = JNIHelpers.invokeWithJValue(obj, methodID, argAddress, TypeReference.Float, false);
      return Reflection.unwrapFloat(returnObj);     // should be a wrapper for a float value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallDoubleMethod:  invoke a virtual method that returns a double value
   *                           arguments passed using the vararg ... style
   * NOTE:  the vararg's are not visible in the method signature here;
   *        they are saved in the caller frame and the glue frame
   * @param env A JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param methodID id of a MethodReference
   * @return the double value returned from the method invocation
   */
  private static double CallDoubleMethod(JNIEnvironment env, int objJREF, int methodID) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallDoubleMethod  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object obj = env.getJNIRef(objJREF);
      Object returnObj = JNIHelpers.invokeWithDotDotVarArg(obj, methodID, TypeReference.Double, false);
      return Reflection.unwrapDouble(returnObj);     // should be a wrapper for a double value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallDoubleMethodV:  invoke a virtual method that returns a double value
   * @param env A JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param methodID id of a MethodReference
   * @param argAddress a raw address to a variable argument list, each element is
   *              1-word or 2-words of the appropriate type for the method invocation
   * @return the double value returned from the method invocation
   */
  private static double CallDoubleMethodV(JNIEnvironment env, int objJREF, int methodID, Address argAddress)
      throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallDoubleMethodV  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object obj = env.getJNIRef(objJREF);
      Object returnObj = JNIHelpers.invokeWithVarArg(obj, methodID, argAddress, TypeReference.Double, false);
      return Reflection.unwrapDouble(returnObj);     // should be a wrapper for a double value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallDoubleMethodA:  invoke a virtual method that returns a double value
   * @param env A JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param methodID id of a MethodReference
   * @param argAddress a raw address to an array of unions in C, each element is 2-word
   *        and hold an argument of the appropriate type for the method invocation
   * @return the double value returned from the method invocation
   */
  private static double CallDoubleMethodA(JNIEnvironment env, int objJREF, int methodID, Address argAddress)
      throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallDoubleMethodA  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object obj = env.getJNIRef(objJREF);
      Object returnObj = JNIHelpers.invokeWithJValue(obj, methodID, argAddress, TypeReference.Double, false);
      return Reflection.unwrapDouble(returnObj);     // should be a wrapper for a double value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallVoidMethod:  invoke a virtual method that returns a void value
   *                           arguments passed using the vararg ... style
   * NOTE:  the vararg's are not visible in the method signature here;
   *        they are saved in the caller frame and the glue frame
   * @param env A JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param methodID id of a MethodReference
   */
  private static void CallVoidMethod(JNIEnvironment env, int objJREF, int methodID) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallVoidMethod  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object obj = env.getJNIRef(objJREF);
      JNIHelpers.invokeWithDotDotVarArg(obj, methodID, TypeReference.Void, false);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * CallVoidMethodV:  invoke a virtual method that returns void
   * @param env A JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param methodID id of a MethodReference
   * @param argAddress a raw address to a variable argument list, each element is
   *              1-word or 2-words of the appropriate type for the method invocation
   */
  private static void CallVoidMethodV(JNIEnvironment env, int objJREF, int methodID, Address argAddress)
      throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallVoidMethodV  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object obj = env.getJNIRef(objJREF);
      JNIHelpers.invokeWithVarArg(obj, methodID, argAddress, TypeReference.Void, false);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * CallVoidMethodA:  invoke a virtual method that returns void
   * @param env A JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param methodID id of a MethodReference
   * @param argAddress a raw address to an array of unions in C, each element is 2-word
   *        and hold an argument of the appropriate type for the method invocation
   */
  private static void CallVoidMethodA(JNIEnvironment env, int objJREF, int methodID, Address argAddress)
      throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallVoidMethodA  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object obj = env.getJNIRef(objJREF);
      JNIHelpers.invokeWithJValue(obj, methodID, argAddress, TypeReference.Void, false);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * CallNonvirtualObjectMethod:  invoke a virtual method that returns an object
   *                           arguments passed using the vararg ... style
   * NOTE:  the vararg's are not visible in the method signature here;
   *        they are saved in the caller frame and the glue frame
   * @param env A JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param classJREF a JREF index for the class object that declares this method
   * @param methodID id of a MethodReference
   * @return the JREF index for the object returned from the method invocation
   */
  private static int CallNonvirtualObjectMethod(JNIEnvironment env, int objJREF, int classJREF, int methodID)
      throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallNonvirtualObjectMethod  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object obj = env.getJNIRef(objJREF);
      Object returnObj = JNIHelpers.invokeWithDotDotVarArg(obj, methodID, null, true);
      return env.pushJNIRef(returnObj);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallNonvirtualObjectMethodV:  invoke a virtual method that returns an object
   * @param env A JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param classJREF a JREF index for the class object that declares this method
   * @param methodID id of a MethodReference
   * @param argAddress a raw address to a variable argument list, each element is
   *              1-word or 2-words of the appropriate type for the method invocation
   * @return the JREF index for the object returned from the method invocation
   */
  private static int CallNonvirtualObjectMethodV(JNIEnvironment env, int objJREF, int classJREF, int methodID,
                                                 Address argAddress) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallNonvirtualObjectMethodV  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object obj = env.getJNIRef(objJREF);
      Object returnObj = JNIHelpers.invokeWithVarArg(obj, methodID, argAddress, null, true);
      return env.pushJNIRef(returnObj);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallNonvirtualNonvirtualObjectMethodA:  invoke a virtual method that returns an object value
   * @param env A JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param classJREF a JREF index for the class object that declares this method
   * @param methodID id of a MethodReference
   * @param argAddress a raw address to an array of unions in C, each element is 2-word
   *        and hold an argument of the appropriate type for the method invocation
   * @return the JREF index for the object returned from the method invocation
   */
  private static int CallNonvirtualObjectMethodA(JNIEnvironment env, int objJREF, int classJREF, int methodID,
                                                 Address argAddress) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallNonvirtualObjectMethodA  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object obj = env.getJNIRef(objJREF);
      Object returnObj = JNIHelpers.invokeWithJValue(obj, methodID, argAddress, null, true);
      return env.pushJNIRef(returnObj);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallNonvirtualBooleanMethod:  invoke a virtual method that returns a boolean value
   *                           arguments passed using the vararg ... style
   * NOTE:  the vararg's are not visible in the method signature here;
   *        they are saved in the caller frame and the glue frame
   * @param env A JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param classJREF a JREF index for the class object that declares this method
   * @param methodID id of a MethodReference
   * @return the boolean value returned from the method invocation
   */
  private static boolean CallNonvirtualBooleanMethod(JNIEnvironment env, int objJREF, int classJREF, int methodID)
      throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallNonvirtualBooleanMethod  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object obj = env.getJNIRef(objJREF);
      Object returnObj = JNIHelpers.invokeWithDotDotVarArg(obj, methodID, TypeReference.Boolean, true);
      return Reflection.unwrapBoolean(returnObj);     // should be a wrapper for a boolean value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return false;
    }
  }

  /**
   * CallNonvirtualBooleanMethodV:  invoke a virtual method that returns a boolean value
   * @param env A JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param classJREF a JREF index for the class object that declares this method
   * @param methodID id of a MethodReference
   * @param argAddress a raw address to a variable argument list, each element is
   *              1-word or 2-words of the appropriate type for the method invocation
   * @return the boolean value returned from the method invocation
   */
  private static boolean CallNonvirtualBooleanMethodV(JNIEnvironment env, int objJREF, int classJREF, int methodID,
                                                      Address argAddress) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallNonvirtualBooleanMethodV  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object obj = env.getJNIRef(objJREF);
      Object returnObj = JNIHelpers.invokeWithVarArg(obj, methodID, argAddress, TypeReference.Boolean, true);
      return Reflection.unwrapBoolean(returnObj);     // should be a wrapper for a boolean value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return false;
    }
  }

  /**
   * CallNonvirtualBooleanMethodA:  invoke a virtual method that returns a boolean value
   * @param env A JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param classJREF a JREF index for the class object that declares this method
   * @param methodID id of a MethodReference
   * @param argAddress a raw address to an array of unions in C, each element is 2-word
   *        and hold an argument of the appropriate type for the method invocation
   * @return the boolean value returned from the method invocation
   */
  private static boolean CallNonvirtualBooleanMethodA(JNIEnvironment env, int objJREF, int classJREF, int methodID,
                                                      Address argAddress) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallNonvirtualBooleanMethodA  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object obj = env.getJNIRef(objJREF);
      Object returnObj = JNIHelpers.invokeWithJValue(obj, methodID, argAddress, TypeReference.Boolean, true);
      return Reflection.unwrapBoolean(returnObj);     // should be a wrapper for a boolean value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return false;
    }
  }

  /**
   * CallNonvirtualByteMethod:  invoke a virtual method that returns a byte value
   *                           arguments passed using the vararg ... style
   * NOTE:  the vararg's are not visible in the method signature here;
   *        they are saved in the caller frame and the glue frame
   * @param env A JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param classJREF a JREF index for the class object that declares this method
   * @param methodID id of a MethodReference
   * @return the byte value returned from the method invocation
   */
  private static byte CallNonvirtualByteMethod(JNIEnvironment env, int objJREF, int classJREF, int methodID)
      throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallNonvirtualByteMethod  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object obj = env.getJNIRef(objJREF);
      Object returnObj = JNIHelpers.invokeWithDotDotVarArg(obj, methodID, TypeReference.Byte, true);
      return Reflection.unwrapByte(returnObj);     // should be a wrapper for a byte value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallNonvirtualByteMethodV:  invoke a virtual method that returns a byte value
   * @param env A JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param methodID id of a MethodReference
   * @param classJREF a JREF index for the class object that declares this method
   * @param argAddress a raw address to a variable argument list, each element is
   *              1-word or 2-words of the appropriate type for the method invocation
   * @return the byte value returned from the method invocation
   */
  private static byte CallNonvirtualByteMethodV(JNIEnvironment env, int objJREF, int classJREF, int methodID,
                                                Address argAddress) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallNonvirtualByteMethodV  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object obj = env.getJNIRef(objJREF);
      Object returnObj = JNIHelpers.invokeWithVarArg(obj, methodID, argAddress, TypeReference.Byte, true);
      return Reflection.unwrapByte(returnObj);     // should be a wrapper for a byte value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallNonvirtualByteMethodA:  invoke a virtual method that returns a byte value
   * @param env A JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param methodID id of a MethodReference
   * @param classJREF a JREF index for the class object that declares this method
   * @param argAddress a raw address to an array of unions in C, each element is 2-word
   *        and hold an argument of the appropriate type for the method invocation
   * @return the byte value returned from the method invocation
   */
  private static byte CallNonvirtualByteMethodA(JNIEnvironment env, int objJREF, int classJREF, int methodID,
                                                Address argAddress) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallNonvirtualByteMethodA  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object obj = env.getJNIRef(objJREF);
      Object returnObj = JNIHelpers.invokeWithJValue(obj, methodID, argAddress, TypeReference.Byte, true);
      return Reflection.unwrapByte(returnObj);     // should be a wrapper for a byte value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallNonvirtualCharMethod:  invoke a virtual method that returns a char value
   *                           arguments passed using the vararg ... style
   * NOTE:  the vararg's are not visible in the method signature here;
   *        they are saved in the caller frame and the glue frame
   * @param env A JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param classJREF a JREF index for the class object that declares this method
   * @param methodID id of a MethodReference
   * @return the char value returned from the method invocation
   */
  private static char CallNonvirtualCharMethod(JNIEnvironment env, int objJREF, int classJREF, int methodID)
      throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallNonvirtualCharMethod  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object obj = env.getJNIRef(objJREF);
      Object returnObj = JNIHelpers.invokeWithDotDotVarArg(obj, methodID, TypeReference.Char, true);
      return Reflection.unwrapChar(returnObj);     // should be a wrapper for a char value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallNonvirtualCharMethodV:  invoke a virtual method that returns a char value
   * @param env A JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param classJREF a JREF index for the class object that declares this method
   * @param methodID id of a MethodReference
   * @param argAddress a raw address to a variable argument list, each element is
   *              1-word or 2-words of the appropriate type for the method invocation
   * @return the char value returned from the method invocation
   */
  private static char CallNonvirtualCharMethodV(JNIEnvironment env, int objJREF, int classJREF, int methodID,
                                                Address argAddress) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallNonvirtualCharMethodV  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object obj = env.getJNIRef(objJREF);
      Object returnObj = JNIHelpers.invokeWithVarArg(obj, methodID, argAddress, TypeReference.Char, true);
      return Reflection.unwrapChar(returnObj);     // should be a wrapper for a char value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallNonvirtualCharMethodA:  invoke a virtual method that returns a char value
   * @param env A JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param classJREF a JREF index for the class object that declares this method
   * @param methodID id of a MethodReference
   * @param argAddress a raw address to an array of unions in C, each element is 2-word
   *        and hold an argument of the appropriate type for the method invocation
   * @return the char value returned from the method invocation
   */
  private static char CallNonvirtualCharMethodA(JNIEnvironment env, int objJREF, int classJREF, int methodID,
                                                Address argAddress) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallNonvirtualCharMethodA  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object obj = env.getJNIRef(objJREF);
      Object returnObj = JNIHelpers.invokeWithJValue(obj, methodID, argAddress, TypeReference.Char, true);
      return Reflection.unwrapChar(returnObj);     // should be a wrapper for a char value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallNonvirtualShortMethod:  invoke a virtual method that returns a short value
   *                           arguments passed using the vararg ... style
   * NOTE:  the vararg's are not visible in the method signature here;
   *        they are saved in the caller frame and the glue frame
   * @param env A JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param classJREF a JREF index for the class object that declares this method
   * @param methodID id of a MethodReference
   * @return the short value returned from the method invocation
   */
  private static short CallNonvirtualShortMethod(JNIEnvironment env, int objJREF, int classJREF, int methodID)
      throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallNonvirtualShortMethod  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object obj = env.getJNIRef(objJREF);
      Object returnObj = JNIHelpers.invokeWithDotDotVarArg(obj, methodID, TypeReference.Short, true);
      return Reflection.unwrapShort(returnObj);     // should be a wrapper for a short value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallNonvirtualShortMethodV:  invoke a virtual method that returns a short value
   * @param env A JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param classJREF a JREF index for the class object that declares this method
   * @param methodID id of a MethodReference
   * @param argAddress a raw address to a variable argument list, each element is
   *              1-word or 2-words of the appropriate type for the method invocation
   * @return the short value returned from the method invocation
   */
  private static short CallNonvirtualShortMethodV(JNIEnvironment env, int objJREF, int classJREF, int methodID,
                                                  Address argAddress) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallNonvirtualShortMethodV  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object obj = env.getJNIRef(objJREF);
      Object returnObj = JNIHelpers.invokeWithVarArg(obj, methodID, argAddress, TypeReference.Short, true);
      return Reflection.unwrapShort(returnObj);     // should be a wrapper for a short value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallNonvirtualShortMethodA:  invoke a virtual method that returns a short value
   * @param env A JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param classJREF a JREF index for the class object that declares this method
   * @param methodID id of a MethodReference
   * @param argAddress a raw address to an array of unions in C, each element is 2-word
   *        and hold an argument of the appropriate type for the method invocation
   * @return the short value returned from the method invocation
   */
  private static short CallNonvirtualShortMethodA(JNIEnvironment env, int objJREF, int classJREF, int methodID,
                                                  Address argAddress) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallNonvirtualShortMethodA  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object obj = env.getJNIRef(objJREF);
      Object returnObj = JNIHelpers.invokeWithJValue(obj, methodID, argAddress, TypeReference.Short, true);
      return Reflection.unwrapShort(returnObj);     // should be a wrapper for a short value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallNonvirtualIntMethod:  invoke a virtual method that returns a int value
   *                           arguments passed using the vararg ... style
   * NOTE:  the vararg's are not visible in the method signature here;
   *        they are saved in the caller frame and the glue frame
   * @param env A JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param classJREF a JREF index for the class object that declares this method
   * @param methodID id of a MethodReference
   * @return the int value returned from the method invocation
   */
  private static int CallNonvirtualIntMethod(JNIEnvironment env, int objJREF, int classJREF, int methodID)
      throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallNonvirtualIntMethod  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object obj = env.getJNIRef(objJREF);
      Object returnObj = JNIHelpers.invokeWithDotDotVarArg(obj, methodID, TypeReference.Int, true);
      return Reflection.unwrapInt(returnObj);     // should be a wrapper for an integer value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallNonvirtualIntMethodV:  invoke a virtual method that returns an int value
   * @param env A JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param classJREF a JREF index for the class object that declares this method
   * @param methodID id of a MethodReference
   * @param argAddress a raw address to a variable argument list, each element is
   *              1-word or 2-words of the appropriate type for the method invocation
   * @return the int value returned from the method invocation
   */
  private static int CallNonvirtualIntMethodV(JNIEnvironment env, int objJREF, int classJREF, int methodID,
                                              Address argAddress) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallNonvirtualIntMethodV  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object obj = env.getJNIRef(objJREF);
      Object returnObj = JNIHelpers.invokeWithVarArg(obj, methodID, argAddress, TypeReference.Int, true);
      return Reflection.unwrapInt(returnObj);     // should be a wrapper for an integer value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallNonvirtualIntMethodA:  invoke a virtual method that returns an integer value
   * @param env A JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param classJREF a JREF index for the class object that declares this method
   * @param methodID id of a MethodReference
   * @param argAddress a raw address to an array of unions in C, each element is 2-word
   *        and hold an argument of the appropriate type for the method invocation
   * @return the integer value returned from the method invocation
   */
  private static int CallNonvirtualIntMethodA(JNIEnvironment env, int objJREF, int classJREF, int methodID,
                                              Address argAddress) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallNonvirtualIntMethodA  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object obj = env.getJNIRef(objJREF);
      Object returnObj = JNIHelpers.invokeWithJValue(obj, methodID, argAddress, TypeReference.Int, true);
      return Reflection.unwrapInt(returnObj);     // should be a wrapper for an integer value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallNonvirtualLongMethod:  invoke a virtual method that returns a long value
   *                           arguments passed using the vararg ... style
   * NOTE:  the vararg's are not visible in the method signature here;
   *        they are saved in the caller frame and the glue frame
   * @param env A JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param classJREF a JREF index for the class object that declares this method
   * @param methodID id of a MethodReference
   * @return the long value returned from the method invocation
   */
  private static long CallNonvirtualLongMethod(JNIEnvironment env, int objJREF, int classJREF, int methodID)
      throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallNonvirtualLongMethod  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object obj = env.getJNIRef(objJREF);
      Object returnObj = JNIHelpers.invokeWithDotDotVarArg(obj, methodID, TypeReference.Long, true);
      return Reflection.unwrapLong(returnObj);     // should be a wrapper for a long value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallNonvirtualLongMethodV:  invoke a virtual method that returns a long value
   * @param env A JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param classJREF a JREF index for the class object that declares this method
   * @param methodID id of a MethodReference
   * @param argAddress a raw address to a variable argument list, each element is
   *              1-word or 2-words of the appropriate type for the method invocation
   * @return the long value returned from the method invocation
   */
  private static long CallNonvirtualLongMethodV(JNIEnvironment env, int objJREF, int classJREF, int methodID,
                                                Address argAddress) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallNonvirtualLongMethodV  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object obj = env.getJNIRef(objJREF);
      Object returnObj = JNIHelpers.invokeWithVarArg(obj, methodID, argAddress, TypeReference.Long, true);
      return Reflection.unwrapLong(returnObj);     // should be a wrapper for a long value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallNonvirtualLongMethodA:  invoke a virtual method that returns a long value
   * @param env A JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param classJREF a JREF index for the class object that declares this method
   * @param methodID id of a MethodReference
   * @param argAddress a raw address to an array of unions in C, each element is 2-word
   *        and hold an argument of the appropriate type for the method invocation
   * @return the long value returned from the method invocation
   */
  private static long CallNonvirtualLongMethodA(JNIEnvironment env, int objJREF, int classJREF, int methodID,
                                                Address argAddress) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallNonvirtualLongMethodA  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object obj = env.getJNIRef(objJREF);
      Object returnObj = JNIHelpers.invokeWithJValue(obj, methodID, argAddress, TypeReference.Long, true);
      return Reflection.unwrapLong(returnObj);     // should be a wrapper for a long value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallNonvirtualFloatMethod:  invoke a virtual method that returns a float value
   *                           arguments passed using the vararg ... style
   * NOTE:  the vararg's are not visible in the method signature here;
   *        they are saved in the caller frame and the glue frame
   * @param env A JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param classJREF a JREF index for the class object that declares this method
   * @param methodID id of a MethodReference
   * @return the float value returned from the method invocation
   */
  private static float CallNonvirtualFloatMethod(JNIEnvironment env, int objJREF, int classJREF, int methodID)
      throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallNonvirtualFloatMethod  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object obj = env.getJNIRef(objJREF);
      Object returnObj = JNIHelpers.invokeWithDotDotVarArg(obj, methodID, TypeReference.Float, true);
      return Reflection.unwrapFloat(returnObj);     // should be a wrapper for a float value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallNonvirtualFloatMethodV:  invoke a virtual method that returns a float value
   * @param env A JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param classJREF a JREF index for the class object that declares this method
   * @param methodID id of a MethodReference
   * @param argAddress a raw address to a variable argument list, each element is
   *              1-word or 2-words of the appropriate type for the method invocation
   * @return the float value returned from the method invocation
   */
  private static float CallNonvirtualFloatMethodV(JNIEnvironment env, int objJREF, int classJREF, int methodID,
                                                  Address argAddress) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallNonvirtualFloatMethodV  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object obj = env.getJNIRef(objJREF);
      Object returnObj = JNIHelpers.invokeWithVarArg(obj, methodID, argAddress, TypeReference.Float, true);
      return Reflection.unwrapFloat(returnObj);     // should be a wrapper for a float value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallNonvirtualFloatMethodA:  invoke a virtual method that returns a float value
   * @param env A JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param classJREF a JREF index for the class object that declares this method
   * @param methodID id of a MethodReference
   * @param argAddress a raw address to an array of unions in C, each element is 2-word
   *        and hold an argument of the appropriate type for the method invocation
   * @return the float value returned from the method invocation
   */
  private static float CallNonvirtualFloatMethodA(JNIEnvironment env, int objJREF, int classJREF, int methodID,
                                                  Address argAddress) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallNonvirtualFloatMethodA  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object obj = env.getJNIRef(objJREF);
      Object returnObj = JNIHelpers.invokeWithJValue(obj, methodID, argAddress, TypeReference.Float, true);
      return Reflection.unwrapFloat(returnObj);     // should be a wrapper for a float value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallNonvirtualDoubleMethod:  invoke a virtual method that returns a double value
   *                           arguments passed using the vararg ... style
   * NOTE:  the vararg's are not visible in the method signature here;
   *        they are saved in the caller frame and the glue frame
   * @param env A JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param classJREF a JREF index for the class object that declares this method
   * @param methodID id of a MethodReference
   * @return the double value returned from the method invocation
   */
  private static double CallNonvirtualDoubleMethod(JNIEnvironment env, int objJREF, int classJREF, int methodID)
      throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallNonvirtualDoubleMethod  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object obj = env.getJNIRef(objJREF);
      Object returnObj = JNIHelpers.invokeWithDotDotVarArg(obj, methodID, TypeReference.Double, true);
      return Reflection.unwrapDouble(returnObj);     // should be a wrapper for a double value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallNonvirtualDoubleMethodV:  invoke a virtual method that returns a double value
   * @param env A JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param classJREF a JREF index for the class object that declares this method
   * @param methodID id of a MethodReference
   * @param argAddress a raw address to a variable argument list, each element is
   *              1-word or 2-words of the appropriate type for the method invocation
   * @return the double value returned from the method invocation
   */
  private static double CallNonvirtualDoubleMethodV(JNIEnvironment env, int objJREF, int classJREF, int methodID,
                                                    Address argAddress) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallNonvirtualDoubleMethodV  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object obj = env.getJNIRef(objJREF);
      Object returnObj = JNIHelpers.invokeWithVarArg(obj, methodID, argAddress, TypeReference.Double, true);
      return Reflection.unwrapDouble(returnObj);     // should be a wrapper for a double value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallNonvirtualDoubleMethodA:  invoke a virtual method that returns a double value
   * @param env A JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param classJREF a JREF index for the class object that declares this method
   * @param methodID id of a MethodReference
   * @param argAddress a raw address to an array of unions in C, each element is 2-word
   *        and hold an argument of the appropriate type for the method invocation
   * @return the double value returned from the method invocation
   */
  private static double CallNonvirtualDoubleMethodA(JNIEnvironment env, int objJREF, int classJREF, int methodID,
                                                    Address argAddress) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallNonvirtualDoubleMethodA  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object obj = env.getJNIRef(objJREF);
      Object returnObj = JNIHelpers.invokeWithJValue(obj, methodID, argAddress, TypeReference.Double, true);
      return Reflection.unwrapDouble(returnObj);     // should be a wrapper for a double value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallNonvirtualVoidMethod:  invoke a virtual method that returns a void value
   *                           arguments passed using the vararg ... style
   * NOTE:  the vararg's are not visible in the method signature here;
   *        they are saved in the caller frame and the glue frame
   * @param env A JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param classJREF a JREF index for the class object that declares this method
   * @param methodID id of a MethodReference
   */
  private static void CallNonvirtualVoidMethod(JNIEnvironment env, int objJREF, int classJREF, int methodID)
      throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallNonvirtualVoidMethod  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object obj = env.getJNIRef(objJREF);
      JNIHelpers.invokeWithDotDotVarArg(obj, methodID, TypeReference.Void, true);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * CallNonvirtualVoidMethodV:  invoke a virtual method that returns void
   * @param env A JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param classJREF a JREF index for the class object that declares this method
   * @param methodID id of a MethodReference
   * @param argAddress a raw address to a variable argument list, each element is
   *              1-word or 2-words of the appropriate type for the method invocation
   */
  private static void CallNonvirtualVoidMethodV(JNIEnvironment env, int objJREF, int classJREF, int methodID,
                                                Address argAddress) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallNonvirtualVoidMethodV  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object obj = env.getJNIRef(objJREF);
      JNIHelpers.invokeWithVarArg(obj, methodID, argAddress, TypeReference.Void, true);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * CallNonvirtualVoidMethodA:  invoke a virtual method that returns void
   * @param env A JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param classJREF a JREF index for the class object that declares this method
   * @param methodID id of a MethodReference
   * @param argAddress a raw address to an array of unions in C, each element is 2-word
   *        and hold an argument of the appropriate type for the method invocation
   */
  private static void CallNonvirtualVoidMethodA(JNIEnvironment env, int objJREF, int classJREF, int methodID,
                                                Address argAddress) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallNonvirtualVoidMethodA  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object obj = env.getJNIRef(objJREF);
      JNIHelpers.invokeWithJValue(obj, methodID, argAddress, TypeReference.Void, true);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * GetFieldID:  return a field id, which can be cached in native code and reused
   * @param env A JREF index for the JNI environment object
   * @param classJREF a JREF index for the RVMClass object
   * @param fieldNameAddress a raw address to a null-terminated string in C for the field name
   * @param descriptorAddress a raw address to a null-terminated string in C for the descriptor
   * @return the fieldID of an instance field given the class, field name
   *         and type. Return 0 if the field is not found
   * @exception NoSuchFieldError if the specified field cannot be found
   * @exception ExceptionInInitializerError if the class initializer fails
   * @exception OutOfMemoryError if the system runs out of memory
   */
  private static int GetFieldID(JNIEnvironment env, int classJREF, Address fieldNameAddress,
                                Address descriptorAddress) {
    if (traceJNI) VM.sysWrite("JNI called: GetFieldID  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      if (traceJNI)
        VM.sysWriteln("called GetFieldID with classJREF = ",classJREF);
      Class<?> cls = (Class<?>) env.getJNIRef(classJREF);
      if (VM.VerifyAssertions) VM._assert(cls!=null);
      String fieldString = JNIHelpers.createStringFromC(fieldNameAddress);
      Atom fieldName = Atom.findOrCreateAsciiAtom(fieldString);

      String descriptorString = JNIHelpers.createStringFromC(descriptorAddress);
      Atom descriptor = Atom.findOrCreateAsciiAtom(descriptorString);

      // list of all instance fields including superclasses
      RVMField[] fields = java.lang.JikesRVMSupport.getTypeForClass(cls).getInstanceFields();
      for (RVMField f : fields) {
        if (f.getName() == fieldName && f.getDescriptor() == descriptor) {
          return f.getId();
        }
      }

      // create exception and return 0 if not found
      env.recordException(new NoSuchFieldError(fieldString + ", " + descriptorString + " of " + cls));
      return 0;
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * GetObjectField: read a instance field of type Object
   * @param env A JREF index for the JNI environment object
   * @param objJREF a JREF index for the target object
   * @param fieldID the id for the RVMField that describes this field
   * @return the value of the Object field, converted to a JREF index
   *         or 0 if the fieldID is incorrect
   */
  private static int GetObjectField(JNIEnvironment env, int objJREF, int fieldID) {
    if (traceJNI) VM.sysWrite("JNI called: GetObjectField  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object obj = env.getJNIRef(objJREF);
      RVMField field = MemberReference.getMemberRef(fieldID).asFieldReference().resolve();
      Object objVal = field.getObjectUnchecked(obj);
      return env.pushJNIRef(objVal);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * GetBooleanField: read an instance field of type boolean
   * @param env A JREF index for the JNI environment object
   * @param objJREF a JREF index for the target object
   * @param fieldID the id for the RVMField that describes this field
   * @return the value of the boolean field, or 0 if the fieldID is incorrect
   */
  private static int GetBooleanField(JNIEnvironment env, int objJREF, int fieldID) {
    if (traceJNI) VM.sysWrite("JNI called: GetBooleanField  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object obj = env.getJNIRef(objJREF);
      RVMField field = MemberReference.getMemberRef(fieldID).asFieldReference().resolve();
      return field.getBooleanValueUnchecked(obj) ? 1 : 0;
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * GetByteField:  read an instance field of type byte
   * @param env A JREF index for the JNI environment object
   * @param objJREF a JREF index for the target object
   * @param fieldID the id for the RVMField that describes this field
   * @return the value of the byte field, or 0 if the fieldID is incorrect
   */
  private static int GetByteField(JNIEnvironment env, int objJREF, int fieldID) {
    if (traceJNI) VM.sysWrite("JNI called: GetByteField  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object obj = env.getJNIRef(objJREF);
      RVMField field = MemberReference.getMemberRef(fieldID).asFieldReference().resolve();
      return field.getByteValueUnchecked(obj);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * GetCharField:  read an instance field of type character
   * @param env A JREF index for the JNI environment object
   * @param objJREF a JREF index for the target object
   * @param fieldID the id for the RVMField that describes this field
   * @return the value of the character field, or 0 if the fieldID is incorrect
   */
  private static int GetCharField(JNIEnvironment env, int objJREF, int fieldID) {
    if (traceJNI) VM.sysWrite("JNI called: GetCharField  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object obj = env.getJNIRef(objJREF);
      RVMField field = MemberReference.getMemberRef(fieldID).asFieldReference().resolve();
      return field.getCharValueUnchecked(obj);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * GetShortField:  read an instance field of type short
   * @param env A JREF index for the JNI environment object
   * @param objJREF a JREF index for the target object
   * @param fieldID the id for the RVMField that describes this field
   * @return the value of the short field, or 0 if the fieldID is incorrect
   */
  private static int GetShortField(JNIEnvironment env, int objJREF, int fieldID) {
    if (traceJNI) VM.sysWrite("JNI called: GetShortField  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object obj = env.getJNIRef(objJREF);
      RVMField field = MemberReference.getMemberRef(fieldID).asFieldReference().resolve();
      return field.getShortValueUnchecked(obj);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * GetIntField:  read an instance field of type integer
   * @param env A JREF index for the JNI environment object
   * @param objJREF a JREF index for the target object
   * @param fieldID the id for the RVMField that describes this field
   * @return the value of the integer field, or 0 if the fieldID is incorrect
   */
  private static int GetIntField(JNIEnvironment env, int objJREF, int fieldID) {
    if (traceJNI) VM.sysWrite("JNI called: GetIntField  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object obj = env.getJNIRef(objJREF);
      RVMField field = MemberReference.getMemberRef(fieldID).asFieldReference().resolve();
      return field.getIntValueUnchecked(obj);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * GetLongField:  read an instance field of type long
   * @param env A JREF index for the JNI environment object
   * @param objJREF a JREF index for the target object
   * @param fieldID the id for the RVMField that describes this field
   * @return the value of the long field or 0 if the fieldID is incorrect
   */
  private static long GetLongField(JNIEnvironment env, int objJREF, int fieldID) {
    if (traceJNI) VM.sysWrite("JNI called: GetLongField  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object obj = env.getJNIRef(objJREF);
      RVMField field = MemberReference.getMemberRef(fieldID).asFieldReference().resolve();
      return field.getLongValueUnchecked(obj);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0L;
    }
  }

  /**
   * GetFloatField:  read an instance field of type float
   * @param env A JREF index for the JNI environment object
   * @param objJREF a JREF index for the target object
   * @param fieldID the id for the RVMField that describes this field
   * @return the value of the float field or 0 if the fieldID is incorrect
   */
  private static float GetFloatField(JNIEnvironment env, int objJREF, int fieldID) {
    if (traceJNI) VM.sysWrite("JNI called: GetFloatField  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object obj = env.getJNIRef(objJREF);
      RVMField field = MemberReference.getMemberRef(fieldID).asFieldReference().resolve();
      return field.getFloatValueUnchecked(obj);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0f;
    }
  }

  /**
   * GetDoubleField:  read an instance field of type double
   * @param env A JREF index for the JNI environment object
   * @param objJREF a JREF index for the target object
   * @param fieldID the id for the RVMField that describes this field
   * @return the value of the double field or 0 if the fieldID is incorrect
   */
  private static double GetDoubleField(JNIEnvironment env, int objJREF, int fieldID) {
    if (traceJNI) VM.sysWrite("JNI called: GetDoubleField  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object obj = env.getJNIRef(objJREF);
      RVMField field = MemberReference.getMemberRef(fieldID).asFieldReference().resolve();
      return field.getDoubleValueUnchecked(obj);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0.0;
    }
  }

  /**
   * SetObjectField: set a instance field of type Object
   * @param env A JREF index for the JNI environment object
   * @param objJREF a JREF index for the target object
   * @param fieldID the id for the RVMField that describes this field
   * @param valueJREF a JREF index for the value to assign
   */
  private static void SetObjectField(JNIEnvironment env, int objJREF, int fieldID, int valueJREF) {
    if (traceJNI) VM.sysWrite("JNI called: SetObjectField  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object obj = env.getJNIRef(objJREF);
      Object value = env.getJNIRef(valueJREF);
      RVMField field = MemberReference.getMemberRef(fieldID).asFieldReference().resolve();
      field.setObjectValueUnchecked(obj, value);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * SetBooleanField: set an instance field of type boolean
   * @param env A JREF index for the JNI environment object
   * @param objJREF a JREF index for the target object
   * @param fieldID the id for the RVMField that describes this field
   * @param value   boolean value to assign
   */
  private static void SetBooleanField(JNIEnvironment env, int objJREF, int fieldID, boolean value) {
    if (traceJNI) VM.sysWrite("JNI called: SetBooleanField  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object obj = env.getJNIRef(objJREF);
      RVMField field = MemberReference.getMemberRef(fieldID).asFieldReference().resolve();
      field.setBooleanValueUnchecked(obj, value);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * SetByteField: set an instance field of type byte
   * @param env A JREF index for the JNI environment object
   * @param objJREF a JREF index for the target object
   * @param fieldID the id for the RVMField that describes this field
   * @param value   byte value to assign
   */
  private static void SetByteField(JNIEnvironment env, int objJREF, int fieldID, byte value) {
    if (traceJNI) VM.sysWrite("JNI called: SetByteField  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object obj = env.getJNIRef(objJREF);
      RVMField field = MemberReference.getMemberRef(fieldID).asFieldReference().resolve();
      field.setByteValueUnchecked(obj, value);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * SetCharField: set an instance field of type char
   * @param env A JREF index for the JNI environment object
   * @param objJREF a JREF index for the target object
   * @param fieldID the id for the RVMField that describes this field
   * @param value   char value to assign
   */
  private static void SetCharField(JNIEnvironment env, int objJREF, int fieldID, char value) {
    if (traceJNI) VM.sysWrite("JNI called: SetCharField  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object obj = env.getJNIRef(objJREF);
      RVMField field = MemberReference.getMemberRef(fieldID).asFieldReference().resolve();
      field.setCharValueUnchecked(obj, value);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * SetShortField: set an instance field of type short
   * @param env A JREF index for the JNI environment object
   * @param objJREF a JREF index for the target object
   * @param fieldID the id for the RVMField that describes this field
   * @param value   short value to assign
   */
  private static void SetShortField(JNIEnvironment env, int objJREF, int fieldID, short value) {
    if (traceJNI) VM.sysWrite("JNI called: SetShortField  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object obj = env.getJNIRef(objJREF);
      RVMField field = MemberReference.getMemberRef(fieldID).asFieldReference().resolve();
      field.setShortValueUnchecked(obj, value);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * SetIntField: set an instance field of type integer
   * @param env A JREF index for the JNI environment object
   * @param objJREF a JREF index for the target object
   * @param fieldID the id for the RVMField that describes this field
   * @param value   integer value to assign
   */
  private static void SetIntField(JNIEnvironment env, int objJREF, int fieldID, int value) {
    if (traceJNI) VM.sysWrite("JNI called: SetIntField  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object obj = env.getJNIRef(objJREF);
      RVMField field = MemberReference.getMemberRef(fieldID).asFieldReference().resolve();
      field.setIntValueUnchecked(obj, value);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * SetLongField: set an instance field of type long
   * @param env A JREF index for the JNI environment object
   * @param objJREF a JREF index for the target object
   * @param fieldID the id for the RVMField that describes this field
   * @param value   long value to assign
   */
  private static void SetLongField(JNIEnvironment env, int objJREF, int fieldID, long value) {
    if (traceJNI) VM.sysWrite("JNI called: SetLongField  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object obj = env.getJNIRef(objJREF);
      RVMField field = MemberReference.getMemberRef(fieldID).asFieldReference().resolve();
      field.setLongValueUnchecked(obj, value);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * SetFloatField: set an instance field of type float
   * @param env A JREF index for the JNI environment object
   * @param objJREF a JREF index for the target object
   * @param fieldID the id for the RVMField that describes this field
   * @param value   float value to assign
   */
  private static void SetFloatField(JNIEnvironment env, int objJREF, int fieldID, float value) {
    if (traceJNI) VM.sysWrite("JNI called: SetFloatField  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object obj = env.getJNIRef(objJREF);
      RVMField field = MemberReference.getMemberRef(fieldID).asFieldReference().resolve();
      field.setFloatValueUnchecked(obj, value);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * SetDoubleField: set an instance field of type double
   * @param env A JREF index for the JNI environment object
   * @param objJREF a JREF index for the target object
   * @param fieldID the id for the RVMField that describes this field
   * @param value   double value to assign
   */
  private static void SetDoubleField(JNIEnvironment env, int objJREF, int fieldID, double value) {
    if (traceJNI) VM.sysWrite("JNI called: SetDoubleField  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object obj = env.getJNIRef(objJREF);
      RVMField field = MemberReference.getMemberRef(fieldID).asFieldReference().resolve();
      field.setDoubleValueUnchecked(obj, value);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * GetStaticMethodID:  return the method ID for invocation later
   * @param env A JREF index for the JNI environment object
   * @param classJREF a JREF index for the class object
   * @param methodNameAddress a raw address to a null-terminated string in C for the method name
   * @param methodSigAddress a raw address to a null-terminated string in C for <DOCUMENTME TODO>
   * @return a method ID or null if it fails
   * @exception NoSuchMethodError if the method is not found
   * @exception ExceptionInInitializerError if the initializer fails
   * @exception OutOfMemoryError if the system runs out of memory
   */
  private static int GetStaticMethodID(JNIEnvironment env, int classJREF, Address methodNameAddress,
                                       Address methodSigAddress) {
    if (traceJNI) VM.sysWrite("JNI called: GetStaticMethodID  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      // obtain the names as String from the native space
      String methodString = JNIHelpers.createStringFromC(methodNameAddress);
      Atom methodName = Atom.findOrCreateAsciiAtom(methodString);
      String sigString = JNIHelpers.createStringFromC(methodSigAddress);
      Atom sigName = Atom.findOrCreateAsciiAtom(sigString);

      // get the target class
      Class<?> jcls = (Class<?>) env.getJNIRef(classJREF);
      RVMType type = java.lang.JikesRVMSupport.getTypeForClass(jcls);
      if (!type.isClassType()) {
        env.recordException(new NoSuchMethodError());
        return 0;
      }

      RVMClass klass = type.asClass();
      if (!klass.isInitialized()) {
        RuntimeEntrypoints.initializeClassForDynamicLink(klass);
      }

      // Find the target method
      RVMMethod meth = klass.findStaticMethod(methodName, sigName);
      if (meth == null) {
        env.recordException(new NoSuchMethodError());
        return 0;
      }

      if (traceJNI) VM.sysWrite("got method " + meth + "\n");
      return meth.getId();
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallStaticObjectMethod:  invoke a static method that returns an object value
   *                          arguments passed using the vararg ... style
   * NOTE:  the vararg's are not visible in the method signature here; they are saved
   *        in the caller frame and the glue frame
   * @param env A JREF index for the JNI environment object
   * @param classJREF a JREF index for the class object
   * @param methodID id of a MethodReference
   * @return the JREF index for the object returned from the method invocation
   */
  private static int CallStaticObjectMethod(JNIEnvironment env, int classJREF, int methodID) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallStaticObjectMethod  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object returnObj = JNIHelpers.invokeWithDotDotVarArg(methodID, null);
      return env.pushJNIRef(returnObj);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallStaticObjectMethodV:  invoke a static method that returns an object
   * @param env A JREF index for the JNI environment object
   * @param classJREF a JREF index for the class object
   * @param methodID id of a MethodReference
   * @param argAddress a raw address to a variable argument list, each element is 1-word or 2-words
   *                   of the appropriate type for the method invocation
   * @return the JREF index for the object returned from the method invocation
   */
  private static int CallStaticObjectMethodV(JNIEnvironment env, int classJREF, int methodID, Address argAddress)
      throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallStaticObjectMethodV  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object returnObj = JNIHelpers.invokeWithVarArg(methodID, argAddress, null);
      return env.pushJNIRef(returnObj);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallStaticObjectMethodA:  invoke a static method that returns an object
   * @param env A JREF index for the JNI environment object
   * @param classJREF a JREF index for the class object
   * @param methodID id of a MethodReference
   * @param argAddress a raw address to an array of unions in C, each element is 2-word and hold an argument
   *                   of the appropriate type for the method invocation
   * @return the JREF index for the object returned from the method invocation
   */
  private static int CallStaticObjectMethodA(JNIEnvironment env, int classJREF, int methodID, Address argAddress)
      throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallStaticObjectMethodA  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object returnObj = JNIHelpers.invokeWithJValue(methodID, argAddress, null);
      return env.pushJNIRef(returnObj);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallStaticBooleanMethod:  invoke a static method that returns a boolean value
   *                           arguments passed using the vararg ... style
   * NOTE:  the vararg's are not visible in the method signature here; they are saved
   *        in the caller frame and the glue frame
   * @param env A JREF index for the JNI environment object
   * @param classJREF a JREF index for the class object
   * @param methodID id of a MethodReference
   * @return the boolean value returned from the method invocation
   */
  private static boolean CallStaticBooleanMethod(JNIEnvironment env, int classJREF, int methodID) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallStaticBooleanMethod  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object returnObj = JNIHelpers.invokeWithDotDotVarArg(methodID, TypeReference.Boolean);
      return Reflection.unwrapBoolean(returnObj);     // should be a wrapper for a boolean value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return false;
    }
  }

  /**
   * CallStaticBooleanMethodV:  invoke a static method that returns a boolean value
   * @param env A JREF index for the JNI environment object
   * @param classJREF a JREF index for the class object
   * @param methodID id of a MethodReference
   * @param argAddress a raw address to a variable argument list, each element is 1-word or 2-words
   *                   of the appropriate type for the method invocation
   * @return the boolean value returned from the method invocation
   */
  private static boolean CallStaticBooleanMethodV(JNIEnvironment env, int classJREF, int methodID,
                                                  Address argAddress) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallStaticBooleanMethodV  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object returnObj = JNIHelpers.invokeWithVarArg(methodID, argAddress, TypeReference.Boolean);
      return Reflection.unwrapBoolean(returnObj);     // should be a wrapper for a boolean value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return false;
    }
  }

  /**
   * CallStaticBooleanMethodA:  invoke a static method that returns a boolean value
   * @param env A JREF index for the JNI environment object
   * @param classJREF a JREF index for the class object
   * @param methodID id of a MethodReference
   * @param argAddress a raw address to an array of unions in C, each element is 2-word and hold an argument
   *                   of the appropriate type for the method invocation
   * @return the boolean value returned from the method invocation
   */
  private static boolean CallStaticBooleanMethodA(JNIEnvironment env, int classJREF, int methodID,
                                                  Address argAddress) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallStaticBooleanMethodA  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object returnObj = JNIHelpers.invokeWithJValue(methodID, argAddress, TypeReference.Boolean);
      return Reflection.unwrapBoolean(returnObj);     // should be a wrapper for a boolean value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return false;
    }
  }

  /**
   * CallStaticByteMethod:  invoke a static method that returns a byte value
   *                        arguments passed using the vararg ... style
   * NOTE:  the vararg's are not visible in the method signature here; they are saved
   *        in the caller frame and the glue frame
   * @param env A JREF index for the JNI environment object
   * @param classJREF a JREF index for the class object
   * @param methodID id of a MethodReference
   * @return the byte value returned from the method invocation
   */
  private static byte CallStaticByteMethod(JNIEnvironment env, int classJREF, int methodID) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallStaticByteMethod  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object returnObj = JNIHelpers.invokeWithDotDotVarArg(methodID, TypeReference.Byte);
      return Reflection.unwrapByte(returnObj);     // should be a wrapper for a byte value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallStaticByteMethodV:  invoke a static method that returns a byte value
   * @param env A JREF index for the JNI environment object
   * @param classJREF a JREF index for the class object
   * @param methodID id of a MethodReference
   * @param argAddress a raw address to a variable argument list, each element is 1-word or 2-words
   *                   of the appropriate type for the method invocation
   * @return the byte value returned from the method invocation
   */
  private static byte CallStaticByteMethodV(JNIEnvironment env, int classJREF, int methodID, Address argAddress)
      throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallStaticByteMethodV  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object returnObj = JNIHelpers.invokeWithVarArg(methodID, argAddress, TypeReference.Byte);
      return Reflection.unwrapByte(returnObj);     // should be a wrapper for a byte value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallStaticByteMethodA:  invoke a static method that returns a byte value
   * @param env A JREF index for the JNI environment object
   * @param classJREF a JREF index for the class object
   * @param methodID id of a MethodReference
   * @param argAddress a raw address to an array of unions in C, each element is 2-word and hold an argument
   *                   of the appropriate type for the method invocation
   * @return the byte value returned from the method invocation
   */
  private static byte CallStaticByteMethodA(JNIEnvironment env, int classJREF, int methodID, Address argAddress)
      throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallStaticByteMethodA  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object returnObj = JNIHelpers.invokeWithJValue(methodID, argAddress, TypeReference.Byte);
      return Reflection.unwrapByte(returnObj);     // should be a wrapper for a byte value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallStaticCharMethod:  invoke a static method that returns a char value
   *                        arguments passed using the vararg ... style
   * NOTE:  the vararg's are not visible in the method signature here; they are saved
   *        in the caller frame and the glue frame
   * @param env A JREF index for the JNI environment object
   * @param classJREF a JREF index for the class object
   * @param methodID id of a MethodReference
   * @return the char value returned from the method invocation
   */
  private static char CallStaticCharMethod(JNIEnvironment env, int classJREF, int methodID) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallStaticCharMethod  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object returnObj = JNIHelpers.invokeWithDotDotVarArg(methodID, TypeReference.Char);
      return Reflection.unwrapChar(returnObj);     // should be a wrapper for a char value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallStaticCharMethodV:  invoke a static method that returns a char value
   * @param env A JREF index for the JNI environment object
   * @param classJREF a JREF index for the class object
   * @param methodID id of a MethodReference
   * @param argAddress a raw address to a variable argument list, each element is 1-word or 2-words
   *                   of the appropriate type for the method invocation
   * @return the char value returned from the method invocation
   */
  private static char CallStaticCharMethodV(JNIEnvironment env, int classJREF, int methodID, Address argAddress)
      throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallStaticCharMethodV  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object returnObj = JNIHelpers.invokeWithVarArg(methodID, argAddress, TypeReference.Char);
      return Reflection.unwrapChar(returnObj);     // should be a wrapper for a char value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallStaticCharMethodA:  invoke a static method that returns a char value
   * @param env A JREF index for the JNI environment object
   * @param classJREF a JREF index for the class object
   * @param methodID id of a MethodReference
   * @param argAddress a raw address to an array of unions in C, each element is 2-word and hold an argument
   *                   of the appropriate type for the method invocation
   * @return the char value returned from the method invocation
   */
  private static char CallStaticCharMethodA(JNIEnvironment env, int classJREF, int methodID, Address argAddress)
      throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallStaticCharMethodA  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object returnObj = JNIHelpers.invokeWithJValue(methodID, argAddress, TypeReference.Char);
      return Reflection.unwrapChar(returnObj);     // should be a wrapper for a char value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallStaticShortMethod:  invoke a static method that returns a short value
   *                         arguments passed using the vararg ... style
   * NOTE:  the vararg's are not visible in the method signature here; they are saved
   *        in the caller frame and the glue frame
   * @param env A JREF index for the JNI environment object
   * @param classJREF a JREF index for the class object
   * @param methodID id of a MethodReference
   * @return the short value returned from the method invocation
   */
  private static short CallStaticShortMethod(JNIEnvironment env, int classJREF, int methodID) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallStaticShortMethod  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object returnObj = JNIHelpers.invokeWithDotDotVarArg(methodID, TypeReference.Short);
      return Reflection.unwrapShort(returnObj);     // should be a wrapper for an short value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallStaticShortMethodV:  invoke a static method that returns a short value
   * @param env A JREF index for the JNI environment object
   * @param classJREF a JREF index for the class object
   * @param methodID id of a MethodReference
   * @param argAddress a raw address to a variable argument list, each element is 1-word or 2-words
   *                   of the appropriate type for the method invocation
   * @return the short value returned from the method invocation
   */
  private static short CallStaticShortMethodV(JNIEnvironment env, int classJREF, int methodID, Address argAddress)
      throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallStaticShortMethodV  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object returnObj = JNIHelpers.invokeWithVarArg(methodID, argAddress, TypeReference.Short);
      return Reflection.unwrapShort(returnObj);     // should be a wrapper for a short value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallStaticShortMethodA:  invoke a static method that returns a short value
   * @param env A JREF index for the JNI environment object
   * @param classJREF a JREF index for the class object
   * @param methodID id of a MethodReference
   * @param argAddress a raw address to an array of unions in C, each element is 2-word and hold an argument
   *                   of the appropriate type for the method invocation
   * @return the short value returned from the method invocation
   */
  private static short CallStaticShortMethodA(JNIEnvironment env, int classJREF, int methodID, Address argAddress)
      throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallStaticShortMethodA  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object returnObj = JNIHelpers.invokeWithJValue(methodID, argAddress, TypeReference.Short);
      return Reflection.unwrapShort(returnObj);     // should be a wrapper for a short value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallStaticIntMethod:  invoke a static method that returns an integer value
   *                       arguments passed using the vararg ... style
   * NOTE:  the vararg's are not visible in the method signature here; they are saved
   *        in the caller frame and the glue frame
   * @param env A JREF index for the JNI environment object
   * @param classJREF a JREF index for the class object
   * @param methodID id of a MethodReference
   * @return the integer value returned from the method invocation
   */
  private static int CallStaticIntMethod(JNIEnvironment env, int classJREF, int methodID) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallStaticIntMethod  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object returnObj = JNIHelpers.invokeWithDotDotVarArg(methodID, TypeReference.Int);
      return Reflection.unwrapInt(returnObj);     // should be a wrapper for an integer value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallStaticIntMethodV:  invoke a static method that returns an integer value
   * @param env A JREF index for the JNI environment object
   * @param classJREF a JREF index for the class object
   * @param methodID id of a MethodReference
   * @param argAddress a raw address to a variable argument list, each element is 1-word or 2-words
   *                   of the appropriate type for the method invocation
   * @return the integer value returned from the method invocation
   */
  private static int CallStaticIntMethodV(JNIEnvironment env, int classJREF, int methodID, Address argAddress)
      throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallStaticIntMethodV  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object returnObj = JNIHelpers.invokeWithVarArg(methodID, argAddress, TypeReference.Int);
      return Reflection.unwrapInt(returnObj);     // should be a wrapper for an integer value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallStaticIntMethodA:  invoke a static method that returns an integer value
   * @param env A JREF index for the JNI environment object
   * @param classJREF a JREF index for the class object
   * @param methodID id of a MethodReference
   * @param argAddress a raw address to an array of unions in C, each element is 2-word and hold an argument
   *                   of the appropriate type for the method invocation
   * @return the integer value returned from the method invocation
   */
  private static int CallStaticIntMethodA(JNIEnvironment env, int classJREF, int methodID, Address argAddress)
      throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallStaticIntMethodA  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object returnObj = JNIHelpers.invokeWithJValue(methodID, argAddress, TypeReference.Int);
      return Reflection.unwrapInt(returnObj);     // should be a wrapper for an integer value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallStaticLongMethod:  invoke a static method that returns a long value
   *                        arguments passed using the vararg ... style
   * NOTE:  the vararg's are not visible in the method signature here; they are saved
   *        in the caller frame and the glue frame
   * @param env A JREF index for the JNI environment object
   * @param classJREF a JREF index for the class object
   * @param methodID id of a MethodReference
   * @return the long value returned from the method invocation
   */
  private static long CallStaticLongMethod(JNIEnvironment env, int classJREF, int methodID) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallStaticLongMethod  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object returnObj = JNIHelpers.invokeWithDotDotVarArg(methodID, TypeReference.Long);
      return Reflection.unwrapLong(returnObj);     // should be a wrapper for a long value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0L;
    }
  }

  /**
   * CallStaticLongMethodV:  invoke a static method that returns a long value
   * @param env A JREF index for the JNI environment object
   * @param classJREF a JREF index for the class object
   * @param methodID id of a MethodReference
   * @param argAddress a raw address to a variable argument list, each element is 1-word or 2-words
   *                   of the appropriate type for the method invocation
   * @return the long value returned from the method invocation
   */
  private static long CallStaticLongMethodV(JNIEnvironment env, int classJREF, int methodID, Address argAddress)
      throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallStaticLongMethodV  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object returnObj = JNIHelpers.invokeWithVarArg(methodID, argAddress, TypeReference.Long);
      return Reflection.unwrapLong(returnObj);     // should be a wrapper for a long value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0L;
    }
  }

  /**
   * CallStaticLongMethodA:  invoke a static method that returns a long value
   * @param env A JREF index for the JNI environment object
   * @param classJREF a JREF index for the class object
   * @param methodID id of a MethodReference
   * @param argAddress a raw address to an array of unions in C, each element is 2-word and hold an argument
   *                   of the appropriate type for the method invocation
   * @return the long value returned from the method invocation
   */
  private static long CallStaticLongMethodA(JNIEnvironment env, int classJREF, int methodID, Address argAddress)
      throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallStaticLongMethodA  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object returnObj = JNIHelpers.invokeWithJValue(methodID, argAddress, TypeReference.Long);
      return Reflection.unwrapLong(returnObj);     // should be a wrapper for a long value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0L;
    }
  }

  /**
   * CallStaticFloagMethod:  invoke a static method that returns a float value
   *                         arguments passed using the vararg ... style
   * NOTE:  the vararg's are not visible in the method signature here; they are saved
   *        in the caller frame and the glue frame
   * @param env A JREF index for the JNI environment object
   * @param classJREF a JREF index for the class object
   * @param methodID id of a MethodReference
   * @return the float value returned from the method invocation
   */
  private static float CallStaticFloatMethod(JNIEnvironment env, int classJREF, int methodID) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallStaticFloatMethod  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object returnObj = JNIHelpers.invokeWithDotDotVarArg(methodID, TypeReference.Float);
      return Reflection.unwrapFloat(returnObj);     // should be a wrapper for a float value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0f;
    }
  }

  /**
   * CallStaticFloatMethodV:  invoke a static method that returns a float value
   * @param env A JREF index for the JNI environment object
   * @param classJREF a JREF index for the class object
   * @param methodID id of a MethodReference
   * @param argAddress a raw address to a variable argument list, each element is 1-word or 2-words
   *                   of the appropriate type for the method invocation
   * @return the float value returned from the method invocation
   */
  private static float CallStaticFloatMethodV(JNIEnvironment env, int classJREF, int methodID, Address argAddress)
      throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallStaticFloatMethodV  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object returnObj = JNIHelpers.invokeWithVarArg(methodID, argAddress, TypeReference.Float);
      return Reflection.unwrapFloat(returnObj);     // should be a wrapper for a float value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0f;
    }
  }

  /**
   * CallStaticFloatMethodA:  invoke a static method that returns a float value
   * @param env A JREF index for the JNI environment object
   * @param classJREF a JREF index for the class object
   * @param methodID id of a MethodReference
   * @param argAddress a raw address to an array of unions in C, each element is 2-word and hold an argument
   *                   of the appropriate type for the method invocation
   * @return the float value returned from the method invocation
   */
  private static float CallStaticFloatMethodA(JNIEnvironment env, int classJREF, int methodID, Address argAddress)
      throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallStaticFloatMethodA  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object returnObj = JNIHelpers.invokeWithJValue(methodID, argAddress, TypeReference.Float);
      return Reflection.unwrapFloat(returnObj);     // should be a wrapper for a float value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0f;
    }
  }

  /**
   * CallStaticDoubleMethod:  invoke a static method that returns a double value
   *                          arguments passed using the vararg ... style
   * NOTE:  the vararg's are not visible in the method signature here; they are saved
   *        in the caller frame and the glue frame
   * @param env A JREF index for the JNI environment object
   * @param classJREF a JREF index for the class object
   * @param methodID an id of a MethodReference
   * @return the double value returned from the method invocation
   */
  private static double CallStaticDoubleMethod(JNIEnvironment env, int classJREF, int methodID) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallStaticDoubleMethod  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object returnObj = JNIHelpers.invokeWithDotDotVarArg(methodID, TypeReference.Double);
      return Reflection.unwrapDouble(returnObj);     // should be a wrapper for a double value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallStaticDoubleMethodV:  invoke a static method that returns a double value
   * @param env A JREF index for the JNI environment object
   * @param classJREF a JREF index for the class object
   * @param methodID an id of a MethodReference
   * @param argAddress a raw address to a  variable argument list, each element is 1-word or 2-words
   *                   of the appropriate type for the method invocation
   * @return the double value returned from the method invocation
   */
  private static double CallStaticDoubleMethodV(JNIEnvironment env, int classJREF, int methodID, Address argAddress)
      throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallStaticDoubleMethodV  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object returnObj = JNIHelpers.invokeWithVarArg(methodID, argAddress, TypeReference.Double);
      return Reflection.unwrapDouble(returnObj);     // should be a wrapper for a double value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallStaticDoubleMethodA:  invoke a static method that returns a double value
   * @param env A JREF index for the JNI environment object
   * @param classJREF a JREF index for the class object
   * @param methodID id of a MethodReference
   * @param argAddress a raw address to an array of unions in C, each element is 2-word and hold an argument
   *                   of the appropriate type for the method invocation
   * @return the double value returned from the method invocation
   */
  private static double CallStaticDoubleMethodA(JNIEnvironment env, int classJREF, int methodID, Address argAddress)
      throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallStaticDoubleMethodA  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object returnObj = JNIHelpers.invokeWithJValue(methodID, argAddress, TypeReference.Double);
      return Reflection.unwrapDouble(returnObj);     // should be a wrapper for a double value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallStaticVoidMethod:  invoke a static method that returns void
   *                       arguments passed using the vararg ... style
   * NOTE:  the vararg's are not visible in the method signature here; they are saved
   *        in the caller frame and the glue frame
   * @param env A JREF index for the JNI environment object
   * @param classJREF a JREF index for the class object
   * @param methodID id of a MethodReference
   */
  private static void CallStaticVoidMethod(JNIEnvironment env, int classJREF, int methodID) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallStaticVoidMethod  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      JNIHelpers.invokeWithDotDotVarArg(methodID, TypeReference.Void);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * CallStaticVoidMethodA:  invoke a static method that returns void
   * @param env A JREF index for the JNI environment object
   * @param classJREF a JREF index for the class object
   * @param methodID id of a MethodReference
   * @param argAddress a raw address to an array of unions in C, each element is 2-word and hold an argument
   *                   of the appropriate type for the method invocation
   */
  private static void CallStaticVoidMethodV(JNIEnvironment env, int classJREF, int methodID, Address argAddress)
      throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallStaticVoidMethodV  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      JNIHelpers.invokeWithVarArg(methodID, argAddress, TypeReference.Void);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * CallStaticVoidMethodA:  invoke a static method that returns void
   * @param env A JREF index for the JNI environment object
   * @param classJREF a JREF index for the class object
   * @param methodID id of a MethodReference
   * @param argAddress a raw address to an array of unions in C, each element is 2-word and hold an argument
   *                   of the appropriate type for the method invocation
   */
  private static void CallStaticVoidMethodA(JNIEnvironment env, int classJREF, int methodID, Address argAddress)
      throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallStaticVoidMethodA  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      JNIHelpers.invokeWithJValue(methodID, argAddress, TypeReference.Void);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * GetStaticFieldID:  return a field id which can be cached in native code and reused
   * @param env A JREF index for the JNI environment object
   * @param classJREF a JREF index for the RVMClass object
   * @param fieldNameAddress a raw address to a null-terminated string in C for the field name
   * @param descriptorAddress a raw address to a null-terminated string in C for the descriptor
   * @return the offset of a static field given the class, field name
   *         and type. Return 0 if the field is not found
   * @exception NoSuchFieldError if the specified field cannot be found
   * @exception ExceptionInInitializerError if the class initializer fails
   * @exception OutOfMemoryError if the system runs out of memory
   */
  private static int GetStaticFieldID(JNIEnvironment env, int classJREF, Address fieldNameAddress,
                                      Address descriptorAddress) {
    if (traceJNI) VM.sysWrite("JNI called: GetStaticFieldID  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Class<?> cls = (Class<?>) env.getJNIRef(classJREF);

      String fieldString = JNIHelpers.createStringFromC(fieldNameAddress);
      Atom fieldName = Atom.findOrCreateAsciiAtom(fieldString);
      String descriptorString = JNIHelpers.createStringFromC(descriptorAddress);
      Atom descriptor = Atom.findOrCreateAsciiAtom(descriptorString);

      // list of all instance fields including superclasses
      RVMField[] fields = java.lang.JikesRVMSupport.getTypeForClass(cls).getStaticFields();
      for (RVMField field : fields) {
        if (field.getName() == fieldName && field.getDescriptor() == descriptor) {
          return field.getId();
        }
      }

      env.recordException(new NoSuchFieldError(fieldString + ", " + descriptorString + " of " + cls));
      return 0;
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * GetStaticObjectField: read a static field of type Object
   * @param env A JREF index for the JNI environment object
   * @param classJREF a JREF index for the RVMClass object
   * @param fieldID the id for the RVMField that describes this field
   * @return the value of the Object field, converted to a JREF index
   *         or 0 if the fieldID is incorrect
   */
  private static int GetStaticObjectField(JNIEnvironment env, int classJREF, int fieldID) {
    if (traceJNI) VM.sysWrite("JNI called: GetStaticObjectField  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      RVMField field = MemberReference.getMemberRef(fieldID).asFieldReference().resolve();
      Object value = field.getObjectUnchecked(null);
      return env.pushJNIRef(value);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * GetStaticBooleanField: read a static field of type boolean
   * @param env A JREF index for the JNI environment object
   * @param classJREF a JREF index for the RVMClass object
   * @param fieldID the id for the RVMField that describes this field
   * @return the value of the boolean field, or 0 if the fieldID is incorrect
   */
  private static int GetStaticBooleanField(JNIEnvironment env, int classJREF, int fieldID) {
    if (traceJNI) VM.sysWrite("JNI called: GetStaticBooleanField  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      RVMField field = MemberReference.getMemberRef(fieldID).asFieldReference().resolve();
      return field.getBooleanValueUnchecked(null) ? 1 : 0;
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * GetStaticByteField:  read a static field of type byte
   * @param env A JREF index for the JNI environment object
   * @param classJREF a JREF index for the RVMClass object
   * @param fieldID the id for the RVMField that describes this field
   * @return the value of the byte field, or 0 if the fieldID is incorrect
   */
  private static int GetStaticByteField(JNIEnvironment env, int classJREF, int fieldID) {
    if (traceJNI) VM.sysWrite("JNI called: GetStaticByteField  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      RVMField field = MemberReference.getMemberRef(fieldID).asFieldReference().resolve();
      return field.getByteValueUnchecked(null);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * GetStaticCharField:  read a static field of type character
   * @param env A JREF index for the JNI environment object
   * @param classJREF a JREF index for the RVMClass object
   * @param fieldID the id for the RVMField that describes this field
   * @return the value of the character field, or 0 if the fieldID is incorrect
   */
  private static int GetStaticCharField(JNIEnvironment env, int classJREF, int fieldID) {
    if (traceJNI) VM.sysWrite("JNI called: GetStaticCharField  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      RVMField field = MemberReference.getMemberRef(fieldID).asFieldReference().resolve();
      return field.getCharValueUnchecked(null);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * GetStaticShortField:  read a static field of type short
   * @param env A JREF index for the JNI environment object
   * @param classJREF a JREF index for the RVMClass object
   * @param fieldID the id for the RVMField that describes this field
   * @return the value of the short field, or 0 if the fieldID is incorrect
   */
  private static int GetStaticShortField(JNIEnvironment env, int classJREF, int fieldID) {
    if (traceJNI) VM.sysWrite("JNI called: GetStaticShortField  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      RVMField field = MemberReference.getMemberRef(fieldID).asFieldReference().resolve();
      return field.getShortValueUnchecked(null);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * GetStaticIntField:  read a static field of type integer
   * @param env A JREF index for the JNI environment object
   * @param classJREF a JREF index for the RVMClass object
   * @param fieldID the id for the RVMField that describes this field
   * @return the value of the integer field, or 0 if the fieldID is incorrect
   */
  private static int GetStaticIntField(JNIEnvironment env, int classJREF, int fieldID) {
    if (traceJNI) VM.sysWrite("JNI called: GetStaticIntField  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      RVMField field = MemberReference.getMemberRef(fieldID).asFieldReference().resolve();
      return field.getIntValueUnchecked(null);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * GetStaticLongField:  read a static field of type long
   * @param env A JREF index for the JNI environment object
   * @param classJREF a JREF index for the RVMClass object
   * @param fieldID the id for the RVMField that describes this field
   * @return the value of the long field or 0 if the fieldID is incorrect
   */
  private static long GetStaticLongField(JNIEnvironment env, int classJREF, int fieldID) {
    if (traceJNI) VM.sysWrite("JNI called: GetStaticLongField  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      RVMField field = MemberReference.getMemberRef(fieldID).asFieldReference().resolve();
      return field.getLongValueUnchecked(null);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * GetStaticFloatField:  read a static field of type float
   * @param env A JREF index for the JNI environment object
   * @param classJREF a JREF index for the RVMClass object
   * @param fieldID the id for the RVMField that describes this field
   * @return the value of the float field or 0 if the fieldID is incorrect
   */
  private static float GetStaticFloatField(JNIEnvironment env, int classJREF, int fieldID) {
    if (traceJNI) VM.sysWrite("JNI called: GetStaticFloatField  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      RVMField field = MemberReference.getMemberRef(fieldID).asFieldReference().resolve();
      return field.getFloatValueUnchecked(null);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * GetStaticDoubleField:  read a static field of type double
   * @param env A JREF index for the JNI environment object
   * @param classJREF a JREF index for the RVMClass object
   * @param fieldID the id for the RVMField that describes this field
   * @return the value of the double field or 0 if the fieldID is incorrect
   */
  private static double GetStaticDoubleField(JNIEnvironment env, int classJREF, int fieldID) {
    if (traceJNI) VM.sysWrite("JNI called: GetStaticDoubleField  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      RVMField field = MemberReference.getMemberRef(fieldID).asFieldReference().resolve();
      return field.getDoubleValueUnchecked(null);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * SetStaticObjectField:  set a static field of type Object
   * @param env         A JREF index for the JNI environment object
   * @param classJREF   A JREF index for the {@link RVMClass} object
   * @param fieldID     The id for the {@link RVMField} that describes this
   *                    field
   * @param objectJREF  A JREF index of the value to assign
   */
  private static void SetStaticObjectField(JNIEnvironment env, int classJREF, int fieldID, int objectJREF) {
    if (traceJNI) VM.sysWrite("JNI called: SetStaticObjectField  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      RVMField field = MemberReference.getMemberRef(fieldID).asFieldReference().resolve();
      Object ref = env.getJNIRef(objectJREF);
      field.setObjectValueUnchecked(null, ref);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * SetStaticBooleanField:  set a static field of type boolean
   * @param env A JREF index for the JNI environment object
   * @param classJREF A JREF index for the RVMClass object
   * @param fieldID the id for the RVMField that describes this field
   * @param fieldValue  The value to assign
   */
  private static void SetStaticBooleanField(JNIEnvironment env, int classJREF, int fieldID, boolean fieldValue) {
    if (traceJNI) VM.sysWrite("JNI called: SetStaticBooleanField  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      RVMField field = MemberReference.getMemberRef(fieldID).asFieldReference().resolve();
      field.setBooleanValueUnchecked(null, fieldValue);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * SetStaticByteField:  set a static field of type byte
   * @param env A JREF index for the JNI environment object
   * @param classJREF a JREF index for the RVMClass object
   * @param fieldID the id for the RVMField that describes this field
   * @param fieldValue the value to assign
   */
  private static void SetStaticByteField(JNIEnvironment env, int classJREF, int fieldID, byte fieldValue) {
    if (traceJNI) VM.sysWrite("JNI called: SetStaticByteField  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      RVMField field = MemberReference.getMemberRef(fieldID).asFieldReference().resolve();
      field.setByteValueUnchecked(null, fieldValue);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * SetStaticCharField:  set a static field of type char
   * @param env A JREF index for the JNI environment object
   * @param classJREF a JREF index for the RVMClass object
   * @param fieldID the id for the RVMField that describes this field
   * @param fieldValue  The value to assign
   */
  private static void SetStaticCharField(JNIEnvironment env, int classJREF, int fieldID, char fieldValue) {
    if (traceJNI) VM.sysWrite("JNI called: SetStaticCharField  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      RVMField field = MemberReference.getMemberRef(fieldID).asFieldReference().resolve();
      field.setCharValueUnchecked(null, fieldValue);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * SetStaticShortField:  set a static field of type short
   * @param env A JREF index for the JNI environment object
   * @param classJREF a JREF index for the RVMClass object
   * @param fieldID the id for the RVMField that describes this field
   * @param fieldValue  The value to assign
   */
  private static void SetStaticShortField(JNIEnvironment env, int classJREF, int fieldID, short fieldValue) {
    if (traceJNI) VM.sysWrite("JNI called: SetStaticShortField  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      RVMField field = MemberReference.getMemberRef(fieldID).asFieldReference().resolve();
      field.setShortValueUnchecked(null, fieldValue);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * SetStaticIntField:  set a static field of type integer
   * @param env A JREF index for the JNI environment object
   * @param classJREF a JREF index for the RVMClass object
   * @param fieldID the id for the RVMField that describes this field
   * @param fieldValue  The value to assign
   */
  private static void SetStaticIntField(JNIEnvironment env, int classJREF, int fieldID, int fieldValue) {
    if (traceJNI) VM.sysWrite("JNI called: SetStaticIntField  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      RVMField field = MemberReference.getMemberRef(fieldID).asFieldReference().resolve();
      field.setIntValueUnchecked(null, fieldValue);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * SetStaticLongField:  set a static field of type long
   * @param env A JREF index for the JNI environment object
   * @param classJREF a JREF index for the RVMClass object
   * @param fieldID the id for the RVMField that describes this field
   * @param fieldValue  The value to assign
   */
  private static void SetStaticLongField(JNIEnvironment env, int classJREF, int fieldID, long fieldValue) {
    if (traceJNI) VM.sysWrite("JNI called: SetStaticLongField  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      RVMField field = MemberReference.getMemberRef(fieldID).asFieldReference().resolve();
      field.setLongValueUnchecked(null, fieldValue);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * SetStaticFloatField:  set a static field of type float
   * @param env A JREF index for the JNI environment object
   * @param classJREF a JREF index for the RVMClass object
   * @param fieldID the id for the RVMField that describes this field
   * @param fieldValue  The value to assign
   */
  private static void SetStaticFloatField(JNIEnvironment env, int classJREF, int fieldID, float fieldValue) {
    if (traceJNI) VM.sysWrite("JNI called: SetStaticFloatField  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      RVMField field = MemberReference.getMemberRef(fieldID).asFieldReference().resolve();
      field.setFloatValueUnchecked(null, fieldValue);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * SetStaticDoubleField:  set a static field of type float
   * @param env A JREF index for the JNI environment object
   * @param classJREF a JREF index for the RVMClass object
   * @param fieldID the id for the RVMField that describes this field
   * @param fieldValue  The value to assign
   */
  private static void SetStaticDoubleField(JNIEnvironment env, int classJREF, int fieldID, double fieldValue) {
    if (traceJNI) VM.sysWrite("JNI called: SetStaticDoubleField  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      RVMField field = MemberReference.getMemberRef(fieldID).asFieldReference().resolve();
      field.setDoubleValueUnchecked(null, fieldValue);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * NewString: create a String Object from C array of unicode chars
   * @param env A JREF index for the JNI environment object
   * @param uchars address of C array of 16 bit unicode characters
   * @param len the number of chars in the C array
   * @return the allocated String Object, converted to a JREF index
   *         or 0 if an OutOfMemoryError Exception has been thrown
   * @exception OutOfMemoryError
   */
  private static int NewString(JNIEnvironment env, Address uchars, int len) {
    if (traceJNI) VM.sysWrite("JNI called: NewString  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      final char[] contents = new char[len];
      Memory.memcopy(Magic.objectAsAddress(contents), uchars, len * 2);
      return env.pushJNIRef(java.lang.JikesRVMSupport.newStringWithoutCopy(contents, 0, len));
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * GetStringLength:  return the length of a String
   * @param env A JREF index for the JNI environment object
   * @param objJREF a JREF index for the String object
   * @return the length of the String
   */
  private static int GetStringLength(JNIEnvironment env, int objJREF) {
    if (traceJNI) VM.sysWrite("JNI called: GetStringLength  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      String str = (String) env.getJNIRef(objJREF);
      return str.length();
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * GetStringChars:  return address of buffer containing contents of a String
   * @param env A JREF index for the JNI environment object
   * @param strJREF a JREF index for the String object
   * @param isCopyAddress address of isCopy jboolean (an int)
   * @return address of a copy of the String unicode characters
   *         and *isCopy is set to 1 (TRUE)
   * @exception OutOfMemoryError if the system runs out of memory
   */
  private static Address GetStringChars(JNIEnvironment env, int strJREF, Address isCopyAddress) {
    if (traceJNI) VM.sysWrite("JNI called: GetStringChars  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    String str = (String) env.getJNIRef(strJREF);
    char[] strChars = java.lang.JikesRVMSupport.getBackingCharArray(str);
    int strOffset = java.lang.JikesRVMSupport.getStringOffset(str);
    int len = java.lang.JikesRVMSupport.getStringLength(str);

    // alloc non moving buffer in C heap for a copy of string contents
    Address copyBuffer = sysCall.sysMalloc(len * 2);
    if (copyBuffer.isZero()) {
      env.recordException(new OutOfMemoryError());
      return Address.zero();
    }
    try {
      Address strBase = Magic.objectAsAddress(strChars);
      Address srcBase = strBase.plus(strOffset * 2);
      Memory.memcopy(copyBuffer, srcBase, len * 2);

      /* Set caller's isCopy boolean to true, if we got a valid (non-null)
         address */
      JNIGenericHelpers.setBoolStar(isCopyAddress, true);

      return copyBuffer;
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      sysCall.sysFree(copyBuffer);
      return Address.zero();
    }
  }

  /**
   * ReleaseStringChars:  release buffer obtained via GetStringChars
   * @param env A JREF index for the JNI environment object
   * @param objJREF a JREF index for the String object
   * @param bufAddress address of buffer to release
   */
  private static void ReleaseStringChars(JNIEnvironment env, int objJREF, Address bufAddress) {
    if (traceJNI) VM.sysWrite("JNI called: ReleaseStringChars  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      sysCall.sysFree(bufAddress);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * NewStringUTF: create a String Object from C array of utf8 bytes
   * @param env A JREF index for the JNI environment object
   * @param utf8bytes address of C array of 8 bit utf8 bytes
   * @return the allocated String Object, converted to a JREF index
   *         or 0 if an OutOfMemoryError Exception has been thrown
   * @exception OutOfMemoryError if the system runs out of memory
   */
  private static int NewStringUTF(JNIEnvironment env, Address utf8bytes) {
    if (traceJNI) VM.sysWrite("JNI called: NewStringUTF  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      String returnString = JNIHelpers.createUTFStringFromC(utf8bytes);
      return env.pushJNIRef(returnString);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * GetStringUTFLength: return number of bytes to represent a String in UTF8 format
   * @param env A JREF index for the JNI environment object
   * @param objJREF a JREF index for the String object
   * @return number of bytes to represent in UTF8 format
   */
  private static int GetStringUTFLength(JNIEnvironment env, int objJREF) {
    if (traceJNI) VM.sysWrite("JNI called: GetStringUTFLength  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      String str = (String) env.getJNIRef(objJREF);
      return UTF8Convert.utfLength(str);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * GetStringUTFChars:  return address of buffer containing contents of a String
   * @param env A JREF index for the JNI environment object
   * @param strJREF a JREF index for the String object
   * @param isCopyAddress address of isCopy jboolean (an int)
   * @return address of a copy of the String unicode characters
   *         and *isCopy is set to 1 (TRUE)
   * @exception OutOfMemoryError if the system runs out of memory
   */
  private static Address GetStringUTFChars(JNIEnvironment env, int strJREF, Address isCopyAddress) {
    if (traceJNI) VM.sysWrite("JNI called: GetStringUTFChars  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    String str = (String) env.getJNIRef(strJREF);

    // Get length of C string
    int len = UTF8Convert.utfLength(str) + 1; // for terminating zero

    // alloc non moving buffer in C heap for string
    Address copyBuffer = sysCall.sysMalloc(len);
    if (copyBuffer.isZero()) {
      env.recordException(new OutOfMemoryError());
      return Address.zero();
    }
    try {
      JNIHelpers.createUTFForCFromString(str, copyBuffer, len);
      JNIGenericHelpers.setBoolStar(isCopyAddress, true);
      return copyBuffer;
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return Address.zero();
    }
  }

  /**
   * ReleaseStringUTFChars:  release buffer obtained via GetStringUTFChars
   * @param env A JREF index for the JNI environment object
   * @param objJREF a JREF index for the String object
   * @param bufAddress address of buffer to release
   */
  private static void ReleaseStringUTFChars(JNIEnvironment env, int objJREF, Address bufAddress) {
    if (traceJNI) VM.sysWrite("JNI called: ReleaseStringUTFChars  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      sysCall.sysFree(bufAddress);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * GetArrayLength: return array length
   * @param env A JREF index for the JNI environment object
   * @param arrayJREF a JREF index for the source array
   * @return the array length, or -1 if it's not an array
   */
  private static int GetArrayLength(JNIEnvironment env, int arrayJREF) {
    if (traceJNI) VM.sysWrite("JNI called: GetArrayLength  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object theArray = env.getJNIRef(arrayJREF);
      RVMType arrayType = Magic.getObjectType(theArray);
      return arrayType.isArrayType() ? Magic.getArrayLength(theArray) : -1;
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return -1;
    }
  }

  /**
   * NewObjectArray: create a new Object array
   * @param env A JREF index for the JNI environment object
   * @param length the size of the new array
   * @param classJREF a JREF index for the class of the element
   * @param initElementJREF a JREF index for the value to initialize the array elements
   * @return the new Object array initialized
   * @exception OutOfMemoryError if the system runs out of memory
   */
  private static int NewObjectArray(JNIEnvironment env, int length, int classJREF, int initElementJREF) {
    if (traceJNI) VM.sysWrite("JNI called: NewObjectArray  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object initElement = env.getJNIRef(initElementJREF);
      Class<?> cls = (Class<?>) env.getJNIRef(classJREF);

      if (cls == null) {
        throw new NullPointerException();
      }
      if (length < 0) {
        throw new NegativeArraySizeException();
      }

      RVMArray arrayType = java.lang.JikesRVMSupport.getTypeForClass(cls).getArrayTypeForElementType();
      if (!arrayType.isInitialized()) {
        arrayType.resolve();
        arrayType.instantiate();
        arrayType.initialize();
      }
      Object[] newArray = (Object[]) RuntimeEntrypoints.resolvedNewArray(length, arrayType);

      if (initElement != null) {
        for (int i = 0; i < length; i++) {
          newArray[i] = initElement;
        }
      }

      return env.pushJNIRef(newArray);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * GetObjectArrayElement: retrieve an object from an object array
   * @param env A JREF index for the JNI environment object
   * @param arrayJREF a JREF index for the source array
   * @param index the index for the targeted element
   * @return the object at the specified index
   * @exception ArrayIndexOutOfBoundsException if the index is out of range
   */
  private static int GetObjectArrayElement(JNIEnvironment env, int arrayJREF, int index) {
    if (traceJNI) VM.sysWrite("JNI called: GetObjectArrayElement  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object[] sourceArray = (Object[]) env.getJNIRef(arrayJREF);

      if (sourceArray == null) {
        return 0;
      }

      RVMArray arrayType = Magic.getObjectType(sourceArray).asArray();
      RVMType elementType = arrayType.getElementType();
      if (elementType.isPrimitiveType() || elementType.isUnboxedType()) {
        return 0;
      }

      if (index >= Magic.getArrayLength(sourceArray)) {
        env.recordException(new ArrayIndexOutOfBoundsException());
        return 0;
      }

      return env.pushJNIRef(sourceArray[index]);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * SetObjectArrayElement: store an object into an object array
   * @param env A JREF index for the JNI environment object
   * @param arrayJREF a JREF index for the source array
   * @param index the index for the targeted element
   * @param objectJREF a JREF index for the object to store into the array
   * @exception ArrayStoreException if the element types do not match
   *            ArrayIndexOutOfBoundsException if the index is out of range
   */
  private static void SetObjectArrayElement(JNIEnvironment env, int arrayJREF, int index, int objectJREF) {
    if (traceJNI) VM.sysWrite("JNI called: SetObjectArrayElement  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object[] sourceArray = (Object[]) env.getJNIRef(arrayJREF);
      Object elem = env.getJNIRef(objectJREF);
      sourceArray[index] = elem;
    } catch (Throwable e) {
      env.recordException(e);
    }
  }

  /**
   * NewBooleanArray: create a new boolean array
   * @param env A JREF index for the JNI environment object
   * @param length the size of the new array
   * @return the new boolean array
   * @exception OutOfMemoryError if the system runs out of memory
   */
  private static int NewBooleanArray(JNIEnvironment env, int length) {
    if (traceJNI) VM.sysWrite("JNI called: NewBooleanArray  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      boolean[] newArray = new boolean[length];
      return env.pushJNIRef(newArray);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * NewByteArray: create a new byte array
   * @param env A JREF index for the JNI environment object
   * @param length the size of the new array
   * @return the new byte array
   * @exception OutOfMemoryError if the system runs out of memory
   */
  private static int NewByteArray(JNIEnvironment env, int length) {
    if (traceJNI) VM.sysWrite("JNI called: NewByteArray  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      byte[] newArray = new byte[length];
      return env.pushJNIRef(newArray);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * NewCharArray: create a new char array
   * @param env A JREF index for the JNI environment object
   * @param length the size of the new array
   * @return the new char array
   * @exception OutOfMemoryError if the system runs out of memory
   */
  private static int NewCharArray(JNIEnvironment env, int length) {
    if (traceJNI) VM.sysWrite("JNI called: NewCharArray  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      char[] newArray = new char[length];
      return env.pushJNIRef(newArray);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * NewShortArray: create a new short array
   * @param env A JREF index for the JNI environment object
   * @param length the size of the new array
   * @return the new short array
   * @exception OutOfMemoryError if the system runs out of memory
   */
  private static int NewShortArray(JNIEnvironment env, int length) {
    if (traceJNI) VM.sysWrite("JNI called: NewShortArray  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      short[] newArray = new short[length];
      return env.pushJNIRef(newArray);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * NewIntArray: create a new integer array
   * @param env A JREF index for the JNI environment object
   * @param length the size of the new array
   * @return the new integer array
   * @exception OutOfMemoryError if the system runs out of memory
   */
  private static int NewIntArray(JNIEnvironment env, int length) {
    if (traceJNI) VM.sysWrite("JNI called: NewIntArray  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      int[] newArray = new int[length];
      return env.pushJNIRef(newArray);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * NewLongArray: create a new long array
   * @param env A JREF index for the JNI environment object
   * @param length the size of the new array
   * @return the new long array
   * @exception OutOfMemoryError if the system runs out of memory
   */
  private static int NewLongArray(JNIEnvironment env, int length) {
    if (traceJNI) VM.sysWrite("JNI called: NewLongArray  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      long[] newArray = new long[length];
      return env.pushJNIRef(newArray);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * NewFloatArray: create a new float array
   * @param env A JREF index for the JNI environment object
   * @param length the size of the new array
   * @return the new float array
   * @exception OutOfMemoryError if the system runs out of memory
   */
  private static int NewFloatArray(JNIEnvironment env, int length) {
    if (traceJNI) VM.sysWrite("JNI called: NewFloatArray  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      float[] newArray = new float[length];
      return env.pushJNIRef(newArray);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * NewDoubleArray: create a new double array
   * @param env A JREF index for the JNI environment object
   * @param length the size of the new array
   * @return the new long array
   * @exception OutOfMemoryError if the system runs out of memory
   */
  private static int NewDoubleArray(JNIEnvironment env, int length) {
    if (traceJNI) VM.sysWrite("JNI called: NewDoubleArray  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      double[] newArray = new double[length];
      return env.pushJNIRef(newArray);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * GetBooleanArrayElements: get all the elements of a boolean array
   * @param env A JREF index for the JNI environment object
   * @param arrayJREF a JREF index for the source array
   * @param isCopyAddress address of a flag to indicate whether the returned array is a copy or a direct pointer
   * @return A pointer to the boolean array and the isCopy flag is set to true if it's a copy
   *         or false if it's a direct pointer
   * @exception OutOfMemoryError if the system runs out of memory
   */
  private static Address GetBooleanArrayElements(JNIEnvironment env, int arrayJREF, Address isCopyAddress) {
    if (traceJNI) VM.sysWrite("JNI called: GetBooleanArrayElements  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      boolean[] sourceArray = (boolean[]) env.getJNIRef(arrayJREF);
      int size = sourceArray.length;

      // alloc non moving buffer in C heap for a copy of string contents
      Address copyBuffer = sysCall.sysMalloc(size);
      if (copyBuffer.isZero()) {
        env.recordException(new OutOfMemoryError());
        return Address.zero();
      }

      Memory.memcopy(copyBuffer, Magic.objectAsAddress(sourceArray), size);

      /* Set caller's isCopy boolean to true, if we got a valid (non-null)
         address */
      JNIGenericHelpers.setBoolStar(isCopyAddress, true);

      return copyBuffer;
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return Address.zero();
    }
  }

  /**
   * GetByteArrayElements: get all the elements of a byte array
   * @param env A JREF index for the JNI environment object
   * @param arrayJREF a JREF index for the source array
   * @param isCopyAddress address of a flag to indicate whether the returned array is a copy or a direct pointer
   * @return A pointer to the byte array and the isCopy flag is set to true if it's a copy
   *         or false if it's a direct pointer
   * @exception OutOfMemoryError if the system runs out of memory
   */
  private static Address GetByteArrayElements(JNIEnvironment env, int arrayJREF, Address isCopyAddress) {
    if (traceJNI) VM.sysWrite("JNI called: GetByteArrayElements \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      byte[] sourceArray = (byte[]) env.getJNIRef(arrayJREF);
      int size = sourceArray.length;

      if (MemoryManager.willNeverMove(sourceArray)) {
        /* return a direct pointer */
        JNIGenericHelpers.setBoolStar(isCopyAddress, false);
        return Magic.objectAsAddress(sourceArray);
      } else {
        // alloc non moving buffer in C heap for a copy of string contents
        Address copyBuffer = sysCall.sysMalloc(size);

        if (copyBuffer.isZero()) {
          env.recordException(new OutOfMemoryError());
          return Address.zero();
        }

        Memory.memcopy(copyBuffer, Magic.objectAsAddress(sourceArray), size);

        /* Set caller's isCopy boolean to true, if we got a valid (non-null)
           address */
        JNIGenericHelpers.setBoolStar(isCopyAddress, true);

        return copyBuffer;
      }
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return Address.zero();
    }
  }

  /**
   * GetCharArrayElements: get all the elements of a char array
   * @param env A JREF index for the JNI environment object
   * @param arrayJREF a JREF index for the source array
   * @param isCopyAddress address of a flag to indicate whether the returned array is a copy or a direct pointer
   * @return A pointer to the char array and the isCopy flag is set to true if it's a copy
   *         or false if it's a direct pointer
   * @exception OutOfMemoryError if the system runs out of memory
   */
  private static Address GetCharArrayElements(JNIEnvironment env, int arrayJREF, Address isCopyAddress) {
    if (traceJNI) VM.sysWrite("JNI called: GetCharArrayElements  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      char[] sourceArray = (char[]) env.getJNIRef(arrayJREF);
      int size = sourceArray.length;

      if (MemoryManager.willNeverMove(sourceArray)) {
        JNIGenericHelpers.setBoolStar(isCopyAddress, false);
        return Magic.objectAsAddress(sourceArray);
      } else {
        // alloc non moving buffer in C heap for a copy of string contents
        Address copyBuffer = sysCall.sysMalloc(size * BYTES_IN_CHAR);
        if (copyBuffer.isZero()) {
          env.recordException(new OutOfMemoryError());
          return Address.zero();
        }

        Memory.memcopy(copyBuffer, Magic.objectAsAddress(sourceArray), size * BYTES_IN_CHAR);

        /* Set caller's isCopy boolean to true, if we got a valid (non-null)
         address */
        JNIGenericHelpers.setBoolStar(isCopyAddress, true);

        return copyBuffer;
      }
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return Address.zero();
    }
  }

  /**
   * GetShortArrayElements: get all the elements of a short array
   * @param env A JREF index for the JNI environment object
   * @param arrayJREF a JREF index for the source array
   * @param isCopyAddress address of a flag to indicate whether the returned array is a copy or a direct pointer
   * @return A pointer to the short array and the isCopy flag is set to true if it's a copy
   *         or false if it's a direct pointer
   * @exception OutOfMemoryError if the system runs out of memory
   */
  private static Address GetShortArrayElements(JNIEnvironment env, int arrayJREF, Address isCopyAddress) {
    if (traceJNI) VM.sysWrite("JNI called: GetShortArrayElements  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      short[] sourceArray = (short[]) env.getJNIRef(arrayJREF);
      int size = sourceArray.length;

      if (MemoryManager.willNeverMove(sourceArray)) {
        JNIGenericHelpers.setBoolStar(isCopyAddress, false);
        return Magic.objectAsAddress(sourceArray);
      } else {
        // alloc non moving buffer in C heap for a copy of string contents
        Address copyBuffer = sysCall.sysMalloc(size * BYTES_IN_SHORT);
        if (copyBuffer.isZero()) {
          env.recordException(new OutOfMemoryError());
          return Address.zero();
        }

        Memory.memcopy(copyBuffer, Magic.objectAsAddress(sourceArray), size * BYTES_IN_SHORT);

        /* Set caller's isCopy boolean to true, if we got a valid (non-null)
         address */
        JNIGenericHelpers.setBoolStar(isCopyAddress, true);

        return copyBuffer;
      }
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return Address.zero();
    }
  }

  /**
   * GetIntArrayElements: get all the elements of an integer array
   * @param env A JREF index for the JNI environment object
   * @param arrayJREF a JREF index for the source array
   * @param isCopyAddress address of a flag to indicate whether the returned array is a copy or a direct pointer
   * @return A pointer to the integer array and the isCopy flag is set to true if it's a copy
   *         or false if it's a direct pointer
   * @exception OutOfMemoryError if the system runs out of memory
   */
  private static Address GetIntArrayElements(JNIEnvironment env, int arrayJREF, Address isCopyAddress) {
    if (traceJNI) VM.sysWrite("JNI called: GetIntArrayElements  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      int[] sourceArray = (int[]) env.getJNIRef(arrayJREF);
      int size = sourceArray.length;

      if (MemoryManager.willNeverMove(sourceArray)) {
        JNIGenericHelpers.setBoolStar(isCopyAddress, false);
        return Magic.objectAsAddress(sourceArray);
      } else {
        // alloc non moving buffer in C heap for a copy of array contents
        Address copyBuffer = sysCall.sysMalloc(size << LOG_BYTES_IN_INT);
        if (copyBuffer.isZero()) {
          env.recordException(new OutOfMemoryError());
          return Address.zero();
        }
        Memory.memcopy(copyBuffer, Magic.objectAsAddress(sourceArray), size << LOG_BYTES_IN_INT);

        /* Set caller's isCopy boolean to true, if we got a valid (non-null)
         address */
        JNIGenericHelpers.setBoolStar(isCopyAddress, true);

        return copyBuffer;
      }
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return Address.zero();
    }
  }

  /**
   * GetLongArrayElements: get all the elements of a long array
   * @param env A JREF index for the JNI environment object
   * @param arrayJREF a JREF index for the source array
   * @param isCopyAddress address of a flag to indicate whether the returned array is a copy or a direct pointer
   * @return A pointer to the long array and the isCopy flag is set to true if it's a copy
   *         or false if it's a direct pointer
   * @exception OutOfMemoryError if the system runs out of memory
   */
  private static Address GetLongArrayElements(JNIEnvironment env, int arrayJREF, Address isCopyAddress) {
    if (traceJNI) VM.sysWrite("JNI called: GetLongArrayElements  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      long[] sourceArray = (long[]) env.getJNIRef(arrayJREF);
      int size = sourceArray.length;

      if (MemoryManager.willNeverMove(sourceArray)) {
        JNIGenericHelpers.setBoolStar(isCopyAddress, false);
        return Magic.objectAsAddress(sourceArray);
      } else {
        // alloc non moving buffer in C heap for a copy of string contents
        Address copyBuffer = sysCall.sysMalloc(size << LOG_BYTES_IN_LONG);
        if (copyBuffer.isZero()) {
          env.recordException(new OutOfMemoryError());
          return Address.zero();
        }
        Memory.memcopy(copyBuffer, Magic.objectAsAddress(sourceArray), size << LOG_BYTES_IN_LONG);

        /* Set caller's isCopy boolean to true, if we got a valid (non-null)
         address */
        JNIGenericHelpers.setBoolStar(isCopyAddress, true);

        return copyBuffer;
      }
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return Address.zero();
    }
  }

  /**
   * GetFloatArrayElements: get all the elements of a float array
   * @param env A JREF index for the JNI environment object
   * @param arrayJREF a JREF index for the source array
   * @param isCopyAddress address of a flag to indicate whether the returned array is a copy or a direct pointer
   * @return A pointer to the float array and the isCopy flag is set to true if it's a copy
   *         or false if it's a direct pointer
   * @exception OutOfMemoryError if the system runs out of memory
   */
  private static Address GetFloatArrayElements(JNIEnvironment env, int arrayJREF, Address isCopyAddress) {
    if (traceJNI) VM.sysWrite("JNI called: GetFloatArrayElements  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      float[] sourceArray = (float[]) env.getJNIRef(arrayJREF);
      int size = sourceArray.length;

      if (MemoryManager.willNeverMove(sourceArray)) {
        JNIGenericHelpers.setBoolStar(isCopyAddress, false);
        return Magic.objectAsAddress(sourceArray);
      } else {
        // alloc non moving buffer in C heap for a copy of string contents
        Address copyBuffer = sysCall.sysMalloc(size << LOG_BYTES_IN_FLOAT);
        if (copyBuffer.isZero()) {
          env.recordException(new OutOfMemoryError());
          return Address.zero();
        }

        Memory.memcopy(copyBuffer, Magic.objectAsAddress(sourceArray), size << LOG_BYTES_IN_FLOAT);

        /* Set caller's isCopy boolean to true, if we got a valid (non-null)
         address */
        JNIGenericHelpers.setBoolStar(isCopyAddress, true);

        return copyBuffer;
      }
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return Address.zero();
    }
  }

  /**
   * GetDoubleArrayElements: get all the elements of a double array
   * @param env A JREF index for the JNI environment object
   * @param arrayJREF a JREF index for the source array
   * @param isCopyAddress address of a flag to indicate whether the returned array is a copy or a direct pointer
   * @return A pointer to the double array and the isCopy flag is set to true if it's a copy
   *         or false if it's a direct pointer
   * @exception OutOfMemoryError if the system runs out of memory
   */
  private static Address GetDoubleArrayElements(JNIEnvironment env, int arrayJREF, Address isCopyAddress) {
    if (traceJNI) VM.sysWrite("JNI called: GetDoubleArrayElements  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      double[] sourceArray = (double[]) env.getJNIRef(arrayJREF);
      int size = sourceArray.length;

      if (MemoryManager.willNeverMove(sourceArray)) {
        JNIGenericHelpers.setBoolStar(isCopyAddress, false);
        return Magic.objectAsAddress(sourceArray);
      } else {
        // alloc non moving buffer in C heap for a copy of string contents
        Address copyBuffer = sysCall.sysMalloc(size << LOG_BYTES_IN_DOUBLE);
        if (copyBuffer.isZero()) {
          env.recordException(new OutOfMemoryError());
          return Address.zero();
        }
        Memory.memcopy(copyBuffer, Magic.objectAsAddress(sourceArray), size << LOG_BYTES_IN_DOUBLE);

        /* Set caller's isCopy boolean to true, if we got a valid (non-null)
         address */
        JNIGenericHelpers.setBoolStar(isCopyAddress, true);

        return copyBuffer;
      }
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return Address.zero();
    }
  }

  /**
   * ReleaseBooleanArrayElements: free the native copy of the array, update changes to Java array as indicated
   * @param env A JREF index for the JNI environment object
   * @param arrayJREF a JREF index for the source array
   * @param copyBufferAddress the address of the copy of the array
   * @param releaseMode one of 3 codes to indicate whether to copy back or free the array:
   *    releaseMode 0:  copy back and free the buffer
   *    releaseMode 1:  JNI_COMMIT, copy back but do not free the buffer
   *    releaseMode 2:  JNI_ABORT, free the buffer with copying back
   */
  private static void ReleaseBooleanArrayElements(JNIEnvironment env, int arrayJREF, Address copyBufferAddress,
                                                  int releaseMode) {
    if (traceJNI) VM.sysWrite("JNI called: ReleaseBooleanArrayElements  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      boolean[] sourceArray = (boolean[]) env.getJNIRef(arrayJREF);

      // If a direct pointer was given to the user, no need to update or release
      if (Magic.objectAsAddress(sourceArray).NE(copyBufferAddress)) {
        int size = sourceArray.length;

        // mode 0 and mode 1:  copy back the buffer
        if ((releaseMode == 0 || releaseMode == 1) && size != 0) {
          for (int i = 0; i < size; i += BYTES_IN_INT) {
            Address addr = copyBufferAddress.plus(i);
            int data = addr.loadInt();
            if (VM.LittleEndian) {
              if (i < size) sourceArray[i] = ((data) & 0x000000ff) != 0;
              if (i + 1 < size) sourceArray[i + 1] = ((data >>> BITS_IN_BYTE) & 0x000000ff) != 0;
              if (i + 2 < size) sourceArray[i + 2] = ((data >>> (2 * BITS_IN_BYTE)) & 0x000000ff) != 0;
              if (i + 3 < size) sourceArray[i + 3] = ((data >>> (3 * BITS_IN_BYTE)) & 0x000000ff) != 0;
            } else {
              if (i < size) sourceArray[i] = ((data >>> (3 * BITS_IN_BYTE)) & 0x000000ff) != 0;
              if (i + 1 < size) sourceArray[i + 1] = ((data >>> (2 * BITS_IN_BYTE)) & 0x000000ff) != 0;
              if (i + 2 < size) sourceArray[i + 2] = ((data >>> BITS_IN_BYTE) & 0x000000ff) != 0;
              if (i + 3 < size) sourceArray[i + 3] = ((data) & 0x000000ff) != 0;
            }
          }
        }

        // mode 0 and mode 2:  free the buffer
        if (releaseMode == 0 || releaseMode == 2) {
          sysCall.sysFree(copyBufferAddress);
        }
      }
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * ReleaseByteArrayElements: free the native copy of the array, update changes to Java array as indicated
   * @param env A JREF index for the JNI environment object
   * @param arrayJREF a JREF index for the source array
   * @param copyBufferAddress the address of the copy of the array
   * @param releaseMode one of 3 codes to indicate whether to copy back or free the array:
   *    releaseMode 0:  copy back and free the buffer
   *    releaseMode 1:  JNI_COMMIT, copy back but do not free the buffer
   *    releaseMode 2:  JNI_ABORT, free the buffer with copying back
   */
  private static void ReleaseByteArrayElements(JNIEnvironment env, int arrayJREF, Address copyBufferAddress,
                                               int releaseMode) {
    if (traceJNI) VM.sysWrite("JNI called: ReleaseByteArrayElements  releaseMode=", releaseMode);
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      byte[] sourceArray = (byte[]) env.getJNIRef(arrayJREF);

      // If a direct pointer was given to the user, no need to update or release
      if (Magic.objectAsAddress(sourceArray).NE(copyBufferAddress)) {
        int size = sourceArray.length;
        if (traceJNI) VM.sysWrite(" size=", size);

        // mode 0 and mode 1:  copy back the buffer
        if ((releaseMode == 0 || releaseMode == 1) && size != 0) {
          Memory.memcopy(Magic.objectAsAddress(sourceArray), copyBufferAddress, size);
        }

        // mode 0 and mode 2:  free the buffer
        if (releaseMode == 0 || releaseMode == 2) {
          sysCall.sysFree(copyBufferAddress);
        }
      } else {
        // Nothing to be done
      }
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
    if (traceJNI) VM.sysWrite("\n");
  }

  /**
   * ReleaseCharArrayElements: free the native copy of the array, update changes to Java array as indicated
   * @param env A JREF index for the JNI environment object
   * @param arrayJREF a JREF index for the source array
   * @param copyBufferAddress the address of the copy of the array
   * @param releaseMode one of 3 codes to indicate whether to copy back or free the array:
   *    releaseMode 0:  copy back and free the buffer
   *    releaseMode 1:  JNI_COMMIT, copy back but do not free the buffer
   *    releaseMode 2:  JNI_ABORT, free the buffer with copying back
   */
  private static void ReleaseCharArrayElements(JNIEnvironment env, int arrayJREF, Address copyBufferAddress,
                                               int releaseMode) {
    if (traceJNI) VM.sysWrite("JNI called: ReleaseCharArrayElements \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      char[] sourceArray = (char[]) env.getJNIRef(arrayJREF);

      // If a direct pointer was given to the user, no need to update or release
      if (Magic.objectAsAddress(sourceArray).NE(copyBufferAddress)) {
        int size = sourceArray.length;

        // mode 0 and mode 1:  copy back the buffer
        if ((releaseMode == 0 || releaseMode == 1) && size != 0) {
          Memory.memcopy(Magic.objectAsAddress(sourceArray), copyBufferAddress, size << LOG_BYTES_IN_CHAR);
        }

        // mode 0 and mode 2:  free the buffer
        if (releaseMode == 0 || releaseMode == 2) {
          sysCall.sysFree(copyBufferAddress);
        }
      }
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * ReleaseShortArrayElements: free the native copy of the array, update changes to Java array as indicated
   * @param env A JREF index for the JNI environment object
   * @param arrayJREF a JREF index for the source array
   * @param copyBufferAddress the address of the copy of the array
   * @param releaseMode one of 3 codes to indicate whether to copy back or free the array:
   *    releaseMode 0:  copy back and free the buffer
   *    releaseMode 1:  JNI_COMMIT, copy back but do not free the buffer
   *    releaseMode 2:  JNI_ABORT, free the buffer with copying back
   */
  private static void ReleaseShortArrayElements(JNIEnvironment env, int arrayJREF, Address copyBufferAddress,
                                                int releaseMode) {
    if (traceJNI) VM.sysWrite("JNI called: ReleaseShortArrayElements  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      short[] sourceArray = (short[]) env.getJNIRef(arrayJREF);

      // If a direct pointer was given to the user, no need to update or release
      if (Magic.objectAsAddress(sourceArray).NE(copyBufferAddress)) {
        int size = sourceArray.length;

        // mode 0 and mode 1:  copy back the buffer
        if ((releaseMode == 0 || releaseMode == 1) && size != 0) {
          Memory.memcopy(Magic.objectAsAddress(sourceArray), copyBufferAddress, size << LOG_BYTES_IN_SHORT);
        }

        // mode 0 and mode 2:  free the buffer
        if (releaseMode == 0 || releaseMode == 2) {
          sysCall.sysFree(copyBufferAddress);
        }
      }
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * ReleaseIntArrayElements: free the native copy of the array, update changes to Java array as indicated
   * @param env A JREF index for the JNI environment object
   * @param arrayJREF a JREF index for the source array
   * @param copyBufferAddress the address of the copy of the array
   * @param releaseMode one of 3 codes to indicate whether to copy back or free the array:
   *    releaseMode 0:  copy back and free the buffer
   *    releaseMode 1:  JNI_COMMIT, copy back but do not free the buffer
   *    releaseMode 2:  JNI_ABORT, free the buffer with copying back
   */
  private static void ReleaseIntArrayElements(JNIEnvironment env, int arrayJREF, Address copyBufferAddress,
                                              int releaseMode) {
    if (traceJNI) VM.sysWrite("JNI called: ReleaseIntArrayElements  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      int[] sourceArray = (int[]) env.getJNIRef(arrayJREF);

      // If a direct pointer was given to the user, no need to update or release
      if (Magic.objectAsAddress(sourceArray).NE(copyBufferAddress)) {
        int size = sourceArray.length;

        // mode 0 and mode 1:  copy back the buffer
        if (releaseMode == 0 || releaseMode == 1) {
          Memory.memcopy(Magic.objectAsAddress(sourceArray), copyBufferAddress, size << LOG_BYTES_IN_INT);
        }

        // mode 0 and mode 2:  free the buffer
        if (releaseMode == 0 || releaseMode == 2) {
          sysCall.sysFree(copyBufferAddress);
        }
      }
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * ReleaseLongArrayElements: free the native copy of the array, update changes to Java array as indicated
   * @param env A JREF index for the JNI environment object
   * @param arrayJREF a JREF index for the source array
   * @param copyBufferAddress the address of the copy of the array
   * @param releaseMode one of 3 codes to indicate whether to copy back or free the array:
   *    releaseMode 0:  copy back and free the buffer
   *    releaseMode 1:  JNI_COMMIT, copy back but do not free the buffer
   *    releaseMode 2:  JNI_ABORT, free the buffer with copying back
   */
  private static void ReleaseLongArrayElements(JNIEnvironment env, int arrayJREF, Address copyBufferAddress,
                                               int releaseMode) {
    if (traceJNI) VM.sysWrite("JNI called: ReleaseLongArrayElements  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      long[] sourceArray = (long[]) env.getJNIRef(arrayJREF);

      // If a direct pointer was given to the user, no need to update or release
      if (Magic.objectAsAddress(sourceArray).NE(copyBufferAddress)) {
        int size = sourceArray.length;

        // mode 0 and mode 1:  copy back the buffer
        if (releaseMode == 0 || releaseMode == 1) {
          Memory.memcopy(Magic.objectAsAddress(sourceArray), copyBufferAddress, size << LOG_BYTES_IN_LONG);
        }

        // mode 0 and mode 2:  free the buffer
        if (releaseMode == 0 || releaseMode == 2) {
          sysCall.sysFree(copyBufferAddress);
        }
      }
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * ReleaseFloatArrayElements: free the native copy of the array, update changes to Java array as indicated
   * @param env A JREF index for the JNI environment object
   * @param arrayJREF a JREF index for the source array
   * @param copyBufferAddress the address of the copy of the array
   * @param releaseMode one of 3 codes to indicate whether to copy back or free the array:
   *    releaseMode 0:  copy back and free the buffer
   *    releaseMode 1:  JNI_COMMIT, copy back but do not free the buffer
   *    releaseMode 2:  JNI_ABORT, free the buffer with copying back
   */
  private static void ReleaseFloatArrayElements(JNIEnvironment env, int arrayJREF, Address copyBufferAddress,
                                                int releaseMode) {
    if (traceJNI) VM.sysWrite("JNI called: ReleaseFloatArrayElements  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      float[] sourceArray = (float[]) env.getJNIRef(arrayJREF);

      // If a direct pointer was given to the user, no need to update or release
      if (Magic.objectAsAddress(sourceArray).NE(copyBufferAddress)) {
        int size = sourceArray.length;

        // mode 0 and mode 1:  copy back the buffer
        if (releaseMode == 0 || releaseMode == 1) {
          Memory.memcopy(Magic.objectAsAddress(sourceArray), copyBufferAddress, size << LOG_BYTES_IN_FLOAT);
        }

        // mode 0 and mode 2:  free the buffer
        if (releaseMode == 0 || releaseMode == 2) {
          sysCall.sysFree(copyBufferAddress);
        }
      }
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * ReleaseDoubleArrayElements: free the native copy of the array, update changes to Java array as indicated
   * @param env A JREF index for the JNI environment object
   * @param arrayJREF a JREF index for the source array
   * @param copyBufferAddress the address of the copy of the array
   * @param releaseMode one of 3 codes to indicate whether to copy back or free the array:
   *    releaseMode 0:  copy back and free the buffer
   *    releaseMode 1:  JNI_COMMIT, copy back but do not free the buffer
   *    releaseMode 2:  JNI_ABORT, free the buffer with copying back
   */
  private static void ReleaseDoubleArrayElements(JNIEnvironment env, int arrayJREF, Address copyBufferAddress,
                                                 int releaseMode) {
    if (traceJNI) VM.sysWrite("JNI called: ReleaseDoubleArrayElements  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      double[] sourceArray = (double[]) env.getJNIRef(arrayJREF);

      // If a direct pointer was given to the user, no need to update or release
      if (Magic.objectAsAddress(sourceArray).NE(copyBufferAddress)) {
        int size = sourceArray.length;

        // mode 0 and mode 1:  copy back the buffer
        if (releaseMode == 0 || releaseMode == 1) {
          Memory.memcopy(Magic.objectAsAddress(sourceArray), copyBufferAddress, size << LOG_BYTES_IN_DOUBLE);
        }

        // mode 0 and mode 2:  free the buffer
        if (releaseMode == 0 || releaseMode == 2) {
          sysCall.sysFree(copyBufferAddress);
        }
      }
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * GetBooleanArrayRegion: copy a region of the array into the native buffer
   * @param env A JREF index for the JNI environment object
   * @param arrayJREF a JREF index for the source array
   * @param startIndex the starting index to copy
   * @param length the number of elements to copy
   * @param bufAddress the destination address in native to copy to
   * @exception ArrayIndexOutOfBoundsException if one of the indices in the region is not valid
   */
  private static void GetBooleanArrayRegion(JNIEnvironment env, int arrayJREF, int startIndex, int length,
                                            Address bufAddress) {
    if (traceJNI) VM.sysWrite("JNI called: GetBooleanArrayRegion  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      boolean[] sourceArray = (boolean[]) env.getJNIRef(arrayJREF);

      if ((startIndex < 0) || (startIndex + length > sourceArray.length)) {
        env.recordException(new ArrayIndexOutOfBoundsException());
        return;
      }
      Memory.memcopy(bufAddress, Magic.objectAsAddress(sourceArray).plus(startIndex), length);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * GetByteArrayRegion: copy a region of the array into the native buffer
   * @param env A JREF index for the JNI environment object
   * @param arrayJREF a JREF index for the source array
   * @param startIndex the starting index to copy
   * @param length the number of elements to copy
   * @param bufAddress the destination address in native to copy to
   * @exception ArrayIndexOutOfBoundsException if one of the indices in the region is not valid
   */
  private static void GetByteArrayRegion(JNIEnvironment env, int arrayJREF, int startIndex, int length,
                                         Address bufAddress) {
    if (traceJNI) VM.sysWrite("JNI called: GetByteArrayRegion  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      byte[] sourceArray = (byte[]) env.getJNIRef(arrayJREF);

      if ((startIndex < 0) || (startIndex + length > sourceArray.length)) {
        env.recordException(new ArrayIndexOutOfBoundsException());
        return;
      }

      Memory.memcopy(bufAddress, Magic.objectAsAddress(sourceArray).plus(startIndex), length);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * GetCharArrayRegion: copy a region of the array into the native buffer
   * @param env A JREF index for the JNI environment object
   * @param arrayJREF a JREF index for the source array
   * @param startIndex the starting index to copy
   * @param length the number of elements to copy
   * @param bufAddress the destination address in native to copy to
   * @exception ArrayIndexOutOfBoundsException if one of the indices in the region is not valid
   */
  private static void GetCharArrayRegion(JNIEnvironment env, int arrayJREF, int startIndex, int length,
                                         Address bufAddress) {
    if (traceJNI) VM.sysWrite("JNI called: GetCharArrayRegion  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      char[] sourceArray = (char[]) env.getJNIRef(arrayJREF);

      if ((startIndex < 0) || (startIndex + length > sourceArray.length)) {
        env.recordException(new ArrayIndexOutOfBoundsException());
        return;
      }

      Memory.memcopy(bufAddress,
                        Magic.objectAsAddress(sourceArray).plus(startIndex << LOG_BYTES_IN_CHAR),
                        length << LOG_BYTES_IN_CHAR);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * GetShortArrayRegion: copy a region of the array into the native buffer
   * @param env A JREF index for the JNI environment object
   * @param arrayJREF a JREF index for the source array
   * @param startIndex the starting index to copy
   * @param length the number of elements to copy
   * @param bufAddress the destination address in native to copy to
   * @exception ArrayIndexOutOfBoundsException if one of the indices in the region is not valid
   */
  private static void GetShortArrayRegion(JNIEnvironment env, int arrayJREF, int startIndex, int length,
                                          Address bufAddress) {
    if (traceJNI) VM.sysWrite("JNI called: GetShortArrayRegion  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      short[] sourceArray = (short[]) env.getJNIRef(arrayJREF);

      if ((startIndex < 0) || (startIndex + length > sourceArray.length)) {
        env.recordException(new ArrayIndexOutOfBoundsException());
        return;
      }

      Memory.memcopy(bufAddress,
                        Magic.objectAsAddress(sourceArray).plus(startIndex << LOG_BYTES_IN_SHORT),
                        length << LOG_BYTES_IN_SHORT);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * GetIntArrayRegion: copy a region of the array into the native buffer
   * @param env A JREF index for the JNI environment object
   * @param arrayJREF a JREF index for the source array
   * @param startIndex the starting index to copy
   * @param length the number of elements to copy
   * @param bufAddress the destination address in native to copy to
   * @exception ArrayIndexOutOfBoundsException if one of the indices in the region is not valid
   */
  private static void GetIntArrayRegion(JNIEnvironment env, int arrayJREF, int startIndex, int length,
                                        Address bufAddress) {
    if (traceJNI) VM.sysWrite("JNI called: GetIntArrayRegion  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      int[] sourceArray = (int[]) env.getJNIRef(arrayJREF);

      if ((startIndex < 0) || (startIndex + length > sourceArray.length)) {
        env.recordException(new ArrayIndexOutOfBoundsException());
        return;
      }

      Memory.memcopy(bufAddress,
                        Magic.objectAsAddress(sourceArray).plus(startIndex << LOG_BYTES_IN_INT),
                        length << LOG_BYTES_IN_INT);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * GetLongArrayRegion: copy a region of the array into the native buffer
   * @param env A JREF index for the JNI environment object
   * @param arrayJREF a JREF index for the source array
   * @param startIndex the starting index to copy
   * @param length the number of elements to copy
   * @param bufAddress the destination address in native to copy to
   * @exception ArrayIndexOutOfBoundsException if one of the indices in the region is not valid
   */
  private static void GetLongArrayRegion(JNIEnvironment env, int arrayJREF, int startIndex, int length,
                                         Address bufAddress) {
    if (traceJNI) VM.sysWrite("JNI called: GetLongArrayRegion   \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      long[] sourceArray = (long[]) env.getJNIRef(arrayJREF);

      if ((startIndex < 0) || (startIndex + length > sourceArray.length)) {
        env.recordException(new ArrayIndexOutOfBoundsException());
        return;
      }

      Memory.memcopy(bufAddress,
                        Magic.objectAsAddress(sourceArray).plus(startIndex << LOG_BYTES_IN_LONG),
                        length << LOG_BYTES_IN_LONG);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * GetFloatArrayRegion: copy a region of the array into the native buffer
   * @param env A JREF index for the JNI environment object
   * @param arrayJREF a JREF index for the source array
   * @param startIndex the starting index to copy
   * @param length the number of elements to copy
   * @param bufAddress the destination address in native to copy to
   * @exception ArrayIndexOutOfBoundsException if one of the indices in the region is not valid
   */
  private static void GetFloatArrayRegion(JNIEnvironment env, int arrayJREF, int startIndex, int length,
                                          Address bufAddress) {
    if (traceJNI) VM.sysWrite("JNI called: GetFloatArrayRegion    \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      float[] sourceArray = (float[]) env.getJNIRef(arrayJREF);

      if ((startIndex < 0) || (startIndex + length > sourceArray.length)) {
        env.recordException(new ArrayIndexOutOfBoundsException());
        return;
      }

      Memory.memcopy(bufAddress,
                        Magic.objectAsAddress(sourceArray).plus(startIndex << LOG_BYTES_IN_FLOAT),
                        length << LOG_BYTES_IN_FLOAT);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * GetDoubleArrayRegion: copy a region of the array into the native buffer
   * @param env A JREF index for the JNI environment object
   * @param arrayJREF a JREF index for the source array
   * @param startIndex the starting index to copy
   * @param length the number of elements to copy
   * @param bufAddress the destination address in native to copy to
   * @exception ArrayIndexOutOfBoundsException if one of the indices in the region is not valid
   */
  private static void GetDoubleArrayRegion(JNIEnvironment env, int arrayJREF, int startIndex, int length,
                                           Address bufAddress) {
    if (traceJNI) VM.sysWrite("JNI called: GetDoubleArrayRegion   \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      double[] sourceArray = (double[]) env.getJNIRef(arrayJREF);

      if ((startIndex < 0) || (startIndex + length > sourceArray.length)) {
        env.recordException(new ArrayIndexOutOfBoundsException());
        return;
      }

      Memory.memcopy(bufAddress,
                        Magic.objectAsAddress(sourceArray).plus(startIndex << LOG_BYTES_IN_DOUBLE),
                        length << LOG_BYTES_IN_DOUBLE);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * SetBooleanArrayRegion: copy a region of the native buffer into the array (1 byte element)
   * @param env A JREF index for the JNI environment object
   * @param arrayJREF a JREF index for the destination array
   * @param startIndex the starting index to copy
   * @param length the number of elements to copy
   * @param bufAddress the source address in native to copy from
   * @exception ArrayIndexOutOfBoundsException if one of the indices in the region is not valid
   */
  private static void SetBooleanArrayRegion(JNIEnvironment env, int arrayJREF, int startIndex, int length,
                                            Address bufAddress) {
    if (traceJNI) VM.sysWrite("JNI called: SetBooleanArrayRegion  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      boolean[] destinationArray = (boolean[]) env.getJNIRef(arrayJREF);

      if ((startIndex < 0) || (startIndex + length > destinationArray.length)) {
        env.recordException(new ArrayIndexOutOfBoundsException());
        return;
      }

      Memory.memcopy(Magic.objectAsAddress(destinationArray).plus(startIndex), bufAddress, length);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * SetByteArrayRegion: copy a region of the native buffer into the array (1 byte element)
   * @param env A JREF index for the JNI environment object
   * @param arrayJREF a JREF index for the destination array
   * @param startIndex the starting index to copy
   * @param length the number of elements to copy
   * @param bufAddress the source address in native to copy from
   * @exception ArrayIndexOutOfBoundsException if one of the indices in the region is not valid
   */
  private static void SetByteArrayRegion(JNIEnvironment env, int arrayJREF, int startIndex, int length,
                                         Address bufAddress) {
    if (traceJNI) VM.sysWrite("JNI called: SetByteArrayRegion  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      byte[] destinationArray = (byte[]) env.getJNIRef(arrayJREF);

      if ((startIndex < 0) || (startIndex + length > destinationArray.length)) {
        env.recordException(new ArrayIndexOutOfBoundsException());
        return;
      }

      Memory.memcopy(Magic.objectAsAddress(destinationArray).plus(startIndex), bufAddress, length);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * SetCharArrayRegion: copy a region of the native buffer into the array (2 byte element)
   * @param env A JREF index for the JNI environment object
   * @param arrayJREF a JREF index for the destination array
   * @param startIndex the starting index to copy
   * @param length the number of elements to copy
   * @param bufAddress the source address in native to copy from
   * @exception ArrayIndexOutOfBoundsException if one of the indices in the region is not valid
   */
  private static void SetCharArrayRegion(JNIEnvironment env, int arrayJREF, int startIndex, int length,
                                         Address bufAddress) {
    if (traceJNI) VM.sysWrite("JNI called: SetCharArrayRegion  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      char[] destinationArray = (char[]) env.getJNIRef(arrayJREF);

      if ((startIndex < 0) || (startIndex + length > destinationArray.length)) {
        env.recordException(new ArrayIndexOutOfBoundsException());
        return;
      }

      Memory.memcopy(Magic.objectAsAddress(destinationArray).plus(startIndex << LOG_BYTES_IN_CHAR),
                        bufAddress,
                        length << LOG_BYTES_IN_CHAR);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * SetShortArrayRegion: copy a region of the native buffer into the array (2 byte element)
   * @param env A JREF index for the JNI environment object
   * @param arrayJREF a JREF index for the destination array
   * @param startIndex the starting index to copy
   * @param length the number of elements to copy
   * @param bufAddress the source address in native to copy from
   * @exception ArrayIndexOutOfBoundsException if one of the indices in the region is not valid
   */
  private static void SetShortArrayRegion(JNIEnvironment env, int arrayJREF, int startIndex, int length,
                                          Address bufAddress) {
    if (traceJNI) VM.sysWrite("JNI called: SetShortArrayRegion  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      short[] destinationArray = (short[]) env.getJNIRef(arrayJREF);

      if ((startIndex < 0) || (startIndex + length > destinationArray.length)) {
        env.recordException(new ArrayIndexOutOfBoundsException());
        return;
      }

      Memory.memcopy(Magic.objectAsAddress(destinationArray).plus(startIndex << LOG_BYTES_IN_SHORT),
                        bufAddress,
                        length << LOG_BYTES_IN_SHORT);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * SetIntArrayRegion: copy a region of the native buffer into the array
   * @param env A JREF index for the JNI environment object
   * @param arrayJREF a JREF index for the destination array
   * @param startIndex the starting index to copy
   * @param length the number of elements to copy
   * @param bufAddress the source address in native to copy from
   * @exception ArrayIndexOutOfBoundsException if one of the indices in the region is not valid
   */
  private static void SetIntArrayRegion(JNIEnvironment env, int arrayJREF, int startIndex, int length,
                                        Address bufAddress) {
    if (traceJNI) VM.sysWrite("JNI called: SetIntArrayRegion  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      int[] destinationArray = (int[]) env.getJNIRef(arrayJREF);

      if ((startIndex < 0) || (startIndex + length > destinationArray.length)) {
        env.recordException(new ArrayIndexOutOfBoundsException());
        return;
      }

      Memory.memcopy(Magic.objectAsAddress(destinationArray).plus(startIndex << LOG_BYTES_IN_INT),
                        bufAddress,
                        length << LOG_BYTES_IN_INT);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * SetLongArrayRegion: copy a region of the native buffer into the array
   * @param env A JREF index for the JNI environment object
   * @param arrayJREF a JREF index for the destination array
   * @param startIndex the starting index to copy
   * @param length the number of elements to copy
   * @param bufAddress the source address in native to copy from
   * @exception ArrayIndexOutOfBoundsException if one of the indices in the region is not valid
   */
  private static void SetLongArrayRegion(JNIEnvironment env, int arrayJREF, int startIndex, int length,
                                         Address bufAddress) {
    if (traceJNI) VM.sysWrite("JNI called: SetLongArrayRegion  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      long[] destinationArray = (long[]) env.getJNIRef(arrayJREF);

      if ((startIndex < 0) || (startIndex + length > destinationArray.length)) {
        env.recordException(new ArrayIndexOutOfBoundsException());
        return;
      }

      Memory.memcopy(Magic.objectAsAddress(destinationArray).plus(startIndex << LOG_BYTES_IN_LONG),
                        bufAddress,
                        length << LOG_BYTES_IN_LONG);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * SetFloatArrayRegion: copy a region of the native buffer into the array
   * @param env A JREF index for the JNI environment object
   * @param arrayJREF a JREF index for the destination array
   * @param startIndex the starting index to copy
   * @param length the number of elements to copy
   * @param bufAddress the source address in native to copy from
   * @exception ArrayIndexOutOfBoundsException if one of the indices in the region is not valid
   */
  private static void SetFloatArrayRegion(JNIEnvironment env, int arrayJREF, int startIndex, int length,
                                          Address bufAddress) {
    if (traceJNI) VM.sysWrite("JNI called: SetFloatArrayRegion  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      float[] destinationArray = (float[]) env.getJNIRef(arrayJREF);

      if ((startIndex < 0) || (startIndex + length > destinationArray.length)) {
        env.recordException(new ArrayIndexOutOfBoundsException());
        return;
      }

      Memory.memcopy(Magic.objectAsAddress(destinationArray).plus(startIndex << LOG_BYTES_IN_FLOAT),
                        bufAddress,
                        length << LOG_BYTES_IN_FLOAT);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * SetDoubleArrayRegion: copy a region of the native buffer into the array
   * @param env A JREF index for the JNI environment object
   * @param arrayJREF a JREF index for the destination array
   * @param startIndex the starting index to copy
   * @param length the number of elements to copy
   * @param bufAddress the source address in native to copy from
   * @exception ArrayIndexOutOfBoundsException if one of the indices in the region is not valid
   */
  private static void SetDoubleArrayRegion(JNIEnvironment env, int arrayJREF, int startIndex, int length,
                                           Address bufAddress) {
    if (traceJNI) VM.sysWrite("JNI called: SetDoubleArrayRegion  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      double[] destinationArray = (double[]) env.getJNIRef(arrayJREF);

      if ((startIndex < 0) || (startIndex + length > destinationArray.length)) {
        env.recordException(new ArrayIndexOutOfBoundsException());
        return;
      }

      Memory.memcopy(Magic.objectAsAddress(destinationArray).plus(startIndex << LOG_BYTES_IN_DOUBLE),
                        bufAddress,
                        length << LOG_BYTES_IN_DOUBLE);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * RegisterNatives: registers implementation of native methods
   * @param env A JREF index for the JNI environment object
   * @param classJREF a JREF index for the class to register native methods in
   * @param methodsAddress the address of an array of native methods to be registered
   * @param nmethods the number of native methods in the array
   * @return 0 is successful -1 if failed
   * @exception NoSuchMethodError if a specified method cannot be found or is not native
   */
  private static int RegisterNatives(JNIEnvironment env, int classJREF, Address methodsAddress, int nmethods) {
    if (traceJNI) VM.sysWrite("JNI called: RegisterNatives  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      // get the target class
      Class<?> jcls = (Class<?>) env.getJNIRef(classJREF);
      RVMType type = java.lang.JikesRVMSupport.getTypeForClass(jcls);
      if (!type.isClassType()) {
        env.recordException(new NoSuchMethodError());
        return 0;
      }

      RVMClass klass = type.asClass();
      if (!klass.isInitialized()) {
        RuntimeEntrypoints.initializeClassForDynamicLink(klass);
      }

      // Create list of methods and verify them to avoid partial success
      NativeMethod[] methods = new NativeMethod[nmethods];
      AddressArray symbols = AddressArray.create(nmethods);

      Address curMethod = methodsAddress;
      for (int i = 0; i < nmethods; i++) {
        String methodString = JNIHelpers.createStringFromC(curMethod.loadAddress());
        Atom methodName = Atom.findOrCreateAsciiAtom(methodString);
        String sigString =
            JNIHelpers.createStringFromC(curMethod.loadAddress(Offset.fromIntSignExtend(BYTES_IN_ADDRESS)));
        Atom sigName = Atom.findOrCreateAsciiAtom(sigString);

        // Find the target method
        RVMMethod meth = klass.findDeclaredMethod(methodName, sigName);

        if (meth == null || !meth.isNative()) {
          env.recordException(new NoSuchMethodError(klass + ": " + methodName + " " + sigName));
          return -1;
        }
        methods[i] = (NativeMethod) meth;
        symbols.set(i, curMethod.loadAddress(Offset.fromIntSignExtend(BYTES_IN_ADDRESS * 2)));
        curMethod = curMethod.plus(3 * BYTES_IN_ADDRESS);
      }

      // Register methods
      for (int i = 0; i < nmethods; i++) {
        methods[i].registerNativeSymbol(symbols.get(i));
      }

      return 0;
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return -1;
    }
  }

  /**
   * UnregisterNatives: unregisters native methods
   * @param env A JREF index for the JNI environment object
   * @param classJREF a JREF index for the class to register native methods in
   * @return 0 is successful -1 if failed
   */
  private static int UnregisterNatives(JNIEnvironment env, int classJREF) {
    if (traceJNI) VM.sysWrite("JNI called: UnregisterNatives  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {

      // get the target class
      Class<?> jcls = (Class<?>) env.getJNIRef(classJREF);
      RVMType type = java.lang.JikesRVMSupport.getTypeForClass(jcls);
      if (!type.isClassType()) {
        env.recordException(new NoClassDefFoundError());
        return -1;
      }

      RVMClass klass = type.asClass();
      if (!klass.isInitialized()) {
        return 0;
      }

      klass.unregisterNativeMethods();
      return 0;
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return -1;
    }
  }

  /**
   * MonitorEnter
   * @param env A JREF index for the JNI environment object
   * @param objJREF a JREF index for the object to lock
   * @return 0 if the object is locked successfully, -1 if not
   */
  private static int MonitorEnter(JNIEnvironment env, int objJREF) {
    if (traceJNI) VM.sysWrite("JNI called: MonitorEnter  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object obj = env.getJNIRef(objJREF);
      ObjectModel.genericLock(obj);
      return 0;
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      return -1;
    }
  }

  /**
   * MonitorExit
   * @param env A JREF index for the JNI environment object
   * @param objJREF a JREF index for the object to unlock
   * @return 0 if the object is unlocked successfully, -1 if not
   */
  private static int MonitorExit(JNIEnvironment env, int objJREF) {
    if (traceJNI) VM.sysWrite("JNI called: MonitorExit  \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object obj = env.getJNIRef(objJREF);
      ObjectModel.genericUnlock(obj);
      return 0;
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      return -1;
    }
  }

  private static int GetJavaVM(JNIEnvironment env, Address StarStarJavaVM) {
    if (traceJNI) VM.sysWrite("JNI called: GetJavaVM \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      if (traceJNI) VM.sysWriteln(StarStarJavaVM);
      Address JavaVM = BootRecord.the_boot_record.sysJavaVM;
      StarStarJavaVM.store(JavaVM);

      return 0;
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      return -1;
    }
  }

  /*******************************************************************
   * These functions were added in Java 2  (JNI 1.2)
   */

  /**
   * FromReflectedMethod
   * @param env A JREF index for the JNI environment object
   * @param methodJREF a JREF index for the java.lang.reflect.Method or
   * java.lang.reflect.Constructor object.
   * @return the jmethodID corresponding to methodJREF
   */
  private static int FromReflectedMethod(JNIEnvironment env, int methodJREF) {
    if (traceJNI) VM.sysWrite("JNI called: FromReflectedMethod \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    Object methodObj = env.getJNIRef(methodJREF);
    RVMMethod meth;
    if (methodObj instanceof Constructor) {
      meth = java.lang.reflect.JikesRVMSupport.getMethodOf((Constructor<?>) methodObj);
    } else {
      meth = java.lang.reflect.JikesRVMSupport.getMethodOf((Method) methodObj);
    }

    if (traceJNI) VM.sysWrite("got method " + meth + "\n");
    return meth.getId();
  }

  /**
   * FromReflectedField
   * @param env A JREF index for the JNI environment object
   * @param fieldJREF a JREF index for a java.lang.reflect.Field methodID
   * @return the jfieldID corresponding to fieldJREF
   * */
  private static int FromReflectedField(JNIEnvironment env, int fieldJREF) {
    if (traceJNI) VM.sysWrite("JNI called: FromReflectedField \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    Field fieldObj = (Field) env.getJNIRef(fieldJREF);
    RVMField f = java.lang.reflect.JikesRVMSupport.getFieldOf(fieldObj);
    if (traceJNI) VM.sysWrite("got field " + f + "\n");
    return f.getId();
  }

  /**
   * ToReflectedMethod
   * @param env A JREF index for the JNI environment object
   * @param clsJREF The JREF index of the class from which methodID was
   * derived.
   * @param methodID a jmethodID to turn into a reflected method
   * @param isStatic argument that is not specified in Sun's JNI 1.2 spec,
   *            but IS present in the 1.4.2 JDK's implementation!  Our
   *            implementation will just ignore it, in any case.  This is a
   *            good example of why the same entity
   *            shouldn't get to write both the spec and the reference
   *            implementation.
   * @return a JREF index for the java.lang.reflect.Method or
   * java.lang.reflect.Constructor object associated with methodID.
   */
  private static int ToReflectedMethod(JNIEnvironment env, int clsJREF, int methodID, boolean isStatic) {
    if (traceJNI) VM.sysWrite("JNI called: ToReflectedMethod \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    RVMMethod targetMethod = MemberReference.getMemberRef(methodID).asMethodReference().resolve();
    Object ret;
    if (targetMethod.isObjectInitializer()) {
      ret = java.lang.reflect.JikesRVMSupport.createConstructor(targetMethod);
    } else {
      ret = java.lang.reflect.JikesRVMSupport.createMethod(targetMethod);
    }
    return env.pushJNIRef(ret);
  }

  /**
   * ToReflectedField
   * @param env A JREF index for the JNI environment object
   * @param clsJREF The JREF index of the class from which fieldID was
   * derived.
   * @param fieldID a jfieldID
   * @param isStatic argument that is not specified in Sun's JNI 1.2 spec,
   *            but IS present in the 1.4.2 JDK's implementation!  Our
   *            implementation will just ignore it, in any case.  This is a
   *            good example of why the same entity
   *            shouldn't get to write both the spec and the reference
   *            implementation.
   * @return a JREF index for the java.lang.reflect.Field object associated
   *         with fieldID.
   */
  private static int ToReflectedField(JNIEnvironment env, int clsJREF, int fieldID, boolean isStatic) {
    if (traceJNI) VM.sysWrite("JNI called: ToReflectedField \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    RVMField field = MemberReference.getMemberRef(fieldID).asFieldReference().resolve();
    return env.pushJNIRef(java.lang.reflect.JikesRVMSupport.createField(field));
  }

  /** Push a local frame for local references.
   * We could implement this more fancily, but it seems that we hardly need
   * to, since we allow an unlimited number of local refs.  One could force
   * running out of memory in a long-running loop in JNI, of course.
   */
  private static int PushLocalFrame(JNIEnvironment env, int capacity) {
    if (traceJNI) VM.sysWrite("JNI called: PushLocalFrame \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    return 0;                   // OK
  }

  /** Push a local frame for local references.
   * We could implement this more fancily, but it seems that we hardly need
   * to, since we allow an unlimited number of local refs.  One could force
   * running out of memory in a long-running loop in JNI, of course, and this
   * might save us from that.  Let's hold off until we need it.  TODO.
   *
   * @return a local reference in the old frame that refers to the same object
   *   as oldJREF.
   */
  private static int PopLocalFrame(JNIEnvironment env, int resultJREF) {
    if (traceJNI) VM.sysWrite("JNI called: PopLocalFrame \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    // do nothing.
    return resultJREF;
  }

  /**
   * NewLocalRef
   *
   * @param env A JREF index for the JNI environment object
   * @param oldJREF JREF index of an existing reference.
   * @return a new local reference that refers to the same object as oldJREF.
   *       C NULL pointer if the oldJREF refers to null.
   */
  private static int NewLocalRef(JNIEnvironment env, int oldJREF) {
    if (traceJNI) VM.sysWrite("JNI called: NewLocalRef \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    Object oldObj = env.getJNIRef(oldJREF);
    /* pushJNIRef automatically handles null refs properly. */
    return env.pushJNIRef(oldObj);
  }

  /**
   * EnsureLocalCapacity
   *
   * @param env A JREF index for the JNI environment object
   * @param capacity how many more local references do we want to ensure can
   * be created?
   * @return 0 on success.  The JNI spec says that on failure this throws
   * OutOfMemoryError and returns a negative number.  But we don't have to
   * worry about that at all.
   */
  private static int EnsureLocalCapacity(JNIEnvironment env, int capacity) {
    if (traceJNI) VM.sysWrite("JNI called: EnsureLocalCapacity \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    return 0;                   // success!
  }

  /** GetStringRegion:  Copy a region of Unicode characters from a string to
   *  the given buffer.
   *
   *  @param env A JREF index for the JNI environment object
   *  @param strJREF a JREF index for the String object
   *  @param start index to start reading characters from the string
   *  @param len how many characters to read
   *  @param buf the buffer to copy the region into
   *  @exception StringIndexOutOfBoundsException if asked for an out-of-range
   *        region of the string.
   */
  private static void GetStringRegion(JNIEnvironment env, int strJREF, int start, int len, Address buf) {
    if (traceJNI) VM.sysWrite("JNI called: GetStringRegion \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      String str = (String) env.getJNIRef(strJREF);
      char[] strChars = java.lang.JikesRVMSupport.getBackingCharArray(str);
      int strOffset = java.lang.JikesRVMSupport.getStringOffset(str);
      int strLen = java.lang.JikesRVMSupport.getStringLength(str);
      if (strLen < start + len) {
        env.recordException(new StringIndexOutOfBoundsException());
        return;
      }
      Address strBase = Magic.objectAsAddress(strChars);
      Address srcBase = strBase.plus(strOffset * 2).plus(start * 2);
      Memory.memcopy(buf, srcBase, len * 2);

    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /** GetStringUTFRegion:  Copy a region of Unicode characters from a string to
   *  the given buffer, as UTF8 characters.
   *
   *  @param env A JREF index for the JNI environment object
   *  @param strJREF a JREF index for the String object
   *  @param start index to start reading characters from the string
   *  @param len how many characters to read from the string
   *  @param buf the buffer to copy the region into -- assume it's big enough
   *  @exception StringIndexOutOfBoundsException if asked for an out-of-range
   *        region of the string.
   */
  private static void GetStringUTFRegion(JNIEnvironment env, int strJREF, int start, int len, Address buf) {
    if (traceJNI) VM.sysWrite("JNI called: GetStringUTFRegion \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      String str = (String) env.getJNIRef(strJREF);
      String region = str.substring(start, start+len);
      // Get length of C string
      int utflen = UTF8Convert.utfLength(region) + 1; // for terminating zero
      JNIHelpers.createUTFForCFromString(region, buf, utflen);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * GetPrimitiveArrayCritical: return a direct pointer to the primitive array
   * and disable GC so that the array will not be moved.  This function
   * is intended to be paired with the ReleasePrimitiveArrayCritical function
   * within a short time so that GC will be reenabled
   *
   * @param env A JREF index for the JNI environment object
   * @param arrayJREF a JREF index for the primitive array in Java
   * @param isCopyAddress address of isCopy jboolean (an int)
   * @return The address of the primitive array, and the jboolean pointed to by isCopyAddress is set to false, indicating that this is not a copy.   Address zero (null) on error.
   * @exception OutOfMemoryError is specified but will not be thrown in this implementation
   *            since no copy will be made
   */
  private static Address GetPrimitiveArrayCritical(JNIEnvironment env, int arrayJREF, Address isCopyAddress) {

    if (traceJNI) VM.sysWrite("JNI called: GetPrimitiveArrayCritical \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object primitiveArray = env.getJNIRef(arrayJREF);

      // not an array, return null
      if (!primitiveArray.getClass().isArray()) {
        return Address.zero();
      }

      /* Set caller's isCopy boolean to false, if we got a valid (non-null)
         address */
      JNIGenericHelpers.setBoolStar(isCopyAddress, false);

      // For array of primitive, return the object address, which is the array itself
      VM.disableGC(true);
      return Magic.objectAsAddress(primitiveArray);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return Address.zero();
    }
  }

  /**
   * ReleasePrimitiveArrayCritical: this function is intended to be paired
   * with the GetPrimitiveArrayCritical function.
   * Since the native code has direct access
   * to the array, no copyback update is necessary;  GC is simply reenabled.
   * @param env A JREF index for the JNI environment object
   * @param arrayJREF a JREF index for the primitive array in Java
   * @param arrayCopyAddress
   * @param mode a flag indicating whether to update the Java array with the
   *            copy and whether to free the copy. For this implementation,
   *            no copy was made so this flag has no effect.
   */
  private static void ReleasePrimitiveArrayCritical(JNIEnvironment env, int arrayJREF, Address arrayCopyAddress,
                                                    int mode) {
    if (traceJNI) VM.sysWrite("JNI called: ReleasePrimitiveArrayCritical \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      VM.enableGC(true);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /** GetStringCritical
   * Like GetStringChars and ReleaseStringChars, but in some VM environments
   * the VM may be able to avoid making a copy.   Native code must not issue
   * arbitrary JNI calls and must not cause the current thread to block.
   *
   * @param env A JREF index for the JNI environment object
   * @param strJREF a JREF index for the string in Java
   * @param isCopyAddress address of isCopy jboolean (an int)
   * @return The address of the backing array; address zero (null) on error, and the jboolean pointed to by isCopyAddress is set to false, indicating that this is not a copy.
   */
  private static Address GetStringCritical(JNIEnvironment env, int strJREF, Address isCopyAddress) {
    if (traceJNI) VM.sysWrite("JNI called: GetStringCritical \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    String str = (String) env.getJNIRef(strJREF);
    char[] strChars = java.lang.JikesRVMSupport.getBackingCharArray(str);
    int strOffset = java.lang.JikesRVMSupport.getStringOffset(str);

    /* Set caller's isCopy boolean to false, if we got a valid (non-null)
       address */
    JNIGenericHelpers.setBoolStar(isCopyAddress, false);

    VM.disableGC(true);
    Address strBase = Magic.objectAsAddress(strChars);
    return strBase.plus(strOffset * 2);
  }

  /**
   * ReleaseStringCritical: this function is intended to be paired with the
   * GetStringCritical function.  Since the native code has direct access
   * to the string's backing array of characters, no copyback update is
   * necessary;  GC is simply reenabled.
   *
   * @param env A JREF index for the JNI environment object
   * @param strJREF a JREF index for the string in Java (ignored)
   * @param carray the pointer returned by GetStringCritical (ignored)
   */
  private static void ReleaseStringCritical(JNIEnvironment env, int strJREF, Address carray) {
    if (traceJNI) VM.sysWrite("JNI called: ReleaseStringCritical \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      VM.enableGC(true);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  private static int NewWeakGlobalRef(JNIEnvironment env, int objectJREF) {
    if (traceJNI) VM.sysWrite("JNI called: NewWeakGlobalRef \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Object obj1 = env.getJNIRef(objectJREF);
      return JNIGlobalRefTable.newWeakRef(obj1);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  private static void DeleteWeakGlobalRef(JNIEnvironment env, int refJREF) {
    if (traceJNI) VM.sysWrite("JNI called: DeleteWeakGlobalRef \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      JNIGlobalRefTable.deleteWeakRef(refJREF);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  private static int ExceptionCheck(JNIEnvironment env) {
    if (traceJNI) VM.sysWrite("JNI called: ExceptionCheck \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    return env.getException() == null ? 0 : 1;
  }

  /*******************************************************************
   * These functions are in JNI 1.4
   */

  private static int NewDirectByteBuffer(JNIEnvironment env, Address address, long capacity) {
    if (traceJNI) VM.sysWrite("JNI called: NewDirectByteBuffer \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Buffer buffer = java.nio.JikesRVMSupport.newDirectByteBuffer(address, capacity);
      return env.pushJNIRef(buffer);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  private static Address GetDirectBufferAddress(JNIEnvironment env, int bufJREF) {
    if (traceJNI) VM.sysWrite("JNI called: GetDirectBufferAddress \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Buffer buffer = (Buffer) env.getJNIRef(bufJREF);
      //if (buffer instanceof ByteBuffer) {
      //  VM.sysWrite("ByteBuffer, ");
      //  if (((ByteBuffer)buffer).isDirect())
      //    VM.sysWrite("Direct, ");
      //}
      //VM.sysWriteln("Direct buffer address = ",result);
      return java.nio.JikesRVMSupport.getDirectBufferAddress(buffer);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return Address.zero();
    }
  }

  private static long GetDirectBufferCapacity(JNIEnvironment env, int bufJREF) {
    if (traceJNI) VM.sysWrite("JNI called: GetDirectBufferCapacity \n");
    RuntimeEntrypoints.checkJNICountDownToGC();

    try {
      Buffer buffer = (Buffer) env.getJNIRef(bufJREF);
      return buffer.capacity();
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return -1;
    }
  }

  /*******************************************************************
   * Empty Slots
   */

  private static int reserved0(JNIEnvironment env) {
    VM.sysWrite("JNI ERROR: reserved function slot not implemented, exiting ...\n");
    VM.sysExit(VM.EXIT_STATUS_UNSUPPORTED_INTERNAL_OP);
    return -1;
  }

  private static int reserved1(JNIEnvironment env) {
    VM.sysWrite("JNI ERROR: reserved function slot not implemented, exiting ...\n");
    VM.sysExit(VM.EXIT_STATUS_UNSUPPORTED_INTERNAL_OP);
    return -1;
  }

  private static int reserved2(JNIEnvironment env) {
    VM.sysWrite("JNI ERROR: reserved function slot not implemented, exiting ...\n");
    VM.sysExit(VM.EXIT_STATUS_UNSUPPORTED_INTERNAL_OP);
    return -1;
  }

  private static int reserved3(JNIEnvironment env) {
    VM.sysWrite("JNI ERROR: reserved function slot not implemented, exiting ...\n");
    VM.sysExit(VM.EXIT_STATUS_UNSUPPORTED_INTERNAL_OP);
    return -1;
  }
}
