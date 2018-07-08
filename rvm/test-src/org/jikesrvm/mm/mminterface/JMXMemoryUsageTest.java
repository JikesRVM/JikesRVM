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
package org.jikesrvm.mm.mminterface;

import org.jikesrvm.junit.runners.Requires32BitAddressing;
import org.jikesrvm.junit.runners.RequiresBuiltJikesRVM;
import org.jikesrvm.junit.runners.VMRequirements;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mmtk.policy.ImmortalSpace;
import org.mmtk.policy.Space;
import org.mmtk.utility.heap.VMRequest;
import org.mmtk.vm.VM;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeThat;

@RunWith(VMRequirements.class)
@Category(RequiresBuiltJikesRVM.class)
public class JMXMemoryUsageTest {

  @Test
  public void canCreateEmptyUsage() throws Exception {
    JMXMemoryUsage empty = JMXMemoryUsage.empty();
    assertThat(empty.getUsed(), is(0L));
    assertThat(empty.getReserved(), is(0L));
    assertThat(empty.getMax(), is(0L));
  }

  @Category(Requires32BitAddressing.class) // all 64-bit spaces are contiguous
  @Test
  public void discontigousSpacesAreMappedCorrectly() throws Exception {
      assumeDiscontiguousSpacesPossible();
      Space space = createDiscontiguousSpace();
      JMXMemoryUsage usage = new JMXMemoryUsage(space);
      assertThat(usage.getMax(), is(-1L));
  }

  private Space createDiscontiguousSpace() {
    VMRequest discontiguous = VMRequest.discontiguous();
    return new ImmortalSpace("testspace", discontiguous);
  }

  private void assumeDiscontiguousSpacesPossible() {
    assumeThat(VM.HEAP_LAYOUT_64BIT, is(false));
  }

  @Category(Requires32BitAddressing.class) // all 64-bit spaces are contiguous
  @Test
  public void addingASpaceAndADiscontiguousSpaceLeadsToMaxOfMinusOne() throws Exception {
    assumeDiscontiguousSpacesPossible();
    Space space = createDiscontiguousSpace();
    JMXMemoryUsage usage = new JMXMemoryUsage(space);
    Space[] spaces = Space.getSpaces();
    int spaceCount = Space.getSpaceCount();
    for (int i = 0; i < spaceCount; i++) {
      usage.add(spaces[i]);
    }
    assertThat(usage.getMax(), is(-1L));
  }

}
