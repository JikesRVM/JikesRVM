package org.mmtk.harness.scheduler;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mmtk.harness.Harness;

/**
 * Test the plain Java scheduler
 */
public class JavaSchedulerTest {

  @BeforeClass
  public static void setUpBeforeClass() throws Exception {
    Harness.init("scheduler=JAVA"/* ,"trace=SCHEDULER" */);
  }

  @Before
  public void setUp() throws Exception {
  }

  @Test
  public void testOneThreadOneItem() {
    assertEquals(Arrays.asList("a"),new SchedulerTestCases().testOneThreadOneItem("a"));
  }

  @Test
  public void testOneThreadTwoItems() {
    assertEquals(Arrays.asList("a","b"),
        new SchedulerTestCases().testOneThreadTwoItems("a","b"));
  }

  @Test
  public void testTwoThreadsOneItem() {
    assertEquals(setOf("a","b"),
        setOf(new SchedulerTestCases().testTwoThreadsOneItem("a", "b")));
  }

  @Test
  public void testTwoThreadsTwoItems() {
    List<Object> result = new SchedulerTestCases().testTwoThreadsTwoItems("a", "b", "c", "d");
    assertEquals(setOf("a","b","c","d"),setOf(result));
    assertTrue(result.indexOf("a") < result.indexOf("b"));
    assertTrue(result.indexOf("c") < result.indexOf("d"));
  }

  private <T> Set<T> setOf(T...items) {
    return new HashSet<T>(Arrays.asList(items));
  }

  private <T> Set<T> setOf(Collection<T> items) {
    return new HashSet<T>(items);
  }

}
