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
package org.mmtk.harness.lang.pcode;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.Set;
import java.util.TreeSet;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mmtk.harness.Harness;
import org.mmtk.harness.lang.Visitor;
import org.mmtk.harness.lang.ast.AST;
import org.mmtk.harness.lang.compiler.Register;
import org.mmtk.harness.lang.parser.Token;
import org.mmtk.harness.lang.type.Type;
import org.vmmagic.unboxed.harness.ArchitecturalWord;

public class BranchTest {

  @BeforeClass
  public static void initHarness() {
    ArchitecturalWord.init(Harness.bits.getValue());
  }

  @Test
  public void toStringForBranchWorksCorrectly() throws Exception {
    int registerIndex = 4;
    Register objectTemporary = createLocalObjectRegister(registerIndex);
    AST ignored = new MockAST();
    int target = 2;
    Branch falseBranch = new Branch(ignored, objectTemporary, false, target);
    String expectedFalseBranchToString = "[] if(!t" + Integer.toString(registerIndex) + ") goto " + Integer.toString(target);
    String actualFalseBranchToString = falseBranch.toString();
    Assert.assertEquals(expectedFalseBranchToString, actualFalseBranchToString);
    Branch trueBranch = new Branch(ignored, objectTemporary, true, target);
    String expectedTrueBranchToString = "[] if(t" + Integer.toString(registerIndex) + ") goto " + Integer.toString(target);
    String actualTrueBranchToString = trueBranch.toString();
    Assert.assertEquals(expectedTrueBranchToString, actualTrueBranchToString);
  }

  @Test
  public void gcMapsInToStringIsHandledCorrectly() throws Exception {
    int registerIndex = 5;
    Register objectTemporary = createLocalObjectRegister(registerIndex);
    AST ignored = new MockAST();
    int target = 2;
    Branch trueBranch = new Branch(ignored, objectTemporary, true, target);
    Set<Register> live = new TreeSet<Register>(new RegisterComparator());
    int firstLiveRegIndex = 3;
    int secondLiveRegIndex = 1;
    live.add(createLocalObjectRegister(firstLiveRegIndex));
    live.add(createLocalObjectRegister(secondLiveRegIndex));
    trueBranch.setGcMap(live);
    LinkedList<Integer> allRegisters = new LinkedList<Integer>();
    allRegisters.add(firstLiveRegIndex);
    allRegisters.add(secondLiveRegIndex);
    Collections.sort(allRegisters);
    String expectedToString = "[" + allRegisters.get(0) + "," + allRegisters.get(1) + "] if(t" + Integer.toString(registerIndex) + ") goto " + Integer.toString(target);
    String actualToStrring = trueBranch.toString();
    Assert.assertEquals(expectedToString, actualToStrring);
  }

  private Register createLocalObjectRegister(int registerIndex) {
    return Register.createLocal(registerIndex, Type.OBJECT);
  }

  private static class MockAST implements AST {

    @Override
    public Object accept(Visitor v) {
      return null;
    }

    @Override
    public int getLine() {
      return 1;
    }

    @Override
    public int getColumn() {
      return 2;
    }

    @Override
    public Token getToken() {
      return null;
    }

    @Override
    public String sourceLocation(String prefix) {
      return "ignored";
    }

  }

  private static class RegisterComparator implements Comparator<Register> {

    @Override
    public int compare(Register o1, Register o2) {
      return o1.getIndex() - o2.getIndex();
    }

  }

}
