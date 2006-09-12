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
import java.io.IOException;
import java.util.List;
import java.util.Vector;
import java.text.DateFormat;
import java.text.MessageFormat;
import java.util.Date;
import java.text.*;

import org.eclipse.core.*;
import org.eclipse.core.runtime.*;
import org.eclipse.debug.core.*;
import org.eclipse.debug.core.model.*;
import org.eclipse.jdt.launching.*;
import org.eclipse.swt.widgets.*;
import org.eclipse.jface.dialogs.*;


import com.sun.jdi.VirtualMachine;

/**
 * @author Jeffrey Palm
 */
public class JikesRVMRunner implements IVMRunner {

  private final JikesRVMInstall install;

  protected final JikesRVMInstall install() {
    return install;
  }

  protected void addArguments(String[] args, List v) {
    if (args == null) {
      return;
    }
    for (int i= 0; i < args.length; i++) {
      v.add(args[i]);
    }
  }

  protected boolean askRetry(final String title, final String message) {
    final boolean[] result= new boolean[1];
    Display.getDefault().syncExec(new Runnable() {
        public void run() {
          result[0] = (MessageDialog.openConfirm
                       (JikesRVMLaunchingPlugin.getActiveWorkbenchShell(), 
                        title, message));
        }
      });
    return result[0];
  }

  protected String getRVMLocation(String dflt) {
    File location= install().getInstallLocation();
    JikesRVMDebug.d.bug("getRVMLocation location="+location);
    if (location == null) {
      return dflt;
    }
    return location.getAbsolutePath();
  }

  protected static String renderCommandLine(String[] commandLine) {
    if (commandLine.length < 1) {
      return ""; 
    }
    StringBuffer buf= new StringBuffer(commandLine[0]);
    for (int i= 1; i < commandLine.length; i++) {
      buf.append(' ');
      buf.append(commandLine[i]);
    }
    JikesRVMDebug.d.bug("renderCommandLine returning " + buf);
    return buf.toString();
  }

  protected String renderDebugTarget(String classToRun, int host) {
    String format = JikesRVMLauncherMessages.getString("javaVMRunner.format.dbgTarget");
    return MessageFormat.format(format, new String[] { classToRun, String.valueOf(host) });
  }

  public static String renderProcessLabel(String[] commandLine) {
    String format = JikesRVMLauncherMessages.getString("javaVMRunner.format.processLabel");
    String timestamp = DateFormat.getInstance().format(new Date(System.currentTimeMillis()));
    return MessageFormat.format(format, new String[] { commandLine[0], timestamp });
  }

  protected final static void showErrorDialog(String message) {
    showErrorDialog(message, null);
  }

  protected final static void showErrorDialog(String message, Throwable e) {
    showErrorDialog(JikesRVMLauncherMessages.getString("JikesRVMLauncher.dialog.title"),
                    message, e);
  }

  protected final static void showErrorDialog(String title, String message, Throwable e) {
    if (e != null) {
      System.err.println("<<< showErrorDialog >>>");
      e.printStackTrace(System.err);
      System.err.println("<<<      DONE       >>>");
    }
    showErrorDialog(title, message, new Status(IStatus.ERROR, 
                                               JikesRVMLaunchingPlugin.getPluginId(), 
                                               0, message, e));
  }

  protected static void showErrorDialog(final String title, 
                                        final String message, 
                                        final IStatus error) {
    Display d = Display.getDefault();
    if (d != null) {
      d.syncExec(new Runnable() {
          public void run() {
            JikesRVMLaunchingPlugin.errorDialog(JikesRVMLaunchingPlugin.getActiveWorkbenchShell(), 
                                                title, message, error);
          }
        });
    } else {
      JikesRVMLaunchingPlugin.getDefault().getLog().log(error);
    }
  }
        
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

  public JikesRVMRunner(JikesRVMInstall install) {
    this.install = install;
  }

