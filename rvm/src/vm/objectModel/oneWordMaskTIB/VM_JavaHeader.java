/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.memoryManagers.mmInterface.VM_AllocatorHeader;
//-#if RVM_WITH_OPT_COMPILER
import com.ibm.JikesRVM.opt.*;
import com.ibm.JikesRVM.opt.ir.*;
//-#endif

/**
 * Defines the JavaHeader portion of the object header for the 
 * JikesRVM object model. <p>
 * This object model uses a one-word header for most scalar objects, and
 * a two-word header for scalar objects of classes with synchronized
 * methods<p>
 *
 * In this object model, the bottom N (N<=2) bits of the TIB word are the
 * available bits, and TIBs are aligned. So to acquire the TIB, we mask
 * the bottom N bits.
 *
 * @author David Bacon
 * @author Steve Fink
 * @author Dave Grove
 */
public final class VM_JavaHeader extends VM_LockNurseryJavaHeader
  implements Uninterruptible
             //-#if RVM_WITH_OPT_COMPILER
             ,OPT_Operators
             //-#endif
{

  /**
   * The number of low-order-bits of TIB pointer that must be zero.
   */
  static final int LOG_TIB_ALIGNMENT = NUM_AVAILABLE_BITS + HASH_STATE_BITS <= 2 ? 2 : NUM_AVAILABLE_BITS + HASH_STATE_BITS;

  /**
   * The byte alignment of a TIB pointer.
   */
  static final int TIB_ALIGNMENT = 1 << LOG_TIB_ALIGNMENT;

  /**
   * The mask that defines the TIB value in the one-word header.
   */
  private static final int TIB_MASK = 0xffffffff << LOG_TIB_ALIGNMENT;

  /**
   * The mask that defines the available bits the one-word header.
   */
  private static final int BITS_MASK = (~TIB_MASK) & (~HASH_STATE_MASK);

  static {
    if (VM.VerifyAssertions) {
      VM._assert(VM_MiscHeader.REQUESTED_BITS + VM_AllocatorHeader.REQUESTED_BITS <= NUM_AVAILABLE_BITS);
    }
  }

  /**
   * Get the TIB for an object.
   */
  public static Object[] getTIB(Object o) throws InlinePragma { 
    int tibWord = VM_Magic.getIntAtOffset(o,TIB_OFFSET);
    if (VM_Collector.MOVES_OBJECTS) {
      int fmask = tibWord & VM_AllocatorHeader.GC_FORWARDING_MASK;
      if (fmask != 0 && fmask == VM_AllocatorHeader.GC_FORWARDED) {
        int forwardPtr = tibWord & ~VM_AllocatorHeader.GC_FORWARDING_MASK;
        tibWord = VM_Magic.getIntAtOffset(VM_Magic.addressAsObject(Address.fromInt(forwardPtr)), TIB_OFFSET);
      }
    }      
    tibWord &= TIB_MASK;
    return VM_Magic.addressAsObjectArray(Address.fromInt(tibWord));
  }
  
  /**
   * Set the TIB for an object.
   */
  public static void setTIB(Object ref, Object[] tib) throws InlinePragma {
    int tibPtr = VM_Magic.objectAsAddress(tib).toInt();
    if (VM.VerifyAssertions) {
      VM._assert((tibPtr & BITS_MASK) == 0);
    }
    int tibWord = (VM_Magic.getIntAtOffset(ref, TIB_OFFSET) & BITS_MASK) | tibPtr;
    VM_Magic.setIntAtOffset(ref, TIB_OFFSET, tibWord);
  }

  /**
   * Set the TIB for an object.
   * Note: Beware; this function clears the additional bits.
   */
  public static void setTIB(BootImageInterface bootImage, int refOffset, Address tibAddr, VM_Type type) {
    bootImage.setAddressWord(refOffset + TIB_OFFSET, tibAddr.toWord());
  }

  /**
   * Process the TIB field during copyingGC.
   */
  public static void gcProcessTIB(Address ref) {
    int tibWord    = tibAddress.loadWord();
    int savedBits  = tibWord & ~TIB_MASK;
    int tibNew     = MM_Interface.processPtrValue(Address.fromInt(tibWord & TIB_MASK)).toInt();
    ref.store(tibNew | savedBits, TIB_OFFSET);
  }

  public static void gcProcessTIB(Address ref, boolean root) {
    VM._assert(false);  // hard to match default model - fix this later
  }

  /*
   * NOTE: 
   * The collector may have laid down a forwarding pointer 
   * in place of the TIB word.  Check for this fringe case
   * and handle it by following the forwarding pointer to
   * find the TIB.
   * We only have to build this extra check into the bootimage code
   * since all code used by the collector should be in the bootimage.
   * TODO: be more selective by marking a subset of the bootimage classes
   *       with a special interface that indicates that this conditional redirect 
   *       is required.
   */

  /**
   * The following method will emit code that moves a reference to an
   * object's TIB into a destination register.
   *
   * @param asm the assembler object to emit code with
   * @param dest the number of the destination register
   * @param object the number of the register holding the object reference
   */
  //-#if RVM_FOR_POWERPC
  public static void baselineEmitLoadTIB(VM_Assembler asm, int dest, 
                                         int object) {
    int ME = 31 - LOG_TIB_ALIGNMENT;
    if (VM_Collector.MOVES_OBJECTS && VM.writingBootImage) {
      if (VM.VerifyAssertions) {
        VM._assert(dest != 0);
        VM._assert(VM_AllocatorHeader.GC_FORWARDING_MASK == 0x00000003);
      }
      asm.emitLAddr  (dest, TIB_OFFSET, object);
      asm.emitANDI(0, dest, VM_AllocatorHeader.GC_FORWARDING_MASK);
      asm.emitBEQ (5);  // if dest & FORWARDING_MASK == 0; then dest has a valid tib index
      asm.emitCMPI(0, VM_AllocatorHeader.GC_FORWARDED);
      asm.emitBNE (3); 
      // It really has been forwarded; chase the forwarding pointer and get the tib word from there.
      asm.emitRLAddrINM(dest, dest, 0, 0, 29);    // mask out bottom two bits of forwarding pointer
      asm.emitLAddr    (dest, TIB_OFFSET, dest); // get TIB word from forwarded object
      // The following clears the high and low-order bits. See p.119 of PowerPC book
      // Because TIB_SHIFT is 2 the masked value is a JTOC offset.
      asm.emitRLAddrINM(dest, dest, 0, 0, ME);
    } else {
      asm.emitLAddr(dest, TIB_OFFSET, object);
      asm.emitRLAddrINM(dest, dest, 0, 0, ME);
    }
  }
  //-#elif RVM_FOR_IA32
  public static void baselineEmitLoadTIB(VM_Assembler asm, byte dest, 
                                         byte object) {
    if (VM_Collector.MOVES_OBJECTS && VM.writingBootImage) {
      if (VM.VerifyAssertions) {
        VM._assert(VM_AllocatorHeader.GC_FORWARDING_MASK == 0x00000003);
        VM._assert(dest != object);
      }
      asm.emitMOV_Reg_RegDisp(dest, object, TIB_OFFSET);
      asm.emitTEST_Reg_Imm(dest, VM_AllocatorHeader.GC_FORWARDING_MASK);
      VM_ForwardReference fr1 = asm.forwardJcc(asm.EQ);
      asm.emitAND_Reg_Imm(dest, VM_AllocatorHeader.GC_FORWARDING_MASK);
      asm.emitCMP_Reg_Imm(dest, VM_AllocatorHeader.GC_FORWARDED);
      asm.emitMOV_Reg_RegDisp(dest, object, TIB_OFFSET);
      VM_ForwardReference fr2 = asm.forwardJcc(asm.NE);
      // It really has been forwarded; chase the forwarding pointer and get the tib word from there.
      asm.emitAND_Reg_Imm(dest, ~VM_AllocatorHeader.GC_FORWARDING_MASK);
      asm.emitMOV_Reg_RegDisp(dest, dest, TIB_OFFSET);
      fr1.resolve(asm);
      fr2.resolve(asm);
      asm.emitAND_Reg_Imm(dest,TIB_MASK);
    } else {
      asm.emitMOV_Reg_RegDisp(dest, object, TIB_OFFSET);
      asm.emitAND_Reg_Imm(dest,TIB_MASK);
    }
  }
  //-#endif

  //-#if RVM_WITH_OPT_COMPILER
  /**
   * Mutate a GET_OBJ_TIB instruction to the LIR
   * instructions required to implement it.
   * 
   * @param s the GET_OBJ_TIB instruction to lower
   * @param ir the enclosing OPT_IR
   */
  public static void lowerGET_OBJ_TIB(OPT_Instruction s, OPT_IR ir) {
    OPT_RegisterOperand result = GuardedUnary.getClearResult(s);
    OPT_Operand guard = GuardedUnary.getClearGuard(s);
    OPT_RegisterOperand headerWord = OPT_ConvertToLowLevelIR.InsertLoadOffset(s, ir, INT_LOAD, 
                                                                              VM_Type.IntType, 
                                                                              GuardedUnary.getClearVal(s),
                                                                              TIB_OFFSET, guard);
    if (VM_Collector.MOVES_OBJECTS && !ir.compiledMethod.getMethod().isInterruptible()) {
      // Uninterruptible code may be part of the GC subsytem so it needs to robustly handle
      // the TIB word holding a forwarding pointer.  If the method is interruptible, then
      // it can't be executing during GC time and therefore does not need these extra instructions
      if (VM.VerifyAssertions) {
        VM._assert(VM_AllocatorHeader.GC_FORWARDING_MASK == 0x00000003);
      }

      OPT_BasicBlock prevBlock = s.getBasicBlock();
      OPT_BasicBlock doneBlock = prevBlock.splitNodeAt(s.prevInstructionInCodeOrder(), ir);
      OPT_BasicBlock middleBlock = doneBlock.createSubBlock(s.bcIndex, ir);
      ir.cfg.linkInCodeOrder(prevBlock, middleBlock);
      ir.cfg.linkInCodeOrder(middleBlock, doneBlock);
      prevBlock.insertOut(middleBlock);
      prevBlock.insertOut(doneBlock);
      middleBlock.insertOut(doneBlock);

      OPT_RegisterOperand fs = ir.regpool.makeTempInt();
      prevBlock.appendInstruction(Binary.create(INT_AND, fs, headerWord.copyRO(),
                                                new OPT_IntConstantOperand(VM_AllocatorHeader.GC_FORWARDING_MASK)));
      prevBlock.appendInstruction(IfCmp.create(INT_IFCMP, null, fs.copyRO(), new OPT_IntConstantOperand(VM_AllocatorHeader.GC_FORWARDED),
                                               OPT_ConditionOperand.NOT_EQUAL(), doneBlock.makeJumpTarget(),
                                               OPT_BranchProfileOperand.likely()));
      OPT_RegisterOperand fp = ir.regpool.makeTempInt();
      middleBlock.appendInstruction(Binary.create(INT_AND, fp, headerWord.copyRO(), 
                                                  new OPT_IntConstantOperand(~VM_AllocatorHeader.GC_FORWARDING_MASK)));
      middleBlock.appendInstruction(Load.create(INT_LOAD, headerWord.copyRO(), fp.copyRO(), 
                                                new OPT_IntConstantOperand(TIB_OFFSET), null, guard));
    }
    Binary.mutate(s, INT_AND, result, headerWord.copyRO(), new OPT_IntConstantOperand(TIB_MASK));
  }

  //-#endif
}
