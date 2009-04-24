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
package org.jikesrvm.compilers.opt.regalloc;

import java.util.HashMap;

import org.jikesrvm.compilers.opt.ir.Register;
import org.jikesrvm.compilers.opt.util.GraphEdge;
import org.jikesrvm.compilers.opt.util.SpaceEffGraph;
import org.jikesrvm.compilers.opt.util.SpaceEffGraphEdge;
import org.jikesrvm.compilers.opt.util.SpaceEffGraphNode;
import org.jikesrvm.compilers.opt.util.SpaceEffGraphNode.GraphEdgeEnumeration;

/**
 * This class represents a graph, where
 *    - the nodes are registers
 *    - the edge weights represent affinities between registers.
 *
 * This graph is used to drive coalescing during register allocation.
 *
 * Implementation: this is meant to be an undirected graph.  By
 * convention, we enforce that the register with the lower number is the
 * source of an edge.
 */
class CoalesceGraph extends SpaceEffGraph {

  /**
   * Mapping register -> Node
   */
  final HashMap<Register, Node> nodeMap = new HashMap<Register, Node>();

  /**
   * find or create a node in the graph corresponding to a register.
   */
  private Node findOrCreateNode(Register r) {
    Node n = nodeMap.get(r);
    if (n == null) {
      n = new Node(r);
      nodeMap.put(r, n);
      addGraphNode(n);
    }
    return n;
  }

  /**
   * Find the node corresponding to a regsiter.
   */
  Node findNode(Register r) {
    return nodeMap.get(r);
  }

  /**
   * find or create an edge in the graph
   */
  private Edge findOrCreateEdge(Node src, Node dest) {
    Edge edge = null;
    for (GraphEdgeEnumeration<GraphEdge> e = src.outEdges(); e.hasMoreElements();) {
       Edge candidate = (Edge)e.next();
      if (candidate.toNode() == dest) {
        edge = candidate;
        break;
      }
    }
    if (edge == null) {
      edge = new Edge(src, dest);
      addGraphEdge(edge);
    }
    return edge;
  }

  /**
   * Add an affinity of weight w between registers r1 and r2
   */
  void addAffinity(int w, Register r1, Register r2) {
    Node src;
    Node dest;
    if (r1.getNumber() == r2.getNumber()) return;

    // the register with the smaller number is the source of the edge.
    if (r1.getNumber() < r2.getNumber()) {
      src = findOrCreateNode(r1);
      dest = findOrCreateNode(r2);
    } else {
      src = findOrCreateNode(r2);
      dest = findOrCreateNode(r1);
    }

    Edge edge = findOrCreateEdge(src, dest);

    edge.addWeight(w);
  }

  static class Node extends SpaceEffGraphNode {
    final Register r;

    Node(Register r) {
      this.r = r;
    }

    Register getRegister() {
      return r;
    }
  }

  static class Edge extends SpaceEffGraphEdge {
    private int w;

    Edge(Node src, Node dest) {
      super(src, dest);
    }

    void addWeight(int x) {
      w += x;
    }

    int getWeight() {
      return w;
    }
  }
}
