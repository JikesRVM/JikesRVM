/*
 * This file is part of the Jikes RVM project (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.opt.ir.*;

/**
 * This class helps manage register preferences for coalescing and
 * register allocation.
 *
 * @author Stephen Fink
 */
abstract class OPT_GenericRegisterPreferences {
  /**
   * The main backing data structure;
   */
  OPT_CoalesceGraph graph = new OPT_CoalesceGraph();

  /**
   * Add a affinity of weight w between two registers.
   */
  void addAffinity(int w, OPT_Register r1, OPT_Register r2) {
    graph.addAffinity(w,r1,r2);
  }

  /** 
   * Set up register preferences for an IR. This is machine-dependent.
   */
  abstract void initialize(OPT_IR ir);

  /**
   * Return the backing graph holding the preferences.
   */
  OPT_CoalesceGraph getGraph() {
    return graph;
  }
}
