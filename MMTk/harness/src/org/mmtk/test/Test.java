/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.mmtk.test;

import org.mmtk.harness.Harness;
import org.mmtk.harness.Mutator;

/**
 * This is the entry point for running simple MMTk tests.
 */
public abstract class Test {
    public static void main(String[] args) {
      /* Usage */
      if (args.length < 1) {
        System.err.println("usage: java -jar mmtk-harness.jar test-class [options ...]");
        System.exit(1);
      }

      /* First argument is the test class name */
      String[] harnessArgs = new String[args.length - 1];
      System.arraycopy(args, 1, harnessArgs, 0, harnessArgs.length);

      /* Initialise the harness */
      Harness.init(harnessArgs);

      /* Determine the test class name */
      String testClassName = args[0];
      if (!testClassName.contains(".")) {
        testClassName = "org.mmtk.test." + testClassName;
      }

      /* Create the test class */
      final Test test;
      try {
        Class<?> testClass = Class.forName(testClassName);
        test = (Test)testClass.newInstance();
      } catch (Exception ex) {
        throw new RuntimeException("Error creating test class", ex);
      }

      /* Invoke the test */
      Mutator m = new Mutator(new Runnable() {
        public void run() {
          test.main(Mutator.current());
        }
      });
      m.start();
    }

    /**
     * Entry point for tests.
     */
    protected abstract void main(Mutator m);
}
