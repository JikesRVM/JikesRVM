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

import java.util.ArrayList;
import java.util.List;

/**
 * Sequential execution of a sequence of statements
 */
public class Sequence implements Statement {

  /**
   * The list of statements
   */
  private final List<Statement> stmts = new ArrayList<Statement>();

  /**
   * Constructor.  Filters out empty statements.
   *
   * @param stmts
   */
  public Sequence(List<Statement> stmts) {
    for (Statement s : stmts) {
      if (!(s instanceof Empty))
        this.stmts.add(s);
    }
  }

  /**
   * Execute the statements in sequence
   */
  public void exec(Env env) throws ReturnException {
    for (Statement s : stmts) {
      s.exec(env);
      env.gcSafePoint();
    }
  }
}
