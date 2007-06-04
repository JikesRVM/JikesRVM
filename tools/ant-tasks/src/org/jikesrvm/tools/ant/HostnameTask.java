package org.jikesrvm.tools.ant;

import java.net.InetAddress;
import java.net.UnknownHostException;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.taskdefs.Property;

/**
 * Hostname task sets a property to name of host.
 */
public class HostnameTask extends Task {
  private String property;

  public void setProperty(final String property) {
    this.property = property;
  }

  public void execute() throws BuildException {
    if (null == property) throw new BuildException("Property not set.");
    setProperty(property, getHostname());
  }

  private String getHostname() {
    try {
      return InetAddress.getLocalHost().getHostName();
    } catch (final UnknownHostException uhe) {
      return "Unknown";
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
