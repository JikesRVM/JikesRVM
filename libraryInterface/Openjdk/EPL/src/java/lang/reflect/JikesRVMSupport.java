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
import org.jikesrvm.VM;

import org.jikesrvm.classloader.*;

/**
 * Library support interface of Jikes RVM
 */
public class JikesRVMSupport {

  public static Field createField(RVMField f) {
    VM.sysWriteln("CreateFile is called");
    throw new Error("Openjdk: Create Filed");
    //      return new Field(f);
  }

  public static Method createMethod(RVMMethod m) {
    VM.sysWriteln("CreateMethod is called");
    throw new Error("Openjdk createmethod");
    //      return new Method(m);
  }

  @SuppressWarnings("unchecked") // Can't type-check this without <T> type<T>, which breaks javac
  public static <T> Constructor<T> createConstructor(RVMMethod m) {
    VM.sysWriteln("CreateConstructor is called");
    throw new Error("Harmony doesn't provide the CreateConstructor API");
    //      return new Constructor<T>(m);
  }

  public static Object createVMConstructor(RVMMethod m) {
    VM.sysWriteln("CreateVMConstructor is called");
    throw new Error("Harmony doesn't provide the VMConstructor API");
  }

  public static RVMField getFieldOf(Field f) {
    VM.sysWriteln("getFieldOf is called");
    //     return (RVMField)f.getVMMember();
    throw new Error("Harmony doesn't provide the VMConstructor API");

  }

  public static RVMMethod getMethodOf(Method m) {
    VM.sysWriteln("getMethodOf is called");
    //      return (RVMMethod)m.getVMMember();
    throw new Error("Harmony doesn't provide the VMConstructor API");
  }

  public static RVMMethod getMethodOf(Constructor cons) {
    VM.sysWriteln("getMethodOf Constructor is called");
    //      return (RVMMethod)cons.getVMMember();
    throw new Error("Harmony doesn't provide the VMConstructor API");
  }
}
