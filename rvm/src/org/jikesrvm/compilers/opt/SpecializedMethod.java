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
package org.jikesrvm.compilers.opt;

import org.jikesrvm.classloader.VM_NormalMethod;
import org.jikesrvm.compilers.common.VM_CompiledMethod;

/**
 * This is the top-level class to support specialized versions of Java methods
 */
public class SpecializedMethod {
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
  SpecializationContext context;

  /**
   * constructor for OPT compiler.
   */
  SpecializedMethod(VM_NormalMethod source, SpecializationContext context) {
    this.method = source;
    this.context = context;
    this.smid = SpecializedMethodPool.createSpecializedMethodID();
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

  public SpecializationContext getSpecializationContext() {
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



