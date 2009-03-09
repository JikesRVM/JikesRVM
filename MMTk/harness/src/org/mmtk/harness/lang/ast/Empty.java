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

import org.mmtk.harness.lang.Visitor;

/**
 * An empty statement. Useful in helping construct the parser.
 */
public class Empty extends AbstractAST implements Statement {

  public Empty() {
    super(0,0);
  }

  @Override
  public Object accept(Visitor v) {
    return v.visit(this);
  }
}
