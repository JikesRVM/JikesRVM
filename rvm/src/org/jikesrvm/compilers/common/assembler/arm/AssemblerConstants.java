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
package org.jikesrvm.compilers.common.assembler.arm;

import org.jikesrvm.VM;
import org.vmmagic.pragma.Pure;
import org.vmmagic.pragma.Uninterruptible;

/**
 * Constants exported by the assembler
 */
public final class AssemblerConstants {

  public enum COND {
    EQ(0x0 << 28), // 0b0000
    NE(0x1 << 28), // 0b0001
    HS(0x2 << 28), // 0b0010 // Unsigned greater than or equal
    LO(0x3 << 28), // 0b0011 // Unsigned less than
    MI(0x4 << 28), // 0b0100 // Negative
    PL(0x5 << 28), // 0b0101 // Positive or zero
    VS(0x6 << 28), // 0b0110 // Overflow (for integers)
    VC(0x7 << 28), // 0b0111 // No overflow
    HI(0x8 << 28), // 0b1000 // Unsigned greater than
    LS(0x9 << 28), // 0b1001 // Unsigned less than or equal
    GE(0xA << 28), // 0b1010
    LT(0xB << 28), // 0b1011
    GT(0xC << 28), // 0b1100
    LE(0xD << 28), // 0b1101
    ALWAYS(0xE << 28),    // 0b1110 // Always (unconditional)
    NOCOND(0xF << 28);    // 0b1111 // For instructions with no condition

    public static final COND UNORDERED = VS;                  // Includes an NaN (for floating point)

    private final int v;

    COND(int v) {
      this.v = v;
    }

    @Uninterruptible
    @Pure
    public int value() {
      return v;
    }

    @Pure
    public COND flip() {
      switch(this) {
          case EQ: return NE;
          case NE: return EQ;
          case HS: return LO;
          case LO: return HS;
          case MI: return PL;
          case PL: return MI;
          case VS: return VC;
          case VC: return VS;
          case HI: return LS;
          case LS: return HI;
          case GE: return LT;
          case LT: return GE;
          case GT: return LE;
          case LE: return GT;
          default: if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED); // Can't flip an ALWAYS or NOCOND
      }
      return NOCOND;
    }
  }



  private AssemblerConstants() {
    // prevent instantiation
  }

}
