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

import org.mmtk.harness.lang.ast.AST;

/**
 * A user-defined type.
 */
public interface UserType extends Type, AST {

  /**
   * Define a field in a user-defined type
   * @param name
   * @param type
   */
  void defineField(String name, Type type);

  /**
   * Get a field by name
   * @param name
   * @return
   */
  Field getField(String name);

  /**
   * The number of reference fields in the type
   * @return
   */
  int referenceFieldCount();

  /**
   * The number of non-reference fields in the type
   * @return
   */
  int dataFieldCount();
}
