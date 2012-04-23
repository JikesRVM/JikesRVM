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
package org.jikesrvm.compilers.opt.ir.operand;

import org.jikesrvm.compilers.opt.OptimizingCompilerException;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;

/**
 * A memory operand.
 * Used to represent complex addrssing modes on CISC machines.
 * A memory operand contains some set of other operands that are used
 * in the address calculation.
 *
 * May contain 0, 1, or 2 RegisterOperands as well as a scale factor and
 * dispacement.
 *
 * The effective address represented by this operand is:
 *     [base] + [index]*(2^scale) + disp
 *
 * @see Operand
 */
public final class MemoryOperand extends Operand {

  /**
   * The location operand describing this memory access
   */
  public LocationOperand loc;

  /**
   * The guard operand that validates this memory access
   */
  public Operand guard;

  /**
   * The base register (may be null)
   */
  public RegisterOperand base;

  /**
   * The index register (may be null)
   */
  public RegisterOperand index;

  /**
   * The scale value (log power of 2)
   * valid values are 0,1,2,3
   */
  public byte scale;

  /**
   * The displacement
   */
  public Offset disp;

  /**
   * Number of bytes being accessed (1,2,4,8)
   */
  public byte size;

  public MemoryOperand(RegisterOperand base, RegisterOperand index, byte scale, Offset disp, byte size,
                           LocationOperand loc, Operand guard) {
    this.loc = loc;
    this.guard = guard;
    this.base = base;
    this.index = index;
    this.scale = scale;
    this.disp = disp;
    this.size = size;
    if (loc != null) loc.instruction = null;
    if (guard != null) guard.instruction = null;
    if (base != null) base.instruction = null;
    if (index != null) index.instruction = null;
  }

  // Shortcuts for some common addressing modes
  public static MemoryOperand B(RegisterOperand base, byte size, LocationOperand loc, Operand guard) {
    return new MemoryOperand(base, null, (byte) 0, Offset.zero(), size, loc, guard);
  }

  public static MemoryOperand BI(RegisterOperand base, RegisterOperand index, byte size,
                                     LocationOperand loc, Operand guard) {
    return new MemoryOperand(base, index, (byte) 0, Offset.zero(), size, loc, guard);
  }

  public static MemoryOperand BD(RegisterOperand base, Offset disp, byte size, LocationOperand loc,
                                     Operand guard) {
    return new MemoryOperand(base, null, (byte) 0, disp, size, loc, guard);
  }

  public static MemoryOperand BID(RegisterOperand base, RegisterOperand index, Offset disp, byte size,
                                      LocationOperand loc, Operand guard) {
    return new MemoryOperand(base, index, (byte) 0, disp, size, loc, guard);
  }

  public static MemoryOperand BIS(RegisterOperand base, RegisterOperand index, byte scale, byte size,
                                      LocationOperand loc, Operand guard) {
    return new MemoryOperand(base, index, scale, Offset.zero(), size, loc, guard);
  }

  public static MemoryOperand D(Address disp, byte size, LocationOperand loc, Operand guard) {
    return new MemoryOperand(null, null, (byte) 0, disp.toWord().toOffset(), size, loc, guard);
  }

  public static MemoryOperand I(RegisterOperand base, byte size, LocationOperand loc, Operand guard) {
    return new MemoryOperand(base, null, (byte) 0, Offset.zero(), size, loc, guard);
  }

  /**
   * Returns a copy of the current operand.
   */
  @Override
  public Operand copy() {
    RegisterOperand newBase = (base != null) ? (RegisterOperand) base.copy() : null;
    RegisterOperand newIndex = (index != null) ? (RegisterOperand) index.copy() : null;
    LocationOperand newLoc = (loc != null) ? (LocationOperand) loc.copy() : null;
    Operand newGuard = (guard != null) ? guard.copy() : null;
    return new MemoryOperand(newBase, newIndex, scale, disp, size, newLoc, newGuard);
  }

  /**
   * Returns if this operand is the 'same' as another operand.
   *
   * @param op other operand
   */
  @Override
  public boolean similar(Operand op) {
    if (op instanceof MemoryOperand) {
      MemoryOperand mop = (MemoryOperand) op;
      if (base == null) {
        if (mop.base != null) return false;
      } else {
        if (mop.base == null) return false;
        if (!base.similar(mop.base)) return false;
      }
      if (index == null) {
        if (mop.index != null) return false;
      } else {
        if (mop.index == null) return false;
        if (!index.similar(mop.index)) return false;
      }
      return (mop.scale == scale) && (mop.disp.EQ(disp)) && (mop.size == size);
    } else {
      return false;
    }
  }

  /**
   * Return a string rep of the operand (ie the effective address)
   */
  @Override
  public String toString() {
    String addr = (base == null) ? "<0" : "<[" + base + "]";
    if (index != null) {
      addr += "+[" + index;
      switch (scale) {
        case 0:
          addr += "]";
          break;
        case 1:
          addr += "*2]";
          break;
        case 2:
          addr += "*4]";
          break;
        case 3:
          addr += "*8]";
          break;
        default:
          OptimizingCompilerException.UNREACHABLE();
      }
    }
    if (!disp.isZero()) {
      addr += "+" + disp.toInt();
    }
    switch (size) {
      case 1:
        addr += ">B";
        break;
      case 2:
        addr += ">W";
        break;
      case 4:
        addr += ">DW";
        break;
      case 8:
        addr += ">QW";
        break;
      case 16:
        addr += ">PARAGRAPH";
        break;
      default:
        OptimizingCompilerException.UNREACHABLE();
    }
    if (loc != null && guard != null) {
      addr += " (" + loc + ", " + guard + ")";
    } else if (loc != null) {
      addr += " (" + loc + ")";
    } else if (guard != null) {
      addr += " (" + guard + ")";
    }
    return addr;
  }
}
