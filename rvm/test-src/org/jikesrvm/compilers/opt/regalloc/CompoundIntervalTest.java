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

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.jikesrvm.compilers.opt.ir.Operators.*;

import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.compilers.opt.ir.ControlFlowGraph;
import org.jikesrvm.compilers.opt.ir.Empty;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.Register;
import org.jikesrvm.junit.runners.RequiresOptCompiler;
import org.jikesrvm.junit.runners.VMRequirements;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

@RunWith(VMRequirements.class)
@Category({RequiresOptCompiler.class})
public class CompoundIntervalTest {

  private static final int DEFAULT_BEGIN = 1;
  private static final int DEFAULT_END = 2;

  private static final int SPILL_INTERVAL_OFFSET = -23;
  private static final int SPILL_INTERVAL_SIZE = 17;

  @Test
  public void compoundIntervalConstructorDoesNotCheckForNull() {
    createCompoundIntervalWithoutRegister();
  }

  @Test
  public void compoundIntervalsAreInfrequentByDefault() {
    CompoundInterval ci = createCompoundIntervalWithoutRegister();
    assertThat(ci.isInfrequent(), is(true));
  }

  @Test
  public void compoundIntervalsCanBeMadeFrequent() {
    CompoundInterval ci = createCompoundIntervalWithoutRegister();
    ci.setFrequent();
    assertThat(ci.isInfrequent(), is(false));
  }

  @Test
  public void compoundIntervalsDoNotHaveASpillIntervalByDefault() {
    CompoundInterval ci = createCompoundIntervalWithoutRegister();
    assertNull(ci.getSpillInterval());
  }

  @Test
  public void spillingOfCompoundIntervalSetsTheSpillInterval() {
    Register r = new Register(0);
    CompoundInterval ci = new CompoundInterval(DEFAULT_BEGIN, DEFAULT_END, r);
    RegisterAllocatorState regAllocState = new RegisterAllocatorState(1);
    assertThat(ci.isSpilled(regAllocState), is(false));
    ci.spill(new MockSpillLocationManager(), regAllocState);
    assertThat(ci.getSpillInterval().getOffset(), is(SPILL_INTERVAL_OFFSET));
    assertThat(ci.getSpillInterval().getSize(),is(SPILL_INTERVAL_SIZE));
    assertThat(ci.isSpilled(regAllocState), is(true));
  }

  @Test
  public void assignCausesAssignmentOfTheIntervalsRegisterToTheGivenRegister() {
    Register r = new Register(0);
    CompoundInterval ci = new CompoundInterval(DEFAULT_BEGIN, DEFAULT_END, r);
    Register s = new Register(1);
    RegisterAllocatorState regAllocState = new RegisterAllocatorState(0);
    assertThat(ci.isAssigned(regAllocState), is(false));
    assertNull(ci.getAssignment(regAllocState));
    ci.assign(s);
    assertThat(s.mapsToRegister, is(r));
    assertThat(!s.isSpilled() && s.isTouched() && s.isAllocated(), is(true));
    assertThat(r.mapsToRegister, is(s));
    assertThat(!r.isSpilled() && r.isTouched() && r.isAllocated(), is(true));
    assertThat(ci.isAssigned(regAllocState), is(true));
    assertThat(ci.getAssignment(regAllocState), is(s));
  }

  @Test
  public void getBasicIntervalReturnsNullIfNoMatchingIntervalIsFound() {
    CompoundInterval ci = createCompoundIntervalWithoutRegister();
    assertNull(ci.getBasicInterval(10));
  }

  @Test
  public void getBasicIntervalReturnsAnIntervalIfOneMatches() {
    CompoundInterval ci = createCompoundIntervalWithoutRegister();
    BasicInterval bi = ci.getBasicInterval(DEFAULT_END);
    assertThat(bi.getBegin(), is(DEFAULT_BEGIN));
    assertThat(bi.getEnd(), is(DEFAULT_END));
  }

  @Test
  public void getBasicIntervalReturnsIntervalWithGreatestStartIfMultipleMatch() {
    CompoundInterval ci = new CompoundInterval(DEFAULT_BEGIN, DEFAULT_END, null);
    ci.add(new BasicInterval(DEFAULT_BEGIN, DEFAULT_END + 1));
    BasicInterval bi = ci.getBasicInterval(DEFAULT_END);
    assertThat(bi.getBegin(), is(DEFAULT_BEGIN));
    assertThat(bi.getEnd(), is(DEFAULT_END + 1));
  }

