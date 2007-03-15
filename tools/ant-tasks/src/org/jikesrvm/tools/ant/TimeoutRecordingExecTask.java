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
import org.apache.tools.ant.util.Watchdog;
import org.apache.tools.ant.taskdefs.ExecTask;
import org.apache.tools.ant.taskdefs.ExecuteWatchdog;
import org.apache.tools.ant.taskdefs.Property;
import java.lang.reflect.Field;

/**
 * ExecTask extension that sets a proeprty when watchdog kills task.
 *
 * @author Peter Donald
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
    if (false) {
      watchdog = super.createWatchdog();
    } else {
      try {
        final Field field = ExecTask.class.getDeclaredField("timeout");
        field.setAccessible(true);
        final Long timeout = (Long) field.get(this);
        watchdog = (timeout == null) ? null : new MyExecuteWatchdog(timeout);
      } catch (final Exception e) {
        e.printStackTrace();
        throw new BuildException("Error getting timeout", e);
      }
    }
    return watchdog;
  }

  static class MyExecuteWatchdog extends ExecuteWatchdog {

    public MyExecuteWatchdog(long l) {
      super(l);
    }

    public void timeoutOccured(Watchdog watchdog) {
      System.out.println("Timeout occured. Attempting to kill process...");
      try {
        super.timeoutOccured(watchdog);
        System.out.println("Process should be dead...");
      } catch (final Throwable e) {
        System.out.println("Error terminating process...");
        e.printStackTrace(System.out);        
      }      
    }
  }
}
