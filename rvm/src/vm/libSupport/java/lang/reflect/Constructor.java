/*
 * (C) Copyright IBM Corp 2002
 */
//$Id$
package java.lang.reflect;

import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.VM_Runtime;
import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;

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
  VM_Method constructor;

  /**
   * Prevent this class from being instantiated.
   */
  private Constructor() {}

  // For use by java.lang.Class
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

  // TODO: This is wrong in a number of ways.  
  // Must do all of the error and accessibility checking.
  public Object newInstance(Object args[]) throws InstantiationException, 
						  IllegalAccessException, 
						  IllegalArgumentException, 
						  InvocationTargetException {

    // Get a new instance, uninitialized.
    //
    VM_Class cls = constructor.getDeclaringClass();
    if (!cls.isInitialized()) {
      try {
	VM_Runtime.initializeClassForDynamicLink(cls);
      } catch (Throwable e) {
	InstantiationException ex = new InstantiationException();
	ex.initCause(e);
	throw ex;
      }
    }

    int      size = cls.getInstanceSize();
    Object[] tib  = cls.getTypeInformationBlock();
    boolean  hasFinalizer = cls.hasFinalizer();
    int allocator = VM_Interface.pickAllocator(cls);
    Object   obj  = VM_Runtime.resolvedNewScalar(size, tib, hasFinalizer, allocator);

    // Run <init> on the instance.
    //
    Method m = JikesRVMSupport.createMethod(constructor);

    // Note that <init> is not overloaded but it is
    // not static either: it is called with a "this"
    // pointer but not looked up in the TypeInformationBlock.
    // See VM_Reflection.invoke().
    //
    m.invoke(obj, args);
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
