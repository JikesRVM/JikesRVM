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
package org.jikesrvm.util;

import static org.junit.Assert.assertFalse;

import java.util.Enumeration;
import java.util.NoSuchElementException;

import org.junit.Before;
import org.junit.Test;

public class EmptyEnumerationTest {

  private Enumeration<Object> emptyEnum;

  @Before
  public void setUp() {
    emptyEnum = EmptyEnumeration.<Object>emptyEnumeration();
  }

  @Test
  public void testHasMoreElements() throws Exception {
    assertFalse(emptyEnum.hasMoreElements());
  }

  @Test(expected = NoSuchElementException.class)
  public void testNextElement() throws Exception {
    emptyEnum.nextElement();
  }

}
