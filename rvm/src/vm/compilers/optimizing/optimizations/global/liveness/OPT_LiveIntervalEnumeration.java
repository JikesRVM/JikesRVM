/*
 * (C) Copyright IBM Corp. 2001, 2004
 */
//$Id$
package com.ibm.JikesRVM.opt;

import java.util.Enumeration;

/**
 * Enumerator for a list of live intervals stored on a basic block.
 *
 * Note: This is fragile.  Use with care iff you know what you're doing.
 * TODO: redesign the way live info is stored on the IR to be a bit more
 * robust.  eg., don't use scratch fields.
 *
 * @author Stephen Fink
 */
public class OPT_LiveIntervalEnumeration implements Enumeration {
  private OPT_LiveIntervalElement currentElement;

  /**
   * @param first  The first live interval in a list to be enumerated
   */
  public OPT_LiveIntervalEnumeration(OPT_LiveIntervalElement first) {
    this.currentElement = first;
  }

  public boolean hasMoreElements() {
    return currentElement != null;
  }

  public Object nextElement() { 
    Object result = currentElement;
    currentElement = currentElement.getNext();
    return result;
  } 
}
