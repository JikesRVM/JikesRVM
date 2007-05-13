/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.compilers.opt;

import org.jikesrvm.classloader.VM_NormalMethod;
import org.jikesrvm.compilers.common.VM_CompiledMethod;

/**
 * This is the top-level class to support specialized versions of Java methods
 */
public class OPT_SpecializedMethod {
  /**
   * The method that was specialized
   */
  VM_NormalMethod method;

  /**
   * Corresponding compiled method
   */
  VM_CompiledMethod compiledMethod;

  /**
   * Specialized Method index into the SpecializedMethods table
   */
  int smid;

  /**
   * Encodes the rules for generating the specialized code.
   */
  OPT_SpecializationContext context;

  /**
   * constructor for OPT compiler.
   */
  OPT_SpecializedMethod(VM_NormalMethod source, OPT_SpecializationContext context) {
    this.method = source;
    this.context = context;
    this.smid = OPT_SpecializedMethodPool.createSpecializedMethodID();
  }

  /**
   * generate the specialized code for this method
   */
  void compile() {
    compiledMethod = context.specialCompile(method);
  }

  public VM_NormalMethod getMethod() {
    return method;
  }

  public OPT_SpecializationContext getSpecializationContext() {
    return context;
  }

  public VM_CompiledMethod getCompiledMethod() {
    return compiledMethod;
  }

  public void setCompiledMethod(VM_CompiledMethod cm) {
    compiledMethod = cm;
  }

  public int getSpecializedMethodIndex() {
    return smid;
  }

  public String toString() {
    return "Specialized " + method + "  (Context: " + context + ")";
  }
}



