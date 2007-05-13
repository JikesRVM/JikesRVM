/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Peter Donald. 2007
 */
package org.jikesrvm.tools.ant;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.taskdefs.ExecTask;
import org.apache.tools.ant.taskdefs.ExecuteWatchdog;
import org.apache.tools.ant.taskdefs.Property;

/**
 * ExecTask extension that sets a proeprty when watchdog kills task.
 *
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
