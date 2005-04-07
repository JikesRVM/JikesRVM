/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt.ir;

import com.ibm.JikesRVM.opt.OPT_OptimizingCompilerException; 
import org.vmmagic.unboxed.*;
/**
 * A memory operand.
 * Used to represent complex addrssing modes on CISC machines.
 * A memory operand contains some set of other operands that are used
 * in the address calculation.
 * 
 * May contain 0, 1, or 2 OPT_RegisterOperands as well as a scale factor and 
 * dispacement.
 * 
 * The effective address represented by this operand is: 
 *     [base] + [index]*(2^scale) + disp
 * 
 * @see OPT_Operand
 * 
 * @author Michael Hind
 * @author Dave Grove
 */
public final class OPT_MemoryOperand extends OPT_Operand {

  /**
   * The location operand describing this memory access
   */
  public OPT_LocationOperand loc;
  
  /**
   * The guard operand that validates this memory access
   */
  public OPT_Operand guard;

  /**
   * The base register (may be null)
   */
  public OPT_RegisterOperand base;

  /**
   * The index register (may be null)
   */
  public OPT_RegisterOperand index;

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

  public OPT_MemoryOperand(OPT_RegisterOperand base, 
                    OPT_RegisterOperand index,
                    byte scale,
                    Offset disp,
                    byte size,
                    OPT_LocationOperand loc,
                    OPT_Operand guard) {
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
  public static OPT_MemoryOperand B(OPT_RegisterOperand base, 
                                    byte size, 
                                    OPT_LocationOperand loc,
                                    OPT_Operand guard) {
    return new OPT_MemoryOperand(base, null, (byte)0, Offset.zero(), size, loc, guard);
  }
  public static OPT_MemoryOperand BI(OPT_RegisterOperand base,
                                     OPT_RegisterOperand index, 
                                     byte size, 
                                     OPT_LocationOperand loc,
                                     OPT_Operand guard) {   
    return new OPT_MemoryOperand(base, index, (byte)0, Offset.zero(), size, loc, guard);
  }
  public static OPT_MemoryOperand BD(OPT_RegisterOperand base, 
                                     Offset disp, 
                                     byte size,
                                     OPT_LocationOperand loc,
                                     OPT_Operand guard) {
    return new OPT_MemoryOperand(base, null, (byte)0, disp, size, loc, guard);
  }
  public static OPT_MemoryOperand BID(OPT_RegisterOperand base, 
                                      OPT_RegisterOperand index,
                                      Offset disp, 
                                      byte size,
                                      OPT_LocationOperand loc,
                                      OPT_Operand guard) {
    return new OPT_MemoryOperand(base, index, (byte)0, disp, size, loc, guard);
  }
  public static OPT_MemoryOperand BIS(OPT_RegisterOperand base,
                                      OPT_RegisterOperand index,
                                      byte scale,
                                      byte size,
                                      OPT_LocationOperand loc,
                                      OPT_Operand guard) {
    return new OPT_MemoryOperand(base, index, scale, Offset.zero(), size, loc, guard);
  }
  public static OPT_MemoryOperand D(Address disp, 
                                    byte size,
                                    OPT_LocationOperand loc,
                                    OPT_Operand guard) {
    return new OPT_MemoryOperand(null, null, (byte)0, disp.toWord().toOffset(), size, loc, guard);
  }
  public static OPT_MemoryOperand I(OPT_RegisterOperand base,
                                    byte size,
                                    OPT_LocationOperand loc,
                                    OPT_Operand guard) {
    return new OPT_MemoryOperand(base, null, (byte)0, Offset.zero(), size, loc, guard);
  }


  /**
   * Returns a copy of the current operand.
   */
  public final OPT_Operand copy() { 
    OPT_RegisterOperand newBase = 
      (base != null) ? (OPT_RegisterOperand)base.copy() : null;
    OPT_RegisterOperand newIndex = 
      (index != null) ? (OPT_RegisterOperand)index.copy() : null;
    OPT_LocationOperand newLoc = 
      (loc != null) ? (OPT_LocationOperand)loc.copy() : null;
    OPT_Operand newGuard = (guard != null) ? guard.copy() : null;
    return new OPT_MemoryOperand(newBase, newIndex, scale, disp, size, 
                                 newLoc, newGuard);
  }


  /**
   * Returns if this operand is the 'same' as another operand.
   *
   * @param op other operand
   */
  public final boolean similar(OPT_Operand op) {
    if (op instanceof OPT_MemoryOperand) {
      OPT_MemoryOperand mop = (OPT_MemoryOperand)op;
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
  public final String toString() {
    String addr = (base==null)?"<0":"<["+base+"]";
    if (index != null) {
      addr += "+["+index;
      switch (scale) {
      case 0: addr += "]"; break;
      case 1: addr += "*2]"; break;
      case 2: addr += "*4]"; break;
      case 3: addr += "*8]"; break;
      default:
        OPT_OptimizingCompilerException.UNREACHABLE();
      }
    }
    if (!disp.isZero()) {
      addr += "+"+disp.toInt();
    }
    switch (size) {
    case 1: addr += ">B"; break;
    case 2: addr += ">W"; break;
    case 4: addr += ">DW"; break;
    case 8: addr += ">QW"; break;
    default:
      OPT_OptimizingCompilerException.UNREACHABLE();
    }
    if (loc != null && guard != null) {
      addr += " ("+loc+", "+guard+")";
    } else if (loc != null) {
      addr += " ("+loc+")";
    } else if (guard != null) {
      addr += " ("+guard+")";
    }
    return addr;
  }
}
