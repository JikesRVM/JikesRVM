/*
 * (C) Copyright IBM Corp 2002
 */
//$Id$

package com.ibm.JikesRVM.OSR;

import com.ibm.JikesRVM.*;
import org.vmmagic.unboxed.*;

/** 
 * An instance of OSR_VariableElement represents a byte code variable
 * (local or stack element).  It is used to generate prologue to
 * recover the runtime state.  It refers to VM architecture.
 *
 * @author Feng Qian
 */

public class OSR_VariableElement implements OSR_Constants {
  
  //////////////////////////////////
  // instance fields
  //////////////////////////////////

  /* the kind of this element : LOCAL or STACK
   */
  private int kind;

  /* the number of element, e.g., with kind we 
   * can know it is L0 or S1.
   */
  private int num;

  /* type code, can only be INT, FLOAT, LONG, DOUBLE, RET_ADDR, WORD or REF */
  private int tcode;

  /* The value of this element. 
   * For type INT, FLOAT, RET_ADDR and WORD (32-bit), the lower 32 bits are valid.
   * For type LONG and DOUBLE and WORD(64-bit), 64 bits are valid.
   * For REF type, next field 'ref' is valid.
   *
   * For FLOAT, and DOUBLE, use VM_Magic.intBitsAsFloat
   *                         or VM_Magic.longBitsAsDouble 
   * to convert bits to floating-point value.
   */
  private long value;

  /* for reference type value */
  private Object ref;

  //////////////////////////////////
  // class auxiliary methods
  ///////////////////////////////// 
  final static boolean isIBitsType(int tcode) {
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

  final static boolean isLBitsType(int tcode) {
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

  final static boolean isRefType(int tcode) {
    return tcode == REF;
  }

  final static boolean isWordType(int tcode) {
    return tcode == WORD;
  }

  //////////////////////////////////////
  // Initializer
  /////////////////////////////////////

  /* for 32-bit value */
  OSR_VariableElement(int what_kind, 
                     int which_num,
                     int type,
                     int ibits) {
    if (VM.VerifyAssertions) {
      VM._assert(isIBitsType(type));
    }
    
    this.kind  = what_kind;
    this.num   = which_num;
    this.tcode = type;
    this.value = (long)ibits & 0x0FFFFFFFFL;
  }

  /* for 64-bit value */
  OSR_VariableElement(int what_kind,
                     int which_num,
                     int type,
                     long lbits) {
    if (VM.VerifyAssertions) {
      VM._assert(isLBitsType(type));
    }
    
    this.kind  = what_kind;
    this.num   = which_num;
    this.tcode = type;
    this.value = lbits;
  }

  /* for reference type */
  OSR_VariableElement(int what_kind,
                     int which_num,
                     int type,
                     Object ref) {
    if (VM.VerifyAssertions) {
      VM._assert(isRefType(type));
    }

    this.kind  = what_kind;
    this.num   = which_num;
    this.tcode = type;
    this.ref   = ref;
  }

  /* for word type */
  OSR_VariableElement(int what_kind,
                     int which_num,
                     int type,
                     Word word) {
    if (VM.VerifyAssertions) {
      VM._assert(isWordType(type));
    }

    this.kind  = what_kind;
    this.num   = which_num;
    this.tcode = type;
    if (VM.BuildFor32Addr)
      this.value = ((long)word.toInt()) & 0x0FFFFFFFFL;
    else 
      this.value = word.toLong();
  }

  ////////////////////////////////
  // instance method
  ////////////////////////////////

  /* local or stack element */
  boolean isLocal() {
    return kind == LOCAL;
  }

  /* get type code */
  int getTypeCode() {
    return tcode;
  }

  int getNumber() {
    return num;
  }

  /* is reference type */
  boolean isRefType() {
    return (this.tcode == REF);
  }

  Object getObject() {
    return ref;
  }

  /* is word type */
  boolean isWordType() {
    return (this.tcode == WORD);
  }

  Word getWord() {
    //-#if RVM_FOR_32_ADDR
    return Word.fromIntSignExtend((int)value);
    //-#endif
    //-#if RVM_FOR_64_ADDR
    return Word.fromLong(value);
    //-#endif
  }

  /* for numerical */
  int getIntBits() {
    return (int)(value & 0x0FFFFFFFF);
  }

  long getLongBits() {
    return value;
  }

  /* to string */
  public String toString() {
    StringBuffer buf = new StringBuffer("(");
    
    if (kind == LOCAL)
      buf.append('L');
    else 
      buf.append('S');
    buf.append(num);
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
        buf.append("0x");
        if (VM.BuildFor32Addr)
          buf.append(Integer.toHexString(VM_Magic.objectAsAddress(ref).toInt()));
        else
          buf.append(Long.toHexString(VM_Magic.objectAsAddress(ref).toLong()));
        buf.append(" ");
//      buf.append(ref.toString());
      }
      break;
    case WORD:
      buf.append("0x");
      if (VM.BuildFor32Addr)
        buf.append(Integer.toHexString((int)(value & 0x0FFFFFFFFL)));
      else
        buf.append(Long.toHexString(value));
      buf.append(" ");
      break;
    case FLOAT:
      buf.append(VM_Magic.intBitsAsFloat((int)(value & 0x0FFFFFFFF)));
      break;
    case LONG:
      buf.append(value);
      break;
    case DOUBLE:
      buf.append(VM_Magic.longBitsAsDouble(value));
      break;
    default:
      buf.append((int)(value & 0x0FFFFFFFF));
      break;
    }

    buf.append(")");
    
    return buf.toString();
  }
}
