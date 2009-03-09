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

public class Alloc extends AbstractAST implements Expression {
  /** Call site ID */
  private final int site;
  /** Number of reference fields */
  private final Expression refCount;
  /** Number of data fields */
  private final Expression dataCount;
  /** Double align the object? */
  private final Expression doubleAlign;

  /**
   * Allocate an object.
   */
  public Alloc(Token t, int site, Expression refCount, Expression dataCount, Expression doubleAlign) {
    super(t);
    this.site = site;
    this.refCount = refCount;
    this.dataCount = dataCount;
    this.doubleAlign = doubleAlign;
  }

  public Object accept(Visitor v) {
    return v.visit(this);
  }

  public int getSite() { return site; }
  public Expression getRefCount() { return refCount; }
  public Expression getDataCount() { return dataCount; }
  public Expression getDoubleAlign() { return doubleAlign; }
}
