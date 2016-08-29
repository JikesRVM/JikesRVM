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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Enumeration;

import org.jikesrvm.classloader.RVMMethod;

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

    if (ir.options.PRINT_PHASES_TO_FILES) {
      PrintStream fileOut = null;
      try {
        String prefix = ir.getIdForCurrentPhase() + "-";
        String suffix = ".irdump";
        String fileName = determineFileName(ir, tag, prefix, suffix);
        File f = new File(fileName);
        FileOutputStream fos = new FileOutputStream(f);
        fileOut = new PrintStream(fos);
        dumpIR(fileOut, ir, tag, false);
      } catch (IOException e) {
        System.out.println("Error dumping IR to file: ");
        e.printStackTrace(System.out);
      } finally {
        if (fileOut != null) fileOut.close();
      }

    }
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

  public static void dumpIR(PrintStream out, IR ir, String tag, boolean forceCFG) {
    out.println("********* START OF IR DUMP  " + tag + "   FOR " + ir.method);
    ir.printInstructionsToStream(out);
    if (forceCFG || ir.options.PRINT_CFG) {
      ir.cfg.printDepthFirstToStream(out);
    }
    out.println("*********   END OF IR DUMP  " + tag + "   FOR " + ir.method);
  }

  public static void dumpCFG(IR ir) {
    for (Enumeration<BasicBlock> allBB = ir.getBasicBlocks(); allBB.hasMoreElements();) {
      BasicBlock curBB = allBB.nextElement();
      curBB.printExtended();
    }
  }

  static String determineFileName(IR ir, String tag, String suffix) {
    return determineFileName(ir, tag, "", suffix);
  }

  static String determineFileName(IR ir, String tag, String prefix, String suffix) {
    RVMMethod method = ir.getMethod();
    return prefix + tag.replace(' ', '-').replace('/', '-') + "_" +
        method.getDeclaringClass().getDescriptor().classNameFromDescriptor() + "_" +
        method.getName() + "_" +
        "opt" + ir.options.getOptLevel() + "-" + System.currentTimeMillis() + suffix;
  }

}
