/*
 * (C) Copyright IBM Corp 2002
 */
//$Id$
package java.lang.reflect;

import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.VM_Reflection;

/**
 * Implementation of java.lang.reflect.Field for JikesRVM.
 *
 * By convention, order methods in the same order
 * as they appear in the method summary list of Sun's 1.4 Javadoc API. 
 *
 * @author John Barton 
 * @author Julian Dolby
 * @author Stephen Fink
 * @author Eugene Gluzberg
 * @author Dave Grove
 */
public final class Method extends AccessibleObject implements Member {
  VM_Method method;

  /**
   * Prevent this class from being instantiated.
   */
  private Method() {}
    
  // For use by java.lang.reflect.Constructor
  //
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

  public Class getDeclaringClass() {
    return method.getDeclaringClass().getClassForType();
  }

  public Class[] getExceptionTypes() {
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

  public Class[] getParameterTypes() {
    return JikesRVMSupport.typesToClasses(method.getParameterTypes());
  }

  public Class getReturnType() {
    try {
      return method.getReturnType().resolve().getClassForType();
    } catch (ClassNotFoundException e) {
      throw new InternalError(e.toString()); // Should never happen.
    }
  }

  public int hashCode() {
    int code1 = getName().hashCode();
    int code2 = method.getDeclaringClass().toString().hashCode();
    return code1 ^ code2;
  }

  // TODO: This diverges from the spec in a number of way.
  public Object invoke(Object receiver, Object args[])
    throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {

    // validate "this" argument
    //
    if (!method.isStatic()) {
      if (receiver == null) throw new NullPointerException();
      receiver = JikesRVMSupport.makeArgumentCompatible(method.getDeclaringClass(), receiver);
    }
    
    // validate number and types of remaining arguments
    //
    VM_TypeReference[] parameterTypes = method.getParameterTypes();
    if (args == null) {
      if (parameterTypes.length != 0) {
	throw new IllegalArgumentException("argument count mismatch");
      }
    } else if (args.length != parameterTypes.length) {
      throw new IllegalArgumentException("argument count mismatch");
    }
    for (int i = 0, n = parameterTypes.length; i < n; ++i) {
      try {
	args[i] = JikesRVMSupport.makeArgumentCompatible(parameterTypes[i].resolve(), args[i]);
      } catch (ClassNotFoundException e) {
	throw new InternalError(e.toString()); // Should never happen.
      }
    }

    // invoke method
    // Note that we catch all possible exceptions, not just Error's and RuntimeException's,
    // for compatibility with jdk behavior (which even catches a "throw new Throwable()").
    //
    try {
      return VM_Reflection.invoke(method, receiver, args);
    } catch (Throwable e) {
      throw new InvocationTargetException(e);
    }
  }

  public String toString() {
    Class current;
	
    StringBuffer buf = new StringBuffer();
    String mods = Modifier.toString(getModifiers());
    if(mods.length() != 0) {
      buf.append(mods);
      buf.append(" ");
    }
	
    current = getReturnType();
    int arity = 0;
    while(current.isArray()) {
      current = current.getComponentType();
      arity++;
    }
    buf.append(current.getName());
    for(;arity > 0; arity--) buf.append("[]");
	
    buf.append(" ");
    buf.append(getDeclaringClass().getName());
    buf.append(".");
    buf.append(getName());
    buf.append("(");
    Class[] types = getParameterTypes();
    for(int i = 0; i < types.length; i++) {
      current = types[i];
      arity = 0;
      while(current.isArray()) {
	current = current.getComponentType();
	arity++;
      }
      buf.append(current.getName());
      for(;arity > 0; arity--) buf.append("[]");
      if(i != (types.length - 1))
	buf.append(",");
    }
    buf.append(")");
    types = getExceptionTypes();
    if(types.length > 0) {
      buf.append(" throws ");
      for(int i = 0; i < types.length; i++) {
	current = types[i];
	buf.append(current.getName());
	if(i != (types.length - 1))
	  buf.append(",");
      }
    }

    return buf.toString();
  }
}
