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
import org.jikesrvm.runtime.VM_Magic;
import org.jikesrvm.runtime.VM_Runtime;

/**
 * Implementation of java.lang.reflect.Field for JikesRVM.
 *
 * By convention, order methods in the same order
 * as they appear in the method summary list of Sun's 1.4 Javadoc API.
 */
final class VMMethod {
  final VM_Method method;
  Method m;

   // Prevent this class from being instantiated.
  @SuppressWarnings("unused")
  private VMMethod() {
    method = null;
  }

  // For use by JikesRVMSupport
  VMMethod(VM_Method m) {
    method = m;
  }

  public boolean equals(Object other) {
    if (other instanceof Method) {
      return method == ((Method)other).m.method;
    } else {
      return false;
    }
  }

  public Class<?> getDeclaringClass() {
    return method.getDeclaringClass().getClassForType();
  }

  Class<?>[] getExceptionTypes() {
    VM_TypeReference[] exceptionTypes = method.getExceptionTypes();
    if (exceptionTypes == null) {
      return new Class[0];
    } else {
      return JikesRVMSupport.typesToClasses(exceptionTypes);
    }
  }

  int getModifiersInternal() {
    return method.getModifiers();
  }

  public String getName() {
    return method.getName().toString();
  }

  Class<?>[] getParameterTypes() {
    return JikesRVMSupport.typesToClasses(method.getParameterTypes());
  }

  Class<?> getReturnType() {
    return method.getReturnType().resolve().getClassForType();
  }

  Object invoke(Object receiver, Object[] args) throws IllegalAccessException,
                         IllegalArgumentException,
                         ExceptionInInitializerError,
                         InvocationTargetException {
    VM_Method method = this.method;
    VM_Class declaringClass = method.getDeclaringClass();

    // validate "this" argument
    if (!method.isStatic()) {
      if (receiver == null) throw new NullPointerException();
      receiver = JikesRVMSupport.makeArgumentCompatible(declaringClass, receiver);
    }

    // validate number and types of remaining arguments
    VM_TypeReference[] parameterTypes = method.getParameterTypes();
    if (args == null) {
      if (parameterTypes.length != 0) {
        throw new IllegalArgumentException("argument count mismatch");
      }
    } else if (args.length != parameterTypes.length) {
      throw new IllegalArgumentException("argument count mismatch");
    }
    for (int i = 0, n = parameterTypes.length; i < n; ++i) {
      args[i] = JikesRVMSupport.makeArgumentCompatible(parameterTypes[i].resolve(), args[i]);
    }

    // Accessibility checks
    if (!method.isPublic() && !m.isAccessible()) {
      VM_Class accessingClass = VM_Class.getClassFromStackFrame(2);
      JikesRVMSupport.checkAccess(method, accessingClass);
    }

    // find the right method to call
    if (! method.isStatic()) {
        VM_Class C = VM_Magic.getObjectType(receiver).asClass();
        method = C.findVirtualMethod(method.getName(), method.getDescriptor());
    }

    // Forces initialization of declaring class
    if (method.isStatic() && !declaringClass.isInitialized()) {
      try {
        VM_Runtime.initializeClassForDynamicLink(declaringClass);
      } catch (Throwable e) {
        ExceptionInInitializerError ex = new ExceptionInInitializerError();
        ex.initCause(e);
        throw ex;
      }
    }

    // Invoke method
    try {
      return VM_Reflection.invoke(method, receiver, args);
    } catch (Throwable t) {
      throw new InvocationTargetException(t,
                "While invoking the method:\n\t\"" + method + "\"\n" +
                " on the object:\n\t\"" + receiver + "\"\n" +
                " it threw the exception:\n\t\"" + t + "\"");
     }
  }

  // AnnotatedElement interface

  Annotation[] getDeclaredAnnotations() {
    return method.getDeclaredAnnotations();
  }

  <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
    return method.getAnnotation(annotationClass);
  }

  Object getDefaultValue() {
    /* FIXME: This should handle the case where this is an annotation method */
    return null;
  }

  String getSignature() {
    return method.getSignature().toString();
  }

  Annotation[][] getParameterAnnotations() {
    return method.getDeclaredParameterAnnotations();
  }

}
