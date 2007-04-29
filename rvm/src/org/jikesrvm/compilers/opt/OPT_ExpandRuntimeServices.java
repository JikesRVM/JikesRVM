/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001, 2004
 */
package org.jikesrvm.compilers.opt;

import java.lang.reflect.Constructor;
import org.jikesrvm.VM;
import org.jikesrvm.classloader.VM_Array;
import org.jikesrvm.classloader.VM_Class;
import org.jikesrvm.classloader.VM_FieldReference;
import org.jikesrvm.classloader.VM_Method;
import org.jikesrvm.classloader.VM_Type;
import org.jikesrvm.classloader.VM_TypeReference;
import static org.jikesrvm.compilers.opt.OPT_Constants.RUNTIME_SERVICES_BCI;
import org.jikesrvm.compilers.opt.ir.AStore;
import org.jikesrvm.compilers.opt.ir.Athrow;
import org.jikesrvm.compilers.opt.ir.Call;
import org.jikesrvm.compilers.opt.ir.MonitorOp;
import org.jikesrvm.compilers.opt.ir.Move;
import org.jikesrvm.compilers.opt.ir.New;
import org.jikesrvm.compilers.opt.ir.NewArray;
import org.jikesrvm.compilers.opt.ir.OPT_IR;
import org.jikesrvm.compilers.opt.ir.OPT_IRTools;
import org.jikesrvm.compilers.opt.ir.OPT_Inliner;
import org.jikesrvm.compilers.opt.ir.OPT_Instruction;
import org.jikesrvm.compilers.opt.ir.OPT_IntConstantOperand;
import org.jikesrvm.compilers.opt.ir.OPT_LocationOperand;
import org.jikesrvm.compilers.opt.ir.OPT_MethodOperand;
import org.jikesrvm.compilers.opt.ir.OPT_Operand;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.ATHROW_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.CALL;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.MONITORENTER_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.MONITOREXIT_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.NEWARRAY_UNRESOLVED_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.NEWARRAY_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.NEWOBJMULTIARRAY_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.NEW_UNRESOLVED_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.NEW_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.PUTFIELD_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.REF_ASTORE_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.REF_MOVE;
import org.jikesrvm.compilers.opt.ir.OPT_RegisterOperand;
import org.jikesrvm.compilers.opt.ir.OPT_TypeOperand;
import org.jikesrvm.compilers.opt.ir.PutField;
import org.jikesrvm.memorymanagers.mminterface.MM_Constants;
import org.jikesrvm.memorymanagers.mminterface.MM_Interface;
import org.jikesrvm.objectmodel.VM_ObjectModel;
import org.jikesrvm.runtime.VM_Entrypoints;

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
public final class OPT_ExpandRuntimeServices extends OPT_CompilerPhase {

  /**
   * Constructor for this compiler phase
   */
  private static final Constructor<OPT_CompilerPhase> constructor = getCompilerPhaseConstructor(OPT_ExpandRuntimeServices.class);

  /**
   * Get a constructor object for this compiler phase
   * @return compiler phase constructor
   */
  public Constructor<OPT_CompilerPhase> getClassConstructor() {
    return constructor;
  }

  public boolean shouldPerform (OPT_Options options) { 
    return true; 
  }

  public String getName () {
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
   * @param ir  The HIR to expand
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
        OPT_IntConstantOperand hasFinalizer = OPT_IRTools.IC(cls.hasFinalizer() ? 1 : 0);
        VM_Method callSite = inst.position.getMethod();
        OPT_IntConstantOperand allocator = OPT_IRTools.IC(MM_Interface.pickAllocator(cls, callSite));
        OPT_IntConstantOperand align = OPT_IRTools.IC(VM_ObjectModel.getAlignment(cls));
        OPT_IntConstantOperand offset = OPT_IRTools.IC(VM_ObjectModel.getOffsetForAlignment(cls));
        OPT_Operand tib = OPT_ConvertToLowLevelIR.getTIB(inst, ir, Type);
        if (VM.BuildForIA32 && VM.runningVM) {
          // shield BC2IR from address constants
          OPT_RegisterOperand tmp = ir.regpool.makeTemp(VM_TypeReference.JavaLangObjectArray);
          inst.insertBefore(Move.create(REF_MOVE, tmp, tib));
          tib = tmp.copyRO();
        }
        OPT_IntConstantOperand site = OPT_IRTools.IC(MM_Interface.getAllocationSite(true));
        VM_Method target = VM_Entrypoints.resolvedNewScalarMethod;
        Call.mutate7(inst, CALL, New.getClearResult(inst), 
                     OPT_IRTools.AC(target.getOffset()),
                     OPT_MethodOperand.STATIC(target),
                     OPT_IRTools.IC(cls.getInstanceSize()),
                     tib,
                     hasFinalizer,
                     allocator,
                     align,
                     offset,
                     site);
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
        OPT_IntConstantOperand site = OPT_IRTools.IC(MM_Interface.getAllocationSite(true));
        Call.mutate2(inst, CALL, New.getClearResult(inst), 
                     OPT_IRTools.AC(target.getOffset()),
                     OPT_MethodOperand.STATIC(target),
                     OPT_IRTools.IC(typeRefId),
		     site);
      }
      break;

