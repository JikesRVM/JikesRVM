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
package org.mmtk.harness.lang.type;

import org.mmtk.harness.lang.runtime.Value;
import org.mmtk.vm.ReferenceProcessor.Semantics;

/**
 * Types in the scripting language.
 */
public interface Type {

  /** The built-in "int" type */
  Type INT = new IntType();
  /** The built-in "object" type */
  Type OBJECT = new ObjectType();
  /** The type of the 'null' constant */
  Type NULL = new ObjectType();
  /** The built-in "string" type */
  Type STRING = new StringType();
  /** The built-in "boolean" type */
  Type BOOLEAN = new BooleanType();
  /** The built-in "void" type */
  Type VOID = new VoidType();
  /** The built-in "reference" type */
  Type WEAKREF = new ReferenceType(Semantics.WEAK);
  /** The built-in "reference" type */
  Type SOFTREF = new ReferenceType(Semantics.SOFT);
  /** The built-in "reference" type */
  Type PHANTOMREF = new ReferenceType(Semantics.PHANTOM);

  /** @return The name of the type */
  String getName();

  /**
   * @param rhs RHS of the assignment
   * @return Is this type assignment-compatible with the rhs type
   */
  boolean isCompatibleWith(Type rhs);

  /** @return initial value of variables of this type */
  Value initialValue();

  /** @return is this an object type (as opposed to a primitive) */
  boolean isObject();

  /** @return is this user-defined (as opposed to built-in) */
  boolean isUserType();
}
