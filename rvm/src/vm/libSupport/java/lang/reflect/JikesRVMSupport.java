/*
 * (C) Copyright IBM Corp 2002
 */
//$Id$
package java.lang.reflect;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_ObjectModel;
import com.ibm.JikesRVM.VM_Runtime;
import com.ibm.JikesRVM.classloader.*;

/**
 * Library support interface of Jikes RVM
 *
 * @author Julian Dolby
 */
public class JikesRVMSupport {

  /**
   * Convert from "vm" type system to "jdk" type system.
   */ 
  static Class[] typesToClasses(VM_TypeReference[] types) {
    Class[] classes = new Class[types.length];
    for (int i = 0; i < types.length; i++) {
      try {
	classes[i] = types[i].resolve().getClassForType();
      } catch (ClassNotFoundException e) {
	throw new InternalError(e.toString()); // Should never happen.
      }
    }
    return classes;
  }

  // Make possibly wrapped method argument compatible with expected type
  // throwing IllegalArgumentException if it cannot be.
  //
  static Object makeArgumentCompatible(VM_Type expectedType, Object arg) {
    if (expectedType.isPrimitiveType()) { 
      if (arg instanceof java.lang.Void) {
	if (expectedType.isVoidType()) return arg;
      } else if (arg instanceof java.lang.Boolean) {
	if (expectedType.isBooleanType()) return arg;
      } else if (arg instanceof java.lang.Byte) {
	if (expectedType.isByteType()) return arg;
	if (expectedType.isShortType()) return new Short(((java.lang.Byte)arg).byteValue());
	if (expectedType.isIntType()) return new Integer(((java.lang.Byte)arg).byteValue());
	if (expectedType.isLongType()) return new Long(((java.lang.Byte)arg).byteValue());
      } else if (arg instanceof java.lang.Short) {
	if (expectedType.isShortType()) return arg;
	if (expectedType.isIntType()) return new Integer(((java.lang.Short)arg).shortValue());
	if (expectedType.isLongType()) return new Long(((java.lang.Short)arg).shortValue());
      } else if (arg instanceof java.lang.Character) {
	if (expectedType.isCharType()) return arg;
	if (expectedType.isIntType()) return new Integer(((java.lang.Character)arg).charValue());
	if (expectedType.isLongType()) return new Long(((java.lang.Character)arg).charValue());
      } else if (arg instanceof java.lang.Integer) {
	if (expectedType.isIntType()) return arg;
	if (expectedType.isLongType()) return new Long(((java.lang.Integer)arg).intValue());
      } else if (arg instanceof java.lang.Long) {
	if (expectedType.isLongType()) return arg;
      } else if (arg instanceof java.lang.Float) {
	if (expectedType.isFloatType()) return arg;
	if (expectedType.isDoubleType()) return new Double(((java.lang.Integer)arg).floatValue());
      } else if (arg instanceof java.lang.Double) {
	if (expectedType.isDoubleType()) return arg;
      }
    } else {
      if (arg == null) return arg; // null is always ok
      VM_Type actualType = VM_ObjectModel.getObjectType(arg);
      if (expectedType == actualType || 
	  expectedType == VM_Type.JavaLangObjectType ||
	  VM_Runtime.isAssignableWith(expectedType, actualType)) {
	return arg;
      }
    } 
    throw new IllegalArgumentException();
  }

  public static Field createField(VM_Field m) {
    return new Field(m);
  }

  public static Method createMethod(VM_Method m) {
    return new Method(m);
  }

  public static Constructor createConstructor(VM_Method m) {
    return new Constructor(m);
  }

  public static VM_Field getFieldOf(Field f) {
    return f.field;
  }

  public static VM_Method getMethodOf(Method f) {
    return f.method;
  }

  public static VM_Method getMethodOf(Constructor f) {
    return f.constructor;
  }
}
