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
import static org.jikesrvm.compilers.opt.ir.Operators.FENCE;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;

import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.compilers.opt.OptOptions;
import org.jikesrvm.compilers.opt.OptimizingCompilerException;
import org.jikesrvm.compilers.opt.ir.Empty;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.MIRInfo;
import org.jikesrvm.compilers.opt.ir.Register;
import org.jikesrvm.junit.runners.RequiresBuiltJikesRVM;
import org.jikesrvm.junit.runners.RequiresOptCompiler;
import org.jikesrvm.junit.runners.VMRequirements;
import org.jikesrvm.tests.util.MethodsForTests;
import org.jikesrvm.tests.util.TestingTools;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

@RunWith(VMRequirements.class)
@Category({RequiresBuiltJikesRVM.class, RequiresOptCompiler.class})
public class ActiveSetTest {

  //TODO tests for the most important methods are still missing:
  // the findAvailableRegister(*) methods and the allocate method currently
  // do not have any useful tests.

  private static final int SPILL_LOCATION = -23;

  private static final int UNUSED_REG_NUMBER = -2;
  private static final int FIRST_REG_NUMBER = 100;
  private static final int SECOND_REG_NUMBER = 101;

  private static final int MAXIMUM_REGISTER_NUMBER = 102;

  private SpillLocationManager spillLocations;

  private ActiveSet createActiveSet() throws Exception {
    return createActiveSetFromRegisterAllocatorState(null);
  }

  private ActiveSet createActiveSetFromRegisterAllocatorState(
      RegisterAllocatorState state) throws Exception {
    NormalMethod nm = TestingTools.getNormalMethod(MethodsForTests.class, "emptyStaticMethodWithoutAnnotations");
    OptOptions opts = new OptOptions();
    IR ir = new IR(nm, null, opts);
    ir.MIRInfo = new MIRInfo(ir);
    ir.MIRInfo.regAllocState = state;
    SpillLocationManager spillLocManager = new SpillLocationManager(ir);
    this.spillLocations = spillLocManager;
    return new ActiveSet(ir, spillLocManager, null);
  }

  @Test
  public void activeSetStartsWithNothingSpilled() throws Exception {
    ActiveSet active = createActiveSet();
    assertThat(active.spilledSomething(), is(false));
  }

  @Test
  public void freeIntervalDeallocatesPhysicalRegisters() throws Exception {
    ActiveSet active = createActiveSet();
    Register reg = new Register(FIRST_REG_NUMBER);
    Register target = new Register(SECOND_REG_NUMBER);
    reg.setPhysical();
    reg.allocateRegister(target);
    CompoundInterval ci = new CompoundInterval(reg);
    MappedBasicInterval mapped = new MappedBasicInterval(new BasicInterval(1, 2), ci);
    active.freeInterval(mapped);
    assertThat(reg.isAllocated(), is(false));
    assertNull(reg.mapsToRegister);
  }

  @Test
  public void freeIntervalMakesSpillIntervalsAvailableAgainWhenPossible() throws Exception {
    Register reg = new Register(FIRST_REG_NUMBER);
    reg.setInteger();
    RegisterAllocatorState state = new RegisterAllocatorState(MAXIMUM_REGISTER_NUMBER);
    state.setSpill(reg, SPILL_LOCATION);
    ActiveSet active = createActiveSetFromRegisterAllocatorState(state);

    CompoundInterval ci = new CompoundInterval(reg);
    BasicInterval first = new BasicInterval(1, 2);
    MappedBasicInterval firstMapped = new MappedBasicInterval(first, ci);
    active.add(firstMapped);
    ci.add(firstMapped);
    ci.spill(spillLocations, state);
    SpillLocationInterval toBeFreed = ci.getSpillInterval();
    active.freeInterval(firstMapped);
    assertThat(spillLocations.freeIntervals.contains(toBeFreed), is(true));
    assertThat(spillLocations.freeIntervals.size(), is(1));
  }

