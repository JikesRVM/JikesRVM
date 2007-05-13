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
import org.apache.tools.ant.Task;
import org.apache.tools.ant.taskdefs.Property;

/**
 * Timer task makes it possible to measure the start, stop and duration between two points in time.
 *
 * When the task is run with stop set to fals or not yet specified the task stores
 * the current time in a property with the name "&lt;property&gt;.start". If stop is set to
 * true then the task attempts to load a start time from "&lt;property&gt;.start" and then
 * stores the current time in "&lt;property&gt;.end" and the duration between "&lt;property&gt;.start"
 * and now in "&lt;property&gt;.duration". 
 */
public class TimerTask
    extends Task {
  private String property;
  private boolean stop;

  public void setProperty(final String property) {
    this.property = property;
  }

  public void setStop(final boolean stop) {
    this.stop = stop;
  }

  public void execute() throws BuildException {
    if (null == property) throw new BuildException("Property not set.");
    final long now = System.currentTimeMillis();
    if (stop) {
      final String start = getProject().getProperty(property + ".start");
      if (null == start) throw new BuildException("Start not yet set.");
      final long startTime = Long.parseLong(start);
      setProperty(property + ".end", String.valueOf(now));
      setProperty(property + ".duration", String.valueOf(now - startTime));
    } else {
      setProperty(property + ".start", String.valueOf(now));
    }
  }

  private void setProperty(final String name, final String value) {
    final Property property = (Property) getProject().createTask("property");
    property.setOwningTarget(getOwningTarget());
    property.init();
    property.setName(name);
    property.setValue(value);
    property.execute();
  }
}
