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

import java.util.HashMap;

/**
 * This class implements depth-first search over a OPT_Graph,
 * and stores the discover and finish numbers for each vertex
 * See Cormen, Leiserson, Rivest Ch. 23
 *
 * TODO: this implementation is not terribly efficient
 * (hopefully, I have fixed this to some degree -- Julian)
 *
 * @author Stephen Fink
 * @author Julian Dolby
 */
class OPT_DFS extends OPT_Stack<VertexInfo> {
  // Interface 

  OPT_DFS(OPT_Graph net) {
    traverse(net.enumerateNodes());
  }

  OPT_DFS(OPT_GraphNodeEnumeration nodes) {
    traverse( nodes );
  }

  /** 
   * Return the discovery time for a vertex.
   */
  public int getDiscover(OPT_GraphNode v) {
    VertexInfo vInfo = (VertexInfo)info.get(v);
    if (vInfo == null) return -1;
    return vInfo.discover;
  }

  /** 
   * Return the finish time for a vertex.
   */
  public int getFinish(OPT_GraphNode v) {
    VertexInfo vInfo = (VertexInfo)info.get(v);
    if (vInfo == null) return -1;
    return vInfo.finish;
  }

  // Implementation
  /** f: vertex -> VertexInfo */
  private final HashMap<OPT_GraphNode, VertexInfo> info =
    new HashMap<OPT_GraphNode, VertexInfo>();
  private int time;

  /**
   * Perform a DFS of net and mark the discover and finish times
   * for each vertex
   */
  static final int WHITE = 0;
  static final int GRAY  = 1;
  static final int BLACK = 2;

  private synchronized void traverse(OPT_GraphNodeEnumeration e) {
    time = 0;
    for (; e.hasMoreElements(); ) {
      OPT_GraphNode v = e.next();
      if (info.get(v) == null) 
        DFSVisit(new VertexInfo(v, info));
    }
  }

  /**
   * Implementation: simulates recursion
   */
  protected static OPT_GraphNodeEnumeration getConnected(OPT_GraphNode n) {
    return n.outNodes();
  }

  private void DFSVisit(VertexInfo v) {
    push(v);

    recurse: while (!empty()) {
      VertexInfo vInfo = (VertexInfo) peek();

      if (vInfo.color == WHITE) {
        // this is the first time we've discovered this vertex.
        vInfo.color = GRAY;   
        vInfo.discover = time;
        time = time+1;
      }
       
      for (OPT_GraphNodeEnumeration e = vInfo.pendingChildren; e.hasMoreElements();){
        OPT_GraphNode n = e.next();
        VertexInfo nInfo = (VertexInfo)info.get(n);
        if (nInfo == null) {
          // found a new child: recurse to it.
          nInfo = new VertexInfo(n, info);
          push(nInfo);
          continue recurse;
        }
      }

      // no more children to visit: finish up vertex.
      vInfo.color = BLACK;
      vInfo.finish = time;
      time = time+1;
      pop();
    }
  }
}

/** Class that holds information for the DFS */
class VertexInfo {
  int discover;
  int finish;
  int color = OPT_DFS.WHITE;
  final OPT_GraphNode node;
  final OPT_GraphNodeEnumeration pendingChildren;
  
  VertexInfo(OPT_GraphNode n, HashMap<OPT_GraphNode, VertexInfo> info) {
    pendingChildren = OPT_DFS.getConnected( n );
    node = n;
    info.put(n, this);
  }
}