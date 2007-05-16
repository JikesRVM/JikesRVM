/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
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
