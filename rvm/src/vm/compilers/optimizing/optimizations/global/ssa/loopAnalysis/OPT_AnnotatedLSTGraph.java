/*
 * (C) Copyright Ian Rogers, The University of Manchester 2003 - 2005
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.opt.ir.OPT_IR;
import com.ibm.JikesRVM.opt.OPT_LSTGraph;
import com.ibm.JikesRVM.VM;
import java.util.Vector;
import java.util.Enumeration;

/**
 * Extends the functionality of a {@link OPT_LSTGraph} so that it comprises
 * {@link OPT_AnnotatedLSTNode}s which have extra information in them.
 *
 * @see OPT_LSTGraph
 * @see OPT_AnnotatedLSTNode
 *
 * @author Ian Rogers
 */
public class OPT_AnnotatedLSTGraph extends OPT_LSTGraph {
  /**
   * Debug messages?
   */
  private static final boolean DEBUG = false;

  /**
   * Debug helper
   * @param message debug message
   */
  private static void report (String message){
    if(DEBUG) {
      VM.sysWrite(message);
    }
  }

  /**
   * The main entry point
   * @param ir the IR to process
   */
  public static void perform(OPT_IR ir) {
	 if (DEBUG) {
		report("Creating an AnnotatedLSTGraph for " + ir.method);    
	 }
    ir.HIRInfo.LoopStructureTree = new OPT_AnnotatedLSTGraph(ir, ir.HIRInfo.LoopStructureTree);
	 if (DEBUG) {
		report(ir.HIRInfo.LoopStructureTree.toString());
	 }
  }

  /**
   * Constructor
   *
   * @param ir    The containing IR
   * @param graph The {@link OPT_LSTGraph} to convert into an annotated graph
   */
  OPT_AnnotatedLSTGraph (OPT_IR ir, OPT_LSTGraph graph) {
    super(graph);
    rootNode = new OPT_AnnotatedLSTNode(ir, rootNode);
  }
}
