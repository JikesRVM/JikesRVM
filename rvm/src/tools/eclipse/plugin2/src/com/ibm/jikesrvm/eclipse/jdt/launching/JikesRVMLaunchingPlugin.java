/*
 * (C) Copyright IBM Corp 2001,2002
 *
 * ==========
 * $Source$
 * $Revision$
 * $Date$
 * $Author$
 * $Id$
 */
package com.ibm.jikesrvm.eclipse.jdt.launching;

import java.util.*;

import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.IPluginDescriptor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.plugin.AbstractUIPlugin;

/**
 * @author Jeffrey Palm
 */
public class JikesRVMLaunchingPlugin extends AbstractUIPlugin {

  private static JikesRVMLaunchingPlugin instance;

  // For debugging we just want to launch the jdp wrapper
  private String debuggerProgram = !JikesRVMDebug.d.debug() ? "jdpWrapper" : "jdp";

  /**
   * Utility method with conventions
   */
  public static void errorDialog(Shell shell, String title, String message, IStatus s) {
    ErrorDialog.openError(shell, title, message, s);
  }

  public static Shell getActiveWorkbenchShell() {
    IWorkbenchWindow window = getActiveWorkbenchWindow();
    if (window != null) {
      return window.getShell();
    }
    return null; //TODO
  }

  public static IWorkbenchWindow getActiveWorkbenchWindow() {
    return getDefault().getWorkbench().getActiveWorkbenchWindow();
  }

  public static JikesRVMLaunchingPlugin getDefault() {
    if (instance == null) {
      throw new NullPointerException("JikesRVMLaunchingPlugin.instance is NULL!");
    }
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
    JikesRVMDebug.d.bug("addInstall (begin) install="+install);
    installs.put(install.toString(), install);
    JikesRVMDebug.d.bug("addInstall   (end) install="+install);
  }

  /**
   * Returns all known RVM installs.
   * @return all known RVM installs.
   */
  public Collection getRVMs() {
    return installs.values();
  }

  /**
   * Returns the program to use for debugging... just the name.
   * @return the program to use for debugging... just the name.
   */
  public final String debuggerProgram() {
    return debuggerProgram;
  }

  /**
   * Sets the program to use for debugging.
   * @param debuggerProgram the program to use for debugging
   */
  public final void setDebuggerProgram(String debuggerProgram) {
    this.debuggerProgram = debuggerProgram;
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