  @Test
  public void getBasicIntervalRegAllocStateReturnsNullIfNoMatchingIntervalIsFound() {
    CompoundInterval ci = createCompoundIntervalWithoutRegister();
    RegisterAllocatorState regAllocState = new RegisterAllocatorState(1);
    regAllocState.initializeDepthFirstNumbering(20);
    Instruction writeFloor = Empty.create(WRITE_FLOOR);
    regAllocState.setDFN(writeFloor, DEFAULT_BEGIN);
    ci.getBasicInterval(regAllocState, writeFloor);
    assertNull(ci.getBasicInterval(10));
  }

  @Test
  public void getBasicIntervalRegAllocStateReturnsAnIntervalIfOneMatches() {
    CompoundInterval ci = createCompoundIntervalWithoutRegister();
    RegisterAllocatorState regAllocState = new RegisterAllocatorState(1);
    regAllocState.initializeDepthFirstNumbering(20);
    Instruction writeFloor = Empty.create(WRITE_FLOOR);
    regAllocState.setDFN(writeFloor, DEFAULT_END);
    BasicInterval bi = ci.getBasicInterval(regAllocState, writeFloor);
    assertThat(bi.getBegin(), is(DEFAULT_BEGIN));
    assertThat(bi.getEnd(), is(DEFAULT_END));
  }

  @Test
  public void getBasicIntervalRegAllocStateReturnsIntervalWithGreatestStartIfMultipleMatch() {
    CompoundInterval ci = new CompoundInterval(DEFAULT_BEGIN, DEFAULT_END, null);
    ci.add(new BasicInterval(DEFAULT_BEGIN, DEFAULT_END + 1));
    RegisterAllocatorState regAllocState = new RegisterAllocatorState(1);
    regAllocState.initializeDepthFirstNumbering(20);
    Instruction writeFloor = Empty.create(WRITE_FLOOR);
    regAllocState.setDFN(writeFloor, DEFAULT_END);
    BasicInterval bi = ci.getBasicInterval(regAllocState, writeFloor);
    assertThat(bi.getBegin(), is(DEFAULT_BEGIN));
    assertThat(bi.getEnd(), is(DEFAULT_END + 1));
  }

  @Test
  public void addNonIntersectingSetAddsAllIntervalsInTheOtherCompoundInterval() {
    CompoundInterval ci = createCompoundIntervalWithoutRegister();
    CompoundInterval toMerge = new CompoundInterval(DEFAULT_END + 1, DEFAULT_END + 2, null);
    BasicInterval otherInterval = new BasicInterval(DEFAULT_END + 1, DEFAULT_END + 3);
    toMerge.add(otherInterval);
    BasicInterval otherInterval2 = new BasicInterval(DEFAULT_END + 2, DEFAULT_END + 4);
    toMerge.add(otherInterval2);
    BasicInterval stop = new BasicInterval(DEFAULT_END + 5, DEFAULT_END + 6);
    ci.addNonIntersectingInterval(toMerge, stop);
    assertThat(ci.size(), is(4));
    assertThat(ci.getLowerBound(), is(DEFAULT_BEGIN));
    assertThat(ci.getUpperBound(), is(DEFAULT_END + 4));
  }

  @Test
  public void addNonIntersectingSetStopsAtTheStopInterval() {
    CompoundInterval ci = createCompoundIntervalWithoutRegister();
    CompoundInterval toMerge = new CompoundInterval(DEFAULT_END + 1, DEFAULT_END + 2, null);
    BasicInterval otherInterval = new BasicInterval(DEFAULT_END + 1, DEFAULT_END + 3);
    toMerge.add(otherInterval);
    BasicInterval otherInterval2 = new BasicInterval(DEFAULT_END + 2, DEFAULT_END + 4);
    toMerge.add(otherInterval2);
    BasicInterval stop = new BasicInterval(DEFAULT_END + 1, DEFAULT_END + 3);
    ci.addNonIntersectingInterval(toMerge, stop);
    assertThat(ci.size(), is(3));
    assertThat(ci.getLowerBound(), is(DEFAULT_BEGIN));
    assertThat(ci.getUpperBound(), is(DEFAULT_END + 3));
  }

