/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.compilers.opt.ssa;

import org.jikesrvm.compilers.opt.util.SpaceEffGraphEdge;

/**
 * This class implements an edge in the value graph used in global value
 * numbering
 * ala Alpern, Wegman and Zadeck.  See Muchnick p.348 for a nice
 * discussion.
 */
final class ValueGraphEdge extends SpaceEffGraphEdge {

  ValueGraphEdge(ValueGraphVertex src, ValueGraphVertex target) {
    super(src, target);
  }

  @Override
  public String toString() {
    ValueGraphVertex src = (ValueGraphVertex) fromNode();
    ValueGraphVertex dest = (ValueGraphVertex) toNode();
    return src.getName() + " --> " + dest.getName();
  }
}
