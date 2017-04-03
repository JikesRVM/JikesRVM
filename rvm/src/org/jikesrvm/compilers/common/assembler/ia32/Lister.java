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
package org.jikesrvm.compilers.common.assembler.ia32;

import java.util.LinkedList;
import java.util.List;

import org.jikesrvm.VM;
import org.jikesrvm.architecture.MachineRegister;
import org.jikesrvm.compilers.common.assembler.AbstractLister;
import org.vmmagic.unboxed.Offset;
import org.vmmagic.unboxed.Address;
import org.vmmagic.pragma.Pure;

/**
 * Provides methods to produce listings for IA32 machine code.
 * <p>
 * This class is intended to be used by the IA32 assemblers. Every time
 * the IA32 assembler emits an instruction, it calls the lister to produce
 * the listing for the instruction. The main drawback of this approach is
 * that code patching isn't supported: for every instruction that's being
 * patched (e.g. jumps, moves and NOPs), the listing will show the original,
 * unpatched version.
 */
public final class Lister extends AbstractLister {

  private static class Line {

    private final StringBuilder lineContent = new StringBuilder(DEFAULT_LINE_SIZE);

    private void addToLine(String s) {
      lineContent.append(s);
    }

    private void addNewline() {
      lineContent.append(NEWLINE);
    }

    public String finish() {
      addNewline();
      return lineContent.toString();
    }

  }

  /** an estimate of the maximum size of a line printed by the lister */
  private static final int DEFAULT_LINE_SIZE = 80;

  private static final int PREFIX_AREA_SIZE = 8;
  private static final int OP_AREA_SIZE = 9;
  private static final int SOURCE_AREA_SIZE = 16;
  private static final int DEST_AREA_SIZE = 16;

  private final Assembler asm;

  private enum Prefix {LOCK, LIKELY, UNLIKELY};

  private Prefix prefix;

  private final List<Line> lines;

  private Line currentLine;

  public Lister(Assembler asm) {
    this.asm = asm;
    this.lines = new LinkedList<Lister.Line>();
    this.currentLine = new Line();
  }

  public void lockPrefix() {
    prefix = Prefix.LOCK;
  }

  public void branchLikelyPrefix() {
    prefix = Prefix.LIKELY;
  }

  public void branchUnlikelyPrefix() {
    prefix = Prefix.UNLIKELY;
  }

  public void OP(int i, String op) {
    i = begin(i, op);
    writeString(right("", DEST_AREA_SIZE));
    writeString(right("", SOURCE_AREA_SIZE));
    end(i);
  }

  public void I(int i, String op, int n) {
    i = begin(i, op);
    writeString(right(decimal(n) + " ", DEST_AREA_SIZE));
    writeString(right("", SOURCE_AREA_SIZE));
    end(i);
  }

  public void R(int i, String op, MachineRegister R0) {
    i = begin(i, op);
    writeString(right(R0 + " ", DEST_AREA_SIZE));
    writeString(right("", SOURCE_AREA_SIZE));
    end(i);
  }

  public void RD(int i, String op, MachineRegister R0, Offset d) {
    i = begin(i, op);
    writeString(right(decimal(d) + "[" + R0 + "]", DEST_AREA_SIZE));
    writeString(right("", SOURCE_AREA_SIZE));
    end(i);
  }

  public void RI(int i, String op, MachineRegister R0, long n) {
    i = begin(i, op);
    writeString(right(R0 + " ", DEST_AREA_SIZE));
    writeString(right(decimal(n) + " ", SOURCE_AREA_SIZE));
    end(i);
  }

  public void RDI(int i, String op, MachineRegister R0, Offset d, long n) {
    i = begin(i, op);
    writeString(right(decimal(d) + "[" + R0 + "]", DEST_AREA_SIZE));
    writeString(right(decimal(n) + " ", SOURCE_AREA_SIZE));
    end(i);
  }

  public void RNI(int i, String op, MachineRegister R0, long n) {
    i = begin(i, op);
    writeString(right("[" + R0 + "]", DEST_AREA_SIZE));
    writeString(right(decimal(n) + " ", SOURCE_AREA_SIZE));
    end(i);
  }

