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

import java.util.Iterator;
import java.util.List;

/**
 * A conditional statement whereby one of two statements (either possibly an empty sequence)
 * is executed depending on the value of a condition expression.
 */
public class IfStatement implements Statement {
  /** The expressions to be evaluated */
  private final List<Expression> conds;
  /** The statement to execute if the corresponding condition is true */
  private final List<Statement> stmts;

  /**
   * Create a new conditional statement.
   */
  public IfStatement(List<Expression> conds, List<Statement> stmts) {
    this.conds = conds;
    this.stmts = stmts;
    assert conds.size() == stmts.size() || conds.size() +1 == stmts.size() :
      "mismatch between conditions and statements for a conditional";
  }

  /**
   * Evaluate the conditions in sequence until one of them evaluates
   * to true, then execute the corresponding statement.  If all evaluate
   * to false, and there is an 'extra' statement, execute it.
   */
  public void exec(Env env) throws ReturnException {
    Iterator<Statement> stmtIter = stmts.iterator();
    for (Expression cond : conds) {
      Value condVal = cond.eval(env);
      env.check(condVal.type() == Type.BOOLEAN || condVal.type() == Type.OBJECT, "Condition must be object or boolean");
      env.gcSafePoint();

      assert stmtIter.hasNext() : "too few statements in a conditional";
      Statement stmt = stmtIter.next();
      if (condVal.getBoolValue()) {
        stmt.exec(env);
        return;
      }
    }
    if (stmtIter.hasNext()) {
      Statement stmt = stmtIter.next();
      stmt.exec(env);
    }
  }
}
