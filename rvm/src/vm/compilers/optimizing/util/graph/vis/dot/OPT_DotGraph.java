/*
 * (C) Copyright IBM Corp. 2001
 */
//OPT_DotGraph.java
//$Id$
package com.ibm.JikesRVM.opt;

/**
 * OPT_DotGraph provides the minimum set of routines for printing a graph in
 * Dot format.  The graph nodes and edges should implement OPT_DotNode and
 * OPT_DotEdge interfaces respectively.
 *
 * Graph: "digraph name {
 *    attributes { group };
 *    node [nodedefaults];
 *    edge [edgedefaults];
 * }"
 * attributes (optional):
 * x   center       when true, centers drawing on page
 * x   clusterrank  may be local, global or none (local)
 *     color        background or cluster outline color (black)
 *     concentrate  enables edge concentrators when TRUE
 * x   fontcolor    type face color (black)
 * x   fontname     PostScript font family (Times-Roman)
 * x   fontsize     point size of label (14)
 *     label        any string
 * x   layerseq     id:id:id...
 * x   margin       margin included in page (.5,.5)
 * x   mclimit      if set to f, adjusts mincross iterations by f (1.0)
 * x   nodesep      separation between nodes, in inches (.25)
 * x   nslimit      if set to f, bounds network simplex iterations by f
 * x                (number of nodes)
 * x   ordering     out (for ordered edges)
 * x   orientation  portrait or landscape (portrait)
 * x   page         unit of pagination, e.g. 8.5,11
 * x   rank         same, min, or max
 *     rankdir      LR (left to right) or TB (top to bottom) (TB)
 * x   ranksep      separation between ranks, in inches (.75)
 * x   ratio        approximate aspect ratio desired, or fill
 * x   size         drawing bounding box, in inches
 *
 * @author Igor Pechtchanski
 * @see OPT_Dot
 * @see OPT_DotNode
 * @see OPT_DotEdge
 */

public interface OPT_DotGraph extends OPT_VisGraph {
  /**
   * Returns a Dot descriptor for the graph which will provide Dot-relevant
   * information for the graph.
   * If finer control over graph options is not needed, it's enough to
   * implement in the following fashion:
   * <pre>
   *    public GraphDesc getDotDescriptor() { return defaultDotDesc; }
   * </pre>
   * @return graph descriptor
   */
  public GraphDesc getDotDescriptor();

  /**
   * Default Dot descriptor
   */
  public final GraphDesc defaultDotDesc = new GraphDesc();

  /**
   * Dot Graph Descriptor class
   * Subclass to extend functionality
   */
  public static class GraphDesc implements OPT_DotConstants {
    /**
     * Returns the label of the graph.
     * @return graph label
     */
    public String getLabel() { return null; }

    /** 
     * Returns whether the viewer should enable edge concentrators
     * (share edge connection points).
     * @return true if sharing is allowed, false otherwise
     */
    public boolean concentrate() { return false; }

    //////////////////////
    // Graph direction constants
    //////////////////////

    /** Top-to-bottom (default) */
    public static final String RANKTB = "TB";
    /** Left-to-right */
    public static final String RANKLR = "LR";

    /**
     * Returns the direction of the graph.
     * Should be one of the above.
     * @return graph direction
     */
    public String getDirection() { return null; }

    /**
     * Returns the color of the background or cluster outline.
     * Should be one of color constants or an RGB color.
     * @see OPT_DotUtils.RGB()
     * @return outline or background color
     */
    public String getColor() { return null; }

    /**
     * Returns the default node attributes object.
     * Override to change node attribute defaults.
     * @return default node attributes
     */
    OPT_DotNode.NodeDesc getDefaultNodeDescriptor() { return null; }

    /**
     * Returns the default edge attributes object.
     * Override to change edge attribute defaults.
     * @return default edge attributes
     */
    OPT_DotEdge.EdgeDesc getDefaultEdgeDescriptor() { return null; }

    /** 
     * Returns custom layout parameters.
     * @return custom layout parameters
     */
    public String getLayoutParameters() { return null; }
  }
}

