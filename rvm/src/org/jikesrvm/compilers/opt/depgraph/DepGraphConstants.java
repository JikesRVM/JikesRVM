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
package org.jikesrvm.compilers.opt.depgraph;

/**
 * Constants used in the dependence graph
 */
public final class DepGraphConstants {

  // The dependence kind info is one of the above types
  // WARNING: it can use only the lower 28 bits
  // (see SpaceEffGraphEdge.java)
  public static final int REG_TRUE = 0x00001;
  public static final int REG_ANTI = 0x00002;
  public static final int REG_OUTPUT = 0x00004;
  public static final int MEM_TRUE = 0x00008;
  public static final int MEM_ANTI = 0x00010;
  public static final int MEM_OUTPUT = 0x00020;
  public static final int CONTROL = 0x00040;
  public static final int EXCEPTION_E = 0x00080;
  public static final int EXCEPTION_MS = 0x00100;
  public static final int EXCEPTION_ML = 0x00200;
  public static final int EXCEPTION_R = 0x00400;
  public static final int SEQ = 0x00800;
  public static final int GUARD_TRUE = 0x01000;
  public static final int GUARD_ANTI = 0x02000;
  public static final int GUARD_OUTPUT = 0x04000;
  public static final int MEM_READS_KILL = 0x08000;
  public static final int REG_MAY_DEF = 0x10000;

  /**
   * Compact redundant edges?
   * Set to {@code false} if redundant edges are desired.
   */
  public static final boolean COMPACT = true;

  private DepGraphConstants() {
    // prevent instantiation
  }

}
