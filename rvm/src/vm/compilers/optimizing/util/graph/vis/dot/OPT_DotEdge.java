/*
 * (C) Copyright IBM Corp. 2001
 */
//OPT_DotEdge.java
//$Id$
package com.ibm.JikesRVM.opt;

/**
 * OPT_DotEdge provides the minimum set of routines for printing a graph
 * edge in Dot format.  The graph should implement OPT_DotGraph interface,
 * and its nodes - OPT_DotNode interface.
 *
 * Edge: "source -> dest [attributes];" (attributes optional)
 * attributes:
 *     color      edge stroke color (black)
 *     decorate   if set, draws a line connecting labels with their edges
 *     dir        forward, back, both, or none (forward)
 * x   fontcolor  type face color (black)
 * x   fontname   PostScript font family (Times-Roman)
 * x   fontsize   point size of label (14)
 *     id         optional value to distinguish multiple edges
 *     label      label, if not empty
 * x   layer      overlay range all, id or id:id
 * x   minlen     minimum rank distance between head and tail (1)
 *     style      graphics options, e.g. bold, dotted, filled
 *     weight     integer reflecting importance of edge (1)
 *
 * @author Igor Pechtchanski
 * @see OPT_Dot
 * @see OPT_DotGraph
 * @see OPT_DotNode
 */

public interface OPT_DotEdge extends OPT_VisEdge {
  /**
   * Returns a Dot descriptor for the edge which will provide Dot-relevant
   * information for the edge.
   * If finer control over edge options is not needed, it's enough to
   * implement in the following fashion:
   * <pre>
   *    public EdgeDesc getDotDescriptor() { return defaultDotDesc; }
   * </pre>
   * @return edge descriptor
   */
  public EdgeDesc getDotDescriptor();

  /**
   * Default Dot descriptor
   */
  public final EdgeDesc defaultDotDesc = new EdgeDesc();

  /**
   * Dot Graph Edge Descriptor class
   * Subclass to extend functionality
   */
  public static class EdgeDesc extends OPT_DotUtils {
    /**
     * Returns the label of the edge (contents).
     * Default is blank.
     * @return edge label
     */
    public String getLabel() { return null; }

    /**
     * Returns the color of the edge.
     * Should be one of color constants or an RGB color.
     * @see OPT_DotUtils#RGB
     * @return edge color
     */
    public String getColor() { return null; }

    /**
     * Returns whether to connect the label to the edge with a line.
     * @return true if there should be a line from label to edge
     */
    public boolean decorate() { return false; }

    //////////////////////
    // Arrow style constants
    //////////////////////

    /** Forward [end point] (default) */
    public static final String ARROWFORWARD = "forward";
    /** Back [start point] */
    public static final String ARROWBACK = "back";
    /** None */
    public static final String ARROWNONE = "none";
    /** Both */
    public static final String ARROWBOTH = "both";

    /**
     * Returns the arrow direction of the edge.
     * Should be one of the above.
     * @return edge arrow direction
     */
    public String getDirection() { return null; }

    /**
     * Returns optional value to distinguish multiple edges.
     * Default is none.
     * @return edge id
     */
    public String getId() { return null; }

    /**
     * Returns the line style of the edge.
     * Should be one of style constants or a combination.
     * @see OPT_DotUtils#combine
     * @return edge line style
     */
    public String getStyle() { return null; }

    /**
     * Returns the integer reflecting the importance of the edge.
     * @return edge importance
     */
    public int getWeight() { return NONE; }
  }
}


