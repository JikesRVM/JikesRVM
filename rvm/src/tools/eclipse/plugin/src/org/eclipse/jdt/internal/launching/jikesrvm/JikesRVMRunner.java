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
import java.io.IOException;
import java.util.List;
import java.util.Vector;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.jdt.launching.IVMInstall;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jdt.launching.VMRunnerConfiguration;
import org.eclipse.jdt.launching.VMRunnerResult;

/**
 * @author Jeffrey Palm
 */
public class JikesRVMRunner extends JavaVMRunner {
	
  protected String convertClassPath(String[] cp) {
    int pathCount= 0;
    StringBuffer buf= new StringBuffer();
    for (int i= 0; i < cp.length; i++) {
      if (pathCount > 0) {
	buf.append(File.pathSeparator);
      }
      buf.append(cp[i]);
      pathCount++;
    }
    return buf.toString();
  }

  public JikesRVMRunner(IVMInstall vmInstance) {
    super(vmInstance);
  }

  public VMRunnerResult run(VMRunnerConfiguration config) {

    String location= getRVMLocation("");
    if ("".equals(location)) {
      String message= JikesRVMLauncherMessages.getString("JikesRVMLauncher.error.noHome");
      showErrorDialog(JikesRVMLauncherMessages.getString("JikesRVMLauncher.dialog.title"), 
		      message, new Status(IStatus.ERROR, JikesRVMLaunchingPlugin.getPluginId(), 
					  0, message, null));
      return null;
    }

    if (!(fVMInstance instanceof JikesRVMInstall)) {
      
    }
    JikesRVMInstall install = (JikesRVMInstall)fVMInstance;	
    
    // Try to find an executable
    // If we're overriding the environment, then we run 'runrvm' otherwise
    // we'll run 'rvm' with the known environment
    File rvmDir = new File(location, "rvm");
    File binDir = new File(rvmDir, "bin");
    File rvm    = new File(binDir, install.overridingEnv() ? "runrvm" : "rvm");
    String program = rvm.getAbsolutePath();
		
    Vector arguments = new Vector();

    arguments.addElement(program);

    // Add the location of HOME, RVM_ROOT, and RVM_BUILD if we're
    // overriding these locations
    if (install.overridingEnv()) {
      arguments.add(install.getHome());
      arguments.add(install.getRvmRoot());
      arguments.add(install.getRvmBuild());
    }
				
    //TODO: Look at this crap!
//      String[] bootCP= config.getBootClassPath();
//      if (bootCP.length > 0) {
//        arguments.add("-bp:"+convertClassPath(bootCP));
//      }  

    String[] cp = config.getClassPath();
    if (cp.length > 0) {
      arguments.add("-classpath");
      arguments.add(convertClassPath(cp));
    }

    // Ensure that the crap we get from config.getVMArguments aren't crap
    String[] vmArgs = validateVMArguments(config.getVMArguments(), install);
    addArguments(vmArgs, arguments);
		
    arguments.addElement(config.getClassToLaunch());
		
    String[] programArgs = config.getProgramArguments();
    addArguments(programArgs, arguments);

    String[] cmdLine = new String[arguments.size()];
    arguments.copyInto(cmdLine);

    try {     
      Process p = Runtime.getRuntime().exec(cmdLine);
      IProcess process = DebugPlugin.newProcess(p, renderProcessLabel(cmdLine));
      process.setAttribute(JavaRuntime.ATTR_CMDLINE, renderCommandLine(cmdLine));
      return new VMRunnerResult(null, new IProcess[] { process });
    } catch (IOException e) {
      
      //palm
      e.printStackTrace();
      
      String title= JikesRVMLauncherMessages.getString("JikesRVMLauncher.dialog.title");
      String message= JikesRVMLauncherMessages.getString("JikesRVMLauncher.error.startVM");
      showErrorDialog(title, message, 
		      new Status(IStatus.ERROR, JikesRVMLaunchingPlugin.getPluginId(), 0, 
				 JikesRVMLauncherMessages.getString("JikesRVMLauncher.status.startVM"), 
				 e));
    }
    return null;
  }

  protected String[] linkToCurrentEnv(String[] envp) {
    //TODO
    return envp;
  }

  protected boolean shouldIncludeInPath(String path) {
    return true;
  }

  protected String[] validateVMArguments(String[] args, JikesRVMInstall install) {
    final List result = new Vector();
    for (int i = 0; i < args.length; i++) {
      String arg = args[i];
      if (arg.equals("-verify")) continue;
      result.add(arg);
    }
    //XXX This is basically a shitty hack
    String rvmBuild = install.getRvmBuild();
    if (rvmBuild != null && rvmBuild.indexOf("Adaptive") != -1) {
      result.add("-X:aos:primary_strategy=baseonly");
      result.add("-X:aos:gather_profile_data=true");
    }
    return (String[])result.toArray(new String[0]);
  }
}
