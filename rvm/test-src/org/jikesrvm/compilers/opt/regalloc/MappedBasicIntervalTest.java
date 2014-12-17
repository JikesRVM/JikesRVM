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
import static org.junit.Assert.*;

import org.jikesrvm.junit.runners.VMRequirements;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VMRequirements.class)
public class MappedBasicIntervalTest {

  @Test
  public void mappedBasicIntervalConstructorReadsBeginAndEndOfBasicInterval() {
    BasicInterval bi = new BasicInterval(3, 7);
    MappedBasicInterval mbi = new MappedBasicInterval(bi, null);
    assertThat(mbi.getBegin(), is(bi.getBegin()));
    assertThat(mbi.getEnd(), is(bi.getEnd()));
  }

  @Test
  public void mappedBasicIntervalsAllowNullArguments() {
    MappedBasicInterval mbi = new MappedBasicInterval(1, 2, null);
    assertThat(mbi.container, nullValue());
    BasicInterval bi = new BasicInterval(1, 2);
    MappedBasicInterval mbi2 = new MappedBasicInterval(bi, null);
    assertThat(mbi2.container, nullValue());
  }

  @Test
  public void equalObjectsHaveTheSameHashcode() {
    BasicInterval bi = new BasicInterval(1, 2);
    BasicInterval bi2 = new BasicInterval(1, 2);
    CompoundInterval c = new CompoundInterval(1, 2, null);
    MappedBasicInterval mbi = new MappedBasicInterval(bi, c);
    MappedBasicInterval mbi2 = new MappedBasicInterval(bi2, c);
    assertThat(mbi, equalTo(mbi2));
    assertThat(mbi.hashCode() == mbi2.hashCode(), is(true));
  }

  @Test
  public void mappedBasicIntervalsAndBasicIntervalsAreNotConsideredEqual() {
    BasicInterval bi = new BasicInterval(1, 2);
    MappedBasicInterval mbi = new MappedBasicInterval(1, 2, null);
    boolean biIsEqualToMbi = bi.equals(mbi);
    boolean mbiIsEqualToBi = mbi.equals(bi);
    assertThat(biIsEqualToMbi, is(false));
    assertThat(mbiIsEqualToBi, is(false));
  }

}
