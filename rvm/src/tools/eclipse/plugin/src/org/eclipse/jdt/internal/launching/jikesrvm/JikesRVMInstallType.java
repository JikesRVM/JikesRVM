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

  /**
   * @see IVMInstallType#getDefaultSystemLibraryDescription(java.io.File)
   */
  public LibraryLocation getDefaultLibraryLocation(File installLocation) {

    // Location of the OTI libraries
    File support = new File(installLocation, "support");

    // lib
    File supportLib = new File(support, "lib");
    File lib = new File(supportLib, "rvmrt.jar");
    
    // src
    File supportSrc = new File(support, "src");
    File src = new File(supportSrc, "src.zip");

    // path TODO: what the hell is this???
    IPath path= new Path("");
    
    return new LibraryLocation(lib, src, path);
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

  JikesRVMInstallType() {
    super();
  }
}
