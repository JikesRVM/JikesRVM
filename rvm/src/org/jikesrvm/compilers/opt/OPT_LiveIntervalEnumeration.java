/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001, 2004
 */
package org.jikesrvm.compilers.opt;

import java.util.Enumeration;

/**
 * Enumerator for a list of live intervals stored on a basic block.
 *
 * Note: This is fragile.  Use with care iff you know what you're doing.
 * TODO: redesign the way live info is stored on the IR to be a bit more
 * robust.  eg., don't use scratch fields.
 *
 */
public class OPT_LiveIntervalEnumeration implements Enumeration<OPT_LiveIntervalElement> {
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

  public OPT_LiveIntervalElement nextElement() { 
    OPT_LiveIntervalElement result = currentElement;
    currentElement = currentElement.getNext();
    return result;
  } 
}
