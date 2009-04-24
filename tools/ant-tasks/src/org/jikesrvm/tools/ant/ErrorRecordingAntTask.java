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
package org.jikesrvm.tools.ant;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.taskdefs.Ant;
import org.apache.tools.ant.taskdefs.Property;

/**
 * AntTask extension that can ignore build failures and may set a property on failure.
 */
public class ErrorRecordingAntTask
    extends Ant {
  private boolean failonerror = true;
  private String failonerrorProperty;

  public ErrorRecordingAntTask() {
    setInheritAll(false);
  }

  public void setFailonerror(final boolean failonerror) {
    this.failonerror = failonerror;
  }

  public void setFailonerrorProperty(final String failonerrorProperty) {
    this.failonerrorProperty = failonerrorProperty;
  }

  public void execute() throws BuildException {
    try {
      super.execute();
    } catch (final BuildException be) {
      if (failonerror) throw be;
      if (null != failonerrorProperty) {
        final Property property = (Property) getProject().createTask("property");
        property.setOwningTarget(getOwningTarget());
        property.init();
        property.setName(failonerrorProperty);
        property.setValue("true");
        property.execute();
      }
    }
  }
}
