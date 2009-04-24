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
package org.jikesrvm.adaptive.controller;

import org.jikesrvm.VM;
import org.jikesrvm.adaptive.recompilation.CompilerDNA;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.compilers.opt.runtimesupport.OptCompiledMethod;

/**
 * Abstract parent class for events from organizers to the controller
 * used to communicate that a method should be considered as a candidate
 * for recompilation.
 */
public abstract class HotMethodEvent {

  /**
   * The compiled method associated querries.
   */
  private CompiledMethod cm;

  public final int getCMID() { return cm.getId(); }

  public final CompiledMethod getCompiledMethod() { return cm; }

  public final RVMMethod getMethod() { return cm.getMethod(); }

  public final boolean isOptCompiled() {
    return cm.getCompilerType() == CompiledMethod.OPT;
  }

  public final int getOptCompiledLevel() {
    if (!isOptCompiled()) return -1;
    return ((OptCompiledMethod) cm).getOptLevel();
  }

  public final int getPrevCompilerConstant() {
    if (isOptCompiled()) {
      return CompilerDNA.getCompilerConstant(getOptCompiledLevel());
    } else {
      return CompilerDNA.BASELINE;
    }
  }

  /**
   * Number of samples attributed to this method.
   */
  private double numSamples;

  public final double getNumSamples() { return numSamples; }

  /**
   * @param _cm the compiled method
   * @param _numSamples the number of samples attributed to the method
   */
  HotMethodEvent(CompiledMethod _cm, double _numSamples) {
    if (VM.VerifyAssertions) {
      VM._assert(_cm != null, "Don't create me for null compiled method!");
      VM._assert(_numSamples >= 0.0, "Invalid numSamples value");
    }
    cm = _cm;
    numSamples = _numSamples;
  }

  /**
   * @param _cm the compiled method
   * @param _numSamples the number of samples attributed to the method
   */
  HotMethodEvent(CompiledMethod _cm, int _numSamples) {
    this(_cm, (double) _numSamples);
  }

  public String toString() {
    return getMethod() + " = " + getNumSamples();
  }
}
