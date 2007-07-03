/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
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
