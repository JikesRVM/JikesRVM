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