  public void RR(int i, String op, MachineRegister R0, MachineRegister R1) {
    i = begin(i, op);
    writeString(right(R0 + " ", DEST_AREA_SIZE));
    writeString(right(R1 + " ", SOURCE_AREA_SIZE));
    end(i);
  }

  public void RDR(int i, String op, MachineRegister R0, Offset d, MachineRegister R1) {
    i = begin(i, op);
    writeString(right(decimal(d) + "[" + R0 + "]", DEST_AREA_SIZE));
    writeString(right(R1 + " ", SOURCE_AREA_SIZE));
    end(i);
  }

  public void RDRI(int i, String op, MachineRegister R0, Offset d, MachineRegister R1, int imm) {
    i = begin(i, op);
    writeString(right(decimal(d) + "[" + R0 + "]", DEST_AREA_SIZE));
    writeString(right(R1 + " ", SOURCE_AREA_SIZE));
    writeString(right(decimal(imm), SOURCE_AREA_SIZE));
    end(i);
  }

  public void RDRR(int i, String op, MachineRegister R0, Offset d, MachineRegister R1, MachineRegister R2) {
    i = begin(i, op);
    writeString(right(decimal(d) + "[" + R0 + "]", DEST_AREA_SIZE));
    writeString(right(R1 + " ", SOURCE_AREA_SIZE));
    writeString(right(R2 + " ", SOURCE_AREA_SIZE));
    end(i);
  }

  public void RRD(int i, String op, MachineRegister R0, MachineRegister R1, Offset d) {
    i = begin(i, op);
    writeString(right(R0 + " ", DEST_AREA_SIZE));
    writeString(right(decimal(d) + "[" + R1 + "]", SOURCE_AREA_SIZE));
    end(i);
  }

  public void RNR(int i, String op, MachineRegister R0, MachineRegister R1) {
    i = begin(i, op);
    writeString(right("[" + R0 + "]", DEST_AREA_SIZE));
    writeString(right(R1 + " ", SOURCE_AREA_SIZE));
    end(i);
  }

  public void RN(int i, String op, MachineRegister R0) {
    i = begin(i, op);
    writeString(right("[" + R0 + "]", DEST_AREA_SIZE));
    writeString(right(" ", SOURCE_AREA_SIZE));
    end(i);
  }

  public void RRN(int i, String op, MachineRegister R0, MachineRegister R1) {
    i = begin(i, op);
    writeString(right(R0 + " ", DEST_AREA_SIZE));
    writeString(right("[" + R1 + "]", SOURCE_AREA_SIZE));
    end(i);
  }

  public void RXD(int i, String op, MachineRegister R0, MachineRegister X, short s, Offset d) {
    i = begin(i, op);
    writeString(right("[" + decimal(d) + "+" + R0 + "+" + X + "<<" + decimal(s) + "]",
                      DEST_AREA_SIZE));
    writeString(right("", SOURCE_AREA_SIZE));
    end(i);
  }

  public void RXDI(int i, String op, MachineRegister R0, MachineRegister X, short s, Offset d, long n) {
    i = begin(i, op);
    writeString(right("[" + decimal(d) + "+" + R0 + "+" + X + "<<" + decimal(s) + "]",
                      DEST_AREA_SIZE));
    writeString(right(decimal(n), SOURCE_AREA_SIZE));
    end(i);
  }

  public void RFD(int i, String op, MachineRegister X, short s, Offset d) {
    i = begin(i, op);
    writeString(right("[" + decimal(d) + "+" + X + "<<" + decimal(s) + "]", DEST_AREA_SIZE));
    writeString(right("", SOURCE_AREA_SIZE));
    end(i);
  }

  public void RA(int i, String op, Address d) {
    i = begin(i, op);
    writeString(right("[" + hex(d) + "]", DEST_AREA_SIZE));
    writeString(right("", SOURCE_AREA_SIZE));
    end(i);
  }

  public void RFDI(int i, String op, MachineRegister X, short s, Offset d, long n) {
    i = begin(i, op);
    writeString(right("[" + decimal(d) + "+" + X + "<<" + decimal(s) + "]", DEST_AREA_SIZE));
    writeString(right(decimal(n), SOURCE_AREA_SIZE));
    end(i);
  }

