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
package org.jikesrvm.compilers.opt.regalloc;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import org.jikesrvm.junit.runners.VMRequirements;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VMRequirements.class)
public class BasicIntervalTest {

  private static final int BEGIN = 4;
  private static final int END = 9;

  @Test
  public void endAndBeginAreNotModifiedByConstructor() {
    BasicInterval bi = new BasicInterval(BEGIN, END);
    assertThat(bi.getBegin(), is(BEGIN));
    assertThat(bi.getEnd(), is(END));
  }

  @Test
  public void constructorDoesNotCheckBeginAndEnd() {
    BasicInterval negativeNumbers = new BasicInterval(-2, -3);
    assertThat(negativeNumbers.getBegin(), is(-2));
    assertThat(negativeNumbers.getEnd(), is(-3));
    BasicInterval endLessThanBegin = new BasicInterval(3, 2);
    assertThat(endLessThanBegin.getBegin(), is(3));
    assertThat(endLessThanBegin.getEnd(), is(2));
  }

  @Test
  public void intervalsCanBeExtendedAtTheEnd() {
    BasicInterval bi = new BasicInterval(BEGIN, END);
    int newEnd = END + 2;
    bi.setEnd(newEnd);
    assertThat(bi.getEnd(), is(newEnd));
  }

  @Test
  public void intervalsCanBeShortenedAtTheEnd() {
    BasicInterval bi = new BasicInterval(BEGIN, END);
    int newEnd = END - 2;
    bi.setEnd(newEnd);
    assertThat(bi.getEnd(), is(newEnd));
  }

  @Test
  public void intervalEndMayBeBeforeStart() {
    BasicInterval bi = new BasicInterval(2, 10);
    int newEnd = 1;
    bi.setEnd(newEnd);
    assertThat(bi.getEnd(), is(newEnd));
  }

  @Test
  public void startsAfterExcludesBegin() {
    BasicInterval reference = new BasicInterval(BEGIN, END);
    assertThat(reference.startsAfter(BEGIN - 1), is(true));
    assertThat(reference.startsAfter(BEGIN), is(false));
  }

  @Test
  public void startsBeforeExcludesBegin() {
    BasicInterval reference = new BasicInterval(BEGIN, END);
    assertThat(reference.startsBefore(BEGIN), is(false));
    assertThat(reference.startsBefore(BEGIN + 1), is(true));
  }

  @Test
  public void endsAfterExcludesEnd() {
    BasicInterval reference = new BasicInterval(BEGIN, END);
    assertThat(reference.endsAfter(END - 1), is(true));
    assertThat(reference.endsAfter(END), is(false));
  }

  @Test
  public void endsBeforeExcludesEnd() {
    BasicInterval reference = new BasicInterval(BEGIN, END);
    assertThat(reference.endsBefore(END + 1), is(true));
    assertThat(reference.endsBefore(END), is(false));
  }

  @Test
  public void intervalsContainTheirOwnBegin() {
    BasicInterval bi = new BasicInterval(BEGIN, END);
    assertThat(bi.contains(bi.getBegin()), is(true));
  }

  @Test
  public void intervalsDoNotContainAnythingSmallerThanTheirBegin() {
    BasicInterval bi = new BasicInterval(BEGIN, END);
    assertThat(bi.contains(bi.getBegin() - 1), is(false));
  }

  @Test
  public void intervalsContainTheirOwnEnd() {
    BasicInterval bi = new BasicInterval(BEGIN, END);
    assertThat(bi.contains(bi.getEnd()), is(true));
  }

  @Test
  public void intervalsDoNotContainAnythingGreaterThanTheirEnd() {
    BasicInterval bi = new BasicInterval(BEGIN, END);
    assertThat(bi.contains(bi.getEnd() + 1), is(false));
  }

  @Test
  public void valuesBetweenBeginAndEndAreContainedInTheInterval() {
    BasicInterval bi = new BasicInterval(BEGIN, END);
    assertThat(bi.contains(BEGIN), is(true));
    assertThat(bi.contains(BEGIN + 1), is(true));
    assertThat(bi.contains(END - 1), is(true));
  }

