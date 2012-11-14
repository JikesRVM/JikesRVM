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
import static org.jikesrvm.tests.util.TestingTools.*;

import java.util.Vector;

import org.junit.Test;

public class CompoundEnumeratorTest {

  @Test
  public void testHasMoreElementsAndNextElement() {
    Vector<Integer> v0 = asVector(1,2);
    Vector<Integer> v1 = asVector(1);
    CompoundEnumerator<Integer> ce = new CompoundEnumerator<Integer>(v0.elements(),v1.elements());

    assertTrue(ce.hasMoreElements());
    ce.nextElement();
    assertEquals((Integer) 2,ce.nextElement());
    assertEquals((Integer) 1,ce.nextElement());
    assertFalse(ce.hasMoreElements());
  }
}
