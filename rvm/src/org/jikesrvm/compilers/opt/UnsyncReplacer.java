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
package org.jikesrvm.compilers.opt;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.VM_NormalMethod;
import org.jikesrvm.compilers.opt.ir.Call;
import org.jikesrvm.compilers.opt.ir.Empty;
import org.jikesrvm.compilers.opt.ir.New;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.MethodOperand;
import static org.jikesrvm.compilers.opt.ir.Operators.CALL_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.MONITORENTER_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.MONITOREXIT_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.READ_CEILING;
import static org.jikesrvm.compilers.opt.ir.Operators.SYSCALL_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.WRITE_FLOOR;
import org.jikesrvm.compilers.opt.ir.Register;
import org.jikesrvm.compilers.opt.ir.RegisterOperand;

/**
 * Replace calls to synchronized methods to calls specialized to be
 * unsynchronized.
 */
public final class UnsyncReplacer {
  private static final boolean DEBUG = false;

  /**
   * Generate an instance of this class for a particular
   * instantiation site.
   *
   * @param inst the allocation site
   * @param ir governing ir
   * @return the object, or null if illegal
   */
  public static UnsyncReplacer getReplacer(Instruction inst, IR ir) {
    Register r = New.getResult(inst).getRegister();
    return new UnsyncReplacer(r, ir.options);
  }

  /**
   * Perform the transformation
   */
  public void transform() {
    synchronized (context) {
      // first change the defs
      for (RegisterOperand def = reg.defList; def != null; def = def.getNext()) {
        transform(def);
      }
      // now fix the uses
      for (RegisterOperand use = reg.useList; use != null; use = use.getNext()) {
        transform(use);
      }
    }
  }

  /**
   * @param r the register operand target of the allocation
   * @param options controlling compiler options
   */
  private UnsyncReplacer(Register r, OptOptions options) {
    reg = r;
    this.options = options;
  }

  /**
   * Perform the transformation for a given register appearance
   *
   * @param rop  The def or use to check
   */
  private void transform(RegisterOperand rop) {
    Instruction inst = rop.instruction;
    switch (inst.getOpcode()) {
      case SYSCALL_opcode:
      case CALL_opcode:
        RegisterOperand invokee = Call.getParam(inst, 0).asRegister();
        if (invokee == rop) {
          // replace with equivalent call on the synthetic
          // unsynchronized type
          MethodOperand mop = Call.getMethod(inst);
          if (mop.getTarget().isSynchronized()) {
            mop.spMethod = context.findOrCreateSpecializedVersion((VM_NormalMethod) mop.getTarget());
            if (DEBUG) {
              VM.sysWrite("Identified call " + inst + " for unsynchronization\n");
            }
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
        DefUse.removeInstructionAndUpdateDU(inst);
        break;
      case MONITOREXIT_opcode:
        if (DEBUG) {
          VM.sysWrite("Removing " + inst);
        }
        if (!options.NO_CACHE_FLUSH) {
          inst.insertAfter(Empty.create(WRITE_FLOOR));
        }
        DefUse.removeInstructionAndUpdateDU(inst);
        break;
      default:
        // no action necessary
        break;
    }
  }

  /**
   * The register to replace
   */
  private Register reg;
  /**
   * Controlling compiler options
   */
  private OptOptions options;
  /**
   * Singleton: a single context representing "specialize this method when
   * the invokee of this method is thread-local"
   */
  private static final InvokeeThreadLocalContext context = new InvokeeThreadLocalContext();
}
