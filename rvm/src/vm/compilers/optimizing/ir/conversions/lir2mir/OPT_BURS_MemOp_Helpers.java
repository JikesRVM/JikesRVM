/*
 * (C) Copyright IBM Corp. 2001,2002
 */
//$Id$
package com.ibm.JikesRVM.opt;
import com.ibm.JikesRVM.*;

import com.ibm.JikesRVM.opt.ir.*;

/**
 * Contains common BURS helper functions for platforms with memory operands.
 * 
 * @author Dave Grove
 * @author Stephen Fink
 */
abstract class OPT_BURS_MemOp_Helpers extends OPT_BURS_Common_Helpers {
  // word size for memory operands
  static final byte B  = 0x01;  // byte (8 bits)
  static final byte W  = 0x02;  // word (16 bits)
  static final byte DW = 0x04;  // doubleword (32 bits)
  static final byte QW = 0x08;  // quadword (64 bits)

  static final byte B_S  = 0x00;  // byte (8*2^0 bits)
  static final byte W_S  = 0x01;  // word (8*2^116 bits)
  static final byte DW_S = 0x02;  // doubleword (8*2^2 bits)
  static final byte QW_S = 0x03;  // quadword (8*2^3 bits)

  OPT_BURS_MemOp_Helpers(OPT_BURS burs) {
    super(burs);
  }

  // Cost functions better suited to grammars with multiple non-termials
  protected final int ADDRESS_EQUAL(OPT_Instruction store, OPT_Instruction load, int trueCost) {
    return ADDRESS_EQUAL(store, load, trueCost, INFINITE);
  }
  protected final int ADDRESS_EQUAL(OPT_Instruction store, OPT_Instruction load, int trueCost, int falseCost) {
    if (Store.getAddress(store).similar(Load.getAddress(load)) &&
        Store.getOffset(store).similar(Load.getOffset(load))) {
      return trueCost;
    } else {
      return falseCost;
    }
  }

  protected final int ARRAY_ADDRESS_EQUAL(OPT_Instruction store, OPT_Instruction load, int trueCost) {
    return ARRAY_ADDRESS_EQUAL(store, load, trueCost, INFINITE);
  }
  protected final int ARRAY_ADDRESS_EQUAL(OPT_Instruction store, OPT_Instruction load, int trueCost, int falseCost) {
    if (AStore.getArray(store).similar(ALoad.getArray(load)) &&
        AStore.getIndex(store).similar(ALoad.getIndex(load))) {
      return trueCost;
    } else {
      return falseCost;
    }
  }

  // support to remember an address being computed in a subtree
  private static final class AddrStackElement {
    OPT_RegisterOperand base;
    OPT_RegisterOperand index;
    byte scale;
    int displacement;
    AddrStackElement next;
    AddrStackElement(OPT_RegisterOperand b,
                     OPT_RegisterOperand i,
                     byte s, int d,
                     AddrStackElement n) {
      base = b;
      index = i;
      scale = s;
      displacement = d;
      next = n;
    }
  }
  private AddrStackElement AddrStack;
  protected final void pushAddress(OPT_RegisterOperand base,
                                   OPT_RegisterOperand index,
                                   byte scale,
                                   int disp) {
    AddrStack = new AddrStackElement(base, index, scale, disp, AddrStack);
  }
  protected final void augmentAddress(OPT_Operand op) {
    if (VM.VerifyAssertions) VM._assert(AddrStack != null, "No address to augment");
    if (op.isRegister()) {
      OPT_RegisterOperand rop = op.asRegister();
      if (AddrStack.base == null) {
        AddrStack.base = rop;
      } else if (AddrStack.index == null) {
        if (VM.VerifyAssertions) VM._assert(AddrStack.scale == (byte)0);
        AddrStack.index = rop;
      } else {
        throw new OPT_OptimizingCompilerException("three base registers in address");
      }
    } else {
      int disp = ((OPT_IntConstantOperand)op).value;
      AddrStack.displacement += disp;
    }
  }
  protected final void combineAddresses() {
    if (VM.VerifyAssertions) VM._assert(AddrStack != null, "No address to combine");
    AddrStackElement tmp = AddrStack;
    AddrStack = AddrStack.next;
    if (VM.VerifyAssertions) VM._assert(AddrStack != null, "only 1 address to combine");
    if (tmp.base != null) {
      if (AddrStack.base == null) {
        AddrStack.base = tmp.base;
      } else if (AddrStack.index == null) {
        if (VM.VerifyAssertions) VM._assert(AddrStack.scale == (byte)0);
        AddrStack.index = tmp.base;
      } else {
        throw new OPT_OptimizingCompilerException("three base registers in address");
      }
    }
    if (tmp.index != null) {
      if (AddrStack.index == null) {
        if (VM.VerifyAssertions) VM._assert(AddrStack.scale == (byte)0);
        AddrStack.index = tmp.index;
        AddrStack.scale = tmp.scale;
      } else if (AddrStack.base == null && tmp.scale == (byte)0) {
        AddrStack.base = tmp.base;
      } else {
        throw new OPT_OptimizingCompilerException("two scaled registers in address");
      }
    }
    AddrStack.displacement += tmp.displacement;
  }
  protected final OPT_MemoryOperand consumeAddress(byte size, 
                                         OPT_LocationOperand loc,
                                         OPT_Operand guard) {
    if (VM.VerifyAssertions) VM._assert(AddrStack != null, "No address to consume");
    OPT_MemoryOperand mo = 
      new OPT_MemoryOperand(AddrStack.base, AddrStack.index, AddrStack.scale,
                            AddrStack.displacement, size, loc, guard);
    AddrStack = AddrStack.next;
    return mo;
  }

