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
package org.jikesrvm.compilers.opt;

import java.util.Enumeration;
import java.util.NoSuchElementException;

final class OPT_DepthFirstEnumerator implements Enumeration<OPT_GraphNode> {
  OPT_Stack<OPT_GraphNode> stack;
  int mark;

  OPT_DepthFirstEnumerator(OPT_GraphNode start, int markNumber) {
    stack = new OPT_Stack<OPT_GraphNode>();
    stack.push(start);
    mark = markNumber;
  }

  public boolean hasMoreElements() {
    if (stack == null) {
      return false;
    }

    for (OPT_GraphNode node : stack) {
      if (node.getScratch() != mark) {
        return true;
      }
    }
    return false;
  }

  public OPT_GraphNode nextElement() {
    return next();
  }

  public OPT_GraphNode next() {
    if (stack == null) {
      throw new NoSuchElementException("OPT_DepthFirstEnumerator");
    }
    while (!stack.isEmpty()) {
      OPT_GraphNode node = stack.pop();
      if (node.getScratch() != mark) {
        for (Enumeration<OPT_GraphNode> e = node.outNodes(); e.hasMoreElements();) {
          OPT_GraphNode n = e.nextElement();
          if (n != null) {
            stack.push(n);
          }
        }
        node.setScratch(mark);
        return node;
      }
    }
    throw new NoSuchElementException("OPT_DepthFirstEnumerator");
  }
}
