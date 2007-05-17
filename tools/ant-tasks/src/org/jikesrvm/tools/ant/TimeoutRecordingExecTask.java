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
package org.jikesrvm.tools.ant;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.taskdefs.ExecTask;
import org.apache.tools.ant.taskdefs.ExecuteWatchdog;
import org.apache.tools.ant.taskdefs.Property;

/**
 * ExecTask extension that sets a proeprty when watchdog kills task.
 */
public class TimeoutRecordingExecTask
    extends ExecTask {
  private String timeoutProperty;
  private ExecuteWatchdog watchdog;

  public void setTimeoutProperty(final String timeoutProperty) {
    this.timeoutProperty = timeoutProperty;
  }

  public void execute() throws BuildException {
    super.execute();
    if (null != watchdog && watchdog.killedProcess()) {
      final Property property = (Property) getProject().createTask("property");
      property.setOwningTarget(getOwningTarget());
      property.init();
      property.setName(timeoutProperty);
      property.setValue("true");
      property.execute();
    }
  }

  protected ExecuteWatchdog createWatchdog() throws BuildException {
    watchdog = super.createWatchdog();
    return watchdog;
  }
}
