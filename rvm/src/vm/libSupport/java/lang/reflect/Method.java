/*
 * (C) Copyright IBM Corp 2002
 */
//$Id$
package java.lang.reflect;

import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.VM_Reflection;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_Runtime;

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
 * @modified Steven Augart
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
    return method.getReturnType().resolve().getClassForType();
  }

  public int hashCode() {
    int code1 = getName().hashCode();
    int code2 = method.getDeclaringClass().toString().hashCode();
    return code1 ^ code2;
  }

  public Object invoke(Object receiver, Object args[]) throws IllegalAccessException, 
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
