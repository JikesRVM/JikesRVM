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

  @Test
  public void testIsEmpty() {
    Tree t = new Tree();
    assertTrue(t.isEmpty());
    assertEquals(0, t.numberOfNodes());
  }

  @Test
  public void testGetRoot() {
    TreeNode root = new TreeNode();
    Tree t = new Tree(root);
    assertSame(root, t.getRoot());
  }

  @Test
  public void testSetRoot() {
    TreeNode root = new TreeNode();
    Tree t = new Tree();
    t.setRoot(root);
    assertSame(root, t.getRoot());
  }

  @Test
  public void testElements() {
    TreeNode root = new TreeNode();
    TreeNode tn2 = new TreeNode();
    TreeNode tn3 = new TreeNode();
    root.addChild(tn2);
    root.addChild(tn3);
    Tree t = new Tree(root);
    Enumeration<TreeNode> en = t.elements();
    assertThat(toList(en), contains(root,tn2,tn3));
    assertFalse(en.hasMoreElements());
  }

  @Test
  public void testNumberOfNodes() {
    TreeNode root = new TreeNode();
    TreeNode tn2 = new TreeNode();
    TreeNode tn3 = new TreeNode();
    root.addChild(tn2);
    root.addChild(tn3);
    Tree t = new Tree(root);
    assertEquals(3, t.numberOfNodes());
  }

  @Test
  public void testEmptyTopDownEnumerator(){
    Tree t = new Tree();
    Enumeration<TreeNode> en = t.getTopDownEnumerator();
    assertFalse(en.hasMoreElements());
  }

  @Test
  public void testEmptyBottomUpEnumerator(){
    Tree t = new Tree();
    Enumeration<TreeNode> en = t.getBottomUpEnumerator();
    assertFalse(en.hasMoreElements());
  }

  @Test
  public void testGetBottomUpEnumerator() {
    TreeNode root = new TreeNode();
    TreeNode tn2 = new TreeNode();
    TreeNode tn3 = new TreeNode();
    root.addChild(tn2);
    tn2.addChild(tn3);
    Tree t = new Tree(root);
    Enumeration<TreeNode> en = t.getBottomUpEnumerator();
    assertTrue(en.hasMoreElements());
    assertSame(tn3, en.nextElement());
    assertSame(tn2, en.nextElement());
    assertSame(root, en.nextElement());
    assertFalse(en.hasMoreElements());
  }

  @Test
  public void testGetTopDownEnumerator() {
    TreeNode root = new TreeNode();
    TreeNode tn2 = new TreeNode();
    TreeNode tn3 = new TreeNode();
    root.addChild(tn2);
    root.addChild(tn3);
    Tree t = new Tree(root);
    Enumeration<TreeNode> en = t.getTopDownEnumerator();
    assertTrue(en.hasMoreElements());
    assertSame(root, en.nextElement());
    assertSame(tn2, en.nextElement());
    assertSame(tn3, en.nextElement());
    assertFalse(en.hasMoreElements());
  }

}
