/*
 * (C) Copyright IBM Corp. 2001
 */
/**
 * Structure for the D and XL forms, PowerPC instruction set 
 * @author John Waley
 * @see PPC_Disassembler 
 */

class opcode_tab {  

  int format;
  String mnemonic;

  opcode_tab(int format, String mnemonic) {
    this.format = format;
    this.mnemonic = mnemonic;
  }

}
