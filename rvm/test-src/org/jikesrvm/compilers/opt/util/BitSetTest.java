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

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.jikesrvm.junit.runners.RequiresBootstrapVM;
import org.jikesrvm.junit.runners.VMRequirements;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

@RunWith(VMRequirements.class)
@Category(RequiresBootstrapVM.class)
public class BitSetTest {

  private static final String n0 = "Zero";
  private static final String n1 = "One";


  @Test
  public void testAddAndContains() {
    BitSetMapping m = mock(BitSetMapping.class);
    when(m.getMappedIndex(n0)).thenReturn(0);
    when(m.getMappedIndex(n1)).thenReturn(1);
    when(m.getMappedObject(0)).thenReturn(n0);
    when(m.getMappedObject(1)).thenReturn(n1);
    when(m.getMappingSize()).thenReturn(2);
    BitSet s = new BitSet(m);
    s.add(n0);
    s.add(n1);
    assertTrue(s.contains(n0));
    assertTrue(s.contains(n1));
  }

  @Test
  public void testAddAll() {
    BitSetMapping m = mock(BitSetMapping.class);when(m.getMappedIndex(n0)).thenReturn(0);
    when(m.getMappedIndex(n1)).thenReturn(1);
    when(m.getMappedObject(0)).thenReturn(n0);
    when(m.getMappedObject(1)).thenReturn(n1);
    when(m.getMappingSize()).thenReturn(2);
    BitSet s0 = new BitSet(m);
    BitSet s1 = new BitSet(m);
    s0.add(n0);
    s0.add(n1);
    s1.addAll(s0);
    assertTrue(s1.contains(n0) && s1.contains(n1));
  }

}
