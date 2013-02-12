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
package org.jikesrvm.osr;

import static org.junit.Assert.*;
import static org.hamcrest.Matchers.*;
import static org.jikesrvm.tests.util.TestingTools.*;

import org.jikesrvm.junit.runners.RequiresJikesRVM;
import org.jikesrvm.junit.runners.VMRequirements;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

@RunWith(VMRequirements.class)
public class TypeStackTest {

  private static final byte DEFAULT_VALUE = 0;
  private static final byte EMPTY = 0;
  private static final int TYPE_STACK_LENGTH = 3;

  private static final byte B0 = 0;
  private static final byte B1 = 1;

  private TypeStack newTypeStack() {
    TypeStack t = new TypeStack(TYPE_STACK_LENGTH, DEFAULT_VALUE);
    t.push(B0);
    t.push(B1);
    return t;
  }

  @Test
  public void testSnapshot() {
    TypeStack t = newTypeStack();
    byte [] snapshot = t.snapshot();

    assertThat(boxed(snapshot), arrayContaining(B0, B1, DEFAULT_VALUE));
  }

  @Test
  public void testPop() {
    TypeStack t = newTypeStack();

    t.pop();
    assertEquals(0, t.peek());
    assertEquals(1, t.depth());
    t.pop();
    assertEquals(EMPTY, t.depth());
  }

  @Test
  public void testZeroElementsForPopInt() {
    TypeStack t = newTypeStack();
    t.pop(0);

    assertThat(boxed(t.snapshot()), arrayContaining(B0, B1, DEFAULT_VALUE));
    assertEquals(2, t.depth());
  }

  @Test
  public void testPopInt() {
    TypeStack t = newTypeStack();
    t.pop(1);

    assertThat(boxed(t.snapshot()), arrayContaining(B0, DEFAULT_VALUE, DEFAULT_VALUE));
    assertEquals(1, t.depth());
  }

  @Test
  public void testMultipleElementsForPopInt() {
    TypeStack t = newTypeStack();
    t.pop(2);

    assertThat(boxed(t.snapshot()), arrayContaining(DEFAULT_VALUE, DEFAULT_VALUE, DEFAULT_VALUE));
    assertEquals(EMPTY, t.depth());
  }

  @Category(RequiresJikesRVM.class)
  @Test(expected=ArrayIndexOutOfBoundsException.class)
  public void testPopMoreElementsThanExisting() {
    TypeStack t = newTypeStack();
    t.pop(3);
  }

  @Test
  public void testPushAndPeek() {
    TypeStack t = new TypeStack(TYPE_STACK_LENGTH, DEFAULT_VALUE);
    t.push(B0);
    assertEquals(B0, t.peek());
    t.push(B1);
    assertEquals(B1, t.peek());
  }

  @Test(expected=ArrayIndexOutOfBoundsException.class)
  public void testPeekingAnEmptyStack() {
    TypeStack t = new TypeStack(TYPE_STACK_LENGTH, DEFAULT_VALUE);
    t.peek();
  }

  @Test
  public void testEmptyStack() {
    TypeStack t = new TypeStack(TYPE_STACK_LENGTH, DEFAULT_VALUE);
    assertEquals(EMPTY, t.depth());
    byte [] snapshot = t.snapshot();
    assertThat(boxed(snapshot), arrayContainingInAnyOrder(DEFAULT_VALUE, DEFAULT_VALUE, DEFAULT_VALUE));
  }

  @Test
  public void testClearEmptyStack() {
    TypeStack t = new TypeStack(TYPE_STACK_LENGTH, DEFAULT_VALUE);
    assertEquals(EMPTY, t.depth());
    t.clear();
    assertEquals(EMPTY, t.depth());
    byte [] snapshot = t.snapshot();
    assertThat(boxed(snapshot), arrayContaining(DEFAULT_VALUE, DEFAULT_VALUE, DEFAULT_VALUE));
  }

  @Test
  public void testDepth() {
    TypeStack t = new TypeStack(TYPE_STACK_LENGTH, DEFAULT_VALUE);
    assertEquals(EMPTY, t.depth());
    t.push((byte) 1);
    assertEquals(1, t.depth());
    t.push((byte) 2);
    assertEquals(2, t.depth());
    t.clear();
    assertEquals(EMPTY, t.depth());
  }
}
