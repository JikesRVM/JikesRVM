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
import org.mmtk.harness.lang.parser.Token;

/**
 * Abstract interface to all the components of an AST
 */
public interface AST {

  /**
   * Accept visitors
   * @param v The visitor
   * @return A visitor-specific value
   */
  Object accept(Visitor v);

  /**
   * @return The source line for this node
   */
  int getLine();

  /**
   * @return The source column for this node
   */
  int getColumn();

  /**
   * @return The source token for this node
   */
  Token getToken();

  /**
   * @param prefix Prefix for the string (formatting)
   * @return A string locating this node in the source file
   */
  String sourceLocation(String prefix);
}
