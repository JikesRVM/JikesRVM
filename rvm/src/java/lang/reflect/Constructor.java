/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp 2002
 */
//$Id$
package java.lang.reflect;

import com.ibm.jikesrvm.classloader.*;
import com.ibm.jikesrvm.VM_Reflection;
import com.ibm.jikesrvm.VM_Runtime;

import gnu.java.lang.ClassHelper;
import gnu.java.lang.reflect.MethodSignatureParser;

/**
 * Implementation of java.lang.reflect.Constructor for JikesRVM.
 *
 * By convention, order methods in the same order
 * as they appear in the method summary list of Sun's 1.4 Javadoc API. 
 *
 * @author John Barton 
 * @author Julian Dolby
 * @author Stephen Fink
 * @author Eugene Gluzberg
 * @author Dave Grove
 * @modified Ian Rogers
 */
public final class Constructor extends AccessibleObject
  implements GenericDeclaration, Member
{
  final VM_Method constructor;

  // Prevent this class from being instantiated.
  private Constructor() {
    constructor = null;
  }

  // For use by JikesRVMSupport
  Constructor(VM_Method m) {
    constructor = m;
  }

  public boolean equals(Object other) {
    if (other instanceof Constructor) {
      return constructor == ((Constructor)other).constructor;
    } else {
      return false;
    }
  }
    
  public Class getDeclaringClass() {
    return constructor.getDeclaringClass().getClassForType();
  }

  public Class[] getExceptionTypes() {
    VM_TypeReference[] exceptionTypes = constructor.getExceptionTypes();
    if (exceptionTypes == null) {
      return new Class[0];
    } else {
      return JikesRVMSupport.typesToClasses(exceptionTypes);
    }
  }

  public int getModifiers() {
    return constructor.getModifiers();
  }

  public String getName() {
    return getDeclaringClass().getName();
  }
    
  public Class[] getParameterTypes() {
    return JikesRVMSupport.typesToClasses(constructor.getParameterTypes());
  }

  public int hashCode() {
    return getName().hashCode();
  }

  public boolean isSynthetic() {
	 return constructor.isSynthetic();
  }

  public Object newInstance(Object args[]) throws InstantiationException, 
                                                  IllegalAccessException, 
                                                  IllegalArgumentException, 
                                                  InvocationTargetException {
    // Check accessibility
    if (!constructor.isPublic() && !isAccessible()) {
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

  public String toString() {
    StringBuilder sb = new StringBuilder(128);
    Modifier.toString(getModifiers(), sb).append(' ');
    sb.append(getDeclaringClass().getName()).append('(');
    Class[] c = getParameterTypes();
    if (c.length > 0)
      {
        sb.append(ClassHelper.getUserName(c[0]));
        for (int i = 1; i < c.length; i++)
          sb.append(',').append(ClassHelper.getUserName(c[i]));
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

  // Generics support

  public TypeVariable[] getTypeParameters() {
    VM_Atom sig = constructor.getSignature();
    if (sig == null)
      return new TypeVariable[0];
    MethodSignatureParser p = new MethodSignatureParser(this, sig.toString());
    return p.getTypeParameters();
  }

  public Type[] getGenericExceptionTypes() {
    VM_Atom sig = constructor.getSignature();
    if (sig == null)
      return getExceptionTypes();
    MethodSignatureParser p = new MethodSignatureParser(this, sig.toString());
    return p.getGenericExceptionTypes();
  }

  public Type[] getGenericParameterTypes() {
    VM_Atom sig = constructor.getSignature();
    if (sig == null)
      return getParameterTypes();
    MethodSignatureParser p = new MethodSignatureParser(this, sig.toString());
    return p.getGenericParameterTypes();
  }

  public String toGenericString() {
    StringBuilder sb = new StringBuilder(128);
    Modifier.toString(getModifiers(), sb).append(' ');
    addTypeParameters(sb, getTypeParameters());
    sb.append(getDeclaringClass().getName()).append('(');
    Type[] types = getGenericParameterTypes();
    if (types.length > 0)
      {
        sb.append(types[0]);
        for (int i = 1; i < types.length; ++i)
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

  static void addTypeParameters(StringBuilder sb, TypeVariable[] typeArgs) {
    if (typeArgs.length == 0)
      return;
    sb.append('<');
    for (int i = 0; i < typeArgs.length; ++i)
      {
        if (i > 0)
          sb.append(',');
        sb.append(typeArgs[i]);
      }
    sb.append("> ");
  }
}
