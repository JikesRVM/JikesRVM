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

  public boolean equals(Object object) { 
    return VM_ReflectionSupport.methodEquals(this,object);
  }

  public Class getDeclaringClass() {
    return VM_ReflectionSupport.getDeclaringClass(this);
  }

  public Class[] getExceptionTypes() {
    return VM_ReflectionSupport.getExceptionTypes(this);
  }

  public int getModifiers() {
    return VM_ReflectionSupport.getModifiers(this);
  }

  public String getName() {
    return VM_ReflectionSupport.getName(this);
  }

  public Class[] getParameterTypes() {
    return VM_ReflectionSupport.getParameterTypes(this);
  }

  public Class getReturnType() {
    return VM_ReflectionSupport.getReturnType(this);
  }

  public String getSignature() {
    return VM_ReflectionSupport.getSignature(this);
  }

  public int hashCode() {
    return getName().hashCode();
  }

  public Object invoke(Object receiver, Object args[])
    throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
    return VM_ReflectionSupport.invoke(this,receiver,args);
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
	
    current = getReturnType();
    arity = 0;
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
