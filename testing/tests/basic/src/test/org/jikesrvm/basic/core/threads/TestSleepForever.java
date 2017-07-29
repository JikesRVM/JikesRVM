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
package test.org.jikesrvm.basic.core.threads;

/**
 * Sleeps forever. This test is intended as a sanity check for
 * the test system: After a timeout that can be configured
 * in the {@code build.xml} file for the containing test run,
 * the test harness should kill the test and the result should
 * be set to {@code OVERTIME}. This isn't useful as a general
 * test so it's disabled by default.
 */
class TestSleepForever {
  public static void main(String[] args) throws Throwable {
    Thread.sleep(100000000L);
  }
}
