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
package org.mmtk.harness.lang.ast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.mmtk.harness.lang.Declaration;
import org.mmtk.harness.lang.Visitor;
import org.mmtk.harness.lang.parser.Token;
import org.mmtk.harness.lang.type.Type;

/**
 * A method is a set of variable declarations followed by a statement.
 */
public class NormalMethod extends Method {
  /** The variable declarations */
  private final List<Declaration> decls;
  /** The statement this block will execute */
  private Statement body;

  /**
   * Create a new method.
   */
  public NormalMethod(Token t, String name, int params, Type returnType, List<Declaration> decls, Statement body) {
    super(t,name,params,returnType);
    this.decls = decls;
    this.body = body;
  }

  /** Accept visitors */
  @Override
  public Object accept(Visitor v) {
    return v.visit(this);
  }

  /** Return the list of variable declarations */;
  public List<Declaration> getDecls() {
    return Collections.unmodifiableList(decls);
  }

  /** Return the list of parameter declarations */
  public List<Declaration> getParams() {
    return Collections.unmodifiableList(decls.subList(0, getParamCount()));
  }

  public Statement getBody() {
    return body;
  }

  public void setBody(Statement newBody) {
    body = newBody;
  }

  @Override
  public List<Type> getParamTypes() {
    List<Type> result = new ArrayList<Type>();
    for (Declaration decl : getParams()) {
      result.add(decl.getType());
    }
    return result;
  }
}
