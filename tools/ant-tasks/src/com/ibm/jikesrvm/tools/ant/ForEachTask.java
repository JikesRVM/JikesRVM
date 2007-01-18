/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Peter Donald. 2007
 */
package com.ibm.jikesrvm.tools.ant;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.taskdefs.Property;
import org.apache.tools.ant.taskdefs.CallTarget;

/**
 * A looping construct for ant. The task will accept a list of space separated
 * values. The task will then invoke antcall setting property with name passed in property
 * parameter to value.
 *
 * @author Peter Donald
 */
public class ForEachTask
    extends Task {
  private String list;
  private String target;
  private String property;

  public void setProperty(final String property) { this.property = property; }

  public void setList(final String list) { this.list = list; }

  public void setTarget(final String target) { this.target = target; }

  public void execute() {
    validate();

    final String[] values = list.split(" ");
    for (String value : values) {
      final CallTarget a = (CallTarget) getProject().createTask("antcall");
      a.setOwningTarget(getOwningTarget());
      a.init();
      a.setTarget(target);
      a.setInheritAll(false);
      a.setInheritRefs(false);
      final Property param = a.createParam();
      param.setName(property);
      param.setValue(value);
      a.execute();
    }
  }

  private void validate() {
    if (null == list) throw new BuildException("List not set.");
    if (null == target) throw new BuildException("Target not set.");
    if (null == property) throw new BuildException("Property not set.");
  }
}
