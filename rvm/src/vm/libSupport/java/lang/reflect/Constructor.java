/*
 * (C) Copyright IBM Corp 2002
 */
//$Id$
package java.lang.reflect;

import com.ibm.JikesRVM.classloader.VM_Method;
import com.ibm.JikesRVM.classloader.VM_ReflectionSupport;

/**
 * Library support interface of Jikes RVM
 *
 * @author Julian Dolby
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
    return VM_ReflectionSupport.getExceptionTypes(this);
  }

  public int getModifiers() {
    return VM_ReflectionSupport.getModifiers(this);
  }

  public String getName() {
    return getDeclaringClass().getName();
  }
    
  public Class[] getParameterTypes() {
    return VM_ReflectionSupport.getParameterTypes(this);
  }

  public String getSignature() {
    return VM_ReflectionSupport.getSignature(this);
  } 

  public int hashCode() {
    return getName().hashCode();
  }

  public Object newInstance(Object args[])
    throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException
  {
    return VM_ReflectionSupport.newInstance(this,args);
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