      case NEWARRAY_opcode: {
        OPT_TypeOperand Array = NewArray.getClearType(inst);
        VM_Array array = (VM_Array)Array.getVMType();
        OPT_Operand numberElements = NewArray.getClearSize(inst);
        boolean inline = numberElements instanceof OPT_IntConstantOperand;
        OPT_Operand width = OPT_IRTools.IC(array.getLogElementSize());
        OPT_Operand headerSize = OPT_IRTools.IC(VM_ObjectModel.computeArrayHeaderSize(array));
        VM_Method callSite = inst.position.getMethod();
        OPT_IntConstantOperand allocator = OPT_IRTools.IC(MM_Interface.pickAllocator(array, callSite));
        OPT_IntConstantOperand align = OPT_IRTools.IC(VM_ObjectModel.getAlignment(array));
        OPT_IntConstantOperand offset = OPT_IRTools.IC(VM_ObjectModel.getOffsetForAlignment(array));
        OPT_Operand tib = OPT_ConvertToLowLevelIR.getTIB(inst, ir, Array);
        if (VM.BuildForIA32 && VM.runningVM) {
          // shield BC2IR from address constants
          OPT_RegisterOperand tmp = ir.regpool.makeTemp(VM_TypeReference.JavaLangObjectArray);
          inst.insertBefore(Move.create(REF_MOVE, tmp, tib));
          tib = tmp.copyRO();
        }
        OPT_IntConstantOperand site = OPT_IRTools.IC(MM_Interface.getAllocationSite(true));
        VM_Method target = VM_Entrypoints.resolvedNewArrayMethod;
        Call.mutate8(inst, CALL, NewArray.getClearResult(inst),  
                     OPT_IRTools.AC(target.getOffset()),
                     OPT_MethodOperand.STATIC(target),
                     numberElements, 
                     width,
                     headerSize,
                     tib,
                     allocator,
                     align,
                     offset,
                     site);
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
        OPT_IntConstantOperand site = OPT_IRTools.IC(MM_Interface.getAllocationSite(true));
        Call.mutate3(inst, CALL, NewArray.getClearResult(inst), 
                     OPT_IRTools.AC(target.getOffset()),
                     OPT_MethodOperand.STATIC(target),
                     numberElements, 
                     OPT_IRTools.IC(typeRefId),
		     site);
      }
      break;

      case NEWOBJMULTIARRAY_opcode: {
        int typeRefId = NewArray.getType(inst).getTypeRef().getId();
        VM_Method target = VM_Entrypoints.optNewArrayArrayMethod;
        VM_Method callSite = inst.position.getMethod();
        Call.mutate3(inst, CALL, NewArray.getClearResult(inst),
                     OPT_IRTools.AC(target.getOffset()),
                     OPT_MethodOperand.STATIC(target),
                     OPT_IRTools.IC(callSite.getId()),
                     NewArray.getClearSize(inst),
                     OPT_IRTools.IC(typeRefId));
      }
      break;
        
      case ATHROW_opcode: {
        VM_Method target = VM_Entrypoints.athrowMethod;
        OPT_MethodOperand methodOp = OPT_MethodOperand.STATIC(target);
        methodOp.setIsNonReturningCall(true);   // Record the fact that this is a non-returning call.
        Call.mutate1(inst, CALL, null, OPT_IRTools.AC(target.getOffset()),
                     methodOp, Athrow.getClearValue(inst));
      }
      break;

