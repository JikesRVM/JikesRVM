/*
 * (C) Copyright IBM Corp 2001,2002
 *
 * ==========
 * $Source$
 * $Revision$
 * $Date$
 * $Author$
 */
package org.eclipse.jdt.internal.launching.jikesrvm;

import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.jdt.launching.AbstractVMInstall;
import org.eclipse.jdt.launching.IVMInstallType;
import org.eclipse.jdt.launching.IVMRunner;
import org.eclipse.jdt.launching.LibraryLocation;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Jeffrey Palm
 */
public class JikesRVMInstall extends AbstractVMInstall {

  /** System property key for RVM_ROOT */
  final static String SYS_RVM_ROOT = "rvm.root";

  /** System property key for RVM_BUILD */
  final static String SYS_RVM_BUILD = "rvm.build";
 
  /**
   * @see IVMInstall#getVMRunner(String)
   */
  public IVMRunner getVMRunner(String mode) {

    if (ILaunchManager.RUN_MODE.equals(mode)) {
      return new JikesRVMRunner(this);
    } 

    if (ILaunchManager.DEBUG_MODE.equals(mode)) {
      //
      // We can't debug... yet
      //
      //return new JikesRVMDebugVMRunner(this);
      final String msg = 
	JikesRVMLauncherMessages.getString("JikesRVMInstall.error.noDebuggerMessage");
      JikesRVMRunner.showErrorDialog
	(JikesRVMLauncherMessages.getString("JikesRVMInstall.error.noDebuggerTitle"),
	 JikesRVMLauncherMessages.getString("JikesRVMInstall.error.noDebuggerMessage"),
	 new Status(IStatus.ERROR, JikesRVMLaunchingPlugin.getPluginId(), 
		    0, 
		    JikesRVMLauncherMessages.getString
		    ("JikesRVMInstall.error.noDebuggerReason"), 
		    null));
      return null;
      
    }
    return null;
  }
  /**
   * Constructor for Jikes RVM
   */
  JikesRVMInstall(IVMInstallType type, String id) {
    super(type, id);
    setRvmRoot(getDefaultRvmRoot());
    setRvmBuild(getDefaultRvmBuild());
  }

  /**
   * This is a hack.  The class {@link org.eclipse.jdt.internal.ui.launcher.AddVMDialog}
   * calls this before closing its preferences box, so this seemed an
   * opportune time to ask the user for the following variables:
   * <ul>
   *  <li><code>RVM_BUILD</code>
   *  <li><code>RVM_ROOT</code>
   * </ul>
   *
   * @see IVMInstall#setLibraryLocation(LibraryLocation)
   */
  public void setLibraryLocation(LibraryLocation description) {
    super.setLibraryLocation(description);
    ensureRequiredEnvVariables();
  }

  private void ensureRequiredEnvVariables() {    
    // The first time start with defaults
    for (boolean first = true;
	 overridingEnv && (rvmRoot == null || rvmBuild == null);
	 first = false) {
      ChangeConfigurationDialog.changeConfiguration(this, first);
    }
  }

  private boolean overridingEnv = false;
  void setOverridingEnv(boolean overridingEnv) { this.overridingEnv = overridingEnv; }
  public boolean overridingEnv() { return overridingEnv; }

  private String home;
  public String getHome() { /*TODO*/ return System.getProperty("user.home"); }

  private String rvmBuild;
  void setRvmBuild(String rvmBuild) { this.rvmBuild = rvmBuild; }
  public String getRvmBuild() { return rvmBuild; }
  

  private String rvmRoot;
  void setRvmRoot(String rvmRoot) { this.rvmRoot = rvmRoot; }
  public String getRvmRoot() { return rvmRoot; }

  public final static String getDefaultRvmRoot() { return System.getProperty(SYS_RVM_ROOT); }
  public final static String getDefaultRvmBuild() { return System.getProperty(SYS_RVM_BUILD); }

  /**
   * Hashcode of the id.  This is needed because we keep a list
   * of all installed Jikes RVMs in {@link JikesRVMInstallType}.
   */
  public int hashCode() {
    String id = getId();
    if (id == null) {
      String msg = JikesRVMLauncherMessages.getString("JikesRVMInstall.error.nullId");
      throw new NullPointerException(msg);
    }
    return id.hashCode();
  }

  public String toString() {
    return JikesRVMLauncherMessages.getString("JikesRVMInstall.name") + " (" + getId() + ")";
  }

}
