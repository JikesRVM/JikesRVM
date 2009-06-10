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
package org.jikesrvm.compilers.opt.util;

/**
 * An object that implements a bijection between whole numbers and
 * objects.
 */
public interface BitSetMapping {
  /**
   * Return the object numbered n.
   */
  Object getMappedObject(int n);

  /**
   * Return the number of a given object.
   */
  int getMappedIndex(Object o);

  /**
   * Return the size of the domain of the bijection.
   */
  int getMappingSize();
}
