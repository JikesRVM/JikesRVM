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
package org.vmmagic.pragma;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.ElementType;
import org.vmmagic.Pragma;

/**
 * This pragma indicates that a particular method should always be inlined
 * by the optimizing compiler.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Pragma
public @interface Inline {
  /**
   * Enumeration defining when to inline
   */
  public enum When {
    /**
     * Always inline, regardless of arguments
     */
    Always,
    /**
     * Inline when all the arguments are constants
     */
    AllArgumentsAreConstant,
    /**
     * Inline when the specified arguments are constants
     */
    ArgumentsAreConstant,
    /**
     * Inline when the VM is built without Assertions (VM.VerifyAssertions == false).
     * Note: It would be nicer to have the more general ExpressionIsTrue annotation,
     * but the argument expression to the annotation is restricted to be a fairly
     * trivial constant, and that isn't enough to handle how VM.VERIFY_ASSERTIONS
     * is defined in MMTk.
     */
    AssertionsDisabled
  }
  /**
   * When to inline, default When.Always
   */
  When value() default When.Always;
  /**
   * Arguments that must be constant to cause inlining. NB for static methods 0
   * is the first argument whilst for virtual methods 0 is this
   */
  int[] arguments() default {};
}
