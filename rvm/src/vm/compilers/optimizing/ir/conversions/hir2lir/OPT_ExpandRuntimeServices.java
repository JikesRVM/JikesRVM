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
 * @author Martin Trapp
 */
public final class OPT_ExpandRuntimeServices extends OPT_CompilerPhase
  implements OPT_Operators, VM_Constants, OPT_Constants {

  boolean shouldPerform (OPT_Options options) { 
    return true; 
  }

  final String getName () {
    return  "Expand Runtime Services";
  }

  final boolean printingEnabled(OPT_Options options, boolean before) {
    return false;
  }

  /** 
   * Given an HIR, expand operators that are implemented as calls to
   * runtime service methods. This method should be called as one of the
   * first steps in lowering HIR into LIR.
   * 
   * @param OPT_IR HIR to expand
   */
  public void perform (OPT_IR ir) {
    
    // resync gc
    ir.gc.resync();
    
    // String name = ir.method.getDeclaringClass() + "." + ir.method.getName() + ir.method.getDescriptor();

    OPT_Instruction next;
    for (OPT_Instruction inst = ir.firstInstructionInCodeOrder(); 
	 inst != null; 
	 inst = next) {
      next = inst.nextInstructionInCodeOrder();
      int opcode = inst.getOpcode();

      switch (opcode) {

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
	  inline(inst, ir, true);
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


/* 
--------- START TEMP STUFF ----------------
partial fragmented array stuff 
	      OPT_BasicBlock beforeBB = inst.getBasicBlock();           
	      OPT_BasicBlock afterBB = beforeBB.splitNodeAt(inst.getPrev(), ir);  
	      ir.cfg.insertAfterInCodeOrder(beforeBB, afterBB);
	      OPT_BasicBlock negativeBB = beforeBB.createSubBlock(inst.bcIndex, ir); 
	      beforeBB.insertOut(negativeBB);   // unusual path
	      negativeBB.insertOut(afterBB);    // unusual path
	      ir.cfg.addLastInCodeOrder(negativeBB);
	      negativeBB.setInfrequent(true);
	      // Modify the before block to branch to negatiev block if necessary
	      OPT_RegisterOperand array_length = ir.gc.temps.makeTempInt(); // logical length
	      inst.insertBefore(GuardedUnary.create(ARRAYLENGTH, array_length,  
						    BoundsCheck.getClearRef(inst),
						    BoundsCheck.getClearGuard(inst)));
	      inst.insertBefore(IfCmp.create(INT_IFCMP, null,
					     array_length, 
					     new OPT_IntConstantOperand(0),
					     OPT_ConditionOperand.LESS(),
					     negativeBB.makeJumpTarget(),
					     OPT_BranchProfileOperand.unlikely()));
--------- END TEMP STUFF ----------------
*/


      case INT_ALOAD_opcode:
      case LONG_ALOAD_opcode:
      case FLOAT_ALOAD_opcode:
      case DOUBLE_ALOAD_opcode:
      case REF_ALOAD_opcode:
      case BYTE_ALOAD_opcode:
      case UBYTE_ALOAD_opcode:
      case USHORT_ALOAD_opcode:
      case SHORT_ALOAD_opcode:
	  if (VM_Configuration.BuildWithLazyRedirect) {
	      // This barrier completely replaces all access instruction.
	      OPT_Operand origArray = AStore.getClearArray(inst);
	      OPT_RegisterOperand newArray = ir.gc.temps.makeTemp(origArray);
	      OPT_Instruction redirectInst = GuardedUnary.create(GET_OBJ_RAW, newArray, origArray, OPT_IRTools.TG());
	      redirectInst.bcIndex = inst.bcIndex;
	      inst.insertBefore(redirectInst);
	      ALoad.setArray(inst, newArray);  // let standard conversion take care of this
	  }
	  else if (VM_Configuration.BuildWithEagerRedirect) {
	      // This barrier completely replaces just REF_ALOAD
	      if (opcode == REF_ALOAD_opcode) {
		  OPT_RegisterOperand result = ALoad.getClearResult(inst);
		  OPT_RegisterOperand temp = ir.gc.temps.makeTemp(result);
		  ALoad.setResult(inst,temp);
		  OPT_BasicBlock beforeBB = inst.getBasicBlock();           
		  OPT_BasicBlock redirectBB = beforeBB.createSubBlock(inst.bcIndex, ir); 
		  OPT_BasicBlock afterBB = beforeBB.splitNodeAt(inst, ir);  
		  ir.cfg.linkInCodeOrder(beforeBB, redirectBB);
		  ir.cfg.linkInCodeOrder(redirectBB, afterBB);
		  beforeBB.insertOut(afterBB);   // unusual path
		  OPT_Instruction ifInst = IfCmp.create(INT_IFCMP, null, temp,
							new OPT_IntConstantOperand(0),  // NULL is 0
							OPT_ConditionOperand.EQUAL(),
							afterBB.makeJumpTarget(),
							OPT_BranchProfileOperand.unlikely());
		  OPT_Instruction moveInst = Move.create(INT_MOVE, result, temp); // no REF_MOVE in lir
		  OPT_Instruction redirectInst = GuardedUnary.create(GET_OBJ_RAW, result, temp, OPT_IRTools.TG());
		  ifInst.bcIndex = moveInst.bcIndex = redirectInst.bcIndex = inst.bcIndex;
		  beforeBB.appendInstruction(moveInst);
		  beforeBB.appendInstruction(ifInst);
		  redirectBB.appendInstruction(redirectInst);
		  next = inst.nextInstructionInCodeOrder();   // needed since we split blocks
	      }
	  }
	  break;
      case INT_ASTORE_opcode:
      case LONG_ASTORE_opcode:
      case FLOAT_ASTORE_opcode:
      case DOUBLE_ASTORE_opcode:
      case REF_ASTORE_opcode:
      case BYTE_ASTORE_opcode:
      case SHORT_ASTORE_opcode:
	  if (VM_Configuration.BuildWithLazyRedirect) {
	      // This barrier completely replaces all access instruction.
	      OPT_Operand origArray = AStore.getClearArray(inst);
	      OPT_RegisterOperand newArray = ir.gc.temps.makeTemp(origArray);
	      OPT_Instruction redirectInst1 = GuardedUnary.create(GET_OBJ_RAW, newArray, origArray, OPT_IRTools.TG());
	      redirectInst1.bcIndex = inst.bcIndex;
	      inst.insertBefore(redirectInst1);
	      AStore.setArray(inst, newArray);
	      if (opcode == REF_ASTORE_opcode) {
		  OPT_Operand value = AStore.getClearValue(inst);
		  OPT_RegisterOperand temp = ir.gc.temps.makeTemp(value);
		  AStore.setValue(inst,temp);
		  OPT_BasicBlock beforeBB = inst.getBasicBlock();           
		  OPT_BasicBlock redirectBB = beforeBB.createSubBlock(inst.bcIndex, ir); 
		  OPT_BasicBlock afterBB = beforeBB.splitNodeAt(inst.getPrev(), ir);  
		  ir.cfg.linkInCodeOrder(beforeBB, redirectBB);
		  ir.cfg.linkInCodeOrder(redirectBB, afterBB);
		  beforeBB.insertOut(afterBB);   // unusual path
		  OPT_Instruction ifInst = IfCmp.create(INT_IFCMP, null, temp,
							new OPT_IntConstantOperand(0),  // NULL is 0
							OPT_ConditionOperand.EQUAL(),
							afterBB.makeJumpTarget(),
							OPT_BranchProfileOperand.unlikely());
		  OPT_Instruction moveInst = Move.create(INT_MOVE, temp, value); // no REF_MOVE in lir
		  OPT_Instruction redirectInst = GuardedUnary.create(GET_OBJ_RAW, temp, value, OPT_IRTools.TG());
		  ifInst.bcIndex = moveInst.bcIndex = redirectInst.bcIndex = inst.bcIndex;
		  beforeBB.appendInstruction(moveInst);
		  beforeBB.appendInstruction(ifInst);
		  redirectBB.appendInstruction(redirectInst);
		  next = inst.nextInstructionInCodeOrder();   // needed since we split blocks
	      }
	  }
	  else if (VM_Configuration.BuildWithEagerRedirect) {
	      // no modifications needed for eager version on stores
	  }
	  
	  else if (opcode == REF_ASTORE_opcode) {
	//-#if RVM_WITH_CONCURRENT_GC
	      Call.mutate3(inst, CALL, null, null, 
			   OPT_MethodOperand.STATIC(OPT_Entrypoints.RCGC_aastoreMethod), 
			   AStore.getClearArray(inst), 
			   AStore.getClearIndex(inst), 
			   AStore.getClearValue(inst));
	      inline(inst, ir);
        //-#else
	      if (VM_Collector.NEEDS_WRITE_BARRIER) {
		  OPT_Instruction wb =
		      Call.create3(CALL, null, null, 
				   OPT_MethodOperand.STATIC(VM_Entrypoints.arrayStoreWriteBarrierMethod), 
				   AStore.getArray(inst).copy(), 
				   AStore.getIndex(inst).copy(), 
				   AStore.getValue(inst).copy());
		  wb.bcIndex = RUNTIME_SERVICES_BCI;
		  wb.position = inst.position;
		  inst.insertBefore(wb);
		  inline(wb, ir, true);
		  next = inst.nextInstructionInCodeOrder(); 
	      }
	//-#endif
	  }
	break;

    case GETFIELD_opcode:
    case GETFIELD_UNRESOLVED_opcode:
	{
	  if ((VM_Configuration.BuildWithLazyRedirect ||
	      VM_Configuration.BuildWithEagerRedirect)) {
	      // Leave the opcode so we don't have to handle UNRESOLVED stuff
	      OPT_LocationOperand dataLoc = GetField.getLocation(inst);
	      VM_Field dataField = dataLoc.field;
	      OPT_Operand origObject = GetField.getClearRef(inst);
	      if (VM_Configuration.BuildWithLazyRedirect) {
		  OPT_RegisterOperand realObject = ir.gc.temps.makeTemp(origObject);
		  OPT_Instruction redirectInst = GuardedUnary.create(GET_OBJ_RAW, realObject, origObject, 
								     GetField.getGuard(inst).copy());
		  redirectInst.bcIndex = inst.bcIndex;
		  inst.insertBefore(redirectInst);
		  GetField.setRef(inst,realObject);
	      }
	      else {  // VM_Configuration.BuildWithEagerRedirect
		  if (!dataField.getType().isPrimitiveType()) {
		      OPT_RegisterOperand result = GetField.getClearResult(inst);
		      OPT_RegisterOperand temp = ir.gc.temps.makeTemp(result);
		      GetField.setResult(inst,temp);
		      OPT_BasicBlock beforeBB = inst.getBasicBlock();           
		      OPT_BasicBlock redirectBB = beforeBB.createSubBlock(inst.bcIndex, ir); 
		      OPT_BasicBlock afterBB = beforeBB.splitNodeAt(inst, ir);  
		      ir.cfg.linkInCodeOrder(beforeBB, redirectBB);
		      ir.cfg.linkInCodeOrder(redirectBB, afterBB);
		      beforeBB.insertOut(afterBB);   // unusual path
		      OPT_Instruction ifInst = IfCmp.create(INT_IFCMP, null, temp,
							    new OPT_IntConstantOperand(0),  // NULL is 0
							    OPT_ConditionOperand.EQUAL(),
							    afterBB.makeJumpTarget(),
							    OPT_BranchProfileOperand.unlikely());
		      OPT_Instruction moveInst = Move.create(INT_MOVE, result, temp); // no REF_MOVE in lir
		      OPT_Instruction redirectInst = GuardedUnary.create(GET_OBJ_RAW, result, temp, OPT_IRTools.TG());
		      ifInst.bcIndex = moveInst.bcIndex = redirectInst.bcIndex = inst.bcIndex;
		      beforeBB.appendInstruction(moveInst);
		      beforeBB.appendInstruction(ifInst);
		      redirectBB.appendInstruction(redirectInst);
		  }
	      }
	  }
	  else { 
	      //-#if RVM_WITH_READ_BARRIER2
            if ( (VM.CompileForIBGCinst || VM.CompileForCBGCinstrumentation || VM.CompileForDCacheSimulation) &&
		 safeToInlineForInstrumentation(field, ir) ) {
                // Sharad: load of a ref, instrument for both CBGC and cacheSimulator
		OPT_Instruction rb =
		    (opcode == GETFIELD_opcode) ?
		    Call.create2(CALL, null, null,
				 OPT_MethodOperand.STATIC(OPT_Entrypoints.resolvedGetfieldReadBarrierMethod),
				 GetField.getRef(inst).copy(),
				 new OPT_IntConstantOperand(field.getOffset())) :
		    Call.create2(CALL, null, null,
				 OPT_MethodOperand.STATIC(OPT_Entrypoints.unresolvedGetfieldReadBarrierMethod),
				 GetField.getRef(inst).copy(),
				 new OPT_IntConstantOperand(field.getDictionaryId()));
		rb.bcIndex = RUNTIME_SERVICES;
		rb.position = inst.position;
		inst.insertBefore(rb);
		// do inlining here and adjust next
		inline(rb, ir);
		next = inst.nextInstructionInCodeOrder();
	    }
	    //-#endif RVM_WITH_READ_BARRIER2
	  }
	}
	break;


        case PUTFIELD_opcode:
        case PUTFIELD_UNRESOLVED_opcode:
          {
	    OPT_LocationOperand loc = PutField.getClearLocation(inst);
	    VM_Field field = loc.field;
	    if (VM_Configuration.BuildWithLazyRedirect) {
		// This barrier leaves the PUTFIELD_? there for further translation so we don't have to handle UNRESOLVED
		OPT_Operand origObject = PutField.getClearRef(inst);
		OPT_RegisterOperand realObject = ir.gc.temps.makeTemp(origObject);
		OPT_Operand redirectOffset = new OPT_IntConstantOperand(VM_ObjectLayoutConstants.OBJECT_REDIRECT_OFFSET);
		OPT_Instruction redirectInst1 = GuardedUnary.create(GET_OBJ_RAW, realObject, origObject, 
								   GetField.getGuard(inst).copy());
		redirectInst1.bcIndex = inst.bcIndex;
		inst.insertBefore(redirectInst1);
		PutField.setRef(inst, realObject);
		if (!field.getType().isPrimitiveType()) {
		    OPT_Operand value = PutField.getClearValue(inst);
		    OPT_RegisterOperand temp = ir.gc.temps.makeTemp(value);
		    PutField.setValue(inst,temp);
		    OPT_BasicBlock beforeBB = inst.getBasicBlock();           
		    OPT_BasicBlock redirectBB = beforeBB.createSubBlock(inst.bcIndex, ir); 
		    OPT_BasicBlock afterBB = beforeBB.splitNodeAt(inst.getPrev(), ir);  // afterBB contains PutField
		    ir.cfg.linkInCodeOrder(beforeBB, redirectBB);
		    ir.cfg.linkInCodeOrder(redirectBB, afterBB);
		    beforeBB.insertOut(afterBB);   // unusual path
		    OPT_Instruction ifInst = IfCmp.create(INT_IFCMP, null, value,
							  new OPT_IntConstantOperand(0),  // NULL is 0
							  OPT_ConditionOperand.EQUAL(),
							  afterBB.makeJumpTarget(),
							  OPT_BranchProfileOperand.unlikely());
		    OPT_Instruction moveInst = Move.create(INT_MOVE, temp, value); // no REF_MOVE in lir
		    OPT_Instruction redirectInst = 
			GuardedUnary.create(GET_OBJ_RAW, temp, value, OPT_IRTools.TG());
		    ifInst.bcIndex = moveInst.bcIndex = redirectInst.bcIndex = inst.bcIndex;
		    beforeBB.appendInstruction(moveInst);
		    beforeBB.appendInstruction(ifInst);
		    redirectBB.appendInstruction(redirectInst);
		    next = inst.nextInstructionInCodeOrder();   // needed since we split blocks
		}
	    }
	    else if (VM_Configuration.BuildWithEagerRedirect) {
		// No modification needed
	    }
	    else if (!field.getType().isPrimitiveType()) {
		    //-#if RVM_WITH_CONCURRENT_GC
		    String className = 
			field.getDeclaringClass().getDescriptor().toString();
		    if (className.equals("LVM_BlockControl;")) {
			VM.sysWrite("Omitting barrier for method " + ir.method
				    + " field " + field + "\n");
		    } else {
			if (opcode == PUTFIELD_opcode)
			    Call.mutate3(inst, CALL, null, null, 
				     OPT_MethodOperand.STATIC(OPT_Entrypoints.RCGC_resolvedPutfieldMethod), 
				     PutField.getClearRef(inst), 
				     new OPT_IntConstantOperand(field.getOffset()), 
				     PutField.getClearValue(inst));
			else // opcode == PUTFIELD_UNRESOLVED_opcode
			    Call.mutate3(inst, CALL, null, null, 
					 OPT_MethodOperand.STATIC(OPT_Entrypoints.RCGC_unresolvedPutfieldMethod), 
					 PutField.getClearRef(inst), 
					 new OPT_IntConstantOperand(field.getDictionaryId()), 
					 PutField.getClearValue(inst));
			inline(inst, ir);
		    }
		    //-#else
		    if (VM_Collector.NEEDS_WRITE_BARRIER) {
			OPT_Instruction wb = 
			    (opcode == PUTFIELD_opcode) ? 
			    Call.create3(CALL, null, null, 
					 OPT_MethodOperand.STATIC(VM_Entrypoints.resolvedPutfieldWriteBarrierMethod), 
					 PutField.getRef(inst).copy(), 
					 new OPT_IntConstantOperand(field.getOffset()), 
					 PutField.getValue(inst).copy()) :
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
	    } // else if
	  }
	  break;

      case GETSTATIC_opcode: 
      case GETSTATIC_UNRESOLVED_opcode: 
	  if (VM_Configuration.BuildWithEagerRedirect) {
	      // Leave the opcode here for further translation so we can avoid dealing with UNRESOLVED
	      OPT_LocationOperand loc = GetStatic.getClearLocation(inst);
	      VM_Field field = loc.field;
	      if (!field.getType().isPrimitiveType()) {
		  OPT_RegisterOperand result = ALoad.getClearResult(inst);
		  OPT_RegisterOperand temp = ir.gc.temps.makeTemp(result);
		  GetStatic.setResult(inst,temp);
		  OPT_BasicBlock beforeBB = inst.getBasicBlock();           
		  OPT_BasicBlock redirectBB = beforeBB.createSubBlock(inst.bcIndex, ir); 
		  OPT_BasicBlock afterBB = beforeBB.splitNodeAt(inst, ir);  
		  ir.cfg.linkInCodeOrder(beforeBB, redirectBB);
		  ir.cfg.linkInCodeOrder(redirectBB, afterBB);
		  beforeBB.insertOut(afterBB);   // unusual path
		  OPT_Instruction ifInst = IfCmp.create(INT_IFCMP, null,temp,
							new OPT_IntConstantOperand(0),  // NULL is 0
							OPT_ConditionOperand.EQUAL(),
							afterBB.makeJumpTarget(),
							OPT_BranchProfileOperand.unlikely());
		  OPT_Instruction moveInst = Move.create(INT_MOVE, result, temp); // no REF_MOVE in lir
		  OPT_Instruction redirectInst = GuardedUnary.create(GET_OBJ_RAW, result, temp, OPT_IRTools.TG());
		  ifInst.bcIndex = moveInst.bcIndex = redirectInst.bcIndex = inst.bcIndex;
		  beforeBB.appendInstruction(moveInst);
		  beforeBB.appendInstruction(ifInst);
		  redirectBB.appendInstruction(redirectInst);
	      }
	  }
	  break;

      case PUTSTATIC_opcode:
      case PUTSTATIC_UNRESOLVED_opcode:
	{
	  //-#if RVM_WITH_CONCURRENT_GC
	  OPT_LocationOperand loc = PutStatic.getLocation(inst);
	  VM_Field field = loc.field;
	  if (!field.getType().isPrimitiveType()) {
	      if (opcode == PUTSTATIC_opcode)
		  Call.mutate2(inst, CALL, null, null, 
			       OPT_MethodOperand.STATIC(OPT_Entrypoints.RCGC_resolvedPutstaticMethod), 
			       new OPT_IntConstantOperand(field.getOffset()), 
			       PutStatic.getClearValue(inst));
	      else
		  Call.mutate2(inst, CALL, null, null, 
			       OPT_MethodOperand.STATIC(OPT_Entrypoints.RCGC_unresolvedPutstaticMethod), 
			       new OPT_IntConstantOperand(field.getDictionaryId()), 
			       PutStatic.getClearValue(inst));
	      inline(inst, ir);
	  }
	  //-#endif
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
    try {
      OPT_InlineDecision inlDec = 
	OPT_InlineDecision.YES(Call.getMethod(inst).method, 
			       "Expansion of runtime service");
      OPT_Inliner.execute(inlDec, ir, inst);
    } finally {
      ir.options.INLINE = savedInliningOption;
      ir.options.NO_CALLEE_EXCEPTIONS = savedExceptionOption;
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
