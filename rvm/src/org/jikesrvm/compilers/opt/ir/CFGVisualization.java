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

import static org.jikesrvm.compilers.opt.ir.IRDumpTools.determineFileName;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Enumeration;

import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.compilers.opt.inlining.InlineSequence;
import org.jikesrvm.compilers.opt.ir.operand.MethodOperand;
import org.jikesrvm.util.Pair;

/**
 * Prints a CFG visualization to a file using the dot output format. Further processing
 * can be done using tools such as <a href="http://graphviz.org">Graphviz</a>
 * or graphical frontends such as <a href="http://zvtm.sourceforge.net/zgrviewer.html">ZGRviewer</a>.
 */
public class CFGVisualization {

  private static final String CENTERED = "\\n";
  private static final String LEFT_JUSTIFIED = "\\l";

  @SuppressWarnings("unused")
  // just here as documentation to make it clear that 0 is taken as a value
  private static final byte UNVISITED = 0;

  private static final byte HIGHLIGHTED = 1;
  private static final byte GRAY = 2;
  private static final byte BLACK = 3;

  private final BufferedWriter out;
  private final IR ir;

  private final byte[] nodeToColour;

  public CFGVisualization(IR ir, String tag) throws SecurityException, IOException {
    String fileName = determineFileName(ir, tag, ".graph");
    String dir = ir.options.VISUALIZE_IR_DIRECTORY;
    if (dir.trim().isEmpty()) {
      dir = System.getProperty("user.dir");
    }
    File directory = new File(dir);
    File file = new File(directory, fileName);
    this.out = new BufferedWriter(new FileWriter(file));
    this.ir = ir;
    nodeToColour = new byte[ir.cfg.numberOfNodes()];
  }

  public void visualizeCFG() throws IOException {
    out.write("digraph G {\n node [shape=box];\n");
    out.write("ENTRY" + "[ label=\"" + "ENTRY" + CENTERED + "\n");
    BasicBlock entry = ir.cfg.entry();
    out.write(enumerateAndFormatInstructions(entry));
    out.write("\"" + formatHighlighting(entry) + "];\n");
    dfsCFG(entry, ir);
    out.write("}");
    out.close();
  }

  protected void dfsCFG(BasicBlock bb, IR ir) throws IOException {
    Enumeration<BasicBlock> successors = bb.getOutNodes();
    nodeToColour[bb.getIndex()] = GRAY;
    while (successors.hasMoreElements()) {
      BasicBlock succBB = successors.nextElement();
      StringWrapper returnObj = setDirectionalEdges(succBB, bb);
      out.write(returnObj.getStr());
      String to = returnObj.getTo();
      boolean toNotEmpty = !to.isEmpty();
      if (toNotEmpty) {
        out.write(to + "[ label=\"" + to + CENTERED);
      }
      out.write(enumerateAndFormatInstructions(succBB));
      if (toNotEmpty) {
        out.write("\"" + formatHighlighting(succBB) + "];\n");
      }
      byte colour = nodeToColour[succBB.getIndex()];
      if (colour != GRAY && colour != BLACK) {
        dfsCFG(succBB,ir);
      }
    }
    nodeToColour[bb.getIndex()] = BLACK;
  }

  /**
   * Generates control-flow edge descriptions for basic blocks.
   * @param succBB successor basic block
   * @param bb current basic block
   * @return a pair of strings
   */
  protected StringWrapper setDirectionalEdges(BasicBlock succBB, BasicBlock bb) {
    String to = null;
    String str = null;
    int bbNumber = bb.getNumber();
    BasicBlock entry = ir.cfg.entry();
    boolean succBBIsExit = succBB.isExit();
    boolean bbIsEntry = bb == entry;

    if (bbIsEntry || succBBIsExit) {
      if (bbIsEntry && !succBBIsExit) {
        to = "BB" + succBB.getNumber();
        str = "ENTRY" + "->" + "{" + to + "}" + ";\n";
      } else if (!bbIsEntry && succBBIsExit) {
        to = "EXIT";
        str = "BB" + bbNumber + "->" + "{" + to + "}" + ";\n";
      } else { // bbIsEntry && succBBisExit
        to = "EXIT";
        str = "ENTRY" + "->" + "{" + to + "}" + ";\n";
      }
    } else {
      to = "BB" + succBB.getNumber();
      if (succBB == entry) {
        to = "ENTRY";
      }
      str = "BB" + bbNumber + "->" + "{" + to + "}" + ";\n";
    }
    return new StringWrapper(to, str);
  }

  protected String enumerateAndFormatInstructions(BasicBlock succBB) {
    StringBuilder next = new StringBuilder();
    for (Enumeration<Instruction> e = succBB.forwardInstrEnumerator(); e.hasMoreElements();) {
      Instruction inst = e.nextElement();
      int lineNumber = 0;
      String s = formatInstruction(inst);
      s = s.replaceAll("\n", " ");
      s = s.replaceAll("\"", "\\\\\"");
      InlineSequence position = inst.position();
      int bytecodeIndex = inst.getBytecodeIndex();
      if (position != null) {
        lineNumber = position.getMethod().getLineNumberForBCIndex(bytecodeIndex);
      }
      next.append(s);
      next.append(" ");
      next.append(bytecodeIndex);
      next.append(",");
      next.append(lineNumber);
      next.append(LEFT_JUSTIFIED);
      next.append("\n");
    }
    return next.toString();
  }

  /**
   * Formats instructions. Currently only calls are specially formatted,
   * everything else is just printed.
   *
   * @param inst an instruction
   * @return the String for the instruction
   */
  protected String formatInstruction(Instruction inst) {
    if (Call.conforms(inst)) {
      return "CALL " + formatCall(inst);
    } else {
      return inst.toString();
    }
  }

  protected String formatCall(Instruction inst) {
    StringBuilder buf = new StringBuilder();
    MethodOperand mop = Call.getMethod(inst);
    if (mop != null && mop.hasTarget()) {
      RVMMethod method = mop.getTarget();
      buf.append(method.getDeclaringClass());
      buf.append(":");
      buf.append(method.getName());
      buf.append(":");
      buf.append(method.getDescriptor());
      buf.append(":");
      int params = Call.getNumberOfParams(inst);
      for (int i = 1; i <= params; i++) {
        buf.append(Call.getParam(inst, i - 1));
        if (i < params) {
          buf.append(", ");
        } else {
          buf.append("; ");
        }
      }
    }
    return buf.toString();
  }

  public void markBlockAsHighlighted(BasicBlock bb) {
    if (bb != null) {
      nodeToColour[bb.getIndex()] = HIGHLIGHTED;
    }
  }

  protected String formatHighlighting(BasicBlock bb) {
    if (nodeToColour[bb.getIndex()] == HIGHLIGHTED) {
      return " style=\"filled\", color=\"lightblue\"";
    } else {
      return "";
    }
  }

  protected static final class StringWrapper extends Pair<String, String> {

    public StringWrapper(String to, String str) {
      super(to, str);
    }

    public String getTo() {
      return first;
    }

    public String getStr() {
      return second;
    }

  }

}
