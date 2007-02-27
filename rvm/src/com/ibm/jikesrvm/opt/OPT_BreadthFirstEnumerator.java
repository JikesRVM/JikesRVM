/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package com.ibm.jikesrvm.opt;

import  java.util.Enumeration;
import  java.util.NoSuchElementException;


/**
 * @author Mauricio J. Serrano
 * @author John Whaley
 */
final class OPT_BreadthFirstEnumerator
    implements Enumeration<OPT_GraphNode> {
  OPT_Queue<OPT_GraphNode> queue= new OPT_Queue<OPT_GraphNode>();
  int mark;

  OPT_BreadthFirstEnumerator(OPT_GraphNode start, int markNumber) {
    queue.insert(start);
    mark = markNumber;
  }

  public boolean hasMoreElements() {
    if (queue == null)
      return  false;
    for (OPT_GraphNode node : queue) {
      if (node.getScratch() != mark)
        return  true;
    }
    return  false;
  }

  public OPT_GraphNode nextElement() {
    return  next();
  }

  public OPT_GraphNode next() {
    while (!queue.isEmpty()) {
      OPT_GraphNode node = queue.remove();
      if (node.getScratch() != mark) {
        for (OPT_GraphNodeEnumeration e = node.outNodes(); e.hasMoreElements();) {
          OPT_GraphNode n = e.nextElement();
          if (n != null)
            queue.insert(n);
        }
        node.setScratch(mark);
        return  node;
      }
    }
    throw  new NoSuchElementException("OPT_BreadthFirstEnumerator");
  }

  private OPT_BreadthFirstEnumerator() {
  }
  public static OPT_BreadthFirstEnumerator EMPTY = 
      new OPT_BreadthFirstEnumerator();
}



