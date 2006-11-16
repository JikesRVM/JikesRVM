/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.jikesrvm.opt;

/**
 * This class implements a graph vertex that holds an SCC.
 *
 * @author Stephen Fink
 */
class OPT_SCC_Vertex extends OPT_EdgelessGraphNode {
  private OPT_SCC scc;

  OPT_SCC_Vertex(OPT_SCC scc) {
    this.scc = scc;
  }

  OPT_SCC getSCC() {
    return  scc;
  }

  public String toString() {
    return  scc.toString();
  }
}
