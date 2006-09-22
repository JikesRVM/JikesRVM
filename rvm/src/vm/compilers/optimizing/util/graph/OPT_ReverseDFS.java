/*
 * This file is part of the Jikes RVM project (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

/**
 * @author Mauricio J. Serrano
 * @author John Whaley
 */
class OPT_ReverseDFS extends OPT_DFS {

  OPT_ReverseDFS (OPT_Graph net) {
    super(net);
  }

  OPT_ReverseDFS (OPT_GraphNodeEnumeration nodes) {
    super(nodes);
  }

  protected OPT_GraphNodeEnumeration getConnected (OPT_GraphNode n) {
    return  n.inNodes();
  }
}
