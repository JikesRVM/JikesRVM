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

import org.jikesrvm.classloader.*;

/**
 * Library support interface of Jikes RVM
 */
public class JikesRVMSupport {

  public static Field createField(RVMField f) {
    return new Field(new VMField(f));
  }

  public static Method createMethod(RVMMethod m) {
    return new Method(new VMMethod(m));
  }

  //Can't type-check this without <T> type<T>, which breaks javac
  @SuppressWarnings("unchecked")
  public static <T> Constructor<T> createConstructor(RVMMethod m) {
    return new Constructor(new VMConstructor(m));
  }

  public static VMConstructor createVMConstructor(RVMMethod m) {
    return new VMConstructor(m);
  }

  public static RVMField getFieldOf(Field f) {
    return f.f.field;
  }

  public static RVMMethod getMethodOf(Method m) {
    return m.m.method;
  }

  public static RVMMethod getMethodOf(Constructor<?> cons) {
    return cons.cons.constructor;
  }

  public static RVMField getFieldOf(VMField f) {
    return f.field;
  }

  public static RVMMethod getMethodOf(VMMethod m) {
    return m.method;
  }

  public static RVMMethod getMethodOf(VMConstructor cons) {
    return cons.constructor;
  }
}
