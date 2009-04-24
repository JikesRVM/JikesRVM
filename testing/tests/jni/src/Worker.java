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
class Worker extends Thread  {
  private String name;
  volatile boolean readyFlag = false;
  volatile boolean doneFlag = false;
  Object theLock;
  int rc;

  /**
   * Constructor
   */
  Worker(String name, Object lockObject) {
    this.name = name;
    theLock = lockObject;
    readyFlag = false;
    doneFlag = false;

  }



  // overrides Thread
  public void start()  {
    super.start();
  }

  // overrides Thread
  public void run() {

    // signal ready and wait for the main thread to tell to start
    readyFlag = true;
    MonitorTest.printVerbose(".... " + name + " ready to start");
    while (!MonitorTest.startCounting) {
    }

    // call the native code to contend for the lock from native
    MonitorTest.printVerbose(".... " + name + " calling native monitor");
    rc = MonitorTest.accessMonitorFromNative(theLock);

    if (rc!=0)
      MonitorTest.setFailFlag();

    MonitorTest.printVerbose(".... " + name + " done.");
    doneFlag = true;



  }


}
