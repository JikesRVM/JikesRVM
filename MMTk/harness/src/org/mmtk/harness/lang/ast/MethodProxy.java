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
package org.mmtk.harness.lang.ast;

import java.util.List;

import org.mmtk.harness.lang.Visitor;
import org.mmtk.harness.lang.parser.MethodTable;
import org.mmtk.harness.lang.type.Type;

/**
 * A proxy for a method, used so that we can defer lookup of a method
 * from parse time to execution time.
 */
public class MethodProxy extends Method {

  /** The method table in which to lookup the real method */
  private final MethodTable methods;

  /** After doing the lookup, this points to the actual method */
  private Method actualMethod = null;

  /**
   * @param methods
   * @param name
   * @param params
   */
  public MethodProxy(MethodTable methods, String name, int params) {
    super(name,params,Type.VOID);
    this.methods = methods;
  }

  @Override
  public Object accept(Visitor v) {
    return v.visit(this);
  }

  @Override
  public Type getReturnType() {
    return getMethod().getReturnType();
  }

  @Override
  public Method getMethod() {
    if (actualMethod == null) {
      actualMethod = methods.get(name);
    }
    return actualMethod;
  }

  @Override
  public List<Type> getParamTypes() {
    return getMethod().getParamTypes();
  }
}
