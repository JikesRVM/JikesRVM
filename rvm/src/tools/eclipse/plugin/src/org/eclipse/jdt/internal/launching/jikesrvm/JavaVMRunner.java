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
package org.eclipse.jdt.internal.launching.jikesrvm;

import java.io.File;
import java.text.DateFormat;
import java.text.MessageFormat;
import java.util.Date;
import java.util.List;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.jdt.launching.IVMInstall;
import org.eclipse.jdt.launching.IVMRunner;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Display;

import com.sun.jdi.VirtualMachine;

/**
 * @author Jeffrey Palm
 */
public abstract class JavaVMRunner implements IVMRunner {

  protected IVMInstall fVMInstance;

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
    File location= fVMInstance.getInstallLocation();
    JikesRVMDebug.d.bug("getRVMLocation location="+location);
    if (location == null) {
      return dflt;
    }
    return location.getAbsolutePath();
  }

  public JavaVMRunner(IVMInstall vmInstance) {
    fVMInstance= vmInstance;
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

  protected void setTimeout(VirtualMachine vm) {		
    if (vm instanceof 	org.eclipse.jdi.VirtualMachine) {
      int timeout= fVMInstance.getDebuggerTimeout();
      org.eclipse.jdi.VirtualMachine vm2= (org.eclipse.jdi.VirtualMachine)vm;
      vm2.setRequestTimeout(timeout);
    }
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
}
