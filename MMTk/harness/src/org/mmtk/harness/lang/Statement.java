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
 * Interface representing any statement.
 */
public interface Statement {

  /**
   * Execute the statement, possibly with side-effects on the environment.
   * @throws ReturnException as the result of a 'return' statement
   */
  void exec(Env env) throws ReturnException;
}