  @Test
  public void addNonIntersectingStopIntervalNeedNotBeInIntervalThatsMerged() {
    CompoundInterval ci = createCompoundIntervalWithoutRegister();
    CompoundInterval toMerge = new CompoundInterval(DEFAULT_END + 1, DEFAULT_END + 2, null);
    BasicInterval otherInterval = new BasicInterval(DEFAULT_END + 1, DEFAULT_END + 3);
    toMerge.add(otherInterval);
    BasicInterval otherInterval2 = new BasicInterval(DEFAULT_END + 2, DEFAULT_END + 4);
    toMerge.add(otherInterval2);
    BasicInterval stop = new BasicInterval(DEFAULT_END + 1, DEFAULT_END + 4);
    ci.addNonIntersectingInterval(toMerge, stop);
    assertThat(ci.size(), is(3));
    assertThat(ci.getLowerBound(), is(DEFAULT_BEGIN));
    assertThat(ci.getUpperBound(), is(DEFAULT_END + 3));
  }

  @Test
  public void copyCopiesAllIntervalsToNewCompoundIntervalWithRightRegister() {
    BasicInterval bi = new BasicInterval(DEFAULT_BEGIN, DEFAULT_END);
    CompoundInterval toCopy = new CompoundInterval(bi, null);
    BasicInterval bi2 = new BasicInterval(DEFAULT_END, DEFAULT_END + 1);
    BasicInterval bi3 = new BasicInterval(DEFAULT_END + 1, DEFAULT_END + 2);
    toCopy.add(bi2);
    toCopy.add(bi3);
    Register r = new Register(2);
    CompoundInterval copy = toCopy.copy(r);
    assertThat(copy.getRegister(), is(r));
    assertThat(copy.contains(bi), is(true));
    assertThat(copy.contains(bi2), is(true));
    assertThat(copy.contains(bi3), is(true));
    assertThat(copy.size(), is(3));
  }

  @Test
  public void copyStopsAtTheRightPointWhenRequired() {
    BasicInterval bi = new BasicInterval(DEFAULT_BEGIN, DEFAULT_END);
    CompoundInterval toCopy = new CompoundInterval(bi, null);
    BasicInterval bi2 = new BasicInterval(DEFAULT_END, DEFAULT_END + 1);
    BasicInterval bi3 = new BasicInterval(DEFAULT_END + 1, DEFAULT_END + 2);
    toCopy.add(bi2);
    toCopy.add(bi3);
    Register r = new Register(2);
    CompoundInterval copy = toCopy.copy(r, bi2);
    assertThat(copy.getRegister(), is(r));
    assertThat(copy.contains(bi), is(true));
    assertThat(copy.contains(bi2), is(true));
    assertThat(copy.contains(bi3), is(false));
    assertThat(copy.size(), is(2));
  }

  @Test
  public void copyMethodsReallyReturnCopies() {
    BasicInterval bi = new BasicInterval(DEFAULT_BEGIN, DEFAULT_END);
    CompoundInterval toCopy = new CompoundInterval(bi, null);
    BasicInterval bi2 = new BasicInterval(DEFAULT_END, DEFAULT_END + 1);
    toCopy.add(bi2);
    CompoundInterval aCopy = toCopy.copy(new Register(1));
    assertThat(aCopy, not(sameInstance(toCopy)));
    CompoundInterval anotherCopy = toCopy.copy(new Register(3), bi);
    assertThat(anotherCopy, not(sameInstance(toCopy)));
  }

  @Test
  public void addRangeChangesEndOfLastIntervalWhenRangesDirectlyFollowEachOther_NoDefUse() {
    Register reg = new Register(3);
    CompoundInterval ci = new CompoundInterval(DEFAULT_BEGIN, DEFAULT_END, reg);
    assertThat(ci.last().getEnd(), is(DEFAULT_END));
    RegisterAllocatorState regAllocState = new RegisterAllocatorState(1);
    LiveIntervalElement live = new LiveIntervalElement(reg, null, null);
    ControlFlowGraph emptyCfg = new ControlFlowGraph(0);
    BasicBlock bb = new BasicBlock(1, null, emptyCfg);
    regAllocState.initializeDepthFirstNumbering(10);
    regAllocState.setDFN(bb.firstInstruction(), DEFAULT_END);
    regAllocState.setDFN(bb.lastInstruction(), DEFAULT_END + 1);
    BasicInterval bi = ci.addRange(regAllocState, live, bb);
    assertNull(bi);
    assertThat(ci.last().getEnd(), is(DEFAULT_END + 1));
  }

