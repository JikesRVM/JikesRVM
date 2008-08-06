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
package org.mmtk.harness.lang;

/**
 * An expectation for an exception (e.g. OutOfMemory).
 */
public class Expect implements Statement {
  Class<?> expectedThrowable;

  /**
   * Constructor
   */
  public Expect(String name) {
    try {
      expectedThrowable = Class.forName("org.mmtk.harness.Mutator$" + name);
    } catch (ClassNotFoundException cnfe) {
      throw new RuntimeException(cnfe);
    }
  }

  /**
   * Trigger a garbage collection
   */
  public void exec(Env env) throws ReturnException {
    env.setExpectedThrowable(expectedThrowable);
  }
}
