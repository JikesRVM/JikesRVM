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
package org.mmtk.harness.lang.pcode;

import java.util.List;

import org.mmtk.harness.lang.ast.AST;
import org.mmtk.harness.lang.compiler.Register;

public abstract class CallOp extends EnnaryOp {

  public CallOp(AST source, Register resultTemp, List<Register> params) {
    super(source,"call", resultTemp, params);
  }

  public CallOp(AST source, List<Register> params) {
    super(source,"call", params);
  }

  public boolean affectsControlFlow() {
    return true;
  }
}