  @Test
  public void addRangeChangesEndOfLastIntervalWhenRangesDirectlyFollowEachOther_DefUse() {
    Register reg = new Register(3);
    CompoundInterval ci = new CompoundInterval(DEFAULT_BEGIN, DEFAULT_END, reg);
    assertThat(ci.last().getEnd(), is(DEFAULT_END));
    RegisterAllocatorState regAllocState = new RegisterAllocatorState(1);
    Instruction def = Empty.create(WRITE_FLOOR);
    Instruction lastUse = Empty.create(WRITE_FLOOR);
    LiveIntervalElement live = new LiveIntervalElement(reg, def, lastUse);
    ControlFlowGraph emptyCfg = new ControlFlowGraph(0);
    BasicBlock bb = new BasicBlock(1, null, emptyCfg);
    bb.appendInstruction(def);
    bb.appendInstruction(lastUse);
    regAllocState.initializeDepthFirstNumbering(10);
    regAllocState.setDFN(def, DEFAULT_END);
    regAllocState.setDFN(lastUse, DEFAULT_END + 1);
    BasicInterval bi = ci.addRange(regAllocState, live, bb);
    assertNull(bi);
    assertThat(ci.last().getEnd(), is(DEFAULT_END + 1));
  }

  @Test
  public void addRangeChangesEndOfLastIntervalWhenRangesDirectlyFollowEachOther_DefUseSameInstruction() {
    Register reg = new Register(3);
    CompoundInterval ci = new CompoundInterval(DEFAULT_BEGIN, DEFAULT_END, reg);
    assertThat(ci.last().getEnd(), is(DEFAULT_END));
    RegisterAllocatorState regAllocState = new RegisterAllocatorState(1);
    Instruction empty = Empty.create(WRITE_FLOOR);
    Instruction defUse = Empty.create(WRITE_FLOOR);
    Instruction lastUse = Empty.create(WRITE_FLOOR);
    LiveIntervalElement live = new LiveIntervalElement(reg, defUse, lastUse);
    ControlFlowGraph emptyCfg = new ControlFlowGraph(0);
    BasicBlock bb = new BasicBlock(1, null, emptyCfg);
    bb.appendInstruction(empty);
    bb.appendInstruction(defUse);
    bb.appendInstruction(lastUse);
    regAllocState.initializeDepthFirstNumbering(10);
    regAllocState.setDFN(empty, DEFAULT_BEGIN);
    regAllocState.setDFN(defUse, DEFAULT_END);
    regAllocState.setDFN(lastUse, DEFAULT_END + 1);
    BasicInterval bi = ci.addRange(regAllocState, live, bb);
    assertNull(bi);
    assertThat(ci.last().getEnd(), is(DEFAULT_END + 1));
  }


  @Test
  public void addRangeCreatesNewIntervalWhenThereIsAGapBetweenRanges() {
    Register reg = new Register(3);
    CompoundInterval ci = new CompoundInterval(DEFAULT_BEGIN, DEFAULT_END, reg);
    assertThat(ci.last().getEnd(), is(DEFAULT_END));
    RegisterAllocatorState regAllocState = new RegisterAllocatorState(1);
    LiveIntervalElement live = new LiveIntervalElement(reg, null, null);
    ControlFlowGraph emptyCfg = new ControlFlowGraph(0);
    BasicBlock bb = new BasicBlock(1, null, emptyCfg);
    regAllocState.initializeDepthFirstNumbering(10);
    regAllocState.setDFN(bb.firstInstruction(), DEFAULT_END + 2);
    regAllocState.setDFN(bb.lastInstruction(), DEFAULT_END + 3);
    BasicInterval bi = ci.addRange(regAllocState, live, bb);
    assertThat(ci.last(), sameInstance(bi));
  }

