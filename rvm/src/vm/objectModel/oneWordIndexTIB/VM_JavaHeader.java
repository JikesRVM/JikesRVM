/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_AllocatorHeader;
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
 * In this object model, the bottom N bits of the TIB word are the
 * available bits, and the rest of the TIB word holds the index into the
 * JTOC holding the TIB reference.
 *
 * @author David Bacon
 * @author Steve Fink
 * @author Dave Grove
 */
public final class VM_JavaHeader extends VM_LockNurseryJavaHeader
  implements VM_Uninterruptible,
             VM_BaselineConstants
             //-#if RVM_WITH_OPT_COMPILER
             ,OPT_Operators
             //-#endif
{
  

  /**
   * How many bits the TIB index is shifted in the header.
   * NOTE: when this is 2 then we have slightly more efficient access
   * to the TIB, since the shifted TIB index its JTOC offset.
   */
  private static final int TIB_SHIFT = NUM_AVAILABLE_BITS;
  
  /**
   * Mask for available bits
   */  
  private static final int AVAILABLE_BITS_MASK = ~(0xffffffff << NUM_AVAILABLE_BITS);

  /**
   * Mask to extract the TIB index
   */
  private static final int TIB_MASK = ~(AVAILABLE_BITS_MASK | HASH_STATE_MASK);

  static {
    if (VM.VerifyAssertions) {
      VM._assert(VM_MiscHeader.REQUESTED_BITS + VM_AllocatorHeader.REQUESTED_BITS <= NUM_AVAILABLE_BITS);
    }
  }

  /**
   * Get the TIB for an object.
   */
  public static Object[] getTIB(Object o) { 
    int tibWord = VM_Magic.getIntAtOffset(o,TIB_OFFSET);
    if (VM_Collector.MOVES_OBJECTS) {
      int fmask = tibWord & VM_AllocatorHeader.GC_FORWARDING_MASK;
      if (fmask != 0 && fmask == VM_AllocatorHeader.GC_FORWARDED) {
        int forwardPtr = tibWord & ~VM_AllocatorHeader.GC_FORWARDING_MASK;
        tibWord = VM_Magic.getIntAtOffset(VM_Magic.addressAsObject(VM_Address.fromInt(forwardPtr)), TIB_OFFSET);
      }
    }      
    int offset = (tibWord & TIB_MASK) >>> (TIB_SHIFT - 2);
    return VM_Magic.addressAsObjectArray(VM_Magic.getMemoryAddress(VM_Magic.getTocPointer().add(offset)));
  }
  
  /**
   * Set the TIB for an object.
   */
  public static void setTIB(Object ref, Object[] tib) throws VM_PragmaInline {
    int idx = VM_Magic.objectAsType(tib[0]).getTibSlot() << TIB_SHIFT;
    if (VM.VerifyAssertions) VM._assert((idx & TIB_MASK) == idx);
    int tibWord = (VM_Magic.getIntAtOffset(ref, TIB_OFFSET) & ~TIB_MASK) | idx;
    VM_Magic.setIntAtOffset(ref, TIB_OFFSET, tibWord);

  }

  /**
   * Set the TIB for an object.
   * Note: Beware; this function clears the additional bits.
   */
  public static void setTIB(BootImageInterface bootImage, int refOffset, VM_Address tibAddr, VM_Type type) {
    VM_Word idx = VM_Word.fromIntZeroExtend(type.getTibSlot()).lsh(TIB_SHIFT);
    if (VM.VerifyAssertions) VM._assert((idx.toInt() & TIB_MASK) == idx);
    bootImage.setAddressWord(refOffset + TIB_OFFSET, idx);
  }

  /**
   * Process the TIB field during copyingGC.
   */
  public static void gcProcessTIB(VM_Address ref) {
    // nothing to do (TIB is not a pointer)
  }


  public static void gcProcessTIB(VM_Address ref, boolean root) {
    // nothing to do (TIB is not a pointer)
  }

  /*
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
   * DANGER: this function destroys R0 if VM_Collector.MOVES_OBJECTS !!!!
   *         
   * @param asm the assembler object to emit code with
   * @param dest the number of the destination register
   * @param object the number of the register holding the object reference
   */
  //-#if RVM_FOR_POWERPC
  public static void baselineEmitLoadTIB(VM_Assembler asm, int dest, 
                                         int object) {
    if (VM.VerifyAssertions) VM._assert(TIB_SHIFT == 2);
    int ME = 31 - TIB_SHIFT;
    int MB = HASH_STATE_BITS;
    if (VM_Collector.MOVES_OBJECTS && VM.writingBootImage) {
      if (VM.VerifyAssertions) {
        VM._assert(dest != 0);
        VM._assert(VM_AllocatorHeader.GC_FORWARDING_MASK == 0x00000003);
      }
      asm.emitAddr  (dest, TIB_OFFSET, object);
      asm.emitANDI(0, dest, VM_AllocatorHeader.GC_FORWARDING_MASK);
      asm.emitBEQ (5);  // if dest & FORWARDING_MASK == 0; then dest has a valid tib index
      asm.emitCMPI(0, VM_AllocatorHeader.GC_FORWARDED);
      asm.emitBNE (3); 
      // It really has been forwarded; chase the forwarding pointer and get the tib word from there.
      asm.emitRLAddrINM(dest, dest, 0, 0, 29);    // mask out bottom two bits of forwarding pointer
      asm.emitLAddr    (dest, TIB_OFFSET, dest); // get TIB word from forwarded object
      // The following clears the high and low-order bits. See p.119 of PowerPC book
      // Because TIB_SHIFT is 2 the masked value is a JTOC offset.
      asm.emitRLAddrINM(dest, dest, 0, MB, ME); 
      asm.emitLAddrX(dest,JTOC,dest);
    } else {
      asm.emitLAddr(dest, TIB_OFFSET, object);
      // The following clears the high and low-order bits. See p.119 of PowerPC book
      // Because TIB_SHIFT is 2 the masked value is a JTOC offset.
      asm.emitRLAddrINM(dest, dest, 0, MB, ME);
      asm.emitLAddrX(dest,JTOC,dest);
    }
  }
  //-#elif RVM_FOR_IA32
  public static void baselineEmitLoadTIB(VM_Assembler asm, byte dest, 
                                         byte object) {
    if (VM.VerifyAssertions) VM._assert(TIB_SHIFT == 2);
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
      asm.emitMOV_Reg_RegIdx(dest, JTOC, dest, asm.BYTE, 0);
    } else {
      asm.emitMOV_Reg_RegDisp(dest, object, TIB_OFFSET);
      asm.emitAND_Reg_Imm(dest,TIB_MASK);
      asm.emitMOV_Reg_RegIdx(dest, JTOC, dest, asm.BYTE, 0);
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
    if (VM.VerifyAssertions) VM._assert(TIB_SHIFT == 2);
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
    OPT_RegisterOperand tibOffset = OPT_ConvertToLowLevelIR.InsertBinary(s, ir, INT_AND, 
                                                                         VM_Type.IntType, headerWord, 
                                                                         new OPT_IntConstantOperand(TIB_MASK));
    Load.mutate(s, INT_LOAD, result, ir.regpool.makeJTOCOp(ir,s), tibOffset, null);
  }
  //-#endif
}
