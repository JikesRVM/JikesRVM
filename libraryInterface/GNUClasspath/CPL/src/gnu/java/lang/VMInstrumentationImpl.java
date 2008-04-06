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
package gnu.java.lang;

import java.lang.instrument.Instrumentation;
import java.lang.instrument.ClassDefinition;

import org.jikesrvm.classloader.VM_Type;
import org.jikesrvm.classloader.VM_Class;
import org.jikesrvm.classloader.VM_Array;


/**
 * Jikes RVM implementation of VMInstrumentationImpl
 */
final class VMInstrumentationImpl {
  static boolean isRedefineClassesSupported() {
    return false;
  }

  static void redefineClasses(Instrumentation inst,
    ClassDefinition[] definitions) {
    throw new UnsupportedOperationException();
  }

  static Class<?>[] getAllLoadedClasses() {
    return java.lang.JikesRVMSupport.getAllLoadedClasses();
  }

  static Class<?>[] getInitiatedClasses(ClassLoader loader) {
    return java.lang.JikesRVMSupport.getInitiatedClasses(loader);
  }

  static long getObjectSize(Object objectToSize) {
    Class<?> cl = objectToSize.getClass();
    VM_Type vmType = java.lang.JikesRVMSupport.getTypeForClass(cl);
    if (cl.isArray()) {
      VM_Array vmArray = (VM_Array)vmType;
      int nelements = java.lang.reflect.Array.getLength(objectToSize);
      return vmArray.getInstanceSize(nelements);
    } else {
      VM_Class vmClass = (VM_Class)vmType;
      return vmClass.getInstanceSize();
    }
  }
}
