/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001, 2004
 */
package org.jikesrvm.compilers.opt;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.VM_NormalMethod;
import org.jikesrvm.compilers.opt.ir.Call;
import org.jikesrvm.compilers.opt.ir.Empty;
import org.jikesrvm.compilers.opt.ir.New;
import org.jikesrvm.compilers.opt.ir.OPT_IR;
import org.jikesrvm.compilers.opt.ir.OPT_Instruction;
import org.jikesrvm.compilers.opt.ir.OPT_MethodOperand;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.CALL_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.MONITORENTER_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.MONITOREXIT_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.READ_CEILING;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.SYSCALL_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.WRITE_FLOOR;
import org.jikesrvm.compilers.opt.ir.OPT_Register;
import org.jikesrvm.compilers.opt.ir.OPT_RegisterOperand;

/**
 * Replace calls to synchronized methods to calls specialized to be
 * unsynchronized.
 *
 * @author Stephen Fink
 */
public class OPT_UnsyncReplacer {
  private static final boolean DEBUG = false;

  /** 
   * Generate an instance of this class for a particular
   * instantiation site.
   *
   * @param inst the allocation site 
   * @param ir governing ir
   * @return the object, or null if illegal 
   */
  public static OPT_UnsyncReplacer getReplacer (OPT_Instruction inst, 
                                                OPT_IR ir) {
    OPT_Register r = New.getResult(inst).register;
    return  new OPT_UnsyncReplacer(r, ir.options);
  }

  /** 
   * Perform the transformation
   */
  public void transform () {
    synchronized(context){
      // first change the defs
      for (OPT_RegisterOperand def = reg.defList; def != null; 
           def = def.getNext()) {
        transform(def);
      }
      // now fix the uses
      for (OPT_RegisterOperand use = reg.useList; use != null; 
           use = use.getNext()) {
        transform(use);
      }
    }
  }

  /** 
   * @param r the register operand target of the allocation 
   * @param options controlling compiler options
   */
  private OPT_UnsyncReplacer (OPT_Register r, OPT_Options options) {
    reg = r;
    this.options = options;
  }

  /** 
   * Perform the transformation for a given register appearance
   *
   * @param rop  The def or use to check
   */
  private void transform (OPT_RegisterOperand rop) {
    OPT_Instruction inst = rop.instruction;
    switch (inst.getOpcode()) {
      case SYSCALL_opcode:
      case CALL_opcode:
        OPT_RegisterOperand invokee = Call.getParam(inst, 0).asRegister();
        if (invokee == rop) {
          // replace with equivalent call on the synthetic 
          // unsynchronized type
          OPT_MethodOperand mop = Call.getMethod(inst);
          if (mop.getTarget().isSynchronized()) {
            mop.spMethod = context.findOrCreateSpecializedVersion((VM_NormalMethod)mop.getTarget());
            if (DEBUG)
              VM.sysWrite("Identified call " + inst + " for unsynchronization\n");
          }
        }
        break;
      case MONITORENTER_opcode:
        if (DEBUG) {
          VM.sysWrite("Removing " + inst);
        }
        if (!options.NO_CACHE_FLUSH) {
          inst.insertBefore(Empty.create(READ_CEILING));
        }
        OPT_DefUse.removeInstructionAndUpdateDU(inst);
        break;
      case MONITOREXIT_opcode:
        if (DEBUG) {
          VM.sysWrite("Removing " + inst);
        }
        if (!options.NO_CACHE_FLUSH) {
          inst.insertAfter(Empty.create(WRITE_FLOOR));
        }
        OPT_DefUse.removeInstructionAndUpdateDU(inst);
        break;
      default:
        // no action necessary
        break;
    }
  }
  /**
   * The register to replace
   */
  private OPT_Register reg; 
  /**
   * Controlling compiler options
   */
  private OPT_Options options;  
  /**
   * Singleton: a single context representing "specialize this method when
   * the invokee of this method is thread-local"
   */
  private static final OPT_InvokeeThreadLocalContext context = 
    new OPT_InvokeeThreadLocalContext();
}
