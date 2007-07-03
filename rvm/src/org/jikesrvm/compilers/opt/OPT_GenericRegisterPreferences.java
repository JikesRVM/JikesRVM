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

import org.jikesrvm.compilers.opt.ir.OPT_IR;
import org.jikesrvm.compilers.opt.ir.OPT_Register;

/**
 * This class helps manage register preferences for coalescing and
 * register allocation.
 */
public abstract class OPT_GenericRegisterPreferences {
  /**
   * The main backing data structure;
   */
  OPT_CoalesceGraph graph = new OPT_CoalesceGraph();

  /**
   * Add a affinity of weight w between two registers.
   */
  protected void addAffinity(int w, OPT_Register r1, OPT_Register r2) {
    graph.addAffinity(w, r1, r2);
  }

  /**
   * Set up register preferences for an IR. This is machine-dependent.
   */
  public abstract void initialize(OPT_IR ir);

  /**
   * Return the backing graph holding the preferences.
   */
  public OPT_CoalesceGraph getGraph() {
    return graph;
  }
}
