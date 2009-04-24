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
package org.mmtk.harness.lang.runtime;

import java.util.ArrayList;

import org.mmtk.harness.lang.parser.Token;

/**
 * Information about allocation sites in a script.  Used when debugging to
 * identify objects.
 */
public class AllocationSite {

  /**
   * The global collection of allocation sites.
   */
  private static final ArrayList<AllocationSite> sites = new ArrayList<AllocationSite>();

  /**
   * Retrieve an allocation site by ID.
   * @param id
   * @return
   */
  public static AllocationSite getSite(int id) {
    return sites.get(id);
  }

  private final int id;
  private final int column;
  private final int line;

  /**
   * Create an allocation site for a given source code line/column.
   * @param line
   * @param column
   */
  public AllocationSite(int line, int column) {
    this.id = sites.size();
    sites.add(this);
    this.line = line;
    this.column = column;
  }

  /**
   * An anonymous allocation site
   */
  public AllocationSite() {
    this(0,0);
  }

  /**
   * An allocation site for a given script token.
   * @param token
   */
  public AllocationSite(Token token) {
    this(token.beginLine,token.beginColumn);
  }

  public String toString() {
    return String.format("%d:%d", line, column);
  }

  public int getId() {
    return id;
  }

  public int getColumn() {
    return column;
  }

  public int getLine() {
    return line;
  }
}
