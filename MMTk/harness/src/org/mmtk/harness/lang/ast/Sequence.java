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
package org.mmtk.harness.lang.ast;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.mmtk.harness.lang.Visitor;
import org.mmtk.harness.lang.parser.Token;

/**
 * Sequential execution of a sequence of statements
 */
public class Sequence extends AbstractAST implements Statement,Iterable<Statement> {

  /**
   * The list of statements
   */
  private final List<Statement> stmts = new ArrayList<Statement>();

  /**
   * Constructor.  Filters out empty statements.
   *
   * @param stmts
   */
  public Sequence(Token t, List<Statement> stmts) {
    super(t);
    for (Statement s : stmts) {
      if (!(s instanceof Empty))
        this.stmts.add(s);
    }
  }

  public Iterator<Statement> iterator() {
    return stmts.iterator();
  }

  @Override
  public Object accept(Visitor v) {
    return v.visit(this);
  }
}
