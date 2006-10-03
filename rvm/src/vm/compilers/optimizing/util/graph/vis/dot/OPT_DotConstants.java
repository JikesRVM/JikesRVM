/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
//OPT_DotConstants.java
//$Id$
package com.ibm.JikesRVM.opt;

/**
 * A set of constants for use in Dot routines.
 *
 * @author Igor Pechtchanski
 * @see OPT_Dot
 * @see OPT_DotUtils
 * @see OPT_DotGraph
 * @see OPT_DotNode
 * @see OPT_DotEdge
 */

public interface OPT_DotConstants {
  //////////////////////
  // Style constants
  //////////////////////

  /** Solid (default) */
  public static final String SOLID = "solid";
  /** Bold */
  public static final String BOLD = "bold";
  /** Dashed */
  public static final String DASHED = "dashed";
  /** Dotted */
  public static final String DOTTED = "dotted";
  /** Invisible */
  public static final String INVIS = "invis";
  /** Filled */
  public static final String FILLED = "filled";

  //////////////////////
  // Color constants
  //////////////////////

  /** Black (default) */
  public static final String BLACK = "black";
  /** White */
  public static final String WHITE = "white";
  /** Red */
  public static final String RED = "red";
  /** Blue */
  public static final String BLUE = "blue";
  /** Green */
  public static final String GREEN = "green";
  /** Yellow */
  public static final String YELLOW = "yellow";

  /**
   * Special value for numeric types indicating default
   */
  public static final int NONE = -1;
}

