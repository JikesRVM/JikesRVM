/*
 * (C) Copyright IBM Corp 2001,2002
 */
//$Id$
package com.ibm.JikesRVM.jni;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.memoryManagers.vmInterface.MM_Interface;
import com.ibm.JikesRVM.classloader.*;

import java.io.UTFDataFormatException;
import java.lang.reflect.*;

/**
 * This class implements the 229 JNI functions.
 * All methods here will be specially compiled with the necessary prolog to
 * perform the transition from native code (Linux/AIX/OSX convention) to RVM.
 * For this reason, no Java methods (including the JNI methods here) can call 
 * any methods in this class from within Java.  These JNI methods are to 
 * be invoked from native C or C++. <p>
 *
 * The first argument for all the functions is the VM_JNIEnvironment object 
 * of the thread. <p>
 * 
 * The second argument is a JREF index for either the VM_Class object
 * or the object instance itself.  To get the actual object, we use 
 * the access method in VM_JNIEnvironment and cast the reference as
 * needed. <p>
 * 
 * NOTE:  
 * (1) JREF index refers to the index into the side table of references
 * maintained by the VM_JNIEnvironment for each thread. Except for some cases
 * of array access, no references are passed directly to the native code; 
 * rather, the references are kept in the table and the index is passed to the 
 * native procedure.  The JREF index are used by the JNI code to retrieve the 
 * corresponding reference. <p>
 *
 * (2) Strings from C are seen as raw address (int) and need to be cloned as 
 * Java Strings <p>
 *
 * (3) Because of many of the transformation above, the method signature of the 
 * JNI functions may not match its definition in the jni.h file <p>
 *
 * (4) For exception handling, all JNI functions are wrapped in Try/Catch block
 * to catch all exception generated during JNI call, then these exceptions
 * or the appropriate exception to be thrown according to the spec is recorded
 * in VM_JNIEnvironment.pendingException.  When the native code returns to the
 * the Java caller, the epilogue in the glue code will check for the pending
 * exception and deliver it to the caller as if executing an athrow bytecode
 * in the caller. <p>
 * 
 * @author Ton Ngo 
 * @author Steve Smith  
 * @date 2/1/00
 */
