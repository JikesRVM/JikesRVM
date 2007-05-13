/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
//OPT_VCGEdge.java
package org.jikesrvm.compilers.opt;

/**
 * OPT_VCGEdge provides the minimum set of routines for printing a graph
 * edge in VCG format.  The graph should implement OPT_VCGGraph interface,
 * and its nodes - OPT_VCGNode interface.
 *
 * @see OPT_VCG
 * @see OPT_VCGGraph
 * @see OPT_VCGNode
 */

public interface OPT_VCGEdge extends OPT_VisEdge {
  /**
   * Returns a VCG descriptor for the edge which will provide VCG-relevant
   * information for the edge.
   * If finer control over edge options is not needed, it's enough to
   * implement in the following fashion:
   * <pre>
   *    public EdgeDesc getVCGDescriptor() { return defaultVCGDesc; }
   * </pre>
   * @return edge descriptor
   */
  EdgeDesc getVCGDescriptor();

  /**
   * Returns whether this edge is a backedge.
   * @return true if the edge is a backedge, false otherwise
   */
  boolean backEdge();

  /**
   * Default VCG descriptor
   */
  EdgeDesc defaultVCGDesc = new EdgeDesc();

  /**
   * VCG Graph Edge Descriptor class
   * Subclass to extend functionality
   */
  class EdgeDesc implements OPT_VCGConstants {
    /**
     * Returns the label of the edge (contents).
     * Default is blank.
     * @return edge label
     */
    public String getLabel() { return null; }

    /**
     * Returns the class of the edge.
     * @return edge class
     */
    public int getType() { return NONE; }

    /**
     * Returns the color of the edge.
     * Default is black if edge colors are not defined,
     * and the color corresponding to the edge class if they are defined.
     * To assign edge colors, use getEdgeColors in OPT_VCGGraph.GraphDesc.
     * NOTE: colors for ALL classes must be defined.
     * @return edge color
     */
    public String getColor() { return null; }

    /**
     * Returns the thickness of the edge.
     * @return edge thickness
     */
    public int getThickness() { return 1; }

    /**
     * Returns the line style of the edge.
     * Default is solid.
     * @return edge line style
     */
    public String getStyle() { return null; }
  }
}


