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
package org.jikesrvm.classloader;

import org.jikesrvm.VM;
import org.jikesrvm.compilers.common.VM_BootImageCompiler;
import org.jikesrvm.compilers.common.VM_CompiledMethod;
import org.jikesrvm.ArchitectureSpecific.VM_CodeArray;

/**
 * A method that is specialized across all reference types.
 *
 * In general as there may not be a 1-1 mapping between objects and the
 * specialized methods this class is responsible for performing the
 * mapping.
 *
 * Specialized methods must have a static 'invoke' method that matches
 * the given signature and return type.
 */
public abstract class VM_SpecializedMethod {

  /** This specialized method's id */
  protected final int id;

  /**
   * Constructor.
   */
  protected VM_SpecializedMethod(int id) {
    this.id = id;
  }

  /**
   * Return the specialized method for the given type.
   */
  public abstract VM_CodeArray specializeMethod(VM_Type type);

  /**
   * Return the method signature of the specialized method's invoke.
   */
  public abstract VM_TypeReference[] getSignature();

  /**
   * Return the return type of the specialized method's invoke.
   * @return
   */
  public abstract VM_TypeReference getReturnType();

  /**
   * Compile a specialized version of a template method. The template must be a method
   * that matches the signature of this specialized method class.
   *
   * The specialized types are the set of types to tell the compiler to use during specialized
   * compilation. This array must match the length of the array returned from getSignature.
   *
   * @param template The method to use as a template
   * @param specializedParams The known types of the parameters, possibly more refined than in the template
   * @return The compiled code array.
   */
  protected VM_CompiledMethod compileSpecializedMethod(VM_Method template, VM_TypeReference[] specializedParams) {
    VM_NormalMethod normalMethod = (VM_NormalMethod)template;
    /* Currently only support eagerly compiled methods */
    if (VM.VerifyAssertions) VM._assert(VM.writingBootImage);

    return VM_BootImageCompiler.compile(normalMethod, specializedParams);
  }
}