  // support to remember a memory operand computed in a subtree
  private static final class MOStackElement {
    OPT_MemoryOperand mo;
    MOStackElement next;
    MOStackElement(OPT_MemoryOperand m, MOStackElement n) {
      mo = m;
      next = n;
    }
  }
  private MOStackElement MOStack;
  protected final void pushMO(OPT_MemoryOperand mo) {
    MOStack = new MOStackElement(mo, MOStack);
  }
  protected final OPT_MemoryOperand consumeMO() {
    if (VM.VerifyAssertions) VM._assert(MOStack != null, "No memory operand to consume");
    OPT_MemoryOperand mo = MOStack.mo;
    MOStack = MOStack.next;
    return mo;
  }


  // Construct a memory operand for the effective address of the 
  // load instruction
  protected final OPT_MemoryOperand MO_L(OPT_Instruction s, byte size) {
    return MO(Load.getAddress(s), Load.getOffset(s), size, 
              Load.getLocation(s), Load.getGuard(s));
  }
  // Construct a memory operand for the effective address of the 
  // store instruction
  protected final OPT_MemoryOperand MO_S(OPT_Instruction s, byte size) {
    return MO(Store.getAddress(s), Store.getOffset(s), size, 
              Store.getLocation(s), Store.getGuard(s));
  }
  // Construct a memory operand for the effective address of the 
  // array load instruction
  protected final OPT_MemoryOperand MO_AL(OPT_Instruction s, byte scale, byte size) {
    return MO_ARRAY(ALoad.getArray(s), ALoad.getIndex(s), scale, size, 
                    ALoad.getLocation(s), ALoad.getGuard(s));
  }
  // Construct a memory operand for the effective address of the 
  // array store instruction
  protected final OPT_MemoryOperand MO_AS(OPT_Instruction s, byte scale, byte size) {
    return MO_ARRAY(AStore.getArray(s), AStore.getIndex(s), scale, size, 
                    AStore.getLocation(s), AStore.getGuard(s));
  }

  // Construct a memory operand for the effective address of the 
  // load instruction 
  protected final OPT_MemoryOperand MO_L(OPT_Instruction s, byte size, int disp) {
    return MO(Load.getAddress(s), Load.getOffset(s), size, disp,
              Load.getLocation(s), Load.getGuard(s));
  }
  // Construct a memory operand for the effective address of the 
  // store instruction
  protected final OPT_MemoryOperand MO_S(OPT_Instruction s, byte size, int disp) {
    return MO(Store.getAddress(s), Store.getOffset(s), size, disp,
              Store.getLocation(s), Store.getGuard(s));
  }
  // Construct a memory operand for the effective address of the 
  // array load instruction
  protected final OPT_MemoryOperand MO_AL(OPT_Instruction s, byte scale, byte size, int disp) {
    return MO_ARRAY(ALoad.getArray(s), ALoad.getIndex(s), scale, size, disp,
                    ALoad.getLocation(s), ALoad.getGuard(s));
  }
  // Construct a memory operand for the effective address of the array store instruction
  protected final OPT_MemoryOperand MO_AS(OPT_Instruction s, byte scale, byte size, int disp) {
    return MO_ARRAY(AStore.getArray(s), AStore.getIndex(s), scale, size, disp,
                    AStore.getLocation(s), AStore.getGuard(s));
  }

  protected final OPT_MemoryOperand MO(OPT_Operand base, OPT_Operand offset, 
                                       byte size, OPT_LocationOperand loc,
                                       OPT_Operand guard) {
    if (base instanceof OPT_IntConstantOperand) {
      if (offset instanceof OPT_IntConstantOperand) {
        return MO_D(IV(base)+IV(offset), size, loc, guard);
      } else {
        return MO_BD(offset, IV(base), size, loc, guard);
      }
    } else {
      if (offset instanceof OPT_IntConstantOperand) {
        return MO_BD(base, IV(offset), size, loc, guard);
      } else {
        return MO_BI(base, offset, size, loc, guard);
      }
    }
  }

