/*
 * (C) Copyright IBM Corp. 2001
 */
// $Id$
package com.ibm.JikesRVM;
/**
 * Constants exported by the assembler
 * @author Julian Dolby
 */
public interface VM_AssemblerConstants {
  static final String [] CONDITION = {
   "O", "NO", "LLT", "LGE", "EQ", "NE", "LLE", "LGT", "S", "NS", "PE", "PO", "LT", "GE", "LE", "GT" 
  };

  static final byte   O = 0x0; // OF == 1 - overflow
  static final byte  NO = 0x1; // OF == 0 - not overflow
  static final byte LLT = 0x2; // CF == 1 - logically less than (below)
  static final byte LGE = 0x3; // CF == 0 - logically greater than or equal (not below)
  static final byte  EQ = 0x4; // ZF == 1 - equal (zero)
  static final byte  NE = 0x5; // ZF == 0 - not equal (not zero)
  static final byte LLE = 0x6; // CF == 1 or ZF == 1 - logically less than or equal (not above)
  static final byte LGT = 0x7; // CF == 0 and ZF == 0 - logically greater than (above)
  static final byte   S = 0x8; // SF == 1 - (sign) negative??
  static final byte  NS = 0x9; // SF == 0 - (not sign) positive or zero??
  static final byte  PE = 0xA; // PF == 1 - even parity or unordered floating point #s
  static final byte  PO = 0xB; // PF == 0 - odd parity or ordered floating point #s
  static final byte  LT = 0xC; // SF != OF - less than
  static final byte  GE = 0xD; // SF == OF - greater than or equal (not less than)
  static final byte  LE = 0xE; // ZF == 1 or SF != OF - less than or equal (not greater than)
  static final byte  GT = 0xF; // ZF == 0 and SF == OF - greater than

  // scale factors for SIB bytes
  static final short BYTE  = 0;
  static final short SHORT = 1;
  static final short WORD  = 2;
  static final short LONG  = 3;

}
