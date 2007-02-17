package org.jikesrvm.tools.ant;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.taskdefs.Ant;
import org.apache.tools.ant.taskdefs.Property;

/**
 * AntTask extension that can ignore build failures and may set a property on failure.
 *
 * @author Peter Donald
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
