/*
 * (C) Copyright IBM Corp 2001,2002
 */
//$Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;
import com.ibm.JikesRVM.classloader.*;

import java.io.UTFDataFormatException;
import java.lang.reflect.*;

import com.ibm.JikesRVM.librarySupport.ReflectionSupport;

/**
 * This class implements the 211 JNI functions
 * All methods here will be specially compiled with the necessary prolog to
 * perform the transition from native code (AIX convention) to RVM
 * For this reason, no Java methods (including the JNI methods here) can call 
 * any methods in this class from within Java.  These JNI methods are to 
 * be invoked from native C or C++. <p>
 *
 * The first argument for all the functions is a JREF index for the 
 * VM_JNIEnvironment object of the thread.  Since the access method 
 * for the JREF index is itself virtual, we can't use this index to 
 * get the VM_JNIEnvironment. Rather, we use the current thread to 
 * access the VM_JNIEnvironment. <p>
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
public class VM_JNIFunctions implements VM_NativeBridge, VM_JNIConstants {
  // one message for each JNI function called from native
  final static boolean traceJNI = false;

  private static final boolean LittleEndian = VM.BuildForIA32;

  /**
   * GetVersion: the version of the JNI
   * @param a JREF index for the JNI environment object
   * @return 0x00010002 for Java 1.2, otherwise return 0x00010001
   */     
  private static int GetVersion(int envJREF) {
    if (traceJNI) VM.sysWrite("JNI called: GetVersion  \n");

    return 0x00010001;
  }


  private static int DefineClass(int envHandler) {
    if (traceJNI) VM.sysWrite("JNI called: DefineClass  \n");

    VM_Scheduler.traceback("JNI ERROR: DefineClass not implemented yet.");
    VM.sysExit(200);
    return DEFINECLASS ; 
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
  private static int FindClass(int envJREF, VM_Address classNameAddress) {
    if (traceJNI) VM.sysWrite("JNI called: FindClass  \n");

    VM_JNIEnvironment env;
    String classString = null;
    try {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      classString = VM_JNIEnvironment.createStringFromC(classNameAddress);
      if (traceJNI) VM.sysWriteln( classString );
      ClassLoader cl = com.ibm.JikesRVM.librarySupport.ClassLoaderSupport.getClassLoaderFromStackFrame(1);
      Class matchedClass = Class.forName(classString.replace('/', '.'), true, cl);
      return env.pushJNIRef(matchedClass);  
    } catch (ClassNotFoundException e) {
      e.printStackTrace();

      if (traceJNI) e.printStackTrace( System.err );
      env = VM_Thread.getCurrentThread().getJNIEnv();
      env.recordException(new NoClassDefFoundError(classString));
      return 0;
    } catch (Throwable unexpected) {
      if (traceJNI) unexpected.printStackTrace( System.err );
      env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static int GetSuperclass(int envJREF, int classJREF) {
    if (traceJNI) VM.sysWrite("JNI called: GetSuperclass  \n");

    VM_JNIEnvironment env;
    try {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      Class cls = (Class) env.getJNIRef(classJREF); 
      Class supercls = cls.getSuperclass();
      if (supercls==null)
        return 0;
      else
        return env.pushJNIRef(supercls);
    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static boolean IsAssignableFrom(int envJREF, int firstClassJREF, 
                                          int secondClassJREF) {
    if (traceJNI) VM.sysWrite("JNI called: IsAssignableFrom  \n");

    VM_JNIEnvironment env;
    try {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      Class cls1 = (Class) env.getJNIRef(firstClassJREF);
      Class cls2 = (Class) env.getJNIRef(secondClassJREF);
      if (cls1==null || cls2==null)
        return false;
      return cls2.isAssignableFrom(cls1);
    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static int Throw(int envJREF, int exceptionJREF) {
    if (traceJNI) VM.sysWrite("JNI called: Throw  \n");

    VM_JNIEnvironment env;
    try {
      env = VM_Thread.getCurrentThread().getJNIEnv();    
      env.recordException((Throwable) env.getJNIRef(exceptionJREF));
      return 0;
    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static int ThrowNew(int envJREF, int throwableClassJREF, VM_Address exceptionNameAddress) {
    if (traceJNI) VM.sysWrite("JNI called: ThrowNew  \n");

    VM_JNIEnvironment env;
    try {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      Class cls = (Class) env.getJNIRef(throwableClassJREF);
      // find the constructor that has a string as a parameter
      Class[] argClasses = new Class[1];
      argClasses[0] = Class.forName("java.lang.String");
      Constructor constMethod = cls.getConstructor(argClasses);
      // prepare the parameter list for reflective invocation
      Object[] argObjs = new Object[1];
      argObjs[0] = VM_JNIEnvironment.createStringFromC(exceptionNameAddress);

      // invoke the constructor to obtain a new Throwable object
      env.recordException((Throwable) constMethod.newInstance(argObjs));
      return 0;

    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static int ExceptionOccurred(int envJREF) {
    if (traceJNI) VM.sysWrite("JNI called: ExceptionOccurred  \n");

    VM_JNIEnvironment env;
    try {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      Throwable e = env.getException();
      if (e==null)
        return 0;
      else {
        if (traceJNI) {
          VM.sysWrite( e.toString() );
          VM.sysWrite("\n");
        }
        return env.pushJNIRef(e);
      }
    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      env.recordException(unexpected);
      if (traceJNI) {
        VM.sysWrite( unexpected.toString() );
        VM.sysWrite("\n");
      }
      return env.pushJNIRef(unexpected);
    }
  }



  /**
   * ExceptionDescribe: print the exception description and the stack trace back, 
   *                    then clear the exception
   * @param envJREF a JREF index for the JNI environment object
   */
  private static void ExceptionDescribe(int envJREF) {
    if (traceJNI) VM.sysWrite("JNI called: ExceptionDescribe  \n");

    VM_JNIEnvironment env;
    try {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      Throwable e = env.getException();
      if (e!=null) {
        e.printStackTrace();
        env.recordException(null);
      }
    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      env.recordException(null);            // clear old exception and register new one
      env.recordException(unexpected);
    }
  }


  /**
   * ExceptionClear
   * @param envJREF a JREF index for the JNI environment object
   */
  private static void ExceptionClear(int envJREF) {
    if (traceJNI) VM.sysWrite("JNI called: ExceptionClear  \n");

    VM_JNIEnvironment env;
    try {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      env.recordException(null);
    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static void FatalError(int envJREF, VM_Address messageAddress) {
    if (traceJNI) VM.sysWrite("JNI called: FatalError  \n");

    try {
      VM.sysWrite(VM_JNIEnvironment.createStringFromC(messageAddress));
      System.exit(0);
    } catch (Throwable unexpected) {
      System.exit(0);
    }
  }


  private static int NewGlobalRef(int envJREF, int objectJREF) {
    if (traceJNI) VM.sysWrite("JNI called: NewGlobalRef\n");
    try {
      VM_JNIEnvironment env = VM_Thread.getCurrentThread().getJNIEnv();
      Object obj1 = (Object) env.getJNIRef(objectJREF);
      return VM_JNIGlobalRefTable.newGlobalRef( obj1 );
    } catch (Throwable whatever) {
      VM_JNIEnvironment env = VM_Thread.getCurrentThread().getJNIEnv();
      env.recordException(whatever);
      return 0;
    }
  }

  private static void DeleteGlobalRef(int envJREF, int refJREF) {
    if (traceJNI) VM.sysWrite("JNI called: DeleteGlobalRef\n");
    try {
      VM_JNIEnvironment env = VM_Thread.getCurrentThread().getJNIEnv();
      VM_JNIGlobalRefTable.deleteGlobalRef( refJREF );
    } catch (Throwable whatever) {
      VM_JNIEnvironment env = VM_Thread.getCurrentThread().getJNIEnv();
      env.recordException(whatever);
    }
  }

  private static void DeleteLocalRef(int envJREF, int objJREF) {
    if (traceJNI) VM.sysWrite("JNI called: DeleteLocalRef\n");
    try {
      VM_JNIEnvironment env = VM_Thread.getCurrentThread().getJNIEnv();
      env.deleteJNIRef(objJREF);
    } catch (Throwable whatever) {
      VM_JNIEnvironment env = VM_Thread.getCurrentThread().getJNIEnv();
      env.recordException(whatever);
    }
  }


  /**
   * IsSameObject: determine if two references point to the same object
   * @param a JREF index for the JNI environment object
   * @param a JREF index for the first object
   * @param a JREF index for the second object
   * @return true if it's the same object, false otherwise
   */
  private static boolean IsSameObject(int envJREF, int obj1JREF, int obj2JREF) {
    if (traceJNI) VM.sysWrite("JNI called: IsSameObject  \n");

    VM_JNIEnvironment env;
    try {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      Object obj1 = (Object) env.getJNIRef(obj1JREF);
      Object obj2 = (Object) env.getJNIRef(obj2JREF);
      return (VM_Magic.objectAsAddress(obj1)==VM_Magic.objectAsAddress(obj2));
    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static int AllocObject(int envJREF, int classJREF) 
    throws InstantiationException, OutOfMemoryError {
      if (traceJNI) VM.sysWrite("JNI called: AllocObject  \n");

      VM_JNIEnvironment env;
      try {
        env = VM_Thread.getCurrentThread().getJNIEnv();
        Class javaCls = (Class) env.getJNIRef(classJREF);
        if (javaCls.isArray() || javaCls.isPrimitive() || javaCls.isInterface())
          throw new InstantiationException();

        VM_Class cls = java.lang.JikesRVMSupport.getTypeForClass(javaCls).asClass();
        if (cls.isAbstract() || cls.isInterface()) {
          env.recordException(new InstantiationException());
          return 0;
        }

	int allocator = VM_Interface.pickAllocator(cls);
        Object newObj = VM_Runtime.resolvedNewScalar(cls.getInstanceSize(), 
						     cls.getTypeInformationBlock(),
						     cls.hasFinalizer(),
						     allocator);
        if (newObj==null)
          return 0;
        else      
          return env.pushJNIRef(newObj);

      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static int NewObject(int envJREF, int classJREF, int methodID)
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: NewObject  \n");

      VM_JNIEnvironment env;
      try {
        env = VM_Thread.getCurrentThread().getJNIEnv();    
        Class cls = (Class) env.getJNIRef(classJREF); 
        VM_Class vmcls = java.lang.JikesRVMSupport.getTypeForClass(cls).asClass();

        if (vmcls.isAbstract() || vmcls.isInterface()) {
          env.recordException(new InstantiationException());
          return 0;
        }

        Object newobj = VM_JNIEnvironment.invokeInitializer(cls, methodID, VM_Address.zero(), false, true);

        return env.pushJNIRef(newobj);    

      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static int NewObjectV(int envJREF, int classJREF, int methodID, VM_Address argAddress)
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: NewObjectV  \n");

      VM_JNIEnvironment env;
      try {
        env = VM_Thread.getCurrentThread().getJNIEnv();    
        Class cls = (Class) env.getJNIRef(classJREF); 
        VM_Class vmcls = java.lang.JikesRVMSupport.getTypeForClass(cls).asClass();
        if (vmcls.isAbstract() || vmcls.isInterface()) {
          env.recordException(new InstantiationException());
          return 0;
        }

        Object newobj = VM_JNIEnvironment.invokeInitializer(cls, methodID, argAddress, false, false);

        return env.pushJNIRef(newobj);    

      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static int NewObjectA(int envJREF, int classJREF, int methodID, VM_Address argAddress) 
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: NewObjectA  \n");

      VM_JNIEnvironment env;
      try {
        env = VM_Thread.getCurrentThread().getJNIEnv();    
        Class cls = (Class) env.getJNIRef(classJREF);
        VM_Class vmcls = java.lang.JikesRVMSupport.getTypeForClass(cls).asClass();

        if (vmcls.isAbstract() || vmcls.isInterface()) {
          env.recordException(new InstantiationException());
          return 0;
        }

        Object newobj = VM_JNIEnvironment.invokeInitializer(cls, methodID, argAddress, true, false);

        return env.pushJNIRef(newobj);

      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static int GetObjectClass(int envJREF, int objJREF) {
    if (traceJNI) VM.sysWrite("JNI called: GetObjectClass  \n");

    VM_JNIEnvironment env;
    try {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      Object obj = (Object) env.getJNIRef(objJREF);
      return env.pushJNIRef(obj.getClass());
    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static int IsInstanceOf(int envJREF, int objJREF, int classJREF) {
    if (traceJNI) VM.sysWrite("JNI called: IsInstanceOf  \n");

    VM_JNIEnvironment env;
    try {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      Class cls = (Class) env.getJNIRef(classJREF);
      Object obj = (Object) env.getJNIRef(objJREF);
      if (obj == null) return 0; // null instanceof T is always false
      VM_Type RHStype = VM_ObjectModel.getObjectType(obj);
      VM_Type LHStype = java.lang.JikesRVMSupport.getTypeForClass(cls);
      return VM_DynamicTypeCheck.instanceOf(LHStype, RHStype) ? 1 : 0;
    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static int GetMethodID(int envJREF, int classJREF, 
                                 VM_Address methodNameAddress, VM_Address methodSigAddress) {
    if (traceJNI) VM.sysWrite("JNI called: GetMethodID  \n");

    VM_JNIEnvironment env = VM_Thread.getCurrentThread().getJNIEnv();
    try {

      // obtain the names as String from the native space
      String methodString = VM_JNIEnvironment.createStringFromC(methodNameAddress);
      VM_Atom methodName = VM_Atom.findOrCreateAsciiAtom(methodString);
      String sigString = VM_JNIEnvironment.createStringFromC(methodSigAddress);
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
  private static int CallObjectMethod(int envJREF, int objJREF, int methodID)
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: CallObjectMethod  \n");  

      VM_JNIEnvironment env;
      try {
        // dereference the object instance
        env = VM_Thread.getCurrentThread().getJNIEnv();    
        Object obj =  env.getJNIRef(objJREF);

        Object returnObj = VM_JNIEnvironment.invokeWithDotDotVarArg(obj, methodID, null, false);
        return env.pushJNIRef(returnObj);     

      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static int CallObjectMethodV(int envJREF, int objJREF, int methodID, VM_Address argAddress)
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: CallObjectMethodV  \n");

      VM_JNIEnvironment env;
      try {
        // dereference the object instance
        env = VM_Thread.getCurrentThread().getJNIEnv();    
        Object obj =  env.getJNIRef(objJREF);

        Object returnObj = VM_JNIEnvironment.invokeWithVarArg(obj, methodID, argAddress, null, false);
        return env.pushJNIRef(returnObj);     

      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static int CallObjectMethodA(int envJREF, int objJREF, int methodID, VM_Address argAddress)
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: CallObjectMethodA  \n");

      VM_JNIEnvironment env;
      try {
        // dereference the object instance
        env = VM_Thread.getCurrentThread().getJNIEnv();    
        Object obj =  env.getJNIRef(objJREF);

        Object returnObj = VM_JNIEnvironment.invokeWithJValue(obj, methodID, argAddress, null, false);
        return env.pushJNIRef(returnObj);     

      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static boolean CallBooleanMethod(int envJREF, int objJREF, int methodID) 
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: CallBooleanMethod  \n");

      VM_JNIEnvironment env;
      try {
        // dereference the object instance
        env = VM_Thread.getCurrentThread().getJNIEnv();    
        Object obj =  env.getJNIRef(objJREF);

        Object returnObj = VM_JNIEnvironment.invokeWithDotDotVarArg(obj, methodID, 
                                                                    VM_TypeReference.Boolean, 
								    false);
        return VM_Reflection.unwrapBoolean(returnObj);     // should be a wrapper for a boolean value

      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static boolean CallBooleanMethodV(int envJREF, int objJREF, int methodID, VM_Address argAddress) 
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: CallBooleanMethodV  \n");

      VM_JNIEnvironment env;
      try {
        // dereference the object instance
        env = VM_Thread.getCurrentThread().getJNIEnv();    
        Object obj =  env.getJNIRef(objJREF);

        Object returnObj = VM_JNIEnvironment.invokeWithVarArg(obj, methodID, argAddress, 
                                                              VM_TypeReference.Boolean, false);

        return VM_Reflection.unwrapBoolean(returnObj);     // should be a wrapper for a boolean value

      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static boolean CallBooleanMethodA(int envJREF, int objJREF, int methodID, VM_Address argAddress)
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: CallBooleanMethodA  \n");  

      VM_JNIEnvironment env;
      try {
        // dereference the object instance
        env = VM_Thread.getCurrentThread().getJNIEnv();    
        Object obj =  env.getJNIRef(objJREF);

        Object returnObj = VM_JNIEnvironment.invokeWithJValue(obj, methodID, argAddress, 
                                                              VM_TypeReference.Boolean, false);
        return VM_Reflection.unwrapBoolean(returnObj);     // should be a wrapper for a boolean value

      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static byte CallByteMethod(int envJREF, int objJREF, int methodID)
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: CallByteMethod  \n");

      VM_JNIEnvironment env;
      try {
        // dereference the object instance
        env = VM_Thread.getCurrentThread().getJNIEnv();    
        Object obj =  env.getJNIRef(objJREF);

        Object returnObj = VM_JNIEnvironment.invokeWithDotDotVarArg(obj, methodID, 
                                                                    VM_TypeReference.Byte, false);
        return VM_Reflection.unwrapByte(returnObj);     // should be a wrapper for a byte value

      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static byte CallByteMethodV(int envJREF, int objJREF, int methodID, VM_Address argAddress)
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: CallByteMethodV  \n");  

      VM_JNIEnvironment env;
      try {
        // dereference the object instance
        env = VM_Thread.getCurrentThread().getJNIEnv();    
        Object obj =  env.getJNIRef(objJREF);

        Object returnObj = VM_JNIEnvironment.invokeWithVarArg(obj, methodID, argAddress, 
                                                              VM_TypeReference.Byte, false);
        return VM_Reflection.unwrapByte(returnObj);     // should be a wrapper for a byte value

      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static byte CallByteMethodA(int envJREF, int objJREF, int methodID, VM_Address argAddress)
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: CallByteMethodA  \n");  

      VM_JNIEnvironment env;
      try {
        // dereference the object instance
        env = VM_Thread.getCurrentThread().getJNIEnv();    
        Object obj =  env.getJNIRef(objJREF);

        Object returnObj = VM_JNIEnvironment.invokeWithJValue(obj, methodID, argAddress, 
                                                              VM_TypeReference.Byte, false);
        return VM_Reflection.unwrapByte(returnObj);     // should be a wrapper for a byte value

      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static char CallCharMethod(int envJREF, int objJREF, int methodID)
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: CallCharMethod  \n");  

      VM_JNIEnvironment env;
      try {
        // dereference the object instance
        env = VM_Thread.getCurrentThread().getJNIEnv();    
        Object obj =  env.getJNIRef(objJREF);

        Object returnObj = VM_JNIEnvironment.invokeWithDotDotVarArg(obj, methodID, 
                                                                    VM_TypeReference.Char, false);
        return VM_Reflection.unwrapChar(returnObj);     // should be a wrapper for a char value

      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static char CallCharMethodV(int envJREF, int objJREF, int methodID, VM_Address argAddress)
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: CallCharMethodV  \n");  

      VM_JNIEnvironment env;
      try {
        // dereference the object instance
        env = VM_Thread.getCurrentThread().getJNIEnv();    
        Object obj =  env.getJNIRef(objJREF);

        Object returnObj = VM_JNIEnvironment.invokeWithVarArg(obj, methodID, argAddress, 
                                                              VM_TypeReference.Char, false);
        return VM_Reflection.unwrapChar(returnObj);     // should be a wrapper for a char value

      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static char CallCharMethodA(int envJREF, int objJREF, int methodID, VM_Address argAddress)
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: CallCharMethodA  \n");  

      VM_JNIEnvironment env;
      try {
        // dereference the object instance
        env = VM_Thread.getCurrentThread().getJNIEnv();    
        Object obj =  env.getJNIRef(objJREF);

        Object returnObj = VM_JNIEnvironment.invokeWithJValue(obj, methodID, argAddress, 
                                                              VM_TypeReference.Char, false);
        return VM_Reflection.unwrapChar(returnObj);     // should be a wrapper for a char value

      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static short CallShortMethod(int envJREF, int objJREF, int methodID)
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: CallShortMethod  \n");


      VM_JNIEnvironment env;
      try {
        // dereference the object instance
        env = VM_Thread.getCurrentThread().getJNIEnv();    
        Object obj =  env.getJNIRef(objJREF);

        Object returnObj = VM_JNIEnvironment.invokeWithDotDotVarArg(obj, methodID, 
                                                                    VM_TypeReference.Short, false);
        return VM_Reflection.unwrapShort(returnObj);     // should be a wrapper for a short value

      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static short CallShortMethodV(int envJREF, int objJREF, int methodID, VM_Address argAddress)
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: CallShortMethodV  \n");  

      VM_JNIEnvironment env;
      try {
        // dereference the object instance
        env = VM_Thread.getCurrentThread().getJNIEnv();    
        Object obj =  env.getJNIRef(objJREF);

        Object returnObj = VM_JNIEnvironment.invokeWithVarArg(obj, methodID, argAddress, 
                                                              VM_TypeReference.Short, false);
        return VM_Reflection.unwrapShort(returnObj);     // should be a wrapper for a short value

      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static short CallShortMethodA(int envJREF, int objJREF, int methodID, VM_Address argAddress)
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: CallShortMethodA  \n");  

      VM_JNIEnvironment env;
      try {
        // dereference the object instance
        env = VM_Thread.getCurrentThread().getJNIEnv();    
        Object obj =  env.getJNIRef(objJREF);

        Object returnObj = VM_JNIEnvironment.invokeWithJValue(obj, methodID, argAddress, 
                                                              VM_TypeReference.Short, false);
        return VM_Reflection.unwrapShort(returnObj);     // should be a wrapper for a short value

      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static int CallIntMethod(int envJREF, int objJREF, int methodID)
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: CallIntMethod  \n");  

      VM_JNIEnvironment env;
      try {
        // dereference the object instance
        env = VM_Thread.getCurrentThread().getJNIEnv();    
        Object obj =  env.getJNIRef(objJREF);

        Object returnObj = VM_JNIEnvironment.invokeWithDotDotVarArg(obj, methodID, 
                                                                    VM_TypeReference.Int, false);
        return VM_Reflection.unwrapInt(returnObj);     // should be a wrapper for an integer value

      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static int CallIntMethodV(int envJREF, int objJREF, int methodID, VM_Address argAddress)
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: CallIntMethodV  \n");  

      VM_JNIEnvironment env;
      try {
        // dereference the object instance
        env = VM_Thread.getCurrentThread().getJNIEnv();    
        Object obj =  env.getJNIRef(objJREF);

        Object returnObj = VM_JNIEnvironment.invokeWithVarArg(obj, methodID, argAddress, 
                                                              VM_TypeReference.Int, false);
        return VM_Reflection.unwrapInt(returnObj);     // should be a wrapper for an integer value

      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static int CallIntMethodA(int envJREF, int objJREF, int methodID, VM_Address argAddress)
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: CallIntMethodA  \n");  

      VM_JNIEnvironment env;
      try {
        // dereference the object instance
        env = VM_Thread.getCurrentThread().getJNIEnv();    
        Object obj =  env.getJNIRef(objJREF);

        Object returnObj = VM_JNIEnvironment.invokeWithJValue(obj, methodID, argAddress, 
                                                              VM_TypeReference.Int, false);
        return VM_Reflection.unwrapInt(returnObj);     // should be a wrapper for an integer value

      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static long CallLongMethod(int envJREF, int objJREF, int methodID)
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: CallLongMethod  \n");  

      VM_JNIEnvironment env;
      try {
        // dereference the object instance
        env = VM_Thread.getCurrentThread().getJNIEnv();    
        Object obj =  env.getJNIRef(objJREF);

        Object returnObj = VM_JNIEnvironment.invokeWithDotDotVarArg(obj, methodID, 
                                                                    VM_TypeReference.Long, false);
        return VM_Reflection.unwrapLong(returnObj);     // should be a wrapper for a long value

      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static long CallLongMethodV(int envJREF, int objJREF, int methodID, VM_Address argAddress)
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: CallLongMethodV  \n");  

      VM_JNIEnvironment env;
      try {
        // dereference the object instance
        env = VM_Thread.getCurrentThread().getJNIEnv();    
        Object obj =  env.getJNIRef(objJREF);

        Object returnObj = VM_JNIEnvironment.invokeWithVarArg(obj, methodID, argAddress, 
                                                              VM_TypeReference.Long, false);
        return VM_Reflection.unwrapLong(returnObj);     // should be a wrapper for a long value

      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static long CallLongMethodA(int envJREF, int objJREF, int methodID, VM_Address argAddress)
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: CallLongMethodA  \n");  

      VM_JNIEnvironment env;
      try {
        // dereference the object instance
        env = VM_Thread.getCurrentThread().getJNIEnv();    
        Object obj =  env.getJNIRef(objJREF);

        Object returnObj = VM_JNIEnvironment.invokeWithJValue(obj, methodID, argAddress, 
                                                              VM_TypeReference.Long, false);
        return VM_Reflection.unwrapLong(returnObj);     // should be a wrapper for a long value

      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static float CallFloatMethod(int envJREF, int objJREF, int methodID)
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: CallFloatMethod  \n");  

      VM_JNIEnvironment env;
      try {
        // dereference the object instance
        env = VM_Thread.getCurrentThread().getJNIEnv();    
        Object obj =  env.getJNIRef(objJREF);

        Object returnObj = VM_JNIEnvironment.invokeWithDotDotVarArg(obj, methodID, 
                                                                    VM_TypeReference.Float, false);
        return VM_Reflection.unwrapFloat(returnObj);     // should be a wrapper for a float value

      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static float CallFloatMethodV(int envJREF, int objJREF, int methodID, VM_Address argAddress)
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: CallFloatMethodV  \n");  

      VM_JNIEnvironment env;
      try {
        // dereference the object instance
        env = VM_Thread.getCurrentThread().getJNIEnv();    
        Object obj =  env.getJNIRef(objJREF);

        Object returnObj = VM_JNIEnvironment.invokeWithVarArg(obj, methodID, argAddress, 
                                                              VM_TypeReference.Float, false);
        return VM_Reflection.unwrapFloat(returnObj);     // should be a wrapper for a float value

      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static float CallFloatMethodA(int envJREF, int objJREF, int methodID, VM_Address argAddress)
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: CallFloatMethodA  \n");  

      VM_JNIEnvironment env;
      try {
        // dereference the object instance
        env = VM_Thread.getCurrentThread().getJNIEnv();    
        Object obj =  env.getJNIRef(objJREF);

        Object returnObj = VM_JNIEnvironment.invokeWithJValue(obj, methodID, argAddress, 
                                                              VM_TypeReference.Float, false);
        return VM_Reflection.unwrapFloat(returnObj);     // should be a wrapper for a float value

      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static double CallDoubleMethod(int envJREF, int objJREF, int methodID)
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: CallDoubleMethod  \n");  

      VM_JNIEnvironment env;
      try {
        // dereference the object instance
        env = VM_Thread.getCurrentThread().getJNIEnv();    
        Object obj =  env.getJNIRef(objJREF);

        Object returnObj = VM_JNIEnvironment.invokeWithDotDotVarArg(obj, methodID, 
                                                                    VM_TypeReference.Double, false);
        return VM_Reflection.unwrapDouble(returnObj);     // should be a wrapper for a double value

      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static double CallDoubleMethodV(int envJREF, int objJREF, int methodID, VM_Address argAddress)
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: CallDoubleMethodV  \n");

      VM_JNIEnvironment env;
      try {
        // dereference the object instance
        env = VM_Thread.getCurrentThread().getJNIEnv();    
        Object obj =  env.getJNIRef(objJREF);

        Object returnObj = VM_JNIEnvironment.invokeWithVarArg(obj, methodID, argAddress, 
                                                              VM_TypeReference.Double, false);
        return VM_Reflection.unwrapDouble(returnObj);     // should be a wrapper for a double value

      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static double CallDoubleMethodA(int envJREF, int objJREF, int methodID, VM_Address argAddress)
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: CallDoubleMethodA  \n");

      VM_JNIEnvironment env;
      try {
        // dereference the object instance
        env = VM_Thread.getCurrentThread().getJNIEnv();    
        Object obj =  env.getJNIRef(objJREF);

        Object returnObj = VM_JNIEnvironment.invokeWithJValue(obj, methodID, argAddress, 
                                                              VM_TypeReference.Double, false);
        return VM_Reflection.unwrapDouble(returnObj);     // should be a wrapper for a double value

      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static void CallVoidMethod(int envJREF, int objJREF, int methodID)
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: CallVoidMethod  \n");  

      VM_JNIEnvironment env;
      try {
        // dereference the object instance
        env = VM_Thread.getCurrentThread().getJNIEnv();    
        Object obj =  env.getJNIRef(objJREF);

        VM_JNIEnvironment.invokeWithDotDotVarArg(obj, methodID, VM_TypeReference.Void, false);

      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static void CallVoidMethodV(int envJREF, int objJREF, int methodID, VM_Address argAddress)
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: CallVoidMethodV  \n");

      VM_JNIEnvironment env;
      try {
        // dereference the object instance
        env = VM_Thread.getCurrentThread().getJNIEnv();    
        Object obj =  env.getJNIRef(objJREF);

        VM_JNIEnvironment.invokeWithVarArg(obj, methodID, argAddress, VM_TypeReference.Void, false);

      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static void CallVoidMethodA(int envJREF, int objJREF, int methodID, VM_Address argAddress)
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: CallVoidMethodA  \n");

      VM_JNIEnvironment env;
      try {
        // dereference the object instance
        env = VM_Thread.getCurrentThread().getJNIEnv();    
        Object obj =  env.getJNIRef(objJREF);

        VM_JNIEnvironment.invokeWithJValue(obj, methodID, argAddress, VM_TypeReference.Void, false);

      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static int CallNonvirtualObjectMethod(int envJREF, int objJREF, int classJREF, int methodID)
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: CallNonvirtualObjectMethod  \n");  

      VM_JNIEnvironment env;
      try {
        // dereference the object instance
        env = VM_Thread.getCurrentThread().getJNIEnv();    
        Object obj =  env.getJNIRef(objJREF);

        Object returnObj = VM_JNIEnvironment.invokeWithDotDotVarArg(obj, methodID, null, true);
        return env.pushJNIRef(returnObj);     

      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static int CallNonvirtualObjectMethodV(int envJREF, int objJREF, int classJREF, int methodID, VM_Address argAddress)
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: CallNonvirtualObjectMethodV  \n");

      VM_JNIEnvironment env;
      try {
        // dereference the object instance
        env = VM_Thread.getCurrentThread().getJNIEnv();    
        Object obj =  env.getJNIRef(objJREF);

        Object returnObj = VM_JNIEnvironment.invokeWithVarArg(obj, methodID, argAddress, null, true);
        return env.pushJNIRef(returnObj);     

      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static int CallNonvirtualObjectMethodA(int envJREF, int objJREF, int classJREF, int methodID, VM_Address argAddress)
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: CallNonvirtualObjectMethodA  \n");  

      VM_JNIEnvironment env;
      try {
        // dereference the object instance
        env = VM_Thread.getCurrentThread().getJNIEnv();    
        Object obj =  env.getJNIRef(objJREF);

        Object returnObj = VM_JNIEnvironment.invokeWithJValue(obj, methodID, argAddress, null, true);
        return env.pushJNIRef(returnObj);     

      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static boolean CallNonvirtualBooleanMethod(int envJREF, int objJREF, int classJREF, int methodID) 
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: CallNonvirtualBooleanMethod  \n");

      VM_JNIEnvironment env;
      try {
        // dereference the object instance
        env = VM_Thread.getCurrentThread().getJNIEnv();    
        Object obj =  env.getJNIRef(objJREF);

        Object returnObj = VM_JNIEnvironment.invokeWithDotDotVarArg(obj, methodID, VM_TypeReference.Boolean, true);
        return VM_Reflection.unwrapBoolean(returnObj);     // should be a wrapper for a boolean value

      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static boolean CallNonvirtualBooleanMethodV(int envJREF, int objJREF, int classJREF, int methodID, VM_Address argAddress) 
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: CallNonvirtualBooleanMethodV  \n");

      VM_JNIEnvironment env;
      try {
        // dereference the object instance
        env = VM_Thread.getCurrentThread().getJNIEnv();    
        Object obj =  env.getJNIRef(objJREF);

        Object returnObj = VM_JNIEnvironment.invokeWithVarArg(obj, methodID, argAddress, 
                                                              VM_TypeReference.Boolean, true);

        return VM_Reflection.unwrapBoolean(returnObj);     // should be a wrapper for a boolean value

      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static boolean CallNonvirtualBooleanMethodA(int envJREF, int objJREF, int classJREF, int methodID, VM_Address argAddress)
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: CallNonvirtualBooleanMethodA  \n");  

      VM_JNIEnvironment env;
      try {
        // dereference the object instance
        env = VM_Thread.getCurrentThread().getJNIEnv();    
        Object obj =  env.getJNIRef(objJREF);

        Object returnObj = VM_JNIEnvironment.invokeWithJValue(obj, methodID, argAddress, 
                                                              VM_TypeReference.Boolean, true);
        return VM_Reflection.unwrapBoolean(returnObj);     // should be a wrapper for a boolean value

      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static byte CallNonvirtualByteMethod(int envJREF, int objJREF, int classJREF, int methodID)
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: CallNonvirtualByteMethod  \n");


      VM_JNIEnvironment env;
      try {
        // dereference the object instance
        env = VM_Thread.getCurrentThread().getJNIEnv();    
        Object obj =  env.getJNIRef(objJREF);

        Object returnObj = VM_JNIEnvironment.invokeWithDotDotVarArg(obj, methodID, 
                                                                    VM_TypeReference.Byte, true);
        return VM_Reflection.unwrapByte(returnObj);     // should be a wrapper for a byte value

      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static byte CallNonvirtualByteMethodV(int envJREF, int objJREF, int classJREF, int methodID, VM_Address argAddress)
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: CallNonvirtualByteMethodV  \n");


      VM_JNIEnvironment env;
      try {
        // dereference the object instance
        env = VM_Thread.getCurrentThread().getJNIEnv();    
        Object obj =  env.getJNIRef(objJREF);

        Object returnObj = VM_JNIEnvironment.invokeWithVarArg(obj, methodID, argAddress, 
                                                              VM_TypeReference.Byte, true);
        return VM_Reflection.unwrapByte(returnObj);     // should be a wrapper for a byte value

      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static byte CallNonvirtualByteMethodA(int envJREF, int objJREF, int classJREF, int methodID, VM_Address argAddress)
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: CallNonvirtualByteMethodA  \n");


      VM_JNIEnvironment env;
      try {
        // dereference the object instance
        env = VM_Thread.getCurrentThread().getJNIEnv();    
        Object obj =  env.getJNIRef(objJREF);

        Object returnObj = VM_JNIEnvironment.invokeWithJValue(obj, methodID, argAddress, 
                                                              VM_TypeReference.Byte, true);
        return VM_Reflection.unwrapByte(returnObj);     // should be a wrapper for a byte value

      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static char CallNonvirtualCharMethod(int envJREF, int objJREF, int classJREF, int methodID)
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: CallNonvirtualCharMethod  \n");


      VM_JNIEnvironment env;
      try {
        // dereference the object instance
        env = VM_Thread.getCurrentThread().getJNIEnv();    
        Object obj =  env.getJNIRef(objJREF);

        Object returnObj = VM_JNIEnvironment.invokeWithDotDotVarArg(obj, methodID, 
                                                                    VM_TypeReference.Char, true);
        return VM_Reflection.unwrapChar(returnObj);     // should be a wrapper for a char value

      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static char CallNonvirtualCharMethodV(int envJREF, int objJREF, int classJREF, int methodID, VM_Address argAddress)
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: CallNonvirtualCharMethodV  \n");


      VM_JNIEnvironment env;
      try {
        // dereference the object instance
        env = VM_Thread.getCurrentThread().getJNIEnv();    
        Object obj =  env.getJNIRef(objJREF);

        Object returnObj = VM_JNIEnvironment.invokeWithVarArg(obj, methodID, argAddress, 
                                                              VM_TypeReference.Char, true);
        return VM_Reflection.unwrapChar(returnObj);     // should be a wrapper for a char value

      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static char CallNonvirtualCharMethodA(int envJREF, int objJREF, int classJREF, int methodID, VM_Address argAddress)
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: CallNonvirtualCharMethodA  \n");


      VM_JNIEnvironment env;
      try {
        // dereference the object instance
        env = VM_Thread.getCurrentThread().getJNIEnv();    
        Object obj =  env.getJNIRef(objJREF);

        Object returnObj = VM_JNIEnvironment.invokeWithJValue(obj, methodID, argAddress, 
                                                              VM_TypeReference.Char, true);
        return VM_Reflection.unwrapChar(returnObj);     // should be a wrapper for a char value

      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static short CallNonvirtualShortMethod(int envJREF, int objJREF, int classJREF, int methodID)
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: CallNonvirtualShortMethod  \n");


      VM_JNIEnvironment env;
      try {
        // dereference the object instance
        env = VM_Thread.getCurrentThread().getJNIEnv();    
        Object obj =  env.getJNIRef(objJREF);

        Object returnObj = VM_JNIEnvironment.invokeWithDotDotVarArg(obj, methodID, 
                                                                    VM_TypeReference.Short, true);
        return VM_Reflection.unwrapShort(returnObj);     // should be a wrapper for a short value

      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static short CallNonvirtualShortMethodV(int envJREF, int objJREF, int classJREF, int methodID, VM_Address argAddress)
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: CallNonvirtualShortMethodV  \n");


      VM_JNIEnvironment env;
      try {
        // dereference the object instance
        env = VM_Thread.getCurrentThread().getJNIEnv();    
        Object obj =  env.getJNIRef(objJREF);

        Object returnObj = VM_JNIEnvironment.invokeWithVarArg(obj, methodID, argAddress, 
                                                              VM_TypeReference.Short, true);
        return VM_Reflection.unwrapShort(returnObj);     // should be a wrapper for a short value

      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static short CallNonvirtualShortMethodA(int envJREF, int objJREF, int classJREF, int methodID, VM_Address argAddress)
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: CallNonvirtualShortMethodA  \n");


      VM_JNIEnvironment env;
      try {
        // dereference the object instance
        env = VM_Thread.getCurrentThread().getJNIEnv();    
        Object obj =  env.getJNIRef(objJREF);

        Object returnObj = VM_JNIEnvironment.invokeWithJValue(obj, methodID, argAddress, 
                                                              VM_TypeReference.Short, true);
        return VM_Reflection.unwrapShort(returnObj);     // should be a wrapper for a short value

      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static int CallNonvirtualIntMethod(int envJREF, int objJREF, int classJREF, int methodID)
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: CallNonvirtualIntMethod  \n");


      VM_JNIEnvironment env;
      try {
        // dereference the object instance
        env = VM_Thread.getCurrentThread().getJNIEnv();    
        Object obj =  env.getJNIRef(objJREF);

        Object returnObj = VM_JNIEnvironment.invokeWithDotDotVarArg(obj, methodID, 
                                                                    VM_TypeReference.Int, true);
        return VM_Reflection.unwrapInt(returnObj);     // should be a wrapper for an integer value

      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static int CallNonvirtualIntMethodV(int envJREF, int objJREF, int classJREF, int methodID, VM_Address argAddress)
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: CallNonvirtualIntMethodV  \n");


      VM_JNIEnvironment env;
      try {
        // dereference the object instance
        env = VM_Thread.getCurrentThread().getJNIEnv();    
        Object obj =  env.getJNIRef(objJREF);

        Object returnObj = VM_JNIEnvironment.invokeWithVarArg(obj, methodID, argAddress, 
                                                              VM_TypeReference.Int, true);
        return VM_Reflection.unwrapInt(returnObj);     // should be a wrapper for an integer value

      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static int CallNonvirtualIntMethodA(int envJREF, int objJREF, int classJREF, int methodID, VM_Address argAddress)
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: CallNonvirtualIntMethodA  \n");


      VM_JNIEnvironment env;
      try {
        // dereference the object instance
        env = VM_Thread.getCurrentThread().getJNIEnv();    
        Object obj =  env.getJNIRef(objJREF);

        Object returnObj = VM_JNIEnvironment.invokeWithJValue(obj, methodID, argAddress, 
                                                              VM_TypeReference.Int, true);
        return VM_Reflection.unwrapInt(returnObj);     // should be a wrapper for an integer value

      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static long CallNonvirtualLongMethod(int envJREF, int objJREF, int classJREF, int methodID)
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: CallNonvirtualLongMethod  \n");


      VM_JNIEnvironment env;
      try {
        // dereference the object instance
        env = VM_Thread.getCurrentThread().getJNIEnv();    
        Object obj =  env.getJNIRef(objJREF);

        Object returnObj = VM_JNIEnvironment.invokeWithDotDotVarArg(obj, methodID, 
                                                                    VM_TypeReference.Long, true);
        return VM_Reflection.unwrapLong(returnObj);     // should be a wrapper for a long value

      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static long CallNonvirtualLongMethodV(int envJREF, int objJREF, int classJREF, int methodID, VM_Address argAddress)
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: CallNonvirtualLongMethodV  \n");


      VM_JNIEnvironment env;
      try {
        // dereference the object instance
        env = VM_Thread.getCurrentThread().getJNIEnv();    
        Object obj =  env.getJNIRef(objJREF);

        Object returnObj = VM_JNIEnvironment.invokeWithVarArg(obj, methodID, argAddress, 
                                                              VM_TypeReference.Long, true);
        return VM_Reflection.unwrapLong(returnObj);     // should be a wrapper for a long value

      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static long CallNonvirtualLongMethodA(int envJREF, int objJREF, int classJREF, int methodID, VM_Address argAddress)
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: CallNonvirtualLongMethodA  \n");


      VM_JNIEnvironment env;
      try {
        // dereference the object instance
        env = VM_Thread.getCurrentThread().getJNIEnv();    
        Object obj =  env.getJNIRef(objJREF);

        Object returnObj = VM_JNIEnvironment.invokeWithJValue(obj, methodID, argAddress, 
                                                              VM_TypeReference.Long, true);
        return VM_Reflection.unwrapLong(returnObj);     // should be a wrapper for a long value

      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static float CallNonvirtualFloatMethod(int envJREF, int objJREF, int classJREF, int methodID)
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: CallNonvirtualFloatMethod  \n");


      VM_JNIEnvironment env;
      try {
        // dereference the object instance
        env = VM_Thread.getCurrentThread().getJNIEnv();    
        Object obj =  env.getJNIRef(objJREF);

        Object returnObj = VM_JNIEnvironment.invokeWithDotDotVarArg(obj, methodID, VM_TypeReference.Float, true);
        return VM_Reflection.unwrapFloat(returnObj);     // should be a wrapper for a float value

      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static float CallNonvirtualFloatMethodV(int envJREF, int objJREF, int classJREF, int methodID, VM_Address argAddress)
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: CallNonvirtualFloatMethodV  \n");


      VM_JNIEnvironment env;
      try {
        // dereference the object instance
        env = VM_Thread.getCurrentThread().getJNIEnv();    
        Object obj =  env.getJNIRef(objJREF);

        Object returnObj = VM_JNIEnvironment.invokeWithVarArg(obj, methodID, argAddress, 
                                                              VM_TypeReference.Float, true);
        return VM_Reflection.unwrapFloat(returnObj);     // should be a wrapper for a float value

      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static float CallNonvirtualFloatMethodA(int envJREF, int objJREF, int classJREF, int methodID, VM_Address argAddress)
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: CallNonvirtualFloatMethodA  \n");


      VM_JNIEnvironment env;
      try {
        // dereference the object instance
        env = VM_Thread.getCurrentThread().getJNIEnv();    
        Object obj =  env.getJNIRef(objJREF);

        Object returnObj = VM_JNIEnvironment.invokeWithJValue(obj, methodID, argAddress, 
                                                              VM_TypeReference.Float, true);
        return VM_Reflection.unwrapFloat(returnObj);     // should be a wrapper for a float value

      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static double CallNonvirtualDoubleMethod(int envJREF, int objJREF, int classJREF, int methodID)
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: CallNonvirtualDoubleMethod  \n");


      VM_JNIEnvironment env;
      try {
        // dereference the object instance
        env = VM_Thread.getCurrentThread().getJNIEnv();    
        Object obj =  env.getJNIRef(objJREF);

        Object returnObj = VM_JNIEnvironment.invokeWithDotDotVarArg(obj, methodID, VM_TypeReference.Double, true);
        return VM_Reflection.unwrapDouble(returnObj);     // should be a wrapper for a double value

      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static double CallNonvirtualDoubleMethodV(int envJREF, int objJREF, int classJREF, int methodID, VM_Address argAddress)
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: CallNonvirtualDoubleMethodV  \n");


      VM_JNIEnvironment env;
      try {
        // dereference the object instance
        env = VM_Thread.getCurrentThread().getJNIEnv();    
        Object obj =  env.getJNIRef(objJREF);

        Object returnObj = VM_JNIEnvironment.invokeWithVarArg(obj, methodID, argAddress, 
                                                              VM_TypeReference.Double, true);
        return VM_Reflection.unwrapDouble(returnObj);     // should be a wrapper for a double value

      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static double CallNonvirtualDoubleMethodA(int envJREF, int objJREF, int classJREF, int methodID, VM_Address argAddress)
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: CallNonvirtualDoubleMethodA  \n");


      VM_JNIEnvironment env;
      try {
        // dereference the object instance
        env = VM_Thread.getCurrentThread().getJNIEnv();    
        Object obj =  env.getJNIRef(objJREF);

        Object returnObj = VM_JNIEnvironment.invokeWithJValue(obj, methodID, argAddress, 
                                                              VM_TypeReference.Double, true);
        return VM_Reflection.unwrapDouble(returnObj);     // should be a wrapper for a double value

      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static void CallNonvirtualVoidMethod(int envJREF, int objJREF, int classJREF, int methodID)
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: CallNonvirtualVoidMethod  \n");


      VM_JNIEnvironment env;
      try {
        // dereference the object instance
        env = VM_Thread.getCurrentThread().getJNIEnv();    
        Object obj =  env.getJNIRef(objJREF);

        VM_JNIEnvironment.invokeWithDotDotVarArg(obj, methodID, VM_TypeReference.Void, true);

      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static void CallNonvirtualVoidMethodV(int envJREF, int objJREF, int classJREF, int methodID, VM_Address argAddress)
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: CallNonvirtualVoidMethodV  \n");


      VM_JNIEnvironment env;
      try {
        // dereference the object instance
        env = VM_Thread.getCurrentThread().getJNIEnv();    
        Object obj =  env.getJNIRef(objJREF);

        VM_JNIEnvironment.invokeWithVarArg(obj, methodID, argAddress, VM_TypeReference.Void, true);

      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static void CallNonvirtualVoidMethodA(int envJREF, int objJREF, int classJREF, int methodID, VM_Address argAddress)
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: CallNonvirtualVoidMethodA  \n");


      VM_JNIEnvironment env;
      try {
        // dereference the object instance
        env = VM_Thread.getCurrentThread().getJNIEnv();    
        Object obj =  env.getJNIRef(objJREF);

        VM_JNIEnvironment.invokeWithJValue(obj, methodID, argAddress, VM_TypeReference.Void, true);

      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
        env.recordException(unexpected);
      }

    }


  /**
   * GetFieldID:  return the offset into the object instance, which can be cached in 
   * native code and reused 
   * @param envJREF a JREF index for the JNI environment object
   * @param classJREF a JREF index for the VM_Class object
   * @param fieldNameAddress a raw address to a null-terminated string in C for the field name
   * @param descriptorAddress a raw address to a null-terminated string in C for the descriptor
   * @return the index into the  of an instance field given the class, field name 
   *         and type. Return 0 if the field is not found
   * @exception NoSuchFieldError if the specified field cannot be found
   * @exception ExceptionInInitializerError if the class initializer fails
   * @exception OutOfMemoryError if the system runs out of memory
   */
  private static int GetFieldID(int envJREF, int classJREF, 
                                VM_Address fieldNameAddress, VM_Address descriptorAddress) {

    if (traceJNI) VM.sysWrite("JNI called: GetFieldID  \n");  

    VM_JNIEnvironment  env;
    try {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      Class cls = (Class) env.getJNIRef(classJREF);

      String fieldString = VM_JNIEnvironment.createStringFromC(fieldNameAddress);
      VM_Atom fieldName = VM_Atom.findOrCreateAsciiAtom(fieldString);

      String descriptorString = VM_JNIEnvironment.createStringFromC(descriptorAddress);
      VM_Atom descriptor = VM_Atom.findOrCreateAsciiAtom(descriptorString);

      // VM.sysWrite("JNI.GetFieldID: reflection on instance field " + fieldString + ", type " + descriptorString + "\n");

      // list of all instance fields including superclasses
      VM_Field[] fields = java.lang.JikesRVMSupport.getTypeForClass(cls).getInstanceFields();
      int i = 0;
      int length = fields.length;
      for (i = 0; i < length; ++i) {
        if (fields[i].getName() == fieldName && fields[i].getDescriptor() == descriptor)
          return i+1;                  // return index as 1-based to avoid the 0 value used for not found
      }

      // create exception and return 0 if not found
      env.recordException(new NoSuchFieldError(fieldString + ", " + descriptorString + " of " + cls));
      return 0;                      

    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      env.recordException(unexpected);
      return 0;                      
    }

  }



  /**
   * GetObjectField: read a instance field of type Object
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the target object
   * @param fieldIndex the index for the VM_Field that describes this field, 
   *                   computed and saved earlier
   * @return the value of the Object field, converted to a JREF index
   *         or 0 if the fieldIndex is incorrect
   */
  private static int GetObjectField(int envJREF, int objJREF, int fieldIndex1) {
    if (traceJNI) VM.sysWrite("JNI called: GetObjectField  \n");

    int fieldIndex = fieldIndex1-1;
    // VM.sysWrite("GetObjectField: field at index " + fieldIndex + "\n");

    VM_JNIEnvironment env;
    try {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      // retrieve the real object from the side table
      Object obj =  env.getJNIRef(objJREF);

      VM_Field field = VM_JNIEnvironment.getFieldAtIndex(obj, fieldIndex);
      if (field!=null) {
        Object objVal = field.getObject(obj);
        return env.pushJNIRef(objVal);
      } else {
        return 0;
      }

    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      env.recordException(unexpected);
      return 0;                      
    }

  }



  /**
   * GetBooleanField: read an instance field of type boolean
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the target object
   * @param fieldIndex the index for the VM_Field that describes this field, 
   *                   computed and saved earlier
   * @return the value of the boolean field, or 0 if the fieldIndex is incorrect
   */
  private static int GetBooleanField(int envJREF, int objJREF, int fieldIndex1) {
    if (traceJNI) VM.sysWrite("JNI called: GetBooleanField  \n");

    int fieldIndex = fieldIndex1-1;
    // VM.sysWrite("GetBooleanField: field at index " + fieldIndex + "\n");

    VM_JNIEnvironment env;
    try {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      // retrieve the real object from the side table
      Object obj =  env.getJNIRef(objJREF);

      VM_Field field = VM_JNIEnvironment.getFieldAtIndex(obj, fieldIndex);

      if (field==null) 
        return 0;

      if (field.getBooleanValue(obj))
        return 1;
      else 
        return 0;

    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      env.recordException(unexpected);
      return 0;                      
    }

  }


  /**
   * GetByteField:  read an instance field of type byte
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the target object
   * @param fieldIndex the index for the VM_Field that describes this field, 
   *                   computed and saved earlier
   * @return the value of the byte field, or 0 if the fieldIndex is incorrect
   */
  private static int GetByteField(int envJREF, int objJREF, int fieldIndex1) {
    if (traceJNI) VM.sysWrite("JNI called: GetByteField  \n");

    int fieldIndex = fieldIndex1-1;
    // VM.sysWrite("GetByteField: field at index " + fieldIndex + "\n");

    VM_JNIEnvironment env;
    try {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      // retrieve the real object from the side table
      Object obj =  env.getJNIRef(objJREF);

      VM_Field field = VM_JNIEnvironment.getFieldAtIndex(obj, fieldIndex);
      if (field!=null) 
        return field.getByteValue(obj);
      else
        return 0;

    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      env.recordException(unexpected);
      return 0;                      
    }

  }


  /**
   * GetCharField:  read an instance field of type character
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the target object
   * @param fieldIndex the index for the VM_Field that describes this field, 
   *                   computed and saved earlier
   * @return the value of the character field, or 0 if the fieldIndex is incorrect
   */
  private static int GetCharField(int envJREF, int objJREF, int fieldIndex1) {
    if (traceJNI) VM.sysWrite("JNI called: GetCharField  \n");

    int fieldIndex = fieldIndex1-1;
    // VM.sysWrite("GetCharField: field at index " + fieldIndex + "\n");

    VM_JNIEnvironment env;
    try {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      // retrieve the real object from the side table
      Object obj =  env.getJNIRef(objJREF);

      VM_Field field = VM_JNIEnvironment.getFieldAtIndex(obj, fieldIndex);
      if (field!=null)
        return field.getCharValue(obj);
      else
        return 0;

    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      env.recordException(unexpected);
      return 0;                      
    }

  }


  /**
   * GetShortField:  read an instance field of type short
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the target object
   * @param fieldIndex the index for the VM_Field that describes this field, 
   *                   computed and saved earlier
   * @return the value of the short field, or 0 if the fieldIndex is incorrect
   */
  private static int GetShortField(int envJREF, int objJREF, int fieldIndex1) {
    if (traceJNI) VM.sysWrite("JNI called: GetShortField  \n");

    int fieldIndex = fieldIndex1-1;
    // VM.sysWrite("GetShortField: field at index " + fieldIndex + "\n");

    VM_JNIEnvironment env;
    try {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      // retrieve the real object from the side table
      Object obj =  env.getJNIRef(objJREF);

      VM_Field field = VM_JNIEnvironment.getFieldAtIndex(obj, fieldIndex);
      if (field!=null)
        return field.getShortValue(obj);
      else
        return 0;

    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      env.recordException(unexpected);
      return 0;                      
    }

  }


  /**
   * GetIntField:  read an instance field of type integer
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the target object
   * @param fieldIndex the index for the VM_Field that describes this field, 
   *                   computed and saved earlier
   * @return the value of the integer field, or 0 if the fieldIndex is incorrect
   */
  private static int GetIntField(int envJREF, int objJREF, int fieldIndex1) {
    if (traceJNI) VM.sysWrite("JNI called: GetIntField  \n");

    int fieldIndex = fieldIndex1-1;
    // VM.sysWrite("GetIntField: field at index " + fieldIndex + "\n");

    VM_JNIEnvironment env;
    try {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      // retrieve the real object from the side table
      Object obj =  env.getJNIRef(objJREF);

      VM_Field field = VM_JNIEnvironment.getFieldAtIndex(obj, fieldIndex);
      if (field!=null)
        return field.getIntValue(obj);
      else
        return 0;

    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      env.recordException(unexpected);
      return 0;                      
    }

  }


  /**
   * GetLongField:  read an instance field of type long
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the target object
   * @param fieldIndex the index for the VM_Field that describes this field, 
   *                   computed and saved earlier
   * @return the value of the long field which is 8 bytes, 
   *         or 0 if the fieldIndex is incorrect
   */
  private static long GetLongField(int envJREF, int objJREF, int fieldIndex1) {
    if (traceJNI) VM.sysWrite("JNI called: GetLongField  \n");

    int fieldIndex = fieldIndex1-1;
    // VM.sysWrite("GetLongField: field at index " + fieldIndex + "\n");

    VM_JNIEnvironment env;
    try {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      // retrieve the real object from the side table
      Object obj =  env.getJNIRef(objJREF);

      VM_Field field = VM_JNIEnvironment.getFieldAtIndex(obj, fieldIndex);
      if (field!=null)
        return field.getLongValue(obj);
      else
        return 0L;

    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      env.recordException(unexpected);
      return 0L;                      
    }

  }

  /**
   * GetFloatField:  read an instance field of type float
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the target object
   * @param fieldIndex the index for the VM_Field that describes this field, 
   *                   computed and saved earlier
   * @return the value of the float field which is 4 bytes, 
   *         or 0 if the fieldIndex is incorrect
   */
  private static float GetFloatField(int envJREF, int objJREF, int fieldIndex1) {
    if (traceJNI) VM.sysWrite("JNI called: GetFloatField  \n");

    int fieldIndex = fieldIndex1-1;
    // VM.sysWrite("GetFloatField: field at index " + fieldIndex + "\n");

    VM_JNIEnvironment env;
    try {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      // retrieve the real object from the side table
      Object obj =  env.getJNIRef(objJREF);

      VM_Field field = VM_JNIEnvironment.getFieldAtIndex(obj, fieldIndex);
      if (field!=null)
        return field.getFloatValue(obj);
      else
        return 0.0f;

    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      env.recordException(unexpected);
      return 0.0f;                      
    }

  }

  /**
   * GetDoubleField:  read an instance field of type double
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the target object
   * @param fieldIndex the index for the VM_Field that describes this field, 
   *                   computed and saved earlier
   * @return the value of the double field which is 8 bytes, 
   *         or 0 if the fieldIndex is incorrect
   */
  private static double GetDoubleField(int envJREF, int objJREF, int fieldIndex1) {
    if (traceJNI) VM.sysWrite("JNI called: GetDoubleField  \n");

    int fieldIndex = fieldIndex1-1;
    // VM.sysWrite("GetDoubleField: field at index " + fieldIndex + "\n");

    VM_JNIEnvironment env;
    try {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      // retrieve the real object from the side table
      Object obj =  env.getJNIRef(objJREF);

      VM_Field field = VM_JNIEnvironment.getFieldAtIndex(obj, fieldIndex);
      if (field!=null)
        return field.getDoubleValue(obj);
      else      
        return 0.0;

    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      env.recordException(unexpected);
      return 0.0;                      
    }

  }



  /**
   * SetObjectField: set a instance field of type Object
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the target object
   * @param fieldIndex the index for the VM_Field that describes this field, 
   *                   computed and saved earlier
   * @param valueJREF a JREF index for the value to assign
   */
  private static void SetObjectField(int envJREF, int objJREF, int fieldIndex1, int valueJREF) {
    if (traceJNI) VM.sysWrite("JNI called: SetObjectField  \n");

    int fieldIndex = fieldIndex1-1;
    // VM.sysWrite("SetObjectField: field at index " + fieldIndex + "\n");

    VM_JNIEnvironment env;
    try {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      // retrieve the real object from the side table
      Object obj =  env.getJNIRef(objJREF);
      Object value =  env.getJNIRef(valueJREF);

      VM_Field field = VM_JNIEnvironment.getFieldAtIndex(obj, fieldIndex);
      if (field!=null) {
        field.setObjectValue(obj,value);
      }

    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      env.recordException(unexpected);
    }


  }


  /**
   * SetBooleanField: set an instance field of type boolean
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the target object
   * @param fieldIndex the index for the VM_Field that describes this field, 
   *                   computed and saved earlier
   * @param value   boolean value to assign
   */
  private static void SetBooleanField(int envJREF, int objJREF, int fieldIndex1, boolean value) {
    if (traceJNI) VM.sysWrite("JNI called: SetBooleanField  \n");

    int fieldIndex = fieldIndex1-1;
    // VM.sysWrite("SetBooleanField: field at index " + fieldIndex + "\n");

    VM_JNIEnvironment env;
    try {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      // retrieve the real object from the side table
      Object obj =  env.getJNIRef(objJREF);

      VM_Field field = VM_JNIEnvironment.getFieldAtIndex(obj, fieldIndex);
      if (field != null)
        field.setBooleanValue(obj,value);    

    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      env.recordException(unexpected);
    }

  }


  /**
   * SetByteField: set an instance field of type byte
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the target object
   * @param fieldIndex the index for the VM_Field that describes this field, 
   *                   computed and saved earlier
   * @param value   byte value to assign
   */
  private static void SetByteField(int envJREF, int objJREF, int fieldIndex1, byte value) {
    if (traceJNI) VM.sysWrite("JNI called: SetByteField  \n");

    int fieldIndex = fieldIndex1-1;
    // VM.sysWrite("SetByteField: field at index " + fieldIndex + "\n");

    VM_JNIEnvironment env;
    try {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      // retrieve the real object from the side table
      Object obj =  env.getJNIRef(objJREF);

      VM_Field field = VM_JNIEnvironment.getFieldAtIndex(obj, fieldIndex);
      if (field != null)
        field.setByteValue(obj,value);    

    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      env.recordException(unexpected);
    }

  }


  /**
   * SetCharField: set an instance field of type char
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the target object
   * @param fieldIndex the index for the VM_Field that describes this field, 
   *                   computed and saved earlier
   * @param value   char value to assign
   */
  private static void SetCharField(int envJREF, int objJREF, int fieldIndex1, char value) {
    if (traceJNI) VM.sysWrite("JNI called: SetCharField  \n");

    int fieldIndex = fieldIndex1-1;
    // VM.sysWrite("SetCharField: field at index " + fieldIndex + "\n");

    VM_JNIEnvironment env;
    try {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      // retrieve the real object from the side table
      Object obj =  env.getJNIRef(objJREF);

      VM_Field field = VM_JNIEnvironment.getFieldAtIndex(obj, fieldIndex);
      if (field != null)
        field.setCharValue(obj,value);    

    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      env.recordException(unexpected);
    }

  }


  /**
   * SetShortField: set an instance field of type short
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the target object
   * @param fieldIndex the index for the VM_Field that describes this field, 
   *                   computed and saved earlier
   * @param value   short value to assign
   */
  private static void SetShortField(int envJREF, int objJREF, int fieldIndex1, short value) {
    if (traceJNI) VM.sysWrite("JNI called: SetShortField  \n");

    int fieldIndex = fieldIndex1-1;
    // VM.sysWrite("SetShortField: field at index " + fieldIndex + "\n");

    VM_JNIEnvironment env;
    try {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      // retrieve the real object from the side table
      Object obj =  env.getJNIRef(objJREF);

      VM_Field field = VM_JNIEnvironment.getFieldAtIndex(obj, fieldIndex);
      if (field != null)
        field.setShortValue(obj,value);    

    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      env.recordException(unexpected);
    }

  }


  /**
   * SetIntField: set an instance field of type integer
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the target object
   * @param fieldIndex the index for the VM_Field that describes this field, 
   *                   computed and saved earlier
   * @param value   integer value to assign
   */
  private static void SetIntField(int envJREF, int objJREF, int fieldIndex1, int value) {
    if (traceJNI) VM.sysWrite("JNI called: SetIntField  \n");

    int fieldIndex = fieldIndex1-1;
    // VM.sysWrite("SetIntField: field at index " + fieldIndex + "\n");

    VM_JNIEnvironment env;
    try {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      // retrieve the real object from the side table
      Object obj =  env.getJNIRef(objJREF);

      VM_Field field = VM_JNIEnvironment.getFieldAtIndex(obj, fieldIndex);
      if (field != null)
        field.setIntValue(obj,value);    

    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      env.recordException(unexpected);
    }

  }


  /**
   * SetLongField: set an instance field of type long
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the target object
   * @param fieldIndex the index for the VM_Field that describes this field, 
   *                   computed and saved earlier
   * @param value   long value to assign
   */
  private static void SetLongField(int envJREF, int objJREF, int fieldIndex1, long value) {
    if (traceJNI) VM.sysWrite("JNI called: SetLongField  \n");

    int fieldIndex = fieldIndex1-1;
    // VM.sysWrite("SetLongField: field at index " + fieldIndex + "\n");

    VM_JNIEnvironment env;
    try {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      // retrieve the real object from the side table
      Object obj =  env.getJNIRef(objJREF);

      VM_Field field = VM_JNIEnvironment.getFieldAtIndex(obj, fieldIndex);
      if (field != null)
        field.setLongValue(obj,value);    

    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      env.recordException(unexpected);
    }

  }


  /**
   * SetFloatField: set an instance field of type float
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the target object
   * @param fieldIndex the index for the VM_Field that describes this field, 
   *                   computed and saved earlier
   * @param value   float value to assign
   */
  private static void SetFloatField(int envJREF, int objJREF, int fieldIndex1, float value) {
    if (traceJNI) VM.sysWrite("JNI called: SetFloatField  \n");

    int fieldIndex = fieldIndex1-1;
    // VM.sysWrite("SetFloatField: field at index " + fieldIndex + "\n");

    VM_JNIEnvironment env;
    try {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      // retrieve the real object from the side table
      Object obj =  env.getJNIRef(objJREF);

      VM_Field field = VM_JNIEnvironment.getFieldAtIndex(obj, fieldIndex);
      if (field != null)
        field.setFloatValue(obj,value);    

    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      env.recordException(unexpected);
    }

  }


  /**
   * SetDoubleField: set an instance field of type double
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the target object
   * @param fieldIndex the index for the VM_Field that describes this field, 
   *                   computed and saved earlier
   * @param value   double value to assign
   */
  private static void SetDoubleField(int envJREF, int objJREF, int fieldIndex1, double value) {
    if (traceJNI) VM.sysWrite("JNI called: SetDoubleField  \n");

    int fieldIndex = fieldIndex1-1;
    // VM.sysWrite("SetDoubleField: field at index " + fieldIndex + "\n");

    VM_JNIEnvironment env;
    try {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      // retrieve the real object from the side table
      Object obj =  env.getJNIRef(objJREF);

      VM_Field field = VM_JNIEnvironment.getFieldAtIndex(obj, fieldIndex);
      if (field != null)
        field.setDoubleValue(obj,value);    

    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      env.recordException(unexpected);
    }

  }


  /**
   * GetStaticMethodID:  return the method ID for invocation later
   * @param envJREF a JREF index for the JNI environment object
   * @param classJREF a JREF index for the class object
   * @methodNameAddress a raw address to a null-terminated string in C for the method name
   * @methodSigAddress a raw address to a null-terminated string in C for the method name
   * @return a method ID or null if it fails
   * @exception NoSuchMethodError if the method is not found
   * @exception ExceptionInInitializerError if the initializer fails
   * @exception OutOfMemoryError if the system runs out of memory
   */
  private static int GetStaticMethodID(int envJREF, int classJREF, VM_Address methodNameAddress, VM_Address methodSigAddress) {
    if (traceJNI) VM.sysWrite("JNI called: GetStaticMethodID  \n");


    VM_JNIEnvironment env = VM_Thread.getCurrentThread().getJNIEnv();
    try {
      // obtain the names as String from the native space
      String methodString = VM_JNIEnvironment.createStringFromC(methodNameAddress);
      VM_Atom methodName = VM_Atom.findOrCreateAsciiAtom(methodString);
      String sigString = VM_JNIEnvironment.createStringFromC(methodSigAddress);
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
      if (traceJNI) {
	  VM.sysWrite(" GetStaticMethodID: unexpected exception " + unexpected.toString() + "\n");
	  unexpected.printStackTrace( System.err );
      }
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
  private static int CallStaticObjectMethod(int envJREF, int classJREF, int methodID) 
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: CallStaticObjectMethod  \n");


      VM_JNIEnvironment env;
      try {
        Object returnObj = VM_JNIEnvironment.invokeWithDotDotVarArg(methodID, null);

        // this should be the object itself, insert into the side table and substitute with the JREF index
        env = VM_Thread.getCurrentThread().getJNIEnv();    
        return env.pushJNIRef(returnObj);

      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static int CallStaticObjectMethodV(int envJREF, int classJREF, int methodID, VM_Address argAddress) 
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: CallStaticObjectMethodV  \n");


      VM_JNIEnvironment env;
      try {
        Object returnObj = VM_JNIEnvironment.invokeWithVarArg(methodID, argAddress, null);    

        // this should be the object itself, insert into the side table and substitute with the JREF index
        env = VM_Thread.getCurrentThread().getJNIEnv();    
        return env.pushJNIRef(returnObj);           

      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static int CallStaticObjectMethodA(int envJREF, int classJREF, int methodID, VM_Address argAddress) 
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: CallStaticObjectMethodA  \n");


      VM_JNIEnvironment env;
      try {
        Object returnObj = VM_JNIEnvironment.invokeWithJValue(methodID, argAddress, null);    

        // this should be the object itself, insert into the side table and substitute with the JREF index
        env = VM_Thread.getCurrentThread().getJNIEnv();    
        return env.pushJNIRef(returnObj);           

      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static boolean CallStaticBooleanMethod(int envJREF, int classJREF, int methodID) 
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: CallStaticBooleanMethod  \n");


      VM_JNIEnvironment env;
      try {
        Object returnObj = VM_JNIEnvironment.invokeWithDotDotVarArg(methodID, VM_TypeReference.Boolean);
        return VM_Reflection.unwrapBoolean(returnObj);     // should be a wrapper for a boolean value
      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static boolean CallStaticBooleanMethodV(int envJREF, int classJREF, int methodID, VM_Address argAddress) 
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: CallStaticBooleanMethodV  \n");


      VM_JNIEnvironment env;
      try {
        Object returnObj = VM_JNIEnvironment.invokeWithVarArg(methodID, argAddress, VM_TypeReference.Boolean);    
        return VM_Reflection.unwrapBoolean(returnObj);     // should be a wrapper for a boolean value
      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static boolean CallStaticBooleanMethodA(int envJREF, int classJREF, int methodID, VM_Address argAddress) 
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: CallStaticBooleanMethodA  \n");


      VM_JNIEnvironment env;
      try {
        Object returnObj = VM_JNIEnvironment.invokeWithJValue(methodID, argAddress, VM_TypeReference.Boolean);    
        return VM_Reflection.unwrapBoolean(returnObj);     // should be a wrapper for a boolean value
      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static byte CallStaticByteMethod(int envJREF, int classJREF, int methodID) 
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: CallStaticByteMethod  \n");


      VM_JNIEnvironment env;
      try {
        Object returnObj = VM_JNIEnvironment.invokeWithDotDotVarArg(methodID, VM_TypeReference.Byte);
        return VM_Reflection.unwrapByte(returnObj);     // should be a wrapper for a byte value
      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static byte CallStaticByteMethodV(int envJREF, int classJREF, int methodID, VM_Address argAddress) 
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: CallStaticByteMethodV  \n");


      VM_JNIEnvironment env;
      try {
        Object returnObj = VM_JNIEnvironment.invokeWithVarArg(methodID, argAddress, VM_TypeReference.Byte);    
        return VM_Reflection.unwrapByte(returnObj);     // should be a wrapper for a byte value
      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static byte CallStaticByteMethodA(int envJREF, int classJREF, int methodID, VM_Address argAddress) 
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: CallStaticByteMethodA  \n");


      VM_JNIEnvironment env;
      try {
        Object returnObj = VM_JNIEnvironment.invokeWithJValue(methodID, argAddress, VM_TypeReference.Byte);    
        return VM_Reflection.unwrapByte(returnObj);     // should be a wrapper for a byte value
      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static char CallStaticCharMethod(int envJREF, int classJREF, int methodID) 
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: CallStaticCharMethod  \n");


      VM_JNIEnvironment env;
      try {
        Object returnObj = VM_JNIEnvironment.invokeWithDotDotVarArg(methodID, VM_TypeReference.Char);
        return VM_Reflection.unwrapChar(returnObj);     // should be a wrapper for a char value
      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static char CallStaticCharMethodV(int envJREF, int classJREF, int methodID, VM_Address argAddress) 
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: CallStaticCharMethodV  \n");


      VM_JNIEnvironment env;
      try {
        Object returnObj = VM_JNIEnvironment.invokeWithVarArg(methodID, argAddress, VM_TypeReference.Char);    
        return VM_Reflection.unwrapChar(returnObj);     // should be a wrapper for a char value
      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static char CallStaticCharMethodA(int envJREF, int classJREF, int methodID, VM_Address argAddress) 
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: CallStaticCharMethodA  \n");


      VM_JNIEnvironment env;
      try {
        Object returnObj = VM_JNIEnvironment.invokeWithJValue(methodID, argAddress, VM_TypeReference.Char);    
        return VM_Reflection.unwrapChar(returnObj);     // should be a wrapper for a char value
      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static short CallStaticShortMethod(int envJREF, int classJREF, int methodID) 
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: CallStaticShortMethod  \n");


      VM_JNIEnvironment env;
      try {
        Object returnObj = VM_JNIEnvironment.invokeWithDotDotVarArg(methodID, VM_TypeReference.Short);
        return VM_Reflection.unwrapShort(returnObj);     // should be a wrapper for an short value
      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static short CallStaticShortMethodV(int envJREF, int classJREF, int methodID, VM_Address argAddress) 
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: CallStaticShortMethodV  \n");


      VM_JNIEnvironment env;
      try {
        Object returnObj = VM_JNIEnvironment.invokeWithVarArg(methodID, argAddress, VM_TypeReference.Short);    
        return VM_Reflection.unwrapShort(returnObj);     // should be a wrapper for a short value
      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static short CallStaticShortMethodA(int envJREF, int classJREF, int methodID, VM_Address argAddress) 
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: CallStaticShortMethodA  \n");


      VM_JNIEnvironment env;
      try {
        Object returnObj = VM_JNIEnvironment.invokeWithJValue(methodID, argAddress, VM_TypeReference.Short);    
        return VM_Reflection.unwrapShort(returnObj);     // should be a wrapper for a short value
      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static int CallStaticIntMethod(int envJREF, int classJREF, int methodID) 
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: CallStaticIntMethod  \n");


      VM_JNIEnvironment env;
      try {
        Object returnObj = VM_JNIEnvironment.invokeWithDotDotVarArg(methodID, VM_TypeReference.Int);
        return VM_Reflection.unwrapInt(returnObj);     // should be a wrapper for an integer value
      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static int CallStaticIntMethodV(int envJREF, int classJREF, int methodID, VM_Address argAddress)
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: CallStaticIntMethodV  \n");


      VM_JNIEnvironment env;
      try {
        Object returnObj = VM_JNIEnvironment.invokeWithVarArg(methodID, argAddress, VM_TypeReference.Int);    
        return VM_Reflection.unwrapInt(returnObj);     // should be a wrapper for an integer value
      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static int CallStaticIntMethodA(int envJREF, int classJREF, int methodID, VM_Address argAddress) 
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: CallStaticIntMethodA  \n");


      VM_JNIEnvironment env;
      try {
        Object returnObj = VM_JNIEnvironment.invokeWithJValue(methodID, argAddress, VM_TypeReference.Int);    
        return VM_Reflection.unwrapInt(returnObj);     // should be a wrapper for an integer value
      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static long CallStaticLongMethod(int envJREF, int classJREF, int methodID) 
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: CallStaticLongMethod  \n");


      VM_JNIEnvironment env;
      try {
        Object returnObj = VM_JNIEnvironment.invokeWithDotDotVarArg(methodID, VM_TypeReference.Long);
        return VM_Reflection.unwrapLong(returnObj);     // should be a wrapper for a long value
      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static long CallStaticLongMethodV(int envJREF, int classJREF, int methodID, VM_Address argAddress) 
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: CallStaticLongMethodV  \n");


      VM_JNIEnvironment env;
      try {
        Object returnObj = VM_JNIEnvironment.invokeWithVarArg(methodID, argAddress, VM_TypeReference.Long);    
        return VM_Reflection.unwrapLong(returnObj);     // should be a wrapper for a long value
      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static long CallStaticLongMethodA(int envJREF, int classJREF, int methodID, VM_Address argAddress) 
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: CallStaticLongMethodA  \n");


      VM_JNIEnvironment env;
      try {
        Object returnObj = VM_JNIEnvironment.invokeWithJValue(methodID, argAddress, VM_TypeReference.Long);    
        return VM_Reflection.unwrapLong(returnObj);     // should be a wrapper for a long value
      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static float CallStaticFloatMethod(int envJREF, int classJREF, int methodID) 
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: CallStaticFloatMethod  \n");


      VM_JNIEnvironment env;
      try {
        Object returnObj = VM_JNIEnvironment.invokeWithDotDotVarArg(methodID, VM_TypeReference.Float);
        return VM_Reflection.unwrapFloat(returnObj);     // should be a wrapper for a float value
      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
        env.recordException(unexpected);
        return 0.0f;
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
  private static float CallStaticFloatMethodV(int envJREF, int classJREF, int methodID, VM_Address argAddress) 
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: CallStaticFloatMethodV  \n");


      VM_JNIEnvironment env;
      try {
        Object returnObj = VM_JNIEnvironment.invokeWithVarArg(methodID, argAddress, VM_TypeReference.Float);    
        return VM_Reflection.unwrapFloat(returnObj);     // should be a wrapper for a float value
      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
        env.recordException(unexpected);
        return 0.0f;
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
  private static float CallStaticFloatMethodA(int envJREF, int classJREF, int methodID, VM_Address argAddress) 
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: CallStaticFloatMethodA  \n");


      VM_JNIEnvironment env;
      try {
        Object returnObj = VM_JNIEnvironment.invokeWithJValue(methodID, argAddress, VM_TypeReference.Float);    
        return VM_Reflection.unwrapFloat(returnObj);     // should be a wrapper for a float value
      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
        env.recordException(unexpected);
        return 0.0f;
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
  private static double CallStaticDoubleMethod(int envJREF, int classJREF, int methodID) 
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: CallStaticDoubleMethod  \n");


      VM_JNIEnvironment env;
      try {
        Object returnObj = VM_JNIEnvironment.invokeWithDotDotVarArg(methodID, VM_TypeReference.Double);
        return VM_Reflection.unwrapDouble(returnObj);     // should be a wrapper for a double value
      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
        env.recordException(unexpected);
        return 0.0;
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
  private static double CallStaticDoubleMethodV(int envJREF, int classJREF, int methodID, VM_Address argAddress) 
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: CallStaticDoubleMethodV  \n");


      VM_JNIEnvironment env;
      try {
        Object returnObj = VM_JNIEnvironment.invokeWithVarArg(methodID, argAddress, VM_TypeReference.Double);    
        return VM_Reflection.unwrapDouble(returnObj);     // should be a wrapper for a double value
      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
        env.recordException(unexpected);
        return 0.0;
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
  private static double CallStaticDoubleMethodA(int envJREF, int classJREF, int methodID, VM_Address argAddress) 
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: CallStaticDoubleMethodA  \n");


      VM_JNIEnvironment env;
      try {
        Object returnObj = VM_JNIEnvironment.invokeWithJValue(methodID, argAddress, VM_TypeReference.Double);    
        return VM_Reflection.unwrapDouble(returnObj);     // should be a wrapper for a double value
      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
        env.recordException(unexpected);
        return 0.0;
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
  private static void CallStaticVoidMethod(int envJREF, int classJREF, int methodID) 
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: CallStaticVoidMethod  \n");


      VM_JNIEnvironment env;
      try {
        // return nothing
        Object returnObj = VM_JNIEnvironment.invokeWithDotDotVarArg(methodID, VM_TypeReference.Void);
      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static void CallStaticVoidMethodV(int envJREF, int classJREF, int methodID, VM_Address argAddress) 
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: CallStaticVoidMethodV  \n");


      VM_JNIEnvironment env;
      try {
        // return nothing
        Object returnObj = VM_JNIEnvironment.invokeWithVarArg(methodID, argAddress, VM_TypeReference.Void);    
      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static void CallStaticVoidMethodA(int envJREF, int classJREF, int methodID, VM_Address argAddress) 
    throws Exception {
      if (traceJNI) VM.sysWrite("JNI called: CallStaticVoidMethodA  \n");


      VM_JNIEnvironment env;
      try {
        // return nothing
        Object returnObj = VM_JNIEnvironment.invokeWithJValue(methodID, argAddress, VM_TypeReference.Void);    
      } catch (Throwable unexpected) {
        env = VM_Thread.getCurrentThread().getJNIEnv();
        env.recordException(unexpected);
      }

    }


  /**
   * GetStaticFieldID:  return the offset into the JTOC, which can be cached in 
   *                    native code and reused (use the offset into the JTOC)
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
  private static int GetStaticFieldID(int envJREF, int classJREF, 
                                      VM_Address fieldNameAddress, VM_Address descriptorAddress) {
    if (traceJNI) VM.sysWrite("JNI called: GetStaticFieldID  \n");


    VM_JNIEnvironment env;
    try {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      Class cls = (Class) env.getJNIRef(classJREF);

      String fieldString = VM_JNIEnvironment.createStringFromC(fieldNameAddress);
      VM_Atom fieldName = VM_Atom.findOrCreateAsciiAtom(fieldString);

      String descriptorString = VM_JNIEnvironment.createStringFromC(descriptorAddress);
      VM_Atom descriptor = VM_Atom.findOrCreateAsciiAtom(descriptorString);

      // VM.sysWrite("JNI.GetStaticFieldID: reflection on field " + fieldString + ", type " + descriptorString + "\n");

      // list of all instance fields including superclasses
      VM_Field[] fields = java.lang.JikesRVMSupport.getTypeForClass(cls).getStaticFields();
      VM_Field field = null;
      for (int i = 0; i < fields.length; ++i) {
        field = fields[i];
        if (field.getName() == fieldName && field.getDescriptor() == descriptor)
          break;
      }

      if (field==null) {
        env.recordException(new NoSuchFieldError());
        return 0;
      } else {
        return field.getOffset() ;     
      }

    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      env.recordException(unexpected);
      return 0;
    }


  }



  /**
   * GetStaticObjectField: read a static field of type Object
   * @param envJREF a JREF index for the JNI environment object
   * @param classJREF a JREF index for the VM_Class object
   * @param fieldOffset the offset into the JTOC for this field, computed and saved earlier
   * @return the value of the Object field, converted to a JREF index
   *         or 0 if the fieldOffset is incorrect
   */
  private static int GetStaticObjectField(int envJREF, int classJREF, int fieldOffset) {
    if (traceJNI) VM.sysWrite("JNI called: GetStaticObjectField  \n");


    VM_JNIEnvironment env;
    try {
      int slot = fieldOffset>>2;
      env = VM_Thread.getCurrentThread().getJNIEnv();
      if (slot<VM_Statics.getNumberOfSlots() && VM_Statics.isReference(slot)) {
        // place this reference in the stack of the JNI environment
        // and obtain its JREF index to return
        Object ref =  VM_Statics.getSlotContentsAsObject(slot);
        return env.pushJNIRef(ref);      
      } else {
        env.recordException(new Exception("invalid field ID in JNI GetStaticObjectField"));
        return 0;
      }

    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      env.recordException(unexpected);
      return 0;
    }

  }


  /**
   * GetStaticBooleanField: read a static field of type boolean
   * @param envJREF a JREF index for the JNI environment object
   * @param classJREF a JREF index for the VM_Class object
   * @param fieldOffset the offset into the JTOC for this field, computed and saved earlier
   * @return the value of the boolean field, or 0 if the fieldOffset is incorrect
   */
  private static int GetStaticBooleanField(int envJREF, int classJREF, int fieldOffset) {
    if (traceJNI) VM.sysWrite("JNI called: GetStaticBooleanField  \n");

    VM_JNIEnvironment env;
    try {
      int slot = fieldOffset>>2;
      if (slot<VM_Statics.getNumberOfSlots()) {
        return VM_Statics.getSlotContentsAsInt(slot);
      } else {
        env = VM_Thread.getCurrentThread().getJNIEnv();
        env.recordException(new Exception("invalid field ID in JNI GetStaticBooleanField"));
        return 0;
      }

    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      env.recordException(unexpected);
      return 0;
    }
  }


  /**
   * GetStaticByteField:  read a static field of type byte
   * @param envJREF a JREF index for the JNI environment object
   * @param classJREF a JREF index for the VM_Class object
   * @param fieldOffset the offset into the JTOC for this field, computed and saved earlier
   * @return the value of the byte field, or 0 if the fieldOffset is incorrect
   */
  private static int GetStaticByteField(int envJREF, int classJREF, int fieldOffset) {
    if (traceJNI) VM.sysWrite("JNI called: GetStaticByteField  \n");

    VM_JNIEnvironment env;
    try {
      int slot = fieldOffset>>2;
      if (slot<VM_Statics.getNumberOfSlots()) {
        return VM_Statics.getSlotContentsAsInt(slot);
      } else {
        env = VM_Thread.getCurrentThread().getJNIEnv();
        env.recordException(new Exception("invalid field ID in JNI GetStaticByteField"));
        return 0;
      }

    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      env.recordException(unexpected);
      return 0;
    }

  }


  /**
   * GetStaticCharField:  read a static field of type character
   * @param envJREF a JREF index for the JNI environment object
   * @param classJREF a JREF index for the VM_Class object
   * @param fieldOffset the offset into the JTOC for this field, computed and saved earlier
   * @return the value of the character field, or 0 if the fieldOffset is incorrect
   */
  private static int GetStaticCharField(int envJREF, int classJREF, int fieldOffset) {
    if (traceJNI) VM.sysWrite("JNI called: GetStaticCharField  \n");

    VM_JNIEnvironment env;
    try {
      int slot = fieldOffset>>2;
      if (slot<VM_Statics.getNumberOfSlots()) {
        return VM_Statics.getSlotContentsAsInt(slot);
      } else {
        env = VM_Thread.getCurrentThread().getJNIEnv();
        env.recordException(new Exception("invalid field ID in JNI GetStaticCharField"));
        return 0;
      }

    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      env.recordException(unexpected);
      return 0;
    }

  }


  /**
   * GetStaticShortField:  read a static field of type short
   * @param envJREF a JREF index for the JNI environment object
   * @param classJREF a JREF index for the VM_Class object
   * @param fieldOffset the offset into the JTOC for this field, computed and saved earlier
   * @return the value of the short field, or 0 if the fieldOffset is incorrect
   */
  private static int GetStaticShortField(int envJREF, int classJREF, int fieldOffset) {
    if (traceJNI) VM.sysWrite("JNI called: GetStaticShortField  \n");

    VM_JNIEnvironment env;
    try {
      int slot = fieldOffset>>2;
      if (slot<VM_Statics.getNumberOfSlots()) {
        return VM_Statics.getSlotContentsAsInt(slot);
      } else {
        env = VM_Thread.getCurrentThread().getJNIEnv();
        env.recordException(new Exception("invalid field ID in JNI GetStaticShortField"));
        return 0;
      }

    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      env.recordException(unexpected);
      return 0;
    }

  }


  /**
   * GetStaticIntField:  read a static field of type integer
   * @param envJREF a JREF index for the JNI environment object
   * @param classJREF a JREF index for the VM_Class object
   * @param fieldOffset the offset into the JTOC for this field, computed and saved earlier
   * @return the value of the integer field, or 0 if the fieldOffset is incorrect
   */
  private static int GetStaticIntField(int envJREF, int classJREF, int fieldOffset) {
    if (traceJNI) VM.sysWrite("JNI called: GetStaticIntField  \n");


    VM_JNIEnvironment env;
    try {
      int slot = fieldOffset>>2;
      if (slot<VM_Statics.getNumberOfSlots()) {
        return VM_Statics.getSlotContentsAsInt(slot);
      } else {
        env = VM_Thread.getCurrentThread().getJNIEnv();
        env.recordException(new Exception("invalid field ID in JNI GetStaticObjectField"));
        return 0;
      }

    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      env.recordException(unexpected);
      return 0;
    }

  }


  /**
   * GetStaticLongField:  read a static field of type long
   * @param envJREF a JREF index for the JNI environment object
   * @param classJREF a JREF index for the VM_Class object
   * @param fieldOffset the offset into the JTOC for this field, computed and saved earlier
   * @return the value of the long field which is 8 bytes, 
   *         or 0 if the fieldOffset is incorrect
   */
  private static long GetStaticLongField(int envJREF, int classJREF, int fieldOffset) {
    if (traceJNI) VM.sysWrite("JNI called: GetStaticLongField  \n");


    VM_JNIEnvironment env;
    try {
      int slot = fieldOffset>>2;      
      if ((slot+1)<VM_Statics.getNumberOfSlots()) {
        long val = VM_Statics.getSlotContentsAsLong(slot);
        return val;
      } else {
        env = VM_Thread.getCurrentThread().getJNIEnv();
        env.recordException(new Exception("invalid field ID in JNI GetStaticLongField"));
        return 0L;
      }

    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      env.recordException(unexpected);
      return 0;
    }

  }

  /**
   * GetStaticFloatField:  read a static field of type float
   * @param envJREF a JREF index for the JNI environment object
   * @param classJREF a JREF index for the VM_Class object
   * @param fieldOffset the offset into the JTOC for this field, computed and saved earlier
   * @return the value of the float field which is 4 bytes, 
   *         or 0 if the fieldOffset is incorrect
   */
  private static float GetStaticFloatField(int envJREF, int classJREF, int fieldOffset) {
    if (traceJNI) VM.sysWrite("JNI called: GetStaticFloatField  \n");


    VM_JNIEnvironment env;
    try {
      int slot = fieldOffset>>2;
      if (slot<VM_Statics.getNumberOfSlots()) {
        return Float.intBitsToFloat(VM_Statics.getSlotContentsAsInt(slot));
      } else {
        env = VM_Thread.getCurrentThread().getJNIEnv();
        env.recordException(new Exception("invalid field ID in JNI GetStaticFloatField"));
        return 0.0f;
      }

    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      env.recordException(unexpected);
      return 0;
    }

  }

  /**
   * GetStaticDoubleField:  read a static field of type double
   * @param envJREF a JREF index for the JNI environment object
   * @param classJREF a JREF index for the VM_Class object
   * @param fieldOffset the offset into the JTOC for this field, computed and saved earlier
   * @return the value of the double field which is 8 bytes, 
   *         or 0 if the fieldOffset is incorrect
   */
  private static double GetStaticDoubleField(int envJREF, int classJREF, int fieldOffset) {
    if (traceJNI) VM.sysWrite("JNI called: GetStaticDoubleField  \n");


    VM_JNIEnvironment env;
    try {
      int slot = fieldOffset>>2;
      if (slot<VM_Statics.getNumberOfSlots()) {
        return Double.longBitsToDouble(VM_Statics.getSlotContentsAsLong(slot));
      } else {
        env = VM_Thread.getCurrentThread().getJNIEnv();
        env.recordException(new Exception("invalid field ID in JNI GetStaticDoubleField"));
        return 0.0;
      }

    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      env.recordException(unexpected);
      return 0;
    }

  }



  /**
   * SetStaticObjectField:  set a static field of type Object
   * @param envJREF a JREF index for the JNI environment object
   * @param classJREF a JREF index for the VM_Class object
   * @param fieldOffset the offset into the JTOC for this field, computed and saved earlier
   * @param value to assign
   */
  private static void SetStaticObjectField(int envJREF, int classJREF, int fieldOffset, int objectJREF) {
    if (traceJNI) VM.sysWrite("JNI called: SetStaticObjectField  \n");


    VM_JNIEnvironment env;
    try {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      Object ref = env.getJNIRef(objectJREF);      

      int slot = fieldOffset>>2;
      if (slot<VM_Statics.getNumberOfSlots()) {
        VM_Statics.setSlotContents(slot,ref);
      } else {
        env.recordException(new Exception("invalid field ID in JNI SetStaticObjectField"));
      }

    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      env.recordException(unexpected);
    }

  }


  /**
   * SetStaticBooleanField:  set a static field of type boolean
   * @param envJREF a JREF index for the JNI environment object
   * @param classJREF a JREF index for the VM_Class object
   * @param fieldOffset the offset into the JTOC for this field, computed and saved earlier
   * @param value to assign
   */
  private static void SetStaticBooleanField(int envJREF, int classJREF, int fieldOffset, boolean fieldValue) {
    if (traceJNI) VM.sysWrite("JNI called: SetStaticBooleanField  \n");


    VM_JNIEnvironment env;
    try {
      int slot = fieldOffset>>2;
      if (slot<VM_Statics.getNumberOfSlots()) {
        if (fieldValue == true)
          VM_Statics.setSlotContents(slot,1);
        else
          VM_Statics.setSlotContents(slot,0);
      } else {
        env = VM_Thread.getCurrentThread().getJNIEnv();
        env.recordException(new Exception("invalid field ID in JNI SetStaticBooleanField"));
      }

    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      env.recordException(unexpected);
    }

  }


  /**
   * SetStaticByteField:  set a static field of type byte
   * @param envJREF a JREF index for the JNI environment object
   * @param classJREF a JREF index for the VM_Class object
   * @param fieldOffset the offset into the JTOC for this field, computed and saved earlier
   * @param value to assign
   */
  private static void SetStaticByteField(int envJREF, int classJREF, int fieldOffset, byte fieldValue) {
    if (traceJNI) VM.sysWrite("JNI called: SetStaticByteField  \n");


    VM_JNIEnvironment env;
    try {
      int slot = fieldOffset>>2;
      if (slot<VM_Statics.getNumberOfSlots()) {
        VM_Statics.setSlotContents(slot,(int)fieldValue);
      } else {
        env = VM_Thread.getCurrentThread().getJNIEnv();
        env.recordException(new Exception("invalid field ID in JNI SetStaticByteField"));
      }

    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      env.recordException(unexpected);
    }

  }



  /**
   * SetStaticCharField:  set a static field of type char
   * @param envJREF a JREF index for the JNI environment object
   * @param classJREF a JREF index for the VM_Class object
   * @param fieldOffset the offset into the JTOC for this field, computed and saved earlier
   * @param value to assign
   */
  private static void SetStaticCharField(int envJREF, int classJREF, int fieldOffset, char fieldValue) {
    if (traceJNI) VM.sysWrite("JNI called: SetStaticCharField  \n");


    VM_JNIEnvironment env;
    try {
      int slot = fieldOffset>>2;
      if (slot<VM_Statics.getNumberOfSlots()) {
        VM_Statics.setSlotContents(slot,(int)fieldValue);
      } else {
        env = VM_Thread.getCurrentThread().getJNIEnv();
        env.recordException(new Exception("invalid field ID in JNI SetStaticCharField"));
      }

    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      env.recordException(unexpected);
    }

  }


  /**
   * SetStaticShortField:  set a static field of type short
   * @param envJREF a JREF index for the JNI environment object
   * @param classJREF a JREF index for the VM_Class object
   * @param fieldOffset the offset into the JTOC for this field, computed and saved earlier
   * @param value to assign
   */
  private static void SetStaticShortField(int envJREF, int classJREF, int fieldOffset, short fieldValue) {
    if (traceJNI) VM.sysWrite("JNI called: SetStaticShortField  \n");


    VM_JNIEnvironment env;
    try {
      int slot = fieldOffset>>2;
      if (slot<VM_Statics.getNumberOfSlots()) {
        VM_Statics.setSlotContents(slot,(int)fieldValue);
      } else {
        env = VM_Thread.getCurrentThread().getJNIEnv();
        env.recordException(new Exception("invalid field ID in JNI SetStaticShortField"));
      }

    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      env.recordException(unexpected);
    }

  }


  /**
   * SetStaticIntField:  set a static field of type integer
   * @param envJREF a JREF index for the JNI environment object
   * @param classJREF a JREF index for the VM_Class object
   * @param fieldOffset the offset into the JTOC for this field, computed and saved earlier
   * @param value to assign
   */
  private static void SetStaticIntField(int envJREF, int classJREF, int fieldOffset, int fieldValue) {
    if (traceJNI) VM.sysWrite("JNI called: SetStaticIntField  \n");


    VM_JNIEnvironment env;
    try {
      int slot = fieldOffset>>2;
      if (slot<VM_Statics.getNumberOfSlots()) {
        VM_Statics.setSlotContents(slot,fieldValue);
      } else {
        env = VM_Thread.getCurrentThread().getJNIEnv();
        env.recordException(new Exception("invalid field ID in JNI SetStaticIntField"));
      }

    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      env.recordException(unexpected);
    }

  }


  /**
   * SetStaticLongField:  set a static field of type long
   * @param envJREF a JREF index for the JNI environment object
   * @param classJREF a JREF index for the VM_Class object
   * @param fieldOffset the offset into the JTOC for this field, computed and saved earlier
   * @param value to assign
   */
  private static void SetStaticLongField(int envJREF, int classJREF, int fieldOffset, long fieldValue) {
    if (traceJNI) VM.sysWrite("JNI called: SetStaticLongField  \n");


    VM_JNIEnvironment env;
    try {
      int slot = fieldOffset>>2;
      if (slot<VM_Statics.getNumberOfSlots()) {
        VM_Statics.setSlotContents(slot,fieldValue);
      } else {
        env = VM_Thread.getCurrentThread().getJNIEnv();
        env.recordException(new Exception("invalid field ID in JNI SetStaticLongField"));
      }

    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      env.recordException(unexpected);
    }

  }


  /**
   * SetStaticFloatField:  set a static field of type float
   * @param envJREF a JREF index for the JNI environment object
   * @param classJREF a JREF index for the VM_Class object
   * @param fieldOffset the offset into the JTOC for this field, computed and saved earlier
   * @param value to assign
   */
  private static void SetStaticFloatField(int envJREF, int classJREF, int fieldOffset, float fieldValue) {
    if (traceJNI) VM.sysWrite("JNI called: SetStaticFloatField  \n");


    VM_JNIEnvironment env;
    try {
      int slot = fieldOffset>>2;
      if (slot<VM_Statics.getNumberOfSlots()) {
        VM_Statics.setSlotContents( slot, Float.floatToIntBits(fieldValue) );
      } else {
        env = VM_Thread.getCurrentThread().getJNIEnv();
        env.recordException(new Exception("invalid field ID in JNI SetStaticFloatField"));
      }

    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      env.recordException(unexpected);
    }

  }


  /**
   * SetStaticDoubleField:  set a static field of type float
   * @param envJREF a JREF index for the JNI environment object
   * @param classJREF a JREF index for the VM_Class object
   * @param fieldOffset the offset into the JTOC for this field, computed and saved earlier
   * @param value to assign
   */
  private static void SetStaticDoubleField(int envJREF, int classJREF, int fieldOffset, double fieldValue) {
    if (traceJNI) VM.sysWrite("JNI called: SetStaticDoubleField  \n");


    VM_JNIEnvironment env;
    try {
      int slot = fieldOffset>>2;
      if (slot<VM_Statics.getNumberOfSlots()) {
        VM_Statics.setSlotContents( slot, Double.doubleToLongBits(fieldValue) );
      } else {
        env = VM_Thread.getCurrentThread().getJNIEnv();
        env.recordException(new Exception("invalid field ID in JNI SetStaticDoubleField"));
      }

    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static int NewString(int envJREF, int uchars, int len) {
    if (traceJNI) VM.sysWrite("JNI called: NewString  \n");

    VM_JNIEnvironment env;
    try {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      char[] contents = new char[len];
      VM_Memory.memcopy(VM_Magic.objectAsAddress(contents), VM_Address.fromInt(uchars), len*2);
      String s = new String(contents);

      if (s!=null) {
        return env.pushJNIRef(s);
      } else {
        env = VM_Thread.getCurrentThread().getJNIEnv();
        env.recordException(new OutOfMemoryError());
        return 0;
      }

    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static int GetStringLength(int envJREF, int objJREF) {
    if (traceJNI) VM.sysWrite("JNI called: GetStringLength  \n");


    VM_JNIEnvironment env;
    try {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      // retrieve the real String object from the side table
      String str =  (String) env.getJNIRef(objJREF);
      return str.length();
    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static VM_Address GetStringChars(int envJREF, int objJREF, VM_Address isCopyAddress) {
    if (traceJNI) VM.sysWrite("JNI called: GetStringChars  \n");

    //    VM.sysWrite("GetStringChars\n");

    VM_JNIEnvironment env;
    try {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      // retrieve the real String object from the side table
      String str =  (String) env.getJNIRef(objJREF);
      int len = str.length();
      char[] contents = str.toCharArray();

      // alloc non moving buffer in C heap for a copy of string contents
      VM_Address copyBuffer = VM_Address.fromInt(VM.sysCall1(VM_BootRecord.the_boot_record.sysMallocIP,
                                                             len*2));
      if(copyBuffer.isZero()) {
        env.recordException(new OutOfMemoryError());
        return VM_Address.zero();
      }
      VM_Memory.memcopy( copyBuffer, VM_Magic.objectAsAddress(contents), len*2 );

      // set callers isCopy boolean to true, if it's a valid address
      if (!isCopyAddress.isZero()) {
        int temp = VM_Magic.getMemoryWord(isCopyAddress);
        if (LittleEndian) {
          VM_Magic.setMemoryWord(isCopyAddress, ((temp & 0xffffff00) | 0x00000001));
        } else {
          VM_Magic.setMemoryWord(isCopyAddress, ((temp & 0x00ffffff) | 0x01000000));
        }
      }

      return copyBuffer;

    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static void ReleaseStringChars(int envJREF, int objJREF, VM_Address bufAddress) {
    if (traceJNI) VM.sysWrite("JNI called: ReleaseStringChars  \n");


    VM_JNIEnvironment env;
    try {
      // VM.sysWrite("ReleaseStringChars ");
      // VM.sysWrite(bufAddress);
      // VM.sysWrite("\n");

      VM.sysCall1(VM_BootRecord.the_boot_record.sysFreeIP, bufAddress.toInt());
      return;

    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static int NewStringUTF(int envJREF, VM_Address utf8bytes) {
    if (traceJNI) VM.sysWrite("JNI called: NewStringUTF  \n");


    VM_JNIEnvironment env;
    try {
      // VM.sysWrite("NewStringUTF:\n");

      String returnString = null;

      env = VM_Thread.getCurrentThread().getJNIEnv();

      byte[] utf8array = VM_JNIEnvironment.createByteArrayFromC(utf8bytes);

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
      env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static int GetStringUTFLength(int envJREF, int objJREF) {
    if (traceJNI) VM.sysWrite("JNI called: GetStringUTFLength  \n");

    VM_JNIEnvironment env;
    try {
      // VM.sysWrite("GetStringUTFLength\n");

      env = VM_Thread.getCurrentThread().getJNIEnv();
      // retrieve the real String object from the side table
      env = VM_Thread.getCurrentThread().getJNIEnv();
      String str =  (String) env.getJNIRef(objJREF);
      return VM_UTF8Convert.utfLength(str);

    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static VM_Address GetStringUTFChars(int envJREF, int objJREF, VM_Address isCopyAddress) {
    if (traceJNI) VM.sysWrite("JNI called: GetStringUTFChars  \n");

    VM_JNIEnvironment env;
    try {
      //    VM.sysWrite("GetStringUTFChars\n");

      // retrieve the real String object from the side table
      env = VM_Thread.getCurrentThread().getJNIEnv();
      String str =  (String) env.getJNIRef(objJREF);
      byte[] utfcontents = VM_UTF8Convert.toUTF8(str);

      int len = utfcontents.length;
      int copyBufferLen = (len + 4) & ~3; // need extra at end for storing terminator
      // and end at word boundary 

      // alloc non moving buffer in C heap for string contents as utf8 array
      // alloc extra byte for C null terminator
      VM_Address copyBuffer = VM_Address.fromInt(VM.sysCall1(VM_BootRecord.the_boot_record.sysMallocIP, copyBufferLen));

      if(copyBuffer.isZero()) {
        env.recordException(new OutOfMemoryError());
        return VM_Address.zero();
      }

      // store word of 0 at end, before the copy, to set C null terminator
      VM_Magic.setMemoryWord( copyBuffer.add(copyBufferLen - 4), 0 );

      VM_Memory.memcopy( copyBuffer, VM_Magic.objectAsAddress(utfcontents), len );

      // set callers isCopy boolean to true, if it's a valid address
      if (!isCopyAddress.isZero()) {
        int temp = VM_Magic.getMemoryWord(isCopyAddress);
        if (LittleEndian) {
          VM_Magic.setMemoryWord(isCopyAddress, ((temp & 0xffffff00) | 0x00000001));
        } else {
          VM_Magic.setMemoryWord(isCopyAddress, ((temp & 0x00ffffff) | 0x01000000));
        }
      }

      return copyBuffer;

    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static void ReleaseStringUTFChars(int envJREF, int objJREF, int bufAddress) {
    if (traceJNI) VM.sysWrite("JNI called: ReleaseStringUTFChars  \n");


    VM_JNIEnvironment env;
    try {
      // VM.sysWrite("ReleaseStringUTFChars ");
      // VM.sysWrite(bufAddress);
      // VM.sysWrite("\n");

      VM.sysCall1(VM_BootRecord.the_boot_record.sysFreeIP,
                  bufAddress);
      return;

    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      env.recordException(unexpected);
      return;
    }
  }


  /**
   * GetArrayLength: return array length
   * @param envJREF a JREF index for the JNI environment object
   * @param arrayJREF a JREF index for the source array 
   * @return the array length, or -1 if it's not an array
   */
  private static int GetArrayLength(int envJREF, int arrayJREF) {
    if (traceJNI) VM.sysWrite("JNI called: GetArrayLength  \n");


    VM_JNIEnvironment env;
    try {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      Object theArray = (Object) env.getJNIRef(arrayJREF);
      VM_Type arrayType = VM_Magic.getObjectType(theArray);
      if (arrayType.isArrayType())
        return VM_Magic.getArrayLength(theArray);
      else
        return -1;

    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static int NewObjectArray(int envJREF, int length, int classJREF, int initElementJREF ) {
    if (traceJNI) VM.sysWrite("JNI called: NewObjectArray  \n");


    VM_JNIEnvironment env;
    try {
      env = VM_Thread.getCurrentThread().getJNIEnv();

      Object initElement = (Object) env.getJNIRef(initElementJREF);
      Class cls = (Class) env.getJNIRef(classJREF);

      Object newArray[] = (Object [])ReflectionSupport.newInstance(cls, length);
      
      for (int i=0; i<length; i++) {
        newArray[i] = initElement;
      }

      return env.pushJNIRef(newArray);  

    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static int GetObjectArrayElement(int envJREF, int arrayJREF, int index) {
    if (traceJNI) VM.sysWrite("JNI called: GetObjectArrayElement  \n");


    VM_JNIEnvironment env;
    try {
      env = VM_Thread.getCurrentThread().getJNIEnv();
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
      env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static void SetObjectArrayElement(int envJREF, int arrayJREF, int index,
                                            int objectJREF) {
    if (traceJNI) VM.sysWrite("JNI called: SetObjectArrayElement  \n");


    VM_JNIEnvironment env = VM_Thread.getCurrentThread().getJNIEnv();
    try {
	env = VM_Thread.getCurrentThread().getJNIEnv();
	
	Object sourceArray[] = (Object []) env.getJNIRef(arrayJREF);
	Object elem = (Object) env.getJNIRef(objectJREF);
	
	// array exceptions thrown normally, recorded below per spec
	sourceArray[index] = elem;
	
    } catch (Throwable e) {
	env.recordException(e);
	return;
    }
  }


  /**
   * NewBooleanArray: create a new boolean array
   * @param envJREF a JREF index for the JNI environment object
   * @param length the size of the new array
   * @return the new boolean array
   * @exception OutOfMemoryError if the system runs out of memory   
   */
  private static int NewBooleanArray(int envJREF, int length) {
    if (traceJNI) VM.sysWrite("JNI called: NewBooleanArray  \n");


    VM_JNIEnvironment env;
    try {
      boolean newArray[] = new boolean[length];
      env = VM_Thread.getCurrentThread().getJNIEnv();
      return env.pushJNIRef(newArray);  

    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static int NewByteArray(int envJREF, int length) {
    if (traceJNI) VM.sysWrite("JNI called: NewByteArray  \n");


    VM_JNIEnvironment env;
    try {
      byte newArray[] = new byte[length];
      env = VM_Thread.getCurrentThread().getJNIEnv();
      return env.pushJNIRef(newArray);  

    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static int NewCharArray(int envJREF, int length) {
    if (traceJNI) VM.sysWrite("JNI called: NewCharArray  \n");


    VM_JNIEnvironment env;
    try {
      char newArray[] = new char[length];
      env = VM_Thread.getCurrentThread().getJNIEnv();
      return env.pushJNIRef(newArray);  

    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static int NewShortArray(int envJREF, int length) {
    if (traceJNI) VM.sysWrite("JNI called: NewShortArray  \n");


    VM_JNIEnvironment env;
    try {
      short newArray[] = new short[length];
      env = VM_Thread.getCurrentThread().getJNIEnv();
      return env.pushJNIRef(newArray);  

    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static int NewIntArray(int envJREF, int length) {
    if (traceJNI) VM.sysWrite("JNI called: NewIntArray  \n");


    VM_JNIEnvironment env;
    try {
      int newArray[] = new int[length];
      env = VM_Thread.getCurrentThread().getJNIEnv();
      return env.pushJNIRef(newArray);  

    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static int NewLongArray(int envJREF, int length) {
    if (traceJNI) VM.sysWrite("JNI called: NewLongArray  \n");


    VM_JNIEnvironment env;
    try {
      long newArray[] = new long[length];
      env = VM_Thread.getCurrentThread().getJNIEnv();
      return env.pushJNIRef(newArray);  

    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static int NewFloatArray(int envJREF, int length) {
    if (traceJNI) VM.sysWrite("JNI called: NewFloatArray  \n");


    VM_JNIEnvironment env;
    try {
      float newArray[] = new float[length];
      env = VM_Thread.getCurrentThread().getJNIEnv();
      return env.pushJNIRef(newArray);  

    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static int NewDoubleArray(int envJREF, int length) {
    if (traceJNI) VM.sysWrite("JNI called: NewDoubleArray  \n");


    VM_JNIEnvironment env;
    try {
      double newArray[] = new double[length];
      env = VM_Thread.getCurrentThread().getJNIEnv();
      return env.pushJNIRef(newArray);  

    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static VM_Address GetBooleanArrayElements(int envJREF, int arrayJREF, VM_Address isCopyAddress) {
    if (traceJNI) VM.sysWrite("JNI called: GetBooleanArrayElements  \n");


    VM_JNIEnvironment env;
    try {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      boolean sourceArray[] = (boolean []) env.getJNIRef(arrayJREF);

      int size = sourceArray.length;

      // alloc non moving buffer in C heap for a copy of string contents
      VM_Address copyBuffer = VM_Address.fromInt(VM.sysCall1(VM_BootRecord.the_boot_record.sysMallocIP,
                                                             size));
      if(copyBuffer.isZero()) {
        env.recordException(new OutOfMemoryError());
        return VM_Address.zero();
      }

      VM_Memory.memcopy( copyBuffer, VM_Magic.objectAsAddress(sourceArray), size );

      // set callers isCopy boolean to true, if it's a valid address
      if (!isCopyAddress.isZero()) {
        int temp = VM_Magic.getMemoryWord(isCopyAddress);
        if (LittleEndian) {
          VM_Magic.setMemoryWord(isCopyAddress, ((temp & 0xffffff00) | 0x00000001));
        } else {
          VM_Magic.setMemoryWord(isCopyAddress, ((temp & 0x00ffffff) | 0x01000000));
        }
      }

      return copyBuffer;

    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static VM_Address GetByteArrayElements(int envJREF, int arrayJREF, VM_Address isCopyAddress) {
    if (traceJNI) {
      VM.sysWrite("JNI called: GetByteArrayElements for ");
      VM.sysWrite(arrayJREF);
      VM.sysWriteln(":");
    }

    VM_JNIEnvironment env;
    try {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      byte sourceArray[] = (byte []) env.getJNIRef(arrayJREF);
      int size = sourceArray.length;

      // alloc non moving buffer in C heap for a copy of string contents
      VM_Address copyBuffer = VM_Address.fromInt(VM.sysCall1(VM_BootRecord.the_boot_record.sysMallocIP,
                                                             size));
      if(copyBuffer.isZero()) {
        env.recordException(new OutOfMemoryError());
        return VM_Address.zero();
      }

      VM_Memory.memcopy( copyBuffer, VM_Magic.objectAsAddress(sourceArray), size );

      // set callers isCopy boolean to true, if it's a valid address
      if (!isCopyAddress.isZero()) {
        int temp = VM_Magic.getMemoryWord(isCopyAddress);
        if (LittleEndian) {
          VM_Magic.setMemoryWord(isCopyAddress, ((temp & 0xffffff00) | 0x00000001));
        } else {
          VM_Magic.setMemoryWord(isCopyAddress, ((temp & 0x00ffffff) | 0x01000000));
        }
      }

      if (traceJNI) {
        VM.sysWrite("returing ");
        VM.sysWrite(copyBuffer);
        VM.sysWrite("\n");
      }

      return copyBuffer;

    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      env.recordException(unexpected);

      if (traceJNI) {
        VM.sysWrite("returing 0\n");
      }

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
  private static VM_Address GetCharArrayElements(int envJREF, int arrayJREF, VM_Address isCopyAddress) {
    if (traceJNI) VM.sysWrite("JNI called: GetCharArrayElements  \n");


    VM_JNIEnvironment env;
    try {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      char sourceArray[] = (char []) env.getJNIRef(arrayJREF);

      int size = sourceArray.length;

      // alloc non moving buffer in C heap for a copy of string contents
      VM_Address copyBuffer = VM_Address.fromInt(VM.sysCall1(VM_BootRecord.the_boot_record.sysMallocIP,
                                                             size*2));
      if (copyBuffer.isZero()) {
        env.recordException(new OutOfMemoryError());
        return VM_Address.zero();
      }

      VM_Memory.memcopy( copyBuffer, VM_Magic.objectAsAddress(sourceArray), size*2 );

      // set callers isCopy boolean to true, if it's a valid address
      if (!isCopyAddress.isZero()) {
        int temp = VM_Magic.getMemoryWord(isCopyAddress);
        if (LittleEndian) {
          VM_Magic.setMemoryWord(isCopyAddress, ((temp & 0xffffff00) | 0x00000001));
        } else {
          VM_Magic.setMemoryWord(isCopyAddress, ((temp & 0x00ffffff) | 0x01000000));
        }
      }

      return copyBuffer;

    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static VM_Address GetShortArrayElements(int envJREF, int arrayJREF, VM_Address isCopyAddress) {
    if (traceJNI) VM.sysWrite("JNI called: GetShortArrayElements  \n");


    VM_JNIEnvironment env;
    try {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      short sourceArray[] = (short []) env.getJNIRef(arrayJREF);

      int size = sourceArray.length;

      // alloc non moving buffer in C heap for a copy of string contents
      VM_Address copyBuffer = VM_Address.fromInt(VM.sysCall1(VM_BootRecord.the_boot_record.sysMallocIP,
                                                             size*2));
      if(copyBuffer.isZero()) {
        env.recordException(new OutOfMemoryError());
        return VM_Address.zero();
      }

      VM_Memory.memcopy( copyBuffer, VM_Magic.objectAsAddress(sourceArray), size*2 );

      // set callers isCopy boolean to true, if it's a valid address
      if (!isCopyAddress.isZero()) {
        int temp = VM_Magic.getMemoryWord(isCopyAddress);
        if (LittleEndian) {
          VM_Magic.setMemoryWord(isCopyAddress, ((temp & 0xffffff00) | 0x00000001));
        } else {
          VM_Magic.setMemoryWord(isCopyAddress, ((temp & 0x00ffffff) | 0x01000000));
        }
      }

      return copyBuffer;

    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static VM_Address GetIntArrayElements(int envJREF, int arrayJREF, VM_Address isCopyAddress) {
    if (traceJNI) VM.sysWrite("JNI called: GetIntArrayElements  \n");


    VM_JNIEnvironment env;
    try {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      int sourceArray[] = (int []) env.getJNIRef(arrayJREF);

      int size = sourceArray.length;

      // alloc non moving buffer in C heap for a copy of string contents
      VM_Address copyBuffer = VM_Address.fromInt(VM.sysCall1(VM_BootRecord.the_boot_record.sysMallocIP,
                                                             size * 4));
      if(copyBuffer.isZero()) {
        env.recordException(new OutOfMemoryError());
        return VM_Address.zero();
      }
      VM_Memory.memcopy( copyBuffer, VM_Magic.objectAsAddress(sourceArray), size*4 );

      // set callers isCopy boolean to true, if it's a valid address
      if (!isCopyAddress.isZero()) {
        int temp = VM_Magic.getMemoryWord(isCopyAddress);
        if (LittleEndian) {
          VM_Magic.setMemoryWord(isCopyAddress, ((temp & 0xffffff00) | 0x00000001));
        } else {
          VM_Magic.setMemoryWord(isCopyAddress, ((temp & 0x00ffffff) | 0x01000000));
        }
      }

      return copyBuffer;

    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static VM_Address GetLongArrayElements(int envJREF, int arrayJREF, VM_Address isCopyAddress) {
    if (traceJNI) VM.sysWrite("JNI called: GetLongArrayElements  \n");


    VM_JNIEnvironment env;
    try {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      long sourceArray[] = (long []) env.getJNIRef(arrayJREF);

      int size = sourceArray.length;

      // alloc non moving buffer in C heap for a copy of string contents
      VM_Address copyBuffer = VM_Address.fromInt(VM.sysCall1(VM_BootRecord.the_boot_record.sysMallocIP,
                                                             size * 8));
      if(copyBuffer.isZero()) {
        env.recordException(new OutOfMemoryError());
        return VM_Address.zero();
      }
      VM_Memory.memcopy( copyBuffer, VM_Magic.objectAsAddress(sourceArray), size*8 );

      // set callers isCopy boolean to true, if it's a valid address
      if (!isCopyAddress.isZero()) {
        int temp = VM_Magic.getMemoryWord(isCopyAddress);
        if (LittleEndian) {
          VM_Magic.setMemoryWord(isCopyAddress, ((temp & 0xffffff00) | 0x00000001));
        } else {
          VM_Magic.setMemoryWord(isCopyAddress, ((temp & 0x00ffffff) | 0x01000000));
        }
      }

      return copyBuffer;

    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static VM_Address GetFloatArrayElements(int envJREF, int arrayJREF, VM_Address isCopyAddress) {
    if (traceJNI) VM.sysWrite("JNI called: GetFloatArrayElements  \n");


    VM_JNIEnvironment env;
    try {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      float sourceArray[] = (float []) env.getJNIRef(arrayJREF);

      int size = sourceArray.length;

      // alloc non moving buffer in C heap for a copy of string contents
      VM_Address copyBuffer = VM_Address.fromInt(VM.sysCall1(VM_BootRecord.the_boot_record.sysMallocIP,
                                                             size * 4));
      if(copyBuffer.isZero()) {
        env.recordException(new OutOfMemoryError());
        return VM_Address.zero();
      }

      VM_Memory.memcopy( copyBuffer, VM_Magic.objectAsAddress(sourceArray), size*4 );

      // set callers isCopy boolean to true, if it's a valid address
      if (!isCopyAddress.isZero()) {
        int temp = VM_Magic.getMemoryWord(isCopyAddress);
        if (LittleEndian) {
          VM_Magic.setMemoryWord(isCopyAddress, ((temp & 0xffffff00) | 0x00000001));
        } else {
          VM_Magic.setMemoryWord(isCopyAddress, ((temp & 0x00ffffff) | 0x01000000));
        }
      }

      return copyBuffer;

    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static VM_Address GetDoubleArrayElements(int envJREF, int arrayJREF, VM_Address isCopyAddress) {
    if (traceJNI) VM.sysWrite("JNI called: GetDoubleArrayElements  \n");


    VM_JNIEnvironment env;
    try {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      double sourceArray[] = (double []) env.getJNIRef(arrayJREF);

      int size = sourceArray.length;

      // alloc non moving buffer in C heap for a copy of string contents
      VM_Address copyBuffer = VM_Address.fromInt(VM.sysCall1(VM_BootRecord.the_boot_record.sysMallocIP,
                                                             size * 8));
      if(copyBuffer.isZero()) {
        env.recordException(new OutOfMemoryError());
        return VM_Address.zero();
      }
      VM_Memory.memcopy( copyBuffer, VM_Magic.objectAsAddress(sourceArray), size*8 );

      // set callers isCopy boolean to true, if it's a valid address
      if (!isCopyAddress.isZero()) {
        int temp = VM_Magic.getMemoryWord(isCopyAddress);
        if (LittleEndian) {
          VM_Magic.setMemoryWord(isCopyAddress, ((temp & 0xffffff00) | 0x00000001));
        } else {
          VM_Magic.setMemoryWord(isCopyAddress, ((temp & 0x00ffffff) | 0x01000000));
        }
      }

      return copyBuffer;

    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
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
  private static void ReleaseBooleanArrayElements(int envJREF, int arrayJREF, VM_Address copyBufferAddress, 
                                                  int releaseMode) {
    if (traceJNI) VM.sysWrite("JNI called: ReleaseBooleanArrayElements  \n");

    VM_JNIEnvironment env;
    try {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      boolean sourceArray[] = (boolean []) env.getJNIRef(arrayJREF);

      // If a direct pointer was given to the user, no need to update or release
      if (VM_Magic.objectAsAddress(sourceArray).EQ(copyBufferAddress))
        return;

      int size = sourceArray.length;

      // mode 0 and mode 1:  copy back the buffer
      if ((releaseMode== 0 || releaseMode== 1) && size!=0) {

        for (int i=0; i<size; i+= 4) {
          VM_Address addr = copyBufferAddress.add(i);
          int data = VM_Magic.getMemoryWord(addr);
          if (LittleEndian) {
            if (i<size) 
              sourceArray[i]   = ((data)        & 0x000000ff) == 0 ? false : true;
            if (i+1<size) 		    	         
              sourceArray[i+1] = ((data >>> 8)  & 0x000000ff) == 0 ? false : true;
            if (i+2<size) 		    	         
              sourceArray[i+2] = ((data >>> 16) & 0x000000ff) == 0 ? false : true;
            if (i+3<size) 		    	                 
              sourceArray[i+3] = ((data >>> 24) & 0x000000ff) == 0 ? false : true;
          } else {
            if (i<size) 
              sourceArray[i]   = ((data >>> 24) & 0x000000ff) == 0 ? false : true;
            if (i+1<size) 		    	         
              sourceArray[i+1] = ((data >>> 16) & 0x000000ff) == 0 ? false : true;
            if (i+2<size) 		    	         
              sourceArray[i+2] = ((data >>> 8)  & 0x000000ff) == 0 ? false : true;
            if (i+3<size) 		    	                 
              sourceArray[i+3] = ((data)        & 0x000000ff) == 0 ? false : true;
          }
        }
      } 

      // mode 0 and mode 2:  free the buffer
      if (releaseMode== 0 || releaseMode== 2) {
        VM.sysCall1(VM_BootRecord.the_boot_record.sysFreeIP, copyBufferAddress.toInt());
      }

    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      env.recordException(unexpected);
      return;
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
  private static void ReleaseByteArrayElements(int envJREF, int arrayJREF, VM_Address copyBufferAddress, 
                                               int releaseMode) {
    if (traceJNI) VM.sysWrite("JNI called: ReleaseByteArrayElements  \n");

    VM_JNIEnvironment env;
    try {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      byte sourceArray[] = (byte []) env.getJNIRef(arrayJREF);

      // If a direct pointer was given to the user, no need to update or release
      if (VM_Magic.objectAsAddress(sourceArray)==copyBufferAddress)
        return;

      int size = sourceArray.length;

      // mode 0 and mode 1:  copy back the buffer
      if ((releaseMode== 0 || releaseMode== 1) && size!=0) {
        VM_Memory.memcopy( VM_Magic.objectAsAddress(sourceArray), copyBufferAddress, size );
      } 

      // mode 0 and mode 2:  free the buffer
      if (releaseMode== 0 || releaseMode== 2) {
        VM.sysCall1(VM_BootRecord.the_boot_record.sysFreeIP, copyBufferAddress.toInt());
      }

    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      env.recordException(unexpected);
      return;
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
  private static void ReleaseCharArrayElements(int envJREF, int arrayJREF, VM_Address copyBufferAddress, 
                                               int releaseMode) {
    if (traceJNI) VM.sysWrite("JNI called: ReleaseCharArrayElements \n");


    VM_JNIEnvironment env;
    try {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      char sourceArray[] = (char []) env.getJNIRef(arrayJREF);

      // If a direct pointer was given to the user, no need to update or release
      if (VM_Magic.objectAsAddress(sourceArray)==copyBufferAddress)
        return;

      int size = sourceArray.length;

      // mode 0 and mode 1:  copy back the buffer
      if ((releaseMode== 0 || releaseMode== 1) && size!=0) {
        VM_Memory.memcopy( VM_Magic.objectAsAddress(sourceArray), copyBufferAddress, size*2 );
      } 

      // mode 0 and mode 2:  free the buffer
      if (releaseMode== 0 || releaseMode== 2) {
        VM.sysCall1(VM_BootRecord.the_boot_record.sysFreeIP, copyBufferAddress.toInt());
      }

    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      env.recordException(unexpected);
      return;
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
  private static void ReleaseShortArrayElements(int envJREF, int arrayJREF, VM_Address copyBufferAddress, 
                                                int releaseMode) {
    if (traceJNI) VM.sysWrite("JNI called: ReleaseShortArrayElements  \n");


    VM_JNIEnvironment env;
    try {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      short sourceArray[] = (short []) env.getJNIRef(arrayJREF);

      // If a direct pointer was given to the user, no need to update or release
      if (VM_Magic.objectAsAddress(sourceArray)==copyBufferAddress)
        return;

      int size = sourceArray.length;

      // mode 0 and mode 1:  copy back the buffer
      if ((releaseMode== 0 || releaseMode== 1) && size!=0) {
        VM_Memory.memcopy( VM_Magic.objectAsAddress(sourceArray), copyBufferAddress, size*2 );
      } 

      // mode 0 and mode 2:  free the buffer
      if (releaseMode== 0 || releaseMode== 2) {
        VM.sysCall1(VM_BootRecord.the_boot_record.sysFreeIP, copyBufferAddress.toInt());
      }

    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      env.recordException(unexpected);
      return;
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
  private static void ReleaseIntArrayElements(int envJREF, int arrayJREF, VM_Address copyBufferAddress, 
                                              int releaseMode) {
    if (traceJNI) VM.sysWrite("JNI called: ReleaseIntArrayElements  \n");


    VM_JNIEnvironment env;
    try {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      int sourceArray[] = (int []) env.getJNIRef(arrayJREF);

      // If a direct pointer was given to the user, no need to update or release
      if (VM_Magic.objectAsAddress(sourceArray)==copyBufferAddress)
        return;

      int size = sourceArray.length;

      // mode 0 and mode 1:  copy back the buffer
      if (releaseMode== 0 || releaseMode== 1) {
        VM_Memory.memcopy( VM_Magic.objectAsAddress(sourceArray), copyBufferAddress, size*4 );
      } 

      // mode 0 and mode 2:  free the buffer
      if (releaseMode== 0 || releaseMode== 2) {
        VM.sysCall1(VM_BootRecord.the_boot_record.sysFreeIP, copyBufferAddress.toInt());
      }

    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      env.recordException(unexpected);
      return;
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
  private static void ReleaseLongArrayElements(int envJREF, int arrayJREF, VM_Address copyBufferAddress, 
                                               int releaseMode) {
    if (traceJNI) VM.sysWrite("JNI called: ReleaseLongArrayElements  \n");


    VM_JNIEnvironment env;
    try {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      long sourceArray[] = (long []) env.getJNIRef(arrayJREF);

      // If a direct pointer was given to the user, no need to update or release
      if (VM_Magic.objectAsAddress(sourceArray)==copyBufferAddress)
        return;

      int size = sourceArray.length;

      // mode 0 and mode 1:  copy back the buffer
      if (releaseMode== 0 || releaseMode== 1) {
        VM_Memory.memcopy( VM_Magic.objectAsAddress(sourceArray), copyBufferAddress, size*8 );
      } 

      // mode 0 and mode 2:  free the buffer
      if (releaseMode== 0 || releaseMode== 2) {
        VM.sysCall1(VM_BootRecord.the_boot_record.sysFreeIP, copyBufferAddress.toInt());
      }

    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      env.recordException(unexpected);
      return;
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
  private static void ReleaseFloatArrayElements(int envJREF, int arrayJREF, VM_Address copyBufferAddress, 
                                                int releaseMode) {
    if (traceJNI) VM.sysWrite("JNI called: ReleaseFloatArrayElements  \n");


    VM_JNIEnvironment env;
    try {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      float sourceArray[] = (float []) env.getJNIRef(arrayJREF);

      // If a direct pointer was given to the user, no need to update or release
      if (VM_Magic.objectAsAddress(sourceArray)==copyBufferAddress)
        return;

      int size = sourceArray.length;

      // mode 0 and mode 1:  copy back the buffer
      if (releaseMode== 0 || releaseMode== 1) {
        VM_Memory.memcopy( VM_Magic.objectAsAddress(sourceArray), copyBufferAddress, size*4 );
      } 

      // mode 0 and mode 2:  free the buffer
      if (releaseMode== 0 || releaseMode== 2) {
        VM.sysCall1(VM_BootRecord.the_boot_record.sysFreeIP, copyBufferAddress.toInt());
      }

    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      env.recordException(unexpected);
      return;
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
  private static void ReleaseDoubleArrayElements(int envJREF, int arrayJREF, VM_Address copyBufferAddress, 
                                                 int releaseMode) {
    if (traceJNI) VM.sysWrite("JNI called: ReleaseDoubleArrayElements  \n");


    VM_JNIEnvironment env;
    try {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      double sourceArray[] = (double []) env.getJNIRef(arrayJREF);

      // If a direct pointer was given to the user, no need to update or release
      if (VM_Magic.objectAsAddress(sourceArray)==copyBufferAddress)
        return;

      int size = sourceArray.length;

      // mode 0 and mode 1:  copy back the buffer
      if (releaseMode== 0 || releaseMode== 1) {
        VM_Memory.memcopy( VM_Magic.objectAsAddress(sourceArray), copyBufferAddress, size*8 );
      } 

      // mode 0 and mode 2:  free the buffer
      if (releaseMode== 0 || releaseMode== 2) {
        VM.sysCall1(VM_BootRecord.the_boot_record.sysFreeIP, copyBufferAddress.toInt());
      }

    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      env.recordException(unexpected);
      return;
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
  private static void GetBooleanArrayRegion(int envJREF, int arrayJREF, int startIndex, 
                                            int length, VM_Address bufAddress) {
    if (traceJNI) VM.sysWrite("JNI called: GetBooleanArrayRegion  \n");


    VM_JNIEnvironment env;
    try {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      boolean sourceArray[] = (boolean []) env.getJNIRef(arrayJREF);

      if ((startIndex<0) || (startIndex+length>sourceArray.length)) {
        env.recordException(new ArrayIndexOutOfBoundsException());
        return;
      }

      VM_Memory.memcopy(bufAddress, VM_Magic.objectAsAddress(sourceArray).add(startIndex), length); 

    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      env.recordException(unexpected);
      return;
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
  private static void GetByteArrayRegion(int envJREF, int arrayJREF, int startIndex, 
                                         int length, VM_Address bufAddress) {
    if (traceJNI) VM.sysWrite("JNI called: GetByteArrayRegion  \n");


    VM_JNIEnvironment env;
    try {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      byte sourceArray[] = (byte []) env.getJNIRef(arrayJREF);

      if ((startIndex<0) || (startIndex+length>sourceArray.length)) {
        env.recordException(new ArrayIndexOutOfBoundsException());
        return;
      }

      VM_Memory.memcopy(bufAddress, VM_Magic.objectAsAddress(sourceArray).add(startIndex), length); 

    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      env.recordException(unexpected);
      return;
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
  private static void GetCharArrayRegion(int envJREF, int arrayJREF, int startIndex,  
                                         int length, VM_Address bufAddress) {
    if (traceJNI) VM.sysWrite("JNI called: GetCharArrayRegion  \n");


    VM_JNIEnvironment env;
    try {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      char sourceArray[] = (char []) env.getJNIRef(arrayJREF);

      if ((startIndex<0) || (startIndex+length>sourceArray.length)) {
        env.recordException(new ArrayIndexOutOfBoundsException());
        return;
      }

      VM_Memory.memcopy(bufAddress, VM_Magic.objectAsAddress(sourceArray).add(startIndex*2), length*2); 

    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      env.recordException(unexpected);
      return;
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
  private static void GetShortArrayRegion(int envJREF, int arrayJREF, int startIndex,  
                                          int length, VM_Address bufAddress) {
    if (traceJNI) VM.sysWrite("JNI called: GetShortArrayRegion  \n");


    VM_JNIEnvironment env;
    try {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      short sourceArray[] = (short []) env.getJNIRef(arrayJREF);

      if ((startIndex<0) || (startIndex+length>sourceArray.length)) {
        env.recordException(new ArrayIndexOutOfBoundsException());
        return;
      }

      VM_Memory.memcopy(bufAddress, VM_Magic.objectAsAddress(sourceArray).add(startIndex*2), length*2); 

    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      env.recordException(unexpected);
      return;
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
  private static void GetIntArrayRegion(int envJREF, int arrayJREF, int startIndex, 
                                        int length, VM_Address bufAddress) {
    if (traceJNI) VM.sysWrite("JNI called: GetIntArrayRegion  \n");


    VM_JNIEnvironment env;
    try {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      int sourceArray[] = (int []) env.getJNIRef(arrayJREF);

      if ((startIndex<0) || (startIndex+length>sourceArray.length)) {
        env.recordException(new ArrayIndexOutOfBoundsException());
        return;
      }

      VM_Memory.memcopy(bufAddress, VM_Magic.objectAsAddress(sourceArray).add(startIndex*4), length*4); 

    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      env.recordException(unexpected);
      return;
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
  private static void GetLongArrayRegion(int envJREF, int arrayJREF, int startIndex, 
                                         int length, VM_Address bufAddress) {
    if (traceJNI) VM.sysWrite("JNI called: GetLongArrayRegion   \n");


    VM_JNIEnvironment env;
    try {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      long sourceArray[] = (long []) env.getJNIRef(arrayJREF);

      if ((startIndex<0) || (startIndex+length>sourceArray.length)) {
        env.recordException(new ArrayIndexOutOfBoundsException());
        return;
      }

      VM_Memory.memcopy(bufAddress, VM_Magic.objectAsAddress(sourceArray).add(startIndex*8), length*8); 

    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      env.recordException(unexpected);
      return;
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
  private static void GetFloatArrayRegion(int envJREF, int arrayJREF, int startIndex, 
                                          int length, VM_Address bufAddress) {
    if (traceJNI) VM.sysWrite("JNI called: GetFloatArrayRegion    \n");


    VM_JNIEnvironment env;
    try {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      float sourceArray[] = (float []) env.getJNIRef(arrayJREF);

      if ((startIndex<0) || (startIndex+length>sourceArray.length)) {
        env.recordException(new ArrayIndexOutOfBoundsException());
        return;
      }

      VM_Memory.memcopy(bufAddress, VM_Magic.objectAsAddress(sourceArray).add(startIndex*4), length*4); 

    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      env.recordException(unexpected);
      return;
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
  private static void GetDoubleArrayRegion(int envJREF, int arrayJREF, int startIndex, 
                                           int length, VM_Address bufAddress) {
    if (traceJNI) VM.sysWrite("JNI called: GetDoubleArrayRegion   \n");


    VM_JNIEnvironment env;
    try {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      double sourceArray[] = (double []) env.getJNIRef(arrayJREF);

      if ((startIndex<0) || (startIndex+length>sourceArray.length)) {
        env.recordException(new ArrayIndexOutOfBoundsException());
        return;
      }

      VM_Memory.memcopy(bufAddress, VM_Magic.objectAsAddress(sourceArray).add(startIndex*8), length*8); 

    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      env.recordException(unexpected);
      return;
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
  private static void SetBooleanArrayRegion(int envJREF, int arrayJREF, int startIndex, 
                                            int length, VM_Address bufAddress) {
    if (traceJNI) VM.sysWrite("JNI called: SetBooleanArrayRegion  \n");


    VM_JNIEnvironment env;
    try {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      boolean destinationArray[] = (boolean []) env.getJNIRef(arrayJREF);

      if ((startIndex<0) || (startIndex+length>destinationArray.length)) {
        env.recordException(new ArrayIndexOutOfBoundsException());
        return;
      }

      VM_Memory.memcopy(VM_Magic.objectAsAddress(destinationArray).add(startIndex), bufAddress, length); 

    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      env.recordException(unexpected);
      return;
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
  private static void SetByteArrayRegion(int envJREF, int arrayJREF, int startIndex, 
                                         int length, VM_Address bufAddress) {
    if (traceJNI) VM.sysWrite("JNI called: SetByteArrayRegion  \n");


    VM_JNIEnvironment env;
    try {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      byte destinationArray[] = (byte []) env.getJNIRef(arrayJREF);

      if ((startIndex<0) || (startIndex+length>destinationArray.length)) {
        env.recordException(new ArrayIndexOutOfBoundsException());
        return;
      }

      VM_Memory.memcopy(VM_Magic.objectAsAddress(destinationArray).add(startIndex), bufAddress, length); 

    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      env.recordException(unexpected);
      return;
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
  private static void SetCharArrayRegion(int envJREF, int arrayJREF, int startIndex, 
                                         int length, VM_Address bufAddress) {
    if (traceJNI) VM.sysWrite("JNI called: SetCharArrayRegion  \n");


    VM_JNIEnvironment env;
    try {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      char destinationArray[] = (char []) env.getJNIRef(arrayJREF);

      if ((startIndex<0) || (startIndex+length>destinationArray.length)) {
        env.recordException(new ArrayIndexOutOfBoundsException());
        return;
      }

      VM_Memory.memcopy(VM_Magic.objectAsAddress(destinationArray).add(startIndex*2), bufAddress, length*2); 

    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      env.recordException(unexpected);
      return;
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
  private static void SetShortArrayRegion(int envJREF, int arrayJREF, int startIndex, 
                                          int length, VM_Address bufAddress) {
    if (traceJNI) VM.sysWrite("JNI called: SetShortArrayRegion  \n");


    VM_JNIEnvironment env;
    try {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      short destinationArray[] = (short []) env.getJNIRef(arrayJREF);

      if ((startIndex<0) || (startIndex+length>destinationArray.length)) {
        env.recordException(new ArrayIndexOutOfBoundsException());
        return;
      }

      VM_Memory.memcopy(VM_Magic.objectAsAddress(destinationArray).add(startIndex*2), bufAddress, length*2); 

    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      env.recordException(unexpected);
      return;
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
  private static void SetIntArrayRegion(int envJREF, int arrayJREF, int startIndex, 
                                        int length, VM_Address bufAddress) {
    if (traceJNI) VM.sysWrite("JNI called: SetIntArrayRegion  \n");


    VM_JNIEnvironment env;
    try {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      int destinationArray[] = (int []) env.getJNIRef(arrayJREF);

      if ((startIndex<0) || (startIndex+length>destinationArray.length)) {
        env.recordException(new ArrayIndexOutOfBoundsException());
        return;
      }

      VM_Memory.memcopy(VM_Magic.objectAsAddress(destinationArray).add(startIndex*4), bufAddress, length*4); 

    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      env.recordException(unexpected);
      return;
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
  private static void SetLongArrayRegion(int envJREF, int arrayJREF, int startIndex, 
                                         int length, VM_Address bufAddress) {
    if (traceJNI) VM.sysWrite("JNI called: SetLongArrayRegion  \n");


    VM_JNIEnvironment env;
    try {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      long destinationArray[] = (long []) env.getJNIRef(arrayJREF);

      if ((startIndex<0) || (startIndex+length>destinationArray.length)) {
        env.recordException(new ArrayIndexOutOfBoundsException());
        return;
      }

      VM_Memory.memcopy(VM_Magic.objectAsAddress(destinationArray).add(startIndex*8), bufAddress, length*8); 

    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      env.recordException(unexpected);
      return;
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
  private static void SetFloatArrayRegion(int envJREF, int arrayJREF, int startIndex, 
                                          int length, VM_Address bufAddress) {
    if (traceJNI) VM.sysWrite("JNI called: SetFloatArrayRegion  \n");


    VM_JNIEnvironment env;
    try {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      float destinationArray[] = (float []) env.getJNIRef(arrayJREF);

      if ((startIndex<0) || (startIndex+length>destinationArray.length)) {
        env.recordException(new ArrayIndexOutOfBoundsException());
        return;
      }

      VM_Memory.memcopy(VM_Magic.objectAsAddress(destinationArray).add(startIndex*4), bufAddress, length*4); 

    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      env.recordException(unexpected);
      return;
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
  private static void SetDoubleArrayRegion(int envJREF, int arrayJREF, int startIndex, 
                                           int length, VM_Address bufAddress) {
    if (traceJNI) VM.sysWrite("JNI called: SetDoubleArrayRegion  \n");


    VM_JNIEnvironment env;
    try {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      double destinationArray[] = (double []) env.getJNIRef(arrayJREF);

      if ((startIndex<0) || (startIndex+length>destinationArray.length)) {
        env.recordException(new ArrayIndexOutOfBoundsException());
        return;
      }

      VM_Memory.memcopy(VM_Magic.objectAsAddress(destinationArray).add(startIndex*8), bufAddress, length*8); 

    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      env.recordException(unexpected);
      return;
    }

  }


  private static int RegisterNatives(int envHandler) {
    if (traceJNI) VM.sysWrite("JNI called: RegisterNatives  \n");

    VM_Scheduler.traceback("JNI ERROR: RegisterNatives not implemented yet.");
    VM.sysExit(200);
    return REGISTERNATIVES   ; 
  }


  private static int UnregisterNatives(int envHandler) {
    if (traceJNI) VM.sysWrite("JNI called: UnregisterNatives  \n");

    VM_Scheduler.traceback("JNI ERROR: UnregisterNatives not implemented yet.");
    VM.sysExit(200);
    return UNREGISTERNATIVES ; 
  }


  /**
   * MonitorEnter
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the object to lock
   * @return 0 if the object is locked successfully, -1 if not
   */
  private static int MonitorEnter(int envJREF, int objJREF) {
    if (traceJNI) VM.sysWrite("JNI called: MonitorEnter  \n");

    VM_JNIEnvironment env;
    try {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      Object obj = (Object) env.getJNIRef(objJREF);
      VM_ObjectModel.genericLock(obj);
      return 0;
    } catch (Throwable unexpected) {
      return -1;
    }
  }


  /**
   * MonitorExit
   * @param envJREF a JREF index for the JNI environment object
   * @param objJREF a JREF index for the object to unlock
   * @return 0 if the object is unlocked successfully, -1 if not
   */
  private static int MonitorExit(int envJREF, int objJREF) {
    if (traceJNI) VM.sysWrite("JNI called: MonitorExit  \n");

    VM_JNIEnvironment env;
    try {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      Object obj = (Object) env.getJNIRef(objJREF);
      VM_ObjectModel.genericUnlock(obj);
      return 0;
    } catch (Throwable unexpected) {
      return -1;
    }
  }

  // this is the address of the malloc'ed JavaVM struct (one per VM)
  private static VM_Address JavaVM; 

  private static native int createJavaVM();

  private static int GetJavaVM(int envJREF, VM_Address StarStarJavaVM) {
    if (traceJNI) VM.sysWrite("JNI called: GetJavaVM \n");

    if (JavaVM == null) {
	int addr = createJavaVM();
	JavaVM = VM_Address.fromInt( addr );
	VM.sysWriteln(addr);
    }
    
    if (traceJNI) VM.sysWriteln(StarStarJavaVM.toInt());
    VM_Magic.setMemoryWord(StarStarJavaVM, JavaVM.toInt());

    return 0;
  }

  /*******************************************************************
   * These functions are added in Java 2
   */

  private static int FromReflectedMethod(int envHandler) {
    if (traceJNI) VM.sysWrite("JNI called: FromReflectedMethod \n");   
    VM.sysWrite("JNI ERROR: FromReflectedMethod not implemented yet, exiting ...\n");
    VM.sysExit(200);
    return FROMREFLECTEDMETHOD ; 
  }

  private static int FromReflectedField(int envHandler) {
    if (traceJNI) VM.sysWrite("JNI called: FromReflectedField \n");   
    VM.sysWrite("JNI ERROR: FromReflectedField not implemented yet, exiting ...\n");
    VM.sysExit(200);
    return FROMREFLECTEDFIELD ; 
  }

  private static int ToReflectedMethod(int envHandler) {
    if (traceJNI) VM.sysWrite("JNI called: ToReflectedMethod \n");   
    VM.sysWrite("JNI ERROR: ToReflectedMethod not implemented yet, exiting ...\n");
    VM.sysExit(200);
    return TOREFLECTEDMETHOD ; 
  }

  private static int ToReflectedField(int envHandler) {
    if (traceJNI) VM.sysWrite("JNI called: ToReflectedField \n");   
    VM.sysWrite("JNI ERROR: ToReflectedField not implemented yet, exiting ...\n");
    VM.sysExit(200);
    return TOREFLECTEDFIELD ; 
  }

  private static int PushLocalFrame(int envHandler) {
    if (traceJNI) VM.sysWrite("JNI called: PushLocalFrame \n");   
    VM.sysWrite("JNI ERROR: PushLocalFrame not implemented yet, exiting ...\n");
    VM.sysExit(200);
    return PUSHLOCALFRAME ; 
  }

  private static int PopLocalFrame(int envHandler) {
    if (traceJNI) VM.sysWrite("JNI called: PopLocalFrame \n");   
    VM.sysWrite("JNI ERROR: PopLocalFrame not implemented yet, exiting ...\n");
    VM.sysExit(200);
    return POPLOCALFRAME ; 
  }

  private static int NewLocalRef(int envHandler) {
    if (traceJNI) VM.sysWrite("JNI called: NewLocalRef \n");   
    VM.sysWrite("JNI ERROR: NewLocalRef not implemented yet, exiting ...\n");
    VM.sysExit(200);
    return NEWLOCALREF ; 
  }

  private static int EnsureLocalCapacity(int envHandler) {
    if (traceJNI) VM.sysWrite("JNI called: EnsureLocalCapacity \n");   
    VM.sysWrite("JNI ERROR: EnsureLocalCapacity not implemented yet, exiting ...\n");
    VM.sysExit(200);
    return ENSURELOCALCAPACITY ; 
  }

  private static int GetStringRegion(int envHandler) {
    if (traceJNI) VM.sysWrite("JNI called: GetStringRegion \n");   
    VM.sysWrite("JNI ERROR: GetStringRegion not implemented yet, exiting ...\n");
    VM.sysExit(200);
    return GETSTRINGREGION ; 
  }

  private static int GetStringUTFRegion(int envHandler) {
    if (traceJNI) VM.sysWrite("JNI called: GetStringUTFRegion \n");   
    VM.sysWrite("JNI ERROR: GetStringUTFRegion not implemented yet, exiting ...\n");
    VM.sysExit(200);
    return GETSTRINGUTFREGION ; 
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
  private static VM_Address GetPrimitiveArrayCritical(int envHandler, int arrayJREF, VM_Address isCopyAddress) {

    if (traceJNI) VM.sysWrite("JNI called: GetPrimitiveArrayCritical \n");   

    VM_JNIEnvironment env;
    try {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      // retrieve the real String object from the side table
      Object primitiveArray = env.getJNIRef(arrayJREF);

      // not an array, return null
      if (!primitiveArray.getClass().isArray())
        return VM_Address.zero();

      // set Copy flag to false, if it's a valid address
      if (!isCopyAddress.isZero()) {
        int temp = VM_Magic.getMemoryWord(isCopyAddress);
        if (LittleEndian) {
          VM_Magic.setMemoryWord(isCopyAddress, ((temp & 0xffffff00)));
        } else {
          VM_Magic.setMemoryWord(isCopyAddress, ((temp & 0x00ffffff)));
        }
      }

      // For array of primitive, return the object address, which is the array itself
      VM.disableGC();
      return VM_Magic.objectAsAddress(primitiveArray);
    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
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

  private static void ReleasePrimitiveArrayCritical(int envHandler, int arrayJREF, 
                                                    int arrayCopyAddress, int mode) {
    if (traceJNI) VM.sysWrite("JNI called: ReleasePrimitiveArrayCritical \n");   

    VM_JNIEnvironment env;
    try {
      VM.enableGC();
    } catch (Throwable unexpected) {
      env = VM_Thread.getCurrentThread().getJNIEnv();
      env.recordException(unexpected);
    }
  }

  private static int GetStringCritical(int envHandler) {
    if (traceJNI) VM.sysWrite("JNI called: GetStringCritical \n");   
    VM.sysWrite("JNI ERROR: GetStringCritical not implemented yet, exiting ...\n");
    VM.sysExit(200);
    return GETSTRINGCRITICAL ; 
  }

  private static int ReleaseStringCritical(int envHandler) {
    if (traceJNI) VM.sysWrite("JNI called: ReleaseStringCritical \n");   
    VM.sysWrite("JNI ERROR: ReleaseStringCritical not implemented yet, exiting ...\n");
    VM.sysExit(200);
    return RELEASESTRINGCRITICAL ; 
  }

  private static int NewWeakGlobalRef(int envHandler) {
    if (traceJNI) VM.sysWrite("JNI called: NewWeakGlobalRef \n");   
    VM.sysWrite("JNI ERROR: NewWeakGlobalRef not implemented yet, exiting ...\n");
    VM.sysExit(200);
    return NEWWEAKGLOBALREF ; 
  }

  private static int DeleteWeakGlobalRef(int envHandler) {
    if (traceJNI) VM.sysWrite("JNI called: DeleteWeakGlobalRef \n");   
    VM.sysWrite("JNI ERROR: DeleteWeakGlobalRef not implemented yet, exiting ...\n");
    VM.sysExit(200);
    return DELETEWEAKGLOBALREF ; 
  }

  private static int ExceptionCheck(int envHandler) {
    if (traceJNI) VM.sysWrite("JNI called: ExceptionCheck \n");   
    VM.sysWrite("JNI ERROR: ExceptionCheck not implemented yet, exiting ...\n");
    VM.sysExit(200);
    return EXCEPTIONCHECK ; 
  }

  private static int reserved0(int envHandler) {
    VM.sysWrite("JNI ERROR: reserved function slot not implemented, exiting ...\n");
    VM.sysExit(200);
    return RESERVED0 ; 
  }

  private static int reserved1(int envHandler){
    VM.sysWrite("JNI ERROR: reserved function slot not implemented, exiting ...\n");
    VM.sysExit(200);		       
    return RESERVED1 ; 		       
  }				       

  private static int reserved2(int envHandler){	       
    VM.sysWrite("JNI ERROR: reserved function slot not implemented, exiting ...\n");
    VM.sysExit(200);		       
    return RESERVED2 ; 		       
  }				       				       

  private static int reserved3(int envHandler){	       
    VM.sysWrite("JNI ERROR: reserved function slot not implemented, exiting ...\n");
    VM.sysExit(200);		       
    return RESERVED3 ; 		       
  }				       				       				      
}
