/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.opt.ir.*;
import com.ibm.JikesRVM.memoryManagers.vmInterface.MM_Interface;

/**
 * As part of the expansion of HIR into LIR, this compile phase
 * replaces all HIR operators that are implemented as calls to
 * VM service routines with CALLs to those routines.  
 * For some (common and performance critical) operators, we 
 * may optionally inline expand the call (depending on the 
 * the values of the relevant compiler options and/or VM_Controls).
 * This pass is also responsible for inserting write barriers
 * if we are using an allocator that requires them. Write barriers
 * are always inline expanded.
 *   
 * @author Stephen Fink
 * @author Dave Grove
 * @author Martin Trapp
 */
public final class OPT_ExpandRuntimeServices extends OPT_CompilerPhase
  implements OPT_Operators, VM_Constants, OPT_Constants {

  public boolean shouldPerform (OPT_Options options) { 
    return true; 
  }

  public final String getName () {
    return  "Expand Runtime Services";
  }

  public void reportAdditionalStats() {
    VM.sysWrite("  ");
    VM.sysWrite(container.counter1/container.counter2*100, 2);
    VM.sysWrite("% Infrequent RS calls");
  }

  /** 
   * Given an HIR, expand operators that are implemented as calls to
   * runtime service methods. This method should be called as one of the
   * first steps in lowering HIR into LIR.
   * 
   * @param OPT_IR HIR to expand
   */
  public void perform (OPT_IR ir) {
    ir.gc.resync(); // resync generation context -- yuck...
    
    OPT_Instruction next;
    for (OPT_Instruction inst = ir.firstInstructionInCodeOrder(); 
         inst != null; 
         inst = next) {
      next = inst.nextInstructionInCodeOrder();
      int opcode = inst.getOpcode();

      switch (opcode) {
        
      case NEW_opcode: { 
        OPT_TypeOperand Type = New.getClearType(inst);
        VM_Class cls = (VM_Class)Type.getVMType();
        OPT_IntConstantOperand hasFinalizer = I(cls.hasFinalizer() ? 1 : 0);
        VM_Method callSite = inst.position.getMethod();
        OPT_IntConstantOperand allocator = I(MM_Interface.pickAllocator(cls, callSite));
        OPT_IntConstantOperand align = I(VM_ObjectModel.getAlignment(cls));
        OPT_IntConstantOperand offset = I(VM_ObjectModel.getOffsetForAlignment(cls));
        VM_Method target = VM_Entrypoints.resolvedNewScalarMethod;
        Call.mutate6(inst, CALL, New.getClearResult(inst), 
                     I(target.getOffset()),
                     OPT_MethodOperand.STATIC(target),
                     I(cls.getInstanceSize()),
                     OPT_ConvertToLowLevelIR.getTIB(inst, ir, Type), 
                     hasFinalizer,
                     allocator,
                     align,
                     offset);
        if (ir.options.INLINE_NEW) {
          if (inst.getBasicBlock().getInfrequent()) container.counter1++;
          container.counter2++;
          if (!ir.options.FREQ_FOCUS_EFFORT || !inst.getBasicBlock().getInfrequent()) {
            inline(inst, ir);
          }
        }
      }
      break;

      case NEW_UNRESOLVED_opcode: {
        int typeRefId = New.getType(inst).getTypeRef().getId();
        VM_Method target = VM_Entrypoints.unresolvedNewScalarMethod;
        Call.mutate1(inst, CALL, New.getClearResult(inst), 
                     I(target.getOffset()),
                     OPT_MethodOperand.STATIC(target),
                     I(typeRefId));
      }
      break;

      case NEWARRAY_opcode: {
        OPT_TypeOperand Array = NewArray.getClearType(inst);
        VM_Array array = (VM_Array)Array.getVMType();
        OPT_Operand numberElements = NewArray.getClearSize(inst);
        boolean inline = numberElements instanceof OPT_IntConstantOperand;
        OPT_Operand width = I(array.getLogElementSize());
        OPT_Operand headerSize = I(VM_ObjectModel.computeArrayHeaderSize(array));
        VM_Method callSite = inst.position.getMethod();
        OPT_IntConstantOperand allocator = I(MM_Interface.pickAllocator(array, callSite));
        OPT_IntConstantOperand align = I(VM_ObjectModel.getAlignment(array));
        OPT_IntConstantOperand offset = I(VM_ObjectModel.getOffsetForAlignment(array));
        VM_Method target = VM_Entrypoints.resolvedNewArrayMethod;
        Call.mutate7(inst, CALL, NewArray.getClearResult(inst),  
                     I(target.getOffset()),
                     OPT_MethodOperand.STATIC(target),
                     numberElements, 
                     width,
                     headerSize,
                     OPT_ConvertToLowLevelIR.getTIB(inst, ir, Array),
                     allocator,
                     align,
                     offset);
        if (inline && ir.options.INLINE_NEW) {
          if (inst.getBasicBlock().getInfrequent()) container.counter1++;
          container.counter2++;
          if (!ir.options.FREQ_FOCUS_EFFORT || !inst.getBasicBlock().getInfrequent()) {
            inline(inst, ir);
          }
        } 
      }
      break;

      case NEWARRAY_UNRESOLVED_opcode: {
        int typeRefId = NewArray.getType(inst).getTypeRef().getId();
        OPT_Operand numberElements = NewArray.getClearSize(inst);
        VM_Method target = VM_Entrypoints.unresolvedNewArrayMethod;
        Call.mutate2(inst, CALL, NewArray.getClearResult(inst), 
                     I(target.getOffset()),
                     OPT_MethodOperand.STATIC(target),
                     numberElements, 
                     I(typeRefId));
      }
      break;

      case NEWOBJMULTIARRAY_opcode: {
        int typeRefId = NewArray.getType(inst).getTypeRef().getId();
        VM_Method target = VM_Entrypoints.optNewArrayArrayMethod;
        VM_Method callSite = inst.position.getMethod();
        Call.mutate3(inst, CALL, NewArray.getClearResult(inst),
                     I(target.getOffset()),
                     OPT_MethodOperand.STATIC(target),
                     I(callSite.getId()),
                     NewArray.getClearSize(inst),
                     I(typeRefId));
      }
      break;
        
      case ATHROW_opcode: {
        VM_Method target = VM_Entrypoints.athrowMethod;
        OPT_MethodOperand methodOp = OPT_MethodOperand.STATIC(target);
        methodOp.setIsNonReturningCall(true);   // Record the fact that this is a non-returning call.
        Call.mutate1(inst, CALL, null, I(target.getOffset()),
                     methodOp, Athrow.getClearValue(inst));
      }
      break;

      case MONITORENTER_opcode: {
        if (ir.options.NO_SYNCHRO) {
          inst.remove();
        } else {
          OPT_Operand ref = MonitorOp.getClearRef(inst);
          VM_Type refType = ref.getType().peekResolvedType();
          if (refType != null && refType.getThinLockOffset() != -1) {
            VM_Method target = VM_Entrypoints.inlineLockMethod;
            Call.mutate2(inst, CALL, null, I(target.getOffset()),
                         OPT_MethodOperand.STATIC(target),
                         MonitorOp.getClearGuard(inst), 
                         ref,
                         I(refType.getThinLockOffset()));
            if (inst.getBasicBlock().getInfrequent()) container.counter1++;
            container.counter2++;
            if (!ir.options.FREQ_FOCUS_EFFORT || !inst.getBasicBlock().getInfrequent()) {
              inline(inst, ir);
            }
          } else {
            VM_Method target = VM_Entrypoints.lockMethod;
            Call.mutate1(inst, CALL, null, I(target.getOffset()), 
                         OPT_MethodOperand.STATIC(target),
                         MonitorOp.getClearGuard(inst), 
                         ref);
          }
        }
        break;
      }

      case MONITOREXIT_opcode: {
        if (ir.options.NO_SYNCHRO) {
          inst.remove();
        } else {
          OPT_Operand ref = MonitorOp.getClearRef(inst);
          VM_Type refType = ref.getType().peekResolvedType();
          if (refType != null && refType.getThinLockOffset() != -1) {
            VM_Method target = VM_Entrypoints.inlineUnlockMethod;
            Call.mutate2(inst, CALL, null, I(target.getOffset()), 
                         OPT_MethodOperand.STATIC(target),
                         MonitorOp.getClearGuard(inst), 
                         ref,
                         I(refType.getThinLockOffset()));
            if (inst.getBasicBlock().getInfrequent()) container.counter1++;
            container.counter2++;
            if (!ir.options.FREQ_FOCUS_EFFORT || !inst.getBasicBlock().getInfrequent()) {
              inline(inst, ir);
            }
          } else {
            VM_Method target = VM_Entrypoints.unlockMethod;
            Call.mutate1(inst, CALL, null, I(target.getOffset()),
                         OPT_MethodOperand.STATIC(target),
                         MonitorOp.getClearGuard(inst), 
                         ref);
          }
        } 
      }
      break;

      case REF_ASTORE_opcode: {
        if (MM_Interface.NEEDS_WRITE_BARRIER) {
          VM_Method target = VM_Entrypoints.arrayStoreWriteBarrierMethod;
          OPT_Instruction wb =
            Call.create3(CALL, null, I(target.getOffset()),
                         OPT_MethodOperand.STATIC(target),
                         AStore.getArray(inst).copy(), 
                         AStore.getIndex(inst).copy(), 
                         AStore.getValue(inst).copy());
          wb.bcIndex = RUNTIME_SERVICES_BCI;
          wb.position = inst.position;
          inst.replace(wb);
          next = wb.nextInstructionInCodeOrder(); 
          if (ir.options.INLINE_WRITE_BARRIER) 
            inline(wb, ir, true);
        }
      }
      break;

      case PUTFIELD_opcode: {
        if (MM_Interface.NEEDS_WRITE_BARRIER) {
          OPT_LocationOperand loc = PutField.getClearLocation(inst);
          VM_FieldReference field = loc.getFieldRef();
          if (!field.getFieldContentsType().isPrimitiveType()) {
            VM_Method target = VM_Entrypoints.putfieldWriteBarrierMethod;
            OPT_Instruction wb = 
              Call.create3(CALL, null, I(target.getOffset()), 
                           OPT_MethodOperand.STATIC(target),
                           PutField.getRef(inst).copy(), 
                           PutField.getOffset(inst).copy(),
                           PutField.getValue(inst).copy());
            wb.bcIndex = RUNTIME_SERVICES_BCI;
            wb.position = inst.position;
            inst.replace(wb);
            next = wb.nextInstructionInCodeOrder();
            if (ir.options.INLINE_WRITE_BARRIER) 
              inline(wb, ir);
          }
        }
      }
      break;
          
    default:
      break;
      }
    }

    // If we actually inlined anything, clean up the mess
    if (didSomething) {
      branchOpts.perform(ir, true);
      _os.perform(ir);
    }
    // signal that we do not intend to use the gc in other phases anymore.
    ir.gc.close();
  }

  /**
   * Inline a call instruction
   */
  private void inline(OPT_Instruction inst, OPT_IR ir) {
    inline(inst,ir,false);
  }

  /**
   * Inline a call instruction
   */
  private void inline(OPT_Instruction inst, OPT_IR ir, 
                      boolean noCalleeExceptions) {
    // Save and restore inlining control state.
    // Some options have told us to inline this runtime service,
    // so we have to be sure to inline it "all the way" not
    // just 1 level.
    boolean savedInliningOption = ir.options.INLINE;
    boolean savedExceptionOption = ir.options.NO_CALLEE_EXCEPTIONS;
    ir.options.INLINE = true;
    ir.options.NO_CALLEE_EXCEPTIONS = noCalleeExceptions;
    //-#if RVM_WITH_OSR
    boolean savedOsrGI = ir.options.OSR_GUARDED_INLINING;
    ir.options.OSR_GUARDED_INLINING=false;
    //-#endif
    try {
      OPT_InlineDecision inlDec = 
        OPT_InlineDecision.YES(Call.getMethod(inst).getTarget(), 
                               "Expansion of runtime service");
      OPT_Inliner.execute(inlDec, ir, inst);
    } finally {
      ir.options.INLINE = savedInliningOption;
      ir.options.NO_CALLEE_EXCEPTIONS = savedExceptionOption;
      //-#if RVM_WITH_OSR
      ir.options.OSR_GUARDED_INLINING = savedOsrGI;
      //-#endif
    }
    didSomething = true;
  }

  private OPT_Simple _os = new OPT_Simple(false, false);
  private OPT_BranchOptimizations branchOpts = new OPT_BranchOptimizations(-1, true, true);
  private boolean didSomething = false;

  private final OPT_IntConstantOperand I(int x) { return new OPT_IntConstantOperand(x); }
  
}
