/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

/**
 * Structure for opcode 31 and 63, PowerPC instruction set: 
 * these include the X, XO, XFL, XFX and A form
 * @author John Waley
 * @see PPC_Disassembler 
 */

class opcodeXX {

  int key;
  int form;
  int format;
  String mnemonic;

  opcodeXX(int key, int form, int format, String mnemonic) {
    this.key = key;
    this.form = form;
    this.format = format;
    this.mnemonic = mnemonic;
  }

}
