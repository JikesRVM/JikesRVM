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
package org.jikesrvm.classloader;

import org.jikesrvm.VM;
import org.jikesrvm.compilers.common.BootImageCompiler;
import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.ArchitectureSpecific.CodeArray;

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
public abstract class SpecializedMethod {

  /** This specialized method's id */
  protected final int id;

  /**
   * Constructor.
   */
  protected SpecializedMethod(int id) {
    this.id = id;
  }

  /**
   * @return the specialized method for the given type.
   */
  public abstract CodeArray specializeMethod(RVMType type);

  /**
   * @return the method signature of the specialized method's invoke.
   */
  public abstract TypeReference[] getSignature();

  /**
   * @return the return type of the specialized method's invoke
   */
  public abstract TypeReference getReturnType();

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
  protected CompiledMethod compileSpecializedMethod(RVMMethod template, TypeReference[] specializedParams) {
    NormalMethod normalMethod = (NormalMethod)template;
    /* Currently only support eagerly compiled methods */
    if (VM.VerifyAssertions) VM._assert(VM.writingBootImage);

    return BootImageCompiler.compile(normalMethod, specializedParams);
  }
}