  protected final OPT_MemoryOperand MO_ARRAY(OPT_Operand base, 
                                             OPT_Operand index, 
                                             byte scale, byte size, 
                                             OPT_LocationOperand loc,
                                             OPT_Operand guard) {
    if (index instanceof OPT_IntConstantOperand) {
      return MO_BD(base, IV(index)<<scale, size, loc, guard);
    } else {
      return MO_BIS(base, index, scale, size, loc, guard);
    }
  }


  protected final OPT_MemoryOperand MO(OPT_Operand base, OPT_Operand offset, 
                                       byte size, int disp,
                                       OPT_LocationOperand loc,
                                       OPT_Operand guard) {
    if (base instanceof OPT_IntConstantOperand) {
      if (offset instanceof OPT_IntConstantOperand) {
        return MO_D(IV(base)+IV(offset)+disp, size, loc, guard);
      } else {
        return MO_BD(offset, IV(base)+disp, size, loc, guard);
      }
    } else {
      if (offset instanceof OPT_IntConstantOperand) {
        return MO_BD(base, IV(offset)+disp, size, loc, guard);
      } else {
        return MO_BID(base, offset, disp, size, loc, guard);
      }
    }
  }

  protected final OPT_MemoryOperand MO_ARRAY(OPT_Operand base, 
                                             OPT_Operand index, 
                                             byte scale, byte size, 
                                             int disp,
                                             OPT_LocationOperand loc,
                                             OPT_Operand guard) {
    if (index instanceof OPT_IntConstantOperand) {
      return MO_BD(base, (IV(index)<<scale)+disp, size, loc, guard);
    } else {
      return new OPT_MemoryOperand(R(base), R(index), scale, 
                                   disp, size, loc, guard);
    }
  }

 
  protected final OPT_MemoryOperand MO_B(OPT_Operand base, byte size, 
                                         OPT_LocationOperand loc,
                                         OPT_Operand guard) {
    return OPT_MemoryOperand.B(R(base), size, loc, guard);
  }

  protected final OPT_MemoryOperand MO_BI(OPT_Operand base, 
                                          OPT_Operand index, 
                                          byte size, OPT_LocationOperand loc,
                                          OPT_Operand guard) {
    return OPT_MemoryOperand.BI(R(base), R(index), size, loc, guard);
  }

  protected final OPT_MemoryOperand MO_BD(OPT_Operand base, int disp, 
                                          byte size, OPT_LocationOperand loc,
                                          OPT_Operand guard) {
    return OPT_MemoryOperand.BD(R(base), disp, size, loc, guard);
  }

  protected final OPT_MemoryOperand MO_BID(OPT_Operand base, 
                                           OPT_Operand index, 
                                           int disp, byte size, 
                                           OPT_LocationOperand loc,
                                           OPT_Operand guard) {
    return OPT_MemoryOperand.BID(R(base), R(index), disp, size, loc, guard);
  }

  protected final OPT_MemoryOperand MO_BIS(OPT_Operand base, 
                                           OPT_Operand index, 
                                           byte scale, byte size, 
                                           OPT_LocationOperand loc,
                                           OPT_Operand guard) {
    return OPT_MemoryOperand.BIS(R(base), R(index), scale, size, loc, guard);
  }

  protected final OPT_MemoryOperand MO_D(int disp, 
                                         byte size, OPT_LocationOperand loc,
                                         OPT_Operand guard) {
    return OPT_MemoryOperand.D(disp, size, loc, guard);
  }

  protected final OPT_MemoryOperand MO_MC(OPT_Instruction s) {
    OPT_Operand base = Binary.getVal1(s);
    OPT_Operand val = Binary.getVal2(s);
    if (val instanceof OPT_FloatConstantOperand) {
      OPT_FloatConstantOperand fc = (OPT_FloatConstantOperand)val;
      int offset = fc.index << 2;
      OPT_LocationOperand loc = new OPT_LocationOperand(offset);
      if (base instanceof OPT_IntConstantOperand) {
        return MO_D(IV(base)+offset, DW, loc, TG());
      } else {
        return MO_BD(Binary.getVal1(s), offset, DW, loc, TG());
      }
    } else {
      OPT_DoubleConstantOperand dc = (OPT_DoubleConstantOperand)val;
      int offset = dc.index << 2;
      OPT_LocationOperand loc = new OPT_LocationOperand(offset);
      if (base instanceof OPT_IntConstantOperand) {
        return MO_D(IV(base)+offset, QW, loc, TG());
      } else {
        return MO_BD(Binary.getVal1(s), offset, QW, loc, TG());
      }
    }
  }

}
