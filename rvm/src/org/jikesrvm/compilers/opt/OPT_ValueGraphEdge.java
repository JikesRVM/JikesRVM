/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.compilers.opt;

/**
 * This class implements an edge in the value graph used in global value
 * numbering
 * ala Alpern, Wegman and Zadeck.  See Muchnick p.348 for a nice
 * discussion.
 */
final class OPT_ValueGraphEdge extends OPT_SpaceEffGraphEdge {

  OPT_ValueGraphEdge(OPT_ValueGraphVertex src, OPT_ValueGraphVertex target) {
    super(src, target);
  }

  public String toString() {
    OPT_ValueGraphVertex src = (OPT_ValueGraphVertex) fromNode();
    OPT_ValueGraphVertex dest = (OPT_ValueGraphVertex) toNode();
    return src.getName() + " --> " + dest.getName();
  }
}



