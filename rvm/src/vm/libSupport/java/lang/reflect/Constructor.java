/*
 * (C) Copyright IBM Corp 2002
 */
//$Id$
package java.lang.reflect;

import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.VM_Reflection;
import com.ibm.JikesRVM.VM_Runtime;
import com.ibm.JikesRVM.memoryManagers.mmInterface.MM_Interface;

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
 */
public final class Constructor extends AccessibleObject implements Member {
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
    StringBuffer buf;
    String mods;
    Class[] types;
    Class current;
    int i, arity;
        
    buf = new StringBuffer();
    mods = Modifier.toString(getModifiers());
    if(mods.length() != 0) {
      buf.append(mods);
      buf.append(" ");
    }
    buf.append(getName());
    buf.append("(");
    types = getParameterTypes();
    for(i = 0; i < types.length; i++) {
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
      for(i = 0; i < types.length; i++) {
        current = types[i];
        buf.append(current.getName());
        if(i != (types.length - 1))
          buf.append(",");
      }
    }
    return buf.toString();
  }
}
