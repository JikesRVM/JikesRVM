/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.opt.ir.*;
import com.ibm.JikesRVM.*;

/**
 *  This class provides support functionality used by the generated
 * OPT_Assembler; it handles basic impedance-matching functionality
 * such as determining which addressing mode is suitable for a given
 * OPT_IA32MemoryOperand.  This class also provides some boilerplate
 * methods that do not depend on how instructions sould actually be
 * assembled, like the top-level generateCode driver.  This class is
 * not meant to be used in isolation, but rather to provide support
 * from the OPT_Assembler.
 *
 * @author Julian Dolby
 */
abstract class OPT_AssemblerBase 
    extends VM_Assembler 
    implements OPT_Operators, VM_Constants, OPT_PhysicalRegisterConstants
{
    /**
     *  This class requires no particular construction behavior; this
     * constructor simply calls super.
     *
     * @see VM_Assembler
     */
    OPT_AssemblerBase(int bytecodeSize, boolean shouldPrint) {
        super(bytecodeSize, shouldPrint);
    }

    /**
     *  Is the given operand an immediate?  In the IA32 assembly, one
     * cannot specify floating-point constants, so the possible
     * immediates we may see are OPT_IntegerConstants and
     * OPT_TrapConstants (a trap constant really is an integer), and
     * jump targets for which the exact offset is known.
     *
     * @see #getImm
     *
     * @param op the operand being queried
     * @return true if op represents an immediate
     */
    static boolean isImm(OPT_Operand op) {
        return 
            (op instanceof OPT_IntConstantOperand)
                           ||
            (op instanceof OPT_TrapCodeOperand)
                           ||
            (op instanceof OPT_BranchOperand 
                           &&
             op.asBranch().target.getmcOffset() >= 0);
    }

    /**
     *  Return the IA32 ISA encoding of the immediate value
     * represented by the the given operand.  This method assumes the
     * operand is an immediate and will likely throw a
     * ClassCastException if this not the case.  It treats
     * OPT_BranchOperands somewhat differently than isImm does: in
     * case a branch target is not resolved, it simply returns a wrong
     * answer and trusts the caller to ignore it. This behavior
     * simplifies life when generating code for ImmOrLabel operands.
     *
     * @see #isImm
     *
     * @param op the operand being queried
     * @return the immediate value represented by the operand
     */
    static int getImm(OPT_Operand op) {
        if (op instanceof OPT_BranchOperand) {
            // used by ImmOrLabel stuff
            return op.asBranch().target.getmcOffset();
        }
        else if (op instanceof OPT_TrapCodeOperand) 
            return ((OPT_TrapCodeOperand)op).getTrapCode() 
                                  +
                   VM_TrapConstants.RVM_TRAP_BASE;
        else
            return op.asIntConstant().value;
    }

    /**
     *  Is the given operand a register operand?
     *
     * @see #getReg
     *
     * @param op the operand being queried
     * @return true if op is an OPT_RegisterOperand
     */
    static boolean isReg(OPT_Operand op) {
        return (op instanceof OPT_RegisterOperand);
    }

    /**
     *  Return the machine-level register number corresponding to a
     * given OPT_Register.  The optimizing compiler has its own notion
     * of register numbers, which is not the same as the numbers used
     * by the IA32 ISA.  This method takes an optimizing compiler
     * register and translates it into the appropriate machine-level
     * encoding.  This method is not applied directly to operands, but
     * rather to register objects.
     *
     * @see #getReg
     * @see #getBase
     * @see #getIndex
     *
     * @param reg the register being queried
     * @return the 3 bit machine-level encoding of reg
     */
    static private byte getMachineRegister(OPT_Register reg) {
        int type = OPT_PhysicalRegisterSet.getPhysicalRegisterType(reg);
        if (type == INT_REG) 
            return (byte) (reg.number - FIRST_INT);
        else if (type == DOUBLE_REG)
            return (byte) (reg.number - FIRST_DOUBLE);
        else
            throw new OPT_OptimizingCompilerException("unexpected register type " + type);
    }
        
    /**
     *  Given a register operand, return the 3 bit IA32 ISA encoding
     * of that register.  This function translates an optimizing
     * compiler register operand into the 3 bit IA32 ISA encoding that
     * can be passed to the VM_Assembler.  This function assumes its
     * operand is a register operand, and will blow up if it is not;
     * use isReg to check operands passed to this method.
     *
     * @see #isReg 
     *
     * @param op the register operand being queried
     * @return the 3 bit IA32 ISA encoding of op
     */
    static byte getReg(OPT_Operand op) {
        return getMachineRegister( op.asRegister().register );
    }

    /**
     *  Given a memory operand, return the 3 bit IA32 ISA encoding
     * of its base regsiter.  This function translates the optimizing
     * compiler register operand representing the base of the given
     * memory operand into the 3 bit IA32 ISA encoding that
     * can be passed to the VM_Assembler.  This function assumes its
     * operand is a memory operand, and will blow up if it is not;
     * one should confirm an operand really has a base register before
     * invoking this method on it.
     *
     * @see #isRegDisp
     * @see #isRegIdx
     * @see #isRegInd
     *
     * @param op the register operand being queried
     * @return the 3 bit IA32 ISA encoding of the base register of op
     */
    static byte getBase(OPT_Operand op) {
        return getMachineRegister( ((OPT_MemoryOperand)op).base.register );
    }

    /**
     *  Given a memory operand, return the 3 bit IA32 ISA encoding
     * of its index regsiter.  This function translates the optimizing
     * compiler register operand representing the index of the given
     * memory operand into the 3 bit IA32 ISA encoding that
     * can be passed to the VM_Assembler.  This function assumes its
     * operand is a memory operand, and will blow up if it is not;
     * one should confirm an operand really has an index register before
     * invoking this method on it.
     *
     * @see #isRegIdx
     * @see #isRegOff
     *
     * @param op the register operand being queried
     * @return the 3 bit IA32 ISA encoding of the index register of op
     */
    static byte getIndex(OPT_Operand op) {
        return getMachineRegister(((OPT_MemoryOperand)op).index.register);
    }

    /**
     *  Given a memory operand, return the 2 bit IA32 ISA encoding
     * of its scale, suitable for passing to the VM_Assembler to mask
     * into a SIB byte.  This function assumes its operand is a memory
     * operand, and will blow up if it is not; one should confirm an
     * operand really has a scale before invoking this method on it.
     *
     * @see #isRegIdx
     * @see #isRegOff
     *
     * @param op the register operand being queried
     * @return the IA32 ISA encoding of the scale of op
     */
    static short getScale(OPT_Operand op) {
        return ((OPT_MemoryOperand)op).scale;
    }

    /**
     *  Given a memory operand, return the 2 bit IA32 ISA encoding
     * of its scale, suitable for passing to the VM_Assembler to mask
     * into a SIB byte.  This function assumes its operand is a memory
     * operand, and will blow up if it is not; one should confirm an
     * operand really has a scale before invoking this method on it.
     *
     * @see #isRegIdx
     * @see #isRegOff
     *
     * @param op the register operand being queried
     * @return the IA32 ISA encoding of the scale of op
     */
    static int getDisp(OPT_Operand op) {
        return ((OPT_MemoryOperand)op).disp;
    }

    /**
     *  Determine if a given operand is a memory operand representing
     * register-displacement mode addressing.  This method takes an
     * arbitrary operand, checks whether it is a memory operand, and,
     * if it is, checks whether it should be assembled as IA32
     * register-displacement mode.  That is, does it have a non-zero
     * displacement and a base register, but no scale and no index
     * register?
     *
     * @param op the operand being queried
     * @return true if op should be assembled as register-displacement mode
     */
    static boolean isRegDisp(OPT_Operand op) {
        if (op instanceof OPT_MemoryOperand) {
            OPT_MemoryOperand mop = (OPT_MemoryOperand) op;
            return (mop.base != null) &&
                (mop.index == null) &&
                (mop.disp != 0) &&
                (mop.scale == 0);
        } else
            return false;
    }

    /**
     *  Determine if a given operand is a memory operand representing
     * absolute mode addressing.  This method takes an
     * arbitrary operand, checks whether it is a memory operand, and,
     * if it is, checks whether it should be assembled as IA32
     * absolute address mode.  That is, does it have a non-zero
     * displacement, but no scale, no scale and no index register?
     *
     * @param op the operand being queried
     * @return true if op should be assembled as absolute mode
     */
    static boolean isAbs(OPT_Operand op) {
        if (op instanceof OPT_MemoryOperand) {
            OPT_MemoryOperand mop = (OPT_MemoryOperand) op;
            return (mop.base == null) &&
                (mop.index == null) &&
                (mop.disp != 0) &&
                (mop.scale == 0);
        } else
            return false;
    }

    /**
     *  Determine if a given operand is a memory operand representing
     * register-indirect mode addressing.  This method takes an
     * arbitrary operand, checks whether it is a memory operand, and,
     * if it is, checks whether it should be assembled as IA32
     * register-displacement mode.  That is, does it have a base
     * register, but no displacement, no scale and no index
     * register?
     *
     * @param op the operand being queried
     * @return true if op should be assembled as register-indirect mode
     */
    static boolean isRegInd(OPT_Operand op) {
        if (op instanceof OPT_MemoryOperand) {
            OPT_MemoryOperand mop = (OPT_MemoryOperand) op;
            return (mop.base != null) &&
                (mop.index == null) &&
                (mop.disp == 0) &&
                (mop.scale == 0);
        } else
            return false;
    }

    /**
     *  Determine if a given operand is a memory operand representing
     * register-offset mode addressing.  This method takes an
     * arbitrary operand, checks whether it is a memory operand, and,
     * if it is, checks whether it should be assembled as IA32
     * register-offset mode.  That is, does it have a non-zero
     * displacement, a scale parameter and an index register, but no
     * base register?
     *
     * @param op the operand being queried
     * @return true if op should be assembled as register-offset mode
     */
    static boolean isRegOff(OPT_Operand op) {
        if (op instanceof OPT_MemoryOperand) {
            OPT_MemoryOperand mop = (OPT_MemoryOperand) op;
            return (mop.base == null) &&
                (mop.index != null);
        } else
            return false;
    }

    /**
     *  Determine if a given operand is a memory operand representing
     * the full glory of scaled-index-base addressing.  This method takes an
     * arbitrary operand, checks whether it is a memory operand, and,
     * if it is, checks whether it should be assembled as IA32
     * SIB mode.  That is, does it have a non-zero
     * displacement, a scale parameter, a base register and an index
     * register?
     *
     * @param op the operand being queried
     * @return true if op should be assembled as SIB mode
     */
    static boolean isRegIdx(OPT_Operand op) {
        if (op instanceof OPT_MemoryOperand) 
            return !(isAbs(op) || isRegInd(op) || isRegDisp(op) || isRegOff(op));
        else
            return false;
    }

    /**
     *  Return the condition bits of a given optimizing compiler
     * condition operand.  This method returns the IA32 ISA bits
     * representing a given condition operand, suitable for passing to
     * the VM_Assembler to encode into the opcode of a SET, Jcc or
     * CMOV instruction.  This being IA32, there are of course
     * exceptions in the binary encoding of conditions (see FCMOV),
     * but the VM_Assembler handles that.  This function assumes its
     * argument is an OPT_IA32ConditionOperand, and will blow up if it
     * is not.
     *
     * @param op the operand being queried
     * @return the bits that (usually) represent the given condition
     * in the IA32 ISA */
    static byte getCond(OPT_Operand op) {
        return ((OPT_IA32ConditionOperand)op).value;
    }

    /**
     *  Is the given operand an IA32 condition operand?
     *
     * @param op the operand being queried
     * @return true if op is an IA32 condition operand
     */
    static boolean isCond(OPT_Operand op) {
        return (op instanceof OPT_IA32ConditionOperand);
    }

    /**
     *  Return the label representing the target of the given branch
     * operand.  These labels are used to represent branch targets
     * that have not yet been assembled, and so cannot be given
     * concrete machine code offsets.  All instructions are nunbered
     * just prior to assembly, and these numbers are used as labels.
     * This method also returns 0 (not a valid label) for int
     * constants to simplify generation of branches (the branch
     * generation code will ignore this invalid label; it is used to
     * prevent type exceptions).  This method assumes its operand is a
     * branch operand (or an int) and will blow up if it is not.
     *
     * @param op the branch operand being queried
     * @return the label representing the branch target
     */
    static int getLabel(OPT_Operand op) {
        if (op instanceof OPT_IntConstantOperand)
            // used by ImmOrLabel stuff
            return 0;
        else {
            if (op.asBranch().target.getmcOffset() < 0)
                return - op.asBranch().target.getmcOffset();
            else
                return -1;
        }
    }

    /**
     *  Is the given operand a branch target that requires a label?
     *
     * @see #getLabel
     *
     * @param op the operand being queried
     * @return true if it represents a branch requiring a label target
     */
    static boolean isLabel(OPT_Operand op) {
        return (op instanceof OPT_BranchOperand
                                &&
                op.asBranch().target.getmcOffset() < 0);
    }
    
    /**
     *  Is the given operand a branch target?
     *
     * @see #getLabel
     * @see #isLabel
     *
     * @param op the operand being queried
     * @return true if it represents a branch target
     */
    static boolean isImmOrLabel(OPT_Operand op) {
        return (isImm(op) || isLabel(op));
    }

    /**
     *  Does the given instruction operate upon byte-sized data?  The
     * opt compiler does not represent the size of register data, so
     * this method typically looks at the memory operand, if any, and
     * checks whether that is a byte.  This does not work for the
     * size-converting moves (MOVSX and MOVZX), and those instructions
     * use the operator convention that $b on the end of the operator
     * name means operate upon byte data.
     *
     * @param inst the instruction being queried
     * @return true if inst operates upon byte data
     */
    static boolean isByte(OPT_Instruction inst) {
        if (inst.operator.toString().indexOf("$b") != -1)
            return true;

        for(int i = 0; i < inst.getNumberOfOperands(); i++) {
            OPT_Operand op = inst.getOperand(i);
            if (op instanceof OPT_MemoryOperand)
                return (((OPT_MemoryOperand)op).size == 1);
        }

        return false;
    }

    /**
     *  Does the given instruction operate upon word-sized data?  The
     * opt compiler does not represent the size of register data, so
     * this method typically looks at the memory operand, if any, and
     * checks whether that is a word.  This does not work for the
     * size-converting moves (MOVSX and MOVZX), and those instructions
     * use the operator convention that $w on the end of the operator
     * name means operate upon word data.
     *
     * @param inst the instruction being queried
     * @return true if inst operates upon word data
     */
    static boolean isWord(OPT_Instruction inst) {
        if (inst.operator.toString().indexOf("$w") != -1)
            return true;

        for(int i = 0; i < inst.getNumberOfOperands(); i++) {
            OPT_Operand op = inst.getOperand(i);
            if (op instanceof OPT_MemoryOperand)
                return (((OPT_MemoryOperand)op).size == 2);
        }

        return false;
    }

    /**
     *  Does the given instruction operate upon quad-sized data?  The
     * opt compiler does not represent the size of register data, so
     * this method typically looks at the memory operand, if any, and
     * checks whether that is a byte.  This method also recognizes 
     * the operator convention that $q on the end of the operator
     * name means operate upon quad data; no operator currently uses
     * this convention.
     *
     * @param inst the instruction being queried
     * @return true if inst operates upon quad data
     */
    static boolean isQuad(OPT_Instruction inst) {
        if (inst.operator.toString().indexOf("$q") != -1)
            return true;

        for(int i = 0; i < inst.getNumberOfOperands(); i++) {
            OPT_Operand op = inst.getOperand(i);
            if (op instanceof OPT_MemoryOperand)
                return (((OPT_MemoryOperand)op).size == 8);
        }

        return false;
    }

  /**
   * Debugging support (return a printable representation of the machine code).
   *
   * @param instr, an integer to be interpreted as a PowerPC instruction
   * @param offset the mcoffset (in bytes) of the instruction
   *
   */
  public static String disasm(int instr, int offset) {
    OPT_OptimizingCompilerException.TODO("OPT_Assembler: disassembler");
    return null;
  }

  /**
   * generate machine code into ir.machinecode
   * @param ir the IR to generate
   * @return   the number of machinecode instructions generated
   */
  public static final int generateCode(OPT_IR ir, boolean shouldPrint) {
      int count = 0;

      for (OPT_Instruction p = ir.firstInstructionInCodeOrder(); 
           p != null; p = p.nextInstructionInCodeOrder()) 
      {
          p.setmcOffset( - ++count);
      }
      
      OPT_Assembler asm = new OPT_Assembler(count, shouldPrint);

      for (OPT_Instruction p = ir.firstInstructionInCodeOrder(); 
           p != null; p = p.nextInstructionInCodeOrder()) 
      {
          asm.doInst(p);
      }
      
    ir.MIRInfo.machinecode = asm.getMachineCodes();

    return ir.MIRInfo.machinecode.length();
  }

}
