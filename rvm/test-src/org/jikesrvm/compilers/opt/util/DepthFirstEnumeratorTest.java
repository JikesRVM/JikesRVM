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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.NoSuchElementException;

import org.jikesrvm.junit.runners.RequiresBootstrapVM;
import org.jikesrvm.junit.runners.VMRequirements;
import org.jikesrvm.tests.util.SimpleEnumeration;
import org.jikesrvm.util.EmptyEnumeration;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;


@RunWith(VMRequirements.class)
@Category(RequiresBootstrapVM.class)
public class DepthFirstEnumeratorTest {

  @Test
  public void testGraphWithOneNode() throws Exception {
    GraphNode nodeWithoutSuccessors = buildNodeWithoutSuccessor();

    DepthFirstEnumerator enumerator = getDepthFirstEnumerator(nodeWithoutSuccessors);
    assertThat(enumerator.hasMoreElements(), is(true));

    verifyFirstElement(enumerator, nodeWithoutSuccessors);
    assertThat(enumerator.hasMoreElements(), is(false));
  }

  private GraphNode buildNodeWithoutSuccessor() {
    GraphNode nodeWithoutSuccessors = mock(GraphNode.class);
    when(nodeWithoutSuccessors.outNodes()).thenReturn(EmptyEnumeration.<GraphNode>emptyEnumeration());
    return nodeWithoutSuccessors;
  }

  private DepthFirstEnumerator getDepthFirstEnumerator(GraphNode startNode) {
    DepthFirstEnumerator enumerator = new DepthFirstEnumerator(startNode);
    return enumerator;
  }

  private void verifyFirstElement(DepthFirstEnumerator depthFirstEnumerator,
      GraphNode startNode) {
    GraphNode firstElement = depthFirstEnumerator.nextElement();
    assertThat(firstElement, sameInstance(startNode));
  }

  @Test(expected = NoSuchElementException.class)
  public void testThatExceptionIsThrownWhenNoElementsAreLeft() {
    GraphNode nodeWithoutSuccessors = buildNodeWithoutSuccessor();

    DepthFirstEnumerator enumerator = getDepthFirstEnumerator(nodeWithoutSuccessors);
    enumerator.nextElement();
    enumerator.nextElement();
  }

  @Test
  public void testThatMultipleNodesAreEnumerated() throws Exception {
    GraphNode secondNode = buildNodeWithoutSuccessor();

    GraphNode startNode = mock(GraphNode.class);
    setUpSuccessorsForNode(startNode, secondNode);

    DepthFirstEnumerator enumerator = getDepthFirstEnumerator(startNode);
    verifyFirstElement(enumerator, startNode);
    verifyNextElement(enumerator, secondNode);

    assertThat(enumerator.hasMoreElements(), is(false));
  }

  private void setUpSuccessorsForNode(GraphNode startNode,
      GraphNode... successors) {
    ArrayList<GraphNode> successorNodes = new ArrayList<GraphNode>(successors.length);
    for (int i = 0; i < successors.length; i++) {
      successorNodes.add(successors[i]);
    }
    when(startNode.outNodes()).thenReturn(new SimpleEnumeration<GraphNode>(successorNodes));
  }

  private void verifyNextElement(DepthFirstEnumerator enumerator, GraphNode expected) {
    assertThat(enumerator.hasMoreElements(), is(true));
    GraphNode secondElement = enumerator.nextElement();
    assertThat(secondElement, sameInstance(expected));
  }

  @Test
  public void testThatEnumerationIsDepthFirst() throws Exception {
    GraphNode d = buildNodeWithoutSuccessor();

    GraphNode b = mock(GraphNode.class);
    setUpSuccessorsForNode(b, d);

    GraphNode c = buildNodeWithoutSuccessor();

    GraphNode startNode = mock(GraphNode.class);
    setUpSuccessorsForNode(startNode, b, c);

    DepthFirstEnumerator enumerator = getDepthFirstEnumerator(startNode);
    LinkedList<GraphNode> orderedList = new LinkedList<GraphNode>();
    while (enumerator.hasMoreElements()) {
      orderedList.addLast(enumerator.nextElement());
    }
    assertThat(orderedList.size(), is(4));
    assertThat(orderedList.indexOf(startNode), is(0));
    assertThat(orderedList.indexOf(d), is(orderedList.indexOf(b) + 1));
    assertTrue(orderedList.indexOf(c) > orderedList.indexOf(startNode));

    assertThat(enumerator.hasMoreElements(), is(false));
  }


