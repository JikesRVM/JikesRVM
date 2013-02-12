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
package org.mmtk.harness.lang.ast;

import java.util.List;

import org.mmtk.harness.lang.Visitor;
import org.mmtk.harness.lang.parser.Token;
import org.mmtk.harness.lang.type.UserType;

/**
 * AST node for the general alloc(refs,nonrefs,align) allocation method
 */
public class Alloc extends AbstractAST implements Expression {
  /** Call site ID */
  private final int site;

  /** arguments - there are a couple of variants of 'alloc' */
  private final List<Expression> args;

  /**
   * During semantic analysis, we decide what kind of allocation request this is,
   * and set this flag to show which. {@code null} indicates that analysis hasn't yet happened.
   */
  private Boolean typedAlloc = null;

  /**
   * The result type - only available if {@code typedAlloc == true}
   */
  private UserType type = null;

  /**
   * Allocate an object.
   *
   * @param t The parser token for this node
   * @param site A unique site ID
   * @param args
   */
  public Alloc(Token t, int site, List<Expression> args) {
    super(t);
    this.site = site;
    this.args = args;
  }

  @Override
  public Object accept(Visitor v) {
    return v.visit(this);
  }

  public void setTyped(boolean isTyped) {
    assert typedAlloc == null;
    typedAlloc = isTyped;
  }

  public boolean isTyped() {
    assert typedAlloc != null : "Semantic analysis has not yet been run!";
    return typedAlloc;
  }

  public void setType(UserType type) {
    assert typedAlloc;
    this.type = type;
  }

  /**
   * @return The allocation site number
   */
  public int getSite() {
    return site;
  }

  /** @return refCount */
  public Expression getArg(int i) {
    return args.get(i);
  }

  /** Get # args */
  public int numArgs() {
    return args.size();
  }

  public List<Expression> getArgs() {
    return args;
  }

  public UserType getType() {
    assert typedAlloc;
    return type;
  }
}
