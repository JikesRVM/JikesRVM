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
package org.mmtk.harness.lang.parser;

import java.util.HashMap;
import java.util.Map;

import org.mmtk.harness.lang.Method;

/**
 * Parser method table.
 */
public class MethodTable {

  private Map<String, Method> table = new HashMap<String, Method>();

  public void add(Method m) {
    if (SymbolTable.reservedWords.contains(m.getName()))
      throw new RuntimeException(m.getName() + " is a reserved word");
    if (table.containsKey(m.getName()))
      throw new RuntimeException("Method " + m.getName() + " already defined");
    table.put(m.getName(), m);
  }

  public Method get(String name) {
    if (!table.containsKey(name))
      throw new RuntimeException("Method " + name + " not found");
    return table.get(name);
  }
}
