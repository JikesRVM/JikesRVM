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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.model.IDebugTarget;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.jdi.Bootstrap;
import org.eclipse.jdt.debug.core.JDIDebugModel;
import org.eclipse.jdt.launching.IVMInstall;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jdt.launching.VMRunnerConfiguration;
import org.eclipse.jdt.launching.VMRunnerResult;

import com.sun.jdi.VirtualMachine;
import com.sun.jdi.connect.AttachingConnector;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.IllegalConnectorArgumentsException;
import com.sun.jdi.connect.Connector.IntegerArgument;

public class JikesRVMDebugVMRunner extends JikesRVMRunner {
	
  protected AttachingConnector getConnector() {
    List connectors= Bootstrap.virtualMachineManager().attachingConnectors();
    for (int i= 0; i < connectors.size(); i++) {
      AttachingConnector c= (AttachingConnector)connectors.get(i);
      if ("com.sun.jdi.SocketAttach".equals(c.name())) //$NON-NLS-1$
	return c;
    }
    return null;
  }
  public JikesRVMDebugVMRunner(IVMInstall vmInstance) {
    super(vmInstance);
  }
  public VMRunnerResult run(VMRunnerConfiguration config) {
    int port= SocketUtil.findUnusedLocalPort("localhost", 5000, 15000); //$NON-NLS-1$
    int proxyPort= SocketUtil.findUnusedLocalPort("localhost", 5000, 15000); //$NON-NLS-1$

    if (port == -1 || proxyPort == -1) {
      String message= JikesRVMLauncherMessages.getString("JikesRVMLauncher.error.noPort"); //$NON-NLS-1$
      String title= JikesRVMLauncherMessages.getString("JikesRVMLauncher.dialog.title"); //$NON-NLS-1$
      showErrorDialog(title, message, new Status(IStatus.ERROR, JikesRVMLaunchingPlugin.getPluginId(), 0, message, null));
      return null;
    }
    String location= getRVMLocation(""); //$NON-NLS-1$
    if ("".equals(location)) { //$NON-NLS-1$
      String message= JikesRVMLauncherMessages.getString("JikesRVMLauncher.error.noHome"); //$NON-NLS-1$
      String title= JikesRVMLauncherMessages.getString("JikesRVMLauncher.dialog.title"); //$NON-NLS-1$
      showErrorDialog(title, message, new Status(IStatus.ERROR, JikesRVMLaunchingPlugin.getPluginId(), 0, message, null));
      return null;
    }		
    String program= location+File.separator+"bin"+File.separator+"j9"; //$NON-NLS-2$ //$NON-NLS-1$
    String proxy= location+File.separator+"bin"+File.separator+"j9proxy"; //$NON-NLS-2$ //$NON-NLS-1$
		
    List arguments= new ArrayList();
		
    arguments.add(program);
		
    String[] bootCP= config.getBootClassPath();
    if (bootCP.length > 0) {
      arguments.add("-bp:"+convertClassPath(bootCP)); //$NON-NLS-1$
    } 
		
    String[] cp= config.getClassPath();
    if (cp.length > 0) {
      arguments.add("-classpath"); //$NON-NLS-1$
      arguments.add(convertClassPath(cp));
    }
    addArguments(config.getVMArguments(), arguments);
		
    arguments.add("-debug:"+port); //$NON-NLS-1$
    arguments.add(config.getClassToLaunch());
    addArguments(config.getProgramArguments(), arguments);
    String[] cmdLine= new String[arguments.size()];
    arguments.toArray(cmdLine);
		
    String proxyCmd= proxy+" localhost:"+port+" "+ proxyPort; //$NON-NLS-1$ //$NON-NLS-2$

    Process p= null;
    Process p2= null;
    try {
      p= Runtime.getRuntime().exec(cmdLine);
      p2= Runtime.getRuntime().exec(proxyCmd);
			
    } catch (IOException e) {
      if (p != null)
	p.destroy();
      if (p2 != null)
	p2.destroy();
      String title= JikesRVMLauncherMessages.getString("JikesRVMLauncher.dialog.title"); //$NON-NLS-1$
      String message= JikesRVMLauncherMessages.getString("JikesRVMLauncher.error.startVM"); //$NON-NLS-1$
      showErrorDialog(title, message, new Status(IStatus.ERROR, 
						 JikesRVMLaunchingPlugin.getPluginId(), 0, 
						 JikesRVMLauncherMessages.getString("JikesRVMLauncher.status.startVM"), e)); //$NON-NLS-1$
      return null;
    }
    IProcess process1= DebugPlugin.getDefault().newProcess(p, renderProcessLabel(cmdLine));
    IProcess process2= DebugPlugin.getDefault().newProcess(p2, renderProcessLabel(new String[] { "j9Proxy" })); //$NON-NLS-1$
    process1.setAttribute(JavaRuntime.ATTR_CMDLINE, renderCommandLine(cmdLine));
    process2.setAttribute(JavaRuntime.ATTR_CMDLINE, proxyCmd);
				
    AttachingConnector connector= getConnector();
    if (connector == null) {
      p.destroy();
      p2.destroy();
      String title= JikesRVMLauncherMessages.getString("JikesRVMLauncher.dialog.title"); //$NON-NLS-1$
      String message= JikesRVMLauncherMessages.getString("JikesRVMLauncher.error.noConnector"); //$NON-NLS-1$
      showErrorDialog(title, message, new Status(IStatus.ERROR, 
						 JikesRVMLaunchingPlugin.getPluginId(), 0, 
						 message, null));
      return null;
    } 
    Map map= connector.defaultArguments();
    specifyArguments(map, proxyPort);
    boolean retry= false;
    do {
      try {
	VirtualMachine vm;
	try {
	  vm= connector.attach(map);
	} catch (IOException exc) {
	  vm= connector.attach(map);
	}
	setTimeout(vm);
	IDebugTarget debugTarget= JDIDebugModel.newDebugTarget(vm, renderDebugTarget(config.getClassToLaunch(), port), process1, true, false);
	return new VMRunnerResult(debugTarget, new IProcess[] { process1, process2 });
      } catch (IOException e) {
	retry= askRetry(JikesRVMLauncherMessages.getString("JikesRVMLauncher.dialog.title"), 
			JikesRVMLauncherMessages.getString("JikesRVMLauncher.error.noConnect")); //$NON-NLS-2$ //$NON-NLS-1$
      } catch (IllegalConnectorArgumentsException e) {
	retry= false;
	showErrorDialog(JikesRVMLauncherMessages.getString("JikesRVMLauncher.dialog.title"), 
			JikesRVMLauncherMessages.getString("JikesRVMLauncher.error.connectException"), 
			new Status(IStatus.ERROR, JikesRVMLaunchingPlugin.getPluginId(), 0, 
				   JikesRVMLauncherMessages.getString("JikesRVMLauncher.status.connectException"), e)); //$NON-NLS-3$ //$NON-NLS-2$ //$NON-NLS-1$
      }
    } while (retry);
    p.destroy();
    p2.destroy();
    return null;
  }
  protected void specifyArguments(Map map, int portNumber)  {
    // XXX: Revisit: allows us to put a quote (") around the classpath
    Connector.IntegerArgument port= (Connector.IntegerArgument) map.get("port"); //$NON-NLS-1$
    port.setValue(portNumber);
  }
}
