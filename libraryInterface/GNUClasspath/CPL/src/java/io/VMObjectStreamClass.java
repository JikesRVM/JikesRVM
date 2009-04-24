/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package java.io;

import java.lang.reflect.Field;

import org.jikesrvm.classloader.RVMField;
import org.jikesrvm.classloader.RVMType;

/**
 * java.io.ObjectStream helper implemented for Jikes RVM.
 */
final class VMObjectStreamClass {

  static boolean hasClassInitializer(Class<?> cls) {
    RVMType t = java.lang.JikesRVMSupport.getTypeForClass(cls);
    if (t.isClassType()) {
      return t.asClass().getClassInitializerMethod() != null;
    } else {
      return false;
    }
  }

  static void setDoubleNative(Field field, Object obj, double val) {
    RVMField f = java.lang.reflect.JikesRVMSupport.getFieldOf(field);
    f.setDoubleValueUnchecked(obj, val);
  }

  static void setFloatNative(Field field, Object obj, float val) {
    RVMField f = java.lang.reflect.JikesRVMSupport.getFieldOf(field);
    f.setFloatValueUnchecked(obj, val);
  }

  static void setLongNative(Field field, Object obj, long val) {
    RVMField f = java.lang.reflect.JikesRVMSupport.getFieldOf(field);
    f.setLongValueUnchecked(obj, val);
  }

  static void setIntNative(Field field, Object obj, int val) {
    RVMField f = java.lang.reflect.JikesRVMSupport.getFieldOf(field);
    f.setIntValueUnchecked(obj, val);
  }

  static void setShortNative(Field field, Object obj, short val) {
    RVMField f = java.lang.reflect.JikesRVMSupport.getFieldOf(field);
    f.setShortValueUnchecked(obj, val);
  }

  static void setCharNative(Field field, Object obj, char val) {
    RVMField f = java.lang.reflect.JikesRVMSupport.getFieldOf(field);
    f.setCharValueUnchecked(obj, val);
  }

  static void setByteNative(Field field, Object obj, byte val) {
    RVMField f = java.lang.reflect.JikesRVMSupport.getFieldOf(field);
    f.setByteValueUnchecked(obj, val);
  }

  static void setBooleanNative(Field field, Object obj, boolean val) {
    RVMField f = java.lang.reflect.JikesRVMSupport.getFieldOf(field);
    f.setBooleanValueUnchecked(obj, val);
  }

  static void setObjectNative(Field field, Object obj, Object val) {
    RVMField f = java.lang.reflect.JikesRVMSupport.getFieldOf(field);
    f.setObjectValueUnchecked(obj, val);
  }
}
