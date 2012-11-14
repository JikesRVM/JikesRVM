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
import static org.jikesrvm.tests.util.TestingTools.*;

import org.jikesrvm.junit.runners.RequiresJikesRVM;
import org.jikesrvm.junit.runners.VMRequirements;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

@RunWith(VMRequirements.class)
@Category(RequiresJikesRVM.class)
public class CallSiteTreeTest {

  public class Methods {
    public void root(){}

    public void m(){}

    public void n(){}
  }

  @Test
  public void testAddLocationAndFind() throws Exception {
    CallSiteTree t = new CallSiteTree();
    InlineSequence seq = new InlineSequence(getNormalMethod(Methods.class, "root"));
    CallSiteTreeNode location = t.addLocation(seq);
    assertEquals(location, t.find(seq));
  }

}
