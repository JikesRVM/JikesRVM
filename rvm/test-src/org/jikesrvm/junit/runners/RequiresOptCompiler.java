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
package org.jikesrvm.junit.runners;

/**
 * Indicates that a unit test or test method requires the presence of
 * the optimizing compiler.
 * <p>
 * Using an annotation allows testing of opt-compiler specific data
 * structures that don't actually require the presence of the optimizing
 * compiler: those data structures simply won't be marked with
 * this annotation.
 */
public final class RequiresOptCompiler {

  private RequiresOptCompiler() { }
}
