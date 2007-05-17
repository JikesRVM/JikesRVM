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
public final class Method extends AccessibleObject implements Member {
  final VM_Method method;

   // Prevent this class from being instantiated.
  private Method() {
    method = null;
  }
    
  // For use by JikesRVMSupport
  Method(VM_Method m) {
    method = m;
  }

  public boolean equals(Object other) { 
    if (other instanceof Method) {
      return method == ((Method)other).method;
    } else {
      return false;
    }
  }

  public Class<?> getDeclaringClass() {
    return method.getDeclaringClass().getClassForType();
  }

  public Class<?>[] getExceptionTypes() {
    VM_TypeReference[] exceptionTypes = method.getExceptionTypes();
    if (exceptionTypes == null) {
      return new Class[0];
    } else {
      return JikesRVMSupport.typesToClasses(exceptionTypes);
    }
  }

  public int getModifiers() {
    return method.getModifiers();
  }

  public String getName() {
    return method.getName().toString();
  }

  public Class<?>[] getParameterTypes() {
    return JikesRVMSupport.typesToClasses(method.getParameterTypes());
  }

  public boolean isSynthetic() {
    return method.isSynthetic();
  }

  public Class<?> getReturnType() {
    return method.getReturnType().resolve().getClassForType();
  }

  public int hashCode() {
    int code1 = getName().hashCode();
    int code2 = method.getDeclaringClass().toString().hashCode();
    return code1 ^ code2;
  }

  public Object invoke(Object receiver, Object[] args) throws IllegalAccessException,
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
    if (!method.isPublic() && !isAccessible()) {
      VM_Class accessingClass = VM_Class.getClassFromStackFrame(1);
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
                "While invoking the method:\n\t\"" + method + "\"\n"
                + " on the object:\n\t\"" + receiver + "\"\n"
                + " it threw the exception:\n\t\"" + t + "\"");
     }
  }

  public String toString() {
    StringBuilder sb = new StringBuilder(128);
    Modifier.toString(getModifiers(), sb).append(' ');
    sb.append(JikesRVMHelpers.getUserName(getReturnType())).append(' ');
    sb.append(getDeclaringClass().getName()).append('.');
    sb.append(getName()).append('(');
    Class<?>[] c = getParameterTypes();
    if (c.length > 0)
      {
        sb.append(JikesRVMHelpers.getUserName(c[0]));
        for (int i = 1; i < c.length; i++)
          sb.append(',').append(JikesRVMHelpers.getUserName(c[i]));
      }
    sb.append(')');
    c = getExceptionTypes();
    if (c.length > 0)
      {
        sb.append(" throws ").append(c[0].getName());
        for (int i = 1; i < c.length; i++)
          sb.append(',').append(c[i].getName());
      }
    return sb.toString();
  }

  // AnnotatedElement interface

  public Annotation[] getDeclaredAnnotations() {
    return method.getDeclaredAnnotations();
  }

  public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
    return method.getAnnotation(annotationClass);
  }

  // Generics support

  public TypeVariable<?>[] getTypeParameters() {
    VM_Atom sig = method.getSignature();
    if (sig == null) {
      return new TypeVariable[0];
    } else {
      return JikesRVMHelpers.getTypeParameters(this, sig);
    }
  }

  public Type[] getGenericExceptionTypes() {
    VM_Atom sig = method.getSignature();
    if (sig == null) {
      return getExceptionTypes();
    } else {
      return JikesRVMHelpers.getGenericExceptionTypes(this, sig);
    }
  }

  public Type[] getGenericParameterTypes() {
    VM_Atom sig = method.getSignature();
    if (sig == null) {
      return getParameterTypes();
    } else {
      return JikesRVMHelpers.getGenericParameterTypes(this, sig);
    }
  }

  public Type getGenericReturnType() {
    VM_Atom sig = method.getSignature();
    if (sig == null) {
      return getReturnType();
    } else {
      return JikesRVMHelpers.getGenericReturnType(this, sig);
    }
  }

  public String toGenericString() {
    StringBuilder sb = new StringBuilder(128);
    Modifier.toString(getModifiers(), sb).append(' ');
    Constructor.addTypeParameters(sb, getTypeParameters());
    sb.append(getGenericReturnType()).append(' ');
    sb.append(getDeclaringClass().getName()).append('.');
    sb.append(getName()).append('(');
    Type[] types = getGenericParameterTypes();
    if (types.length > 0)
      {
        sb.append(types[0]);
        for (int i = 1; i < types.length; i++)
          sb.append(',').append(types[i]);
      }
    sb.append(')');
    types = getGenericExceptionTypes();
    if (types.length > 0)
      {
        sb.append(" throws ").append(types[0]);
        for (int i = 1; i < types.length; i++)
          sb.append(',').append(types[i]);
      }
    return sb.toString();
  }
}