  @Test
  public void freeIntervalHasNoVisibleInfluenceOnFreeIntervalsWhenThereAreStillIntervalsLeftInTheCompoundInterval() throws Exception {
    Register reg = new Register(FIRST_REG_NUMBER);
    reg.setInteger();
    RegisterAllocatorState state = new RegisterAllocatorState(MAXIMUM_REGISTER_NUMBER);
    state.setSpill(reg, SPILL_LOCATION);
    ActiveSet active = createActiveSetFromRegisterAllocatorState(state);

    CompoundInterval ci = new CompoundInterval(reg);
    BasicInterval first = new BasicInterval(1, 2);
    MappedBasicInterval firstMapped = new MappedBasicInterval(first, ci);
    active.add(firstMapped);
    ci.add(firstMapped);
    BasicInterval second = new BasicInterval(2, 3);
    MappedBasicInterval secondMapped = new MappedBasicInterval(second, ci);
    ci.add(secondMapped);
    ci.spill(spillLocations, state);
    assertThat(spillLocations.freeIntervals.size(), is(0));
    active.freeInterval(firstMapped);
    assertThat(spillLocations.freeIntervals.size(), is(0));
  }

  @Test
  public void freeIntervalWorksForIntervalsAssignedToUnspilledNonPhysicalRegisters() throws Exception {
    Register reg = new Register(FIRST_REG_NUMBER);
    Register mappedReg = new Register(SECOND_REG_NUMBER);
    reg.allocateRegister(mappedReg);
    mappedReg.setPhysical();
    mappedReg.allocateRegister();
    RegisterAllocatorState state = new RegisterAllocatorState(MAXIMUM_REGISTER_NUMBER);
    ActiveSet active = createActiveSetFromRegisterAllocatorState(state);

    CompoundInterval ci = new CompoundInterval(reg);
    BasicInterval first = new BasicInterval(1, 2);
    MappedBasicInterval firstMapped = new MappedBasicInterval(first, ci);
    active.add(firstMapped);
    ci.add(firstMapped);
    BasicInterval second = new BasicInterval(2, 3);
    MappedBasicInterval secondMapped = new MappedBasicInterval(second, ci);
    ci.add(secondMapped);
    active.freeInterval(firstMapped);
    assertThat(mappedReg.isAllocated(), is(false));
    assertNull(mappedReg.mapsToRegister);
  }

  @Test
  public void currentlyActiveReturnsTrueForActiveRegisters() throws Exception {
    Register mapSource = new Register(FIRST_REG_NUMBER);
    Register mapTarget = new Register(SECOND_REG_NUMBER);
    BasicInterval bi = new BasicInterval(1, 2);
    CompoundInterval ci = new CompoundInterval(mapSource);
    MappedBasicInterval mbi = new MappedBasicInterval(bi, ci);
    RegisterAllocatorState state = new RegisterAllocatorState(30);
    state.mapOneToOne(mapSource, mapTarget);
    ActiveSet active = createActiveSetFromRegisterAllocatorState(state);
    active.add(mbi);
    assertThat(active.currentlyActive(mapTarget), is(true));
  }

  @Test
  public void currentlyActiveReturnsFalseForEmptySets() throws Exception {
    Register unused = new Register(FIRST_REG_NUMBER);
    ActiveSet active = createActiveSet();
    assertThat(active.currentlyActive(unused), is(false));
  }

  @Test
  public void currentlyActiveReturnsFalseForNonactiveRegisters() throws Exception {
    Register unused = new Register(UNUSED_REG_NUMBER);
    Register mapSource = new Register(FIRST_REG_NUMBER);
    Register mapTarget = new Register(SECOND_REG_NUMBER);
    BasicInterval bi = new BasicInterval(1, 2);
    CompoundInterval ci = new CompoundInterval(mapSource);
    MappedBasicInterval mbi = new MappedBasicInterval(bi, ci);
    RegisterAllocatorState state = new RegisterAllocatorState(30);
    state.mapOneToOne(mapSource, mapTarget);
    ActiveSet active = createActiveSetFromRegisterAllocatorState(state);
    active.add(mbi);
    assertThat(active.currentlyActive(unused), is(false));
  }

  @Test(expected = OptimizingCompilerException.class)
  public void getCurrentIntervalIsAssumedToAlwaysReturnAValidValue() throws Exception {
    Register unused = new Register(UNUSED_REG_NUMBER);
    ActiveSet active = createActiveSet();
    active.getCurrentInterval(unused);
  }

  @Test
  public void getCurrentIntervalReturnsTheCorrectIntervalForActiveRegisters() throws Exception {
    Register mapSource = new Register(FIRST_REG_NUMBER);
    Register mapTarget = new Register(SECOND_REG_NUMBER);
    BasicInterval bi = new BasicInterval(1, 2);
    CompoundInterval ci = new CompoundInterval(mapSource);
    MappedBasicInterval mbi = new MappedBasicInterval(bi, ci);
    RegisterAllocatorState state = new RegisterAllocatorState(30);
    state.mapOneToOne(mapSource, mapTarget);
    ActiveSet active = createActiveSetFromRegisterAllocatorState(state);
    active.add(mbi);
    assertThat(active.getCurrentInterval(mapTarget), is(ci));
  }

