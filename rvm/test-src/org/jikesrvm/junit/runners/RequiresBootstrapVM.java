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
 * Indicates that a unit test or test method should only be executed
 * on the JVM that is used to build Jikes RVM.
 * <p>
 * This is normally used to mark test that require features that aren't
 * available on Jikes RVM at the time (e.g. support for mocking with Mockito).
 */
public final class RequiresBootstrapVM {

  private RequiresBootstrapVM() { }
}
