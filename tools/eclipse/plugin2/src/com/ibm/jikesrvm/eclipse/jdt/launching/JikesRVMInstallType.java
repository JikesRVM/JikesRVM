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

import java.io.File;
import java.util.*;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.launching.AbstractVMInstallType;
import org.eclipse.jdt.launching.IVMInstall;
import org.eclipse.jdt.launching.LibraryLocation;

/**
 * @author Jeffrey Palm
 */
public class JikesRVMInstallType extends AbstractVMInstallType {

  /**
   * @see IVMInstallType#detectInstallLocation()
   */
  public File detectInstallLocation() {

    String javaVmName = System.getProperty("java.vm.name");
    JikesRVMDebug.d.bug("java.vm.name=" + javaVmName);
    if (!"JikesRVM".equals(javaVmName)) {
      return null;
    }

    String rvmRoot = System.getProperty("rvm.root");
    JikesRVMDebug.d.bug("rvm.root="+rvmRoot);
    return new File(rvmRoot);
  }

  /**
   * @see AbstractVMInstallType#doCreateVMInstall()
   */
  public IVMInstall doCreateVMInstall(String id) {
    JikesRVMInstall install = new JikesRVMInstall(this, id);
    JikesRVMLaunchingPlugin.getDefault().addInstall(install);
    return install;
  }

  /**
   * @see AbstractVMInstallType#disposeVMInstall(String)
   */
  public void disposeVMInstall(String id) {
    JikesRVMLaunchingPlugin.getDefault().removeInstall(id);
  }

  private LibraryLocation[] defaultLibraryLocations;

  private void setDefaultLibraryLocations(File installLocation) {
    // libs
    String buildPath = System.getProperty("rvm.build") + "/RVM.classes";
    Path rvmrtLib = new Path(buildPath + "/rvmrt.jar");
    Path jksvmLib = new Path(buildPath + "/jksvm.jar");

    // srcs
    File supportLibs = new File(installLocation, "/support/lib/");
    Path rvmrtSrc = new Path(supportLibs.getPath() + "/classpathsrc.jar");
    Path jksvmSrc = new Path(buildPath + "/jksvmsrc.jar");

    // path within src jars
    IPath thePath = Path.EMPTY;
    
    defaultLibraryLocations = new LibraryLocation[]{ 
        new LibraryLocation(rvmrtLib, rvmrtSrc, thePath),
        new LibraryLocation(jksvmLib, jksvmSrc, thePath) 
    };
  }

  public LibraryLocation[] getDefaultLibraryLocations(File installLocation) {
      if (defaultLibraryLocations == null)
          setDefaultLibraryLocations( installLocation );

      return defaultLibraryLocations;
  }

  /**
   * @see IVMInstallType#getName()
   */
  public String getName() {
    return JikesRVMLauncherMessages.getString("JikesRVMType.name");
  }

  /**
   * @see IVMInstallType#validateInstallLocation(java.io.File)
   */
  public IStatus validateInstallLocation(File installLocation) {
    File rvmDir = new File(installLocation, "rvm");
    File binDir = new File(rvmDir, "bin");
    File rvm    = new File(binDir, "rvm");
    if (!rvm.isFile() || rvm.isDirectory()) {
      return new Status(IStatus.ERROR, JikesRVMLaunchingPlugin.getPluginId(), 0, 
                        JikesRVMLauncherMessages.getString("JikesRVMType.error.notRoot"), null);
    }
    return new Status(IStatus.OK, JikesRVMLaunchingPlugin.getPluginId(), 0, "ok", null);
  }

  public JikesRVMInstallType() {
    super();
  }
}