  @Test(timeout = 100)
  public void testThatCirclesAreNotAProblem() throws Exception {
    GraphNode b = mock(GraphNode.class);

    GraphNode start = mock(GraphNode.class);
    setUpSuccessorsForNode(start, b);

    GraphNode c = mock(GraphNode.class);
    setUpSuccessorsForNode(b, c);

    GraphNode d = mock(GraphNode.class);
    setUpSuccessorsForNode(c, d);
    setUpSuccessorsForNode(d, start);

    DepthFirstEnumerator depthFirstEnumerator = getDepthFirstEnumerator(start);
    verifyFirstElement(depthFirstEnumerator, start);
    verifyNextElement(depthFirstEnumerator, b);
    verifyNextElement(depthFirstEnumerator, c);
    verifyNextElement(depthFirstEnumerator, d);
    assertThat(depthFirstEnumerator.hasMoreElements(), is(false));
  }

  @Test
  public void testBiggerGraph() throws Exception {
    GraphNode start = mock(GraphNode.class);

    GraphNode b = mock(GraphNode.class);
    GraphNode h = mock(GraphNode.class);
    GraphNode j = buildNodeWithoutSuccessor();
    setUpSuccessorsForNode(start, b, h, j);

    GraphNode d = mock(GraphNode.class);
    GraphNode c = buildNodeWithoutSuccessor();
    setUpSuccessorsForNode(b, d, c);

    GraphNode e = mock(GraphNode.class);
    setUpSuccessorsForNode(d, e, h);

    GraphNode f = buildNodeWithoutSuccessor();
    GraphNode g = buildNodeWithoutSuccessor();
    setUpSuccessorsForNode(e, f, g);

    GraphNode i = buildNodeWithoutSuccessor();
    setUpSuccessorsForNode(h, i);

    DepthFirstEnumerator enumerator = getDepthFirstEnumerator(start);
    LinkedList<GraphNode> orderedList = new LinkedList<GraphNode>();
    while (enumerator.hasMoreElements()) {
      orderedList.addLast(enumerator.nextElement());
    }

    assertThat(orderedList.size(), is(10));
    assertThat(orderedList.indexOf(start), is(0));
    boolean bIsDirectSuccessorOfStart = isDirectSuccessorOf(b, start, orderedList);
    boolean hIsDirectSuccessorOfStart = isDirectSuccessorOf(h, start, orderedList);
    boolean jIsDirectSuccessorOfStart = isDirectSuccessorOf(j, start, orderedList);
    assertThat(bIsDirectSuccessorOfStart || hIsDirectSuccessorOfStart ||
          jIsDirectSuccessorOfStart, is(true));

    boolean cIsDirectSuccessorOfB = isDirectSuccessorOf(c, b, orderedList);
    boolean dIsDirectSuccessorOfB = isDirectSuccessorOf(d, b, orderedList);
    assertThat(cIsDirectSuccessorOfB || dIsDirectSuccessorOfB, is(true));

    boolean eIsDirectSuccessorOfD = isDirectSuccessorOf(e, d, orderedList);
    boolean hIsDirectSuccessorOfD = isDirectSuccessorOf(h, d, orderedList);
    assertThat(eIsDirectSuccessorOfD || hIsDirectSuccessorOfD, is(true));

    boolean fIsDirectSuccessorofE = isDirectSuccessorOf(f, e, orderedList);
    boolean gIsDirectSuccessorofE = isDirectSuccessorOf(g, e, orderedList);
    assertThat(fIsDirectSuccessorofE || gIsDirectSuccessorofE, is(true));

    assertThat(isDirectSuccessorOf(i, h, orderedList), is(true));
  }

  private boolean isDirectSuccessorOf(GraphNode second,
      GraphNode first, LinkedList<GraphNode> orderedList) {
    return orderedList.indexOf(second) == orderedList.indexOf(first) + 1;
  }

}
