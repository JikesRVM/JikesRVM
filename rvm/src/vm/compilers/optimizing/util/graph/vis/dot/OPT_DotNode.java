/*
 * (C) Copyright IBM Corp. 2001
 */
//OPT_DotNode.java
//$Id$
package com.ibm.JikesRVM.opt;

/**
 * OPT_DotNode provides the minimum set of routines for printing a graph
 * node in Dot format.  The graph should implement OPT_DotGraph interface,
 * and its edges - OPT_DotEdge interface.
 *
 * Node: "name [attributes];"
 * attributes (optional):
 *     color      node shape color (black)
 * x   fontcolor  type face color (black)
 * x   fontname   PostScript font family (Times-Roman)
 * x   fontsize   point size of label (14)
 *     height     height in inches (.5)
 *     width      width in inches (.75)
 *     label      any string (node name)
 * x   layer      all, id or id:id (overlay range)
 *     shape      ellipse, box, circle, doublecircle, diamond, plaintext,
 *                polygon, record, [x] epsf (ellipse)
 * x   shapefile  external EPSF file if epsf shape
 *     style      graphics options, e.g. bold, dotted, filled
 *
 * @author Igor Pechtchanski
 * @see OPT_Dot
 * @see OPT_DotGraph
 * @see OPT_DotEdge
 */

public interface OPT_DotNode extends OPT_VisNode {
  /**
   * Returns a Dot descriptor for the node which will provide Dot-relevant
   * information for the node.
   * If finer control over node options is not needed, it's enough to
   * implement in the following fashion:
   * <pre>
   *    public NodeDesc getDotDescriptor() { return defaultDotDesc; }
   * </pre>
   * @return node descriptor
   */
  public NodeDesc getDotDescriptor();

  /**
   * Default Dot descriptor
   */
  public final NodeDesc defaultDotDesc = new NodeDesc();

  /**
   * Dot Graph Node Descriptor class
   * Subclass to extend functionality
   */
  public static class NodeDesc extends OPT_DotUtils {
    /**
     * Returns the label of the node (contents).
     * Default is node number.
     * @return node label
     */
    public String getLabel() { return null; }

    /**
     * Returns the color of the node.
     * Should be one of color constants or an RGB color.
     * @see OPT_DotUtils.RGB()
     * @return node color
     */
    public String getColor() { return null; }

    //////////////////////
    // Node shape constants
    //////////////////////

    /** Ellipse shape (default) */
    public static final String ELLIPSE = "ellipse";
    /** Box shape */
    public static final String BOX = "box";
    /** Diamond shape */
    public static final String DIAMOND = "diamond";
    /** Plain text shape (no shape) */
    public static final String PLAINTEXT = "plaintext";
    /** Cicrle shape */
    public static final String CIRCLE = "circle";
    /** Double circle shape */
    public static final String DOUBLECIRCLE = "doublecircle";
    /** Triangle shape */
    public static final String TRIANGLE = "triangle";
    /** Inverted triangle shape */
    public static final String INVTRIANGLE = "invtriangle";
    /** Trapezium shape */
    public static final String TRAPEZIUM = "trapezium";
    /** Inverted trapezium shape */
    public static final String INVTRAPEZIUM = "invtrapezium";
    /** Hexagon shape */
    public static final String HEXAGON = "hexagon";

    /**
     * Regular polygon shape
     * @param sides number of sides
     */
    public static String POLYGON(int sides) {
      return "polygon,sides="+sides;
    }

    /**
     * Regular polygon shape (rotated)
     * @param sides number of sides
     * @param orientation angle of rotation (in degrees)
     */
    public static String POLYGON(int sides, int orientation) {
      return "polygon,sides="+sides+",orientation="+orientation;
    }

    /**
     * Parallelogram shape
     * @param skew horizontal skew factor
     */
    public static String PARALLELOGRAM(float skew) {
      return "polygon,sides=4,skew="+skew;
    }

    /**
     * Trapezoid shape
     * @param distortion distortion factor (ratio of bottom to top)
     */
    public static String TRAPEZOID(float distortion) {
      return "polygon,sides=4,distortion="+distortion;
    }

    /**
     * Generic polygon shape
     * @param sides number of sides
     * @param peripheries number of peripheries
     * @param orientation angle of rotation (in degrees)
     * @param skew horizontal skew factor
     * @param distortion distortion factor (lower edge vs. upper edge)
     */
    public static String POLYGON(int sides, int peripheries, int orientation,
                                 float skew, float distortion)
    {
      return "polygon,sides="+sides+",peripheries="+peripheries+
             ",orientation="+orientation+",skew="+skew+
             ",distortion="+distortion;
    }

    /** Record shape TODO (read docs, use at your own risk!) */
    public static final String RECORD = "record";

    /**
     * Returns the shape of the node.
     * Should be one of the above.
     * @return node shape
     */
    public String getShape() { return null; }

    /**
     * Returns the line style of the node.
     * Should be one of style constants or a combination.
     * @see OPT_DotUtils.combine()
     * @return node line style
     */
    public String getStyle() { return null; }

    /** Default node width */
    public static float DEFAULT_WIDTH = (float).5;

    /**
     * Returns the width of the node (in inches).
     * @return node width
     */
    public float getWidth() { return DEFAULT_WIDTH; }

    /** Default node height */
    public static float DEFAULT_HEIGHT = (float).5;

    /**
     * Returns the height of the node (in inches).
     * @return node height
     */
    public float getHeight() { return DEFAULT_HEIGHT; }
  }

  /**
   * To be used for implementing edges() for graphs that don't
   * have explicit edge representation.
   */
  public static class DefaultEdge extends OPT_VisNode.DefaultEdge
    implements OPT_DotEdge
  {
    public DefaultEdge(OPT_DotNode s, OPT_DotNode t) { super(s, t); }
    public EdgeDesc getDotDescriptor() { return OPT_DotEdge.defaultDotDesc; }
  }
}

