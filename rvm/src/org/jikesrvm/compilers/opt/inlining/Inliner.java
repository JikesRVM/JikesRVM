/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.compilers.opt.inlining;

import static org.jikesrvm.compilers.opt.ir.Operators.IG_CLASS_TEST;
import static org.jikesrvm.compilers.opt.ir.Operators.IG_METHOD_TEST;
import static org.jikesrvm.compilers.opt.ir.Operators.IG_PATCH_POINT;
import static org.jikesrvm.compilers.opt.ir.Operators.INSTANCEOF_NOTNULL;
import static org.jikesrvm.compilers.opt.ir.Operators.INT_IFCMP;
import static org.jikesrvm.compilers.opt.ir.Operators.MUST_IMPLEMENT_INTERFACE;

import java.util.Enumeration;

import org.jikesrvm.VM;
import org.jikesrvm.adaptive.controller.Controller;
import org.jikesrvm.adaptive.database.AOSDatabase;
import org.jikesrvm.classloader.RVMClass;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.classloader.RVMType;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.opt.ClassLoaderProxy;
import org.jikesrvm.compilers.opt.OptOptions;
import org.jikesrvm.compilers.opt.OptimizingCompilerException;
import org.jikesrvm.compilers.opt.bc2ir.BC2IR;
import org.jikesrvm.compilers.opt.bc2ir.GenerationContext;
import org.jikesrvm.compilers.opt.driver.OptConstants;
import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.compilers.opt.ir.Call;
import org.jikesrvm.compilers.opt.ir.ExceptionHandlerBasicBlock;
import org.jikesrvm.compilers.opt.ir.ExceptionHandlerBasicBlockBag;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.IfCmp;
import org.jikesrvm.compilers.opt.ir.InlineGuard;
import org.jikesrvm.compilers.opt.ir.InstanceOf;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.Register;
import org.jikesrvm.compilers.opt.ir.TypeCheck;
import org.jikesrvm.compilers.opt.ir.operand.BranchProfileOperand;
import org.jikesrvm.compilers.opt.ir.operand.ConditionOperand;
import org.jikesrvm.compilers.opt.ir.operand.IntConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.MethodOperand;
import org.jikesrvm.compilers.opt.ir.operand.Operand;
import org.jikesrvm.compilers.opt.ir.operand.RegisterOperand;
import org.jikesrvm.compilers.opt.ir.operand.TypeOperand;

/**
 * This class contains the high level logic for executing an inlining decision.
 *
 * @see InlineDecision
 * @see GenerationContext
 */
public class Inliner {

  /**
   * The following flag enables debug counters and requires an adaptive boot
   * image and flag "INSERT_DEBUGGING_COUNTERS" to be true. See instrumentation
   * section of the user guide for more information.
   */
  private static final boolean COUNT_FAILED_GUARDS = false;

