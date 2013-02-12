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

import java.util.Vector;

import org.junit.Test;

public class ReverseEnumeratorTest {

  @Test
  public void testNextElementAndHasMoreElements() {
    Vector<Integer> v = new Vector<Integer>();
    v.add(1);
    v.add(2);
    v.add(3);
    ReverseEnumerator<Integer> re = new ReverseEnumerator<Integer>(v.elements());
    assertTrue(re.hasMoreElements());
    assertEquals((Integer) 3,re.nextElement());
    assertTrue(re.hasMoreElements());
    assertEquals((Integer) 2,re.nextElement());
    assertTrue(re.hasMoreElements());
    assertEquals((Integer) 1,re.nextElement());
    assertFalse(re.hasMoreElements());
  }
}