  public void RAI(int i, String op, Address d, long n) {
    i = begin(i, op);
    writeString(right("[" + hex(d) + "]", DEST_AREA_SIZE));
    writeString(right(decimal(n), SOURCE_AREA_SIZE));
    end(i);
  }

  public void RRR(int i, String op, MachineRegister R0, MachineRegister R1, MachineRegister R2) {
    i = begin(i, op);
    writeString(right(R0 + " ", DEST_AREA_SIZE));
    writeString(right(R1 + " ", SOURCE_AREA_SIZE));
    writeString(right(R2 + " ", SOURCE_AREA_SIZE));
    end(i);
  }

  public void RNRI(int i, String op, MachineRegister R0, MachineRegister R1, int imm) {
    i = begin(i, op);
    writeString(right("[" + R0 + "] ", DEST_AREA_SIZE));
    writeString(right(R1 + " ", SOURCE_AREA_SIZE));
    writeString(right(decimal(imm), SOURCE_AREA_SIZE));
    end(i);
  }

  public void RNRR(int i, String op, MachineRegister R0, MachineRegister R1, MachineRegister R2) {
    i = begin(i, op);
    writeString(right("[" + R0 + "] ", DEST_AREA_SIZE));
    writeString(right(R1 + " ", SOURCE_AREA_SIZE));
    writeString(right(R2 + " ", SOURCE_AREA_SIZE));
    end(i);
  }

  public void RRI(int i, String op, MachineRegister R0, MachineRegister R1, int imm) {
    i = begin(i, op);
    writeString(right(R0 + " ", DEST_AREA_SIZE));
    writeString(right(R1 + " ", SOURCE_AREA_SIZE));
    writeString(right(decimal(imm), SOURCE_AREA_SIZE));
    end(i);
  }

  public void RRXD(int i, String op, MachineRegister R0, MachineRegister R1, MachineRegister X, short s, Offset d) {
    i = begin(i, op);
    writeString(right(R0 + " ", DEST_AREA_SIZE));
    writeString(right("[" + decimal(d) + "+" + R1 + "+" + X + "<<" + decimal(s) + "]",
                      SOURCE_AREA_SIZE));
    end(i);
  }

  public void RXDR(int i, String op, MachineRegister R0, MachineRegister X, short s, Offset d, MachineRegister R1) {
    i = begin(i, op);
    writeString(right("[" + decimal(d) + "+" + R0 + "+" + X + "<<" + decimal(s) + "]",
                      DEST_AREA_SIZE));
    writeString(right(R1 + " ", SOURCE_AREA_SIZE));
    end(i);
  }

  public void RXDRI(int i, String op, MachineRegister R0, MachineRegister X, short s, Offset d, MachineRegister R1, int imm) {
    i = begin(i, op);
    writeString(right("[" + decimal(d) + "+" + R0 + "+" + X + "<<" + decimal(s) + "]",
                      DEST_AREA_SIZE));
    writeString(right(R1 + " ", SOURCE_AREA_SIZE));
    writeString(right(decimal(imm), SOURCE_AREA_SIZE));
    end(i);
  }

  public void RXDRR(int i, String op, MachineRegister R0, MachineRegister X, short s, Offset d, MachineRegister R1, MachineRegister R2) {
    i = begin(i, op);
    writeString(right("[" + decimal(d) + "+" + R0 + "+" + X + "<<" + decimal(s) + "]",
                      SOURCE_AREA_SIZE));
    writeString(right(R1 + " ", DEST_AREA_SIZE));
    writeString(right(R2 + " ", SOURCE_AREA_SIZE));
    end(i);
  }

  public void RRFD(int i, String op, MachineRegister R0, MachineRegister X, short s, Offset d) {
    i = begin(i, op);
    writeString(right(R0 + " ", DEST_AREA_SIZE));
    writeString(right("[" + decimal(d) + "+" + X + "<<" + decimal(s) + "]", SOURCE_AREA_SIZE));
    end(i);
  }

  public void RFDR(int i, String op, MachineRegister X, short s, Offset d, MachineRegister R0) {
    i = begin(i, op);
    writeString(right(R0 + " ", DEST_AREA_SIZE));
    writeString(right("[" + decimal(d) + "+" + X + "<<" + decimal(s) + "]", SOURCE_AREA_SIZE));
    end(i);
  }

