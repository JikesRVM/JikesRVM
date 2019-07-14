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
package org.jikesrvm.classlibrary;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;

/**
 * Provides functionality for the VM side of instrumentation of {@code java.lang.instrument} for OpenJDK.
 * GNU Classpath doesn't need VM support; its classloading implementation already handles instrumentation.
 * <p>
 * The class library side is handled by {@link JikesRVMSupport}, both for OpenJDK and for GNU Classpath.
 */
public class JavaLangInstrument {

  private static java.lang.instrument.Instrumentation instrumenter;

  private static Method transformMethod;

  public static void boot() throws Exception {
    instrumenter = java.lang.JikesRVMSupport.createInstrumentation();
    java.lang.JikesRVMSupport.initializeInstrumentation(instrumenter);
  }

  public static java.lang.instrument.Instrumentation getInstrumenter() {
    return instrumenter;
  }

  public static void setInstrumenter(Instrumentation newInstrumenter) {
    instrumenter = newInstrumenter;
  }

  public static Method getTransformMethod() {
    return transformMethod;
  }

  public static void setTransformMethod(Method newTransformMethod) {
    transformMethod = newTransformMethod;
  }

  public static boolean instrumentationReady() {
    return instrumenter != null && transformMethod != null;
  }

}
