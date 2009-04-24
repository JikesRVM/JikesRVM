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
      return InetAddress.getLocalHost().getCanonicalHostName();
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
