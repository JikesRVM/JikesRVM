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
package org.jikesrvm.ppc;

/**
 * Structure for opcode 31 and 63, PowerPC instruction set:
 * these include the X, XO, XFL, XFX and A form
 * @see Disassembler
 */

final class OpcodeXX {

  int key;
  int form;
  int format;
  String mnemonic;

  OpcodeXX(int key, int form, int format, String mnemonic) {
    this.key = key;
    this.form = form;
    this.format = format;
    this.mnemonic = mnemonic;
  }

}
