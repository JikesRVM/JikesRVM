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
import org.mmtk.harness.lang.parser.Token;
import org.mmtk.harness.lang.type.Type;

public class AllocUserType extends AbstractAST implements Expression {
  /** Call site ID */
  private final int site;
  /** The type to allocate */
  private final Type type;

  /**
   * Allocate an object.
   */
  public AllocUserType(Token t, int site, Type type) {
    super(t);
    this.site = site;
    this.type = type;
  }

  public Object accept(Visitor v) {
    return v.visit(this);
  }

  public int getSite() { return site; }
  public Type getType() { return type; }
}