  public void RFDRI(int i, String op, MachineRegister X, short s, Offset d, MachineRegister R0, int imm) {
    i = begin(i, op);
    writeString(right(R0 + " ", DEST_AREA_SIZE));
    writeString(right("[" + decimal(d) + "+" + X + "<<" + decimal(s) + "]", SOURCE_AREA_SIZE));
    writeString(right(decimal(imm), SOURCE_AREA_SIZE));
    end(i);
  }

  public void RFDRR(int i, String op, MachineRegister X, short s, Offset d, MachineRegister R0, MachineRegister R2) {
    i = begin(i, op);
    writeString(right(R0 + " ", DEST_AREA_SIZE));
    writeString(right("[" + decimal(d) + "+" + X + "<<" + decimal(s) + "]", SOURCE_AREA_SIZE));
    writeString(right(R2 + " ", SOURCE_AREA_SIZE));
    end(i);
  }

  public void RRA(int i, String op, MachineRegister R0, Address d) {
    i = begin(i, op);
    writeString(right(R0 + " ", DEST_AREA_SIZE));
    writeString(right("[" + hex(d) + "]", SOURCE_AREA_SIZE));
    end(i);
  }

  public void RAR(int i, String op, Address d, MachineRegister R0) {
    i = begin(i, op);
    writeString(right(R0 + " ", DEST_AREA_SIZE));
    writeString(right("[" + hex(d) + "]", SOURCE_AREA_SIZE));
    end(i);
  }

  public void RARI(int i, String op, Address d, MachineRegister R0, int imm) {
    i = begin(i, op);
    writeString(right(R0 + " ", DEST_AREA_SIZE));
    writeString(right("[" + hex(d) + "]", SOURCE_AREA_SIZE));
    writeString(right(decimal(imm), SOURCE_AREA_SIZE));
    end(i);
  }

  public void RARR(int i, String op, Address d, MachineRegister R0, MachineRegister R2) {
    i = begin(i, op);
    writeString(right(R0 + " ", DEST_AREA_SIZE));
    writeString(right("[" + hex(d) + "]", SOURCE_AREA_SIZE));
    writeString(right(R2 + " ", SOURCE_AREA_SIZE));
    end(i);
  }

  private int begin(int i, String op) {
    if (prefix != null) i--;
    writeString(right(hex(i), 6) + "| ");
    if (prefix != null) {
      writeString(right(prefix.toString(), PREFIX_AREA_SIZE) + " ");
    } else {
      writeString(right("", PREFIX_AREA_SIZE) + " ");
    }
    writeString(left(op, OP_AREA_SIZE));
    return i;
  }

  private void end(int i) {
    writeString(" | ");
    String inst = asm.lastInstructionAsString(i);
    writeString(inst);
    lines.add(currentLine);
    currentLine = new Line();

    prefix = null;
  }

  @Pure
  private static String left(String s, int w) {
    int n = s.length();
    if (w < n) return s.substring(0, w);
    StringBuilder result = new StringBuilder(s);
    for (int i = n; i < w; i++) {
      result.append(' ');
    }
    return result.toString();
  }

  @Pure
  private static String right(String s, int w) {
    int n = s.length();
    if (w < n) return s.substring(n - w);
    StringBuilder result = new StringBuilder();
    for (int i = n; i < w; i++) {
      result.append(' ');
    }
    result.append(s);
    return result.toString();
  }

  private static String decimal(Offset o) {
    return decimal(o.toInt());
  }

  @Pure
  static String decimal(int n) {
    if (n == 0) return "0";
    String sign = "";
    if (n < 0) {
      sign = "-";
      n = -n;
    }
    String result = "";
    while (0 < n) {
      int i = n % 10;
      n /= 10;
      if (i == 0) {
        result = "0" + result;
      } else if (i == 1) {
        result = "1" + result;
      } else if (i == 2) {
        result = "2" + result;
      } else if (i == 3) {
        result = "3" + result;
      } else if (i == 4) {
        result = "4" + result;
      } else if (i == 5) {
        result = "5" + result;
      } else if (i == 6) {
        result = "6" + result;
      } else if (i == 7) {
        result = "7" + result;
      } else if (i == 8) {
        result = "8" + result;
      } else if (i == 9) result = "9" + result;
    }
    return (sign + result);
  }

