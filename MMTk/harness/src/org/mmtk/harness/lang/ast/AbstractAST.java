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

import org.mmtk.harness.lang.Visitor;
import org.mmtk.harness.lang.parser.Source;
import org.mmtk.harness.lang.parser.Token;

/**
 * Abstract parent of all the components of an AST
 */
public class AbstractAST implements AST {

  /*
   * Track the current source file - assumes only one source file
   * is being parsed at a time.  If this stops being true, we'll need
   * to introduce an AST factory, or add the Source to each
   * AST constructor.
   */

  /** The source file currently being parsed */
  private static Source currentSource = null;

  /** Set the current source file */
  public static void setCurrentSource(Source source) {
    currentSource = source;
  }

  /** Clear the current source file */
  public static void clearCurrentSource() {
    currentSource = null;
  }

  /** The source file */
  private final Source source = currentSource;

  /** Source code token corresponding to this syntax element */
  private final Token t;

  /**
   * Create an AST for an actual source element
   *
   * @param t Source token
   */
  public AbstractAST(Token t) {
    this.t = t;
  }

  /**
   * Constructor only for AST elements that don't have a direct correspondance
   * to the source.
   * @param line
   * @param column
   */
  protected AbstractAST(int line, int column) {
    Token tok = new Token();
    tok.beginLine = line;
    tok.beginColumn = column;
    this.t = tok;
  }

  /** @return The source token */
  public Token getToken() {
    return t;
  }

  /** @return The source line */
  @Override
  public int getLine() {
    return t.beginLine;
  }

  /** @return The source column */
  @Override
  public int getColumn() {
    return t.beginColumn;
  }

  /** @see org.mmtk.harness.lang.ast.AST#sourceLocation(java.lang.String) */
  @Override
  public String sourceLocation(String prefix) {
    if (source == null) {
      return prefix+"<no source available>";
    }
    return prefix+source.getLine(getLine())+"\n"+
    spaces(prefix.length())+spaces(getColumn()-1)+"^";
  }

  private static String spaces(int n) {
    StringBuilder str = new StringBuilder(n);
    for (int i=0; i < n; i++) {
      str.append(' ');
    }
    return str.toString();
  }

  @Override
  public Object accept(Visitor v) {
    throw new UnsupportedOperationException();
  }
}
