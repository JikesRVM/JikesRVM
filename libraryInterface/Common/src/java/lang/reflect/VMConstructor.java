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
package java.lang.reflect;

import java.lang.annotation.Annotation;

import org.jikesrvm.classloader.*;
import org.jikesrvm.runtime.VM_Reflection;
import org.jikesrvm.runtime.VM_Runtime;

/**
 * Implementation of java.lang.reflect.VMConstructor for JikesRVM.
 *
 * By convention, order methods in the same order
 * as they appear in the method summary list of Sun's 1.4 Javadoc API.
 */
final class VMConstructor {
  final VM_Method constructor;
  Constructor cons;

  // Prevent this class from being instantiated.
  @SuppressWarnings("unused")
  private VMConstructor() {
    constructor = null;
  }

  // For use by JikesRVMSupport
  VMConstructor(VM_Method m) {
    constructor = m;
  }

  public boolean equals(Object other) {
    if (other instanceof Constructor) {
      return constructor == ((Constructor)other).cons.constructor;
    } else {
      return false;
    }
  }

  Class getDeclaringClass() {
    return constructor.getDeclaringClass().getClassForType();
  }

  Class[] getExceptionTypes() {
    VM_TypeReference[] exceptionTypes = constructor.getExceptionTypes();
    if (exceptionTypes == null) {
      return new Class[0];
    } else {
      return JikesRVMSupport.typesToClasses(exceptionTypes);
    }
  }

  int getModifiersInternal() {
    return constructor.getModifiers();
  }

  String getName() {
    return getDeclaringClass().getName();
  }

  Class[] getParameterTypes() {
    return JikesRVMSupport.typesToClasses(constructor.getParameterTypes());
  }

  Object construct(Object[] args) throws InstantiationException,
                IllegalAccessException,
                IllegalArgumentException,
                InvocationTargetException {
    // Check accessibility
    if (!constructor.isPublic() && !cons.isAccessible()) {
      VM_Class accessingClass = VM_Class.getClassFromStackFrame(1);
      JikesRVMSupport.checkAccess(constructor, accessingClass);
    }

    // validate number and types of arguments to constructor
    VM_TypeReference[] parameterTypes = constructor.getParameterTypes();
    if (args == null) {
      if (parameterTypes.length != 0) {
        throw new IllegalArgumentException("argument count mismatch");
      }
    } else {
      if (args.length != parameterTypes.length) {
        throw new IllegalArgumentException("argument count mismatch");
      }
      for (int i = 0; i < parameterTypes.length; i++) {
        args[i] = JikesRVMSupport.makeArgumentCompatible(parameterTypes[i].resolve(), args[i]);
      }
    }

    VM_Class cls = constructor.getDeclaringClass();
    if (cls.isAbstract()) {
      throw new InstantiationException("Abstract class");
    }

    // Ensure that the class is initialized
    if (!cls.isInitialized()) {
      try {
        VM_Runtime.initializeClassForDynamicLink(cls);
      } catch (Throwable e) {
        ExceptionInInitializerError ex = new ExceptionInInitializerError();
        ex.initCause(e);
        throw ex;
      }
    }

    // Allocate an uninitialized instance;
    Object obj = VM_Runtime.resolvedNewScalar(cls);

    // Run the constructor on the instance.
    try {
      VM_Reflection.invoke(constructor, obj, args);
    } catch (Throwable e) {
      throw new InvocationTargetException(e);
    }
    return obj;
  }

  String getSignature() {
    return constructor.getSignature().toString();
  }

  Annotation[][] getParameterAnnotations() {
    return constructor.getDeclaredParameterAnnotations();
  }

  Annotation[] getDeclaredAnnotations() {
    return constructor.getDeclaredAnnotations();
  }

  <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
    return constructor.getAnnotation(annotationClass);
  }

}
