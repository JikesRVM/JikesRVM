/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import  java.util.Enumeration;
import  java.util.NoSuchElementException;

/**
 * @author Mauricio J. Serrano
 * @author John Whaley
 */
final class OPT_DepthFirstEnumerator
    implements Enumeration {
  OPT_Stack stack;
  int mark;

  /**
   * put your documentation comment here
   * @param   OPT_GraphNode start
   * @param   int markNumber
   */
  OPT_DepthFirstEnumerator (OPT_GraphNode start, int markNumber) {
    stack = new OPT_Stack();
    stack.push(start);
    mark = markNumber;
  }

  /**
   * put your documentation comment here
   * @return 
   */
  public boolean hasMoreElements () {
    if (stack == null)
      return  false;
    /*Enumeration*/

    OPT_LinkedListObjectEnumerator e = stack.elements();
    while (e.hasMoreElements()) {
      OPT_GraphNode node = (OPT_GraphNode)e.next
      /*Element*/
      ();
      if (node.getScratch() != mark)
        return  true;
    }
    return  false;
  }

  /**
   * put your documentation comment here
   * @return 
   */
  public Object nextElement () {
    return  next();
  }

  /**
   * put your documentation comment here
   * @return 
   */
  public OPT_GraphNode next () {
    if (stack == null)
      throw  new NoSuchElementException("OPT_DepthFirstEnumerator");
    while (!stack.isEmpty()) {
      OPT_GraphNode node = (OPT_GraphNode)stack.pop();
      if (node.getScratch() != mark) {
        for (Enumeration e = node.outNodes(); e.hasMoreElements();) {
          OPT_GraphNode n = (OPT_GraphNode)e.nextElement();
          if (n != null)
            stack.push(n);
        }
        node.setScratch(mark);
        return  node;
      }
    }
    throw  new NoSuchElementException("OPT_DepthFirstEnumerator");
  }

  /**
   * put your documentation comment here
   */
  private OPT_DepthFirstEnumerator () {
  }
  public static OPT_DepthFirstEnumerator EMPTY = new OPT_DepthFirstEnumerator();
}



