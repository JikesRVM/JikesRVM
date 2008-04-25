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
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.NoInline;

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

  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={0})
  Object invoke(Object receiver, Object[] args)
      throws IllegalAccessException, IllegalArgumentException,
      ExceptionInInitializerError, InvocationTargetException {
    // validate number and types of arguments
    if (checkArguments(args)) {
      if (method.isStatic()) {
        return invokeStatic(receiver, args);
      } else {
        return invokeVirtual(receiver, args);
      }
    } else {
      if (method.isStatic()) {
        return invokeStatic(receiver, makeArgumentsCompatible(args));
      } else {
        return invokeVirtual(receiver, makeArgumentsCompatible(args));
      }
    }
  }

  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={0})
  private Object invokeStatic(Object receiver, Object[] args)
      throws IllegalAccessException, IllegalArgumentException,
      ExceptionInInitializerError, InvocationTargetException {
    // Accessibility checks
    VM_Method method = this.method;
    if (!method.isPublic() && !m.isAccessible()) {
      checkAccess();
    }

    // Forces initialization of declaring class
    VM_Class declaringClass = method.getDeclaringClass();
    if (!declaringClass.isInitialized()) {
      runClassInitializer(declaringClass);
    }

    // Invoke method
    try {
      return VM_Reflection.invoke(method, receiver, args);
    } catch (Throwable t) {
      throw new InvocationTargetException(t);
    }
  }

  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={0})
  private Object invokeVirtual(Object receiver, Object[] args)
      throws IllegalAccessException, IllegalArgumentException,
      ExceptionInInitializerError, InvocationTargetException {
    // validate "this" argument
    if (receiver == null) {
      throw new NullPointerException();
    }
    VM_Method method = this.method;
    VM_Class declaringClass = method.getDeclaringClass();
    if (!JikesRVMSupport.isArgumentCompatible(declaringClass, receiver)) {
      throw new IllegalArgumentException();
    }

    // Accessibility checks
    if (!method.isPublic() && !m.isAccessible()) {
      checkAccess();
    }

    // find the right method to call
    VM_Class C = VM_Magic.getObjectType(receiver).asClass();
    method = C.findVirtualMethod(method.getName(), method.getDescriptor());

    // Invoke method
    try {
      return VM_Reflection.invoke(method, receiver, args);
    } catch (Throwable t) {
      throw new InvocationTargetException(t);
    }
  }

  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={0})
  private boolean checkArguments(Object[] args) throws IllegalArgumentException {
    VM_TypeReference[] parameterTypes = method.getParameterTypes();
    if (((args == null) && (parameterTypes.length != 0)) ||
        ((args != null) && (args.length != parameterTypes.length))) {
      throw new IllegalArgumentException("argument count mismatch");
    }
    switch (parameterTypes.length) {
    case 6:
      if (!JikesRVMSupport.isArgumentCompatible(parameterTypes[5].resolve(), args[5])) {
        return false;
      }
    case 5:
      if (!JikesRVMSupport.isArgumentCompatible(parameterTypes[4].resolve(), args[4])) {
        return false;
      }
    case 4:
      if (!JikesRVMSupport.isArgumentCompatible(parameterTypes[3].resolve(), args[3])) {
        return false;
      }
    case 3:
      if (!JikesRVMSupport.isArgumentCompatible(parameterTypes[2].resolve(), args[2])) {
        return false;
      }
    case 2:
      if (!JikesRVMSupport.isArgumentCompatible(parameterTypes[1].resolve(), args[1])) {
        return false;
      }
    case 1:
      if (!JikesRVMSupport.isArgumentCompatible(parameterTypes[0].resolve(), args[0])) {
        return false;
      }
    case 0:
      return true;
    default:
      for (int i=0, n = parameterTypes.length; i < n; ++i) {
        if (!JikesRVMSupport.isArgumentCompatible(parameterTypes[i].resolve(), args[i])) {
          return false;
        }
      }
      return true;
    }
  }

  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={0})
  private  Object[] makeArgumentsCompatible(Object[] args) {
    VM_TypeReference[] parameterTypes = method.getParameterTypes();
    int length = parameterTypes.length;
    Object[] newArgs = new Object[length];
    switch(length) {
    case 6: newArgs[5] = JikesRVMSupport.makeArgumentCompatible(parameterTypes[5].peekType(), args[5]);
    case 5: newArgs[4] = JikesRVMSupport.makeArgumentCompatible(parameterTypes[4].peekType(), args[4]);
    case 4: newArgs[3] = JikesRVMSupport.makeArgumentCompatible(parameterTypes[3].peekType(), args[3]);
    case 3: newArgs[2] = JikesRVMSupport.makeArgumentCompatible(parameterTypes[2].peekType(), args[2]);
    case 2: newArgs[1] = JikesRVMSupport.makeArgumentCompatible(parameterTypes[1].peekType(), args[1]);
    case 1: newArgs[0] = JikesRVMSupport.makeArgumentCompatible(parameterTypes[0].peekType(), args[0]);
    case 0: break;
    default: {
      for (int i = 0; i < length; ++i) {
        newArgs[i] = JikesRVMSupport.makeArgumentCompatible(parameterTypes[i].peekType(), args[i]);
      }
      break;
    }
    }
    return newArgs;
  }

  @NoInline
  private static void runClassInitializer(VM_Class declaringClass) throws ExceptionInInitializerError {
    try {
      VM_Runtime.initializeClassForDynamicLink(declaringClass);
    } catch (Throwable e) {
      ExceptionInInitializerError ex = new ExceptionInInitializerError();
      ex.initCause(e);
      throw ex;
    } 
  }

  @NoInline
  private void checkAccess() throws IllegalAccessException {
    VM_Class accessingClass = VM_Class.getClassFromStackFrame(4);
    JikesRVMSupport.checkAccess(method, accessingClass);
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
