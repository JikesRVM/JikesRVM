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
package org.mmtk.harness.lang.runtime;

import org.mmtk.harness.lang.Env;
import org.mmtk.harness.lang.Trace;
import org.mmtk.harness.lang.Trace.Item;
import org.mmtk.harness.lang.compiler.CompiledMethod;
import org.mmtk.harness.lang.pcode.CallNormalOp;
import org.mmtk.harness.lang.pcode.PseudoOp;
import org.mmtk.harness.lang.pcode.ReturnOp;
import org.vmmagic.unboxed.harness.Clock;

/**
 * Interprets the p-code that is the compilation target of the
 * MMTk Harness scripting language.  Each instance of this class executes
 * one thread to completion.
 * <p>
 * p-ops are interpreted by calling the operator's exec method.
 * The interpreter then performs control-flow adjustments.
 */
public final class PcodeInterpreter {

  /** The environment (stack and associated structures) for this thread. */
  private final Env env;

  /** The code for the current method */
  private PseudoOp[] code;

  /** Program counter (index into the <code>code</code> array) */
  private int pc = 0;

  /** nesting level for method calls.  Return from level 0 exits the thread */
  private int nesting = 0;

  /**
   * Create a pcode interpreter for a given environment/method pair.  This will
   * in general be a thread of execution within a script, either <code>main()</code>
   * or a spawned thread.
   * @param env Thread-local environment for the running thread
   * @param method Method to execute in this instance of the interpreter
   */
  public PcodeInterpreter(Env env, CompiledMethod method) {
    this.env = env;
    code = method.getCodeArray();
    pushFrame(method);
  }

  /**
   * Execute the method, with given values for its parameters.
   * @param params Method parameters
   */
  public void exec(Value...params) {
    setActualParams(params);
    while (true) {
      PseudoOp op = code[pc++];
      Clock.stop();
      Trace.trace(Item.EVAL,"depth=%-4d pc=%4d: %s",nesting,pc-1,op);
      Clock.start();
      try {
        if (op.mayTriggerGc() || op.affectsControlFlow()) {
          gcSafePoint(op, true);
        }
        op.exec(env);
        if (op.affectsControlFlow()) {
          if (op.isBranch()) {
            /*
             * Branch
             */
            if (op.isTaken(env)) {
              pc = op.getBranchTarget();
            }
          } else {
            if (op.isCall()) {
              methodCall((CallNormalOp)op);
            } else if (op.isReturn()) {
              if (nesting == 0) {
                break;
              }
              methodReturn((ReturnOp)op);
            }
          }
        }
      } catch (RuntimeException e) {
        System.err.printf("Runtime exception encountered at line %d, column %d%n",
            op.getLine(),op.getColumn());
        stackTrace(op);
        throw e;
      }
    }
  }

  private void gcSafePoint(PseudoOp op, boolean unconditional) {
    if (unconditional || env.gcRequested()) {
      saveContext(op,env.top());
      env.gcSafePoint();
    }
  }

  /**
   * Return from a method call
   * @param retOp The return pseudo-op
   */
  private void methodReturn(ReturnOp retOp) {
    StackFrame calleeFrame = env.top();
    env.pop();
    StackFrame callerFrame = env.top();

    if (retOp.hasOperand()) {
      callerFrame.setResult(retOp.getOperand(calleeFrame));
    }
    nesting--;
    pc = callerFrame.getSavedPc();
    code = callerFrame.getSavedMethod();
  }

  /**
   * Perform a method call
   * @param callOp The call pseudo-op
   */
  private void methodCall(CallNormalOp callOp) {
    CompiledMethod callee = callOp.getMethod();
    StackFrame callerFrame = env.top();
    saveContext(callOp, callerFrame);
    pushFrame(callee);
    setActualParams(callOp.getOperandValues(callerFrame));
    code = callee.getCodeArray();
    pc = 0;
    nesting++;
  }

  /**
   * Push a new stack frame for a given method
   * @param callee
   */
  private void pushFrame(CompiledMethod callee) {
    env.pushFrame(callee);
  }

  /**
   * Save context in the caller's stack frame
   * @param callOp
   * @param frame
   */
  private void saveContext(PseudoOp callOp, StackFrame frame) {
    frame.savePc(pc);
    frame.saveMethod(code);
    if (callOp.hasResult()) {
      frame.setResultSlot(callOp.getResult());
    } else {
      frame.clearResultSlot();
    }
  }

  /**
   * Initialize the actual parameters in a method call.  Assumes that
   * the callee stack frame has already been pushed on the stack.
   * @param actuals
   */
  private void setActualParams(Value[] actuals) {
    StackFrame calleeFrame = env.top();
    for (int i=0; i < actuals.length; i++) {
      calleeFrame.set(i,actuals[i]);
    }
  }

  /**
   * Print a (script) stack trace
   * @param op The current op at top of stack.
   */
  private void stackTrace(PseudoOp op) {
    saveContext(op,env.top());
    for (StackFrame frame : env.iterator()) {
      op = frame.getSavedMethod()[frame.getSavedPc()-1];
      System.err.println(op.getSourceLocation("at "));
    }
  }
}
