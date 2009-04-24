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
package org.jikesrvm.compilers.opt.specialization;

import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.compilers.common.CompiledMethod;

/**
 * This is the top-level class to support specialized versions of Java methods
 */
public final class SpecializedMethod {
  /**
   * The method that was specialized
   */
  final NormalMethod method;

  /**
   * Corresponding compiled method
   */
  CompiledMethod compiledMethod;

  /**
   * Specialized Method index into the SpecializedMethods table
   */
  final int smid;

  /**
   * Encodes the rules for generating the specialized code.
   */
  final SpecializationContext context;

  /**
   * constructor for OPT compiler.
   */
  SpecializedMethod(NormalMethod source, SpecializationContext context) {
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

  public NormalMethod getMethod() {
    return method;
  }

  public SpecializationContext getSpecializationContext() {
    return context;
  }

  public CompiledMethod getCompiledMethod() {
    return compiledMethod;
  }

  public void setCompiledMethod(CompiledMethod cm) {
    compiledMethod = cm;
  }

  public int getSpecializedMethodIndex() {
    return smid;
  }

  public String toString() {
    return "Specialized " + method + "  (Context: " + context + ")";
  }
}
