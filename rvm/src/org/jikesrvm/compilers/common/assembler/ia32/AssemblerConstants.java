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

/**
 * Constants exported by the assembler
 */
public interface AssemblerConstants {
  String[] CONDITION =
      {"O", "NO", "LLT", "LGE", "EQ", "NE", "LLE", "LGT", "S", "NS", "PE", "PO", "LT", "GE", "LE", "GT"};

  /** OF == 1 - overflow */
  byte O = 0x0;
  /** OF == 0 - not overflow */
  byte NO = 0x1;
  /** CF == 1 - logically less than (below) */
  byte LLT = 0x2;
  /** CF == 0 - logically greater than or equal (not below) */
  byte LGE = 0x3;
  /** ZF == 1 - equal (zero) */
  byte EQ = 0x4;
  /** ZF == 0 - not equal (not zero) */
  byte NE = 0x5;
  /**  CF == 1 or ZF == 1 - logically less than or equal (not above) */
  byte LLE = 0x6;
  /** CF == 0 and ZF == 0 - logically greater than (above) */
  byte LGT = 0x7;
  byte S = 0x8; // SF == 1 - (sign) negative??
  byte NS = 0x9; // SF == 0 - (not sign) positive or zero??
  /** PF == 1 - even parity or unordered floating point #s */
  byte PE = 0xA;
  /**  PF == 0 - odd parity or ordered floating point #s */
  byte PO = 0xB;
  /** SF != OF - less than */
  byte LT = 0xC;
  /** SF == OF - greater than or equal (not less than) */
  byte GE = 0xD;
  /** ZF == 1 or SF != OF - less than or equal (not greater than) */
  byte LE = 0xE;
  /** ZF == 0 and SF == OF - greater than */
  byte GT = 0xF;

  // scale factors for SIB bytes
  short BYTE = 0;
  short SHORT = 1;
  short WORD = 2;
  short LONG = 3;

}
