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

import org.mmtk.harness.lang.parser.Source;
import org.mmtk.harness.lang.parser.Token;

/**
 * Abstract parent of all the components of an AST
 */
public abstract class AbstractAST implements AST {

  /*
   * Track the current source file - assumes only one source file
   * is being parsed at a time.  If this stops being true, we'll need
   * to introduce an AST factory, or add the Source to each
   * AST constructor.
   */

  /* The source file currently being parsed */
  private static Source currentSource = null;

  /* Set the current source file */
  public static void setCurrentSource(Source source) {
    currentSource = source;
  }

  /* Clear the current source file */
  public static void clearCurrentSource() {
    currentSource = null;
  }

  /* The source file */
  private Source source = currentSource;

  /** Source code line corresponding to this syntax element */
  private final int line;
  /** Source code column corresponding to this syntax element */
  private final int column;

  protected AbstractAST(Token t) {
    this(t.beginLine, t.beginColumn);
  }

  protected AbstractAST(int line, int column) {
    this.line = line;
    this.column = column;
  }

  @Override
  public int getLine() {
    return line;
  }

  @Override
  public int getColumn() {
    return column;
  }

  @Override
  public String sourceLocation(String prefix) {
    if (source == null) {
      return prefix+"<no source available>";
    } else {
      return prefix+source.getLine(line)+"\n"+
      spaces(prefix.length())+spaces(column-1)+"^";
    }
  }

  private String spaces(int n) {
    StringBuilder str = new StringBuilder(n);
    for (int i=0; i < n; i++) {
      str.append(' ');
    }
    return str.toString();
  }
}