  @Pure
  static String decimal(long n) {
    if (n == 0) return "0";
    String sign = "";
    if (n < 0) {
      sign = "-";
      n = -n;
    }
    String result = "";
    while (0 < n) {
      long i = n % 10;
      n /= 10;
      if (i == 0) {
        result = "0" + result;
      } else if (i == 1) {
        result = "1" + result;
      } else if (i == 2) {
        result = "2" + result;
      } else if (i == 3) {
        result = "3" + result;
      } else if (i == 4) {
        result = "4" + result;
      } else if (i == 5) {
        result = "5" + result;
      } else if (i == 6) {
        result = "6" + result;
      } else if (i == 7) {
        result = "7" + result;
      } else if (i == 8) {
        result = "8" + result;
      } else if (i == 9) result = "9" + result;
    }
    return (sign + result);
  }

  private static String decimal(short s) {
    return decimal((int) s);
  }

  @Pure
  static String hex(Address i) {
    return (hex((short) (i.toInt() >> 16)) + hex((short) i.toWord().toInt()));
  }

  @Pure
  public static String hex(int i) {
    return (hex((short) (i >> 16)) + hex((short) i));
  }

  @Pure
  static String hex(short i) {
    return (hex((byte) (i >> 8)) + hex((byte) i));
  }

  @Pure
  static String hex(byte b) {
    int i = b & 0xFF;
    byte j = (byte) (i / 0x10);
    String s;
    if (j == 0x0) {
      s = "0";
    } else if (j == 0x1) {
      s = "1";
    } else if (j == 0x2) {
      s = "2";
    } else if (j == 0x3) {
      s = "3";
    } else if (j == 0x4) {
      s = "4";
    } else if (j == 0x5) {
      s = "5";
    } else if (j == 0x6) {
      s = "6";
    } else if (j == 0x7) {
      s = "7";
    } else if (j == 0x8) {
      s = "8";
    } else if (j == 0x9) {
      s = "9";
    } else if (j == 0xA) {
      s = "A";
    } else if (j == 0xB) {
      s = "B";
    } else if (j == 0xC) {
      s = "C";
    } else if (j == 0xD) {
      s = "D";
    } else if (j == 0xE) {
      s = "E";
    } else {
      s = "F";
    }
    j = (byte) (i % 0x10);
    String t;
    if (j == 0x0) {
      t = "0";
    } else if (j == 0x1) {
      t = "1";
    } else if (j == 0x2) {
      t = "2";
    } else if (j == 0x3) {
      t = "3";
    } else if (j == 0x4) {
      t = "4";
    } else if (j == 0x5) {
      t = "5";
    } else if (j == 0x6) {
      t = "6";
    } else if (j == 0x7) {
      t = "7";
    } else if (j == 0x8) {
      t = "8";
    } else if (j == 0x9) {
      t = "9";
    } else if (j == 0xA) {
      t = "A";
    } else if (j == 0xB) {
      t = "B";
    } else if (j == 0xC) {
      t = "C";
    } else if (j == 0xD) {
      t = "D";
    } else if (j == 0xE) {
      t = "E";
    } else {
      t = "F";
    }
    return s + t;
  }

  @Override
  public void noteBytecode(int i, String bcode) {
    writeLine("[" + decimal(i) + "] " + bcode);
  }

  public void comment(int i, String comment) {
    writeLine(right(hex(i), 6) + "| " + comment);
  }

  public void comefrom(int i, int j) {
    writeLine(right(hex(i), 6) + "| <<< " + right(hex(j), 6));
  }

  private void writeString(String s) {
    currentLine.addToLine(s);
  }

  private void writeLine(String s) {
    currentLine.addToLine(s);
    currentLine.addNewline();
  }

  public void printListing() {
    final int sizeEstimate = lines.size() * DEFAULT_LINE_SIZE;
    StringBuilder listing = new StringBuilder(sizeEstimate);
    for (Line l : lines) {
      String content = l.finish();
      listing.append(content);
    }
    VM.sysWriteln(listing.toString().trim());
  }

}
