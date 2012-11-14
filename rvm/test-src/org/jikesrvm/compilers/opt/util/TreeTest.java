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
import static org.hamcrest.Matchers.*;
import static org.jikesrvm.tests.util.TestingTools.*;

import java.util.Enumeration;

import org.junit.Test;

public class TreeTest {

  private TreeNode root = new TreeNode();
  private TreeNode n0 = new TreeNode();
  private TreeNode n1 = new TreeNode();

  private Tree t = newBigTree();

  private Tree newBigTree() {
    root.addChild(n0);
    root.addChild(n1);
    return new Tree(root);
  }

  @Test
  public void testIsEmpty() {
    Tree t0 = new Tree();
    assertTrue(t0.isEmpty());
    assertFalse(t.isEmpty());
  }

  @Test
  public void testGetRoot() {
    assertSame(root, t.getRoot());
  }

  @Test
  public void testSetRoot() {
    Tree t0 = new Tree();
    t0.setRoot(root);
    assertSame(root, t.getRoot());
  }

  @Test
  public void testElements() {
    Enumeration<TreeNode> en = t.elements();
    assertThat(toList(en), contains(root,n0,n1));
    assertFalse(en.hasMoreElements());
  }

  @Test
  public void testNumberOfNodes() {
    assertEquals(3, t.numberOfNodes());
  }

  @Test
  public void testEmptyTopDownEnumerator() {
    Tree t0 = new Tree();
    Enumeration<TreeNode> en = t0.getTopDownEnumerator();
    assertFalse(en.hasMoreElements());
  }

  @Test
  public void testEmptyBottomUpEnumerator() {
    Tree t0 = new Tree();
    Enumeration<TreeNode> en = t0.getBottomUpEnumerator();
    assertFalse(en.hasMoreElements());
  }

  @Test
  public void testGetBottomUpEnumerator() {
    Enumeration<TreeNode> en = t.getBottomUpEnumerator();
    assertTrue(en.hasMoreElements());
    assertSame(n0, en.nextElement());
    assertSame(n1, en.nextElement());
    assertSame(root, en.nextElement());
    assertFalse(en.hasMoreElements());
  }

  @Test
  public void testGetTopDownEnumerator() {
    Enumeration<TreeNode> en = t.getTopDownEnumerator();
    assertTrue(en.hasMoreElements());
    assertSame(root, en.nextElement());
    assertSame(n0, en.nextElement());
    assertSame(n1, en.nextElement());
    assertFalse(en.hasMoreElements());
  }
}
