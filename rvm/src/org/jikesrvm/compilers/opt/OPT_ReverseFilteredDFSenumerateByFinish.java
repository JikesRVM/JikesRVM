/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2002
 */
package org.jikesrvm.compilers.opt;

class OPT_ReverseFilteredDFSenumerateByFinish extends OPT_ReverseDFSenumerateByFinish {

  private final OPT_GraphEdgeFilter filter;

  OPT_ReverseFilteredDFSenumerateByFinish(OPT_Graph net,
                                          OPT_GraphNodeEnumeration nodes,
                                          OPT_GraphEdgeFilter filter) {
    super(net, nodes);
    this.filter = filter;
  }

  protected OPT_GraphNodeEnumeration getConnected(OPT_GraphNode n) {
    return filter.inNodes(n);
  }

}
