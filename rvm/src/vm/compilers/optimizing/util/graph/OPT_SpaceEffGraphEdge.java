/*
 * (C) Copyright IBM Corp. 2001
 */
//OPT_SpaceEffGraphEdge.java
//$Id$
package com.ibm.JikesRVM.opt;

/**
 * OPT_SpaceEffGraphEdge is a generic graph edge.  Extend this to implement
 * specific graph edge types, or use it as a generic edge.
 * OPT_SpaceEffGraphEdges are directed, and therefore, have a from-node and
 * a to-node.
 * @author Mauricio J. Serrano
 * @author Igor Pechtchanski
 */
public class OPT_SpaceEffGraphEdge implements OPT_GraphEdge, OPT_VCGEdge {
  /**
   * End node.
   */
  protected OPT_SpaceEffGraphNode _toNode;

  /**
   * Start node.
   */
  protected OPT_SpaceEffGraphNode _fromNode;

  /**
   * The following word is defined for several uses.  The first 4 bits
   * are reserved for OPT_SpaceEffGraph.  Classes that subclass this one
   * can use the remaining 28 bits
   */
  protected int scratch;

  static final int VISITED   = 0x10000000; // general purpose

  static final int BACK_EDGE = 0x20000000; // edge information
  static final int DOMINATOR = 0x40000000; // edge information

  static final int INFO_MASK = 0x0fffffff;

  public final boolean visited()       { return (scratch & VISITED  ) != 0; }
  public final boolean backEdge()      { return (scratch & BACK_EDGE) != 0; }
  public final boolean dominatorEdge() { return (scratch & DOMINATOR) != 0; }

  public final void setVisited()       { scratch |= VISITED;   }
  public final void setBackEdge()      { scratch |= BACK_EDGE; }
  public final void setDominatorEdge() { scratch |= DOMINATOR; }

  public final void clearVisited()       { scratch &= ~VISITED;   }
  public final void clearBackEdge()      { scratch &= ~BACK_EDGE; }
  public final void clearDominatorEdge() { scratch &= ~DOMINATOR; }

  public final int getInfo() {
    return scratch & INFO_MASK;
  }

  public final void setInfo(int value) {
    scratch = (scratch & ~INFO_MASK) | (value & INFO_MASK);
  }

  /**
   * Get the end node for the edge.
   * @return end node for the edge
   */
  public final OPT_SpaceEffGraphNode toNode() { return _toNode; }

  /**
   * Get the start node for the edge.
   * @return start node for the edge
   */
  public final OPT_SpaceEffGraphNode fromNode() { return _fromNode; }

  /**
   * Set end node.
   * WARNING: use with caution
   * @param toNode new end node
   */
  final void setToNode(OPT_SpaceEffGraphNode toNode) { _toNode = toNode; }

  /**
   * Set start node.
   * WARNING: use with caution
   * @param fromNode new start node
   */
  final void setFromNode(OPT_SpaceEffGraphNode fromNode) {
    _fromNode = fromNode;
  }

  /**
   * Constructs an empty edge.
   */
  OPT_SpaceEffGraphEdge() { }

  /**
   * Constructs an edge starting at a given node and ending at a given node.
   * @param fromNode start node
   * @param toNode end node
   */
  OPT_SpaceEffGraphEdge(OPT_SpaceEffGraphNode fromNode,
                        OPT_SpaceEffGraphNode toNode)
  {
    _toNode = toNode;
    _fromNode = fromNode;
  }

  /**
   * Delete this edge from the graph.
   */
  final void delete() {
    _fromNode.removeOut(this);
    _toNode.removeIn(this);
  }

  /**
   * Returns the string representation of the edge type.
   * @return string representation of the edge type
   */
  public String getTypeString() { return ""; }

  /**
   * Returns the string representation of the end node (used for printing).
   * @return string representation of the end node
   */
  public String toNodeString() {
     return "---> "+_toNode;
  }

  /**
   * Returns the string representation of the start node (used for printing).
   * @return string representation of the start node
   */
  public String fromNodeString() {
     return "<--- "+_fromNode;
  }

  /**
    * Get the end node for the edge.
    * @return end node for the edge
    */
  public final OPT_GraphNode to() { return _toNode; }
 
  /**
   * Get the start node for the edge.
   * @return start node for the edge
   */
  public final OPT_GraphNode from() { return _fromNode; }
 
  /**
   * Returns the source node of the edge.
   * @return edge source node
   * @see OPT_VisEdge#sourceNode
   */
  public OPT_VisNode sourceNode() { return _fromNode; }

  /**
   * Returns the target node of the edge.
   * @return edge target node
   * @see OPT_VisEdge#targetNode
   */
  public OPT_VisNode targetNode() { return _toNode; }

  /**
   * Returns whether this edge is a backedge.
   * @return true if the edge is a backedge, false otherwise
   * @see OPT_VCGEdge#backEdge
   */
  // Already defined above.
  //public boolean backEdge();

  /**
   * Returns a VCG descriptor for the edge which will provide VCG-relevant
   * information for the edge.
   * @return edge descriptor
   * @see OPT_VCGEdge#getVCGDescriptor
   */
  public EdgeDesc getVCGDescriptor() {
    return new EdgeDesc() {
      public String getStyle() { return backEdge()?"dotted":null; }
      public String getColor() { return backEdge()?"red":null; }
      public int getThickness() { return dominatorEdge()?3:1; }
    };
  }

  /**
   * Links inlined from LinkedListElement2.
   */
  protected OPT_SpaceEffGraphEdge nextIn, nextOut;

  /**
   * Get the next in edge.
   * @return next in edge.
   */
  public final OPT_SpaceEffGraphEdge getNextIn() { return nextIn; }

  /**
   * Get the next out edge.
   * @return next out edge.
   */
  public final OPT_SpaceEffGraphEdge getNextOut() { return nextOut; }

  /**
   * Append a given edge after this edge as an in edge.
   * @param e the edge to append
   */
  final void appendIn(OPT_SpaceEffGraphEdge e) { nextIn = e; }

  /**
   * Append a given edge after this edge as an out edge.
   * @param e the edge to append
   */
  final void appendOut(OPT_SpaceEffGraphEdge e) { nextOut = e; }
}

