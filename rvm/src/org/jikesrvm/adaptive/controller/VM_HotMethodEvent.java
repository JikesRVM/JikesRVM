/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.adaptive.controller;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.VM_Method;
import org.jikesrvm.VM_CompiledMethod;
import org.jikesrvm.adaptive.recompilation.VM_CompilerDNA;
import org.jikesrvm.opt.VM_OptCompiledMethod;

/**
 * Abstract parent class for events from organizers to the controller 
 * used to communicate that a method should be considered as a candidate
 * for recompilation.
 *
 * @author Dave Grove 
 */
public abstract class VM_HotMethodEvent {

  /**
   * The compiled method associated querries.
   */
  private VM_CompiledMethod cm;
  public final int getCMID() { return cm.getId(); }
  public final VM_CompiledMethod getCompiledMethod() { return cm; }
  public final VM_Method getMethod() { return cm.getMethod();  }
  public final boolean isOptCompiled() {
    return cm.getCompilerType() == VM_CompiledMethod.OPT;
  }

  public final int getOptCompiledLevel() {
    if (!isOptCompiled()) return -1;
    return ((VM_OptCompiledMethod)cm).getOptLevel();
  }

  public final int getPrevCompilerConstant() {
    if (isOptCompiled()) {
      return VM_CompilerDNA.getCompilerConstant(getOptCompiledLevel());
    } else {
      return VM_CompilerDNA.BASELINE;
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
  VM_HotMethodEvent(VM_CompiledMethod _cm, double _numSamples) {
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
  VM_HotMethodEvent(VM_CompiledMethod _cm, int _numSamples) {
    this(_cm, (double)_numSamples);
  }

  public String toString() {
    return getMethod()+ " = " +getNumSamples();
  }
}
