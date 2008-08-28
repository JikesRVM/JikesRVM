/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
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

public class PcodeInterpreter {

  private final Env env;

  private PseudoOp[] code;
  private int pc = 0;
  private int nesting = 0;

  public PcodeInterpreter(Env env, CompiledMethod method) {
    this.env = env;
    code = method.getCodeArray();
    pushFrame(method);
  }

  /**
   * Execute the 'main' function
   * @param env
   * @param method
   */
  public void exec(Value...params) {
    setActualParams(params);
    while (true) {
      PseudoOp op = code[pc++];
      Trace.trace(Item.EVAL,"%-4d %4d: %s%n",nesting,pc,op);
      op.exec(env);
      if (op.affectsControlFlow()) {
        env.gcSafePoint();
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
    }
  }

  private void methodReturn(ReturnOp retOp) {
    {
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
  }

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

  private void pushFrame(CompiledMethod callee) {
    env.push(callee.formatStackFrame());
  }

  private void saveContext(CallNormalOp callOp, StackFrame callerFrame) {
    callerFrame.savePc(pc);
    callerFrame.saveMethod(code);
    if (callOp.hasResult()) {
      callerFrame.setResultSlot(callOp.getResult());
    }
  }

  private void setActualParams(Value[] actuals) {
    StackFrame calleeFrame = env.top();
    for (int i=0; i < actuals.length; i++) {
      calleeFrame.set(i,actuals[i]);
    }
  }
}