class VM_JNIFunctions implements VM_NativeBridge, 
				 VM_SizeConstants {
  // one message for each JNI function called from native
  final static boolean traceJNI = false;

  // number of JNI function entries
  public static final int FUNCTIONCOUNT = 229;    

  /**
   * GetVersion: the version of the JNI
   * @param a JREF index for the JNI environment object
   * @return 0x00010002 for Java 1.2, otherwise return 0x00010001
   */     
  private static int GetVersion(VM_JNIEnvironment env) {
    if (traceJNI) VM.sysWrite("JNI called: GetVersion  \n");

    return 0x00010001;
  }


  private static int DefineClass(VM_JNIEnvironment env) {
    if (traceJNI) VM.sysWrite("JNI called: DefineClass  \n");

    VM.sysWrite("JNI ERROR: DefineClass not implemented yet.");
    VM.sysExit(VM.exitStatusUnsupportedInternalOp);
    return -1;
  }


  /**
   * FindClass:  given a class name, find its VM_Class, or 0 if not found
   * @param envJREF a JREF index for the JNI environment object
   * @param classNameAddress a raw address to a null-terminated string in C for the class name
   * @return a JREF index for the Java Class object, or 0 if not found
   * @exception ClassFormatError (not implemented)
   * @exception ClassCircularityError (not implemented)
   * @exception NoClassDefFoundError if the class cannot be found
   * @exception OutOfMemoryError (not implemented)
   * @exception ExceptionInInitializerError (not implemented)
   */
  private static int FindClass(VM_JNIEnvironment env, VM_Address classNameAddress) {
    if (traceJNI) VM.sysWrite("JNI called: FindClass  \n");

    String classString = null;
    try {
      classString = VM_JNIHelpers.createStringFromC(classNameAddress);
      if (traceJNI) VM.sysWriteln( classString );
      ClassLoader cl = VM_Class.getClassLoaderFromStackFrame(1);
      Class matchedClass = Class.forName(classString.replace('/', '.'), true, cl);
      return env.pushJNIRef(matchedClass);  
    } catch (ClassNotFoundException e) {
      if (traceJNI) e.printStackTrace(System.err);
      env.recordException(new NoClassDefFoundError(classString));
      return 0;
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * GetSuperclass: find the superclass given a class
   * @param envJREF a JREF index for the JNI environment object
   * @param classJREF a JREF index for the class object
   * @return a JREF index for the super class object, or 0 if the given class is
   *         java.lang.Object or an interface
   */
  private static int GetSuperclass(VM_JNIEnvironment env, int classJREF) {
    if (traceJNI) VM.sysWrite("JNI called: GetSuperclass  \n");

    try {
      Class cls = (Class) env.getJNIRef(classJREF); 
      Class supercls = cls.getSuperclass();
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
   * @param envJREF a JREF index for the JNI environment object
   * @param firstClassJREF a JREF index for the first class object
   * @param secondClassJREF a JREF index for the second class object
   * @return true if cls1 can be assigned to cls2
   */
  private static boolean IsAssignableFrom(VM_JNIEnvironment env, int firstClassJREF, 
                                          int secondClassJREF) {
    if (traceJNI) VM.sysWrite("JNI called: IsAssignableFrom  \n");

    try {
      Class cls1 = (Class) env.getJNIRef(firstClassJREF);
      Class cls2 = (Class) env.getJNIRef(secondClassJREF);
      if (cls1==null || cls2==null)
        return false;
      return cls2.isAssignableFrom(cls1);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return false;
    }
  }

  /**
   * Throw:  register a Throwable object as a pending exception, to be delivered 
   *         on return to the Java caller
   * @param a JREF index for the JNI environment object
   * @param a JREF index for the Throwable object to be thrown
   * @return 0 if successful, -1 if not
   */
  private static int Throw(VM_JNIEnvironment env, int exceptionJREF) {
    if (traceJNI) VM.sysWrite("JNI called: Throw  \n");

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
   * @param envJREF a JREF index for the JNI environment object
   * @param throwableClassJREF a JREF index for the class object of the exception
   * @param exceptionNameAddress an address of the string in C
   * @return 0 if successful, -1 otherwise
   */
  private static int ThrowNew(VM_JNIEnvironment env, int throwableClassJREF, VM_Address exceptionNameAddress) {
    if (traceJNI) VM.sysWrite("JNI called: ThrowNew  \n");

    try {
      Class cls = (Class) env.getJNIRef(throwableClassJREF);
      // find the constructor that has a string as a parameter
      Class[] argClasses = new Class[1];
      argClasses[0] = VM_Type.JavaLangStringType.getClassForType();
      Constructor constMethod = cls.getConstructor(argClasses);
      // prepare the parameter list for reflective invocation
      Object[] argObjs = new Object[1];
      argObjs[0] = VM_JNIHelpers.createStringFromC(exceptionNameAddress);

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
   * @param envJREF a JREF index for the JNI environment object
   * @return a JREF index for the pending exception or null if nothing pending
   *
   */
  private static int ExceptionOccurred(VM_JNIEnvironment env) {
    if (traceJNI) VM.sysWrite("JNI called: ExceptionOccurred  \n");

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
   * @param envJREF a JREF index for the JNI environment object
   */
  private static void ExceptionDescribe(VM_JNIEnvironment env) {
    if (traceJNI) VM.sysWrite("JNI called: ExceptionDescribe  \n");

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
   * @param envJREF a JREF index for the JNI environment object
   */
  private static void ExceptionClear(VM_JNIEnvironment env) {
    if (traceJNI) VM.sysWrite("JNI called: ExceptionClear  \n");

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
   * @param envJREF a JREF index for the JNI environment object
   * @param messageAddress an address of the string in C
   * @return This function does not return
   */
  private static void FatalError(VM_JNIEnvironment env, VM_Address messageAddress) {
    if (traceJNI) VM.sysWrite("JNI called: FatalError  \n");

    try {
      VM.sysWrite(VM_JNIHelpers.createStringFromC(messageAddress));
      System.exit(VM.exitStatusJNITrouble);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      System.exit(VM.exitStatusRecursivelyShuttingDown);
    }
  }


  private static int NewGlobalRef(VM_JNIEnvironment env, int objectJREF) {
    if (traceJNI) VM.sysWrite("JNI called: NewGlobalRef\n");

    try {
      Object obj1 = (Object) env.getJNIRef(objectJREF);
      return VM_JNIGlobalRefTable.newGlobalRef(obj1);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  private static void DeleteGlobalRef(VM_JNIEnvironment env, int refJREF) {
    if (traceJNI) VM.sysWrite("JNI called: DeleteGlobalRef\n");

    try {
      VM_JNIGlobalRefTable.deleteGlobalRef(refJREF);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  private static void DeleteLocalRef(VM_JNIEnvironment env, int objJREF) {
    if (traceJNI) VM.sysWrite("JNI called: DeleteLocalRef\n");

    try {
      env.deleteJNIRef(objJREF);
    } catch (ArrayIndexOutOfBoundsException e) {
      VM.sysWrite("JNI refs array confused.  Fatal Error!");
      VM.sysExit(VM.exitStatusJNITrouble );
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * IsSameObject: determine if two references point to the same object
   * @param a JREF index for the JNI environment object
   * @param a JREF index for the first object
   * @param a JREF index for the second object
   * @return true if it's the same object, false otherwise
   */
  private static boolean IsSameObject(VM_JNIEnvironment env, int obj1JREF, int obj2JREF) {
    if (traceJNI) VM.sysWrite("JNI called: IsSameObject  \n");

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
   * @param envJREF a JREF index for the JNI environment object
   * @param classJREF a JREF index for the class object
   * @return a JREF index for the uninitialized object
   * @exception InstantiationException if the class is abstract or is an interface
   * @exception OutOfMemoryError if no more memory to allocate
   */
  private static int AllocObject(VM_JNIEnvironment env, int classJREF) 
    throws InstantiationException, OutOfMemoryError {
    if (traceJNI) VM.sysWrite("JNI called: AllocObject  \n");

    try {
      Class javaCls = (Class) env.getJNIRef(classJREF);
      VM_Type type = java.lang.JikesRVMSupport.getTypeForClass(javaCls);
      if (type.isArrayType() || type.isPrimitiveType()) {
	env.recordException(new InstantiationException());
	return 0;
      }
      VM_Class cls = type.asClass();
      if (cls.isAbstract() || cls.isInterface()) {
	env.recordException(new InstantiationException());
	return 0;
      }

      int allocator = MM_Interface.pickAllocator(cls);
      Object newObj = VM_Runtime.resolvedNewScalar(cls.getInstanceSize(), 
						   cls.getTypeInformationBlock(),
						   cls.hasFinalizer(),
						   allocator);

      return newObj == null ? 0 : env.pushJNIRef(newObj);
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
   * @param envJREF a JREF index for the JNI environment object
   * @param classJREF a JREF index for the class object
   * @param methodID id of a VM_MethodReference
   * @return  the new object instance
   * @exception InstantiationException if the class is abstract or is an interface
   * @exception OutOfMemoryError if no more memory to allocate
   */
  private static int NewObject(VM_JNIEnvironment env, int classJREF, int methodID) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: NewObject  \n");

    try {
      Class cls = (Class) env.getJNIRef(classJREF); 
      VM_Class vmcls = java.lang.JikesRVMSupport.getTypeForClass(cls).asClass();
      
      if (vmcls.isAbstract() || vmcls.isInterface()) {
	env.recordException(new InstantiationException());
	return 0;
      }

      Object newobj = VM_JNIHelpers.invokeInitializer(cls, methodID, VM_Address.zero(), false, true);
      
      return env.pushJNIRef(newobj);    
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * NewObjectV: create a new object instance
   * @param envJREF a JREF index for the JNI environment object
   * @param classJREF a JREF index for the class object
   * @param methodID id of a VM_MethodReference
   * @param argAddress a raw address to a variable argument list, each element is 1-word or 
   *                   2-words of the appropriate type for the constructor invocation
   * @return the new object instance
   * @exception InstantiationException if the class is abstract or is an interface
   * @exception OutOfMemoryError if no more memory to allocate
   */
  private static int NewObjectV(VM_JNIEnvironment env, int classJREF, 
				int methodID, VM_Address argAddress) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: NewObjectV  \n");

    try {
      Class cls = (Class) env.getJNIRef(classJREF); 
      VM_Class vmcls = java.lang.JikesRVMSupport.getTypeForClass(cls).asClass();
      if (vmcls.isAbstract() || vmcls.isInterface()) {
	env.recordException(new InstantiationException());
	return 0;
      }

      Object newobj = VM_JNIHelpers.invokeInitializer(cls, methodID, argAddress, false, false);

      return env.pushJNIRef(newobj);    
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }


  /**
   * NewObjectA: create a new object instance
   * @param envJREF a JREF index for the JNI environment object
   * @param classJREF a JREF index for the class object
   * @param methodID id of a VM_MethodReference
   * @param argAddress a raw address to an array of unions in C, each element is 2-word and 
   *                   hold an argument of the appropriate type for the constructor invocation
   * @exception InstantiationException if the class is abstract or is an interface
   * @exception OutOfMemoryError if no more memory to allocate
   * @return  the new object instance
   */
  private static int NewObjectA(VM_JNIEnvironment env, int classJREF, 
				int methodID, VM_Address argAddress) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: NewObjectA  \n");

    try {
      Class cls = (Class) env.getJNIRef(classJREF);
      VM_Class vmcls = java.lang.JikesRVMSupport.getTypeForClass(cls).asClass();

      if (vmcls.isAbstract() || vmcls.isInterface()) {
	env.recordException(new InstantiationException());
	return 0;
      }

      Object newobj = VM_JNIHelpers.invokeInitializer(cls, methodID, argAddress, true, false);
      
      return env.pushJNIRef(newobj);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * GetObjectClass
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the object to check
   * @return a JREF index for the Class object 
   *
   */
  private static int GetObjectClass(VM_JNIEnvironment env, int objJREF) {
    if (traceJNI) VM.sysWrite("JNI called: GetObjectClass  \n");

    try {
      Object obj = (Object) env.getJNIRef(objJREF);
      return env.pushJNIRef(obj.getClass());
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }


  /**
   * IsInstanceOf: determine if an object is an instance of the class
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the object to check
   * @param classJREF a JREF index for the class to check
   * @return true if the object is an instance of the class
   */
  private static int IsInstanceOf(VM_JNIEnvironment env, int objJREF, int classJREF) {
    if (traceJNI) VM.sysWrite("JNI called: IsInstanceOf  \n");

    try {
      Class cls = (Class) env.getJNIRef(classJREF);
      Object obj = (Object) env.getJNIRef(objJREF);
      if (obj == null) return 0; // null instanceof T is always false
      VM_Type RHStype = VM_ObjectModel.getObjectType(obj);
      VM_Type LHStype = java.lang.JikesRVMSupport.getTypeForClass(cls);
      return (LHStype == RHStype || VM_Runtime.isAssignableWith(LHStype, RHStype)) ? 1 : 0;
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * GetMethodID:  get the virtual method ID given the name and the signature
   * @param envJREF a JREF index for the JNI environment object
   * @param classJREF a JREF index for the class object
   * @param methodNameAddress a raw address to a null-terminated string in C for the method name
   * @param methodSigAddress a raw address to a null-terminated string in C for the method signature
   * @return id of a VM_MethodReference
   * @exception NoSuchMethodError if the method cannot be found
   * @exception ExceptionInInitializerError if the class or interface static initializer fails 
   * @exception OutOfMemoryError if the system runs out of memory
   */
  private static int GetMethodID(VM_JNIEnvironment env, int classJREF, 
                                 VM_Address methodNameAddress, VM_Address methodSigAddress) {
    if (traceJNI) VM.sysWrite("JNI called: GetMethodID  \n");

    try {
      // obtain the names as String from the native space
      String methodString = VM_JNIHelpers.createStringFromC(methodNameAddress);
      VM_Atom methodName = VM_Atom.findOrCreateAsciiAtom(methodString);
      String sigString = VM_JNIHelpers.createStringFromC(methodSigAddress);
      VM_Atom sigName  = VM_Atom.findOrCreateAsciiAtom(sigString);

      // get the target class 
      Class jcls = (Class) env.getJNIRef(classJREF);
      VM_Type type = java.lang.JikesRVMSupport.getTypeForClass(jcls);
      if (!type.isClassType()) {
	env.recordException(new NoSuchMethodError());
        return 0;
      }	

      VM_Class klass = type.asClass();
      if (!klass.isInitialized()) {
	VM_Runtime.initializeClassForDynamicLink(klass);
      }

      // Find the target method
      VM_Method meth = null;
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
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param methodID id of a VM_MethodReference
   * @return the JREF index for the object returned from the method invocation
   */
  private static int CallObjectMethod(VM_JNIEnvironment env, int objJREF, int methodID) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallObjectMethod  \n");  

    try {
      Object obj =  env.getJNIRef(objJREF);
      Object returnObj = VM_JNIHelpers.invokeWithDotDotVarArg(obj, methodID, null, false);
      return env.pushJNIRef(returnObj);     
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallObjectMethodV:  invoke a virtual method that returns an object
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param methodID id of a VM_MethodReference
   * @param argAddress a raw address to a variable argument list, each element is 
   *              1-word or 2-words of the appropriate type for the method invocation
   * @return the JREF index for the object returned from the method invocation
   */
  private static int CallObjectMethodV(VM_JNIEnvironment env, int objJREF, 
				       int methodID, VM_Address argAddress) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallObjectMethodV  \n");

    try {
      Object obj =  env.getJNIRef(objJREF);
      Object returnObj = VM_JNIHelpers.invokeWithVarArg(obj, methodID, argAddress, null, false);
      return env.pushJNIRef(returnObj);     
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallObjectMethodA:  invoke a virtual method that returns an object value
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param methodID id of a VM_MethodReference
   * @param argAddress a raw address to an array of unions in C, each element is 2-word 
   *        and hold an argument of the appropriate type for the method invocation   
   * @return the JREF index for the object returned from the method invocation
   */
  private static int CallObjectMethodA(VM_JNIEnvironment env, int objJREF, int methodID, 
				       VM_Address argAddress) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallObjectMethodA  \n");

    try {
      Object obj =  env.getJNIRef(objJREF);
      Object returnObj = VM_JNIHelpers.invokeWithJValue(obj, methodID, argAddress, null, false);
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
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param methodID id of a VM_MethodReference
   * @return the boolean value returned from the method invocation
   */
  private static boolean CallBooleanMethod(VM_JNIEnvironment env, int objJREF, int methodID) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallBooleanMethod  \n");

    try {
      Object obj =  env.getJNIRef(objJREF);
      Object returnObj = VM_JNIHelpers.invokeWithDotDotVarArg(obj, methodID, 
								  VM_TypeReference.Boolean, 
								  false);
      return VM_Reflection.unwrapBoolean(returnObj);     // should be a wrapper for a boolean value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return false;
    }
  }

  /**
   * CallBooleanMethodV:  invoke a virtual method that returns a boolean value
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param methodID id of a VM_MethodReference
   * @param argAddress a raw address to a variable argument list, each element is 
   *              1-word or 2-words of the appropriate type for the method invocation
   * @return the boolean value returned from the method invocation
   */
  private static boolean CallBooleanMethodV(VM_JNIEnvironment env, int objJREF, int methodID, 
					    VM_Address argAddress) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallBooleanMethodV  \n");

    try {
      Object obj =  env.getJNIRef(objJREF);
      Object returnObj = VM_JNIHelpers.invokeWithVarArg(obj, methodID, argAddress, 
							    VM_TypeReference.Boolean, false);
      return VM_Reflection.unwrapBoolean(returnObj);     // should be a wrapper for a boolean value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return false;
    }
  }

  /**
   * CallBooleanMethodA:  invoke a virtual method that returns a boolean value
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param methodID id of a VM_MethodReference
   * @param argAddress a raw address to an array of unions in C, each element is 2-word 
   *        and hold an argument of the appropriate type for the method invocation   
   * @return the boolean value returned from the method invocation
   */
  private static boolean CallBooleanMethodA(VM_JNIEnvironment env, int objJREF, int methodID, 
					    VM_Address argAddress) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallBooleanMethodA  \n");  

    try {
      Object obj =  env.getJNIRef(objJREF);
      Object returnObj = VM_JNIHelpers.invokeWithJValue(obj, methodID, argAddress, 
							    VM_TypeReference.Boolean, false);
      return VM_Reflection.unwrapBoolean(returnObj);     // should be a wrapper for a boolean value
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
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param methodID id of a VM_MethodReference
   * @return the byte value returned from the method invocation
   */
  private static byte CallByteMethod(VM_JNIEnvironment env, int objJREF, int methodID) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallByteMethod  \n");

    try {
      Object obj =  env.getJNIRef(objJREF);
      Object returnObj = VM_JNIHelpers.invokeWithDotDotVarArg(obj, methodID, 
								  VM_TypeReference.Byte, false);
      return VM_Reflection.unwrapByte(returnObj);     // should be a wrapper for a byte value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallByteMethodV:  invoke a virtual method that returns a byte value
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param methodID id of a VM_MethodReference
   * @param argAddress a raw address to a variable argument list, each element is 
   *              1-word or 2-words of the appropriate type for the method invocation
   * @return the byte value returned from the method invocation
   */
  private static byte CallByteMethodV(VM_JNIEnvironment env, int objJREF, int methodID, 
				      VM_Address argAddress) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallByteMethodV  \n");  

    try {
      Object obj =  env.getJNIRef(objJREF);
      Object returnObj = VM_JNIHelpers.invokeWithVarArg(obj, methodID, argAddress, 
							    VM_TypeReference.Byte, false);
      return VM_Reflection.unwrapByte(returnObj);     // should be a wrapper for a byte value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallByteMethodA:  invoke a virtual method that returns a byte value
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param methodID id of a VM_MethodReference
   * @param argAddress a raw address to an array of unions in C, each element is 2-word 
   *        and hold an argument of the appropriate type for the method invocation   
   * @return the byte value returned from the method invocation
   */
  private static byte CallByteMethodA(VM_JNIEnvironment env, int objJREF, int methodID, 
				      VM_Address argAddress) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallByteMethodA  \n");  

    try {
      Object obj =  env.getJNIRef(objJREF);
      Object returnObj = VM_JNIHelpers.invokeWithJValue(obj, methodID, argAddress, 
							    VM_TypeReference.Byte, false);
      return VM_Reflection.unwrapByte(returnObj);     // should be a wrapper for a byte value
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
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param methodID id of a VM_MethodReference
   * @return the char value returned from the method invocation
   */
  private static char CallCharMethod(VM_JNIEnvironment env, int objJREF, int methodID) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallCharMethod  \n");  

    try {
      Object obj =  env.getJNIRef(objJREF);
      Object returnObj = VM_JNIHelpers.invokeWithDotDotVarArg(obj, methodID, 
								  VM_TypeReference.Char, false);
      return VM_Reflection.unwrapChar(returnObj);     // should be a wrapper for a char value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallCharMethodV:  invoke a virtual method that returns a char value
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param methodID id of a VM_MethodReference
   * @param argAddress a raw address to a variable argument list, each element is 
   *              1-word or 2-words of the appropriate type for the method invocation
   * @return the char value returned from the method invocation
   */
  private static char CallCharMethodV(VM_JNIEnvironment env, int objJREF, int methodID, 
				      VM_Address argAddress) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallCharMethodV  \n");  

    try {
      Object obj =  env.getJNIRef(objJREF);
      Object returnObj = VM_JNIHelpers.invokeWithVarArg(obj, methodID, argAddress, 
							    VM_TypeReference.Char, false);
      return VM_Reflection.unwrapChar(returnObj);     // should be a wrapper for a char value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallCharMethodA:  invoke a virtual method that returns a char value
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param methodID id of a VM_MethodReference
   * @param argAddress a raw address to an array of unions in C, each element is 2-word 
   *        and hold an argument of the appropriate type for the method invocation   
   * @return the char value returned from the method invocation
   */
  private static char CallCharMethodA(VM_JNIEnvironment env, int objJREF, int methodID, VM_Address argAddress) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallCharMethodA  \n");  

    try {
      Object obj =  env.getJNIRef(objJREF);
      Object returnObj = VM_JNIHelpers.invokeWithJValue(obj, methodID, argAddress, 
							    VM_TypeReference.Char, false);
      return VM_Reflection.unwrapChar(returnObj);     // should be a wrapper for a char value
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
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param methodID id of a VM_MethodReference
   * @return the short value returned from the method invocation
   */
  private static short CallShortMethod(VM_JNIEnvironment env, int objJREF, int methodID) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallShortMethod  \n");

    try {
      Object obj =  env.getJNIRef(objJREF);
      Object returnObj = VM_JNIHelpers.invokeWithDotDotVarArg(obj, methodID, 
								  VM_TypeReference.Short, false);
      return VM_Reflection.unwrapShort(returnObj);     // should be a wrapper for a short value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallShortMethodV:  invoke a virtual method that returns a short value
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param methodID id of a VM_MethodReference
   * @param argAddress a raw address to a variable argument list, each element is 
   *              1-word or 2-words of the appropriate type for the method invocation
   * @return the short value returned from the method invocation
   */
  private static short CallShortMethodV(VM_JNIEnvironment env, int objJREF, int methodID, 
					VM_Address argAddress) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallShortMethodV  \n");  

    try {
      Object obj =  env.getJNIRef(objJREF);
      Object returnObj = VM_JNIHelpers.invokeWithVarArg(obj, methodID, argAddress, 
							    VM_TypeReference.Short, false);
      return VM_Reflection.unwrapShort(returnObj);     // should be a wrapper for a short value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallShortMethodA:  invoke a virtual method that returns a short value
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param methodID id of a VM_MethodReference
   * @param argAddress a raw address to an array of unions in C, each element is 2-word 
   *        and hold an argument of the appropriate type for the method invocation   
   * @return the short value returned from the method invocation
   */
  private static short CallShortMethodA(VM_JNIEnvironment env, int objJREF, int methodID, 
					VM_Address argAddress) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallShortMethodA  \n");  

    try {
      Object obj =  env.getJNIRef(objJREF);
      Object returnObj = VM_JNIHelpers.invokeWithJValue(obj, methodID, argAddress, 
							    VM_TypeReference.Short, false);
      return VM_Reflection.unwrapShort(returnObj);     // should be a wrapper for a short value
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
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param methodID id of a VM_MethodReference
   * @return the int value returned from the method invocation
   */
  private static int CallIntMethod(VM_JNIEnvironment env, int objJREF, int methodID) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallIntMethod  \n");  

    try {
      Object obj =  env.getJNIRef(objJREF);
      Object returnObj = VM_JNIHelpers.invokeWithDotDotVarArg(obj, methodID, 
								  VM_TypeReference.Int, false);
      return VM_Reflection.unwrapInt(returnObj);     // should be a wrapper for an integer value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallIntMethodV:  invoke a virtual method that returns an int value
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param methodID id of a VM_MethodReference
   * @param argAddress a raw address to a variable argument list, each element is 
   *              1-word or 2-words of the appropriate type for the method invocation
   * @return the int value returned from the method invocation
   */
  private static int CallIntMethodV(VM_JNIEnvironment env, int objJREF, int methodID, 
				    VM_Address argAddress) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallIntMethodV  \n");  

    try {
      Object obj =  env.getJNIRef(objJREF);
      Object returnObj = VM_JNIHelpers.invokeWithVarArg(obj, methodID, argAddress, 
							    VM_TypeReference.Int, false);
      return VM_Reflection.unwrapInt(returnObj);     // should be a wrapper for an integer value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallIntMethodA:  invoke a virtual method that returns an integer value
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param methodID id of a VM_MethodReference
   * @param argAddress a raw address to an array of unions in C, each element is 2-word 
   *        and hold an argument of the appropriate type for the method invocation   
   * @return the integer value returned from the method invocation
   */
  private static int CallIntMethodA(VM_JNIEnvironment env, int objJREF, int methodID, 
				    VM_Address argAddress) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallIntMethodA  \n");  

    try {
      Object obj =  env.getJNIRef(objJREF);
      Object returnObj = VM_JNIHelpers.invokeWithJValue(obj, methodID, argAddress, 
							    VM_TypeReference.Int, false);
      return VM_Reflection.unwrapInt(returnObj);     // should be a wrapper for an integer value
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
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param methodID id of a VM_MethodReference
   * @return the long value returned from the method invocation
   */
  private static long CallLongMethod(VM_JNIEnvironment env, int objJREF, int methodID) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallLongMethod  \n");  

    try {
      Object obj =  env.getJNIRef(objJREF);
      Object returnObj = VM_JNIHelpers.invokeWithDotDotVarArg(obj, methodID, 
								  VM_TypeReference.Long, false);
      return VM_Reflection.unwrapLong(returnObj);     // should be a wrapper for a long value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallLongMethodV:  invoke a virtual method that returns a long value
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param methodID id of a VM_MethodReference
   * @param argAddress a raw address to a variable argument list, each element is 
   *              1-word or 2-words of the appropriate type for the method invocation
   * @return the long value returned from the method invocation
   */
  private static long CallLongMethodV(VM_JNIEnvironment env, int objJREF, int methodID, 
				      VM_Address argAddress) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallLongMethodV  \n");  

    try {
      Object obj =  env.getJNIRef(objJREF);
      Object returnObj = VM_JNIHelpers.invokeWithVarArg(obj, methodID, argAddress, 
							    VM_TypeReference.Long, false);
      return VM_Reflection.unwrapLong(returnObj);     // should be a wrapper for a long value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallLongMethodA:  invoke a virtual method that returns a long value
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param methodID id of a VM_MethodReference
   * @param argAddress a raw address to an array of unions in C, each element is 2-word 
   *        and hold an argument of the appropriate type for the method invocation   
   * @return the long value returned from the method invocation
   */
  private static long CallLongMethodA(VM_JNIEnvironment env, int objJREF, int methodID, 
				      VM_Address argAddress) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallLongMethodA  \n");  

    try {
      Object obj =  env.getJNIRef(objJREF);
      Object returnObj = VM_JNIHelpers.invokeWithJValue(obj, methodID, argAddress, 
							    VM_TypeReference.Long, false);
      return VM_Reflection.unwrapLong(returnObj);     // should be a wrapper for a long value
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
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param methodID id of a VM_MethodReference
   * @return the float value returned from the method invocation
   */
  private static float CallFloatMethod(VM_JNIEnvironment env, int objJREF, int methodID) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallFloatMethod  \n");  

    try {
      Object obj =  env.getJNIRef(objJREF);
      Object returnObj = VM_JNIHelpers.invokeWithDotDotVarArg(obj, methodID, 
								  VM_TypeReference.Float, false);
      return VM_Reflection.unwrapFloat(returnObj);     // should be a wrapper for a float value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallFloatMethodV:  invoke a virtual method that returns a float value
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param methodID id of a VM_MethodReference
   * @param argAddress a raw address to a variable argument list, each element is 
   *              1-word or 2-words of the appropriate type for the method invocation
   * @return the float value returned from the method invocation
   */
  private static float CallFloatMethodV(VM_JNIEnvironment env, int objJREF, int methodID, 
					VM_Address argAddress) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallFloatMethodV  \n");  

    try {
      Object obj =  env.getJNIRef(objJREF);
      Object returnObj = VM_JNIHelpers.invokeWithVarArg(obj, methodID, argAddress, 
							    VM_TypeReference.Float, false);
      return VM_Reflection.unwrapFloat(returnObj);     // should be a wrapper for a float value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallFloatMethodA:  invoke a virtual method that returns a float value
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param methodID id of a VM_MethodReference
   * @param argAddress a raw address to an array of unions in C, each element is 2-word 
   *        and hold an argument of the appropriate type for the method invocation   
   * @return the float value returned from the method invocation
   */
  private static float CallFloatMethodA(VM_JNIEnvironment env, int objJREF, int methodID, 
					VM_Address argAddress) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallFloatMethodA  \n");  

    try {
      Object obj =  env.getJNIRef(objJREF);
      Object returnObj = VM_JNIHelpers.invokeWithJValue(obj, methodID, argAddress, 
							    VM_TypeReference.Float, false);
      return VM_Reflection.unwrapFloat(returnObj);     // should be a wrapper for a float value
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
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param methodID id of a VM_MethodReference
   * @return the double value returned from the method invocation
   */
  private static double CallDoubleMethod(VM_JNIEnvironment env, int objJREF, int methodID) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallDoubleMethod  \n");  

    try {
      Object obj =  env.getJNIRef(objJREF);
      Object returnObj = VM_JNIHelpers.invokeWithDotDotVarArg(obj, methodID, 
								  VM_TypeReference.Double, false);
      return VM_Reflection.unwrapDouble(returnObj);     // should be a wrapper for a double value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallDoubleMethodV:  invoke a virtual method that returns a double value
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param methodID id of a VM_MethodReference
   * @param argAddress a raw address to a variable argument list, each element is 
   *              1-word or 2-words of the appropriate type for the method invocation
   * @return the double value returned from the method invocation
   */
  private static double CallDoubleMethodV(VM_JNIEnvironment env, int objJREF, int methodID, 
					  VM_Address argAddress) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallDoubleMethodV  \n");

    try {
      Object obj =  env.getJNIRef(objJREF);
      Object returnObj = VM_JNIHelpers.invokeWithVarArg(obj, methodID, argAddress, 
							    VM_TypeReference.Double, false);
      return VM_Reflection.unwrapDouble(returnObj);     // should be a wrapper for a double value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallDoubleMethodA:  invoke a virtual method that returns a double value
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param methodID id of a VM_MethodReference
   * @param argAddress a raw address to an array of unions in C, each element is 2-word 
   *        and hold an argument of the appropriate type for the method invocation   
   * @return the double value returned from the method invocation
   */
  private static double CallDoubleMethodA(VM_JNIEnvironment env, int objJREF, int methodID, 
					  VM_Address argAddress) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallDoubleMethodA  \n");

    try {
      Object obj =  env.getJNIRef(objJREF);
      Object returnObj = VM_JNIHelpers.invokeWithJValue(obj, methodID, argAddress, 
							    VM_TypeReference.Double, false);
      return VM_Reflection.unwrapDouble(returnObj);     // should be a wrapper for a double value
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
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param methodID id of a VM_MethodReference
   * @return the void value returned from the method invocation
   */
  private static void CallVoidMethod(VM_JNIEnvironment env, int objJREF, int methodID) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallVoidMethod  \n");  

    try {
      Object obj =  env.getJNIRef(objJREF);
      VM_JNIHelpers.invokeWithDotDotVarArg(obj, methodID, VM_TypeReference.Void, false);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * CallVoidMethodV:  invoke a virtual method that returns void
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param methodID id of a VM_MethodReference
   * @param argAddress a raw address to a variable argument list, each element is 
   *              1-word or 2-words of the appropriate type for the method invocation
   */
  private static void CallVoidMethodV(VM_JNIEnvironment env, int objJREF, int methodID, 
				      VM_Address argAddress) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallVoidMethodV  \n");

    try {
      Object obj =  env.getJNIRef(objJREF);
      VM_JNIHelpers.invokeWithVarArg(obj, methodID, argAddress, VM_TypeReference.Void, false);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * CallVoidMethodA:  invoke a virtual method that returns void
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param methodID id of a VM_MethodReference
   * @param argAddress a raw address to an array of unions in C, each element is 2-word 
   *        and hold an argument of the appropriate type for the method invocation   
   */
  private static void CallVoidMethodA(VM_JNIEnvironment env, int objJREF, int methodID, 
				      VM_Address argAddress) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallVoidMethodA  \n");

    try {
      Object obj =  env.getJNIRef(objJREF);
      VM_JNIHelpers.invokeWithJValue(obj, methodID, argAddress, VM_TypeReference.Void, false);
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
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param classJREF a JREF index for the class object that declares this method
   * @param methodID id of a VM_MethodReference
   * @return the JREF index for the object returned from the method invocation
   */
  private static int CallNonvirtualObjectMethod(VM_JNIEnvironment env, int objJREF, int classJREF, 
						int methodID) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallNonvirtualObjectMethod  \n");  

    try {
      Object obj =  env.getJNIRef(objJREF);
      Object returnObj = VM_JNIHelpers.invokeWithDotDotVarArg(obj, methodID, null, true);
      return env.pushJNIRef(returnObj);     
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallNonvirtualObjectMethodV:  invoke a virtual method that returns an object
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param classJREF a JREF index for the class object that declares this method
   * @param methodID id of a VM_MethodReference
   * @param argAddress a raw address to a variable argument list, each element is 
   *              1-word or 2-words of the appropriate type for the method invocation
   * @return the JREF index for the object returned from the method invocation
   */
  private static int CallNonvirtualObjectMethodV(VM_JNIEnvironment env, int objJREF, int classJREF, 
						 int methodID, VM_Address argAddress) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallNonvirtualObjectMethodV  \n");

    try {
      Object obj =  env.getJNIRef(objJREF);
      Object returnObj = VM_JNIHelpers.invokeWithVarArg(obj, methodID, argAddress, null, true);
      return env.pushJNIRef(returnObj);     
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallNonvirtualNonvirtualObjectMethodA:  invoke a virtual method that returns an object value
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param classJREF a JREF index for the class object that declares this method
   * @param methodID id of a VM_MethodReference
   * @param argAddress a raw address to an array of unions in C, each element is 2-word 
   *        and hold an argument of the appropriate type for the method invocation   
   * @return the JREF index for the object returned from the method invocation
   */
  private static int CallNonvirtualObjectMethodA(VM_JNIEnvironment env, int objJREF, int classJREF, 
						 int methodID, VM_Address argAddress) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallNonvirtualObjectMethodA  \n");  

    try {
      Object obj =  env.getJNIRef(objJREF);
      Object returnObj = VM_JNIHelpers.invokeWithJValue(obj, methodID, argAddress, null, true);
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
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param classJREF a JREF index for the class object that declares this method
   * @param methodID id of a VM_MethodReference
   * @return the boolean value returned from the method invocation
   */
  private static boolean CallNonvirtualBooleanMethod(VM_JNIEnvironment env, int objJREF, int classJREF, 
						     int methodID) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallNonvirtualBooleanMethod  \n");

    try {
      Object obj =  env.getJNIRef(objJREF);
      Object returnObj = VM_JNIHelpers.invokeWithDotDotVarArg(obj, methodID, VM_TypeReference.Boolean, true);
      return VM_Reflection.unwrapBoolean(returnObj);     // should be a wrapper for a boolean value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return false;
    }
  }

  /**
   * CallNonvirtualBooleanMethodV:  invoke a virtual method that returns a boolean value
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param classJREF a JREF index for the class object that declares this method
   * @param methodID id of a VM_MethodReference
   * @param argAddress a raw address to a variable argument list, each element is 
   *              1-word or 2-words of the appropriate type for the method invocation
   * @return the boolean value returned from the method invocation
   */
  private static boolean CallNonvirtualBooleanMethodV(VM_JNIEnvironment env, int objJREF, int classJREF, 
						      int methodID, VM_Address argAddress) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallNonvirtualBooleanMethodV  \n");

    try {
      Object obj =  env.getJNIRef(objJREF);
      Object returnObj = VM_JNIHelpers.invokeWithVarArg(obj, methodID, argAddress, 
							    VM_TypeReference.Boolean, true);
      return VM_Reflection.unwrapBoolean(returnObj);     // should be a wrapper for a boolean value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return false;
    }
  }

  /**
   * CallNonvirtualBooleanMethodA:  invoke a virtual method that returns a boolean value
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param classJREF a JREF index for the class object that declares this method
   * @param methodID id of a VM_MethodReference
   * @param argAddress a raw address to an array of unions in C, each element is 2-word 
   *        and hold an argument of the appropriate type for the method invocation   
   * @return the boolean value returned from the method invocation
   */
  private static boolean CallNonvirtualBooleanMethodA(VM_JNIEnvironment env, int objJREF, int classJREF, 
						      int methodID, VM_Address argAddress) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallNonvirtualBooleanMethodA  \n");  

    try {
      Object obj =  env.getJNIRef(objJREF);
      Object returnObj = VM_JNIHelpers.invokeWithJValue(obj, methodID, argAddress, 
							    VM_TypeReference.Boolean, true);
      return VM_Reflection.unwrapBoolean(returnObj);     // should be a wrapper for a boolean value
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
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param classJREF a JREF index for the class object that declares this method
   * @param methodID id of a VM_MethodReference
   * @return the byte value returned from the method invocation
   */
  private static byte CallNonvirtualByteMethod(VM_JNIEnvironment env, int objJREF, int classJREF, 
					       int methodID) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallNonvirtualByteMethod  \n");

    try {
      Object obj =  env.getJNIRef(objJREF);
      Object returnObj = VM_JNIHelpers.invokeWithDotDotVarArg(obj, methodID, 
								  VM_TypeReference.Byte, true);
      return VM_Reflection.unwrapByte(returnObj);     // should be a wrapper for a byte value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallNonvirtualByteMethodV:  invoke a virtual method that returns a byte value
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param methodID id of a VM_MethodReference
   * @param classJREF a JREF index for the class object that declares this method
   * @param argAddress a raw address to a variable argument list, each element is 
   *              1-word or 2-words of the appropriate type for the method invocation
   * @return the byte value returned from the method invocation
   */
  private static byte CallNonvirtualByteMethodV(VM_JNIEnvironment env, int objJREF, int classJREF, 
						int methodID, VM_Address argAddress) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallNonvirtualByteMethodV  \n");
    
    try {
      Object obj =  env.getJNIRef(objJREF);
      Object returnObj = VM_JNIHelpers.invokeWithVarArg(obj, methodID, argAddress, 
							    VM_TypeReference.Byte, true);
      return VM_Reflection.unwrapByte(returnObj);     // should be a wrapper for a byte value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallNonvirtualByteMethodA:  invoke a virtual method that returns a byte value
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param methodID id of a VM_MethodReference
   * @param classJREF a JREF index for the class object that declares this method
   * @param argAddress a raw address to an array of unions in C, each element is 2-word 
   *        and hold an argument of the appropriate type for the method invocation   
   * @return the byte value returned from the method invocation
   */
  private static byte CallNonvirtualByteMethodA(VM_JNIEnvironment env, int objJREF, int classJREF, 
						int methodID, VM_Address argAddress) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallNonvirtualByteMethodA  \n");

    try {
      Object obj =  env.getJNIRef(objJREF);
      Object returnObj = VM_JNIHelpers.invokeWithJValue(obj, methodID, argAddress, 
							    VM_TypeReference.Byte, true);
      return VM_Reflection.unwrapByte(returnObj);     // should be a wrapper for a byte value
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
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param classJREF a JREF index for the class object that declares this method
   * @param methodID id of a VM_MethodReference
   * @return the char value returned from the method invocation
   */
  private static char CallNonvirtualCharMethod(VM_JNIEnvironment env, int objJREF, int classJREF, 
					       int methodID) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallNonvirtualCharMethod  \n");

    try {
      Object obj =  env.getJNIRef(objJREF);
      Object returnObj = VM_JNIHelpers.invokeWithDotDotVarArg(obj, methodID, 
								  VM_TypeReference.Char, true);
      return VM_Reflection.unwrapChar(returnObj);     // should be a wrapper for a char value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallNonvirtualCharMethodV:  invoke a virtual method that returns a char value
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param classJREF a JREF index for the class object that declares this method
   * @param methodID id of a VM_MethodReference
   * @param argAddress a raw address to a variable argument list, each element is 
   *              1-word or 2-words of the appropriate type for the method invocation
   * @return the char value returned from the method invocation
   */
  private static char CallNonvirtualCharMethodV(VM_JNIEnvironment env, int objJREF, int classJREF, 
						int methodID, VM_Address argAddress) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallNonvirtualCharMethodV  \n");

    try {
      Object obj =  env.getJNIRef(objJREF);
      Object returnObj = VM_JNIHelpers.invokeWithVarArg(obj, methodID, argAddress, 
							    VM_TypeReference.Char, true);
      return VM_Reflection.unwrapChar(returnObj);     // should be a wrapper for a char value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallNonvirtualCharMethodA:  invoke a virtual method that returns a char value
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param classJREF a JREF index for the class object that declares this method
   * @param methodID id of a VM_MethodReference
   * @param argAddress a raw address to an array of unions in C, each element is 2-word 
   *        and hold an argument of the appropriate type for the method invocation   
   * @return the char value returned from the method invocation
   */
  private static char CallNonvirtualCharMethodA(VM_JNIEnvironment env, int objJREF, int classJREF, 
						int methodID, VM_Address argAddress) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallNonvirtualCharMethodA  \n");

    try {
      Object obj =  env.getJNIRef(objJREF);
      Object returnObj = VM_JNIHelpers.invokeWithJValue(obj, methodID, argAddress, 
							    VM_TypeReference.Char, true);
      return VM_Reflection.unwrapChar(returnObj);     // should be a wrapper for a char value
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
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param classJREF a JREF index for the class object that declares this method
   * @param methodID id of a VM_MethodReference
   * @return the short value returned from the method invocation
   */
  private static short CallNonvirtualShortMethod(VM_JNIEnvironment env, int objJREF, int classJREF, 
						 int methodID) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallNonvirtualShortMethod  \n");

    try {
      Object obj =  env.getJNIRef(objJREF);
      Object returnObj = VM_JNIHelpers.invokeWithDotDotVarArg(obj, methodID, 
								  VM_TypeReference.Short, true);
      return VM_Reflection.unwrapShort(returnObj);     // should be a wrapper for a short value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallNonvirtualShortMethodV:  invoke a virtual method that returns a short value
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param classJREF a JREF index for the class object that declares this method
   * @param methodID id of a VM_MethodReference
   * @param argAddress a raw address to a variable argument list, each element is 
   *              1-word or 2-words of the appropriate type for the method invocation
   * @return the short value returned from the method invocation
   */
  private static short CallNonvirtualShortMethodV(VM_JNIEnvironment env, int objJREF, int classJREF, 
						  int methodID, VM_Address argAddress) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallNonvirtualShortMethodV  \n");

    try {
      Object obj =  env.getJNIRef(objJREF);
      Object returnObj = VM_JNIHelpers.invokeWithVarArg(obj, methodID, argAddress, 
							    VM_TypeReference.Short, true);
      return VM_Reflection.unwrapShort(returnObj);     // should be a wrapper for a short value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallNonvirtualShortMethodA:  invoke a virtual method that returns a short value
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param classJREF a JREF index for the class object that declares this method
   * @param methodID id of a VM_MethodReference
   * @param argAddress a raw address to an array of unions in C, each element is 2-word 
   *        and hold an argument of the appropriate type for the method invocation   
   * @return the short value returned from the method invocation
   */
  private static short CallNonvirtualShortMethodA(VM_JNIEnvironment env, int objJREF, int classJREF, 
						  int methodID, VM_Address argAddress) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallNonvirtualShortMethodA  \n");

    try {
      Object obj =  env.getJNIRef(objJREF);
      Object returnObj = VM_JNIHelpers.invokeWithJValue(obj, methodID, argAddress, 
							    VM_TypeReference.Short, true);
      return VM_Reflection.unwrapShort(returnObj);     // should be a wrapper for a short value
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
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param classJREF a JREF index for the class object that declares this method
   * @param methodID id of a VM_MethodReference
   * @return the int value returned from the method invocation
   */
  private static int CallNonvirtualIntMethod(VM_JNIEnvironment env, int objJREF, int classJREF, int methodID) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallNonvirtualIntMethod  \n");

    try {
      Object obj =  env.getJNIRef(objJREF);
      Object returnObj = VM_JNIHelpers.invokeWithDotDotVarArg(obj, methodID, 
								  VM_TypeReference.Int, true);
      return VM_Reflection.unwrapInt(returnObj);     // should be a wrapper for an integer value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallNonvirtualIntMethodV:  invoke a virtual method that returns an int value
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param classJREF a JREF index for the class object that declares this method
   * @param methodID id of a VM_MethodReference
   * @param argAddress a raw address to a variable argument list, each element is 
   *              1-word or 2-words of the appropriate type for the method invocation
   * @return the int value returned from the method invocation
   */
  private static int CallNonvirtualIntMethodV(VM_JNIEnvironment env, int objJREF, int classJREF, 
					      int methodID, VM_Address argAddress) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallNonvirtualIntMethodV  \n");

    try {
      Object obj =  env.getJNIRef(objJREF);
      Object returnObj = VM_JNIHelpers.invokeWithVarArg(obj, methodID, argAddress, 
							    VM_TypeReference.Int, true);
      return VM_Reflection.unwrapInt(returnObj);     // should be a wrapper for an integer value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallNonvirtualIntMethodA:  invoke a virtual method that returns an integer value
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param classJREF a JREF index for the class object that declares this method
   * @param methodID id of a VM_MethodReference
   * @param argAddress a raw address to an array of unions in C, each element is 2-word 
   *        and hold an argument of the appropriate type for the method invocation   
   * @return the integer value returned from the method invocation
   */
  private static int CallNonvirtualIntMethodA(VM_JNIEnvironment env, int objJREF, int classJREF, 
					      int methodID, VM_Address argAddress) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallNonvirtualIntMethodA  \n");

    try {
      Object obj =  env.getJNIRef(objJREF);
      Object returnObj = VM_JNIHelpers.invokeWithJValue(obj, methodID, argAddress, 
							    VM_TypeReference.Int, true);
      return VM_Reflection.unwrapInt(returnObj);     // should be a wrapper for an integer value
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
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param classJREF a JREF index for the class object that declares this method
   * @param methodID id of a VM_MethodReference
   * @return the long value returned from the method invocation
   */
  private static long CallNonvirtualLongMethod(VM_JNIEnvironment env, int objJREF, int classJREF, 
					       int methodID) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallNonvirtualLongMethod  \n");
    
    try {
      Object obj =  env.getJNIRef(objJREF);
      Object returnObj = VM_JNIHelpers.invokeWithDotDotVarArg(obj, methodID, 
								  VM_TypeReference.Long, true);
      return VM_Reflection.unwrapLong(returnObj);     // should be a wrapper for a long value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallNonvirtualLongMethodV:  invoke a virtual method that returns a long value
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param classJREF a JREF index for the class object that declares this method
   * @param methodID id of a VM_MethodReference
   * @param argAddress a raw address to a variable argument list, each element is 
   *              1-word or 2-words of the appropriate type for the method invocation
   * @return the long value returned from the method invocation
   */
  private static long CallNonvirtualLongMethodV(VM_JNIEnvironment env, int objJREF, int classJREF, 
						int methodID, VM_Address argAddress) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallNonvirtualLongMethodV  \n");

    try {
      Object obj =  env.getJNIRef(objJREF);
      Object returnObj = VM_JNIHelpers.invokeWithVarArg(obj, methodID, argAddress, 
							    VM_TypeReference.Long, true);
      return VM_Reflection.unwrapLong(returnObj);     // should be a wrapper for a long value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallNonvirtualLongMethodA:  invoke a virtual method that returns a long value
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param classJREF a JREF index for the class object that declares this method
   * @param methodID id of a VM_MethodReference
   * @param argAddress a raw address to an array of unions in C, each element is 2-word 
   *        and hold an argument of the appropriate type for the method invocation   
   * @return the long value returned from the method invocation
   */
  private static long CallNonvirtualLongMethodA(VM_JNIEnvironment env, int objJREF, int classJREF, 
						int methodID, VM_Address argAddress) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallNonvirtualLongMethodA  \n");

    try {
      Object obj =  env.getJNIRef(objJREF);
      Object returnObj = VM_JNIHelpers.invokeWithJValue(obj, methodID, argAddress, 
							    VM_TypeReference.Long, true);
      return VM_Reflection.unwrapLong(returnObj);     // should be a wrapper for a long value
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
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param classJREF a JREF index for the class object that declares this method
   * @param methodId id of a VM_MethodReference
   * @return the float value returned from the method invocation
   */
  private static float CallNonvirtualFloatMethod(VM_JNIEnvironment env, int objJREF, int classJREF, 
						 int methodID) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallNonvirtualFloatMethod  \n");

    try {
      Object obj =  env.getJNIRef(objJREF);
      Object returnObj = VM_JNIHelpers.invokeWithDotDotVarArg(obj, methodID, VM_TypeReference.Float, true);
      return VM_Reflection.unwrapFloat(returnObj);     // should be a wrapper for a float value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallNonvirtualFloatMethodV:  invoke a virtual method that returns a float value
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param classJREF a JREF index for the class object that declares this method
   * @param methodID id of a VM_MethodReference
   * @param argAddress a raw address to a variable argument list, each element is 
   *              1-word or 2-words of the appropriate type for the method invocation
   * @return the float value returned from the method invocation
   */
  private static float CallNonvirtualFloatMethodV(VM_JNIEnvironment env, int objJREF, int classJREF, 
						  int methodID, VM_Address argAddress) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallNonvirtualFloatMethodV  \n");

    try {
      Object obj =  env.getJNIRef(objJREF);
      Object returnObj = VM_JNIHelpers.invokeWithVarArg(obj, methodID, argAddress, 
							    VM_TypeReference.Float, true);
      return VM_Reflection.unwrapFloat(returnObj);     // should be a wrapper for a float value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallNonvirtualFloatMethodA:  invoke a virtual method that returns a float value
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param classJREF a JREF index for the class object that declares this method
   * @param methodID id of a VM_MethodReference
   * @param argAddress a raw address to an array of unions in C, each element is 2-word 
   *        and hold an argument of the appropriate type for the method invocation   
   * @return the float value returned from the method invocation
   */
  private static float CallNonvirtualFloatMethodA(VM_JNIEnvironment env, int objJREF, int classJREF, 
						  int methodID, VM_Address argAddress) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallNonvirtualFloatMethodA  \n");

    try {
      Object obj =  env.getJNIRef(objJREF);
      Object returnObj = VM_JNIHelpers.invokeWithJValue(obj, methodID, argAddress, 
							    VM_TypeReference.Float, true);
      return VM_Reflection.unwrapFloat(returnObj);     // should be a wrapper for a float value
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
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param classJREF a JREF index for the class object that declares this method
   * @param methodID id of a VM_MethodReference
   * @return the double value returned from the method invocation
   */
  private static double CallNonvirtualDoubleMethod(VM_JNIEnvironment env, int objJREF, int classJREF, 
						   int methodID) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallNonvirtualDoubleMethod  \n");

    try {
      Object obj =  env.getJNIRef(objJREF);
      Object returnObj = VM_JNIHelpers.invokeWithDotDotVarArg(obj, methodID, VM_TypeReference.Double, true);
      return VM_Reflection.unwrapDouble(returnObj);     // should be a wrapper for a double value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallNonvirtualDoubleMethodV:  invoke a virtual method that returns a double value
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param classJREF a JREF index for the class object that declares this method
   * @param methodID id of a VM_MethodReference
   * @param argAddress a raw address to a variable argument list, each element is 
   *              1-word or 2-words of the appropriate type for the method invocation
   * @return the double value returned from the method invocation
   */
  private static double CallNonvirtualDoubleMethodV(VM_JNIEnvironment env, int objJREF, int classJREF, 
						    int methodID, VM_Address argAddress) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallNonvirtualDoubleMethodV  \n");

    try {
      Object obj =  env.getJNIRef(objJREF);
      Object returnObj = VM_JNIHelpers.invokeWithVarArg(obj, methodID, argAddress, 
							    VM_TypeReference.Double, true);
      return VM_Reflection.unwrapDouble(returnObj);     // should be a wrapper for a double value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallNonvirtualDoubleMethodA:  invoke a virtual method that returns a double value
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param classJREF a JREF index for the class object that declares this method
   * @param methodID id of a VM_MethodReference
   * @param argAddress a raw address to an array of unions in C, each element is 2-word 
   *        and hold an argument of the appropriate type for the method invocation   
   * @return the double value returned from the method invocation
   */
  private static double CallNonvirtualDoubleMethodA(VM_JNIEnvironment env, int objJREF, int classJREF, 
						    int methodID, VM_Address argAddress) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallNonvirtualDoubleMethodA  \n");

    try {
      Object obj =  env.getJNIRef(objJREF);
      Object returnObj = VM_JNIHelpers.invokeWithJValue(obj, methodID, argAddress, 
							    VM_TypeReference.Double, true);
      return VM_Reflection.unwrapDouble(returnObj);     // should be a wrapper for a double value
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
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param classJREF a JREF index for the class object that declares this method
   * @param methodID id of a VM_MethodReference
   * @return the void value returned from the method invocation
   */
  private static void CallNonvirtualVoidMethod(VM_JNIEnvironment env, int objJREF, int classJREF, 
					       int methodID) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallNonvirtualVoidMethod  \n");

    try {
      Object obj =  env.getJNIRef(objJREF);
      VM_JNIHelpers.invokeWithDotDotVarArg(obj, methodID, VM_TypeReference.Void, true);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * CallNonvirtualVoidMethodV:  invoke a virtual method that returns void
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param classJREF a JREF index for the class object that declares this method
   * @param methodID id of a VM_MethodReference
   * @param argAddress a raw address to a variable argument list, each element is 
   *              1-word or 2-words of the appropriate type for the method invocation
   */
  private static void CallNonvirtualVoidMethodV(VM_JNIEnvironment env, int objJREF, int classJREF, 
						int methodID, VM_Address argAddress) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallNonvirtualVoidMethodV  \n");

    try {
      Object obj =  env.getJNIRef(objJREF);
      VM_JNIHelpers.invokeWithVarArg(obj, methodID, argAddress, VM_TypeReference.Void, true);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * CallNonvirtualVoidMethodA:  invoke a virtual method that returns void
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the object instance
   * @param classJREF a JREF index for the class object that declares this method
   * @param methodID id of a VM_MethodReference
   * @param argAddress a raw address to an array of unions in C, each element is 2-word 
   *        and hold an argument of the appropriate type for the method invocation   
   */
  private static void CallNonvirtualVoidMethodA(VM_JNIEnvironment env, int objJREF, int classJREF, 
						int methodID, VM_Address argAddress) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallNonvirtualVoidMethodA  \n");
    
    try {
      Object obj =  env.getJNIRef(objJREF);
      VM_JNIHelpers.invokeWithJValue(obj, methodID, argAddress, VM_TypeReference.Void, true);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * GetFieldID:  return a field id, which can be cached in native code and reused 
   * @param envJREF a JREF index for the JNI environment object
   * @param classJREF a JREF index for the VM_Class object
   * @param fieldNameAddress a raw address to a null-terminated string in C for the field name
   * @param descriptorAddress a raw address to a null-terminated string in C for the descriptor
   * @return the fieldID of an instance field given the class, field name 
   *         and type. Return 0 if the field is not found
   * @exception NoSuchFieldError if the specified field cannot be found
   * @exception ExceptionInInitializerError if the class initializer fails
   * @exception OutOfMemoryError if the system runs out of memory
   */
  private static int GetFieldID(VM_JNIEnvironment env, int classJREF, 
                                VM_Address fieldNameAddress, VM_Address descriptorAddress) {
    if (traceJNI) VM.sysWrite("JNI called: GetFieldID  \n");  

    try {
      Class cls = (Class) env.getJNIRef(classJREF);
      String fieldString = VM_JNIHelpers.createStringFromC(fieldNameAddress);
      VM_Atom fieldName = VM_Atom.findOrCreateAsciiAtom(fieldString);

      String descriptorString = VM_JNIHelpers.createStringFromC(descriptorAddress);
      VM_Atom descriptor = VM_Atom.findOrCreateAsciiAtom(descriptorString);

      // list of all instance fields including superclasses
      VM_Field[] fields = java.lang.JikesRVMSupport.getTypeForClass(cls).getInstanceFields();
      for (int i = 0; i<fields.length; i++) {
	VM_Field f = fields[i];
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
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the target object
   * @param fieldID the id for the VM_Field that describes this field
   * @return the value of the Object field, converted to a JREF index
   *         or 0 if the fieldID is incorrect
   */
  private static int GetObjectField(VM_JNIEnvironment env, int objJREF, int fieldID) {
    if (traceJNI) VM.sysWrite("JNI called: GetObjectField  \n");

    try {
      Object obj =  env.getJNIRef(objJREF);
      VM_Field field = VM_MemberReference.getMemberRef(fieldID).asFieldReference().resolve();
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
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the target object
   * @param fieldID the id for the VM_Field that describes this field
   * @return the value of the boolean field, or 0 if the fieldID is incorrect
   */
  private static int GetBooleanField(VM_JNIEnvironment env, int objJREF, int fieldID) {
    if (traceJNI) VM.sysWrite("JNI called: GetBooleanField  \n");

    try {
      Object obj =  env.getJNIRef(objJREF);
      VM_Field field = VM_MemberReference.getMemberRef(fieldID).asFieldReference().resolve();
      return field.getBooleanValueUnchecked(obj) ? 1 : 0;
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;                      
    }
  }

  /**
   * GetByteField:  read an instance field of type byte
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the target object
   * @param fieldID the id for the VM_Field that describes this field
   * @return the value of the byte field, or 0 if the fieldID is incorrect
   */
  private static int GetByteField(VM_JNIEnvironment env, int objJREF, int fieldID) {
    if (traceJNI) VM.sysWrite("JNI called: GetByteField  \n");

    try {
      Object obj =  env.getJNIRef(objJREF);
      VM_Field field = VM_MemberReference.getMemberRef(fieldID).asFieldReference().resolve();
      return field.getByteValueUnchecked(obj);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;                      
    }
  }

  /**
   * GetCharField:  read an instance field of type character
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the target object
   * @param fieldID the id for the VM_Field that describes this field
   * @return the value of the character field, or 0 if the fieldID is incorrect
   */
  private static int GetCharField(VM_JNIEnvironment env, int objJREF, int fieldID) {
    if (traceJNI) VM.sysWrite("JNI called: GetCharField  \n");

    try {
      Object obj =  env.getJNIRef(objJREF);
      VM_Field field = VM_MemberReference.getMemberRef(fieldID).asFieldReference().resolve();
      return field.getCharValueUnchecked(obj);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;                      
    }
  }

  /**
   * GetShortField:  read an instance field of type short
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the target object
   * @param fieldID the id for the VM_Field that describes this field
   * @return the value of the short field, or 0 if the fieldID is incorrect
   */
  private static int GetShortField(VM_JNIEnvironment env, int objJREF, int fieldID) {
    if (traceJNI) VM.sysWrite("JNI called: GetShortField  \n");

    try {
      Object obj =  env.getJNIRef(objJREF);
      VM_Field field = VM_MemberReference.getMemberRef(fieldID).asFieldReference().resolve();
      return field.getShortValueUnchecked(obj);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;                      
    }
  }

  /**
   * GetIntField:  read an instance field of type integer
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the target object
   * @param fieldID the id for the VM_Field that describes this field
   * @return the value of the integer field, or 0 if the fieldID is incorrect
   */
  private static int GetIntField(VM_JNIEnvironment env, int objJREF, int fieldID) {
    if (traceJNI) VM.sysWrite("JNI called: GetIntField  \n");

    try {
      Object obj =  env.getJNIRef(objJREF);
      VM_Field field = VM_MemberReference.getMemberRef(fieldID).asFieldReference().resolve();
      return field.getIntValueUnchecked(obj);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;                      
    }
  }

  /**
   * GetLongField:  read an instance field of type long
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the target object
   * @param fieldID the id for the VM_Field that describes this field
   * @return the value of the long field or 0 if the fieldID is incorrect
   */
  private static long GetLongField(VM_JNIEnvironment env, int objJREF, int fieldID) {
    if (traceJNI) VM.sysWrite("JNI called: GetLongField  \n");

    try {
      Object obj =  env.getJNIRef(objJREF);
      VM_Field field = VM_MemberReference.getMemberRef(fieldID).asFieldReference().resolve();
      return field.getLongValueUnchecked(obj);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0L;                      
    }
  }

  /**
   * GetFloatField:  read an instance field of type float
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the target object
   * @param fieldID the id for the VM_Field that describes this field
   * @return the value of the float field or 0 if the fieldID is incorrect
   */
  private static float GetFloatField(VM_JNIEnvironment env, int objJREF, int fieldID) {
    if (traceJNI) VM.sysWrite("JNI called: GetFloatField  \n");

    try {
      Object obj =  env.getJNIRef(objJREF);
      VM_Field field = VM_MemberReference.getMemberRef(fieldID).asFieldReference().resolve();
      return field.getFloatValueUnchecked(obj);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0f;                      
    }
  }

  /**
   * GetDoubleField:  read an instance field of type double
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the target object
   * @param fieldID the id for the VM_Field that describes this field
   * @return the value of the double field or 0 if the fieldID is incorrect
   */
  private static double GetDoubleField(VM_JNIEnvironment env, int objJREF, int fieldID) {
    if (traceJNI) VM.sysWrite("JNI called: GetDoubleField  \n");

    try {
      Object obj =  env.getJNIRef(objJREF);
      VM_Field field = VM_MemberReference.getMemberRef(fieldID).asFieldReference().resolve();
      return field.getDoubleValueUnchecked(obj);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0.0;                      
    }
  }

  /**
   * SetObjectField: set a instance field of type Object
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the target object
   * @param fieldID the id for the VM_Field that describes this field
   * @param valueJREF a JREF index for the value to assign
   */
  private static void SetObjectField(VM_JNIEnvironment env, int objJREF, int fieldID, int valueJREF) {
    if (traceJNI) VM.sysWrite("JNI called: SetObjectField  \n");

    try {
      Object obj =  env.getJNIRef(objJREF);
      Object value =  env.getJNIRef(valueJREF);
      VM_Field field = VM_MemberReference.getMemberRef(fieldID).asFieldReference().resolve();
      field.setObjectValueUnchecked(obj,value);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }


  /**
   * SetBooleanField: set an instance field of type boolean
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the target object
   * @param fieldID the id for the VM_Field that describes this field
   * @param value   boolean value to assign
   */
  private static void SetBooleanField(VM_JNIEnvironment env, int objJREF, int fieldID, boolean value) {
    if (traceJNI) VM.sysWrite("JNI called: SetBooleanField  \n");

    try {
      Object obj =  env.getJNIRef(objJREF);
      VM_Field field = VM_MemberReference.getMemberRef(fieldID).asFieldReference().resolve();
      field.setBooleanValueUnchecked(obj,value);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * SetByteField: set an instance field of type byte
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the target object
   * @param fieldID the id for the VM_Field that describes this field
   * @param value   byte value to assign
   */
  private static void SetByteField(VM_JNIEnvironment env, int objJREF, int fieldID, byte value) {
    if (traceJNI) VM.sysWrite("JNI called: SetByteField  \n");

    try {
      Object obj =  env.getJNIRef(objJREF);
      VM_Field field = VM_MemberReference.getMemberRef(fieldID).asFieldReference().resolve();
      field.setByteValueUnchecked(obj,value);    
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * SetCharField: set an instance field of type char
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the target object
   * @param fieldID the id for the VM_Field that describes this field
   * @param value   char value to assign
   */
  private static void SetCharField(VM_JNIEnvironment env, int objJREF, int fieldID, char value) {
    if (traceJNI) VM.sysWrite("JNI called: SetCharField  \n");

    try {
      Object obj =  env.getJNIRef(objJREF);
      VM_Field field = VM_MemberReference.getMemberRef(fieldID).asFieldReference().resolve();
      field.setCharValueUnchecked(obj,value);    
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * SetShortField: set an instance field of type short
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the target object
   * @param fieldID the id for the VM_Field that describes this field
   * @param value   short value to assign
   */
  private static void SetShortField(VM_JNIEnvironment env, int objJREF, int fieldID, short value) {
    if (traceJNI) VM.sysWrite("JNI called: SetShortField  \n");

    try {
      Object obj =  env.getJNIRef(objJREF);
      VM_Field field = VM_MemberReference.getMemberRef(fieldID).asFieldReference().resolve();
      field.setShortValueUnchecked(obj,value);    
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * SetIntField: set an instance field of type integer
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the target object
   * @param fieldID the id for the VM_Field that describes this field
   * @param value   integer value to assign
   */
  private static void SetIntField(VM_JNIEnvironment env, int objJREF, int fieldID, int value) {
    if (traceJNI) VM.sysWrite("JNI called: SetIntField  \n");

    try {
      Object obj =  env.getJNIRef(objJREF);
      VM_Field field = VM_MemberReference.getMemberRef(fieldID).asFieldReference().resolve();
      field.setIntValueUnchecked(obj,value);    
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * SetLongField: set an instance field of type long
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the target object
   * @param fieldID the id for the VM_Field that describes this field
   * @param value   long value to assign
   */
  private static void SetLongField(VM_JNIEnvironment env, int objJREF, int fieldID, long value) {
    if (traceJNI) VM.sysWrite("JNI called: SetLongField  \n");

    try {
      Object obj =  env.getJNIRef(objJREF);
      VM_Field field = VM_MemberReference.getMemberRef(fieldID).asFieldReference().resolve();
      field.setLongValueUnchecked(obj,value);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * SetFloatField: set an instance field of type float
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the target object
   * @param fieldID the id for the VM_Field that describes this field
   * @param value   float value to assign
   */
  private static void SetFloatField(VM_JNIEnvironment env, int objJREF, int fieldID, float value) {
    if (traceJNI) VM.sysWrite("JNI called: SetFloatField  \n");

    try {
      Object obj =  env.getJNIRef(objJREF);
      VM_Field field = VM_MemberReference.getMemberRef(fieldID).asFieldReference().resolve();
      field.setFloatValueUnchecked(obj,value);    
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }


  /**
   * SetDoubleField: set an instance field of type double
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the target object
   * @param fieldID the id for the VM_Field that describes this field
   * @param value   double value to assign
   */
  private static void SetDoubleField(VM_JNIEnvironment env, int objJREF, int fieldID, double value) {
    if (traceJNI) VM.sysWrite("JNI called: SetDoubleField  \n");

    try {
      Object obj =  env.getJNIRef(objJREF);
      VM_Field field = VM_MemberReference.getMemberRef(fieldID).asFieldReference().resolve();
      field.setDoubleValueUnchecked(obj,value);    
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }


  /**
   * GetStaticMethodID:  return the method ID for invocation later
   * @param envJREF a JREF index for the JNI environment object
   * @param classJREF a JREF index for the class object
   * @param methodNameAddress a raw address to a null-terminated string in C for the method name
   * @param methodSigAddress a raw address to a null-terminated string in C for <DOCUMENTME TODO>
   * @return a method ID or null if it fails
   * @exception NoSuchMethodError if the method is not found
   * @exception ExceptionInInitializerError if the initializer fails
   * @exception OutOfMemoryError if the system runs out of memory
   */
  private static int GetStaticMethodID(VM_JNIEnvironment env, int classJREF, VM_Address methodNameAddress, 
				       VM_Address methodSigAddress) {
    if (traceJNI) VM.sysWrite("JNI called: GetStaticMethodID  \n");

    try {
      // obtain the names as String from the native space
      String methodString = VM_JNIHelpers.createStringFromC(methodNameAddress);
      VM_Atom methodName = VM_Atom.findOrCreateAsciiAtom(methodString);
      String sigString = VM_JNIHelpers.createStringFromC(methodSigAddress);
      VM_Atom sigName  = VM_Atom.findOrCreateAsciiAtom(sigString);

      // get the target class 
      Class jcls = (Class) env.getJNIRef(classJREF);
      VM_Type type = java.lang.JikesRVMSupport.getTypeForClass(jcls);
      if (!type.isClassType()) {
	env.recordException(new NoSuchMethodError());
        return 0;
      }	

      VM_Class klass = type.asClass();
      if (!klass.isInitialized()) {
	VM_Runtime.initializeClassForDynamicLink(klass);
      }

      // Find the target method
      VM_Method meth = klass.findStaticMethod(methodName, sigName);
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
   * @param envJREF a JREF index for the JNI environment object
   * @param classJREF a JREF index for the class object
   * @param methodID id of a VM_MethodReference
   * @return the JREF index for the object returned from the method invocation
   */
  private static int CallStaticObjectMethod(VM_JNIEnvironment env, int classJREF, int methodID) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallStaticObjectMethod  \n");

    try {
      Object returnObj = VM_JNIHelpers.invokeWithDotDotVarArg(methodID, null);
      return env.pushJNIRef(returnObj);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallStaticObjectMethodV:  invoke a static method that returns an object
   * @param envJREF a JREF index for the JNI environment object
   * @param classJREF a JREF index for the class object
   * @param methodID id of a VM_MethodReference
   * @param argAddress a raw address to a variable argument list, each element is 1-word or 2-words
   *                   of the appropriate type for the method invocation
   * @return the JREF index for the object returned from the method invocation
   */
  private static int CallStaticObjectMethodV(VM_JNIEnvironment env, int classJREF, int methodID, 
					     VM_Address argAddress) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallStaticObjectMethodV  \n");

    try {
      Object returnObj = VM_JNIHelpers.invokeWithVarArg(methodID, argAddress, null);    
      return env.pushJNIRef(returnObj);           
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallStaticObjectMethodA:  invoke a static method that returns an object
   * @param envJREF a JREF index for the JNI environment object
   * @param classJREF a JREF index for the class object
   * @param methodID id of a VM_MethodReference
   * @param argAddress a raw address to an array of unions in C, each element is 2-word and hold an argument
   *                   of the appropriate type for the method invocation
   * @return the JREF index for the object returned from the method invocation
   */
  private static int CallStaticObjectMethodA(VM_JNIEnvironment env, int classJREF, int methodID, 
					     VM_Address argAddress) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallStaticObjectMethodA  \n");

    try {
      Object returnObj = VM_JNIHelpers.invokeWithJValue(methodID, argAddress, null);    
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
   * @param envJREF a JREF index for the JNI environment object
   * @param classJREF a JREF index for the class object
   * @param methodID id of a VM_MethodReference
   * @return the boolean value returned from the method invocation
   */
  private static boolean CallStaticBooleanMethod(VM_JNIEnvironment env, int classJREF, int methodID) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallStaticBooleanMethod  \n");

    try {
      Object returnObj = VM_JNIHelpers.invokeWithDotDotVarArg(methodID, VM_TypeReference.Boolean);
      return VM_Reflection.unwrapBoolean(returnObj);     // should be a wrapper for a boolean value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return false;
    }
  }

  /**
   * CallStaticBooleanMethodV:  invoke a static method that returns a boolean value
   * @param envJREF a JREF index for the JNI environment object
   * @param classJREF a JREF index for the class object
   * @param methodID id of a VM_MethodReference
   * @param argAddress a raw address to a variable argument list, each element is 1-word or 2-words
   *                   of the appropriate type for the method invocation
   * @return the boolean value returned from the method invocation
   */
  private static boolean CallStaticBooleanMethodV(VM_JNIEnvironment env, int classJREF, 
						  int methodID, VM_Address argAddress) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallStaticBooleanMethodV  \n");

    try {
      Object returnObj = VM_JNIHelpers.invokeWithVarArg(methodID, argAddress, VM_TypeReference.Boolean);    
      return VM_Reflection.unwrapBoolean(returnObj);     // should be a wrapper for a boolean value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return false;
    }
  }

  /**
   * CallStaticBooleanMethodA:  invoke a static method that returns a boolean value
   * @param envJREF a JREF index for the JNI environment object
   * @param classJREF a JREF index for the class object
   * @param methodID id of a VM_MethodReference
   * @param argAddress a raw address to an array of unions in C, each element is 2-word and hold an argument
   *                   of the appropriate type for the method invocation
   * @return the boolean value returned from the method invocation
   */
  private static boolean CallStaticBooleanMethodA(VM_JNIEnvironment env, int classJREF, 
						  int methodID, VM_Address argAddress) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallStaticBooleanMethodA  \n");

    try {
      Object returnObj = VM_JNIHelpers.invokeWithJValue(methodID, argAddress, VM_TypeReference.Boolean);    
      return VM_Reflection.unwrapBoolean(returnObj);     // should be a wrapper for a boolean value
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
   * @param envJREF a JREF index for the JNI environment object
   * @param classJREF a JREF index for the class object
   * @param methodID id of a VM_MethodReference
   * @return the byte value returned from the method invocation
   */
  private static byte CallStaticByteMethod(VM_JNIEnvironment env, int classJREF, int methodID) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallStaticByteMethod  \n");

    try {
      Object returnObj = VM_JNIHelpers.invokeWithDotDotVarArg(methodID, VM_TypeReference.Byte);
      return VM_Reflection.unwrapByte(returnObj);     // should be a wrapper for a byte value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallStaticByteMethodV:  invoke a static method that returns a byte value
   * @param envJREF a JREF index for the JNI environment object
   * @param classJREF a JREF index for the class object
   * @param methodID id of a VM_MethodReference
   * @param argAddress a raw address to a variable argument list, each element is 1-word or 2-words
   *                   of the appropriate type for the method invocation
   * @return the byte value returned from the method invocation
   */
  private static byte CallStaticByteMethodV(VM_JNIEnvironment env, int classJREF, int methodID, 
					    VM_Address argAddress) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallStaticByteMethodV  \n");

    try {
      Object returnObj = VM_JNIHelpers.invokeWithVarArg(methodID, argAddress, VM_TypeReference.Byte);    
      return VM_Reflection.unwrapByte(returnObj);     // should be a wrapper for a byte value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallStaticByteMethodA:  invoke a static method that returns a byte value
   * @param envJREF a JREF index for the JNI environment object
   * @param classJREF a JREF index for the class object
   * @param methodID id of a VM_MethodReference
   * @param argAddress a raw address to an array of unions in C, each element is 2-word and hold an argument
   *                   of the appropriate type for the method invocation
   * @return the byte value returned from the method invocation
   */
  private static byte CallStaticByteMethodA(VM_JNIEnvironment env, int classJREF, int methodID, 
					    VM_Address argAddress) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallStaticByteMethodA  \n");

    try {
      Object returnObj = VM_JNIHelpers.invokeWithJValue(methodID, argAddress, VM_TypeReference.Byte);    
      return VM_Reflection.unwrapByte(returnObj);     // should be a wrapper for a byte value
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
   * @param envJREF a JREF index for the JNI environment object
   * @param classJREF a JREF index for the class object
   * @param methodID id of a VM_MethodReference
   * @return the char value returned from the method invocation
   */
  private static char CallStaticCharMethod(VM_JNIEnvironment env, int classJREF, int methodID) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallStaticCharMethod  \n");

    try {
      Object returnObj = VM_JNIHelpers.invokeWithDotDotVarArg(methodID, VM_TypeReference.Char);
      return VM_Reflection.unwrapChar(returnObj);     // should be a wrapper for a char value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallStaticCharMethodV:  invoke a static method that returns a char value
   * @param envJREF a JREF index for the JNI environment object
   * @param classJREF a JREF index for the class object
   * @param methodID id of a VM_MethodReference
   * @param argAddress a raw address to a variable argument list, each element is 1-word or 2-words
   *                   of the appropriate type for the method invocation
   * @return the char value returned from the method invocation
   */  
  private static char CallStaticCharMethodV(VM_JNIEnvironment env, int classJREF, int methodID, 
					    VM_Address argAddress) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallStaticCharMethodV  \n");

    try {
      Object returnObj = VM_JNIHelpers.invokeWithVarArg(methodID, argAddress, VM_TypeReference.Char);    
      return VM_Reflection.unwrapChar(returnObj);     // should be a wrapper for a char value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallStaticCharMethodA:  invoke a static method that returns a char value
   * @param envJREF a JREF index for the JNI environment object
   * @param classJREF a JREF index for the class object
   * @param methodID id of a VM_MethodReference
   * @param argAddress a raw address to an array of unions in C, each element is 2-word and hold an argument
   *                   of the appropriate type for the method invocation
   * @return the char value returned from the method invocation
   */
  private static char CallStaticCharMethodA(VM_JNIEnvironment env, int classJREF, int methodID, 
					    VM_Address argAddress) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallStaticCharMethodA  \n");

    try {
      Object returnObj = VM_JNIHelpers.invokeWithJValue(methodID, argAddress, VM_TypeReference.Char);    
      return VM_Reflection.unwrapChar(returnObj);     // should be a wrapper for a char value
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
   * @param envJREF a JREF index for the JNI environment object
   * @param classJREF a JREF index for the class object
   * @param methodID id of a VM_MethodReference
   * @return the short value returned from the method invocation
   */
  private static short CallStaticShortMethod(VM_JNIEnvironment env, int classJREF, int methodID) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallStaticShortMethod  \n");

    try {
      Object returnObj = VM_JNIHelpers.invokeWithDotDotVarArg(methodID, VM_TypeReference.Short);
      return VM_Reflection.unwrapShort(returnObj);     // should be a wrapper for an short value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallStaticShortMethodV:  invoke a static method that returns a short value
   * @param envJREF a JREF index for the JNI environment object
   * @param classJREF a JREF index for the class object
   * @param methodID id of a VM_MethodReference
   * @param argAddress a raw address to a variable argument list, each element is 1-word or 2-words
   *                   of the appropriate type for the method invocation
   * @return the short value returned from the method invocation
   */
  private static short CallStaticShortMethodV(VM_JNIEnvironment env, int classJREF, int methodID, 
					      VM_Address argAddress) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallStaticShortMethodV  \n");

    try {
      Object returnObj = VM_JNIHelpers.invokeWithVarArg(methodID, argAddress, VM_TypeReference.Short);    
      return VM_Reflection.unwrapShort(returnObj);     // should be a wrapper for a short value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallStaticShortMethodA:  invoke a static method that returns a short value
   * @param envJREF a JREF index for the JNI environment object
   * @param classJREF a JREF index for the class object
   * @param methodID id of a VM_MethodReference
   * @param argAddress a raw address to an array of unions in C, each element is 2-word and hold an argument
   *                   of the appropriate type for the method invocation
   * @return the short value returned from the method invocation
   */
  private static short CallStaticShortMethodA(VM_JNIEnvironment env, int classJREF, int methodID, 
					      VM_Address argAddress) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallStaticShortMethodA  \n");
    
    try {
      Object returnObj = VM_JNIHelpers.invokeWithJValue(methodID, argAddress, VM_TypeReference.Short);    
      return VM_Reflection.unwrapShort(returnObj);     // should be a wrapper for a short value
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
   * @param envJREF a JREF index for the JNI environment object
   * @param classJREF a JREF index for the class object
   * @param methodID id of a VM_MethodReference
   * @return the integer value returned from the method invocation
   */
  private static int CallStaticIntMethod(VM_JNIEnvironment env, int classJREF, int methodID) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallStaticIntMethod  \n");

    try {
      Object returnObj = VM_JNIHelpers.invokeWithDotDotVarArg(methodID, VM_TypeReference.Int);
      return VM_Reflection.unwrapInt(returnObj);     // should be a wrapper for an integer value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallStaticIntMethodV:  invoke a static method that returns an integer value
   * @param envJREF a JREF index for the JNI environment object
   * @param classJREF a JREF index for the class object
   * @param methodID id of a VM_MethodReference
   * @param argAddress a raw address to a variable argument list, each element is 1-word or 2-words
   *                   of the appropriate type for the method invocation
   * @return the integer value returned from the method invocation
   */
  private static int CallStaticIntMethodV(VM_JNIEnvironment env, int classJREF, int methodID, 
					  VM_Address argAddress) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallStaticIntMethodV  \n");

    try {
      Object returnObj = VM_JNIHelpers.invokeWithVarArg(methodID, argAddress, VM_TypeReference.Int);    
      return VM_Reflection.unwrapInt(returnObj);     // should be a wrapper for an integer value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallStaticIntMethodA:  invoke a static method that returns an integer value
   * @param envJREF a JREF index for the JNI environment object
   * @param classJREF a JREF index for the class object
   * @param methodID id of a VM_MethodReference
   * @param argAddress a raw address to an array of unions in C, each element is 2-word and hold an argument
   *                   of the appropriate type for the method invocation
   * @return the integer value returned from the method invocation
   */
  private static int CallStaticIntMethodA(VM_JNIEnvironment env, int classJREF, int methodID, 
					  VM_Address argAddress) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallStaticIntMethodA  \n");

    try {
      Object returnObj = VM_JNIHelpers.invokeWithJValue(methodID, argAddress, VM_TypeReference.Int);    
      return VM_Reflection.unwrapInt(returnObj);     // should be a wrapper for an integer value
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
   * @param envJREF a JREF index for the JNI environment object
   * @param classJREF a JREF index for the class object
   * @param methodID id of a VM_MethodReference
   * @return the long value returned from the method invocation
   */
  private static long CallStaticLongMethod(VM_JNIEnvironment env, int classJREF, int methodID) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallStaticLongMethod  \n");

    try {
      Object returnObj = VM_JNIHelpers.invokeWithDotDotVarArg(methodID, VM_TypeReference.Long);
      return VM_Reflection.unwrapLong(returnObj);     // should be a wrapper for a long value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0L;
    }
  }

  /**
   * CallStaticLongMethodV:  invoke a static method that returns a long value
   * @param envJREF a JREF index for the JNI environment object
   * @param classJREF a JREF index for the class object
   * @param methodID id of a VM_MethodReference
   * @param argAddress a raw address to a variable argument list, each element is 1-word or 2-words
   *                   of the appropriate type for the method invocation
   * @return the long value returned from the method invocation
   */
  private static long CallStaticLongMethodV(VM_JNIEnvironment env, int classJREF, int methodID, 
					    VM_Address argAddress) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallStaticLongMethodV  \n");

    try {
      Object returnObj = VM_JNIHelpers.invokeWithVarArg(methodID, argAddress, VM_TypeReference.Long);    
      return VM_Reflection.unwrapLong(returnObj);     // should be a wrapper for a long value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0L;
    }
  }

  /**
   * CallStaticLongMethodA:  invoke a static method that returns a long value
   * @param envJREF a JREF index for the JNI environment object
   * @param classJREF a JREF index for the class object
   * @param methodID id of a VM_MethodReference
   * @param argAddress a raw address to an array of unions in C, each element is 2-word and hold an argument
   *                   of the appropriate type for the method invocation
   * @return the long value returned from the method invocation
   */
  private static long CallStaticLongMethodA(VM_JNIEnvironment env, int classJREF, int methodID, 
					    VM_Address argAddress) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallStaticLongMethodA  \n");

    try {
      Object returnObj = VM_JNIHelpers.invokeWithJValue(methodID, argAddress, VM_TypeReference.Long);    
      return VM_Reflection.unwrapLong(returnObj);     // should be a wrapper for a long value
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
   * @param envJREF a JREF index for the JNI environment object
   * @param classJREF a JREF index for the class object
   * @param methodID id of a VM_MethodReference
   * @return the float value returned from the method invocation
   */
  private static float CallStaticFloatMethod(VM_JNIEnvironment env, int classJREF, int methodID) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallStaticFloatMethod  \n");

    try {
      Object returnObj = VM_JNIHelpers.invokeWithDotDotVarArg(methodID, VM_TypeReference.Float);
      return VM_Reflection.unwrapFloat(returnObj);     // should be a wrapper for a float value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0f;
    }
  }

  /**
   * CallStaticFloatMethodV:  invoke a static method that returns a float value
   * @param envJREF a JREF index for the JNI environment object
   * @param classJREF a JREF index for the class object
   * @param methodID id of a VM_MethodReference
   * @param argAddress a raw address to a variable argument list, each element is 1-word or 2-words
   *                   of the appropriate type for the method invocation
   * @return the float value returned from the method invocation
   */
  private static float CallStaticFloatMethodV(VM_JNIEnvironment env, int classJREF, int methodID, 
					      VM_Address argAddress) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallStaticFloatMethodV  \n");

    try {
      Object returnObj = VM_JNIHelpers.invokeWithVarArg(methodID, argAddress, VM_TypeReference.Float);    
      return VM_Reflection.unwrapFloat(returnObj);     // should be a wrapper for a float value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0f;
    }
  }

  /**
   * CallStaticFloatMethodA:  invoke a static method that returns a float value
   * @param envJREF a JREF index for the JNI environment object
   * @param classJREF a JREF index for the class object
   * @param methodID id of a VM_MethodReference
   * @param argAddress a raw address to an array of unions in C, each element is 2-word and hold an argument
   *                   of the appropriate type for the method invocation
   * @return the float value returned from the method invocation
   */
  private static float CallStaticFloatMethodA(VM_JNIEnvironment env, int classJREF, int methodID, 
					      VM_Address argAddress) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallStaticFloatMethodA  \n");

    try {
      Object returnObj = VM_JNIHelpers.invokeWithJValue(methodID, argAddress, VM_TypeReference.Float);    
      return VM_Reflection.unwrapFloat(returnObj);     // should be a wrapper for a float value
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
   * @param envJREF a JREF index for the JNI environment object
   * @param classJREF a JREF index for the class object
   * @param methodID an id of a VM_MethodReference
   * @return the double value returned from the method invocation
   */
  private static double CallStaticDoubleMethod(VM_JNIEnvironment env, int classJREF, int methodID) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallStaticDoubleMethod  \n");

    try {
      Object returnObj = VM_JNIHelpers.invokeWithDotDotVarArg(methodID, VM_TypeReference.Double);
      return VM_Reflection.unwrapDouble(returnObj);     // should be a wrapper for a double value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallStaticDoubleMethodV:  invoke a static method that returns a double value
   * @param envJREF a JREF index for the JNI environment object
   * @param classJREF a JREF index for the class object
   * @param methodID an id of a VM_MethodReference
   * @param argAddress a raw address to a  variable argument list, each element is 1-word or 2-words
   *                   of the appropriate type for the method invocation
   * @return the double value returned from the method invocation
   */
  private static double CallStaticDoubleMethodV(VM_JNIEnvironment env, int classJREF, int methodID, 
						VM_Address argAddress) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallStaticDoubleMethodV  \n");

    try {
      Object returnObj = VM_JNIHelpers.invokeWithVarArg(methodID, argAddress, VM_TypeReference.Double);    
      return VM_Reflection.unwrapDouble(returnObj);     // should be a wrapper for a double value
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * CallStaticDoubleMethodA:  invoke a static method that returns a double value
   * @param envJREF a JREF index for the JNI environment object
   * @param classJREF a JREF index for the class object
   * @param methodID id of a VM_MethodReference
   * @param argAddress a raw address to an array of unions in C, each element is 2-word and hold an argument
   *                   of the appropriate type for the method invocation
   * @return the double value returned from the method invocation
   */
  private static double CallStaticDoubleMethodA(VM_JNIEnvironment env, int classJREF, int methodID, 
						VM_Address argAddress) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallStaticDoubleMethodA  \n");

    try {
      Object returnObj = VM_JNIHelpers.invokeWithJValue(methodID, argAddress, VM_TypeReference.Double);    
      return VM_Reflection.unwrapDouble(returnObj);     // should be a wrapper for a double value
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
   * @param envJREF a JREF index for the JNI environment object
   * @param classJREF a JREF index for the class object
   * @param methodID id of a VM_MethodReference
   */
  private static void CallStaticVoidMethod(VM_JNIEnvironment env, int classJREF, int methodID) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallStaticVoidMethod  \n");

    try {
      VM_JNIHelpers.invokeWithDotDotVarArg(methodID, VM_TypeReference.Void);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * CallStaticVoidMethodA:  invoke a static method that returns void
   * @param envJREF a JREF index for the JNI environment object
   * @param classJREF a JREF index for the class object
   * @param methodID id of a VM_MethodReference
   * @param argAddress a raw address to an array of unions in C, each element is 2-word and hold an argument
   *                   of the appropriate type for the method invocation
   */
  private static void CallStaticVoidMethodV(VM_JNIEnvironment env, int classJREF, int methodID, 
					    VM_Address argAddress) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallStaticVoidMethodV  \n");

    try {
      VM_JNIHelpers.invokeWithVarArg(methodID, argAddress, VM_TypeReference.Void);    
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * CallStaticVoidMethodA:  invoke a static method that returns void
   * @param envJREF a JREF index for the JNI environment object
   * @param classJREF a JREF index for the class object
   * @param methodID id of a VM_MethodReference
   * @param argAddress a raw address to an array of unions in C, each element is 2-word and hold an argument
   *                   of the appropriate type for the method invocation
   */
  private static void CallStaticVoidMethodA(VM_JNIEnvironment env, int classJREF, int methodID, 
					    VM_Address argAddress) throws Exception {
    if (traceJNI) VM.sysWrite("JNI called: CallStaticVoidMethodA  \n");

    try {
      VM_JNIHelpers.invokeWithJValue(methodID, argAddress, VM_TypeReference.Void);    
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * GetStaticFieldID:  return a field id which can be cached in native code and reused
   * @param envJREF a JREF index for the JNI environment object
   * @param classJREF a JREF index for the VM_Class object
   * @param fieldNameAddress a raw address to a null-terminated string in C for the field name
   * @param descriptorAddress a raw address to a null-terminated string in C for the descriptor
   * @return the offset of a static field given the class, field name 
   *         and type. Return 0 if the field is not found
   * @exception NoSuchFieldError if the specified field cannot be found
   * @exception ExceptionInInitializerError if the class initializer fails
   * @exception OutOfMemoryError if the system runs out of memory
   */
  private static int GetStaticFieldID(VM_JNIEnvironment env, int classJREF, 
                                      VM_Address fieldNameAddress, VM_Address descriptorAddress) {
    if (traceJNI) VM.sysWrite("JNI called: GetStaticFieldID  \n");
    
    try {
      Class cls = (Class) env.getJNIRef(classJREF);

      String fieldString = VM_JNIHelpers.createStringFromC(fieldNameAddress);
      VM_Atom fieldName = VM_Atom.findOrCreateAsciiAtom(fieldString);
      String descriptorString = VM_JNIHelpers.createStringFromC(descriptorAddress);
      VM_Atom descriptor = VM_Atom.findOrCreateAsciiAtom(descriptorString);

      // list of all instance fields including superclasses
      VM_Field[] fields = java.lang.JikesRVMSupport.getTypeForClass(cls).getStaticFields();
      for (int i = 0; i < fields.length; ++i) {
        VM_Field field = fields[i];
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
   * @param envJREF a JREF index for the JNI environment object
   * @param classJREF a JREF index for the VM_Class object
   * @param fieldID the id for the VM_Field that describes this field
   * @return the value of the Object field, converted to a JREF index
   *         or 0 if the fieldID is incorrect
   */
  private static int GetStaticObjectField(VM_JNIEnvironment env, int classJREF, int fieldID) {
    if (traceJNI) VM.sysWrite("JNI called: GetStaticObjectField  \n");

    try {
      VM_Field field = VM_MemberReference.getMemberRef(fieldID).asFieldReference().resolve();
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
   * @param envJREF a JREF index for the JNI environment object
   * @param classJREF a JREF index for the VM_Class object
   * @param fieldID the id for the VM_Field that describes this field
   * @return the value of the boolean field, or 0 if the fieldID is incorrect
   */
  private static int GetStaticBooleanField(VM_JNIEnvironment env, int classJREF, int fieldID) {
    if (traceJNI) VM.sysWrite("JNI called: GetStaticBooleanField  \n");

    try {
      VM_Field field = VM_MemberReference.getMemberRef(fieldID).asFieldReference().resolve();
      return field.getBooleanValueUnchecked(null) ? 1 : 0;
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * GetStaticByteField:  read a static field of type byte
   * @param envJREF a JREF index for the JNI environment object
   * @param classJREF a JREF index for the VM_Class object
   * @param fieldID the id for the VM_Field that describes this field
   * @return the value of the byte field, or 0 if the fieldID is incorrect
   */
  private static int GetStaticByteField(VM_JNIEnvironment env, int classJREF, int fieldID) {
    if (traceJNI) VM.sysWrite("JNI called: GetStaticByteField  \n");

    try {
      VM_Field field = VM_MemberReference.getMemberRef(fieldID).asFieldReference().resolve();
      return field.getByteValueUnchecked(null);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * GetStaticCharField:  read a static field of type character
   * @param envJREF a JREF index for the JNI environment object
   * @param classJREF a JREF index for the VM_Class object
   * @param fieldID the id for the VM_Field that describes this field
   * @return the value of the character field, or 0 if the fieldID is incorrect
   */
  private static int GetStaticCharField(VM_JNIEnvironment env, int classJREF, int fieldID) {
    if (traceJNI) VM.sysWrite("JNI called: GetStaticCharField  \n");

    try {
      VM_Field field = VM_MemberReference.getMemberRef(fieldID).asFieldReference().resolve();
      return field.getCharValueUnchecked(null);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * GetStaticShortField:  read a static field of type short
   * @param envJREF a JREF index for the JNI environment object
   * @param classJREF a JREF index for the VM_Class object
   * @param fieldID the id for the VM_Field that describes this field
   * @return the value of the short field, or 0 if the fieldID is incorrect
   */
  private static int GetStaticShortField(VM_JNIEnvironment env, int classJREF, int fieldID) {
    if (traceJNI) VM.sysWrite("JNI called: GetStaticShortField  \n");

    try {
      VM_Field field = VM_MemberReference.getMemberRef(fieldID).asFieldReference().resolve();
      return field.getShortValueUnchecked(null);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * GetStaticIntField:  read a static field of type integer
   * @param envJREF a JREF index for the JNI environment object
   * @param classJREF a JREF index for the VM_Class object
   * @param fieldID the id for the VM_Field that describes this field
   * @return the value of the integer field, or 0 if the fieldID is incorrect
   */
  private static int GetStaticIntField(VM_JNIEnvironment env, int classJREF, int fieldID) {
    if (traceJNI) VM.sysWrite("JNI called: GetStaticIntField  \n");

    try {
      VM_Field field = VM_MemberReference.getMemberRef(fieldID).asFieldReference().resolve();
      return field.getIntValueUnchecked(null);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * GetStaticLongField:  read a static field of type long
   * @param envJREF a JREF index for the JNI environment object
   * @param classJREF a JREF index for the VM_Class object
   * @param fieldID the id for the VM_Field that describes this field
   * @return the value of the long field or 0 if the fieldID is incorrect
   */
  private static long GetStaticLongField(VM_JNIEnvironment env, int classJREF, int fieldID) {
    if (traceJNI) VM.sysWrite("JNI called: GetStaticLongField  \n");

    try {
      VM_Field field = VM_MemberReference.getMemberRef(fieldID).asFieldReference().resolve();
      return field.getLongValueUnchecked(null);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * GetStaticFloatField:  read a static field of type float
   * @param envJREF a JREF index for the JNI environment object
   * @param classJREF a JREF index for the VM_Class object
   * @param fieldID the id for the VM_Field that describes this field
   * @return the value of the float field or 0 if the fieldID is incorrect
   */
  private static float GetStaticFloatField(VM_JNIEnvironment env, int classJREF, int fieldID) {
    if (traceJNI) VM.sysWrite("JNI called: GetStaticFloatField  \n");

    try {
      VM_Field field = VM_MemberReference.getMemberRef(fieldID).asFieldReference().resolve();
      return field.getFloatValueUnchecked(null);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * GetStaticDoubleField:  read a static field of type double
   * @param envJREF a JREF index for the JNI environment object
   * @param classJREF a JREF index for the VM_Class object
   * @param fieldID the id for the VM_Field that describes this field
   * @return the value of the double field or 0 if the fieldID is incorrect
   */
  private static double GetStaticDoubleField(VM_JNIEnvironment env, int classJREF, int fieldID) {
    if (traceJNI) VM.sysWrite("JNI called: GetStaticDoubleField  \n");

    try {
      VM_Field field = VM_MemberReference.getMemberRef(fieldID).asFieldReference().resolve();
      return field.getDoubleValueUnchecked(null);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * SetStaticObjectField:  set a static field of type Object
   * @param envJREF a JREF index for the JNI environment object
   * @param classJREF a JREF index for the VM_Class object
   * @param fieldID the id for the VM_Field that describes this field
   * @param value to assign
   */
  private static void SetStaticObjectField(VM_JNIEnvironment env, int classJREF, int fieldID, int objectJREF) {
    if (traceJNI) VM.sysWrite("JNI called: SetStaticObjectField  \n");

    try {
      VM_Field field = VM_MemberReference.getMemberRef(fieldID).asFieldReference().resolve();
      Object ref = env.getJNIRef(objectJREF);      
      field.setObjectValueUnchecked(null, ref);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * SetStaticBooleanField:  set a static field of type boolean
   * @param envJREF a JREF index for the JNI environment object
   * @param classJREF a JREF index for the VM_Class object
   * @param fieldID the id for the VM_Field that describes this field
   * @param value to assign
   */
  private static void SetStaticBooleanField(VM_JNIEnvironment env, int classJREF, int fieldID, boolean fieldValue) {
    if (traceJNI) VM.sysWrite("JNI called: SetStaticBooleanField  \n");

    try {
      VM_Field field = VM_MemberReference.getMemberRef(fieldID).asFieldReference().resolve();
      field.setBooleanValueUnchecked(null, fieldValue);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * SetStaticByteField:  set a static field of type byte
   * @param envJREF a JREF index for the JNI environment object
   * @param classJREF a JREF index for the VM_Class object
   * @param fieldID the id for the VM_Field that describes this field
   * @param value to assign
   */
  private static void SetStaticByteField(VM_JNIEnvironment env, int classJREF, int fieldID, byte fieldValue) {
    if (traceJNI) VM.sysWrite("JNI called: SetStaticByteField  \n");

    try {
      VM_Field field = VM_MemberReference.getMemberRef(fieldID).asFieldReference().resolve();
      field.setByteValueUnchecked(null, fieldValue);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * SetStaticCharField:  set a static field of type char
   * @param envJREF a JREF index for the JNI environment object
   * @param classJREF a JREF index for the VM_Class object
   * @param fieldID the id for the VM_Field that describes this field
   * @param value to assign
   */
  private static void SetStaticCharField(VM_JNIEnvironment env, int classJREF, int fieldID, char fieldValue) {
    if (traceJNI) VM.sysWrite("JNI called: SetStaticCharField  \n");

    try {
      VM_Field field = VM_MemberReference.getMemberRef(fieldID).asFieldReference().resolve();
      field.setCharValueUnchecked(null, fieldValue);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * SetStaticShortField:  set a static field of type short
   * @param envJREF a JREF index for the JNI environment object
   * @param classJREF a JREF index for the VM_Class object
   * @param fieldID the id for the VM_Field that describes this field
   * @param value to assign
   */
  private static void SetStaticShortField(VM_JNIEnvironment env, int classJREF, int fieldID, short fieldValue) {
    if (traceJNI) VM.sysWrite("JNI called: SetStaticShortField  \n");

    try {
      VM_Field field = VM_MemberReference.getMemberRef(fieldID).asFieldReference().resolve();
      field.setShortValueUnchecked(null, fieldValue);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * SetStaticIntField:  set a static field of type integer
   * @param envJREF a JREF index for the JNI environment object
   * @param classJREF a JREF index for the VM_Class object
   * @param fieldID the id for the VM_Field that describes this field
   * @param value to assign
   */
  private static void SetStaticIntField(VM_JNIEnvironment env, int classJREF, int fieldID, int fieldValue) {
    if (traceJNI) VM.sysWrite("JNI called: SetStaticIntField  \n");

    try {
      VM_Field field = VM_MemberReference.getMemberRef(fieldID).asFieldReference().resolve();
      field.setIntValueUnchecked(null, fieldValue);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * SetStaticLongField:  set a static field of type long
   * @param envJREF a JREF index for the JNI environment object
   * @param classJREF a JREF index for the VM_Class object
   * @param fieldID the id for the VM_Field that describes this field
   * @param value to assign
   */
  private static void SetStaticLongField(VM_JNIEnvironment env, int classJREF, int fieldID, long fieldValue) {
    if (traceJNI) VM.sysWrite("JNI called: SetStaticLongField  \n");

    try {
      VM_Field field = VM_MemberReference.getMemberRef(fieldID).asFieldReference().resolve();
      field.setLongValueUnchecked(null, fieldValue);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * SetStaticFloatField:  set a static field of type float
   * @param envJREF a JREF index for the JNI environment object
   * @param classJREF a JREF index for the VM_Class object
   * @param fieldID the id for the VM_Field that describes this field
   * @param value to assign
   */
  private static void SetStaticFloatField(VM_JNIEnvironment env, int classJREF, int fieldID, float fieldValue) {
    if (traceJNI) VM.sysWrite("JNI called: SetStaticFloatField  \n");

    try {
      VM_Field field = VM_MemberReference.getMemberRef(fieldID).asFieldReference().resolve();
      field.setFloatValueUnchecked(null, fieldValue);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * SetStaticDoubleField:  set a static field of type float
   * @param envJREF a JREF index for the JNI environment object
   * @param classJREF a JREF index for the VM_Class object
   * @param fieldID the id for the VM_Field that describes this field
   * @param value to assign
   */
  private static void SetStaticDoubleField(VM_JNIEnvironment env, int classJREF, int fieldID, double fieldValue) {
    if (traceJNI) VM.sysWrite("JNI called: SetStaticDoubleField  \n");

    try {
      VM_Field field = VM_MemberReference.getMemberRef(fieldID).asFieldReference().resolve();
      field.setDoubleValueUnchecked(null, fieldValue);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * NewString: create a String Object from C array of unicode chars
   * @param envJREF a JREF index for the JNI environment object
   * @param uchars address of C array of 16 bit unicode characters
   * @param len the number of chars in the C array
   * @return the allocated String Object, converted to a JREF index
   *         or 0 if an OutOfMemoryError Exception has been thrown
   * @exception OutOfMemoryError
   */
  private static int NewString(VM_JNIEnvironment env, VM_Address uchars, int len) {
    if (traceJNI) VM.sysWrite("JNI called: NewString  \n");

    try {
      char[] contents = new char[len];
      VM_Memory.memcopy(VM_Magic.objectAsAddress(contents), uchars, len*2);
      String s = new String(contents);

      if (s!=null) {
        return env.pushJNIRef(s);
      } else {
        env.recordException(new OutOfMemoryError());
        return 0;
      }
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * GetStringLength:  return the length of a String
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the String object
   * @return the length of the String
   */
  private static int GetStringLength(VM_JNIEnvironment env, int objJREF) {
    if (traceJNI) VM.sysWrite("JNI called: GetStringLength  \n");

    try {
      String str =  (String) env.getJNIRef(objJREF);
      return str.length();
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * GetStringChars:  return address of buffer containing contents of a String
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the String object
   * @param isCopyAddress address of isCopy jboolean (an int)
   * @return address of a copy of the String unicode characters
   *         and *isCopy is set to 1 (TRUE)
   * @exception OutOfMemoryError if the system runs out of memory
   */
  private static VM_Address GetStringChars(VM_JNIEnvironment env, int objJREF, VM_Address isCopyAddress) {
    if (traceJNI) VM.sysWrite("JNI called: GetStringChars  \n");

    try {
      String str =  (String) env.getJNIRef(objJREF);
      int len = str.length();
      char[] contents = str.toCharArray();

      // alloc non moving buffer in C heap for a copy of string contents
      VM_Address copyBuffer = VM_SysCall.sysMalloc(len*2);
      if(copyBuffer.isZero()) {
        env.recordException(new OutOfMemoryError());
        return VM_Address.zero();
      }
      VM_Memory.memcopy(copyBuffer, VM_Magic.objectAsAddress(contents), len*2);

      // set callers isCopy boolean to true, if it's a valid address
      if (!isCopyAddress.isZero()) {
        int temp = VM_Magic.getMemoryInt(isCopyAddress);
        if (VM.LittleEndian) {
          VM_Magic.setMemoryInt(isCopyAddress, ((temp & 0xffffff00) | 0x00000001));
        } else {
          VM_Magic.setMemoryInt(isCopyAddress, ((temp & 0x00ffffff) | 0x01000000));
        }
      }

      return copyBuffer;
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return VM_Address.zero();
    }
  }

  /**
   * ReleaseStringChars:  release buffer obtained via GetStringChars
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the String object
   * @param bufAddress address of buffer to release
   * @return void
   */
  private static void ReleaseStringChars(VM_JNIEnvironment env, int objJREF, VM_Address bufAddress) {
    if (traceJNI) VM.sysWrite("JNI called: ReleaseStringChars  \n");

    try {
      VM_SysCall.sysFree(bufAddress);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return;
    }
  }


  /**
   * NewString: create a String Object from C array of utf8 bytes
   * @param envJREF a JREF index for the JNI environment object
   * @param utf8bytes address of C array of 8 bit utf8 bytes
   * @return the allocated String Object, converted to a JREF index
   *         or 0 if an OutOfMemoryError Exception has been thrown
   * @exception OutOfMemoryError if the system runs out of memory
   */
  private static int NewStringUTF(VM_JNIEnvironment env, VM_Address utf8bytes) {
    if (traceJNI) VM.sysWrite("JNI called: NewStringUTF  \n");

    try {
      String returnString = null;
      byte[] utf8array = VM_JNIHelpers.createByteArrayFromC(utf8bytes);

      try {
        returnString = VM_UTF8Convert.fromUTF8(utf8array);
      } catch (UTFDataFormatException e) {
        VM.sysWrite("UTFDataFormatException caught in VM_JNIFunctions.NewStringUTF\n");
      }

      if (returnString!=null) {
        return env.pushJNIRef(returnString);
      } else {
        env.recordException(new OutOfMemoryError());
        return 0;
      }
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * GetStringUTFLength: return number of bytes to represent a String in UTF8 format
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the String object
   * @return number of bytes to represent in UTF8 format
   */
  private static int GetStringUTFLength(VM_JNIEnvironment env, int objJREF) {
    if (traceJNI) VM.sysWrite("JNI called: GetStringUTFLength  \n");

    try {
      String str =  (String) env.getJNIRef(objJREF);
      return VM_UTF8Convert.utfLength(str);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * GetStringUTFChars:  return address of buffer containing contents of a String
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the String object
   * @param isCopyAddress address of isCopy jboolean (an int)
   * @return address of a copy of the String unicode characters
   *         and *isCopy is set to 1 (TRUE)
   * @exception OutOfMemoryError if the system runs out of memory
   */
  private static VM_Address GetStringUTFChars(VM_JNIEnvironment env, int objJREF, VM_Address isCopyAddress) {
    if (traceJNI) VM.sysWrite("JNI called: GetStringUTFChars  \n");

    try {
      String str =  (String) env.getJNIRef(objJREF);
      byte[] utfcontents = VM_UTF8Convert.toUTF8(str);

      int len = utfcontents.length;
      int copyBufferLen = VM_Memory.alignDown(len + BYTES_IN_ADDRESS, BYTES_IN_ADDRESS ); // need extra at end for storing terminator and end at word boundary 

      // alloc non moving buffer in C heap for string contents as utf8 array
      // alloc extra byte for C null terminator
      VM_Address copyBuffer = VM_SysCall.sysMalloc(copyBufferLen);

      if(copyBuffer.isZero()) {
        env.recordException(new OutOfMemoryError());
        return VM_Address.zero();
      }

      // store word of 0 at end, before the copy, to set C null terminator
      VM_Magic.setMemoryWord(copyBuffer.add(copyBufferLen - BYTES_IN_ADDRESS), VM_Word.zero());
      VM_Memory.memcopy(copyBuffer, VM_Magic.objectAsAddress(utfcontents), len);

      // set callers isCopy boolean to true, if it's a valid address
      if (!isCopyAddress.isZero()) {
        int temp = VM_Magic.getMemoryInt(isCopyAddress);
        if (VM.LittleEndian) {
          VM_Magic.setMemoryInt(isCopyAddress, ((temp & 0xffffff00) | 0x00000001));
        } else {
          VM_Magic.setMemoryInt(isCopyAddress, ((temp & 0x00ffffff) | 0x01000000));
        }
      }

      return copyBuffer;
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return VM_Address.zero();
    }
  }

  /**
   * ReleaseStringUTFChars:  release buffer obtained via GetStringUTFChars
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the String object
   * @param bufAddress address of buffer to release
   * @return void
   */
  private static void ReleaseStringUTFChars(VM_JNIEnvironment env, int objJREF, VM_Address bufAddress) {
    if (traceJNI) VM.sysWrite("JNI called: ReleaseStringUTFChars  \n");

    try {
      VM_SysCall.sysFree(bufAddress);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * GetArrayLength: return array length
   * @param envJREF a JREF index for the JNI environment object
   * @param arrayJREF a JREF index for the source array 
   * @return the array length, or -1 if it's not an array
   */
  private static int GetArrayLength(VM_JNIEnvironment env, int arrayJREF) {
    if (traceJNI) VM.sysWrite("JNI called: GetArrayLength  \n");

    try {
      Object theArray = env.getJNIRef(arrayJREF);
      VM_Type arrayType = VM_Magic.getObjectType(theArray);
      return arrayType.isArrayType() ? VM_Magic.getArrayLength(theArray) : -1;
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return -1;
    }
  }

  /**
   * NewObjectArray: create a new Object array
   * @param envJREF a JREF index for the JNI environment object
   * @param length the size of the new array
   * @param classJREF a JREF index for the class of the element
   * @param initElementJREF a JREF index for the value to initialize the array elements
   * @return the new Object array initialized
   * @exception OutOfMemoryError if the system runs out of memory
   */
  private static int NewObjectArray(VM_JNIEnvironment env, int length, int classJREF, int initElementJREF) {
    if (traceJNI) VM.sysWrite("JNI called: NewObjectArray  \n");

    try {
      Object initElement = env.getJNIRef(initElementJREF);
      Class cls = (Class) env.getJNIRef(classJREF);

      if(cls == null)
	throw new NullPointerException();
      if(length < 0)
	throw new NegativeArraySizeException();

      VM_Array arrayType = java.lang.JikesRVMSupport.getTypeForClass(cls).getArrayTypeForElementType();
      if (!arrayType.isInitialized()) {
	arrayType.resolve();
	arrayType.instantiate();
	arrayType.initialize();
      }
      Object newArray[] = (Object []) VM_Runtime.resolvedNewArray(length, arrayType);

      if (initElement != null) {
	for (int i=0; i<length; i++) {
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
   * @param envJREF a JREF index for the JNI environment object
   * @param arrayJREF a JREF index for the source array 
   * @param index the index for the targeted element
   * @return the object at the specified index
   * @exception ArrayIndexOutOfBoundsException if the index is out of range
   */
  private static int GetObjectArrayElement(VM_JNIEnvironment env, int arrayJREF, int index) {
    if (traceJNI) VM.sysWrite("JNI called: GetObjectArrayElement  \n");

    try {
      Object sourceArray[] = (Object []) env.getJNIRef(arrayJREF);

      if (sourceArray==null)
        return 0;

      VM_Array arrayType = VM_Magic.getObjectType(sourceArray).asArray();
      if (arrayType.getElementType().isPrimitiveType())
        return 0;

      if (index >= VM_Magic.getArrayLength(sourceArray)) {
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
   * @param envJREF a JREF index for the JNI environment object
   * @param arrayJREF a JREF index for the source array 
   * @param index the index for the targeted element
   * @param objectJREF a JREF index for the object to store into the array
   * @exception ArrayStoreException if the element types do not match
   *            ArrayIndexOutOfBoundsException if the index is out of range
   */
  private static void SetObjectArrayElement(VM_JNIEnvironment env, int arrayJREF, int index,
                                            int objectJREF) {
    if (traceJNI) VM.sysWrite("JNI called: SetObjectArrayElement  \n");

    try {
      Object sourceArray[] = (Object []) env.getJNIRef(arrayJREF);
      Object elem = env.getJNIRef(objectJREF);
      sourceArray[index] = elem;
    } catch (Throwable e) {
      env.recordException(e);
    }
  }
  
  /**
   * NewBooleanArray: create a new boolean array
   * @param envJREF a JREF index for the JNI environment object
   * @param length the size of the new array
   * @return the new boolean array
   * @exception OutOfMemoryError if the system runs out of memory   
   */
  private static int NewBooleanArray(VM_JNIEnvironment env, int length) {
    if (traceJNI) VM.sysWrite("JNI called: NewBooleanArray  \n");

    try {
      boolean newArray[] = new boolean[length];
      return env.pushJNIRef(newArray);  
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * NewByteArray: create a new byte array
   * @param envJREF a JREF index for the JNI environment object
   * @param length the size of the new array
   * @return the new byte array
   * @exception OutOfMemoryError if the system runs out of memory   
   */
  private static int NewByteArray(VM_JNIEnvironment env, int length) {
    if (traceJNI) VM.sysWrite("JNI called: NewByteArray  \n");

    try {
      byte newArray[] = new byte[length];
      return env.pushJNIRef(newArray);  
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * NewCharArray: create a new char array
   * @param envJREF a JREF index for the JNI environment object
   * @param length the size of the new array
   * @return the new char array
   * @exception OutOfMemoryError if the system runs out of memory   
   */
  private static int NewCharArray(VM_JNIEnvironment env, int length) {
    if (traceJNI) VM.sysWrite("JNI called: NewCharArray  \n");

    try {
      char newArray[] = new char[length];
      return env.pushJNIRef(newArray);  
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * NewShortArray: create a new short array
   * @param envJREF a JREF index for the JNI environment object
   * @param length the size of the new array
   * @return the new short array
   * @exception OutOfMemoryError if the system runs out of memory   
   */
  private static int NewShortArray(VM_JNIEnvironment env, int length) {
    if (traceJNI) VM.sysWrite("JNI called: NewShortArray  \n");

    try {
      short newArray[] = new short[length];
      return env.pushJNIRef(newArray);  
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * NewIntArray: create a new integer array
   * @param envJREF a JREF index for the JNI environment object
   * @param length the size of the new array
   * @return the new integer array
   * @exception OutOfMemoryError if the system runs out of memory   
   */
  private static int NewIntArray(VM_JNIEnvironment env, int length) {
    if (traceJNI) VM.sysWrite("JNI called: NewIntArray  \n");

    try {
      int newArray[] = new int[length];
      return env.pushJNIRef(newArray);  
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * NewLongArray: create a new long array
   * @param envJREF a JREF index for the JNI environment object
   * @param length the size of the new array
   * @return the new long array
   * @exception OutOfMemoryError if the system runs out of memory   
   */
  private static int NewLongArray(VM_JNIEnvironment env, int length) {
    if (traceJNI) VM.sysWrite("JNI called: NewLongArray  \n");

    try {
      long newArray[] = new long[length];
      return env.pushJNIRef(newArray);  
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * NewFloatArray: create a new float array
   * @param envJREF a JREF index for the JNI environment object
   * @param length the size of the new array
   * @return the new float array
   * @exception OutOfMemoryError if the system runs out of memory   
   */
  private static int NewFloatArray(VM_JNIEnvironment env, int length) {
    if (traceJNI) VM.sysWrite("JNI called: NewFloatArray  \n");

    try {
      float newArray[] = new float[length];
      return env.pushJNIRef(newArray);  
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * NewDoubleArray: create a new double array
   * @param envJREF a JREF index for the JNI environment object
   * @param length the size of the new array
   * @return the new long array
   * @exception OutOfMemoryError if the system runs out of memory   
   */
  private static int NewDoubleArray(VM_JNIEnvironment env, int length) {
    if (traceJNI) VM.sysWrite("JNI called: NewDoubleArray  \n");

    try {
      double newArray[] = new double[length];
      return env.pushJNIRef(newArray);  
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * GetBooleanArrayElements: get all the elements of a boolean array
   * @param envJREF a JREF index for the JNI environment object
   * @param arrayJREF a JREF index for the source array 
   * @param isCopyAddress address of a flag to indicate whether the returned array is a copy or a direct pointer
   * @return A pointer to the boolean array and the isCopy flag is set to true if it's a copy
   *         or false if it's a direct pointer
   * @exception OutOfMemoryError if the system runs out of memory   
   */  
  private static VM_Address GetBooleanArrayElements(VM_JNIEnvironment env, int arrayJREF, VM_Address isCopyAddress) {
    if (traceJNI) VM.sysWrite("JNI called: GetBooleanArrayElements  \n");

    try {
      boolean sourceArray[] = (boolean []) env.getJNIRef(arrayJREF);
      int size = sourceArray.length;

      // alloc non moving buffer in C heap for a copy of string contents
      VM_Address copyBuffer = VM_SysCall.sysMalloc(size);
      if(copyBuffer.isZero()) {
        env.recordException(new OutOfMemoryError());
        return VM_Address.zero();
      }

      VM_Memory.memcopy(copyBuffer, VM_Magic.objectAsAddress(sourceArray), size);

      // set callers isCopy boolean to true, if it's a valid address
      if (!isCopyAddress.isZero()) {
        int temp = VM_Magic.getMemoryInt(isCopyAddress);
        if (VM.LittleEndian) {
          VM_Magic.setMemoryInt(isCopyAddress, ((temp & 0xffffff00) | 0x00000001));
        } else {
          VM_Magic.setMemoryInt(isCopyAddress, ((temp & 0x00ffffff) | 0x01000000));
        }
      }

      return copyBuffer;
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return VM_Address.zero();
    }
  }

  /**
   * GetByteArrayElements: get all the elements of a byte array
   * @param envJREF a JREF index for the JNI environment object
   * @param arrayJREF a JREF index for the source array 
   * @param isCopyAddress address of a flag to indicate whether the returned array is a copy or a direct pointer
   * @return A pointer to the byte array and the isCopy flag is set to true if it's a copy
   *         or false if it's a direct pointer
   * @exception OutOfMemoryError if the system runs out of memory   
   */  
  private static VM_Address GetByteArrayElements(VM_JNIEnvironment env, int arrayJREF, VM_Address isCopyAddress) {
    if (traceJNI) VM.sysWrite("JNI called: GetByteArrayElements \n");

    try {
      byte sourceArray[] = (byte []) env.getJNIRef(arrayJREF);
      int size = sourceArray.length;

      // alloc non moving buffer in C heap for a copy of string contents
      VM_Address copyBuffer = VM_SysCall.sysMalloc(size);

      if(copyBuffer.isZero()) {
        env.recordException(new OutOfMemoryError());
        return VM_Address.zero();
      }

      VM_Memory.memcopy(copyBuffer, VM_Magic.objectAsAddress(sourceArray), size);

      // set callers isCopy boolean to true, if it's a valid address
      if (!isCopyAddress.isZero()) {
        int temp = VM_Magic.getMemoryInt(isCopyAddress);
        if (VM.LittleEndian) {
          VM_Magic.setMemoryInt(isCopyAddress, ((temp & 0xffffff00) | 0x00000001));
        } else {
          VM_Magic.setMemoryInt(isCopyAddress, ((temp & 0x00ffffff) | 0x01000000));
        }
      }

      return copyBuffer;
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return VM_Address.zero();
    }
  }


  /**
   * GetCharArrayElements: get all the elements of a char array
   * @param envJREF a JREF index for the JNI environment object
   * @param arrayJREF a JREF index for the source array 
   * @param isCopyAddress address of a flag to indicate whether the returned array is a copy or a direct pointer
   * @return A pointer to the char array and the isCopy flag is set to true if it's a copy
   *         or false if it's a direct pointer
   * @exception OutOfMemoryError if the system runs out of memory   
   */  
  private static VM_Address GetCharArrayElements(VM_JNIEnvironment env, int arrayJREF, VM_Address isCopyAddress) {
    if (traceJNI) VM.sysWrite("JNI called: GetCharArrayElements  \n");

    try {
      char sourceArray[] = (char []) env.getJNIRef(arrayJREF);
      int size = sourceArray.length;

      // alloc non moving buffer in C heap for a copy of string contents
      VM_Address copyBuffer = VM_SysCall.sysMalloc(size*BYTES_IN_CHAR);
      if (copyBuffer.isZero()) {
        env.recordException(new OutOfMemoryError());
        return VM_Address.zero();
      }

      VM_Memory.memcopy(copyBuffer, VM_Magic.objectAsAddress(sourceArray), size*BYTES_IN_CHAR);

      // set callers isCopy boolean to true, if it's a valid address
      if (!isCopyAddress.isZero()) {
        int temp = VM_Magic.getMemoryInt(isCopyAddress);
        if (VM.LittleEndian) {
          VM_Magic.setMemoryInt(isCopyAddress, ((temp & 0xffffff00) | 0x00000001));
        } else {
          VM_Magic.setMemoryInt(isCopyAddress, ((temp & 0x00ffffff) | 0x01000000));
        }
      }

      return copyBuffer;
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return VM_Address.zero();
    }
  }

  /**
   * GetShortArrayElements: get all the elements of a short array
   * @param envJREF a JREF index for the JNI environment object
   * @param arrayJREF a JREF index for the source array 
   * @param isCopyAddress address of a flag to indicate whether the returned array is a copy or a direct pointer
   * @return A pointer to the short array and the isCopy flag is set to true if it's a copy
   *         or false if it's a direct pointer
   * @exception OutOfMemoryError if the system runs out of memory   
   */  
  private static VM_Address GetShortArrayElements(VM_JNIEnvironment env, int arrayJREF, VM_Address isCopyAddress) {
    if (traceJNI) VM.sysWrite("JNI called: GetShortArrayElements  \n");

    try {
      short sourceArray[] = (short []) env.getJNIRef(arrayJREF);
      int size = sourceArray.length;

      // alloc non moving buffer in C heap for a copy of string contents
      VM_Address copyBuffer = VM_SysCall.sysMalloc(size*BYTES_IN_SHORT);
      if(copyBuffer.isZero()) {
        env.recordException(new OutOfMemoryError());
        return VM_Address.zero();
      }

      VM_Memory.memcopy( copyBuffer, VM_Magic.objectAsAddress(sourceArray), size*BYTES_IN_SHORT );

      // set callers isCopy boolean to true, if it's a valid address
      if (!isCopyAddress.isZero()) {
        int temp = VM_Magic.getMemoryInt(isCopyAddress);
        if (VM.LittleEndian) {
          VM_Magic.setMemoryInt(isCopyAddress, ((temp & 0xffffff00) | 0x00000001));
        } else {
          VM_Magic.setMemoryInt(isCopyAddress, ((temp & 0x00ffffff) | 0x01000000));
        }
      }

      return copyBuffer;
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return VM_Address.zero();
    }
  }

  /**
   * GetIntArrayElements: get all the elements of an integer array
   * @param envJREF a JREF index for the JNI environment object
   * @param arrayJREF a JREF index for the source array 
   * @param isCopyAddress address of a flag to indicate whether the returned array is a copy or a direct pointer
   * @return A pointer to the integer array and the isCopy flag is set to true if it's a copy
   *         or false if it's a direct pointer
   * @exception OutOfMemoryError if the system runs out of memory   
   */  
  private static VM_Address GetIntArrayElements(VM_JNIEnvironment env, int arrayJREF, VM_Address isCopyAddress) {
    if (traceJNI) VM.sysWrite("JNI called: GetIntArrayElements  \n");

    try {
      int sourceArray[] = (int []) env.getJNIRef(arrayJREF);
      int size = sourceArray.length;

      // alloc non moving buffer in C heap for a copy of array contents
      VM_Address copyBuffer = VM_SysCall.sysMalloc(size << LOG_BYTES_IN_INT);
      if(copyBuffer.isZero()) {
        env.recordException(new OutOfMemoryError());
        return VM_Address.zero();
      }
      VM_Memory.memcopy(copyBuffer, VM_Magic.objectAsAddress(sourceArray), size << LOG_BYTES_IN_INT);

      // set callers isCopy boolean to true, if it's a valid address
      if (!isCopyAddress.isZero()) {
        int temp = VM_Magic.getMemoryInt(isCopyAddress);
        if (VM.LittleEndian) {
          VM_Magic.setMemoryInt(isCopyAddress, ((temp & 0xffffff00) | 0x00000001));
        } else {
          VM_Magic.setMemoryInt(isCopyAddress, ((temp & 0x00ffffff) | 0x01000000));
        }
      }

      return copyBuffer;
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return VM_Address.zero();
    }
  }

  /**
   * GetLongArrayElements: get all the elements of a long array
   * @param envJREF a JREF index for the JNI environment object
   * @param arrayJREF a JREF index for the source array 
   * @param isCopyAddress address of a flag to indicate whether the returned array is a copy or a direct pointer
   * @return A pointer to the long array and the isCopy flag is set to true if it's a copy
   *         or false if it's a direct pointer
   * @exception OutOfMemoryError if the system runs out of memory   
   */  
  private static VM_Address GetLongArrayElements(VM_JNIEnvironment env, int arrayJREF, VM_Address isCopyAddress) {
    if (traceJNI) VM.sysWrite("JNI called: GetLongArrayElements  \n");

    try {
      long sourceArray[] = (long []) env.getJNIRef(arrayJREF);
      int size = sourceArray.length;

      // alloc non moving buffer in C heap for a copy of string contents
      VM_Address copyBuffer = VM_SysCall.sysMalloc(size << LOG_BYTES_IN_LONG);
      if(copyBuffer.isZero()) {
        env.recordException(new OutOfMemoryError());
        return VM_Address.zero();
      }
      VM_Memory.memcopy(copyBuffer, VM_Magic.objectAsAddress(sourceArray), size << LOG_BYTES_IN_LONG);

      // set callers isCopy boolean to true, if it's a valid address
      if (!isCopyAddress.isZero()) {
        int temp = VM_Magic.getMemoryInt(isCopyAddress);
        if (VM.LittleEndian) {
          VM_Magic.setMemoryInt(isCopyAddress, ((temp & 0xffffff00) | 0x00000001));
        } else {
          VM_Magic.setMemoryInt(isCopyAddress, ((temp & 0x00ffffff) | 0x01000000));
        }
      }

      return copyBuffer;
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return VM_Address.zero();
    }
  }

  /**
   * GetFloatArrayElements: get all the elements of a float array
   * @param envJREF a JREF index for the JNI environment object
   * @param arrayJREF a JREF index for the source array 
   * @param isCopyAddress address of a flag to indicate whether the returned array is a copy or a direct pointer
   * @return A pointer to the float array and the isCopy flag is set to true if it's a copy
   *         or false if it's a direct pointer
   * @exception OutOfMemoryError if the system runs out of memory   
   */  
  private static VM_Address GetFloatArrayElements(VM_JNIEnvironment env, int arrayJREF, VM_Address isCopyAddress) {
    if (traceJNI) VM.sysWrite("JNI called: GetFloatArrayElements  \n");
    
    try {
      float sourceArray[] = (float []) env.getJNIRef(arrayJREF);
      int size = sourceArray.length;

      // alloc non moving buffer in C heap for a copy of string contents
      VM_Address copyBuffer = VM_SysCall.sysMalloc(size << LOG_BYTES_IN_FLOAT);
      if(copyBuffer.isZero()) {
        env.recordException(new OutOfMemoryError());
        return VM_Address.zero();
      }

      VM_Memory.memcopy(copyBuffer, VM_Magic.objectAsAddress(sourceArray), size << LOG_BYTES_IN_FLOAT);

      // set callers isCopy boolean to true, if it's a valid address
      if (!isCopyAddress.isZero()) {
        int temp = VM_Magic.getMemoryInt(isCopyAddress);
        if (VM.LittleEndian) {
          VM_Magic.setMemoryInt(isCopyAddress, ((temp & 0xffffff00) | 0x00000001));
        } else {
          VM_Magic.setMemoryInt(isCopyAddress, ((temp & 0x00ffffff) | 0x01000000));
        }
      }

      return copyBuffer;
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return VM_Address.zero();
    }
  }

  /**
   * GetDoubleArrayElements: get all the elements of a double array
   * @param envJREF a JREF index for the JNI environment object
   * @param arrayJREF a JREF index for the source array 
   * @param isCopyAddress address of a flag to indicate whether the returned array is a copy or a direct pointer
   * @return A pointer to the double array and the isCopy flag is set to true if it's a copy
   *         or false if it's a direct pointer
   * @exception OutOfMemoryError if the system runs out of memory   
   */  
  private static VM_Address GetDoubleArrayElements(VM_JNIEnvironment env, int arrayJREF, VM_Address isCopyAddress) {
    if (traceJNI) VM.sysWrite("JNI called: GetDoubleArrayElements  \n");

    try {
      double sourceArray[] = (double []) env.getJNIRef(arrayJREF);
      int size = sourceArray.length;

      // alloc non moving buffer in C heap for a copy of string contents
      VM_Address copyBuffer = VM_SysCall.sysMalloc(size << LOG_BYTES_IN_DOUBLE);
      if(copyBuffer.isZero()) {
        env.recordException(new OutOfMemoryError());
        return VM_Address.zero();
      }
      VM_Memory.memcopy(copyBuffer, VM_Magic.objectAsAddress(sourceArray), size << LOG_BYTES_IN_DOUBLE);

      // set callers isCopy boolean to true, if it's a valid address
      if (!isCopyAddress.isZero()) {
        int temp = VM_Magic.getMemoryInt(isCopyAddress);
        if (VM.LittleEndian) {
          VM_Magic.setMemoryInt(isCopyAddress, ((temp & 0xffffff00) | 0x00000001));
        } else {
          VM_Magic.setMemoryInt(isCopyAddress, ((temp & 0x00ffffff) | 0x01000000));
        }
      }

      return copyBuffer;
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return VM_Address.zero();
    }
  }

  /**
   * ReleaseBooleanArrayElements: free the native copy of the array, update changes to Java array as indicated
   * @param envJREF a JREF index for the JNI environment object
   * @param arrayJREF a JREF index for the source array 
   * @param copyBufferAddress the address of the copy of the array
   * @param releaseMode one of 3 codes to indicate whether to copy back or free the array:
   *    releaseMode 0:  copy back and free the buffer
   *    releaseMode 1:  JNI_COMMIT, copy back but do not free the buffer
   *    releaseMode 2:  JNI_ABORT, free the buffer with copying back
   */  
  private static void ReleaseBooleanArrayElements(VM_JNIEnvironment env, int arrayJREF, VM_Address copyBufferAddress, 
                                                  int releaseMode) {
    if (traceJNI) VM.sysWrite("JNI called: ReleaseBooleanArrayElements  \n");

    try {
      boolean sourceArray[] = (boolean []) env.getJNIRef(arrayJREF);

      // If a direct pointer was given to the user, no need to update or release
      if (VM_Magic.objectAsAddress(sourceArray).NE(copyBufferAddress)) {
	int size = sourceArray.length;

	// mode 0 and mode 1:  copy back the buffer
	if ((releaseMode== 0 || releaseMode== 1) && size!=0) {
	  for (int i=0; i<size; i+= BYTES_IN_INT) {
	    VM_Address addr = copyBufferAddress.add(i);
	    int data = VM_Magic.getMemoryInt(addr);
	    if (VM.LittleEndian) {
	      if (i<size) 
		sourceArray[i]   = ((data)        & 0x000000ff) == 0 ? false : true;
	      if (i+1<size) 		    	         
		sourceArray[i+1] = ((data >>> BITS_IN_BYTE)  & 0x000000ff) == 0 ? false : true;
	      if (i+2<size) 		    	         
		sourceArray[i+2] = ((data >>> (2*BITS_IN_BYTE)) & 0x000000ff) == 0 ? false : true;
	      if (i+3<size) 		    	                 
		sourceArray[i+3] = ((data >>> (3*BITS_IN_BYTE)) & 0x000000ff) == 0 ? false : true;
	    } else {
	      if (i<size) 
		sourceArray[i]   = ((data >>> (3*BITS_IN_BYTE)) & 0x000000ff) == 0 ? false : true;
	      if (i+1<size) 		    	         
		sourceArray[i+1] = ((data >>> (2*BITS_IN_BYTE)) & 0x000000ff) == 0 ? false : true;
	      if (i+2<size) 		    	         
		sourceArray[i+2] = ((data >>> BITS_IN_BYTE)  & 0x000000ff) == 0 ? false : true;
	      if (i+3<size) 		    	                 
		sourceArray[i+3] = ((data)        & 0x000000ff) == 0 ? false : true;
	    }
	  }
	} 

	// mode 0 and mode 2:  free the buffer
	if (releaseMode== 0 || releaseMode== 2) {
	  VM_SysCall.sysFree(copyBufferAddress);
	}
      }
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * ReleaseByteArrayElements: free the native copy of the array, update changes to Java array as indicated
   * @param envJREF a JREF index for the JNI environment object
   * @param arrayJREF a JREF index for the source array 
   * @param copyBufferAddress the address of the copy of the array
   * @param releaseMode one of 3 codes to indicate whether to copy back or free the array:
   *    releaseMode 0:  copy back and free the buffer
   *    releaseMode 1:  JNI_COMMIT, copy back but do not free the buffer
   *    releaseMode 2:  JNI_ABORT, free the buffer with copying back
   */  
  private static void ReleaseByteArrayElements(VM_JNIEnvironment env, int arrayJREF, VM_Address copyBufferAddress, 
                                               int releaseMode) {
    if (traceJNI) VM.sysWrite("JNI called: ReleaseByteArrayElements  \n");

    try {
      byte sourceArray[] = (byte []) env.getJNIRef(arrayJREF);

      // If a direct pointer was given to the user, no need to update or release
      if (VM_Magic.objectAsAddress(sourceArray) != copyBufferAddress) {
	int size = sourceArray.length;

	// mode 0 and mode 1:  copy back the buffer
	if ((releaseMode== 0 || releaseMode== 1) && size!=0) {
	  VM_Memory.memcopy(VM_Magic.objectAsAddress(sourceArray), copyBufferAddress, size);
	} 

	// mode 0 and mode 2:  free the buffer
	if (releaseMode== 0 || releaseMode== 2) {
	  VM_SysCall.sysFree(copyBufferAddress);
	}
      }
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * ReleaseCharArrayElements: free the native copy of the array, update changes to Java array as indicated
   * @param envJREF a JREF index for the JNI environment object
   * @param arrayJREF a JREF index for the source array 
   * @param copyBufferAddress the address of the copy of the array
   * @param releaseMode one of 3 codes to indicate whether to copy back or free the array:
   *    releaseMode 0:  copy back and free the buffer
   *    releaseMode 1:  JNI_COMMIT, copy back but do not free the buffer
   *    releaseMode 2:  JNI_ABORT, free the buffer with copying back
   */  
  private static void ReleaseCharArrayElements(VM_JNIEnvironment env, int arrayJREF, VM_Address copyBufferAddress, 
                                               int releaseMode) {
    if (traceJNI) VM.sysWrite("JNI called: ReleaseCharArrayElements \n");

    try {
      char sourceArray[] = (char []) env.getJNIRef(arrayJREF);

      // If a direct pointer was given to the user, no need to update or release
      if (VM_Magic.objectAsAddress(sourceArray) != copyBufferAddress) {
	int size = sourceArray.length;

	// mode 0 and mode 1:  copy back the buffer
	if ((releaseMode== 0 || releaseMode== 1) && size!=0) {
	  VM_Memory.memcopy(VM_Magic.objectAsAddress(sourceArray), copyBufferAddress, size << LOG_BYTES_IN_CHAR);
	} 

	// mode 0 and mode 2:  free the buffer
	if (releaseMode== 0 || releaseMode== 2) {
	  VM_SysCall.sysFree(copyBufferAddress);
	}
      }
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * ReleaseShortArrayElements: free the native copy of the array, update changes to Java array as indicated
   * @param envJREF a JREF index for the JNI environment object
   * @param arrayJREF a JREF index for the source array 
   * @param copyBufferAddress the address of the copy of the array
   * @param releaseMode one of 3 codes to indicate whether to copy back or free the array:
   *    releaseMode 0:  copy back and free the buffer
   *    releaseMode 1:  JNI_COMMIT, copy back but do not free the buffer
   *    releaseMode 2:  JNI_ABORT, free the buffer with copying back
   */  
  private static void ReleaseShortArrayElements(VM_JNIEnvironment env, int arrayJREF, VM_Address copyBufferAddress, 
                                                int releaseMode) {
    if (traceJNI) VM.sysWrite("JNI called: ReleaseShortArrayElements  \n");

    try {
      short sourceArray[] = (short []) env.getJNIRef(arrayJREF);

      // If a direct pointer was given to the user, no need to update or release
      if (VM_Magic.objectAsAddress(sourceArray) != copyBufferAddress) {
	int size = sourceArray.length;

	// mode 0 and mode 1:  copy back the buffer
	if ((releaseMode== 0 || releaseMode== 1) && size!=0) {
	  VM_Memory.memcopy(VM_Magic.objectAsAddress(sourceArray), copyBufferAddress, size << LOG_BYTES_IN_SHORT);
	} 

	// mode 0 and mode 2:  free the buffer
	if (releaseMode== 0 || releaseMode== 2) {
	  VM_SysCall.sysFree(copyBufferAddress);
	}
      }
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * ReleaseIntArrayElements: free the native copy of the array, update changes to Java array as indicated
   * @param envJREF a JREF index for the JNI environment object
   * @param arrayJREF a JREF index for the source array 
   * @param copyBufferAddress the address of the copy of the array
   * @param releaseMode one of 3 codes to indicate whether to copy back or free the array:
   *    releaseMode 0:  copy back and free the buffer
   *    releaseMode 1:  JNI_COMMIT, copy back but do not free the buffer
   *    releaseMode 2:  JNI_ABORT, free the buffer with copying back
   */  
  private static void ReleaseIntArrayElements(VM_JNIEnvironment env, int arrayJREF, VM_Address copyBufferAddress, 
                                              int releaseMode) {
    if (traceJNI) VM.sysWrite("JNI called: ReleaseIntArrayElements  \n");

    try {
      int sourceArray[] = (int []) env.getJNIRef(arrayJREF);

      // If a direct pointer was given to the user, no need to update or release
      if (VM_Magic.objectAsAddress(sourceArray) != copyBufferAddress) {
	int size = sourceArray.length;

	// mode 0 and mode 1:  copy back the buffer
	if (releaseMode== 0 || releaseMode== 1) {
	  VM_Memory.memcopy(VM_Magic.objectAsAddress(sourceArray), copyBufferAddress, size << LOG_BYTES_IN_INT);
	} 

	// mode 0 and mode 2:  free the buffer
	if (releaseMode== 0 || releaseMode== 2) {
	  VM_SysCall.sysFree(copyBufferAddress);
	}
      }
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }


  /**
   * ReleaseLongArrayElements: free the native copy of the array, update changes to Java array as indicated
   * @param envJREF a JREF index for the JNI environment object
   * @param arrayJREF a JREF index for the source array 
   * @param copyBufferAddress the address of the copy of the array
   * @param releaseMode one of 3 codes to indicate whether to copy back or free the array:
   *    releaseMode 0:  copy back and free the buffer
   *    releaseMode 1:  JNI_COMMIT, copy back but do not free the buffer
   *    releaseMode 2:  JNI_ABORT, free the buffer with copying back
   */  
  private static void ReleaseLongArrayElements(VM_JNIEnvironment env, int arrayJREF, VM_Address copyBufferAddress, 
                                               int releaseMode) {
    if (traceJNI) VM.sysWrite("JNI called: ReleaseLongArrayElements  \n");

    try {
      long sourceArray[] = (long []) env.getJNIRef(arrayJREF);

      // If a direct pointer was given to the user, no need to update or release
      if (VM_Magic.objectAsAddress(sourceArray) != copyBufferAddress) {
	int size = sourceArray.length;

	// mode 0 and mode 1:  copy back the buffer
	if (releaseMode== 0 || releaseMode== 1) {
	  VM_Memory.memcopy( VM_Magic.objectAsAddress(sourceArray), copyBufferAddress, size << LOG_BYTES_IN_LONG );
	} 

	// mode 0 and mode 2:  free the buffer
	if (releaseMode== 0 || releaseMode== 2) {
	  VM_SysCall.sysFree(copyBufferAddress);
	}
      }
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * ReleaseFloatArrayElements: free the native copy of the array, update changes to Java array as indicated
   * @param envJREF a JREF index for the JNI environment object
   * @param arrayJREF a JREF index for the source array 
   * @param copyBufferAddress the address of the copy of the array
   * @param releaseMode one of 3 codes to indicate whether to copy back or free the array:
   *    releaseMode 0:  copy back and free the buffer
   *    releaseMode 1:  JNI_COMMIT, copy back but do not free the buffer
   *    releaseMode 2:  JNI_ABORT, free the buffer with copying back
   */  
  private static void ReleaseFloatArrayElements(VM_JNIEnvironment env, int arrayJREF, VM_Address copyBufferAddress, 
                                                int releaseMode) {
    if (traceJNI) VM.sysWrite("JNI called: ReleaseFloatArrayElements  \n");

    try {
      float sourceArray[] = (float []) env.getJNIRef(arrayJREF);

      // If a direct pointer was given to the user, no need to update or release
      if (VM_Magic.objectAsAddress(sourceArray) != copyBufferAddress) {
	int size = sourceArray.length;

	// mode 0 and mode 1:  copy back the buffer
	if (releaseMode== 0 || releaseMode== 1) {
	  VM_Memory.memcopy( VM_Magic.objectAsAddress(sourceArray), copyBufferAddress, size << LOG_BYTES_IN_FLOAT);
	} 

	// mode 0 and mode 2:  free the buffer
	if (releaseMode== 0 || releaseMode== 2) {
	  VM_SysCall.sysFree(copyBufferAddress);
	}
      }
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * ReleaseDoubleArrayElements: free the native copy of the array, update changes to Java array as indicated
   * @param envJREF a JREF index for the JNI environment object
   * @param arrayJREF a JREF index for the source array 
   * @param copyBufferAddress the address of the copy of the array
   * @param releaseMode one of 3 codes to indicate whether to copy back or free the array:
   *    releaseMode 0:  copy back and free the buffer
   *    releaseMode 1:  JNI_COMMIT, copy back but do not free the buffer
   *    releaseMode 2:  JNI_ABORT, free the buffer with copying back
   */  
  private static void ReleaseDoubleArrayElements(VM_JNIEnvironment env, int arrayJREF, VM_Address copyBufferAddress, 
                                                 int releaseMode) {
    if (traceJNI) VM.sysWrite("JNI called: ReleaseDoubleArrayElements  \n");

    try {
      double sourceArray[] = (double []) env.getJNIRef(arrayJREF);

      // If a direct pointer was given to the user, no need to update or release
      if (VM_Magic.objectAsAddress(sourceArray) != copyBufferAddress) {
	int size = sourceArray.length;

	// mode 0 and mode 1:  copy back the buffer
	if (releaseMode== 0 || releaseMode== 1) {
	  VM_Memory.memcopy( VM_Magic.objectAsAddress(sourceArray), copyBufferAddress, size << LOG_BYTES_IN_DOUBLE );
	} 

	// mode 0 and mode 2:  free the buffer
	if (releaseMode== 0 || releaseMode== 2) {
	  VM_SysCall.sysFree(copyBufferAddress);
	}
      }
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * GetBooleanArrayRegion: copy a region of the array into the native buffer
   * @param envJREF a JREF index for the JNI environment object
   * @param arrayJREF a JREF index for the source array 
   * @param startIndex the starting index to copy
   * @param length the number of elements to copy
   * @param bufAddress the destination address in native to copy to
   * @exception ArrayIndexOutOfBoundsException if one of the indices in the region is not valid
   */
  private static void GetBooleanArrayRegion(VM_JNIEnvironment env, int arrayJREF, int startIndex, 
                                            int length, VM_Address bufAddress) {
    if (traceJNI) VM.sysWrite("JNI called: GetBooleanArrayRegion  \n");

    try {
      boolean sourceArray[] = (boolean []) env.getJNIRef(arrayJREF);

      if ((startIndex<0) || (startIndex+length>sourceArray.length)) {
        env.recordException(new ArrayIndexOutOfBoundsException());
        return;
      }
      VM_Memory.memcopy(bufAddress, VM_Magic.objectAsAddress(sourceArray).add(startIndex), length); 
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * GetByteArrayRegion: copy a region of the array into the native buffer
   * @param envJREF a JREF index for the JNI environment object
   * @param arrayJREF a JREF index for the source array 
   * @param startIndex the starting index to copy
   * @param length the number of elements to copy
   * @param bufAddress the destination address in native to copy to
   * @exception ArrayIndexOutOfBoundsException if one of the indices in the region is not valid
   */
  private static void GetByteArrayRegion(VM_JNIEnvironment env, int arrayJREF, int startIndex, 
                                         int length, VM_Address bufAddress) {
    if (traceJNI) VM.sysWrite("JNI called: GetByteArrayRegion  \n");

    try {
      byte sourceArray[] = (byte []) env.getJNIRef(arrayJREF);

      if ((startIndex<0) || (startIndex+length>sourceArray.length)) {
        env.recordException(new ArrayIndexOutOfBoundsException());
        return;
      }

      VM_Memory.memcopy(bufAddress, VM_Magic.objectAsAddress(sourceArray).add(startIndex), length); 
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * GetCharArrayRegion: copy a region of the array into the native buffer
   * @param envJREF a JREF index for the JNI environment object
   * @param arrayJREF a JREF index for the source array 
   * @param startIndex the starting index to copy
   * @param length the number of elements to copy
   * @param bufAddress the destination address in native to copy to
   * @exception ArrayIndexOutOfBoundsException if one of the indices in the region is not valid
   */
  private static void GetCharArrayRegion(VM_JNIEnvironment env, int arrayJREF, int startIndex,  
                                         int length, VM_Address bufAddress) {
    if (traceJNI) VM.sysWrite("JNI called: GetCharArrayRegion  \n");

    try {
      char sourceArray[] = (char []) env.getJNIRef(arrayJREF);

      if ((startIndex<0) || (startIndex+length>sourceArray.length)) {
        env.recordException(new ArrayIndexOutOfBoundsException());
        return;
      }

      VM_Memory.memcopy(bufAddress, VM_Magic.objectAsAddress(sourceArray).add(startIndex << LOG_BYTES_IN_CHAR), length << LOG_BYTES_IN_CHAR); 
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * GetShortArrayRegion: copy a region of the array into the native buffer
   * @param envJREF a JREF index for the JNI environment object
   * @param arrayJREF a JREF index for the source array 
   * @param startIndex the starting index to copy
   * @param length the number of elements to copy
   * @param bufAddress the destination address in native to copy to
   * @exception ArrayIndexOutOfBoundsException if one of the indices in the region is not valid
   */
  private static void GetShortArrayRegion(VM_JNIEnvironment env, int arrayJREF, int startIndex,  
                                          int length, VM_Address bufAddress) {
    if (traceJNI) VM.sysWrite("JNI called: GetShortArrayRegion  \n");

    try {
      short sourceArray[] = (short []) env.getJNIRef(arrayJREF);

      if ((startIndex<0) || (startIndex+length>sourceArray.length)) {
        env.recordException(new ArrayIndexOutOfBoundsException());
        return;
      }

      VM_Memory.memcopy(bufAddress, VM_Magic.objectAsAddress(sourceArray).add(startIndex << LOG_BYTES_IN_SHORT), length << LOG_BYTES_IN_SHORT); 
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * GetIntArrayRegion: copy a region of the array into the native buffer
   * @param envJREF a JREF index for the JNI environment object
   * @param arrayJREF a JREF index for the source array 
   * @param startIndex the starting index to copy
   * @param length the number of elements to copy
   * @param bufAddress the destination address in native to copy to
   * @exception ArrayIndexOutOfBoundsException if one of the indices in the region is not valid
   */
  private static void GetIntArrayRegion(VM_JNIEnvironment env, int arrayJREF, int startIndex, 
                                        int length, VM_Address bufAddress) {
    if (traceJNI) VM.sysWrite("JNI called: GetIntArrayRegion  \n");

    try {
      int sourceArray[] = (int []) env.getJNIRef(arrayJREF);

      if ((startIndex<0) || (startIndex+length>sourceArray.length)) {
        env.recordException(new ArrayIndexOutOfBoundsException());
        return;
      }

      VM_Memory.memcopy(bufAddress, VM_Magic.objectAsAddress(sourceArray).add(startIndex << LOG_BYTES_IN_INT), length << LOG_BYTES_IN_INT); 
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * GetLongArrayRegion: copy a region of the array into the native buffer
   * @param envJREF a JREF index for the JNI environment object
   * @param arrayJREF a JREF index for the source array 
   * @param startIndex the starting index to copy
   * @param length the number of elements to copy
   * @param bufAddress the destination address in native to copy to
   * @exception ArrayIndexOutOfBoundsException if one of the indices in the region is not valid
   */
  private static void GetLongArrayRegion(VM_JNIEnvironment env, int arrayJREF, int startIndex, 
                                         int length, VM_Address bufAddress) {
    if (traceJNI) VM.sysWrite("JNI called: GetLongArrayRegion   \n");

    try {
      long sourceArray[] = (long []) env.getJNIRef(arrayJREF);

      if ((startIndex<0) || (startIndex+length>sourceArray.length)) {
        env.recordException(new ArrayIndexOutOfBoundsException());
        return;
      }

      VM_Memory.memcopy(bufAddress, VM_Magic.objectAsAddress(sourceArray).add(startIndex << LOG_BYTES_IN_LONG), length << LOG_BYTES_IN_LONG); 
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * GetFloatArrayRegion: copy a region of the array into the native buffer
   * @param envJREF a JREF index for the JNI environment object
   * @param arrayJREF a JREF index for the source array 
   * @param startIndex the starting index to copy
   * @param length the number of elements to copy
   * @param bufAddress the destination address in native to copy to
   * @exception ArrayIndexOutOfBoundsException if one of the indices in the region is not valid
   */
  private static void GetFloatArrayRegion(VM_JNIEnvironment env, int arrayJREF, int startIndex, 
                                          int length, VM_Address bufAddress) {
    if (traceJNI) VM.sysWrite("JNI called: GetFloatArrayRegion    \n");

    try {
      float sourceArray[] = (float []) env.getJNIRef(arrayJREF);

      if ((startIndex<0) || (startIndex+length>sourceArray.length)) {
        env.recordException(new ArrayIndexOutOfBoundsException());
        return;
      }

      VM_Memory.memcopy(bufAddress, VM_Magic.objectAsAddress(sourceArray).add(startIndex << LOG_BYTES_IN_FLOAT), length << LOG_BYTES_IN_FLOAT); 
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * GetDoubleArrayRegion: copy a region of the array into the native buffer
   * @param envJREF a JREF index for the JNI environment object
   * @param arrayJREF a JREF index for the source array 
   * @param startIndex the starting index to copy
   * @param length the number of elements to copy
   * @param bufAddress the destination address in native to copy to
   * @exception ArrayIndexOutOfBoundsException if one of the indices in the region is not valid
   */
  private static void GetDoubleArrayRegion(VM_JNIEnvironment env, int arrayJREF, int startIndex, 
                                           int length, VM_Address bufAddress) {
    if (traceJNI) VM.sysWrite("JNI called: GetDoubleArrayRegion   \n");

    try {
      double sourceArray[] = (double []) env.getJNIRef(arrayJREF);

      if ((startIndex<0) || (startIndex+length>sourceArray.length)) {
        env.recordException(new ArrayIndexOutOfBoundsException());
        return;
      }

      VM_Memory.memcopy(bufAddress, VM_Magic.objectAsAddress(sourceArray).add(startIndex << LOG_BYTES_IN_DOUBLE), length << LOG_BYTES_IN_DOUBLE); 
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * SetBooleanArrayRegion: copy a region of the native buffer into the array (1 byte element)
   * @param envJREF a JREF index for the JNI environment object
   * @param arrayJREF a JREF index for the destination array 
   * @param startIndex the starting index to copy
   * @param length the number of elements to copy
   * @param bufAddress the source address in native to copy from
   * @exception ArrayIndexOutOfBoundsException if one of the indices in the region is not valid
   */
  private static void SetBooleanArrayRegion(VM_JNIEnvironment env, int arrayJREF, int startIndex, 
                                            int length, VM_Address bufAddress) {
    if (traceJNI) VM.sysWrite("JNI called: SetBooleanArrayRegion  \n");

    try {
      boolean destinationArray[] = (boolean []) env.getJNIRef(arrayJREF);

      if ((startIndex<0) || (startIndex+length>destinationArray.length)) {
        env.recordException(new ArrayIndexOutOfBoundsException());
        return;
      }

      VM_Memory.memcopy(VM_Magic.objectAsAddress(destinationArray).add(startIndex), bufAddress, length); 
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * SetByteArrayRegion: copy a region of the native buffer into the array (1 byte element)
   * @param envJREF a JREF index for the JNI environment object
   * @param arrayJREF a JREF index for the destination array 
   * @param startIndex the starting index to copy
   * @param length the number of elements to copy
   * @param bufAddress the source address in native to copy from
   * @exception ArrayIndexOutOfBoundsException if one of the indices in the region is not valid
   */
  private static void SetByteArrayRegion(VM_JNIEnvironment env, int arrayJREF, int startIndex, 
                                         int length, VM_Address bufAddress) {
    if (traceJNI) VM.sysWrite("JNI called: SetByteArrayRegion  \n");

    try {
      byte destinationArray[] = (byte []) env.getJNIRef(arrayJREF);

      if ((startIndex<0) || (startIndex+length>destinationArray.length)) {
        env.recordException(new ArrayIndexOutOfBoundsException());
        return;
      }

      VM_Memory.memcopy(VM_Magic.objectAsAddress(destinationArray).add(startIndex), bufAddress, length); 
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }   


  /**
   * SetCharArrayRegion: copy a region of the native buffer into the array (2 byte element)
   * @param envJREF a JREF index for the JNI environment object
   * @param arrayJREF a JREF index for the destination array 
   * @param startIndex the starting index to copy
   * @param length the number of elements to copy
   * @param bufAddress the source address in native to copy from
   * @exception ArrayIndexOutOfBoundsException if one of the indices in the region is not valid
   */
  private static void SetCharArrayRegion(VM_JNIEnvironment env, int arrayJREF, int startIndex, 
                                         int length, VM_Address bufAddress) {
    if (traceJNI) VM.sysWrite("JNI called: SetCharArrayRegion  \n");

    try {
      char destinationArray[] = (char []) env.getJNIRef(arrayJREF);

      if ((startIndex<0) || (startIndex+length>destinationArray.length)) {
        env.recordException(new ArrayIndexOutOfBoundsException());
        return;
      }

      VM_Memory.memcopy(VM_Magic.objectAsAddress(destinationArray).add(startIndex << LOG_BYTES_IN_CHAR), bufAddress, length << LOG_BYTES_IN_CHAR); 
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }   

  /**
   * SetShortArrayRegion: copy a region of the native buffer into the array (2 byte element)
   * @param envJREF a JREF index for the JNI environment object
   * @param arrayJREF a JREF index for the destination array 
   * @param startIndex the starting index to copy
   * @param length the number of elements to copy
   * @param bufAddress the source address in native to copy from
   * @exception ArrayIndexOutOfBoundsException if one of the indices in the region is not valid
   */
  private static void SetShortArrayRegion(VM_JNIEnvironment env, int arrayJREF, int startIndex, 
                                          int length, VM_Address bufAddress) {
    if (traceJNI) VM.sysWrite("JNI called: SetShortArrayRegion  \n");

    try {
      short destinationArray[] = (short []) env.getJNIRef(arrayJREF);

      if ((startIndex<0) || (startIndex+length>destinationArray.length)) {
        env.recordException(new ArrayIndexOutOfBoundsException());
        return;
      }

      VM_Memory.memcopy(VM_Magic.objectAsAddress(destinationArray).add(startIndex << LOG_BYTES_IN_SHORT), bufAddress, length << LOG_BYTES_IN_SHORT); 
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }  

  /**
   * SetIntArrayRegion: copy a region of the native buffer into the array
   * @param envJREF a JREF index for the JNI environment object
   * @param arrayJREF a JREF index for the destination array 
   * @param startIndex the starting index to copy
   * @param length the number of elements to copy
   * @param bufAddress the source address in native to copy from
   * @exception ArrayIndexOutOfBoundsException if one of the indices in the region is not valid
   */
  private static void SetIntArrayRegion(VM_JNIEnvironment env, int arrayJREF, int startIndex, 
                                        int length, VM_Address bufAddress) {
    if (traceJNI) VM.sysWrite("JNI called: SetIntArrayRegion  \n");

    try {
      int destinationArray[] = (int []) env.getJNIRef(arrayJREF);

      if ((startIndex<0) || (startIndex+length>destinationArray.length)) {
        env.recordException(new ArrayIndexOutOfBoundsException());
        return;
      }

      VM_Memory.memcopy(VM_Magic.objectAsAddress(destinationArray).add(startIndex << LOG_BYTES_IN_INT), bufAddress, length << LOG_BYTES_IN_INT); 
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * SetLongArrayRegion: copy a region of the native buffer into the array
   * @param envJREF a JREF index for the JNI environment object
   * @param arrayJREF a JREF index for the destination array 
   * @param startIndex the starting index to copy
   * @param length the number of elements to copy
   * @param bufAddress the source address in native to copy from
   * @exception ArrayIndexOutOfBoundsException if one of the indices in the region is not valid
   */
  private static void SetLongArrayRegion(VM_JNIEnvironment env, int arrayJREF, int startIndex, 
                                         int length, VM_Address bufAddress) {
    if (traceJNI) VM.sysWrite("JNI called: SetLongArrayRegion  \n");

    try {
      long destinationArray[] = (long []) env.getJNIRef(arrayJREF);

      if ((startIndex<0) || (startIndex+length>destinationArray.length)) {
        env.recordException(new ArrayIndexOutOfBoundsException());
        return;
      }

      VM_Memory.memcopy(VM_Magic.objectAsAddress(destinationArray).add(startIndex << LOG_BYTES_IN_LONG), bufAddress, length << LOG_BYTES_IN_LONG); 
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * SetFloatArrayRegion: copy a region of the native buffer into the array
   * @param envJREF a JREF index for the JNI environment object
   * @param arrayJREF a JREF index for the destination array 
   * @param startIndex the starting index to copy
   * @param length the number of elements to copy
   * @param bufAddress the source address in native to copy from
   * @exception ArrayIndexOutOfBoundsException if one of the indices in the region is not valid
   */
  private static void SetFloatArrayRegion(VM_JNIEnvironment env, int arrayJREF, int startIndex, 
                                          int length, VM_Address bufAddress) {
    if (traceJNI) VM.sysWrite("JNI called: SetFloatArrayRegion  \n");

    try {
      float destinationArray[] = (float []) env.getJNIRef(arrayJREF);

      if ((startIndex<0) || (startIndex+length>destinationArray.length)) {
        env.recordException(new ArrayIndexOutOfBoundsException());
        return;
      }

      VM_Memory.memcopy(VM_Magic.objectAsAddress(destinationArray).add(startIndex << LOG_BYTES_IN_FLOAT), bufAddress, length << LOG_BYTES_IN_FLOAT); 
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  /**
   * SetDoubleArrayRegion: copy a region of the native buffer into the array
   * @param envJREF a JREF index for the JNI environment object
   * @param arrayJREF a JREF index for the destination array 
   * @param startIndex the starting index to copy
   * @param length the number of elements to copy
   * @param bufAddress the source address in native to copy from
   * @exception ArrayIndexOutOfBoundsException if one of the indices in the region is not valid
   */
  private static void SetDoubleArrayRegion(VM_JNIEnvironment env, int arrayJREF, int startIndex, 
                                           int length, VM_Address bufAddress) {
    if (traceJNI) VM.sysWrite("JNI called: SetDoubleArrayRegion  \n");

    try {
      double destinationArray[] = (double []) env.getJNIRef(arrayJREF);

      if ((startIndex<0) || (startIndex+length>destinationArray.length)) {
        env.recordException(new ArrayIndexOutOfBoundsException());
        return;
      }

      VM_Memory.memcopy(VM_Magic.objectAsAddress(destinationArray).add(startIndex << LOG_BYTES_IN_DOUBLE), bufAddress, length << LOG_BYTES_IN_DOUBLE); 
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }


  private static int RegisterNatives(VM_JNIEnvironment env) {
    if (traceJNI) VM.sysWrite("JNI called: RegisterNatives  \n");

    VM.sysWrite("JNI ERROR: RegisterNatives not implemented yet.");
    VM.sysExit(VM.exitStatusUnsupportedInternalOp);
    return -1; 
  }


  private static int UnregisterNatives(VM_JNIEnvironment env) {
    if (traceJNI) VM.sysWrite("JNI called: UnregisterNatives  \n");

    VM.sysWrite("JNI ERROR: UnregisterNatives not implemented yet.");
    VM.sysExit(VM.exitStatusUnsupportedInternalOp);
    return -1; 
  }

  /**
   * MonitorEnter
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the object to lock
   * @return 0 if the object is locked successfully, -1 if not
   */
  private static int MonitorEnter(VM_JNIEnvironment env, int objJREF) {
    if (traceJNI) VM.sysWrite("JNI called: MonitorEnter  \n");

    try {
      Object obj = env.getJNIRef(objJREF);
      VM_ObjectModel.genericLock(obj);
      return 0;
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      return -1;
    }
  }

  /**
   * MonitorExit
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the object to unlock
   * @return 0 if the object is unlocked successfully, -1 if not
   */
  private static int MonitorExit(VM_JNIEnvironment env, int objJREF) {
    if (traceJNI) VM.sysWrite("JNI called: MonitorExit  \n");

    try {
      Object obj = env.getJNIRef(objJREF);
      VM_ObjectModel.genericUnlock(obj);
      return 0;
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      return -1;
    }
  }

  // this is the address of the malloc'ed JavaVM struct (one per VM)
  private static VM_Address JavaVM; 

  private static native VM_Address createJavaVM();

  private static int GetJavaVM(VM_JNIEnvironment env, VM_Address StarStarJavaVM) {
    if (traceJNI) VM.sysWrite("JNI called: GetJavaVM \n");

    if (JavaVM == null) {
      JavaVM = createJavaVM();
    }
    
    if (traceJNI) VM.sysWriteln(StarStarJavaVM);
    VM_Magic.setMemoryAddress(StarStarJavaVM, JavaVM);

    return 0;
  }

  /*******************************************************************
   * These functions are added in Java 2
   */

  private static int FromReflectedMethod(VM_JNIEnvironment env) {
    if (traceJNI) VM.sysWrite("JNI called: FromReflectedMethod \n");   
    VM.sysWrite("JNI ERROR: FromReflectedMethod not implemented yet, exiting ...\n");
    VM.sysExit(VM.exitStatusUnsupportedInternalOp);
    return -1; 
  }

  private static int FromReflectedField(VM_JNIEnvironment env) {
    if (traceJNI) VM.sysWrite("JNI called: FromReflectedField \n");   
    VM.sysWrite("JNI ERROR: FromReflectedField not implemented yet, exiting ...\n");
    VM.sysExit(VM.exitStatusUnsupportedInternalOp);
    return -1; 
  }

  private static int ToReflectedMethod(VM_JNIEnvironment env) {
    if (traceJNI) VM.sysWrite("JNI called: ToReflectedMethod \n");   
    VM.sysWrite("JNI ERROR: ToReflectedMethod not implemented yet, exiting ...\n");
    VM.sysExit(VM.exitStatusUnsupportedInternalOp);
    return -1; 
  }

  private static int ToReflectedField(VM_JNIEnvironment env) {
    if (traceJNI) VM.sysWrite("JNI called: ToReflectedField \n");   
    VM.sysWrite("JNI ERROR: ToReflectedField not implemented yet, exiting ...\n");
    VM.sysExit(VM.exitStatusUnsupportedInternalOp);
    return -1; 
  }

  private static int PushLocalFrame(VM_JNIEnvironment env) {
    if (traceJNI) VM.sysWrite("JNI called: PushLocalFrame \n");   
    VM.sysWrite("JNI ERROR: PushLocalFrame not implemented yet, exiting ...\n");
    VM.sysExit(VM.exitStatusUnsupportedInternalOp);
    return -1; 
  }

  private static int PopLocalFrame(VM_JNIEnvironment env) {
    if (traceJNI) VM.sysWrite("JNI called: PopLocalFrame \n");   
    VM.sysWrite("JNI ERROR: PopLocalFrame not implemented yet, exiting ...\n");
    VM.sysExit(VM.exitStatusUnsupportedInternalOp);
    return -1; 
  }

  private static int NewLocalRef(VM_JNIEnvironment env) {
    if (traceJNI) VM.sysWrite("JNI called: NewLocalRef \n");   
    VM.sysWrite("JNI ERROR: NewLocalRef not implemented yet, exiting ...\n");
    VM.sysExit(VM.exitStatusUnsupportedInternalOp);
    return -1; 
  }

  private static int EnsureLocalCapacity(VM_JNIEnvironment env) {
    if (traceJNI) VM.sysWrite("JNI called: EnsureLocalCapacity \n");   
    VM.sysWrite("JNI ERROR: EnsureLocalCapacity not implemented yet, exiting ...\n");
    VM.sysExit(VM.exitStatusUnsupportedInternalOp);
    return -1; 
  }

  private static int GetStringRegion(VM_JNIEnvironment env) {
    if (traceJNI) VM.sysWrite("JNI called: GetStringRegion \n");   
    VM.sysWrite("JNI ERROR: GetStringRegion not implemented yet, exiting ...\n");
    VM.sysExit(VM.exitStatusUnsupportedInternalOp);
    return -1; 
  }

  private static int GetStringUTFRegion(VM_JNIEnvironment env) {
    if (traceJNI) VM.sysWrite("JNI called: GetStringUTFRegion \n");   
    VM.sysWrite("JNI ERROR: GetStringUTFRegion not implemented yet, exiting ...\n");
    VM.sysExit(VM.exitStatusUnsupportedInternalOp);
    return -1; 
  }

  /**
   * GetPrimitiveArrayCritical: return a direct pointer to the primitive array 
   * and disable GC so that the array will not be moved.  This function is intended
   * to be paired with the ReleasePrimitiveArrayCritical function within a short time
   * so that GC will be reenabled
   * @param a JREF index for the JNI environment object
   * @param arrayJREF a JREF index for the primitive array in Java
   * @param isCopyAddress address of isCopy jboolean (an int)
   * @return the address of the primitive array
   * @exception OutOfMemoryError is specified but will not be thrown in this implementation
   *            since no copy will be made
   */
  private static VM_Address GetPrimitiveArrayCritical(VM_JNIEnvironment env, int arrayJREF, VM_Address isCopyAddress) {

    if (traceJNI) VM.sysWrite("JNI called: GetPrimitiveArrayCritical \n");   

    try {
      Object primitiveArray = env.getJNIRef(arrayJREF);

      // not an array, return null
      if (!primitiveArray.getClass().isArray())
        return VM_Address.zero();

      // set Copy flag to false, if it's a valid address
      if (!isCopyAddress.isZero()) {
        int temp = VM_Magic.getMemoryInt(isCopyAddress);
        if (VM.LittleEndian) {
          VM_Magic.setMemoryInt(isCopyAddress, ((temp & 0xffffff00)));
        } else {
          VM_Magic.setMemoryInt(isCopyAddress, ((temp & 0x00ffffff)));
        }
      }

      // For array of primitive, return the object address, which is the array itself
      VM.disableGC();
      return VM_Magic.objectAsAddress(primitiveArray);
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return VM_Address.zero();
    }
  }

  /**
   * ReleasePrimitiveArrayCritical: this function is intended to be paired with the
   * GetPrimitiveArrayCritical function.  Since the native code has direct access
   * to the array, no copyback update is necessary;  GC is simply reenabled.
   * @param a JREF index for the JNI environment object
   * @param arrayJREF a JREF index for the primitive array in Java
   * @param arrayCopyAddress
   * @param mode a flag indicating whether to update the Java array with the copy and
   *             whether to free the copy. For this implementation, no copy was made
   *             so this flag has no effect.
   */
  private static void ReleasePrimitiveArrayCritical(VM_JNIEnvironment env, int arrayJREF, 
                                                    VM_Address arrayCopyAddress, int mode) {
    if (traceJNI) VM.sysWrite("JNI called: ReleasePrimitiveArrayCritical \n");   

    try {
      VM.enableGC();
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
    }
  }

  private static int GetStringCritical(VM_JNIEnvironment env) {
    if (traceJNI) VM.sysWrite("JNI called: GetStringCritical \n");   
    VM.sysWrite("JNI ERROR: GetStringCritical not implemented yet, exiting ...\n");
    VM.sysExit(VM.exitStatusUnsupportedInternalOp);
    return -1; 
  }

  private static int ReleaseStringCritical(VM_JNIEnvironment env) {
    if (traceJNI) VM.sysWrite("JNI called: ReleaseStringCritical \n");   
    VM.sysWrite("JNI ERROR: ReleaseStringCritical not implemented yet, exiting ...\n");
    VM.sysExit(VM.exitStatusUnsupportedInternalOp);
    return -1; 
  }

  private static int NewWeakGlobalRef(VM_JNIEnvironment env) {
    if (traceJNI) VM.sysWrite("JNI called: NewWeakGlobalRef \n");   
    VM.sysWrite("JNI ERROR: NewWeakGlobalRef not implemented yet, exiting ...\n");
    VM.sysExit(VM.exitStatusUnsupportedInternalOp);
    return -1; 
  }

  private static int DeleteWeakGlobalRef(VM_JNIEnvironment env) {
    if (traceJNI) VM.sysWrite("JNI called: DeleteWeakGlobalRef \n");   
    VM.sysWrite("JNI ERROR: DeleteWeakGlobalRef not implemented yet, exiting ...\n");
    VM.sysExit(VM.exitStatusUnsupportedInternalOp);
    return -1; 
  }

  private static int ExceptionCheck(VM_JNIEnvironment env) {
    if (traceJNI) VM.sysWrite("JNI called: ExceptionCheck \n");   

    return env.getException() == null ? 0 : 1;
  }

  private static int reserved0(VM_JNIEnvironment env) {
    VM.sysWrite("JNI ERROR: reserved function slot not implemented, exiting ...\n");
    VM.sysExit(VM.exitStatusUnsupportedInternalOp);
    return -1; 
  }

  private static int reserved1(VM_JNIEnvironment env){
    VM.sysWrite("JNI ERROR: reserved function slot not implemented, exiting ...\n");
    VM.sysExit(VM.exitStatusUnsupportedInternalOp);		       
    return -1; 		       
  }				       

  private static int reserved2(VM_JNIEnvironment env){	       
    VM.sysWrite("JNI ERROR: reserved function slot not implemented, exiting ...\n");
    VM.sysExit(VM.exitStatusUnsupportedInternalOp);		       
    return -1; 		       
  }				       				       

  private static int reserved3(VM_JNIEnvironment env){	       
    VM.sysWrite("JNI ERROR: reserved function slot not implemented, exiting ...\n");
    VM.sysExit(VM.exitStatusUnsupportedInternalOp);		       
    return -1; 		       
  }				       				       				      
}
