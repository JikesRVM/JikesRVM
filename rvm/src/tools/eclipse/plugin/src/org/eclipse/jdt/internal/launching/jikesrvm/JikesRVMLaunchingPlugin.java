/*
 * ===========================================================================
 * IBM Confidential
 * Software Group 
 *
 * Eclipse/Jikes
 *
 * (C) Copyright IBM Corp., 2002.
 *
 * The source code for this program is not published or otherwise divested of
 * its trade secrets, irrespective of what has been deposited with the U.S.
 * Copyright office.
 *
 * ==========
 * $Source$
 * $Revision$
 * $Date$
 * $Author$
 */
package org.eclipse.jdt.internal.launching.jikesrvm;

import java.util.*;

import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.IPluginDescriptor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.plugin.AbstractUIPlugin;

public class JikesRVMLaunchingPlugin extends AbstractUIPlugin {

  private static JikesRVMLaunchingPlugin instance;

  /**
   * Utility method with conventions
   */
  public static void errorDialog(Shell shell, String title, String message, IStatus s) {
    ErrorDialog.openError(shell, title, message, s);
  }

  public static Shell getActiveWorkbenchShell() {
    return getActiveWorkbenchWindow().getShell();
  }

  public static IWorkbenchWindow getActiveWorkbenchWindow() {
    return getDefault().getWorkbench().getActiveWorkbenchWindow();
  }

  public static JikesRVMLaunchingPlugin getDefault() {
    return instance;
  }

  public static String getPluginId() {
    return getDefault().getDescriptor().getUniqueIdentifier();
  }
  
  /**
   * Constructor for JikesRVMLaunchingPlugin
   */
  public JikesRVMLaunchingPlugin(IPluginDescriptor descriptor) {
    super(descriptor);
    instance = this;
  }

  /** Keeps track of all the RVM installs. */
  private final Map installs = new HashMap();

  /**
   * Adds an RVM install.
   * @param install new RVM install
   */
  public void addInstall(JikesRVMInstall install) {
    if (install == null) {
      String msg = JikesRVMLauncherMessages.getString("JikesRVMLaunchingPlugin.error.nullInstall");
      throw new NullPointerException(msg);
    }
    JikesRVMDebug.d.bug("addInsall (begin) install="+install);
    installs.put(install.toString(), install);
    JikesRVMDebug.d.bug("addInsall (end)  install="+install);
  }

  /**
   * Returns all known RVM installs.
   * @return all known RVM installs.
   */
  public Collection getRVMs() {
    return installs.values();
  }

  /**
   * Returns an RVM install for the passed in toString.
   * @param  id id of the desired RVM install
   * @return instance of JikesRVMInstall fi found, <code>null</code> otherwise
   */
  public JikesRVMInstall getInstall(String toString) {
    return (JikesRVMInstall)installs.get(toString);
  }

  /**
   * Removes an RVM install.
   * @param  toString toString of the RVM install to remove from our list
   * @return The removed install, <code>null</code> if it
   *         was not found.
   */
  public JikesRVMInstall removeInstall(String toString) {
    JikesRVMInstall install = getInstall(toString);
    if (install == null) {
      errorDialog(getActiveWorkbenchShell(), 
		  JikesRVMLauncherMessages.getString("JikesRVMLauncher.dialog.error"),
		  JikesRVMLauncherMessages.getString("JikesRVMLauncher.removeInstall.notFound"),
		  new Status(IStatus.ERROR, JikesRVMLaunchingPlugin.getPluginId(), 0, 
			     JikesRVMLauncherMessages.getString("JikesRVMType.removeInstall.notFound"), 
			     null));
    }
    installs.remove(toString);
    return install;
  }
}
