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
package com.ibm.jikesrvm.opt;

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
  String SOLID = "solid";
  /** Bold */
  String BOLD = "bold";
  /** Dashed */
  String DASHED = "dashed";
  /** Dotted */
  String DOTTED = "dotted";
  /** Invisible */
  String INVIS = "invis";
  /** Filled */
  String FILLED = "filled";

  //////////////////////
  // Color constants
  //////////////////////

  /** Black (default) */
  String BLACK = "black";
  /** White */
  String WHITE = "white";
  /** Red */
  String RED = "red";
  /** Blue */
  String BLUE = "blue";
  /** Green */
  String GREEN = "green";
  /** Yellow */
  String YELLOW = "yellow";

  /**
   * Special value for numeric types indicating default
   */
  int NONE = -1;
}

