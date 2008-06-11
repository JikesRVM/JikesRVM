/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package java.lang.reflect;

import java.lang.annotation.Annotation;

import org.jikesrvm.classloader.VM_Class;
import org.jikesrvm.classloader.VM_Field;

/**
 * Implementation of java.lang.reflect.Field for JikesRVM.
 *
 * By convention, order methods in the same order
 * as they appear in the method summary list of Sun's 1.4 Javadoc API.
 */
public final class VMField {

  final VM_Field field;
  Field f;

  // Prevent this class from being instantiated.
  @SuppressWarnings("unused")
  private VMField() {
    field = null;
  }

  // For use by JikesRVMSupport
  VMField(VM_Field f) {
    field = f;
  }

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

  Object get(Object object) throws IllegalAccessException, IllegalArgumentException {
    return VMCommonLibrarySupport.get(object, field, f, VM_Class.getClassFromStackFrame(2));
  }

  boolean getBoolean(Object object) throws IllegalAccessException, IllegalArgumentException {
    return VMCommonLibrarySupport.getBoolean(object, field, f, VM_Class.getClassFromStackFrame(2));
  }

  byte getByte(Object object) throws IllegalAccessException, IllegalArgumentException {
    return VMCommonLibrarySupport.getByte(object, field, f, VM_Class.getClassFromStackFrame(2));
  }

  char getChar(Object object) throws IllegalAccessException, IllegalArgumentException {
    return VMCommonLibrarySupport.getChar(object, field, f, VM_Class.getClassFromStackFrame(2));
  }

  double getDouble(Object object) throws IllegalAccessException, IllegalArgumentException {
    return VMCommonLibrarySupport.getDouble(object, field, f, VM_Class.getClassFromStackFrame(2));
  }

  float getFloat(Object object) throws IllegalAccessException, IllegalArgumentException {
    return VMCommonLibrarySupport.getFloat(object, field, f, VM_Class.getClassFromStackFrame(2));
  }

  int getInt(Object object) throws IllegalAccessException, IllegalArgumentException {
    return VMCommonLibrarySupport.getInt(object, field, f, VM_Class.getClassFromStackFrame(2));
  }

  long getLong(Object object) throws IllegalAccessException, IllegalArgumentException {
    return VMCommonLibrarySupport.getLong(object, field, f, VM_Class.getClassFromStackFrame(2));
  }

  public short getShort(Object object) throws IllegalAccessException, IllegalArgumentException {
    return VMCommonLibrarySupport.getShort(object, field, f, VM_Class.getClassFromStackFrame(2));
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

  void set(Object object, Object value)
    throws IllegalAccessException, IllegalArgumentException     {
    VMCommonLibrarySupport.set(object, value, field, f, VM_Class.getClassFromStackFrame(2));
  }

  void setBoolean(Object object, boolean value)
    throws IllegalAccessException, IllegalArgumentException    {
    VMCommonLibrarySupport.setBoolean(object, value, field, f, VM_Class.getClassFromStackFrame(2));
  }

   void setByte(Object object, byte value)
     throws IllegalAccessException, IllegalArgumentException    {
     VMCommonLibrarySupport.setByte(object, value, field, f, VM_Class.getClassFromStackFrame(2));
  }

  void setChar(Object object, char value)
    throws IllegalAccessException, IllegalArgumentException    {
    VMCommonLibrarySupport.setChar(object, value, field, f, VM_Class.getClassFromStackFrame(2));
  }

  void setDouble(Object object, double value)
    throws IllegalAccessException, IllegalArgumentException    {
    VMCommonLibrarySupport.setDouble(object, value, field, f, VM_Class.getClassFromStackFrame(2));
  }

  void setFloat(Object object, float value)
    throws IllegalAccessException, IllegalArgumentException    {
    VMCommonLibrarySupport.setFloat(object, value, field, f, VM_Class.getClassFromStackFrame(2));
  }

  void setInt(Object object, int value)
    throws IllegalAccessException, IllegalArgumentException    {
    VMCommonLibrarySupport.setInt(object, value, field, f, VM_Class.getClassFromStackFrame(2));
  }

  void setLong(Object object, long value)
    throws IllegalAccessException, IllegalArgumentException    {
    VMCommonLibrarySupport.setLong(object, value, field, f, VM_Class.getClassFromStackFrame(2));
  }

  void setShort(Object object, short value)
    throws IllegalAccessException, IllegalArgumentException   {
    VMCommonLibrarySupport.setShort(object, value, field, f, VM_Class.getClassFromStackFrame(2));
  }

  // AnnotatedElement interface

  Annotation[] getDeclaredAnnotations() {
    return field.getDeclaredAnnotations();
  }

  <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
    return field.getAnnotation(annotationClass);
  }

  String getSignature() {
    return field.getSignature().toString();
  }
}
