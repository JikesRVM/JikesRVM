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

import java.util.Iterator;

import org.junit.Test;

public class StackTest {

  @Test
  public void testPush() {
    Stack<Integer> st = new Stack<Integer>();
    assertEquals((Integer) 1,st.push(1));
  }

  @Test
  public void testPop() {
    Stack<Integer> st = new Stack<Integer>();
    st.push(1);
    assertEquals((Integer) 1,st.pop());
    assertTrue(st.isEmpty());
  }

  @Test
  public void testPeek() {
    Stack<Integer> st = new Stack<Integer>();
    st.push(1);
    st.push(2);
    assertEquals((Integer) 2,st.peek());
  }

  @Test
  public void testEmpty() {
    Stack<Integer> st = new Stack<Integer>();
    st.push(1);
    assertFalse(st.empty());
    assertEquals((Integer) 1,st.pop());
    assertTrue(st.empty());
  }

  @Test
  public void testCompare() {
    Stack<Integer> st1 = new Stack<Integer>();
    Stack<Integer> st2 = new Stack<Integer>();
    st1.push(1);
    st1.push(2);
    st2.push(1);
    st2.push(2);
    st2.push(3);
    assertFalse(st1.compare(st2));
    st1.push(3);
    assertTrue(st1.compare(st2));
  }

  @Test
  public void testCopy() {
    Stack<Integer> st1 = new Stack<Integer>();
    st1.push(1);
    st1.push(2);
    Stack<Integer> st2 = st1.copy();
    assertTrue(st1.compare(st2));
  }

  @Test
  public void testIterator() {
    Stack<Integer> st1 = new Stack<Integer>();
    st1.push(1);
    st1.push(2);
    Iterator<Integer> it = st1.iterator();
    assertTrue(it.hasNext());
    it.next();
    assertTrue(it.hasNext());
    it.next();
    assertFalse(it.hasNext());
  }

}
