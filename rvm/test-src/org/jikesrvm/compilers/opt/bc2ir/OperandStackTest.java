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
package org.jikesrvm.compilers.opt.bc2ir;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;

import org.jikesrvm.compilers.opt.ir.operand.IntConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.Operand;
import org.jikesrvm.compilers.opt.ir.operand.TrueGuardOperand;
import org.jikesrvm.junit.runners.RequiresBootstrapVM;
import org.jikesrvm.junit.runners.VMRequirements;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

@RunWith(VMRequirements.class)
@Category(RequiresBootstrapVM.class)
public class OperandStackTest {

  private OperandStack stack;

  private void createOperandStackWithSize(int size) {
    stack = new OperandStack(size);
  }

  @Test
  public void newlyCreatedOperandStackIsEmpty() {
    createOperandStackWithSize(1);
    assertThat(stack.isEmpty(), is(true));
  }

  @Test
  public void stacksMayBeCreatedWithCapacityOfZero() throws Exception {
    createOperandStackWithSize(0);
  }

  @Test
  public void getSizeReturnsNumberOfOperandsOnTheStack() throws Exception {
    createOperandStackWithSize(25);
    stack.push(mockOperand());
    stack.push(mockOperand());
    stack.push(mockOperand());
    stack.push(mockOperand());
    assertThat(stack.getSize(), is(4));
  }

  @Test(expected = NegativeArraySizeException.class)
  public void stacksWithNegativeCapacityCannotBeCreated() throws Exception {
    createOperandStackWithSize(-1);
  }

  @Test(expected = ArrayIndexOutOfBoundsException.class)
  public void popOnEmptyStackCausesException() throws Exception {
    createOperandStackWithSize(1);
    stack.pop();
  }

  private Operand mockOperand() {
    return mock(Operand.class);
  }



  @Test(expected = ArrayIndexOutOfBoundsException.class)
  public void stacksCannotAcceptMorePushesThanCapacityAllows() throws Exception {
    createOperandStackWithSize(1);
    stack.push(mockOperand());
    stack.push(mockOperand());
  }

  @Test(expected = ArrayIndexOutOfBoundsException.class)
  public void usingPopMoreOftenThanPushLeadsToExceptions() throws Exception {
    createOperandStackWithSize(1);
    stack.push(mockOperand());
    stack.pop();
    stack.pop();
  }

  @Test(expected = ArrayIndexOutOfBoundsException.class)
  public void poppingMoreThanWasPushedLeadsToExceptions() throws Exception {
    createOperandStackWithSize(1);
    stack.push(mockOperand());
    stack.pop2();
  }

  @Test(expected = ArrayIndexOutOfBoundsException.class)
  public void pop2OnEmptyStackCausesException() throws Exception {
    createOperandStackWithSize(1);
    stack.pop2();
  }

  @Test
  public void negativeArgumentsForGetFromTopNeedNotCauseException() throws Exception {
    createOperandStackWithSize(1);
    stack.getFromTop(-1);
  }

  @Test(expected = ArrayIndexOutOfBoundsException.class)
  public void largeNegativeArgumentsForGetFromTopCauseException() throws Exception {
    createOperandStackWithSize(1);
    stack.getFromTop(-2);
  }


  @Test
  public void getFromTopWith0ReturnsTheOperandFromTheTopOfTheStack() throws Exception {
    createOperandStackWithSize(1);
    Operand val = mockOperand();
    stack.push(val);
    Operand fromStack = stack.getFromTop(0);
    assertThat(fromStack, sameInstance(val));
  }

  @Test
  public void getFromTopWithXReturnsItemAtPlaceXFromTheTopOfTheStack() throws Exception {
    createOperandStackWithSize(3);
    Operand bottom = mockOperand();
    stack.push(bottom);
    Operand middle = mockOperand();
    stack.push(middle);
    Operand top = mockOperand();
    stack.push(top);
    Operand topFromStack = stack.getFromTop(0);
    assertThat(topFromStack, sameInstance(top));
    Operand midldeFromStack = stack.getFromTop(1);
    assertThat(midldeFromStack, sameInstance(middle));
    Operand bottomFromStack = stack.getFromTop(2);
    assertThat(bottomFromStack, sameInstance(bottom));
  }

