/*
 * (C) Copyright IBM Corp. 2001
 */
//OPT_DotUtils.java
//$Id$
package com.ibm.JikesRVM.opt;

/**
 * A set of functions for use in Dot routines.
 *
 * @author Igor Pechtchanski
 * @see OPT_Dot
 * @see OPT_DotConstants
 * @see OPT_DotGraph
 * @see OPT_DotNode
 * @see OPT_DotEdge
 */

public abstract class OPT_DotUtils implements OPT_DotConstants {
  //////////////////////
  // Color utilities
  //////////////////////

  /** 
   * Constructs a color given 3 HSB values.
   * All values should be between 0 and 1.
   * @param H hue value
   * @param S saturation value
   * @param B brightness value
   * @return string representation of color
   */
  public static String HSB(float H, float S, float B) {
    return H+" "+S+" "+B;
  }

  private static final char[] hd = {'0','1','2','3','4','5','6','7',
                                    '8','9','a','b','c','d','e','f'};

  /** 
   * Constructs a color given 3 RGB values.
   * All values should be between 0 and 1.
   * @param R red component
   * @param G green component
   * @param B blue component
   * @return string representation of color
   */
  public static String RGB(float R, float G, float B) {
    byte r = (byte)(255*R);
    byte g = (byte)(255*G);
    byte b = (byte)(255*B);
    return "#"+hd[r>>4]+hd[r&15]+hd[g>>4]+hd[g&15]+hd[b>>4]+hd[b&15];
  }

  //////////////////////
  // Style utilities
  //////////////////////

  /** 
   * Combines multiple styles
   * @param style1 first style
   * @param style2 second style
   * @return combination of given styles
   */
  public static String combine(String style1, String style2) {
    return style1+","+style2;
  }
}