  public void run(VMRunnerConfiguration config, 
                  ILaunch launch, 
                  IProgressMonitor monitor) 
    throws CoreException
  {

    String location= getRVMLocation("");
    if ("".equals(location)) {
      String message= JikesRVMLauncherMessages.getString("JikesRVMLauncher.error.noHome");
      showErrorDialog(JikesRVMLauncherMessages.getString("JikesRVMLauncher.dialog.title"), 
                      message, new Status(IStatus.ERROR, JikesRVMLaunchingPlugin.getPluginId(), 
                                          0, message, null));
      return;
    }

    // Try to find an executable
    // If we're overriding the environment, then we run 'runrvm' otherwise
    // we'll run 'rvm' with the known environment
    File rvmDir = new File(location, "rvm");
    File binDir = new File(rvmDir, "bin");
    File rvm    = new File(binDir, install().overridingEnv() ? "runrvm" : "rvm");
    String program = rvm.getAbsolutePath();
                
    Vector arguments = new Vector();

    arguments.addElement(program);

    // Add the location of HOME, RVM_ROOT, and RVM_BUILD if we're
    // overriding these locations
    if (install().overridingEnv()) {
      arguments.add(install().getHome());
      arguments.add(install().getRvmRoot());
      arguments.add(install().getRvmBuild());
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
      IProcess process = DebugPlugin.getDefault().newProcess(launch, p, renderProcessLabel(cmdLine));
      process.setAttribute(JavaRuntime.ATTR_CMDLINE, renderCommandLine(cmdLine));
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
  }

  protected String[] linkToCurrentEnv(String[] envp) {
    //TODO
    return envp;
  }

  protected boolean shouldIncludeInPath(String path) {
    return true;
  }

  protected final String[] validateVMArguments(String[] args, JikesRVMInstall install) {
    final List result = new Vector();
    for (int i = 0; i < args.length; i++) {
      String arg = args[i];
      if (arg.equals("-verify")) continue;
      if (validateVMArgument(arg, result)) result.add(arg);
    }

    return (String[])result.toArray(new String[0]);
  }

  /**
   * Subclasses can override this to add extra validation.  By default
   * this returns <code>true</code>.
   * 
   * @param  arg    the argument to inspect
   * @param  result the resulting list of arguments
   * @return        <code>true</code> if it's ok, otherwise <code>false</code>
   */
  protected boolean validateVMArgument(String arg, List result) { return true; }

  protected File workingDir(VMRunnerConfiguration config) throws CoreException {
    String pwd = config.getWorkingDirectory();
    if (pwd == null) return null;
    File pwdir = new File(pwd);
    if (pwdir.isDirectory()) return pwdir;
    throw new CoreException(new Status(IStatus.ERROR, 
                                       JikesRVMLaunchingPlugin.getPluginId(), 
                                       IStatus.ERROR, 
                                       pwdir.getAbsolutePath() + " isn't a directory!", 
                                       null));
  }

  protected Process exec(String[] cmdLine, File workingDirectory) throws CoreException {
    Process p= null;
    try {
      if (workingDirectory == null) {
        p= Runtime.getRuntime().exec(cmdLine, null);
      } else {
        p= Runtime.getRuntime().exec(cmdLine, null, workingDirectory);
      }
    } catch (IOException e) {
      if (p != null) {
        p.destroy();
      }
      showErrorDialog(JikesRVMLauncherMessages.getString("JikesRVMRunner.Exception_starting_process_1"), e);
    } catch (NoSuchMethodError e) {
      //attempting launches on 1.2.* - no ability to set working directory
      
      IStatus status = 
        new Status(IStatus.ERROR, 
                   JikesRVMLaunchingPlugin.getPluginId(), 
                   IJavaLaunchConfigurationConstants.ERR_WORKING_DIRECTORY_NOT_SUPPORTED, 
                   JikesRVMLauncherMessages.getString
                   ("JikesRVMRunner.runtime_does_not_support_working_directory_2"), 
                   e);
      IStatusHandler handler = DebugPlugin.getDefault().getStatusHandler(status);
      
      if (handler != null) {
        Object result = handler.handleStatus(status, this);
        if (result instanceof Boolean && ((Boolean)result).booleanValue()) {
          p= exec(cmdLine, null);
        }
      }
    }
    return p;
  }     
}
