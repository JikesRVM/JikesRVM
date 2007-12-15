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

/**
 * VCGEdge provides the minimum set of routines for printing a graph
 * edge in VCG format.  The graph should implement VCGGraph interface,
 * and its nodes - VCGNode interface.
 *
 * @see VCG
 * @see VCGGraph
 * @see VCGNode
 */

public interface VCGEdge extends VisEdge {
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
  class EdgeDesc implements VCGConstants {
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
     * To assign edge colors, use getEdgeColors in VCGGraph.GraphDesc.
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


