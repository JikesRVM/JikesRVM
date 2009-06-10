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

import org.mmtk.harness.lang.ast.AST;
import org.mmtk.harness.lang.compiler.Register;

public abstract class NullaryOp extends PseudoOp {

  public NullaryOp(AST source, String name, Register resultTemp) {
    super(source, 0, name, resultTemp);
  }

  public NullaryOp(AST source, String name) {
    super(source, 0, name);
  }
}
