/*
 * (C) Copyright IBM Corp. 2001
 */
//OPT_VisNode.java
//$Id$

import java.util.Enumeration;

/**
 * OPT_VisNode provides the minimum set of routines for graph node
 * visualization.  The graph should implement OPT_VisGraph interface, and
 * its edges - OPT_VisEdge interface.
 *
 * @author Igor Pechtchanski
 * @see OPT_VisGraph
 * @see OPT_VisEdge
 */

public interface OPT_VisNode {
  /**
   * Returns the edges of the node.
   * Each of the edges has to implement the OPT_VisEdge interface
   * @return the enumeration that would list the edges of the node
   */
  public Enumeration edges();

  /**
   * To be used for implementing edges() for graphs that don't
   * have explicit edge representation.
   */
  public static class DefaultEdge implements OPT_VisEdge {
    private OPT_VisNode _s, _t;
    public DefaultEdge(OPT_VisNode s, OPT_VisNode t) { _s = s; _t = t; }
    public OPT_VisNode sourceNode() { return _s; }
    public OPT_VisNode targetNode() { return _t; }
  }
}