  @Test
  public void findAvailableRegisterIgnoresInfrequentIntervalsWhenDesired() throws Exception {
    NormalMethod nm = TestingTools.getNormalMethod(MethodsForTests.class, "emptyStaticMethodWithoutAnnotations");
    OptOptions opts = new OptOptions();
    opts.FREQ_FOCUS_EFFORT = true;
    IR ir = new IR(nm, null, opts);
    ir.MIRInfo = new MIRInfo(ir);
    SpillLocationManager spillLocManager = new SpillLocationManager(ir);
    this.spillLocations = spillLocManager;
    ActiveSet active = new ActiveSet(ir, spillLocManager, null);

    CompoundInterval infrequentCode = new CompoundInterval(null);
    assertNull(active.findAvailableRegister(infrequentCode));
  }

  @Test
  public void getBasicIntervalReturnsNullIfNoBasicIntervalForTheRegisterContainsTheInstruction() throws Exception {
    RegisterAllocatorState state = new RegisterAllocatorState(1);
    ActiveSet active = createActiveSetFromRegisterAllocatorState(state);
    Instruction fence = Empty.create(FENCE);
    BasicInterval bi = active.getBasicInterval(new Register(0), fence);
    assertNull(bi);
  }

  @Test
  public void getBasicIntervalReturnsCorrectIntervalIfRegisterContainsTheInstruction() throws Exception {
    Register regZero = new Register(0);
    RegisterAllocatorState state = new RegisterAllocatorState(1);
    CompoundInterval ci = new CompoundInterval(regZero);
    MappedBasicInterval mbi = new MappedBasicInterval(1, 10, ci);
    ci.add(mbi);
    Instruction fence = Empty.create(FENCE);
    state.setInterval(regZero, ci);
    state.initializeDepthFirstNumbering(10);
    state.setDFN(fence, 5);
    ActiveSet active = createActiveSetFromRegisterAllocatorState(state);
    BasicInterval bi = active.getBasicInterval(regZero, fence);
    assertThat(bi.getBegin(), is(1));
    assertThat(bi.getEnd(), is(10));
  }

  @Test
  public void expireOldIntervalsRemovesIntervalsWhoseEndPrecedesTheNewStart() throws Exception {
    Register reg = new Register(FIRST_REG_NUMBER);
    reg.setInteger();
    RegisterAllocatorState state = new RegisterAllocatorState(MAXIMUM_REGISTER_NUMBER);
    state.setSpill(reg, SPILL_LOCATION);
    ActiveSet active = createActiveSetFromRegisterAllocatorState(state);

    CompoundInterval ci = new CompoundInterval(reg);
    BasicInterval first = new BasicInterval(1, 2);
    BasicInterval firstMapped = new MappedBasicInterval(first, ci);
    active.add(firstMapped);
    ci.add(firstMapped);
    BasicInterval second = new BasicInterval(7, 8);
    BasicInterval secondMapped = new MappedBasicInterval(second, ci);
    active.add(secondMapped);
    ci.add(secondMapped);
    ci.spill(spillLocations, state);
    assertThat(active.size(), is(2));
    active.expireOldIntervals(new MappedBasicInterval(new BasicInterval(4, 5), ci));
    assertThat(active.size(), is(1));
  }

  @Test
  public void expireOldIntervalsCallsFreeInterval() throws Exception {
    Register reg = new Register(FIRST_REG_NUMBER);
    reg.setInteger();
    RegisterAllocatorState state = new RegisterAllocatorState(MAXIMUM_REGISTER_NUMBER);
    state.setSpill(reg, SPILL_LOCATION);
    ActiveSet active = createActiveSetFromRegisterAllocatorState(state);

    CompoundInterval ci = new CompoundInterval(reg);
    BasicInterval first = new BasicInterval(1, 2);
    BasicInterval firstMapped = new MappedBasicInterval(first, ci);
    active.add(firstMapped);
    ci.add(firstMapped);
    ci.spill(spillLocations, state);
    assertThat(active.size(), is(1));
    active.expireOldIntervals(new MappedBasicInterval(new BasicInterval(4, 5), ci));
    assertThat(active.size(), is(0));
    assertThat(spillLocations.freeIntervals.size(), is(1));
  }

}
