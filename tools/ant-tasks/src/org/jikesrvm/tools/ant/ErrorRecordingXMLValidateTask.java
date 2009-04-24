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
import org.apache.tools.ant.taskdefs.optional.XMLValidateTask;

/**
 * AntTask extension that can ignore build failures and may set a property on failure.
 */
public class ErrorRecordingXMLValidateTask extends XMLValidateTask {
  private String failonerrorProperty;

  public ErrorRecordingXMLValidateTask() {
  }

  public void setFailonerrorProperty(final String failonerrorProperty) {
    this.failonerrorProperty = failonerrorProperty;
  }

  public void execute() throws BuildException {
    try {
      super.execute();
    } catch (final BuildException be) {
      if (failonerrorProperty == null) {
        throw be;
      }
      final org.apache.tools.ant.taskdefs.Property property = (org.apache.tools.ant.taskdefs.Property) getProject().createTask("property");
      property.setOwningTarget(getOwningTarget());
      property.init();
      property.setName(failonerrorProperty);
      property.setValue("true");
      property.execute();
    }
  }
}