  @Test
  public void getFromBottomOnEmptyStackReturnsNull() throws Exception {
    createOperandStackWithSize(1);
    Operand fromStack = stack.getFromBottom(0);
    assertNull(fromStack);
  }

  @Test(expected = ArrayIndexOutOfBoundsException.class)
  public void getFromBottomOnEmptyStackWithCapacityZeroCausesException() throws Exception {
    createOperandStackWithSize(0);
    stack.getFromBottom(0);
  }

  @Test(expected = ArrayIndexOutOfBoundsException.class)
  public void getFromBottomCausesExceptionsForNegativeArguments() throws Exception {
    createOperandStackWithSize(1);
    stack.getFromBottom(-1);
  }

  @Test
  public void getFromBottomWith0ReturnsTheOperandFromTheBottomOfTheStack() throws Exception {
    createOperandStackWithSize(1);
    Operand val = mockOperand();
    stack.push(val);
    Operand fromStack = stack.getFromBottom(0);
    assertThat(fromStack, sameInstance(val));
  }

  @Test
  public void getFromBottomWithXReturnsItemAtPlaceXFromTheBottomOfTheStack() throws Exception {
    createOperandStackWithSize(3);
    Operand bottom = mockOperand();
    stack.push(bottom);
    Operand middle = mockOperand();
    stack.push(middle);
    Operand top = mockOperand();
    stack.push(top);
    Operand bottomFromStack = stack.getFromBottom(0);
    assertThat(bottomFromStack, sameInstance(bottom));
    Operand midldeFromStack = stack.getFromBottom(1);
    assertThat(midldeFromStack, sameInstance(middle));
    Operand topFromStack = stack.getFromBottom(2);
    assertThat(topFromStack, sameInstance(top));
  }

  @Test
  public void negativeArgumentsForReplaceFromTopNeedNotCauseException() throws Exception {
    createOperandStackWithSize(1);
    stack.replaceFromTop(-1, mockOperand());
  }

  @Test(expected = ArrayIndexOutOfBoundsException.class)
  public void largeNegativeArgumentsForReplaceFromTopCauseException() throws Exception {
    createOperandStackWithSize(1);
    stack.replaceFromTop(-2, mockOperand());
  }

  @Test
  public void replaceFromTopForTopmostOperandReplacesTheOperand() throws Exception {
    createOperandStackWithSize(1);
    Operand val = mockOperand();
    stack.push(val);
    Operand newOp = mockOperand();
    stack.replaceFromTop(0, newOp);
    Operand replacedOp = stack.getFromTop(0);
    assertThat(replacedOp, sameInstance(newOp));
  }

  @Test
  public void replaceFromTopStackWithXReturnsItemAtPlaceXFromTheTopOfTheStack() throws Exception {
    createOperandStackWithSize(3);
    Operand bottom = mockOperand();
    stack.push(bottom);
    Operand middle = mockOperand();
    stack.push(middle);
    Operand top = mockOperand();
    stack.push(top);
    Operand newMiddleForStack = mockOperand();
    stack.replaceFromTop(1, newMiddleForStack);
    Operand midldeFromStack = stack.getFromTop(1);
    assertThat(midldeFromStack, sameInstance(newMiddleForStack));
    Operand newBottomForStack = mockOperand();
    stack.replaceFromTop(2, newBottomForStack);
    Operand bottomFromStack = stack.getFromTop(2);
    assertThat(bottomFromStack, sameInstance(newBottomForStack));
  }

  @Test
  public void swapSwapsTwoOperandsWhenTwoOperandsAreOnStack() throws Exception {
    createOperandStackWithSize(2);
    Operand oldBottom = mockOperand();
    stack.push(oldBottom);
    Operand oldTop = mockOperand();
    stack.push(oldTop);
    stack.swap();
    assertThat(stack.getFromTop(0), is(oldBottom));
    assertThat(stack.getFromTop(1), is(oldTop));
  }

