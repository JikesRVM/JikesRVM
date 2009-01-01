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
package org.jikesrvm.compilers.opt.lir2mir;

import org.jikesrvm.VM;
import org.jikesrvm.compilers.opt.OptimizingCompilerException;
import org.jikesrvm.compilers.opt.ir.ALoad;
import org.jikesrvm.compilers.opt.ir.AStore;
import org.jikesrvm.compilers.opt.ir.Binary;
import org.jikesrvm.compilers.opt.ir.Load;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.Store;
import org.jikesrvm.compilers.opt.ir.operand.DoubleConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.FloatConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.IntConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.LocationOperand;
import org.jikesrvm.compilers.opt.ir.operand.MemoryOperand;
import org.jikesrvm.compilers.opt.ir.operand.Operand;
import org.jikesrvm.compilers.opt.ir.operand.RegisterOperand;
import org.vmmagic.unboxed.Offset;

/**
 * Contains common BURS helper functions for platforms with memory operands.
 */
public abstract class BURS_MemOp_Helpers extends BURS_Common_Helpers {
  // word size for memory operands
  protected static final byte B  = 0x01;  // byte (8 bits)
  protected static final byte W  = 0x02;  // word (16 bits)
  protected static final byte DW = 0x04;  // doubleword (32 bits)
  protected static final byte QW = 0x08;  // quadword (64 bits)
  protected static final byte PARAGRAPH = 0x10; // paragraph (128 bits)

  protected static final byte B_S = 0x00;  // byte (8*2^0 bits)
  protected static final byte W_S = 0x01;  // word (8*2^116 bits)
  protected static final byte DW_S = 0x02;  // doubleword (8*2^2 bits)
  protected static final byte QW_S = 0x03;  // quadword (8*2^3 bits)

  protected BURS_MemOp_Helpers(BURS burs) {
    super(burs);
  }

  // Cost functions better suited to grammars with multiple non-termials
  protected final int ADDRESS_EQUAL(Instruction store, Instruction load, int trueCost) {
    return ADDRESS_EQUAL(store, load, trueCost, INFINITE);
  }

  protected final int ADDRESS_EQUAL(Instruction store, Instruction load, int trueCost, int falseCost) {
    if (Store.getAddress(store).similar(Load.getAddress(load)) &&
        Store.getOffset(store).similar(Load.getOffset(load))) {
      return trueCost;
    } else {
      return falseCost;
    }
  }

  protected final int ARRAY_ADDRESS_EQUAL(Instruction store, Instruction load, int trueCost) {
    return ARRAY_ADDRESS_EQUAL(store, load, trueCost, INFINITE);
  }

  protected final int ARRAY_ADDRESS_EQUAL(Instruction store, Instruction load, int trueCost, int falseCost) {
    if (AStore.getArray(store).similar(ALoad.getArray(load)) && AStore.getIndex(store).similar(ALoad.getIndex(load))) {
      return trueCost;
    } else {
      return falseCost;
    }
  }

  // support to remember an address being computed in a subtree
  private static final class AddrStackElement {
    RegisterOperand base;
    RegisterOperand index;
    byte scale;
    Offset displacement;
    AddrStackElement next;

    AddrStackElement(RegisterOperand b, RegisterOperand i, byte s, Offset d, AddrStackElement n) {
      base = b;
      index = i;
      scale = s;
      displacement = d;
      next = n;
    }
  }

  private AddrStackElement AddrStack;

  protected final void pushAddress(RegisterOperand base, RegisterOperand index, byte scale, Offset disp) {
    AddrStack = new AddrStackElement(base, index, scale, disp, AddrStack);
  }

