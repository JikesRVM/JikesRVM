/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import java.util.*;
import java.io.Serializable;

/**
 * This is general graph implementation for graphs that do not need
 * explicit edge objects.  It co-operates with OPT_EdgelessGraphNode
 * to implement a graph in which edges are represented by in and out
 * node hash tables in each node.
 *
 * @author Julian Dolby
 *
 * @see OPT_EdgelessGraphNode
 *
 */
class OPT_EdgelessGraph implements OPT_Graph, Serializable {

    /**
     * an array of all nodes in the graph
     */
    protected ArrayList nodes = new ArrayList();

    /** 
     * Enumerate all the nodes in the graph
     * @return an enumeration of all the nodes in the graph
     */
    public OPT_GraphNodeEnumeration enumerateNodes() {
        final Iterator it = nodes.iterator();
        return new OPT_GraphNodeEnumeration() {
          public boolean hasMoreElements() { return it.hasNext(); }
          public OPT_GraphNode next() { return (OPT_GraphNode)it.next();}
          public Object nextElement() { return next(); }
        };
    }

    /** 
     * Find out how many nodes are in the graph
     * @return the number of nodes in the graph
     */
    public int numberOfNodes() {
        return nodes.size();
    }

    /**
     *  Since nodes are numbered in terms of the vector containing
     * them, this method need not do anything.  It is needed to
     * implement OPT_Graph.
     * 
     * @see OPT_Graph
     *
     */
    public void compactNodeNumbering() {

    }

    /**
     * Add a new node to the graph.
     * @param node the node to add, which should be an OPT_EdgelessGraphNode.
     */
    public void addGraphNode(OPT_GraphNode node) {
        OPT_EdgelessGraphNode n = (OPT_EdgelessGraphNode) node;
        nodes.add(n);
        n.setIndex(nodes.size() - 1);
    }
    
    /**
     * Add a new edge to the graph.
     * @param source the source of the edge to add, which should be an
     * OPT_EdgelessGraphNode.
     * @param target the target of the edge to add, which should be an
     * OPT_EdgelessGraphNode.
     */
    public void addGraphEdge(OPT_GraphNode source, OPT_GraphNode target) {
        OPT_EdgelessGraphNode s = (OPT_EdgelessGraphNode) source;
        OPT_EdgelessGraphNode t = (OPT_EdgelessGraphNode) target;
        s.addOutEdgeInternal( t );
        t.addInEdgeInternal( s );
    }

    /**
     * Find a node corresponding to an index.
     * @param index the index for which to obtain the corresponding node.
     * @return the node with the given index.
     */
    public OPT_EdgelessGraphNode getNode(int index) {
        return (OPT_EdgelessGraphNode) nodes.get(index);
    }
}
