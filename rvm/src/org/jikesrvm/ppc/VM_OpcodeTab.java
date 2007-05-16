/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.ppc;

/**
 * Structure for the D and XL forms, PowerPC instruction set
 *
 * @see VM_Disassembler
 */
final class VM_OpcodeTab {

  int format;
  String mnemonic;

  VM_OpcodeTab(int format, String mnemonic) {
    this.format = format;
    this.mnemonic = mnemonic;
  }

}
