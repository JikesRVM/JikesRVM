/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt.ir;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.opt.*;
import java.util.Enumeration;

//-#if RVM_WITH_ADAPTIVE_SYSTEM
import com.ibm.JikesRVM.adaptive.VM_Controller;
import com.ibm.JikesRVM.adaptive.VM_AOSDatabase;
//-#endif

/**
 * This class contains the high level logic for executing an inlining decision.
 *
 * @author Steve Fink
 * @author Dave Grove
 *
 * @see OPT_InlineDecision
 * @see OPT_GenerationContext
 */
public class OPT_Inliner implements OPT_Operators, 
                                    com.ibm.JikesRVM.opt.OPT_Constants {

  // The following flag requires an adaptive boot image and flag
  // "INSERT_DEBUGGING_COUNTERS" to be true.  See instrumentation
  // section of the user guide for more information.
  private static final boolean COUNT_FAILED_GUARDS = false;

  /**
   * Execute an inlining decision inlDec for the CALL instruction
   * callSite that is contained in ir.
   *
   * @param inlDec the inlining decision to execute
   * @param ir the governing IR
   * @param callSite the call site to inline
   */
  public static void execute (OPT_InlineDecision inlDec, OPT_IR ir, 
                              OPT_Instruction callSite) {
    // Find out where the call site is and isolate it in its own basic block.
    OPT_BasicBlock bb = 
      callSite.getBasicBlock().segregateInstruction(callSite, ir);
    OPT_BasicBlock in = bb.prevBasicBlockInCodeOrder();
    OPT_BasicBlock out = bb.nextBasicBlockInCodeOrder();
    // Clear the sratch object of any register operands being 
    // passed as parameters.
    // BC2IR uses this field for its own purposes, and will be confused 
    // if the scratch object has been used by someone else and not cleared.
    for (int i = 0; i < Call.getNumberOfParams(callSite); i++) {
      OPT_Operand arg = Call.getParam(callSite, i);
      if (arg instanceof OPT_RegisterOperand) {
        ((OPT_RegisterOperand)arg).scratchObject = null;
      }
    }
    // We need to ensure that inlining the CALL instruction does not
    // insert any new exceptional edges into the CFG that were not
    // present before the inlining.  Note that inlining the CALL may
    // introduce new CALLS, for which we don't know the exception
    // behavior.  However, we know that any new PEIs introduced in the
    // inlined code had better not add exceptional edges to the
    // original CFG.  So, create a new ExceptionHandlerBasicBlockBag
    // which will enforce this behavior.
    OPT_ExceptionHandlerBasicBlock[] catchBlocks = new OPT_ExceptionHandlerBasicBlock[bb.getNumberOfExceptionalOut()];
    Enumeration e = bb.getExceptionalOut();
    for (int i=0; i<catchBlocks.length; i++) {
       catchBlocks[i] = (OPT_ExceptionHandlerBasicBlock)e.nextElement();
    }
    OPT_ExceptionHandlerBasicBlockBag bag = new OPT_ExceptionHandlerBasicBlockBag(catchBlocks,null);    

    // Execute the inlining decision, updating ir.gc's state.
    OPT_GenerationContext childgc = 
      execute(inlDec, ir.gc, bag, callSite);
    // Splice the callee into the caller's code order
    ir.cfg.removeFromCFGAndCodeOrder(bb);
    ir.cfg.breakCodeOrder(in, out);
    ir.cfg.linkInCodeOrder(in, childgc.cfg.firstInCodeOrder());
    ir.cfg.linkInCodeOrder(childgc.cfg.lastInCodeOrder(), out);
    // Splice the callee into the caller's CFG
    in.insertOut(childgc.prologue);
    if (childgc.epilogue != null) {
      childgc.epilogue.insertOut(out);
    }
  }


  /**
   * Return a generation context that represents the 
   * execution of inlDec in the context <parent,ebag> for
   * the call instruction callSite.
   * <p> PRECONDITION: inlDec.isYes()
   * <p> POSTCONDITIONS: 
   * Let gc be the returned generation context.
   * <ul>
   *  <li> gc.cfg.firstInCodeOrder is the entry to the inlined context
   *  <li>gc.cfg.lastInCodeOrder is the exit from the inlined context
   *  <li> OPT_GenerationContext.transferState(parent, child) has been called.
   * </ul>
   *
   * @param inlDec the inlining decision to execute
   * @param parent the caller generation context
   * @param ebag exception handler scope for the caller
   * @param callSite the callsite to execute
   * @return a generation context that represents the execution of the
   *         inline decision in the given context
   */
  public static OPT_GenerationContext execute(OPT_InlineDecision inlDec, 
                                              OPT_GenerationContext parent, 
                                              OPT_ExceptionHandlerBasicBlockBag ebag, 
                                              OPT_Instruction callSite) {
    if (inlDec.needsGuard()) {
      //Step 1: create the synthetic generation context we'll 
      // return to our caller.
      OPT_GenerationContext container = 
        OPT_GenerationContext.createSynthetic(parent, ebag);
      container.cfg.breakCodeOrder(container.prologue, container.epilogue);
      // Step 2: (a) Print a message (optional)
      //         (b) Generate the child GC for each target
      VM_Method[] targets = inlDec.getTargets();
      byte[] guards = inlDec.getGuards();
      OPT_GenerationContext[] children = 
        new OPT_GenerationContext[targets.length];
      for (int i = 0; i < targets.length; i++) {
        VM_NormalMethod callee = (VM_NormalMethod)targets[i];
        // (a)
        if (parent.options.PRINT_INLINE_REPORT) {
          String guard = guards[i] == 
            OPT_Options.IG_CLASS_TEST ? " (class test) " : " (method test) ";
          VM.sysWrite("\tGuarded inline" + guard + " " + callee 
                      + " into " + callSite.position.getMethod() 
                      + " at bytecode " + callSite.bcIndex + "\n");
        }
        // (b) 
        children[i] = 
          OPT_GenerationContext.createChildContext(parent, ebag, 
                                                   callee, callSite);
        OPT_BC2IR.generateHIR(children[i]);
        OPT_GenerationContext.transferState(parent, children[i]);
      }
      // Step 3: Merge together result from children into container.
      if (Call.hasResult(callSite)) {
        OPT_Register reg = Call.getResult(callSite).register;
        container.result = children[0].result;
        for (int i = 1; i < targets.length; i++) {
          container.result = 
            OPT_Operand.meet(container.result, children[i].result, reg);
        }
        // Account for the non-predicted case as well...
        // Most likely this means that we shouldn't even bother 
        // with the above meet operations
        // and simply pick up Call.getResult(callsite) directly.
        // SJF: However, it's good to keep this around; maybe
        //      IPA will give us more information about the result.
        container.result = 
          OPT_Operand.meet(container.result, Call.getResult(callSite), reg);
      }
      // Step 4: Create a block to contain a copy of the original call 
      // in case all predictions fail. It falls through to container.epilogue
      OPT_BasicBlock testFailed = 
        new OPT_BasicBlock(callSite.bcIndex, callSite.position, parent.cfg);
      testFailed.exceptionHandlers = ebag;
      OPT_Instruction call = callSite.copyWithoutLinks();
      Call.getMethod(call).setIsGuardedInlineOffBranch(true);
      call.bcIndex = callSite.bcIndex;
      call.position = callSite.position;

      //-#if RVM_WITH_ADAPTIVE_SYSTEM
      if (COUNT_FAILED_GUARDS && 
          VM_Controller.options.INSERT_DEBUGGING_COUNTERS) {
        // Get a dynamic count of how many times guards fail at runtime.
        // Need a name for the event to count.  In this example, a
        // separate counter for each method by using the method name
        // as the event name.  You could also have used just the string 
        // "Guarded inline failed" to keep only one counter.
        String eventName = 
          "Guarded inline failed: " + callSite.position.getMethod().toString();
        // Create instruction that will increment the counter
        // corresponding to the named event.
        OPT_Instruction counterInst = 
          VM_AOSDatabase.debuggingCounterData.getCounterInstructionForEvent(eventName);
        testFailed.appendInstruction(counterInst);
      }
      //-#endif
        
      //-#if RVM_WITH_OSR
      if (inlDec.OSRTestFailed()) {
        // note where we're storing the osr barrier instruction
        OPT_Instruction lastOsrBarrier = (OPT_Instruction)callSite.scratchObject;
        OPT_Instruction s = OPT_BC2IR._osrHelper(lastOsrBarrier);
        s.position = callSite.position;
        s.bcIndex = callSite.bcIndex;
        testFailed.appendInstruction(s);
//      testFailed.appendInstruction(call);
      } else {
        testFailed.appendInstruction(call);
      }
      //-#else
      testFailed.appendInstruction(call);
      //-#endif
      testFailed.insertOut(container.epilogue);
      container.cfg.linkInCodeOrder(testFailed, container.epilogue);
      // This is ugly....since we didn't call BC2IR to generate the 
      // block with callSite we have to initialize the block's exception 
      // behavior manually.
      // We can't call createSubBlock to do it because callSite may not
      // be in a basic block yet (when execute is invoked from 
      // BC2IR.maybeInlineMethod).
      if (ebag != null) {
        for (OPT_BasicBlockEnumeration e = ebag.enumerator(); 
             e.hasMoreElements();) {
          OPT_BasicBlock handler = e.next();
          testFailed.insertOut(handler);
        }
      }
      testFailed.setCanThrowExceptions();
      testFailed.setMayThrowUncaughtException();
      testFailed.setInfrequent();
      // Step 5: Patch together all the callees by generating guard blocks
      OPT_BasicBlock firstIfBlock = testFailed;
      // Note: We know that receiver must be a register 
      // operand (and not a string constant) because we are doing a 
      // guarded inlining....if it was a string constant we'd have
      // been able to inline without a guard.
      OPT_RegisterOperand receiver = Call.getParam(callSite, 0).asRegister();
      OPT_MethodOperand mo = Call.getMethod(callSite);
      boolean isInterface = mo.isInterface();
      if (isInterface) {
        if (VM.BuildForIMTInterfaceInvocation ||
            (VM.BuildForITableInterfaceInvocation && VM.DirectlyIndexedITables)) {
          VM_Type interfaceType = mo.getTarget().getDeclaringClass();
          VM_TypeReference recTypeRef = receiver.type;
          VM_Class recType = (VM_Class)recTypeRef.peekResolvedType();
          // Attempt to avoid inserting the check by seeing if the 
          // known static type of the receiver implements the interface.
          boolean requiresImplementsTest = true;
          if (recType != null && recType.isResolved() && !recType.isInterface()) {
            byte doesImplement = 
              OPT_ClassLoaderProxy.includesType(interfaceType.getTypeRef(), recTypeRef);
            requiresImplementsTest = doesImplement != OPT_Constants.YES;
          }
          if (requiresImplementsTest) {
            OPT_Instruction dtc = 
              TypeCheck.create(MUST_IMPLEMENT_INTERFACE,
                               receiver.copyU2U(),
                               new OPT_TypeOperand(interfaceType),
                               Call.getGuard(callSite).copy());
            dtc.copyPosition(callSite);
            testFailed.prependInstruction(dtc);
          }
        }
      }
      // Basic idea of loop: Path together an if...else if.... else...
      // chain from the bottom (testFailed). Some excessive cuteness
      // to allow us to have multiple if blocks for a single
      // "logical" test and to share test insertion for interfaces/virtuals.
      for (int i = children.length - 1; 
           i >= 0; 
           i--, testFailed = firstIfBlock) {
        firstIfBlock = 
          new OPT_BasicBlock(callSite.bcIndex, callSite.position, 
                             parent.cfg);
        firstIfBlock.exceptionHandlers = ebag;
        OPT_BasicBlock lastIfBlock = firstIfBlock;
        VM_Method target = children[i].method;
        OPT_Instruction tmp;

        if (isInterface) {
          VM_Class callDeclClass = mo.getTarget().getDeclaringClass();
          if (!callDeclClass.isInterface()) {
            // Part of ensuring that we catch IncompatibleClassChangeErrors
            // is making sure that we know that callDeclClass is an
            // interface before we attempt to side-step the usual invoke
            // interface sequence.
            // If we don't know at least this much, we can't do the inlining.
            // We used online profile data to tell us that the target was a 
            // frequently called method from this (interface invoke) 
            // callSite, so it would be truly bizarre for us to not be able
            // to establish that callDeclClass is an interface now.
            // If we were using static heuristics to do guarded inlining
            // of interface calls, then we'd probably need to do the 
            // "right" thing and detect this situation
            // before we generated all of the childCFG's and got them
            // entangled into the parent (due to exceptional control flow).
            // This potential entanglement is what forces us to bail on 
            // the entire compilation.
            throw new OPT_OptimizingCompilerException("Attempted guarded inline of invoke interface when decl class of target method may not be an interface");
          }
            
          // We potentially have to generate IR to perform two tests here:
          // (1) Does the receiver object implement callDeclClass?
          // (2) Given that it does, is target the method that would 
          //     be invoked for this receiver?
          // It is quite common to be able to answer (1) "YES" at compile
          // time, in which case we only have to generate IR to establish 
          // (2) at runtime.
          byte doesImplement = OPT_ClassLoaderProxy.
            includesType(callDeclClass.getTypeRef(), target.getDeclaringClass().getTypeRef());
          if (doesImplement != OPT_Constants.YES) {
            // We can't be sure at compile time that the receiver implements
            // the interface. So, inject a test to make sure that it does.
            // Unlike the above case, this can actually happen (when 
            // the method is inherited but only the child actually 
            // implements the interface).
            if (parent.options.PRINT_INLINE_REPORT) {
              VM.sysWrite("\t\tRequired additional instanceof "
                          +callDeclClass+" test\n");
            }
            firstIfBlock = 
              new OPT_BasicBlock(callSite.bcIndex, callSite.position, 
                                 parent.cfg);
            firstIfBlock.exceptionHandlers = ebag;

            OPT_RegisterOperand instanceOfResult = 
              parent.temps.makeTempInt();
            tmp = InstanceOf.create(INSTANCEOF_NOTNULL, 
                                    instanceOfResult,
                                    new OPT_TypeOperand(callDeclClass),
                                    receiver.copy(),
                                    Call.getGuard(callSite));
            tmp.copyPosition(callSite);
            firstIfBlock.appendInstruction(tmp);

            tmp = IfCmp.create(INT_IFCMP,
                               parent.temps.makeTempValidation(),
                               instanceOfResult.copyD2U(),
                               new OPT_IntConstantOperand(0),
                               OPT_ConditionOperand.EQUAL(),
                               testFailed.makeJumpTarget(),
                               OPT_BranchProfileOperand.unlikely());
            tmp.copyPosition(callSite);
            firstIfBlock.appendInstruction(tmp);

            firstIfBlock.insertOut(testFailed);
            firstIfBlock.insertOut(lastIfBlock);
            container.cfg.linkInCodeOrder(firstIfBlock, lastIfBlock);
          }
        }

        if (guards[i] == OPT_Options.IG_CLASS_TEST) {
          tmp = InlineGuard.create(IG_CLASS_TEST, receiver.copyU2U(), 
                                   Call.getGuard(callSite).copy(), 
                                   new OPT_TypeOperand(target.getDeclaringClass()), 
                                   testFailed.makeJumpTarget(),
                                   OPT_BranchProfileOperand.unlikely());
        } else if (guards[i] == OPT_Options.IG_METHOD_TEST) {
          tmp = InlineGuard.create(IG_METHOD_TEST, receiver.copyU2U(), 
                                   Call.getGuard(callSite).copy(), 
                                   OPT_MethodOperand.VIRTUAL(target.getMemberRef().asMethodReference(), target), 
                                   testFailed.makeJumpTarget(),
                                   OPT_BranchProfileOperand.unlikely());
        } else {
          tmp = InlineGuard.create(IG_PATCH_POINT, receiver.copyU2U(), 
                                   Call.getGuard(callSite).copy(), 
                                   OPT_MethodOperand.VIRTUAL(target.getMemberRef().asMethodReference(), target), 
                                   testFailed.makeJumpTarget(),
                                   OPT_BranchProfileOperand.unlikely());
        }
        tmp.copyPosition(callSite);
        lastIfBlock.appendInstruction(tmp);

        lastIfBlock.insertOut(testFailed);
        lastIfBlock.insertOut(children[i].prologue);
        container.cfg.linkInCodeOrder(lastIfBlock, 
                                      children[i].cfg.firstInCodeOrder());
        if (children[i].epilogue != null) {
          children[i].epilogue.appendInstruction(container.epilogue.makeGOTO());
          children[i].epilogue.insertOut(container.epilogue);
        }
        container.cfg.linkInCodeOrder(children[i].cfg.lastInCodeOrder(), 
                                      testFailed);
      }
      //Step 6: finsh by linking container.prologue & testFailed
      container.prologue.insertOut(testFailed);
      container.cfg.linkInCodeOrder(container.prologue, testFailed);
      return  container;
    } else {
      if (VM.VerifyAssertions) VM._assert(inlDec.getNumberOfTargets() == 1);
      VM_NormalMethod callee = (VM_NormalMethod)inlDec.getTargets()[0];
      if (parent.options.PRINT_INLINE_REPORT) {
        VM.sysWrite("\tInline " + callee 
                    + " into " + callSite.position.getMethod()
                    + " at bytecode " + callSite.bcIndex + "\n");
      }
      OPT_GenerationContext child = OPT_GenerationContext.
        createChildContext(parent, ebag, callee, callSite);
      OPT_BC2IR.generateHIR(child);
      OPT_GenerationContext.transferState(parent, child);
      return  child;
    }
  }
}
