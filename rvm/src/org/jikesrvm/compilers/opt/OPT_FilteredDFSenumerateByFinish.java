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

class OPT_FilteredDFSenumerateByFinish extends OPT_DFSenumerateByFinish {

  private final OPT_GraphEdgeFilter filter;

  OPT_FilteredDFSenumerateByFinish(OPT_Graph net, OPT_GraphNodeEnumeration nodes, OPT_GraphEdgeFilter filter) {
    super(net, nodes);
    this.filter = filter;
  }

  protected OPT_GraphNodeEnumeration getConnected(OPT_GraphNode n) {
    return filter.outNodes(n);
  }

}
