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
package org.jikesrvm.osr;

import org.jikesrvm.VM;
import org.jikesrvm.runtime.Magic;
import org.vmmagic.unboxed.Word;

/**
 * An instance of VariableElement represents a byte code variable
 * (local or stack element).  It is used to generate prologue to
 * recover the runtime state.  It refers to VM architecture.
 */
public class VariableElement implements OSRConstants {

  //////////////////////////////////
  // instance fields
  //////////////////////////////////

  /** the kind of this element : LOCAL or STACK */
  private final boolean kind;

  /**
   * the number of element, e.g., with kind we
   * can know it is L0 or S1.
   */
  private final char num;

  /** type code, can only be INT, FLOAT, LONG, DOUBLE, RET_ADDR, WORD or REF */
  private final byte tcode;

  /**
   * The value of this element.
   * For type INT, FLOAT, RET_ADDR and WORD (32-bit), the lower 32 bits are valid.
   * For type LONG and DOUBLE and WORD(64-bit), 64 bits are valid.
   * For REF type, next field 'ref' is valid.
   *
   * For FLOAT, and DOUBLE, use Magic.intBitsAsFloat
   *                         or Magic.longBitsAsDouble
   * to convert bits to floating-point value.
   */
  private final long value;

  /** for reference type value */
  private final Object ref;

  //////////////////////////////////
  // class auxiliary methods
  /////////////////////////////////
  static boolean isIBitsType(int tcode) {
    switch (tcode) {
      case INT:
      case FLOAT:
      case RET_ADDR:
        return true;
      case WORD:
        return VM.BuildFor32Addr;
      default:
        return false;
    }
  }

  static boolean isLBitsType(int tcode) {
    switch (tcode) {
      case LONG:
      case DOUBLE:
        return true;
      case WORD:
        return VM.BuildFor64Addr;
      default:
        return false;
    }
  }

  static boolean isRefType(int tcode) {
    return tcode == REF;
  }

  static boolean isWordType(int tcode) {
    return tcode == WORD;
  }

  //////////////////////////////////////
  // Initializer
  /////////////////////////////////////

  /** Constructor for 32-bit value */
  public VariableElement(boolean what_kind, int which_num, byte type, int ibits) {
    if (VM.VerifyAssertions) {
      VM._assert(isIBitsType(type));
      VM._assert(which_num < 0xFFFF);
    }

    this.kind = what_kind;
    this.num = (char)which_num;
    this.tcode = type;
    this.value = (long) ibits & 0x0FFFFFFFFL;
    this.ref = null;
  }

  /** Constructor for 64-bit value */
  public VariableElement(boolean what_kind, int which_num, byte type, long lbits) {
    if (VM.VerifyAssertions) {
      VM._assert(isLBitsType(type));
      VM._assert(which_num < 0xFFFF);
    }

    this.kind = what_kind;
    this.num = (char)which_num;
    this.tcode = type;
    this.value = lbits;
    this.ref = null;
  }

  /** Constructor for reference type */
  public VariableElement(boolean what_kind, int which_num, byte type, Object ref) {
    if (VM.VerifyAssertions) {
      VM._assert(isRefType(type));
      VM._assert(which_num < 0xFFFF);
    }

    this.kind = what_kind;
    this.num = (char)which_num;
    this.tcode = type;
    this.value = 0;
    this.ref = ref;
  }

  /** Constructor for word type */
  public VariableElement(boolean what_kind, int which_num, byte type, Word word) {
    if (VM.VerifyAssertions) {
      VM._assert(isWordType(type));
      VM._assert(which_num < 0xFFFF);
    }

    this.kind = what_kind;
    this.num = (char)which_num;
    this.tcode = type;
    if (VM.BuildFor32Addr) {
      this.value = ((long) word.toInt()) & 0x0FFFFFFFFL;
    } else {
      this.value = word.toLong();
    }
    this.ref = null;
  }

  ////////////////////////////////
  // instance method
  ////////////////////////////////

  /** local or stack element */
  boolean isLocal() {
    return kind == LOCAL;
  }

  /** get type code */
  byte getTypeCode() {
    return tcode;
  }

  char getNumber() {
    return num;
  }

  /** is reference type */
  boolean isRefType() {
    return (this.tcode == REF);
  }

  Object getObject() {
    return ref;
  }

  /** is word type */
  boolean isWordType() {
    return (this.tcode == WORD);
  }

  Word getWord() {
    return (VM.BuildFor32Addr) ? Word.fromIntSignExtend((int) value) : Word.fromLong(value);
  }

  /* for numerical */
  int getIntBits() {
    return (int) (value & 0x0FFFFFFFF);
  }

  long getLongBits() {
    return value;
  }

  /* to string */
  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder("(");

    if (kind == LOCAL) {
      buf.append('L');
    } else {
      buf.append('S');
    }
    buf.append((int)num);
    buf.append(",");

    char t = 'V';
    switch (tcode) {
      case INT:
        t = 'I';
        break;
      case FLOAT:
        t = 'F';
        break;
      case LONG:
        t = 'J';
        break;
      case DOUBLE:
        t = 'D';
        break;
      case RET_ADDR:
        t = 'R';
        break;
      case REF:
        t = 'L';
        break;
      case WORD:
        t = 'W';
        break;
    }

    buf.append(t);
    buf.append(",");

    switch (tcode) {
      case REF:
        // it is legal to have a null reference.
        if (ref == null) {
          buf.append("null");
        } else {
          buf.append(VM.addressAsHexString(Magic.objectAsAddress(ref)));
          buf.append(" ");
//      buf.append(ref.toString());
        }
        break;
      case WORD:
        buf.append("0x");
        if (VM.BuildFor32Addr) {
          buf.append(Integer.toHexString((int) (value & 0x0FFFFFFFFL)));
        } else {
          buf.append(Long.toHexString(value));
        }
        buf.append(" ");
        break;
      case FLOAT:
        buf.append(Magic.intBitsAsFloat((int) (value & 0x0FFFFFFFF)));
        break;
      case LONG:
        buf.append(value);
        break;
      case DOUBLE:
        buf.append(Magic.longBitsAsDouble(value));
        break;
      default:
        buf.append((int) (value & 0x0FFFFFFFF));
        break;
    }

    buf.append(")");

    return buf.toString();
  }
}
