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
    return new Field(f);
  }

  public static Method createMethod(RVMMethod m) {
    return new Method(m);
  }

  @SuppressWarnings("unchecked") // Can't type-check this without <T> type<T>, which breaks javac
  public static <T> Constructor<T> createConstructor(RVMMethod m) {
    return new Constructor<T>(m);
  }

  public static Object createVMConstructor(RVMMethod m) {
    throw new Error("Harmony doesn't provide the VMConstructor API");
  }

  public static RVMField getFieldOf(Field f) {
    return (RVMField)f.getVMMember();
  }

  public static RVMMethod getMethodOf(Method m) {
    return (RVMMethod)m.getVMMember();
  }

  public static RVMMethod getMethodOf(Constructor cons) {
    return (RVMMethod)cons.getVMMember();
  }
}
