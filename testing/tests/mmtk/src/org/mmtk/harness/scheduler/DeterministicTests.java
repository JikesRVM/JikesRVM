package org.mmtk.harness.scheduler;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mmtk.harness.Harness;

/**
 * Deterministic scheduler test cases, with maximum interleaving
 */
public class DeterministicTests {

  @BeforeClass
  public static void setUpBeforeClass() throws Exception {
    Harness.init("scheduler=DETERMINISTIC","schedulerPolicy=FIXED","yieldInterval=1");
  }

  @Before
  public void setUp() throws Exception {
  }

  @Test
  public void testOneThreadOneItem() {
    assertEquals(Arrays.asList("a"),
        new SchedulerTestCases().testOneThreadOneItem("a"));
  }

  @Test
  public void testOneThreadTwoItems() {
    assertEquals(Arrays.asList("a","b"),
        new SchedulerTestCases().testOneThreadTwoItems("a","b"));
  }

  @Test
  public void testTwoThreadsOneItem() {
    assertEquals(Arrays.asList("a","b"),
        new SchedulerTestCases().testTwoThreadsOneItem("a","b"));
  }

  @Test
  public void testTwoThreadsTwoItems() {
    Object a = new Object();
    Object b = new Object();
    Object c = new Object();
    Object d = new Object();
    assertEquals(Arrays.asList("a","c","b","d"),
        new SchedulerTestCases().testTwoThreadsTwoItems("a","b","c","d"));
  }
}
