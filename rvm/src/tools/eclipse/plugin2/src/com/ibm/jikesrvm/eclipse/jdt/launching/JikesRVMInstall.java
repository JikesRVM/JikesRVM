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

  /** Which debugging mode we're using */
  private DebugMode debugMode;

  /**
   * @see IVMInstall#getVMRunner(String)
   */
  public IVMRunner getVMRunner(String mode) {

    if (ILaunchManager.RUN_MODE.equals(mode)) {
      return new JikesRVMRunner(this);
    } 

    if (ILaunchManager.DEBUG_MODE.equals(mode)) {
      
      // we'll try...
      return new JikesRVMDebugger(this);
      
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
    setDebugMode(DebugMode.GUI);
  }

  /**
   * A type safe enum for the debugging modes
   */
  public final static class DebugMode {

    private final String s;
    private DebugMode(String s) { this.s = s; }

    public final static DebugMode COMMAND_LINE = new DebugMode("Command Line");
    public final static DebugMode GUI          = new DebugMode("GUI");

    public String toString() { return s; }

  }

  public final void setDebugMode(DebugMode debugMode) {
    this.debugMode = debugMode;
  }

  public final DebugMode getDebugMode() {
    return debugMode;
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
   * @see IVMInstall#setLibraryLocations(LibraryLocation)
   */
  public void setLibraryLocations(LibraryLocation[] locations) {
    super.setLibraryLocations(locations);
    ensureRequiredEnvVariables();
  }

  private void ensureRequiredEnvVariables() {    
    // The first time start with defaults
    for (boolean first = true;
         overridingEnv && (rvmRoot == null || rvmBuild == null);
         first = false) {
      JikesRVMChangeConfigurationDialog.changeConfiguration(this, first);
    }
  }

  private boolean overridingEnv = false;
  void setOverridingEnv(boolean overridingEnv) { 
    this.overridingEnv = overridingEnv; 
  }
  public boolean overridingEnv() { 
    return overridingEnv; 
  }

  private String home;
  public String getHome() { 
    /*TODO*/ return System.getProperty("user.home"); 
  }

  private String rvmBuild;
  void setRvmBuild(String rvmBuild) { 
    this.rvmBuild = rvmBuild; 
  }
  public String getRvmBuild() { 
    return rvmBuild; 
  }
  

  private String rvmRoot;
  void setRvmRoot(String rvmRoot) { 
    this.rvmRoot = rvmRoot; 
  }
  public String getRvmRoot() { 
    return rvmRoot; 
  }

  public final static String getDefaultRvmRoot() { 
    return System.getProperty(SYS_RVM_ROOT); 
  }
  public final static String getDefaultRvmBuild() { 
    return System.getProperty(SYS_RVM_BUILD); 
  }

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
