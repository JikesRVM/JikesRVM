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

  static final byte   O = 0x0; // (overflow)
  static final byte  NO = 0x1; // (not overflow)
  static final byte LLT = 0x2; // logically less than (below)
  static final byte LGE = 0x3; // logically greater than or equal (not below) 
  static final byte  EQ = 0x4; // equal (zero)
  static final byte  NE = 0x5; // not equal (not zero)
  static final byte LLE = 0x6; // logically less than or equal (not above)
  static final byte LGT = 0x7; // logically greater than (above)
  static final byte   S = 0x8; // (sign) negative??
  static final byte  NS = 0x9; // (not sign) positive or zero??
  static final byte  PE = 0xA; // (even parity)
  static final byte  PO = 0xB; // (odd parity)
  static final byte  U  = 0xA; // (unordered floating point #s)
  static final byte  NU = 0xB; // (ordered floating point #s)
  static final byte  LT = 0xC; // less than
  static final byte  GE = 0xD; // greater than or equal (not less than)
  static final byte  LE = 0xE; // less than or equal (not greater than)
  static final byte  GT = 0xF; // greater than 

  // scale factors for SIB bytes
  static final short BYTE  = 0;
  static final short SHORT = 1;
  static final short WORD  = 2;
  static final short LONG  = 3;

}
