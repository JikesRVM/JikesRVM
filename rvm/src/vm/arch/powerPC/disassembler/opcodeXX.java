/*
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