  @Test
  public void swapSwapsTheTwoTopmostOperandsWhenEnoughOperandsAreOnStack() throws Exception {
    createOperandStackWithSize(3);
    Operand oldBottom = mockOperand();
    stack.push(oldBottom);
    Operand oldMiddle = mockOperand();
    stack.push(oldMiddle);
    Operand oldTop = mockOperand();
    stack.push(oldTop);
    stack.swap();
    assertThat(stack.getFromTop(0), is(oldMiddle));
    assertThat(stack.getFromTop(1), is(oldTop));
  }

  @Test(expected = ArrayIndexOutOfBoundsException.class)
  public void swapFailsIfStackContainsNoOperand() throws Exception {
    createOperandStackWithSize(1);
    stack.swap();
  }

  @Test(expected = ArrayIndexOutOfBoundsException.class)
  public void swapFailsIfStackContainsOnlyOneOperand() throws Exception {
    createOperandStackWithSize(1);
    stack.push(mockOperand());
    stack.swap();
  }

  private void createNonEmptyStack() {
    createOperandStackWithSize(2);
    stack.push(mockOperand());
    stack.push(mockOperand());
  }

  @Test
  public void clearMakesStackSeemEmpty() throws Exception {
    createNonEmptyStack();
    stack.clear();
    assertThat(stack.isEmpty(), is(true));
  }

  @Test
  public void clearMakesStackSizeZero() throws Exception {
    createNonEmptyStack();
    stack.clear();
    assertThat(stack.getSize(), is(0));
  }


  @Test(expected = ArrayIndexOutOfBoundsException.class)
  public void clearMakesStackSeemEmptyForAlmostAllOperations() throws Exception {
    createNonEmptyStack();
    stack.clear();
    stack.pop();
  }

  @Test
  public void stackDoesNotAppearAsEmptyForGetFromBottom() throws Exception {
    createNonEmptyStack();
    stack.clear();
    stack.getFromBottom(1);
  }

  @Test
  public void deepCopyCopiesAllOperandsInAStack() throws Exception {
    createOperandStackWithSize(3);
    IntConstantOperand bottom = new IntConstantOperand(-2);
    stack.push(bottom);
    TrueGuardOperand top = new TrueGuardOperand();
    stack.push(top);
    OperandStack copy = stack.deepCopy();
    assertThat(copy.getSize(), is(stack.getSize()));
    TrueGuardOperand topFromCopiedStack = (TrueGuardOperand) copy.getFromTop(0);
    assertThat(topFromCopiedStack, not(sameInstance(top)));
    assertThat(copy.getFromTop(0).similar(top), is(true));
    IntConstantOperand bottomFromCopiedStack = (IntConstantOperand) copy.getFromTop(1);
    assertThat(bottomFromCopiedStack, not(sameInstance(bottom)));
    assertThat(copy.getFromTop(1).similar(bottom), is(true));
  }

  @Test
  public void createEmptyOperandStackWithSameCapacityReturnsAnEmptyOperandStack() throws Exception {
    createOperandStackWithSize(3);
    stack.push(mockOperand());
    OperandStack newStack = stack.createEmptyOperandStackWithSameCapacity();
    assertThat(newStack.isEmpty(), is(true));
  }

  @Test
  public void createEmptyOperandStackWithSameCapacityReturnsAStackWithTheSameCapacity() throws Exception {
    createOperandStackWithSize(3);
    stack.push(mockOperand());
    OperandStack newStack = stack.createEmptyOperandStackWithSameCapacity();
    newStack.push(mockOperand());
    newStack.push(mockOperand());
    newStack.push(mockOperand());
    assertThat(newStack.getSize(), is(3));
    boolean stackHasRightCapacity = false;
    try {
      newStack.push(mockOperand());
    } catch (ArrayIndexOutOfBoundsException e) {
      stackHasRightCapacity = true;
    }
    assertThat(stackHasRightCapacity, is(true));
  }

}