  @Test
  public void startsBeforeIntervalExcludesBegin() {
    BasicInterval reference = new BasicInterval(BEGIN, END);
    BasicInterval startingAfter = new BasicInterval(BEGIN + 1, END);
    assertThat(reference.startsBefore(startingAfter), is(true));
    BasicInterval startingSame = new BasicInterval(BEGIN, END + 1);
    assertThat(reference.startsBefore(startingSame), is(false));
  }

  @Test
  public void endsAfterIntervalExcludesEnd() {
    BasicInterval reference = new BasicInterval(BEGIN, END);
    BasicInterval endingBefore = new BasicInterval(BEGIN + 1, END - 1);
    assertThat(reference.endsAfter(endingBefore), is(true));
    BasicInterval endingSame = new BasicInterval(BEGIN - 2, END);
    assertThat(reference.endsAfter(endingSame), is(false));
  }

  @Test
  public void equalIntervalsHaveTheSameRange() {
    BasicInterval first = new BasicInterval(BEGIN, END);
    BasicInterval second = new BasicInterval(BEGIN, END);
    assertThat(first.sameRange(second), is(true));
  }

  @Test
  public void sameRangeChecksBeginAndEnd() {
    BasicInterval reference = new BasicInterval(BEGIN, END);
    BasicInterval otherBegin = new BasicInterval(BEGIN - 1, END);
    assertThat(reference.sameRange(otherBegin), is(false));
    BasicInterval otherEnd = new BasicInterval(BEGIN, END + 1);
    assertThat(reference.sameRange(otherEnd), is(false));
  }

  @Test
  public void intervalsIntersectWithThemselves() {
    BasicInterval bi = new BasicInterval(BEGIN, END);
    assertThat(bi.intersects(bi), is(true));
  }

  @Test
  public void intersectionExcludesEndOfInterval() {
    BasicInterval reference = new BasicInterval(BEGIN, END);
    BasicInterval beginAtReferenceEnd = new BasicInterval(END, END + 2);
    assertThat(reference.intersects(beginAtReferenceEnd), is(false));
  }

  @Test
  public void intersectionIncludesEndOfIntervalMinusOne() {
    BasicInterval reference = new BasicInterval(BEGIN, END);
    BasicInterval beginAtReferenceEndMinusOne = new BasicInterval(END - 1, END + 2);
    assertThat(reference.intersects(beginAtReferenceEndMinusOne), is(true));
  }

  @Test
  public void intersectionExcludesBeginOfInterval() {
    BasicInterval reference = new BasicInterval(BEGIN, END);
    BasicInterval endAtReferenceEnd = new BasicInterval(BEGIN - 3, BEGIN);
    assertThat(reference.intersects(endAtReferenceEnd), is(false));
  }

  @Test
  public void intersectionIncludesBeginOfIntervalPlusOne() {
    BasicInterval reference = new BasicInterval(BEGIN, END);
    BasicInterval endAtReferenceEndPlusOne = new BasicInterval(1, BEGIN + 1);
    assertThat(reference.intersects(endAtReferenceEndPlusOne), is(true));
  }

  @Test
  public void basicIntervalsAreEqualToThemselves() {
    BasicInterval bi = new BasicInterval(BEGIN, END);
    assertThat(bi.equals(bi), is(true));
  }

  @Test
  public void intervalsWithSameBeginAndEndAreEqual() {
    BasicInterval bi = new BasicInterval(BEGIN, END);
    BasicInterval bi2 = new BasicInterval(BEGIN, END);
    assertThat(bi.equals(bi2), is(true));
  }

  @Test
  public void equalObjectsHaveTheSameHashcode() {
    BasicInterval bi = new BasicInterval(1, 2);
    BasicInterval bi2 = new BasicInterval(1, 2);
    assertThat(bi.hashCode() == bi2.hashCode(), is(true));
  }

}
