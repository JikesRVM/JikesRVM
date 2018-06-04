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
package java.lang.reflect;

import java.lang.annotation.Annotation;

import org.jikesrvm.VM;
import org.jikesrvm.classlibrary.JavaLangReflectSupport;
import org.jikesrvm.classloader.Atom;
import org.jikesrvm.classloader.RVMClass;
import org.jikesrvm.classloader.RVMField;

/**
 * Implementation of java.lang.reflect.Field for JikesRVM.<p>
 *
 * By convention, order methods in the same order
 * as they appear in the method summary list of Sun's 1.4 Javadoc API.
 */
public final class VMField {

  final RVMField field;

  // For use by JikesRVMSupport
  VMField(RVMField f) {
    field = f;
  }

  @Override
  public boolean equals(Object object) {
    if (object instanceof Field) {
      return field == ((Field)object).f.field;
    } else {
      return false;
    }
  }

  Class<?> getDeclaringClass() {
    return field.getDeclaringClass().getClassForType();
  }

  Object get(Field f, Object object) throws IllegalAccessException, IllegalArgumentException {
    if (VM.ExtremeAssertions) {
      boolean invalidGet = JikesRVMSupport.getFieldOf(f).getType().isMagicType();
      if (invalidGet) {
        String msg = "Attempted to access reflection field for " + f +
            " which has a magic type and thus doesn't support reflection!";
        VM._assert(VM.NOT_REACHED, msg);
      }
    }

    return JavaLangReflectSupport.get(object, field, f, RVMClass.getClassFromStackFrame(2));
  }

  boolean getBoolean(Field f,Object object) throws IllegalAccessException, IllegalArgumentException {
    return JavaLangReflectSupport.getBoolean(object, field, f, RVMClass.getClassFromStackFrame(2));
  }

  byte getByte(Field f, Object object) throws IllegalAccessException, IllegalArgumentException {
    return JavaLangReflectSupport.getByte(object, field, f, RVMClass.getClassFromStackFrame(2));
  }

  char getChar(Field f, Object object) throws IllegalAccessException, IllegalArgumentException {
    return JavaLangReflectSupport.getChar(object, field, f, RVMClass.getClassFromStackFrame(2));
  }

  double getDouble(Field f, Object object) throws IllegalAccessException, IllegalArgumentException {
    return JavaLangReflectSupport.getDouble(object, field, f, RVMClass.getClassFromStackFrame(2));
  }

  float getFloat(Field f, Object object) throws IllegalAccessException, IllegalArgumentException {
    return JavaLangReflectSupport.getFloat(object, field, f, RVMClass.getClassFromStackFrame(2));
  }

  int getInt(Field f, Object object) throws IllegalAccessException, IllegalArgumentException {
    return JavaLangReflectSupport.getInt(object, field, f, RVMClass.getClassFromStackFrame(2));
  }

  long getLong(Field f, Object object) throws IllegalAccessException, IllegalArgumentException {
    return JavaLangReflectSupport.getLong(object, field, f, RVMClass.getClassFromStackFrame(2));
  }

  public short getShort(Field f, Object object) throws IllegalAccessException, IllegalArgumentException {
    return JavaLangReflectSupport.getShort(object, field, f, RVMClass.getClassFromStackFrame(2));
  }

  int getModifiersInternal() {
    return field.getModifiers();
  }

  String getName() {
    return field.getName().toString();
  }

  Class<?> getType() {
    return field.getType().resolve().getClassForType();
  }

  void set(Field f, Object object, Object value)
    throws IllegalAccessException, IllegalArgumentException     {
    if (VM.ExtremeAssertions) {
      boolean invalidSet = JikesRVMSupport.getFieldOf(f).getType().isMagicType();
      if (invalidSet) {
        String msg = "Attempted to access reflection field for " + f +
            " which has a magic type and thus doesn't support reflection!";
        VM._assert(VM.NOT_REACHED, msg);
      }
    }

    JavaLangReflectSupport.set(object, value, field, f, RVMClass.getClassFromStackFrame(2));
  }

  void setBoolean(Field f, Object object, boolean value)
    throws IllegalAccessException, IllegalArgumentException    {
    JavaLangReflectSupport.setBoolean(object, value, field, f, RVMClass.getClassFromStackFrame(2));
  }

   void setByte(Field f, Object object, byte value)
     throws IllegalAccessException, IllegalArgumentException    {
     JavaLangReflectSupport.setByte(object, value, field, f, RVMClass.getClassFromStackFrame(2));
  }

  void setChar(Field f, Object object, char value)
    throws IllegalAccessException, IllegalArgumentException    {
    JavaLangReflectSupport.setChar(object, value, field, f, RVMClass.getClassFromStackFrame(2));
  }

  void setDouble(Field f, Object object, double value)
    throws IllegalAccessException, IllegalArgumentException    {
    JavaLangReflectSupport.setDouble(object, value, field, f, RVMClass.getClassFromStackFrame(2));
  }

  void setFloat(Field f, Object object, float value)
    throws IllegalAccessException, IllegalArgumentException    {
    JavaLangReflectSupport.setFloat(object, value, field, f, RVMClass.getClassFromStackFrame(2));
  }

  void setInt(Field f, Object object, int value)
    throws IllegalAccessException, IllegalArgumentException    {
    JavaLangReflectSupport.setInt(object, value, field, f, RVMClass.getClassFromStackFrame(2));
  }

  void setLong(Field f, Object object, long value)
    throws IllegalAccessException, IllegalArgumentException    {
    JavaLangReflectSupport.setLong(object, value, field, f, RVMClass.getClassFromStackFrame(2));
  }

  void setShort(Field f, Object object, short value)
    throws IllegalAccessException, IllegalArgumentException   {
    JavaLangReflectSupport.setShort(object, value, field, f, RVMClass.getClassFromStackFrame(2));
  }

  // AnnotatedElement interface

  Annotation[] getDeclaredAnnotations() {
    return field.getDeclaredAnnotations();
  }

  <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
    return field.getAnnotation(annotationClass);
  }

  String getSignature() {
    Atom signature = field.getSignature();
    return (signature == null) ? null : signature.toString();
  }
}
