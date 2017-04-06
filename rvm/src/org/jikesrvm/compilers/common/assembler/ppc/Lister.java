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
package org.jikesrvm.compilers.common.assembler.ppc;

import static org.jikesrvm.ppc.RegisterConstants.LG_INSTRUCTION_WIDTH;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

import org.jikesrvm.VM;
import org.jikesrvm.compilers.common.CodeArray;
import org.jikesrvm.compilers.common.assembler.AbstractLister;
import org.jikesrvm.ppc.Disassembler;
import org.jikesrvm.util.Services;

/**
 * Provides methods to produce listings for PPC machine code.
 * <p>
 * It can produce listings from arbitrary PPC code, provided that the code
 * can be be disassembled by the
 * {@link org.jikesrvm.ppc.Disassembler Jikes RVM PPC disassembler}.
 */
public final class Lister extends AbstractLister {

  private static class Line {

    /**
     * The byte index (not the instruction index) for the current line.
     * This is used for ordering.
     */
    private final int byteIndex;

    /**
     * the line's contents, without a newline
     */
    private final String line;

    Line(int index, String line) {
      this.byteIndex = index;
      this.line = line;
    }

  }

  /**
   * An estimate of the maximum size of a line printed by the lister.
   * This estimate is based for lines for machine code and not comments.
   */
  private static final int DEFAULT_LINE_SIZE = 50;

  private List<Line> lines;
  private final int[] bytecodeMap;

  /**
   * @param bytecodeMap a mapping of bytecode index to machine code index.
   *  This must be non-{@code null} if and only if the client wants to call
   *  {@link #noteBytecode(int, String)}.
   */
  public Lister(int[] bytecodeMap) {
    lines = new LinkedList<Line>();
    this.bytecodeMap = bytecodeMap;
  }

  /**
   * Notes a bytecode, using the internal bytecode map that was provided
   * in the constructor.
   *
   * @param i bytecode index
   * @param bcode string for bytecode
   */
  @Override
  public void noteBytecode(int i, String bcode) {
    if (VM.VerifyAssertions) VM._assert(bytecodeMap != null,
        "No bytecode map present but bytecodes are used");

    int byteIndex = bytecodeMap[i] << LG_INSTRUCTION_WIDTH;
    String s1 = Services.getHexString(byteIndex, true);
    String line = s1 + ": [" + i + "] " + bcode;
    lines.add(new Line(byteIndex, line));
  }

  public void endAndPrintListing() {
    final int sizeEstimate = lines.size() * DEFAULT_LINE_SIZE;
    StringBuilder listing = new StringBuilder(sizeEstimate);
    for (Line l : lines) {
      listing.append(l.line);
      listing.append(NEWLINE);
    }
    VM.sysWrite(listing.toString());
  }

  private abstract static class CodeAccessor {

    abstract int getInstruction(int index);

    abstract int length();
  }

  private static class CodeArrayAccessor extends CodeAccessor {

    private final CodeArray codeArray;

    CodeArrayAccessor(CodeArray codeArray) {
      this.codeArray = codeArray;
    }

    @Override
    int getInstruction(int index) {
      return codeArray.get(index);
    }

    @Override
    int length() {
      return codeArray.length();
    }

  }

  private static class IntArrayAccessor extends CodeAccessor {

    private final int[] machineCodes;
    private final int machineCodeLength;

    IntArrayAccessor(int[] machineCodes, int machineCodeLength) {
      this.machineCodes = machineCodes;
      this.machineCodeLength = machineCodeLength;
    }

    @Override
    int getInstruction(int index) {
      return machineCodes[index];
    }

    @Override
    int length() {
      return machineCodeLength;
    }

  }


  public void addLinesForCode(int[] machineCodes,
      int machineCodeLength) {
    addLinesForCode(new IntArrayAccessor(machineCodes, machineCodeLength));
  }

  public void addLinesForCode(CodeArray machinecodes) {
    addLinesForCode(new CodeArrayAccessor(machinecodes));
  }

  private void addLinesForCode(CodeAccessor code) {
    StringBuilder newLine = new StringBuilder(DEFAULT_LINE_SIZE);
    int codeLength = code.length();

    LinkedList<Line> linesForCode = new LinkedList<Line>();
    for (int instIndex = 0; instIndex < codeLength; instIndex++) {
      int byteIndex = instIndex << LG_INSTRUCTION_WIDTH;
      Line l = createLineFromCode(code.getInstruction(instIndex),
          newLine, byteIndex);
        linesForCode.add(l);
        newLine.setLength(0);
    }

    if (lines.size() == 0) {
      lines = linesForCode;
      return;
    }

    Iterator<Line> oldLines = lines.iterator();
    ListIterator<Line> codeLines = linesForCode.listIterator();
    Line lastReturnedOldLine = oldLines.next();
    Line lastReturnedCodeLine = null;

    while (lastReturnedOldLine.byteIndex <= 0) {
      codeLines.add(lastReturnedOldLine);
      lastReturnedOldLine = oldLines.next();
    }

    while (codeLines.hasNext()) {
      lastReturnedCodeLine = codeLines.next();
      if (lastReturnedOldLine.byteIndex <= lastReturnedCodeLine.byteIndex) {
        codeLines.previous();
        codeLines.add(lastReturnedOldLine);
        // no call to next() because there might be multiple lines
        // to add at a given position
        if (oldLines.hasNext()) {
          lastReturnedOldLine = oldLines.next();
        } else {
          break;
        }
      }
    }

    while (oldLines.hasNext()) {
      codeLines.add(oldLines.next());
    }

    lines = linesForCode;
  }

  private Line createLineFromCode(int inst, StringBuilder newLine, int byteIndex) {
    newLine.append(Services.getHexString(byteIndex, true));
    newLine.append(" : ");
    newLine.append(Services.getHexString(inst, false));
    newLine.append("  ");
    newLine.append(Disassembler.disasm(inst, byteIndex));

    Line l = new Line(byteIndex, newLine.toString());
    newLine.setLength(0);
    return l;
  }

}
