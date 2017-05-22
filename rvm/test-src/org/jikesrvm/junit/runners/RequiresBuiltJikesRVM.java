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
package org.jikesrvm.junit.runners;

/**
 * Indicates that a unit test or test method must be executed on the
 * built Jikes RVM.
 * <p>
 * This generally means that the unit test is accessing the internals
 * of the Jikes RVM, e.g. to to get data about methods and types.
 */
public final class RequiresBuiltJikesRVM {

  private RequiresBuiltJikesRVM() { }
}
