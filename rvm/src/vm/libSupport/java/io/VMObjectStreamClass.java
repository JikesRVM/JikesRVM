/*
 * (C) Copyright IBM Corp 2002
 */
//$Id$
package java.io;

import java.lang.reflect.Field;

import com.ibm.JikesRVM.classloader.VM_Class;
import com.ibm.JikesRVM.classloader.VM_Field;
import com.ibm.JikesRVM.classloader.VM_Type;

/**
 * java.io.ObjectStream helper implemented for Jikes RVM.
 *
 * @author Dave Grove
 */
final class VMObjectStreamClass {

  static boolean hasClassInitializer (Class cls) {
    VM_Type t = java.lang.JikesRVMSupport.getTypeForClass(cls);
    if (t.isClassType()) {
      return t.asClass().getClassInitializerMethod() != null;
    } else {
      return false;
    }
  }

  static void setDoubleNative(Field field, Object obj, double val) {
    VM_Field f = java.lang.reflect.JikesRVMSupport.getFieldOf(field);
    f.setDoubleValueUnchecked(obj, val);
  }

  static void setFloatNative(Field field, Object obj, float val) {
    VM_Field f = java.lang.reflect.JikesRVMSupport.getFieldOf(field);
    f.setFloatValueUnchecked(obj, val);
  }

  static void setLongNative(Field field, Object obj, long val) {
    VM_Field f = java.lang.reflect.JikesRVMSupport.getFieldOf(field);
    f.setLongValueUnchecked(obj, val);
  }
  
  static void setIntNative(Field field, Object obj, int val) {
    VM_Field f = java.lang.reflect.JikesRVMSupport.getFieldOf(field);
    f.setIntValueUnchecked(obj, val);
  }
  
  static void setShortNative(Field field, Object obj, short val) {
    VM_Field f = java.lang.reflect.JikesRVMSupport.getFieldOf(field);
    f.setShortValueUnchecked(obj, val);
  }

  static void setCharNative(Field field, Object obj, char val) {
    VM_Field f = java.lang.reflect.JikesRVMSupport.getFieldOf(field);
    f.setCharValueUnchecked(obj, val);
  }

  static void setByteNative(Field field, Object obj, byte val) {
    VM_Field f = java.lang.reflect.JikesRVMSupport.getFieldOf(field);
    f.setByteValueUnchecked(obj, val);
  }

  static void setBooleanNative(Field field, Object obj, boolean val) {
    VM_Field f = java.lang.reflect.JikesRVMSupport.getFieldOf(field);
    f.setBooleanValueUnchecked(obj, val);
  }

  static void setObjectNative(Field field, Object obj, Object val) {
    VM_Field f = java.lang.reflect.JikesRVMSupport.getFieldOf(field);
    f.setObjectValueUnchecked(obj, val);
  }
}
