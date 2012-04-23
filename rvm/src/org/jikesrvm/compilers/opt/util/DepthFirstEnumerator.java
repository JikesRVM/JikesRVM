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
import java.util.NoSuchElementException;


public final class DepthFirstEnumerator implements Enumeration<GraphNode> {
  Stack<GraphNode> stack;
  int mark;

  public DepthFirstEnumerator(GraphNode start, int markNumber) {
    stack = new Stack<GraphNode>();
    stack.push(start);
    mark = markNumber;
  }

  @Override
  public boolean hasMoreElements() {
    if (stack == null) {
      return false;
    }

    for (GraphNode node : stack) {
      if (node.getScratch() != mark) {
        return true;
      }
    }
    return false;
  }

  @Override
  public GraphNode nextElement() {
    return next();
  }

  public GraphNode next() {
    if (stack == null) {
      throw new NoSuchElementException("DepthFirstEnumerator");
    }
    while (!stack.isEmpty()) {
      GraphNode node = stack.pop();
      if (node.getScratch() != mark) {
        for (Enumeration<GraphNode> e = node.outNodes(); e.hasMoreElements();) {
          GraphNode n = e.nextElement();
          if (n != null) {
            stack.push(n);
          }
        }
        node.setScratch(mark);
        return node;
      }
    }
    throw new NoSuchElementException("DepthFirstEnumerator");
  }
}
