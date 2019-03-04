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
package org.jikesrvm;

import static org.junit.Assert.*;
import static org.jikesrvm.junit.runners.VMRequirements.isRunningOnBuiltJikesRVM;

import org.jikesrvm.junit.runners.VMRequirements;
import org.jikesrvm.junit.runners.RequiresBootstrapVM;
import org.jikesrvm.junit.runners.RequiresBuiltJikesRVM;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

@RunWith(VMRequirements.class)
@Category(RequiresBootstrapVM.class)
public class ExampleThatRequiresBootstrapVMTest {

  @Test
  @Category(RequiresBuiltJikesRVM.class)
  public void testThatRequiresJikesRVM() {
    fail();
  }

  @Test
  @Category(RequiresBootstrapVM.class)
  public void testThatRequiresBootstrapVM() {
    assertFalse(isRunningOnBuiltJikesRVM());
  }

  @Test
  public void testWithNoRequirements() {
    assertFalse(isRunningOnBuiltJikesRVM());
  }
}
