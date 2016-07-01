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
package org.jikesrvm.compilers.opt.ir;

import java.util.Enumeration;

import org.jikesrvm.compilers.opt.driver.CFGVisualization;

public class IRDumpTools {

  /**
   * Prints the IR, optionally including the CFG
   *
   * @param ir the IR to print
   * @param tag a String to use in the start/end message of the IR dump
   */
  public static void dumpIR(IR ir, String tag) {
    if (ir.options.PRINT_VISUALIZATION) {
      try {
        CFGVisualization visualization = new CFGVisualization(ir, tag);
        visualization.visualizeCFG();
        return;
      } catch (Exception e) {
        System.out.println("Error generating IR visualization: ");
        e.printStackTrace(System.out);
        System.out.println("Generating text dump instead ...");
      }
    }
    IRDumpTools.dumpIR(ir, tag, false);
  }

  /**
   * Prints the IR, optionally including the CFG
   *
   * @param ir the IR to print
   * @param forceCFG should the CFG be printed, independent of the value of ir.options.PRINT_CFG?
   * @param tag a String to use in the start/end message of the IR dump
   */
  public static void dumpIR(IR ir, String tag, boolean forceCFG) {
    System.out.println("********* START OF IR DUMP  " + tag + "   FOR " + ir.method);
    ir.printInstructions();
    if (forceCFG || ir.options.PRINT_CFG) {
      ir.cfg.printDepthFirst();
    }
    System.out.println("*********   END OF IR DUMP  " + tag + "   FOR " + ir.method);
  }

  public static void dumpCFG(IR ir) {
    for (Enumeration<BasicBlock> allBB = ir.getBasicBlocks(); allBB.hasMoreElements();) {
      BasicBlock curBB = allBB.nextElement();
      curBB.printExtended();
    }
  }

}