  @Test
  public void headSetInclusiveIncludesBorderInterval() {
    CompoundInterval ci = createCompoundIntervalWithoutRegister();
    ci.add(new MappedBasicInterval(DEFAULT_END + 1, DEFAULT_END + 2, null));
    ci.add(new MappedBasicInterval(DEFAULT_END + 2, DEFAULT_END + 3, null));
    BasicInterval upperBound = new BasicInterval(DEFAULT_END + 2, DEFAULT_END + 3);
    assertThat(ci.headSetInclusive(upperBound).contains(upperBound), is(true));
  }

  @Test
  public void headSetInclusiveIncludesIntervalWithUpperBound() {
    CompoundInterval ci = createCompoundIntervalWithoutRegister();
    ci.add(new MappedBasicInterval(DEFAULT_END + 1, DEFAULT_END + 2, null));
    ci.add(new MappedBasicInterval(DEFAULT_END + 2, DEFAULT_END + 3, null));
    BasicInterval upperBound = new BasicInterval(DEFAULT_END + 2, DEFAULT_END + 3);
    assertThat(ci.headSetInclusive(DEFAULT_END + 3).contains(upperBound), is(true));
  }

  @Test
  public void tailSetInclusiveIncludesIntervalWithLowerBound() {
    CompoundInterval ci = createCompoundIntervalWithoutRegister();
    ci.add(new MappedBasicInterval(DEFAULT_END + 1, DEFAULT_END + 2, null));
    ci.add(new MappedBasicInterval(DEFAULT_END + 2, DEFAULT_END + 3, null));
    BasicInterval lowerBound = new BasicInterval(DEFAULT_BEGIN, DEFAULT_END);
    assertThat(ci.tailSetInclusive(DEFAULT_BEGIN).contains(lowerBound), is(true));
  }

  @Test
  public void removeIntervalsAndCacheDoesNothingWhenOtherIntervalsIsEmpty() {
    CompoundInterval ci = createCompoundIntervalWithoutRegister();
    ci.add(new MappedBasicInterval(DEFAULT_END + 1, DEFAULT_END + 2, null));
    ci.add(new MappedBasicInterval(DEFAULT_END + 2, DEFAULT_END + 3, null));
    CompoundInterval cached = ci.removeIntervalsAndCache(new CompoundInterval(null));
    assertThat(ci.size(), is(3));
    assertThat(cached.isEmpty(), is(true));
  }

  @Test
  public void removeIntervalsAndCacheDoesNothingWhenIntervalEndsAfterThisOne() {
    CompoundInterval ci = createCompoundIntervalWithoutRegister();
    ci.add(new MappedBasicInterval(DEFAULT_END + 1, DEFAULT_END + 2, null));
    ci.add(new MappedBasicInterval(DEFAULT_END + 2, DEFAULT_END + 3, null));
    CompoundInterval cached = ci.removeIntervalsAndCache(new CompoundInterval(DEFAULT_END + 3, DEFAULT_END + 6, null));
    assertThat(ci.size(), is(3));
    assertThat(cached.isEmpty(), is(true));
  }

  @Test
  public void removeIntervalsAndCacheWorksWhenOtherIntervalIsASubset() {
    CompoundInterval ci = createCompoundIntervalWithoutRegister();
    ci.add(new MappedBasicInterval(DEFAULT_END + 1, DEFAULT_END + 2, null));
    ci.add(new MappedBasicInterval(DEFAULT_END + 2, DEFAULT_END + 3, null));
    ci.add(new MappedBasicInterval(DEFAULT_END + 1, DEFAULT_END + 4, null));
    ci.add(new MappedBasicInterval(DEFAULT_BEGIN, DEFAULT_END + 1, null));
    CompoundInterval toRemove = new CompoundInterval(DEFAULT_END + 1, DEFAULT_END + 2, null);
    toRemove.add(new MappedBasicInterval(DEFAULT_BEGIN, DEFAULT_END, null));
    CompoundInterval cached = ci.removeIntervalsAndCache(toRemove);
    assertThat(cached.size(), is(2));
    assertThat(cached.contains(new MappedBasicInterval(DEFAULT_BEGIN, DEFAULT_END, null)), is(true));
    assertThat(cached.contains(new MappedBasicInterval(DEFAULT_END + 1, DEFAULT_END + 2, null)), is(true));
    assertThat(ci.size(), is(3));
    assertThat(ci.contains(new MappedBasicInterval(DEFAULT_BEGIN, DEFAULT_END + 1, null)), is(true));
    assertThat(ci.contains(new MappedBasicInterval(DEFAULT_END + 1, DEFAULT_END + 4, null)), is(true));
    assertThat(ci.contains(new MappedBasicInterval(DEFAULT_END + 2, DEFAULT_END + 3, null)), is(true));
  }

