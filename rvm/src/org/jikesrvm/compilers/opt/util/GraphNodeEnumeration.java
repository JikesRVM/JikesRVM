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
package org.jikesrvm.compilers.opt.util;

import java.util.Enumeration;


/**
 *  Generic interface for enumerations of graph nodes.  All graph
 * implementations should make sure that their enumerations of graph
 * nodes implement this interface, and all graph utilities that need
 * to enumerate nodes should use this interface.
 *
 * @see Graph
 * @see GraphNode
 */
public interface GraphNodeEnumeration extends Enumeration<GraphNode> {

  /**
   *  Return the next graph node in the enumeration.
   * @return the next graph node in the enumeration
   */
  GraphNode next();
}



