/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import  java.util.Enumeration;
import  java.util.NoSuchElementException;


/**
 * @author Mauricio J. Serrano
 * @author John Whaley
 */
final class OPT_BreadthFirstEnumerator
    implements Enumeration {
  OPT_Queue queue;
  int mark;

  OPT_BreadthFirstEnumerator(OPT_GraphNode start, int markNumber) {
    queue = new OPT_Queue(start);
    mark = markNumber;
  }

  public boolean hasMoreElements() {
    if (queue == null)
      return  false;
    OPT_LinkedListObjectEnumerator e = queue.elements();
    while (e.hasMoreElements()) {
      OPT_GraphNode node = (OPT_GraphNode)e.next
      /*Element*/
      ();
      if (node.getScratch() != mark)
        return  true;
    }
    return  false;
  }

  public Object nextElement() {
    return  next();
  }

  public OPT_GraphNode next() {
    if (queue == null) {
      throw  new NoSuchElementException("OPT_BreadthFirstEnumerator");
    }
    while (!queue.isEmpty()) {
      OPT_GraphNode node = (OPT_GraphNode)queue.remove();
      if (node.getScratch() != mark) {
        for (Enumeration e = node.outNodes(); e.hasMoreElements();) {
          OPT_GraphNode n = (OPT_GraphNode)e.nextElement();
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



