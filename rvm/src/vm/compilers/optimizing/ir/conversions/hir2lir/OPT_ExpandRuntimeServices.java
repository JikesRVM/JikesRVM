/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import instructionFormats.*;

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
 */
public final class OPT_ExpandRuntimeServices extends OPT_CompilerPhase
  implements OPT_Operators, VM_Constants, OPT_Constants {

  boolean shouldPerform (OPT_Options options) { 
    return true; 
  }

  final String getName () {
    return  "Expand Runtime Services";
  }


  /** 
   * Given an HIR, expand operators that are implemented as calls to
   * runtime service methods. This method should be called as one of the
   * first steps in lowering HIR into LIR.
   * 
   * @param OPT_IR HIR to expand
   */
  public void perform (OPT_IR ir) {
    OPT_Instruction next;
    for (OPT_Instruction inst = ir.firstInstructionInCodeOrder(); 
	 inst != null; 
	 inst = next) {
      next = inst.nextInstructionInCodeOrder();
      switch (inst.getOpcode()) {

      case NEW_opcode:
	{ 
	  OPT_TypeOperand Type = New.getClearType(inst);
	  VM_Class cls = (VM_Class)Type.type;
	  OPT_IntConstantOperand hasFinalizer;
	  if (cls.hasFinalizer()) {
	    hasFinalizer = new OPT_IntConstantOperand(1);      // true;
	  } else {
	    hasFinalizer = new OPT_IntConstantOperand(0);      // false
	  }
	  //-#if RVM_WITH_GCTk_ALLOC_ADVICE
	  // check if we are at a alloc advice site
	  if (VM.writingBootImage || GCTk_AllocAdvice.isBooted()) {
	    GCTk_AllocAdviceAttribute aadvice = GCTk_AllocAdviceAttribute.getAllocAdviceInfo(inst.position.getMethod(), inst.getBytecodeIndex());
	    if (aadvice != null) {
	      // change to alloc advice call
	      int allocNum = aadvice.getAllocator();
	      Call.mutate3(inst, CALL, New.getClearResult(inst), null,
			   OPT_MethodOperand.STATIC(VM_Entrypoints.allocAdviceQuickNewScalarMethod),
			   new OPT_IntConstantOperand(cls.getInstanceSize()),
			   OPT_ConvertToLowLevelIR.getTIB(inst, ir, Type),
			   new OPT_IntConstantOperand(allocNum));
	      // FIXME: the above doesn't use the finalizer
	    } else
	  Call.mutate3(inst, CALL, New.getClearResult(inst), null, 
		       OPT_MethodOperand.STATIC(VM_Entrypoints.quickNewScalarMethod), 
		       new OPT_IntConstantOperand(cls.getInstanceSize()),
		       OPT_ConvertToLowLevelIR.getTIB(inst, ir, Type), 
		       hasFinalizer);
	  } else {
	  //-#endif
	  Call.mutate3(inst, CALL, New.getClearResult(inst), null, 
		       OPT_MethodOperand.STATIC(VM_Entrypoints.quickNewScalarMethod), 
		       new OPT_IntConstantOperand(cls.getInstanceSize()),
		       OPT_ConvertToLowLevelIR.getTIB(inst, ir, Type), 
		       hasFinalizer);
	  //-#if RVM_WITH_GCTk_ALLOC_ADVICE
	  }
	  //-#endif
	  if (ir.options.INLINE_NEW) {
	    inline(inst, ir);
	  }
	}
	break;

      case NEW_UNRESOLVED_opcode:
	{
	  int typeRefId = New.getType(inst).type.getDictionaryId();
	  Call.mutate1(inst, CALL, New.getClearResult(inst), null,
		       OPT_MethodOperand.STATIC(VM_Entrypoints.newScalarMethod), 
		       new OPT_IntConstantOperand(typeRefId));
	  //-#if RVM_WITH_GCTk_ALLOC_ADVICE
	  if (VM.writingBootImage || GCTk_AllocAdvice.isBooted()) {
	    // overwrite the call with alloc advice version
	    GCTk_AllocAdviceAttribute aadvice = GCTk_AllocAdviceAttribute.getAllocAdviceInfo(inst.position.getMethod(), inst.getBytecodeIndex());
	    if (debug_alloc_advice)
	      GCTk_AllocAdviceAttribute.debugCall("ConvertoLowLevel", inst.position.getMethod(), inst.getBytecodeIndex(), aadvice);
	    
	    if (aadvice != null) {
	      int allocNum = aadvice.getAllocator();
	      // change to alloc advice call
	      Call.mutate2(inst, CALL, New.getClearResult(inst), null,
			   OPT_MethodOperand.STATIC(VM_Entrypoints.allocAdviceNewScalarMethod),
			   new OPT_IntConstantOperand(typeRefId), 
			   new OPT_IntConstantOperand(allocNum));
	    }
	  }
	  //-#endif
	}
	break;

      case NEWARRAY_opcode:
	{
	  OPT_TypeOperand Array = NewArray.getClearType(inst);
	  VM_Array array = (VM_Array)Array.type;
	  // TODO: Forcing early class loading is probably legal, but gets
	  // into some delicate areas of the JVM spec.  We really need a 
	  // NEWARRAY_UNRESOLVED operator & runtime method to deal with those
	  // cases in which we get an error while Array.load is attempting to
	  // load the element type.
	  // See VM_Array.load/resolve/instantiate.
	  array.load();
	  array.resolve();
	  array.instantiate();
	  int width = array.getLogElementSize();
	  OPT_Operand NumberElements = NewArray.getClearSize(inst);
	  OPT_Operand Size = null;
	  if (NumberElements instanceof OPT_RegisterOperand) {
	    OPT_RegisterOperand temp = NumberElements.asRegister();
	    if (width != 0) {
	      temp = OPT_ConvertToLowLevelIR.InsertBinary(inst, ir, INT_SHL, 
							  VM_Type.IntType, 
							  temp, 
							  new OPT_IntConstantOperand(width));
	    }
	    Size = OPT_ConvertToLowLevelIR.InsertBinary(inst, ir, INT_ADD, 
							VM_Type.IntType, temp,
							new OPT_IntConstantOperand(ARRAY_HEADER_SIZE));
	  } else { 
	    int n = NumberElements.asIntConstant().value;
	    Size = new OPT_IntConstantOperand((n<<width)+ARRAY_HEADER_SIZE);
	  }
	  //-#if RVM_WITH_GCTk_ALLOC_ADVICE
	  if (VM.writingBootImage || GCTk_AllocAdvice.isBooted()) {
	    // check if we are at a alloc advice site
	    GCTk_AllocAdviceAttribute aadvice = GCTk_AllocAdviceAttribute.getAllocAdviceInfo(inst.position.getMethod(), inst.getBytecodeIndex());
	    if (aadvice != null) {
	      // change to alloc advice call
	      int allocNum = aadvice.getAllocator();
	      Call.mutate4(inst, CALL, NewArray.getClearResult(inst), null,
			   OPT_MethodOperand.STATIC(VM_Entrypoints.allocAdviceQuickNewArrayMethod),
			   NumberElements.copy(), Size, 
			   OPT_ConvertToLowLevelIR.getTIB(inst, ir, Array),
			   new OPT_IntConstantOperand(allocNum));
	    } else
	      Call.mutate3(inst, CALL, NewArray.getClearResult(inst), null,
			   OPT_MethodOperand.STATIC(VM_Entrypoints.quickNewArrayMethod),
			   NumberElements.copy(), Size, 
			   OPT_ConvertToLowLevelIR.getTIB(inst, ir, Array));
	  } else {
	  //-#endif
	  Call.mutate3(inst, CALL, NewArray.getClearResult(inst), null, 
		       OPT_MethodOperand.STATIC(VM_Entrypoints.quickNewArrayMethod), 
		       NumberElements.copy(), Size, 
		       OPT_ConvertToLowLevelIR.getTIB(inst, ir, Array));
	  //-#if RVM_WITH_GCTk_ALLOC_ADVICE
	  }
	  //-#endif
	  if (ir.options.INLINE_NEW) {
	    inline(inst, ir);
	  } 
	}
	break;

      case NEWOBJMULTIARRAY_opcode:
	{
	  int typeRefId = NewArray.getType(inst).type.getDictionaryId();
	  //-#if RVM_WITH_GCTk_ALLOC_ADVICE
	  if (VM.writingBootImage || GCTk_AllocAdvice.isBooted()) {
	    GCTk_AllocAdviceAttribute aadvice = GCTk_AllocAdviceAttribute.getAllocAdviceInfo(inst.position.getMethod(), inst.getBytecodeIndex());
	    if (debug_alloc_advice)
	      GCTk_AllocAdviceAttribute.debugCall("ConvertoLowLevel", 
					inst.position.getMethod(),
					inst.getBytecodeIndex(), aadvice);
	    
	    if (aadvice != null) {
	      int allocNum = aadvice.getAllocator();
	      // change to alloc advice call
	      Call.mutate3(inst, CALL, NewArray.getClearResult(inst), null,
			   OPT_MethodOperand.STATIC(VM_OptLinker.allocAdviceNewArrayArrayMethod), 
			   NewArray.getClearSize(inst), 
			   new OPT_IntConstantOperand(typeRefId), 
			   new OPT_IntConstantOperand(allocNum));
	    } else
	      Call.mutate2(inst, CALL, NewArray.getClearResult(inst), null,
			   OPT_MethodOperand.STATIC(VM_OptLinker.newArrayArrayMethod),
			   NewArray.getClearSize(inst), 
			   new OPT_IntConstantOperand(typeRefId));
	  } else {
	  //-#endif
	  Call.mutate2(inst, CALL, NewArray.getClearResult(inst), null,
		       OPT_MethodOperand.STATIC(VM_OptLinker.newArrayArrayMethod), 
		       NewArray.getClearSize(inst),
		       new OPT_IntConstantOperand(typeRefId));
	  //-#if RVM_WITH_GCTk_ALLOC_ADVICE
	  }
	  //-#endif
	}
	break;
	
      case ATHROW_opcode:
	OPT_MethodOperand methodOp =
	  OPT_MethodOperand.STATIC(VM_Entrypoints.athrowMethod);

	// Record the fact that this is a non-returning call.
	methodOp.setIsNonReturningCall(true);

	Call.mutate1(inst, CALL, null, null, 
		     methodOp, Athrow.getClearValue(inst));
	break;

      case LONG_2DOUBLE_opcode: case LONG_2FLOAT_opcode:
        if (Unary.getResult(inst).isRegister()) {
          // make sure we get it as a double
          Unary.getResult(inst).asRegister().type = VM_Type.DoubleType;
        }
	Call.mutate1(inst, CALL, Unary.getClearResult(inst), null, 
		     OPT_MethodOperand.STATIC(VM_Entrypoints.longToDoubleMethod), 
		     Unary.getClearVal(inst));
	break;

      case DOUBLE_2LONG_opcode: case FLOAT_2LONG_opcode:
        if (Unary.getVal(inst).isRegister()) {
          // make sure we pass it as a double
          Unary.getVal(inst).asRegister().type = VM_Type.DoubleType;
        }
	Call.mutate1(inst, CALL, Unary.getClearResult(inst), null, 
		     OPT_MethodOperand.STATIC(VM_Entrypoints.doubleToLongMethod), 
		     Unary.getClearVal(inst));
	break;

      case MONITORENTER_opcode:
	if (ir.options.NO_SYNCHRO) {
	  inst.remove();
	} else {
	  Call.mutate1(inst, CALL, null, null, 
		       OPT_MethodOperand.STATIC(OPT_Entrypoints.optLockMethod), 
		       MonitorOp.getClearGuard(inst), 
		       MonitorOp.getClearRef(inst));
	  inline(inst, ir);
	}
	break;

      case MONITOREXIT_opcode:	
	if (ir.options.NO_SYNCHRO) {
	  inst.remove();
	} else {
	  Call.mutate1(inst, CALL, null, null, 
		       OPT_MethodOperand.STATIC(OPT_Entrypoints.optUnlockMethod), 
		       MonitorOp.getClearGuard(inst), 
		       MonitorOp.getClearRef(inst));
	  inline(inst, ir);
	} 
	break;

      case REF_ASTORE_opcode:
	//-#if RVM_WITH_CONCURRENT_GC
	Call.mutate3(inst, CALL, null, null, 
		     OPT_MethodOperand.STATIC(OPT_Entrypoints.RCGC_aastoreMethod), 
		     AStore.getClearArray(inst), 
		     AStore.getClearIndex(inst), 
		     AStore.getClearValue(inst));
	inline(inst, ir);
	//-#else
	if (VM_Collector.USES_WRITE_BARRIER) {
	  OPT_Instruction wb =
	    Call.create3(CALL, null, null, 
			 OPT_MethodOperand.STATIC(VM_Entrypoints.arrayStoreWriteBarrierMethod), 
			 AStore.getArray(inst).copy(), 
			 AStore.getIndex(inst).copy(), 
			 AStore.getValue(inst).copy());
	  wb.bcIndex = RUNTIME_SERVICES_BCI;
	  wb.position = inst.position;
	  inst.insertBefore(wb);
	  inline(wb, ir);
	  next = inst.nextInstructionInCodeOrder(); 
	}
	//-#endif
	break;

        case PUTFIELD_opcode:
          {
	    OPT_LocationOperand loc = PutField.getLocation(inst);
	    VM_Field field = loc.field;
	    if (!field.getType().isPrimitiveType()) {
	      //-#if RVM_WITH_CONCURRENT_GC
	      String className = 
		field.getDeclaringClass().getDescriptor().toString();
	      if (className.equals("LVM_BlockControl;")) {
		VM.sysWrite("Omitting barrier for method " + ir.method
			    + " field " + field + "\n");
	      } else {
		Call.mutate3(inst, CALL, null, null, 
			     OPT_MethodOperand.STATIC(OPT_Entrypoints.RCGC_resolvedPutfieldMethod), 
			     PutField.getClearRef(inst), 
			     new OPT_IntConstantOperand(field.getOffset()), 
			     PutField.getClearValue(inst));
		inline(inst, ir);
	      }
	      //-#else
	      if (VM_Collector.USES_WRITE_BARRIER) {
                OPT_Instruction wb = 
		  Call.create3(CALL, null, null, 
			       OPT_MethodOperand.STATIC(VM_Entrypoints.resolvedPutfieldWriteBarrierMethod), 
			       PutField.getRef(inst).copy(), 
			       new OPT_IntConstantOperand(field.getOffset()), 
			       PutField.getValue(inst).copy());
                wb.bcIndex = RUNTIME_SERVICES_BCI;
                wb.position = inst.position;
                inst.insertBefore(wb);
		inline(wb, ir);
                next = inst.nextInstructionInCodeOrder();
              }
	      //-#endif
	    }
	  }
	  break;

      case PUTFIELD_UNRESOLVED_opcode:
	{
	  OPT_LocationOperand loc = PutField.getLocation(inst);
	  VM_Field field = loc.field;
	  if (!field.getType().isPrimitiveType()) {
	    //-#if RVM_WITH_CONCURRENT_GC
	    Call.mutate3(inst, CALL, null, null, 
			 OPT_MethodOperand.STATIC(OPT_Entrypoints.RCGC_unresolvedPutfieldMethod), 
			 PutField.getClearRef(inst), 
			 new OPT_IntConstantOperand(field.getDictionaryId()), 
			 PutField.getClearValue(inst));
	    inline(inst, ir);
	    //-#else
	    if (VM_Collector.USES_WRITE_BARRIER) {
	      OPT_Instruction wb = 
		Call.create3(CALL, null, null, 
			     OPT_MethodOperand.STATIC(VM_Entrypoints.unresolvedPutfieldWriteBarrierMethod), 
			     PutField.getRef(inst).copy(), 
			     new OPT_IntConstantOperand(field.getDictionaryId()), 
			     PutField.getValue(inst).copy());
	      wb.bcIndex = RUNTIME_SERVICES_BCI;
	      wb.position = inst.position;
	      inst.insertBefore(wb);
	      inline(wb, ir);
	      next = inst.nextInstructionInCodeOrder();
	    }
	    //-#endif
	  }
	}
	break;

      case PUTSTATIC_opcode:
	{
	  //-#if RVM_WITH_CONCURRENT_GC
	  OPT_LocationOperand loc = PutStatic.getLocation(inst);
	  VM_Field field = loc.field;
	  if (!field.getType().isPrimitiveType()) {
	    Call.mutate2(inst, CALL, null, null, 
			 OPT_MethodOperand.STATIC(OPT_Entrypoints.RCGC_resolvedPutstaticMethod), 
			 new OPT_IntConstantOperand(field.getOffset()), 
			 PutStatic.getClearValue(inst));
	    inline(inst, ir);
	  }
	  //-#endif
	}
	break;

      case PUTSTATIC_UNRESOLVED_opcode:
	{
	  //-#if RVM_WITH_CONCURRENT_GC
	  OPT_LocationOperand loc = PutStatic.getLocation(inst);
	  VM_Field field = loc.field;
	  if (!field.getType().isPrimitiveType()) {
	    Call.mutate2(inst, CALL, null, null, 
			 OPT_MethodOperand.STATIC(OPT_Entrypoints.RCGC_unresolvedPutstaticMethod), 
			 new OPT_IntConstantOperand(field.getDictionaryId()), 
			 PutStatic.getClearValue(inst));
	    inline(inst, ir);
	  }
	  //-#endif
	}
	break;

//-#if RVM_WITH_READ_BARRIER
    case GETFIELD_opcode:
	{
	  // Sharad: load of a ref, instrument for both CBGC and cacheSimulator
	  OPT_LocationOperand loc = GetField.getLocation(inst);
	  VM_Field field = loc.field;
	  if ( (VM.CompileForIBGCinst || VM.CompileForCBGCinstrumentation || VM.CompileForDCacheSimulation) &&
	       safeToInlineForInstrumentation(field, ir) ) {
	    OPT_Instruction rb = Call.create2(CALL, null, null,
                    OPT_MethodOperand.STATIC(
		       OPT_Entrypoints.resolvedGetfieldReadBarrierMethod),
		       GetField.getRef(inst).copy(),
                       new OPT_IntConstantOperand(field.getOffset())
			 );

	    rb.bcIndex = RUNTIME_SERVICES;
	    rb.position = inst.position;
	    inst.insertBefore(rb);
	    // do inlining here and adjust next
	    inline(rb, ir);
	    next = inst.nextInstructionInCodeOrder();
	  }
	}
	break;

      case GETFIELD_UNRESOLVED_opcode:
	{
	  // Sharad: if load of a reference field, need for cache simulator
	  OPT_LocationOperand loc = GetField.getLocation(inst);
	  VM_Field field = loc.field;
	  if ( (VM.CompileForIBGCinst || VM.CompileForCBGCinstrumentation || VM.CompileForDCacheSimulation) &&
	       safeToInlineForInstrumentation(field, ir) ) {
	    OPT_Instruction rb = Call.create2(CALL, null, null,
                    OPT_MethodOperand.STATIC(
                       OPT_Entrypoints.unresolvedGetfieldReadBarrierMethod),
		       GetField.getRef(inst).copy(),
                       new OPT_IntConstantOperand(field.getDictionaryId())
			 );

	    rb.bcIndex = RUNTIME_SERVICES;
	    rb.position = inst.position;
	    inst.insertBefore(rb);
	    // do inlining here and adjust next
	    inline(rb, ir);
	    next = inst.nextInstructionInCodeOrder();
	  }
	}
	break;
//-#endif RVM_WITH_READ_BARRIER

        default:
          break;
      }
    }

    // If we actually inlined anything, clean up the mess
    if (didSomething) {
      branchOpts.perform(ir, true);
      _os.perform(ir);
    }

  }


  /**
   * Inline a call instruction
   */
  private void inline(OPT_Instruction inst, OPT_IR ir) {
    // Save and restore inlining control state.
    // Some options have told us to inline this runtime service,
    // so we have to be sure to inline it "all the way" not
    // just 1 level.
    boolean savedInliningOption = ir.options.INLINE;
    ir.options.INLINE = true;
    try {
      OPT_InlineDecision inlDec = 
	OPT_InlineDecision.YES(Call.getMethod(inst).method, 
			       "Expansion of runtime service");
      OPT_Inliner.execute(inlDec, ir, inst);
    } finally {
      ir.options.INLINE = savedInliningOption;
    }
    didSomething = true;
  }

  private OPT_Simple _os = new OPT_Simple(false, false);
  private OPT_BranchOptimizations branchOpts = new OPT_BranchOptimizations(-1);
  private boolean didSomething = false;

  //-#if RVM_WITH_GCTk_ALLOC_ADVICE
  static private final boolean debug_alloc_advice = false;
  //-#endif
}
