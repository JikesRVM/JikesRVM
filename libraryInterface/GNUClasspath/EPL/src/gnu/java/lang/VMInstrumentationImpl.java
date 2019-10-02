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
package gnu.java.lang;

import java.lang.instrument.Instrumentation;
import java.lang.instrument.ClassDefinition;

import org.jikesrvm.classlibrary.JavaLangInstrumentSupport;


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
    return JavaLangInstrumentSupport.getObjectSize(objectToSize);
  }
}