      case MONITORENTER_opcode: {
        if (ir.options.NO_SYNCHRO) {
          inst.remove();
        } else {
          OPT_Operand ref = MonitorOp.getClearRef(inst);
          VM_Type refType = ref.getType().peekResolvedType();
          if (refType != null && !refType.getThinLockOffset().isMax()) {
            VM_Method target = VM_Entrypoints.inlineLockMethod;
            Call.mutate2(inst, CALL, null, OPT_IRTools.AC(target.getOffset()),
                         OPT_MethodOperand.STATIC(target),
                         MonitorOp.getClearGuard(inst), 
                         ref,
                         OPT_IRTools.AC(refType.getThinLockOffset()));
            if (inst.getBasicBlock().getInfrequent()) container.counter1++;
            container.counter2++;
            if (!ir.options.FREQ_FOCUS_EFFORT || !inst.getBasicBlock().getInfrequent()) {
              inline(inst, ir);
            }
          } else {
            VM_Method target = VM_Entrypoints.lockMethod;
            Call.mutate1(inst, CALL, null, OPT_IRTools.AC(target.getOffset()), 
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
          if (refType != null && !refType.getThinLockOffset().isMax()) {
            VM_Method target = VM_Entrypoints.inlineUnlockMethod;
            Call.mutate2(inst, CALL, null, OPT_IRTools.AC(target.getOffset()), 
                         OPT_MethodOperand.STATIC(target),
                         MonitorOp.getClearGuard(inst), 
                         ref,
                         OPT_IRTools.AC(refType.getThinLockOffset()));
            if (inst.getBasicBlock().getInfrequent()) container.counter1++;
            container.counter2++;
            if (!ir.options.FREQ_FOCUS_EFFORT || !inst.getBasicBlock().getInfrequent()) {
              inline(inst, ir);
            }
          } else {
            VM_Method target = VM_Entrypoints.unlockMethod;
            Call.mutate1(inst, CALL, null, OPT_IRTools.AC(target.getOffset()),
                         OPT_MethodOperand.STATIC(target),
                         MonitorOp.getClearGuard(inst), 
                         ref);
          }
        } 
      }
      break;

      case REF_ASTORE_opcode: {
        if (MM_Constants.NEEDS_WRITE_BARRIER) {
          VM_Method target = VM_Entrypoints.arrayStoreWriteBarrierMethod;
          OPT_Instruction wb =
            Call.create3(CALL, null, OPT_IRTools.AC(target.getOffset()),
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
        if (MM_Constants.NEEDS_WRITE_BARRIER) {
          OPT_LocationOperand loc = PutField.getLocation(inst);
          VM_FieldReference field = loc.getFieldRef();
          if (!field.getFieldContentsType().isPrimitiveType()) {
            VM_Method target = VM_Entrypoints.putfieldWriteBarrierMethod;
            OPT_Instruction wb = 
              Call.create4(CALL, null, OPT_IRTools.AC(target.getOffset()), 
                           OPT_MethodOperand.STATIC(target),
                           PutField.getRef(inst).copy(), 
                           PutField.getOffset(inst).copy(),
                           PutField.getValue(inst).copy(),
                           OPT_IRTools.IC(field.getId()));
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
    boolean savedOsrGI = ir.options.OSR_GUARDED_INLINING;
    ir.options.OSR_GUARDED_INLINING=false;
    try {
      OPT_InlineDecision inlDec = 
        OPT_InlineDecision.YES(Call.getMethod(inst).getTarget(), 
                               "Expansion of runtime service");
      OPT_Inliner.execute(inlDec, ir, inst);
    } finally {
      ir.options.INLINE = savedInliningOption;
      ir.options.NO_CALLEE_EXCEPTIONS = savedExceptionOption;
      ir.options.OSR_GUARDED_INLINING = savedOsrGI;
    }
    didSomething = true;
  }

  private final OPT_Simple _os = new OPT_Simple(false, false);
  private final OPT_BranchOptimizations branchOpts = new OPT_BranchOptimizations(-1, true, true);
  private boolean didSomething = false;

  //private final OPT_IntConstantOperand OPT_IRTools.IC(int x) { return OPT_IRTools.OPT_IRTools.IC(x); }
  //private final OPT_AddressConstantOperand OPT_IRTools.AC(Address x) { return OPT_IRTools.OPT_IRTools.AC(x); }
  //private final OPT_AddressConstantOperand OPT_IRTools.AC(Offset x) { return OPT_IRTools.OPT_IRTools.AC(x); }
  
}