  protected final void augmentAddress(Operand op) {
    if (VM.VerifyAssertions) VM._assert(AddrStack != null, "No address to augment");
    if (op.isRegister()) {
      RegisterOperand rop = op.asRegister();
      if (AddrStack.base == null) {
        AddrStack.base = rop;
      } else if (AddrStack.index == null) {
        if (VM.VerifyAssertions) VM._assert(AddrStack.scale == (byte) 0);
        AddrStack.index = rop;
      } else {
        throw new OptimizingCompilerException("three base registers in address");
      }
    } else {
      int disp = ((IntConstantOperand) op).value;
      AddrStack.displacement = AddrStack.displacement.plus(disp);
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
        if (VM.VerifyAssertions) VM._assert(AddrStack.scale == (byte) 0);
        AddrStack.index = tmp.base;
      } else {
        throw new OptimizingCompilerException("three base registers in address");
      }
    }
    if (tmp.index != null) {
      if (AddrStack.index == null) {
        if (VM.VerifyAssertions) VM._assert(AddrStack.scale == (byte) 0);
        AddrStack.index = tmp.index;
        AddrStack.scale = tmp.scale;
      } else if (AddrStack.base == null && tmp.scale == (byte) 0) {
        AddrStack.base = tmp.base;
      } else {
        throw new OptimizingCompilerException("two scaled registers in address");
      }
    }
    AddrStack.displacement = AddrStack.displacement.plus(tmp.displacement.toInt());
  }

  protected final MemoryOperand consumeAddress(byte size, LocationOperand loc, Operand guard) {
    if (VM.VerifyAssertions) VM._assert(AddrStack != null, "No address to consume");
    MemoryOperand mo =
        new MemoryOperand(AddrStack.base,
                              AddrStack.index,
                              AddrStack.scale,
                              AddrStack.displacement,
                              size,
                              loc,
                              guard);
    AddrStack = AddrStack.next;
    return mo;
  }

  // support to remember a memory operand computed in a subtree
  private static final class MOStackElement {
    MemoryOperand mo;
    MOStackElement next;

    MOStackElement(MemoryOperand m, MOStackElement n) {
      mo = m;
      next = n;
    }
  }

  private MOStackElement MOStack;

  protected final void pushMO(MemoryOperand mo) {
    MOStack = new MOStackElement(mo, MOStack);
  }

  protected final MemoryOperand consumeMO() {
    if (VM.VerifyAssertions) VM._assert(MOStack != null, "No memory operand to consume");
    MemoryOperand mo = MOStack.mo;
    MOStack = MOStack.next;
    return mo;
  }

  /**
   * Construct a memory operand for the effective address of the
   * load instruction
   */
  protected final MemoryOperand MO_L(Instruction s, byte size) {
    return MO_L(s, size, 0);
  }

  /**
   * Construct a displaced memory operand for the effective address of the
   * load instruction
   */
  protected final MemoryOperand MO_L(Instruction s, byte size, int disp) {
    if (VM.VerifyAssertions) VM._assert(Load.conforms(s));
    return MO(Load.getAddress(s),
              Load.getOffset(s),
              size,
              Offset.fromIntSignExtend(disp),
              Load.getLocation(s),
              Load.getGuard(s));
  }

  /**
   * Construct a memory operand for the effective address of the
   * store instruction
   */
  protected final MemoryOperand MO_S(Instruction s, byte size) {
    return MO_S(s, size, 0);
  }

  /**
   * Construct a displaced memory operand for the effective address of the
   * store instruction
   */
  protected final MemoryOperand MO_S(Instruction s, byte size, int disp) {
    if (VM.VerifyAssertions) VM._assert(Store.conforms(s));
    return MO(Store.getAddress(s),
              Store.getOffset(s),
              size,
              Offset.fromIntSignExtend(disp),
              Store.getLocation(s),
              Store.getGuard(s));
  }

  protected final MemoryOperand MO(Operand base, Operand offset, byte size, LocationOperand loc,
                                       Operand guard) {
    if (base instanceof IntConstantOperand) {
      if (offset instanceof IntConstantOperand) {
        return MO_D(Offset.fromIntSignExtend(IV(base) + IV(offset)), size, loc, guard);
      } else {
        return MO_BD(offset, Offset.fromIntSignExtend(IV(base)), size, loc, guard);
      }
    } else {
      if (offset instanceof IntConstantOperand) {
        return MO_BD(base, Offset.fromIntSignExtend(IV(offset)), size, loc, guard);
      } else {
        return MO_BI(base, offset, size, loc, guard);
      }
    }
  }

  protected final MemoryOperand MO(Operand base, Operand offset, byte size, LocationOperand loc,
      Operand guard, int disp) {
    if (base instanceof IntConstantOperand) {
      if (offset instanceof IntConstantOperand) {
        return MO_D(Offset.fromIntSignExtend(IV(base) + IV(offset) + disp), size, loc, guard);
      } else {
        return MO_BD(offset, Offset.fromIntSignExtend(IV(base)+disp), size, loc, guard);
      }
    } else {
      if (offset instanceof IntConstantOperand) {
        return MO_BD(base, Offset.fromIntSignExtend(IV(offset)+disp), size, loc, guard);
      } else {
        return MO_BID(base, offset, Offset.fromIntSignExtend(disp), size, loc, guard);
      }
    }
  }

  protected final MemoryOperand MO(Operand base, Operand offset, byte size, Offset disp,
                                       LocationOperand loc, Operand guard) {
    if (base instanceof IntConstantOperand) {
      if (offset instanceof IntConstantOperand) {
        return MO_D(disp.plus(IV(base) + IV(offset)), size, loc, guard);
      } else {
        return MO_BD(offset, disp.plus(IV(base)), size, loc, guard);
      }
    } else {
      if (offset instanceof IntConstantOperand) {
        return MO_BD(base, disp.plus(IV(offset)), size, loc, guard);
      } else {
        return MO_BID(base, offset, disp, size, loc, guard);
      }
    }
  }


  protected final MemoryOperand MO_B(Operand base, byte size, LocationOperand loc, Operand guard) {
    return MemoryOperand.B(R(base), size, loc, guard);
  }

  protected final MemoryOperand MO_BI(Operand base, Operand index, byte size, LocationOperand loc,
                                          Operand guard) {
    return MemoryOperand.BI(R(base), R(index), size, loc, guard);
  }

  protected final MemoryOperand MO_BD(Operand base, Offset disp, byte size, LocationOperand loc,
                                          Operand guard) {
    return MemoryOperand.BD(R(base), disp, size, loc, guard);
  }

  protected final MemoryOperand MO_BID(Operand base, Operand index, Offset disp, byte size,
                                           LocationOperand loc, Operand guard) {
    return MemoryOperand.BID(R(base), R(index), disp, size, loc, guard);
  }

  protected final MemoryOperand MO_BIS(Operand base, Operand index, byte scale, byte size,
                                           LocationOperand loc, Operand guard) {
    return MemoryOperand.BIS(R(base), R(index), scale, size, loc, guard);
  }

  protected final MemoryOperand MO_D(Offset disp, byte size, LocationOperand loc, Operand guard) {
    return MemoryOperand.D(disp.toWord().toAddress(), size, loc, guard);
  }

  /**
   * Construct a memory operand for the effective address of the
   * array load instruction
   */
  protected final MemoryOperand MO_AL(Instruction s, byte scale, byte size) {
    return MO_AL(s, scale, size, 0);
  }

  /**
   * Construct a memory operand for the effective address of the
   * array load instruction
   */
  protected final MemoryOperand MO_AL(Instruction s, byte scale, byte size, int disp) {
    if (VM.VerifyAssertions) VM._assert(ALoad.conforms(s));
    return MO_ARRAY(ALoad.getArray(s),
                    ALoad.getIndex(s),
                    scale,
                    size,
                    Offset.fromIntSignExtend(disp),
                    ALoad.getLocation(s),
                    ALoad.getGuard(s));
  }

  /**
   * Construct a memory operand for the effective address of the
   * array store instruction
   */
  protected final MemoryOperand MO_AS(Instruction s, byte scale, byte size) {
    return MO_AS(s, scale, size, 0);
  }


  // Construct a memory operand for the effective address of the array store instruction
  protected final MemoryOperand MO_AS(Instruction s, byte scale, byte size, int disp) {
    if (VM.VerifyAssertions) VM._assert(AStore.conforms(s));
    return MO_ARRAY(AStore.getArray(s),
                    AStore.getIndex(s),
                    scale,
                    size,
                    Offset.fromIntSignExtend(disp),
                    AStore.getLocation(s),
                    AStore.getGuard(s));
  }

  /**
   * Construct memory operand for an array access
   */
  private MemoryOperand MO_ARRAY(Operand base, Operand index, byte scale, byte size, Offset disp,
                                             LocationOperand loc, Operand guard) {
    if (base instanceof IntConstantOperand) {
      if (index instanceof IntConstantOperand) {
        return MO_D(disp.plus(IV(base) + (IV(index) << scale)), size, loc, guard);
      } else {
        return new MemoryOperand(null, R(index), scale, disp.plus(IV(base)), size, loc, guard);
      }
    } else {
      if (index instanceof IntConstantOperand) {
        return MO_BD(base, disp.plus(IV(index) << scale), size, loc, guard);
      } else {
        return new MemoryOperand(R(base), R(index), scale, disp, size, loc, guard);
      }
    }
  }

  /**
   * Construct memory operand for a MATERIALIZE_FP_CONSTANT
   */
  protected final MemoryOperand MO_MC(Instruction s) {
    Operand base = Binary.getVal1(s); // JTOC
    Operand val = Binary.getVal2(s); // float or double value
    if (val instanceof FloatConstantOperand) {
      FloatConstantOperand fc = (FloatConstantOperand) val;
      Offset offset = fc.offset;
      LocationOperand loc = new LocationOperand(offset);
      if (base instanceof IntConstantOperand) {
        return MO_D(offset.plus(IV(base)), DW, loc, TG());
      } else {
        return MO_BD(base, offset, DW, loc, TG());
      }
    } else {
      DoubleConstantOperand dc = (DoubleConstantOperand) val;
      Offset offset = dc.offset;
      LocationOperand loc = new LocationOperand(offset);
      if (base instanceof IntConstantOperand) {
        return MO_D(offset.plus(IV(base)), QW, loc, TG());
      } else {
        return MO_BD(Binary.getVal1(s), dc.offset, QW, loc, TG());
      }
    }
  }
}
