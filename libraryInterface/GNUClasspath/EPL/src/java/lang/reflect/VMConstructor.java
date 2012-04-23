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

import org.jikesrvm.classloader.RVMClass;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.runtime.Reflection;
import org.jikesrvm.runtime.ReflectionBase;

/**
 * Implementation of java.lang.reflect.VMConstructor for JikesRVM.
 *
 * By convention, order methods in the same order
 * as they appear in the method summary list of Sun's 1.4 Javadoc API.
 */
final class VMConstructor {
  final RVMMethod constructor;
  private final ReflectionBase invoker;

  // Prevent this class from being instantiated.
  @SuppressWarnings("unused")
  private VMConstructor() {
    constructor = null;
    invoker = null;
  }

  // For use by JikesRVMSupport
  VMConstructor(RVMMethod m) {
    constructor = m;
    if (Reflection.cacheInvokerInJavaLangReflect) {
      invoker = m.getInvoker();
    } else {
      invoker = null;
    }
  }

  @Override
  public boolean equals(Object other) {
    if (other instanceof Constructor) {
      return constructor == ((Constructor<?>)other).cons.constructor;
    } else {
      return false;
    }
  }

  Class<?> getDeclaringClass() {
    return constructor.getDeclaringClass().getClassForType();
  }

  Class<?>[] getExceptionTypes() {
    TypeReference[] exceptionTypes = constructor.getExceptionTypes();
    if (exceptionTypes == null) {
      return new Class[0];
    } else {
      return VMCommonLibrarySupport.typesToClasses(exceptionTypes);
    }
  }

  int getModifiersInternal() {
    return constructor.getModifiers();
  }

  String getName() {
    return getDeclaringClass().getName();
  }

  Class<?>[] getParameterTypes() {
    return VMCommonLibrarySupport.typesToClasses(constructor.getParameterTypes());
  }

  Object construct(Object[] args, Constructor<?> cons) throws InstantiationException,
                IllegalAccessException,
                IllegalArgumentException,
                InvocationTargetException {
    return VMCommonLibrarySupport.construct(constructor, cons, args, RVMClass.getClassFromStackFrame(2), invoker);
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
