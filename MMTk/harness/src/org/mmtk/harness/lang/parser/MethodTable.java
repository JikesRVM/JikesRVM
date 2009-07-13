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
package org.mmtk.harness.lang.parser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.mmtk.harness.lang.Trace;
import org.mmtk.harness.lang.ast.Method;
import org.mmtk.harness.lang.ast.NormalMethod;

/**
 * Parser method table.
 */
public class MethodTable {

  private Map<String, Method> table = new HashMap<String, Method>();

  MethodTable(Method...methods) {
    for (Method method : methods) {
      add(method);
    }
  }

  /**
   * Add a new method
   * @param m The method to add
   */
  public void add(Method m) {
    Trace.trace(Trace.Item.PARSER,"defining method %s", m.getName());
    if (SymbolTable.reservedWords.contains(m.getName()))
      throw new RuntimeException(m.getName() + " is a reserved word");
    if (table.containsKey(m.getName()))
      throw new RuntimeException("Method " + m.getName() + " already defined");
    table.put(m.getName(), m);
  }

  /**
   * @param name The name of the method
   * @return The method with the given name
   */
  public Method get(String name) {
    if (!table.containsKey(name))
      throw new RuntimeException("Method " + name + " not found");
    return table.get(name);
  }

  /**
   * @return The normal (ie not intrinsic) methods
   */
  public Iterable<NormalMethod> normalMethods() {
    List<NormalMethod> result = new ArrayList<NormalMethod>();
    for (Method m : table.values()) {
      if (m instanceof NormalMethod) {
        result.add((NormalMethod)m);
      }
    }
    return result;
  }
}
