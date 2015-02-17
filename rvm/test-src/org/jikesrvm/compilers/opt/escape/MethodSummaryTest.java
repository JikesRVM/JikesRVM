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
package org.jikesrvm.compilers.opt.escape;

import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;

import org.jikesrvm.junit.runners.VMRequirements;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VMRequirements.class)
public class MethodSummaryTest {

  private MethodSummary summary;

  @Before
  public void createSummary() {
    summary = new MethodSummary(null);
  }

  @Test
  public void newlyCreatedSummariesAreNotInProgress() {
    assertThat(summary.inProgress(), is(false));
  }

  @Test
  public void everythingEscapesInNewlyCreatedMethodSummaries() {
    assertThat(summary.resultMayEscapeThread(), is(true));
    for (int i = 0; i < 62; i++) {
      assertThat(summary.parameterMayEscapeThread(i), is(true));
    }
  }

  @Test
  public void resultCanBeSetToEscapeOrNotToEscape() {
    summary.setResultMayEscapeThread(false);
    assertThat(summary.resultMayEscapeThread(), is(false));
    summary.setResultMayEscapeThread(true);
    assertThat(summary.resultMayEscapeThread(), is(true));
  }

  @Test
  public void methodParametersLessThanOrEqualTo62CanBeSetToNotEscape() {
    for (int i = 0; i < 62; i++) {
      summary.setParameterMayEscapeThread(i, false);
      assertThat(summary.parameterMayEscapeThread(i), is(false));
    }
  }

  @Test
  public void methodParametersGreaterThan62EscapeByDefault() {
    for (int i = 63; i < 255; i++) {
      assertThat(summary.parameterMayEscapeThread(i), is(true));
    }
  }

  @Test
  public void methodParametersGreaterThan62AlwaysEscape() {
    for (int i = 63; i < 255; i++) {
      summary.setParameterMayEscapeThread(i, false);
      assertThat(summary.parameterMayEscapeThread(i), is(true));
    }
  }

}
