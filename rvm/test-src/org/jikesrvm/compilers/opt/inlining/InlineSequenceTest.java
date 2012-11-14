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
package org.jikesrvm.compilers.opt.inlining;

import static org.junit.Assert.*;
import static org.hamcrest.Matchers.*;
import static org.jikesrvm.tests.util.TestingTools.*;

import java.util.Enumeration;

import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.junit.runners.RequiresJikesRVM;
import org.jikesrvm.junit.runners.VMRequirements;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

@RunWith(VMRequirements.class)
@Category(RequiresJikesRVM.class)
public class InlineSequenceTest {

  public class Methods {
    public void root(){}

    public void m(){}

    public void n(){}
  }

  @Test
  public void testEquals() throws Exception {
    InlineSequence s0 = createInlineSequence(Methods.class, "root");
    InlineSequence s1 = createInlineSequence(Methods.class, "root");

    assertEquals(s0, s1);
  }

  @Test
  public void testEqualsWithMoreInlines() throws Exception {
    InlineSequence s0 = createInlineSequence(Methods.class, "root");
    InlineSequence s1 = createInlineSequence(s0, 0, Methods.class, "m");
    InlineSequence s2 = createInlineSequence(s1, 1, Methods.class, "m");

    InlineSequence t0 = createInlineSequence(Methods.class, "root");
    InlineSequence t1 = createInlineSequence(t0, 0, Methods.class, "m");
    InlineSequence t2 = createInlineSequence(t1, 1, Methods.class, "m");

    assertTrue(s2.equals(t2));

  }

  @Test
  public void testGetMethod() throws Exception {
    InlineSequence s0 = createInlineSequence(Methods.class, "root");
    InlineSequence s1 = createInlineSequence(s0, 0, Methods.class, "m");
    InlineSequence s2 = createInlineSequence(s1, 2, Methods.class, "n");

    NormalMethod root = getNormalMethod(Methods.class, "root");
    NormalMethod a = getNormalMethod(Methods.class, "m");
    NormalMethod b = getNormalMethod(Methods.class, "n");

    assertEquals(root, s0.getMethod());
    assertEquals(a, s1.getMethod());
    assertEquals(b, s2.getMethod());
  }

  @Test
  public void testGetCaller() throws Exception {
    InlineSequence s0 = createInlineSequence(Methods.class, "root");
    InlineSequence s1 = createInlineSequence(s0, 0, Methods.class, "m");
    InlineSequence s2 = createInlineSequence(s1, 2, Methods.class, "n");

    assertNull(s0.getCaller());
    assertEquals(s0, s1.getCaller());
    assertEquals(s1, s2.getCaller());
  }

  @Test
  public void testGetCallSite() throws Exception {
    NormalMethod m = getNormalMethod(Methods.class, "root");
    InlineSequence s0 = new InlineSequence(m);

    assertNull(s0.getCallSite());
  }

  @Test
  public void testGetInlineDepthWithEmptySequence() throws Exception {
    InlineSequence s0 = createInlineSequence(Methods.class, "root");

    assertEquals(0, s0.getInlineDepth());
  }

  @Test
  public void testGetInlineDepth() throws Exception {
    InlineSequence s0 = createInlineSequence(Methods.class, "root");
    InlineSequence s1 = createInlineSequence(s0, 0, Methods.class, "m");
    InlineSequence s2 = createInlineSequence(s1, 2, Methods.class, "n");

    assertEquals(2, s2.getInlineDepth());
  }

  @Test
  public void testGetRootMethod() throws Exception {
    InlineSequence s0 = createInlineSequence(Methods.class, "root");
    InlineSequence s1 = createInlineSequence(s0, 0, Methods.class, "m");
    InlineSequence s2 = createInlineSequence(s1, 2, Methods.class, "n");

    NormalMethod methodroot = getNormalMethod(Methods.class, "root");

    assertEquals(methodroot, s2.getRootMethod());
  }

  @Test
  public void testContainsMethod() throws Exception {
    InlineSequence s0 = createInlineSequence(Methods.class, "root");
    InlineSequence s1 = createInlineSequence(s0, 0,Methods.class, "m");
    InlineSequence s2 = createInlineSequence(s1, 2,Methods.class, "n");

    assertTrue(s0.containsMethod(s0.getMethod()));
    assertTrue(s1.containsMethod(s1.getMethod()));
    assertTrue(s2.containsMethod(s2.getMethod()));

    assertTrue(s1.containsMethod(s0.getMethod()));
    assertTrue(s2.containsMethod(s0.getMethod()));
    assertTrue(s2.containsMethod(s1.getMethod()));
  }

  @Test
  public void testEnumerateFromRoot() throws Exception {
    InlineSequence s0 = createInlineSequence(Methods.class, "root");
    InlineSequence s1 = createInlineSequence(s0, 0, Methods.class, "m");
    InlineSequence s2 = createInlineSequence(s1, 2, Methods.class, "n");

    Enumeration<InlineSequence> en = s2.enumerateFromRoot();

    assertThat(toList(en), contains(s1,s2));
  }
}
