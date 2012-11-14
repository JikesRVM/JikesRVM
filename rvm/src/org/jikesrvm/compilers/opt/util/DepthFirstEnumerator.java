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
import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Set;


public final class DepthFirstEnumerator implements Enumeration<GraphNode> {
  private final Stack<GraphNode> stack;
  private final Set<GraphNode> visited;

  public DepthFirstEnumerator(GraphNode start) {
    stack = new Stack<GraphNode>();
    stack.push(start);
    visited = new HashSet<GraphNode>();
  }

  @Override
  public boolean hasMoreElements() {
    if (stack == null) {
      return false;
    }

    for (GraphNode node : stack) {
      if (notVisited(node)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public GraphNode nextElement() {
    if (stack == null) {
      throw new NoSuchElementException("DepthFirstEnumerator");
    }
    while (!stack.isEmpty()) {
      GraphNode node = stack.pop();
      if (notVisited(node)) {
        for (Enumeration<GraphNode> e = node.outNodes(); e.hasMoreElements();) {
          GraphNode n = e.nextElement();
          if (n != null) {
            stack.push(n);
          }
        }
        visited.add(node);
        return node;
      }
    }
    throw new NoSuchElementException("DepthFirstEnumerator");
  }

  private boolean notVisited(GraphNode node) {
    return !visited.contains(node);
  }

}
