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

import org.mmtk.harness.lang.type.Type;

/**
 * A symbol in the symbol table
 */
public class Symbol {
  /** Variable name */
  private final String name;

  /** Variable type */
  private final Type type;

  /** Syntactic nesting level */
  private final int level;

  /** stack frame location */
  private final int location;

  Symbol(SymbolTable table, String name, Type type) {
    this.name = name;
    this.type = type;
    this.location = table.getFreeLocation();
    this.level = table.getCurrentScope();
    if (SymbolTable.TRACE) System.out.println("Declaring variable "+name+" at location "+getLocation());
  }

  public String getName() {
    return name;
  }

  public Type getType() {
    return type;
  }

  int getLevel() {
    return level;
  }

  public int getLocation() {
    return location;
  }
}
