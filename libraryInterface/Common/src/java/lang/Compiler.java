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
package java.lang;

import org.jikesrvm.classloader.RVMClass;
import org.jikesrvm.classloader.RVMMethod;

/**
 * Jikes RVM implementation of {@link java.lang.Compiler}
 */
public final class Compiler {
  private Compiler() {
  }
  public static boolean compileClass(Class<?> klass) {
    RVMClass rvmKlass = java.lang.JikesRVMSupport.getTypeForClass(klass).asClass();
    if (rvmKlass.isResolved()) {
      RVMMethod[] methods = rvmKlass.getDeclaredMethods();
      for (RVMMethod method : methods) {
        if (!method.isAbstract() && !method.isNative()) {
          method.compile();
        }
      }
      return true;
    } else {
      return false;
    }
  }
  public static boolean compileClasses(String klasses) {
    try {
      return compileClass(Class.forName(klasses));
    } catch (ClassNotFoundException e) {
      return false;
    }
  }
  public static Object command(Object arg) {
    return null;
  }
  public static void enable() {
  }
  public static void disable() {
  }
}
