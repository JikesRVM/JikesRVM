/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.compilers.opt.util;


public class FilteredDFSenumerateByFinish extends DFSenumerateByFinish {

  private final GraphEdgeFilter filter;

  public FilteredDFSenumerateByFinish(Graph net, GraphNodeEnumeration nodes, GraphEdgeFilter filter) {
    super(net, nodes);
    this.filter = filter;
  }

  @Override
  protected GraphNodeEnumeration getConnected(GraphNode n) {
    return filter.outNodes(n);
  }

}