  @Test
  public void emptyCompoundIntervalsDoNotIntersectWithAnything() {
    CompoundInterval empty = new CompoundInterval(null);
    assertThat(empty.intersects(new CompoundInterval(null)), is(false));
  }

  @Test
  public void nonEmptyCompoundIntervalsIntersectWithThemselves() {
    CompoundInterval ci = createCompoundIntervalWithoutRegister();
    assertThat(ci.intersects(ci), is(true));
  }

  @Test
  public void registersDoNotMatterForIntersection() {
    CompoundInterval ci = new CompoundInterval(DEFAULT_BEGIN, DEFAULT_END, new Register(-1));
    CompoundInterval otherCi = new CompoundInterval(DEFAULT_BEGIN, DEFAULT_END, new Register(-2));
    assertThat(ci.intersects(otherCi), is(true));
  }

  @Test
  public void compoundIntervalsDoNotIntersectWithEmptyIntervals() {
    CompoundInterval ci = createCompoundIntervalWithoutRegister();
    assertThat(ci.intersects(new CompoundInterval(null)), is(false));
  }

  @Test
  public void intervalsDoNotIntersectWhenFirstIntervalIsLowerThanTheOther() {
    CompoundInterval ci = createCompoundIntervalWithoutRegister();
    CompoundInterval other = new CompoundInterval(DEFAULT_END + 4, DEFAULT_END + 6, null);
    assertThat(ci.intersects(other), is(false));
  }

  @Test
  public void intervalsDoNotIntersectWhenFirstIntervalIsHigherThanTheOther() {
    CompoundInterval ci = new CompoundInterval(DEFAULT_END + 4, DEFAULT_END + 6, null);
    CompoundInterval other = createCompoundIntervalWithoutRegister();
    assertThat(ci.intersects(other), is(false));
  }

  @Test
  public void intervalsDoNotIntersectWhenContainedIntervalsDoNotIntersect() {
    CompoundInterval ci = new CompoundInterval(DEFAULT_BEGIN, DEFAULT_END, null);
    ci.add(new MappedBasicInterval(DEFAULT_END + 2, DEFAULT_END + 3, null));
    ci.add(new MappedBasicInterval(DEFAULT_END + 5, DEFAULT_END + 7, null));
    CompoundInterval other = new CompoundInterval(DEFAULT_END, DEFAULT_END + 1, null);
    other.add(new MappedBasicInterval(DEFAULT_END + 1, DEFAULT_END + 2, null));
    other.add(new MappedBasicInterval(DEFAULT_END + 3, DEFAULT_END + 4, null));
    assertThat(ci.intersects(other), is(false));
  }

  @Test
  public void intervalsDoNotIntersectWhenContainedIntervalsDoNotIntersectOtherWayRound() {
    CompoundInterval ci = new CompoundInterval(DEFAULT_END, DEFAULT_END + 1, null);
    ci.add(new MappedBasicInterval(DEFAULT_END + 1, DEFAULT_END + 2, null));
    ci.add(new MappedBasicInterval(DEFAULT_END + 3, DEFAULT_END + 4, null));
    CompoundInterval other = new CompoundInterval(1, DEFAULT_END, null);
    other.add(new MappedBasicInterval(DEFAULT_END + 2, DEFAULT_END + 3, null));
    other.add(new MappedBasicInterval(DEFAULT_END + 5, DEFAULT_END + 7, null));
    assertThat(ci.intersects(other), is(false));
  }

  private CompoundInterval createCompoundIntervalWithoutRegister() {
    return new CompoundInterval(DEFAULT_BEGIN, DEFAULT_END, null);
  }

  private static class MockSpillLocationManager extends SpillLocationManager {

    MockSpillLocationManager() {
      super(null);
    }

    @Override
    SpillLocationInterval findOrCreateSpillLocation(CompoundInterval ci) {
      return new SpillLocationInterval(SPILL_INTERVAL_OFFSET, SPILL_INTERVAL_SIZE, 0);
    }
  }

}
