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
 * Constants used in the dependence graph
 */
public interface OPT_DepGraphConstants {

  // The dependence kind info is one of the above types
  // WARNING: it can use only the lower 28 bits 
  // (see OPT_SpaceEffGraphEdge.java)
  int REG_TRUE     = 0x00001;
  int REG_ANTI     = 0x00002;
  int REG_OUTPUT   = 0x00004;
  int MEM_TRUE     = 0x00008;
  int MEM_ANTI     = 0x00010;
  int MEM_OUTPUT   = 0x00020;
  int CONTROL      = 0x00040;
  int EXCEPTION_E  = 0x00080;
  int EXCEPTION_MS = 0x00100;
  int EXCEPTION_ML = 0x00200;
  int EXCEPTION_R  = 0x00400;
  int SEQ          = 0x00800;
  int GUARD_TRUE   = 0x01000;
  int GUARD_ANTI   = 0x02000;
  int GUARD_OUTPUT = 0x04000;
  int MEM_READS_KILL = 0x08000;
  int REG_MAY_DEF  = 0x10000;

  /**
   * Compact redundant edges?
   * Set to false if redundant edges are desired.
   */
  boolean COMPACT = true;
}
