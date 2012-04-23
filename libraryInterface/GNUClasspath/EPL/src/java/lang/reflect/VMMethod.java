/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package java.lang.reflect;

import java.lang.annotation.Annotation;

import org.jikesrvm.classloader.*;
import org.jikesrvm.runtime.ReflectionBase;
import org.jikesrvm.runtime.Reflection;

/**
 * Implementation of java.lang.reflect.Field for JikesRVM.
 *
 * By convention, order methods in the same order
 * as they appear in the method summary list of Sun's 1.4 Javadoc API.
 */
final class VMMethod {
  final RVMMethod method;
  private final ReflectionBase invoker;

   // Prevent this class from being instantiated.
  @SuppressWarnings("unused")
  private VMMethod() {
    method = null;
    invoker = null;
  }

  // For use by JikesRVMSupport
  VMMethod(RVMMethod m) {
    method = m;
    if (Reflection.cacheInvokerInJavaLangReflect) {
      invoker = m.getInvoker();
    } else {
      invoker = null;
    }
  }

  @Override
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
    TypeReference[] exceptionTypes = method.getExceptionTypes();
    if (exceptionTypes == null) {
      return new Class[0];
    } else {
      return VMCommonLibrarySupport.typesToClasses(exceptionTypes);
    }
  }

  int getModifiersInternal() {
    return method.getModifiers();
  }

  public String getName() {
    return method.getName().toString();
  }

  Class<?>[] getParameterTypes() {
    return VMCommonLibrarySupport.typesToClasses(method.getParameterTypes());
  }

  Class<?> getReturnType() {
    return method.getReturnType().resolve().getClassForType();
  }

  Object invoke(Object receiver, Object[] args, Method m)
      throws IllegalAccessException, IllegalArgumentException,
      ExceptionInInitializerError, InvocationTargetException {
    return VMCommonLibrarySupport.invoke(receiver, args, method, m, RVMClass.getClassFromStackFrame(2), invoker);
  }

  // AnnotatedElement interface

  Annotation[] getDeclaredAnnotations() {
    return method.getDeclaredAnnotations();
  }

  <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
    return method.getAnnotation(annotationClass);
  }

  Object getDefaultValue() {
    return method.getAnnotationDefault();
  }

  String getSignature() {
    return method.getSignature().toString();
  }

  Annotation[][] getParameterAnnotations() {
    return method.getDeclaredParameterAnnotations();
  }
}
