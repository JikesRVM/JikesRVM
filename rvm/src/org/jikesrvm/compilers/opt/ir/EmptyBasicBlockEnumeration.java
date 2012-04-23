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
package org.jikesrvm.compilers.opt.ir;

/**
 *  This class is used only for the pre-allocated empty enumeration in
 * BasicBlockEnumeration.  It cannot be an anonymous class in
 * BasicBlockEnumeration because BasicBlockEnumeration is an
 * interface, and when javadoc sees the anonymous class, it converts
 * it into a private member of the interface.  It then complains that
 * interfaces cannot have private members.  This is truly retarded,
 * even by Java's low standards.
 */
class EmptyBasicBlockEnumeration implements BasicBlockEnumeration {

  @Override
  public boolean hasMoreElements() { return false; }

  @Override
  public BasicBlock nextElement() { return next(); }

  @Override
  public BasicBlock next() {
    throw new java.util.NoSuchElementException("Empty BasicBlock Enumeration");
  }
}

