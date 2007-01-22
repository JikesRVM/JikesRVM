/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Peter Donald. 2007
 */
package org.jikesrvm.tools.ant;

import java.util.ArrayList;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.taskdefs.CallTarget;
import org.apache.tools.ant.taskdefs.Property;

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
  private final ArrayList<Property> properties = new ArrayList<Property>();

  public void setProperty(final String property) { this.property = property; }

  public void setList(final String list) { this.list = list; }

  public void setTarget(final String target) { this.target = target; }

  public Property createParam() {
    final Property property = new Property();
    properties.add(property);
    return property;
  }

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
      addParam(a, property, value);
      for (final Property p : properties) {
        addParam(a, p.getName(), p.getValue());
      }
      a.execute();
    }
  }

  private void addParam(final CallTarget a, final String key, final String value) {
    final Property param = a.createParam();
    param.setName(key);
    param.setValue(value);
  }

  private void validate() {
    if (null == list) throw new BuildException("List not set.");
    if (null == target) throw new BuildException("Target not set.");
    if (null == property) throw new BuildException("Property not set.");
  }
}
