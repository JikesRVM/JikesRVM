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
package org.mmtk.harness.lang.compiler;

import org.mmtk.harness.lang.ast.NormalMethod;
import org.mmtk.harness.lang.pcode.PseudoOp;

public class CompiledMethodProxy extends CompiledMethod {
  private final CompiledMethodTable table;

  public CompiledMethodProxy(NormalMethod method, CompiledMethodTable table) {
    super(method);
    this.table = table;
  }

  public boolean isResolved() {
    return false;
  }

  @Override
  public CompiledMethod resolve() {
    return table.get(getName());
  }

  @Override
  public PseudoOp[] getCodeArray() {
    throw new RuntimeException("Unresolved method reference");
  }


}