  /**
   * Execute an inlining decision inlDec for the CALL instruction
   * callSite that is contained in ir.
   *
   * @param inlDec the inlining decision to execute
   * @param ir the governing IR
   * @param callSite the call site to inline
   */
  public static void execute(InlineDecision inlDec, IR ir, Instruction callSite) {
    // Find out where the call site is and isolate it in its own basic block.
    BasicBlock bb = callSite.getBasicBlock().segregateInstruction(callSite, ir);
    BasicBlock in = bb.prevBasicBlockInCodeOrder();
    BasicBlock out = bb.nextBasicBlockInCodeOrder();
    // Clear the sratch object of any register operands being
    // passed as parameters.
    // BC2IR uses this field for its own purposes, and will be confused
    // if the scratch object has been used by someone else and not cleared.
    for (int i = 0; i < Call.getNumberOfParams(callSite); i++) {
      Operand arg = Call.getParam(callSite, i);
      if (arg instanceof RegisterOperand) {
        ((RegisterOperand) arg).scratchObject = null;
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
    ExceptionHandlerBasicBlock[] catchBlocks = new ExceptionHandlerBasicBlock[bb.getNumberOfExceptionalOut()];
    Enumeration<BasicBlock> e = bb.getExceptionalOut();
    for (int i = 0; i < catchBlocks.length; i++) {
      catchBlocks[i] = (ExceptionHandlerBasicBlock) e.nextElement();
    }
    ExceptionHandlerBasicBlockBag bag = new ExceptionHandlerBasicBlockBag(catchBlocks, null);

    // Execute the inlining decision, updating ir.gc's state.
    GenerationContext childgc = execute(inlDec, ir.gc, bag, callSite);
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
   * execution of inlDec in the context <code>&lt;parent,ebag&gt;</code> for
   * the call instruction callSite.
   * <p> PRECONDITION: inlDec.isYes()
   * <p> POSTCONDITIONS:
   * Let gc be the returned generation context.
   * <ul>
   *  <li> gc.cfg.firstInCodeOrder is the entry to the inlined context
   *  <li>gc.cfg.lastInCodeOrder is the exit from the inlined context
   *  <li> GenerationContext.transferState(parent, child) has been called.
   * </ul>
   *
   * @param inlDec the inlining decision to execute
   * @param parent the caller generation context
   * @param ebag exception handler scope for the caller
   * @param callSite the callsite to execute
   * @return a generation context that represents the execution of the
   *         inline decision in the given context
   */
  public static GenerationContext execute(InlineDecision inlDec, GenerationContext parent,
                                          ExceptionHandlerBasicBlockBag ebag, Instruction callSite) {
    if (inlDec.needsGuard()) {
      //Step 1: create the synthetic generation context we'll
      // return to our caller.
      GenerationContext container = GenerationContext.createSynthetic(parent, ebag);
      container.cfg.breakCodeOrder(container.prologue, container.epilogue);
      // Step 2: (a) Print a message (optional)
      //         (b) Generate the child GC for each target
      RVMMethod[] targets = inlDec.getTargets();
      byte[] guards = inlDec.getGuards();
      GenerationContext[] children = new GenerationContext[targets.length];
      for (int i = 0; i < targets.length; i++) {
        NormalMethod callee = (NormalMethod) targets[i];
        // (a)
        if (parent.options.PRINT_INLINE_REPORT) {
          String guard = guards[i] == OptOptions.INLINE_GUARD_CLASS_TEST ? " (class test) " : " (method test) ";
          VM.sysWrite("\tGuarded inline" + guard + " " + callee +
                      " into " + callSite.position.getMethod() +
                      " at bytecode " + callSite.bcIndex + "\n");
        }
        // (b)
        children[i] = GenerationContext.createChildContext(parent, ebag, callee, callSite);
        BC2IR.generateHIR(children[i]);
        GenerationContext.transferState(parent, children[i]);
      }
      // Step 3: Merge together result from children into container.
      //         Note: if the child ended with only exception control flow, then
      //         child.result will be null, which we want to interpret as top.
      //         Operand.meet interprets null as bottom, so we have to do some
      //         special purpose coding wrapping the calls to Operand.meet.
      if (Call.hasResult(callSite)) {
        Register reg = Call.getResult(callSite).getRegister();
        container.result = children[0].result;
        for (int i = 1; i < targets.length; i++) {
          if (children[i].result != null) {
            container.result = (container.result == null) ? children[i].result : Operand.meet(container.result, children[i].result, reg);
          }
        }


        if (!inlDec.OSRTestFailed()) {
          // Account for the non-predicted case as well...
          RegisterOperand failureCaseResult = Call.getResult(callSite).copyRO();
          container.result = (container.result == null) ?  failureCaseResult : Operand.meet(container.result, failureCaseResult, reg);
        }
      }

      // Step 4: Create a block to contain a copy of the original call or an OSR_Yieldpoint
      //         to cover the case that all predictions fail.
      BasicBlock testFailed = new BasicBlock(callSite.bcIndex, callSite.position, parent.cfg);
      testFailed.exceptionHandlers = ebag;

      if (COUNT_FAILED_GUARDS && Controller.options.INSERT_DEBUGGING_COUNTERS) {
        // Get a dynamic count of how many times guards fail at runtime.
        // Need a name for the event to count.  In this example, a
        // separate counter for each method by using the method name
        // as the event name.  You could also have used just the string
        // "Guarded inline failed" to keep only one counter.
        String eventName = "Guarded inline failed: " + callSite.position.getMethod().toString();
        // Create instruction that will increment the counter
        // corresponding to the named event.
        Instruction counterInst = AOSDatabase.debuggingCounterData.getCounterInstructionForEvent(eventName);
        testFailed.appendInstruction(counterInst);
      }

      if (inlDec.OSRTestFailed()) {
        // note where we're storing the osr barrier instruction
        Instruction lastOsrBarrier = (Instruction)callSite.scratchObject;
        Instruction s = BC2IR._osrHelper(lastOsrBarrier);
        s.position = callSite.position;
        s.bcIndex = callSite.bcIndex;
        testFailed.appendInstruction(s);
        testFailed.insertOut(parent.exit);
      } else {
        Instruction call = callSite.copyWithoutLinks();
        Call.getMethod(call).setIsGuardedInlineOffBranch(true);
        call.bcIndex = callSite.bcIndex;
        call.position = callSite.position;
        testFailed.appendInstruction(call);
        testFailed.insertOut(container.epilogue);
        // This is ugly....since we didn't call BC2IR to generate the
        // block with callSite we have to initialize the block's exception
        // behavior manually.
        // We can't call createSubBlock to do it because callSite may not
        // be in a basic block yet (when execute is invoked from
        // BC2IR.maybeInlineMethod).
        if (ebag != null) {
          for (Enumeration<BasicBlock> e = ebag.enumerator(); e.hasMoreElements();) {
            BasicBlock handler = e.nextElement();
            testFailed.insertOut(handler);
          }
        }
        testFailed.setCanThrowExceptions();
        testFailed.setMayThrowUncaughtException();
      }
      container.cfg.linkInCodeOrder(testFailed, container.epilogue);
      testFailed.setInfrequent();

      // Step 5: Patch together all the callees by generating guard blocks
      BasicBlock firstIfBlock = testFailed;
      // Note: We know that receiver must be a register
      // operand (and not a string constant) because we are doing a
      // guarded inlining....if it was a string constant we'd have
      // been able to inline without a guard.
      Operand receiver = Call.getParam(callSite, 0);
      MethodOperand mo = Call.getMethod(callSite);
      boolean isInterface = mo.isInterface();
      if (isInterface) {
        if (VM.BuildForIMTInterfaceInvocation) {
          RVMType interfaceType = mo.getTarget().getDeclaringClass();
          TypeReference recTypeRef = receiver.getType();
          RVMClass recType = (RVMClass) recTypeRef.peekType();
          // Attempt to avoid inserting the check by seeing if the
          // known static type of the receiver implements the interface.
          boolean requiresImplementsTest = true;
          if (recType != null && recType.isResolved() && !recType.isInterface()) {
            byte doesImplement = ClassLoaderProxy.includesType(interfaceType.getTypeRef(), recTypeRef);
            requiresImplementsTest = doesImplement != OptConstants.YES;
          }
          if (requiresImplementsTest) {
            RegisterOperand checkedReceiver = parent.temps.makeTemp(receiver);
            Instruction dtc =
                TypeCheck.create(MUST_IMPLEMENT_INTERFACE,
                    checkedReceiver,
                    receiver.copy(),
                    new TypeOperand(interfaceType),
                    Call.getGuard(callSite).copy());
            dtc.copyPosition(callSite);
            checkedReceiver.refine(interfaceType.getTypeRef());
            Call.setParam(callSite, 0, checkedReceiver.copyRO());
            testFailed.prependInstruction(dtc);
          }
        }
      }
      // Basic idea of loop: Path together an if...else if.... else...
      // chain from the bottom (testFailed). Some excessive cuteness
      // to allow us to have multiple if blocks for a single
      // "logical" test and to share test insertion for interfaces/virtuals.
      for (int i = children.length - 1; i >= 0; i--, testFailed = firstIfBlock) {
        firstIfBlock = new BasicBlock(callSite.bcIndex, callSite.position, parent.cfg);
        firstIfBlock.exceptionHandlers = ebag;
        BasicBlock lastIfBlock = firstIfBlock;
        RVMMethod target = children[i].method;
        Instruction tmp;

        if (isInterface) {
          RVMClass callDeclClass = mo.getTarget().getDeclaringClass();
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
            throw new OptimizingCompilerException(
                "Attempted guarded inline of invoke interface when decl class of target method may not be an interface");
          }

          // We potentially have to generate IR to perform two tests here:
          // (1) Does the receiver object implement callDeclClass?
          // (2) Given that it does, is target the method that would
          //     be invoked for this receiver?
          // It is quite common to be able to answer (1) "YES" at compile
          // time, in which case we only have to generate IR to establish
          // (2) at runtime.
          byte doesImplement = ClassLoaderProxy.
              includesType(callDeclClass.getTypeRef(), target.getDeclaringClass().getTypeRef());
          if (doesImplement != OptConstants.YES) {
            // We can't be sure at compile time that the receiver implements
            // the interface. So, inject a test to make sure that it does.
            // Unlike the above case, this can actually happen (when
            // the method is inherited but only the child actually
            // implements the interface).
            if (parent.options.PRINT_INLINE_REPORT) {
              VM.sysWrite("\t\tRequired additional instanceof " + callDeclClass + " test\n");
            }
            firstIfBlock = new BasicBlock(callSite.bcIndex, callSite.position, parent.cfg);
            firstIfBlock.exceptionHandlers = ebag;

            RegisterOperand instanceOfResult = parent.temps.makeTempInt();
            tmp =
                InstanceOf.create(INSTANCEOF_NOTNULL,
                                  instanceOfResult,
                                  new TypeOperand(callDeclClass),
                                  receiver.copy(),
                                  Call.getGuard(callSite));
            tmp.copyPosition(callSite);
            firstIfBlock.appendInstruction(tmp);

            tmp =
                IfCmp.create(INT_IFCMP,
                             parent.temps.makeTempValidation(),
                             instanceOfResult.copyD2U(),
                             new IntConstantOperand(0),
                             ConditionOperand.EQUAL(),
                             testFailed.makeJumpTarget(),
                             BranchProfileOperand.unlikely());
            tmp.copyPosition(callSite);
            firstIfBlock.appendInstruction(tmp);

            firstIfBlock.insertOut(testFailed);
            firstIfBlock.insertOut(lastIfBlock);
            container.cfg.linkInCodeOrder(firstIfBlock, lastIfBlock);
          }
        }

        if (guards[i] == OptOptions.INLINE_GUARD_CLASS_TEST) {
          tmp =
              InlineGuard.create(IG_CLASS_TEST,
                                 receiver.copy(),
                                 Call.getGuard(callSite).copy(),
                                 new TypeOperand(target.getDeclaringClass()),
                                 testFailed.makeJumpTarget(),
                                 BranchProfileOperand.unlikely());
        } else if (guards[i] == OptOptions.INLINE_GUARD_METHOD_TEST) {
          // method test for interface requires additional check if
          // the reciever's class is a subclass of inlined method's
          // declaring class.
          if (isInterface) {
            RegisterOperand t = parent.temps.makeTempInt();
            Instruction test =
                InstanceOf.create(INSTANCEOF_NOTNULL,
                                  t,
                                  new TypeOperand(target.getDeclaringClass().getTypeRef()),
                                  receiver.copy());
            test.copyPosition(callSite);
            lastIfBlock.appendInstruction(test);

            Instruction cmp =
                IfCmp.create(INT_IFCMP,
                             parent.temps.makeTempValidation(),
                             t.copyD2U(),
                             new IntConstantOperand(0),
                             ConditionOperand.EQUAL(),
                             testFailed.makeJumpTarget(),
                             BranchProfileOperand.unlikely());
            cmp.copyPosition(callSite);
            lastIfBlock.appendInstruction(cmp);

            BasicBlock subclassTest = new BasicBlock(callSite.bcIndex, callSite.position, parent.cfg);

            lastIfBlock.insertOut(testFailed);
            lastIfBlock.insertOut(subclassTest);

            container.cfg.linkInCodeOrder(lastIfBlock, subclassTest);

            lastIfBlock = subclassTest;
          }

          tmp =
              InlineGuard.create(IG_METHOD_TEST,
                                 receiver.copy(),
                                 Call.getGuard(callSite).copy(),
                                 MethodOperand.VIRTUAL(target.getMemberRef().asMethodReference(), target),
                                 testFailed.makeJumpTarget(),
                                 BranchProfileOperand.unlikely());
        } else {
          tmp =
              InlineGuard.create(IG_PATCH_POINT,
                                 receiver.copy(),
                                 Call.getGuard(callSite).copy(),
                                 MethodOperand.VIRTUAL(target.getMemberRef().asMethodReference(), target),
                                 testFailed.makeJumpTarget(),
                                 inlDec.OSRTestFailed() ? BranchProfileOperand.never() : BranchProfileOperand.unlikely());
        }
        tmp.copyPosition(callSite);
        lastIfBlock.appendInstruction(tmp);

        lastIfBlock.insertOut(testFailed);
        lastIfBlock.insertOut(children[i].prologue);
        container.cfg.linkInCodeOrder(lastIfBlock, children[i].cfg.firstInCodeOrder());
        if (children[i].epilogue != null) {
          children[i].epilogue.appendInstruction(container.epilogue.makeGOTO());
          children[i].epilogue.insertOut(container.epilogue);
        }
        container.cfg.linkInCodeOrder(children[i].cfg.lastInCodeOrder(), testFailed);
      }
      //Step 6: finish by linking container.prologue & testFailed
      container.prologue.insertOut(testFailed);
      container.cfg.linkInCodeOrder(container.prologue, testFailed);
      return container;
    } else {
      if (VM.VerifyAssertions) VM._assert(inlDec.getNumberOfTargets() == 1);
      NormalMethod callee = (NormalMethod) inlDec.getTargets()[0];
      if (parent.options.PRINT_INLINE_REPORT) {
        VM.sysWrite("\tInline " + callee +
                    " into " + callSite.position.getMethod() +
                    " at bytecode " + callSite.bcIndex + "\n");
      }
      GenerationContext child = GenerationContext.
          createChildContext(parent, ebag, callee, callSite);
      BC2IR.generateHIR(child);
      GenerationContext.transferState(parent, child);
      return child;
    }
  }
}
