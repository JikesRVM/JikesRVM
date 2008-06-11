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

import org.jikesrvm.classloader.*;

/**
 * Library support interface of Jikes RVM
 */
public class JikesRVMSupport {

  public static Field createField(VM_Field f) {
    return new Field(new VMField(f));
  }

  public static Method createMethod(VM_Method m) {
    return new Method(new VMMethod(m));
  }

  @SuppressWarnings("unchecked") // Can't type-check this without <T> type<T>, which breaks javac
  public static <T> Constructor<T> createConstructor(VM_Method m) {
    return new Constructor(new VMConstructor(m));
  }

  public static VMConstructor createVMConstructor(VM_Method m) {
    return new VMConstructor(m);
  }

  public static VM_Field getFieldOf(Field f) {
    return f.f.field;
  }

  public static VM_Method getMethodOf(Method m) {
    return m.m.method;
  }

  public static VM_Method getMethodOf(Constructor<?> cons) {
    return cons.cons.constructor;
  }

  public static VM_Field getFieldOf(VMField f) {
    return f.field;
  }

  public static VM_Method getMethodOf(VMMethod m) {
    return m.method;
  }

  public static VM_Method getMethodOf(VMConstructor cons) {
    return cons.constructor;
  }
}
