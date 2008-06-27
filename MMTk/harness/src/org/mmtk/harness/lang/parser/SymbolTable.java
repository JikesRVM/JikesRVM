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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import org.mmtk.harness.lang.Declaration;
import org.mmtk.harness.lang.IntValue;
import org.mmtk.harness.lang.ObjectValue;
import org.mmtk.harness.lang.Type;
import org.mmtk.harness.lang.Value;
import org.vmmagic.unboxed.ObjectReference;

/**
 * Parser symbol table.
 *
 * Implements java-style scoping rules.  Allocates variables to slots in a
 * stack frame linearly and never re-uses slots.
 *
 * TODO - implement stack maps so that out-of-scope variables don't hold on
 * to unreachable objects.
 */
public class SymbolTable {

  /** These are illegal variable and method names */
  public static final List<String> reservedWords = new ArrayList<String>();

  static {
    reservedWords.add("alloc");
    reservedWords.add("assert");
    reservedWords.add("else");
    reservedWords.add("gc");
    reservedWords.add("hash");
    reservedWords.add("if");
    reservedWords.add("int");
    reservedWords.add("object");
    reservedWords.add("print");
    reservedWords.add("spawn");
    reservedWords.add("tid");
    reservedWords.add("while");
  }

  private static final boolean TRACE = false;

  /**
   * A symbol in the symbol table
   */
  private class Symbol {
    /** Variable name */
    String name;

    /** Variable type */
    Type type;

    /** Syntactic nesting level */
    int level;

    /** stack frame location */
    int location;

    Symbol(String name, Type type) {
      this.name = name;
      this.type = type;
      this.location = nextLocation++;
      this.level = currentScope;
      if (TRACE) System.out.println("Declaring variable "+name+" at location "+location);
    }
  }

  /** The table of symbols */
  private Map<String, Symbol> table = new HashMap<String,Symbol>();

  /** The stack frame map */
  private List<Declaration> stackMap = new ArrayList<Declaration>();

  /** The next free stack frame slot */
  private int nextLocation = 0;

  /** The current syntactic scope level */
  private int currentScope = 0;

  /**
   * Declare a new variable
   *
   * @param name Variable name
   * @param type Variable type
   */
  void declare(String name, Type type) {
    if (reservedWords.contains(name))
      throw new RuntimeException(name + " is a reserved word");
    if (table.containsKey(name))
      throw new RuntimeException("Symbol "+name+" already defined");
    Symbol symbol = new Symbol(name,type);
    table.put(name, symbol);
    stackMap.add(new Declaration(name,initialValue(type),symbol.location));
  }

  /**
   * Is the given variable defined ?
   * @param name
   * @return
   */
  boolean isDefined(String name) {
    return table.containsKey(name);
  }

  /**
   * Type of the named variable
   * @param name
   * @return
   */
  Type getType(String name) {
    return table.get(name).type;
  }

  /**
   * Stack frame location of the given variable
   * @param name
   * @return
   */
  int getLocation(String name) {
    Symbol symbol = table.get(name);
    if (symbol == null) {
      throw new RuntimeException(String.format("symbol \"%s\" not found",name));
    }
    return symbol.location;
  }

  /**
   * Return the symbol table as a list of declarations
   * @return
   */
  List<Declaration> declarations() {
    return Collections.unmodifiableList(stackMap);
  }

  /**
   * Enter an inner syntactic scope
   */
  void pushScope() {
    currentScope++;
  }

  /**
   * Leave an inner syntactic scope, deleting all inner symbols
   */
  void popScope() {
    Set<Entry<String, Symbol>> entrySet = table.entrySet();
    Iterator<Entry<String, Symbol>> iterator = entrySet.iterator();
    while (iterator.hasNext()) {
      Map.Entry<String, Symbol> entry = iterator.next();
      if (entry.getValue().level == currentScope)
        iterator.remove();
    }
    currentScope--;
  }

  /**
   * Initial value for a variable of a given type.  Actually allocates
   * the Value object that will hold the variables value.
   *
   * @param type
   * @return
   */
  private static Value initialValue(Type type) {
    switch(type) {
      case INT: return new IntValue(0);
      case OBJECT: return new ObjectValue(ObjectReference.nullReference());
    }
    throw new RuntimeException("Invalid type");
  }
}
