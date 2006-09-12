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

import java.io.*;
import java.net.*;
import java.util.*;
import org.eclipse.core.runtime.*;
import org.eclipse.debug.core.*;
import org.eclipse.debug.core.model.*;
import org.eclipse.jdi.Bootstrap;
import org.eclipse.jdt.debug.core.JDIDebugModel;
import org.eclipse.jdt.internal.debug.core.JDIDebugPlugin;
import org.eclipse.jdt.launching.*;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.ui.dialogs.ListSelectionDialog;
import org.eclipse.ui.model.WorkbenchContentProvider;
import org.eclipse.ui.model.WorkbenchLabelProvider;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import com.sun.jdi.*;
import com.sun.jdi.connect.*;

/**
 * @author Jeffrey Palm
 */
public class JikesRVMDebugger extends JikesRVMRunner {

  public JikesRVMDebugger(JikesRVMInstall install) {
    super(install);
  }

  public final static int LAUNCH_MODE = 0;
  public final static int LISTEN_MODE = 1;
  public final static int ATTACH_MODE = 2;

  public void run(VMRunnerConfiguration config, 
                  ILaunch launch, 
                  IProgressMonitor monitor) 
    throws CoreException 
  {

    final int connectMode = askConnectMode();

    final String host = "localhost";

    int port = SocketUtil.findUnusedLocalPort(host, 5000, 15000);
    if (port == -1) {
      showErrorDialog(JikesRVMLauncherMessages.getString("JikesRVMLauncher.error.noPort") + 
                      "port=" + port);
    }

    if (monitor.isCanceled()) {
      System.err.println(" >>> monitor=" + monitor + " isCanceled()");
      return;
    }
    
    // Command line arguments
    final List arguments = new ArrayList();

    // Try to find an executable
    // If we're overriding the environment, then we run 'runrvm' otherwise
    // we'll run 'rvm' with the known environment
    String location = getRVMLocation("");
    File    rvmDir = new File(location, "rvm");
    File    binDir = new File(rvmDir, "bin");
    File       jdp = new File(binDir, JikesRVMLaunchingPlugin.getDefault().debuggerProgram());
    String program = jdp.getAbsolutePath();

    arguments.add(program);

    // First see if we're using the command line or gui debugger
    if (install().getDebugMode() == JikesRVMInstall.DebugMode.COMMAND_LINE) {
      
    } else {
      //
      // now we're using the GUI debugger... best of luck!
      //

      // If we're listening add the address
      if (connectMode == LISTEN_MODE) {
        arguments.add("-jdplisten" + host + ":" + port);
      } else if (connectMode == ATTACH_MODE) {
        arguments.add("-jdpattach" + port);
      } else if (connectMode == LAUNCH_MODE) {
        arguments.add("-jdplistenlocalhost:52944"); //XXX
      }
    }

    // Bootclasspath
    String[] bootclasspath = config.getBootClassPath();
    if (bootclasspath != null) {
      arguments.add("-X:bootclasspath:" + 
                    (bootclasspath.length == 0  ? "" : convertClassPath(bootclasspath)));
    }

    // Classpath
    String[] classpath = config.getClassPath();
    if (classpath != null && classpath.length > 0) {
      arguments.add("-classpath");
      arguments.add(convertClassPath(classpath));
    }

    // Ensure that the crap we get from config.getVMArguments aren't crap
    addArguments(validateVMArguments(config.getVMArguments(), install()), arguments);

    // JDP arguments (TODO)

    arguments.add(config.getClassToLaunch());
    addArguments(config.getProgramArguments(), arguments);

    final String[] cmdLine = new String[arguments.size()];
    arguments.toArray(cmdLine);

    // Again, find out which debugger we're using
    if (install().getDebugMode() == JikesRVMInstall.DebugMode.COMMAND_LINE) {
      //
      // To use the command line debugger we need to just launch the
      // program
      //
      launchCommandLineDebugger(config, launch, monitor, cmdLine, port, program);
    } else {
      //
      // Want to wait forever for things
      //
      JDIDebugModel.getPreferences().setValue(JDIDebugModel.PREF_REQUEST_TIMEOUT,
                                              Integer.MAX_VALUE);
      //
      // To use the GUI debugger we need to launch something
      //
      switch (connectMode) {
      case LAUNCH_MODE: launch(config, launch, monitor, cmdLine, port, program); break;
      case LISTEN_MODE: listen(config, launch, monitor, cmdLine, port, program); break;
      case ATTACH_MODE: attach(config, launch, monitor, cmdLine, port, program); break;
      }
    }
  }

  private void launchCommandLineDebugger(VMRunnerConfiguration config, 
                                         ILaunch launch, 
                                         IProgressMonitor monitor,
                                         String[] cmdLine,
                                         int port,
                                         String program)
                                         
    throws CoreException
  {
    File workingDir = workingDir(config);

    //
    // Just launch the debugger program
    //
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

  private void launch(VMRunnerConfiguration config, 
                      ILaunch launch, 
                      IProgressMonitor monitor,
                      String[] cmdLine,
                      int port,
                      String program) 
    throws CoreException
  {
    File workingDir = workingDir(config);

    LaunchingConnector connector = getLaunchingConnector();

    if (connector == null) {
      showErrorDialog(JikesRVMLauncherMessages.getString("JikesRVMLauncher.error.noConnector"));
      return;
    }

    // Take care of the default agruments
    Map map = connector.defaultArguments();
    JikesRVMDebug.d.bug(" >>> Launching defaultArguments(before)=" + map);
    Connector.StringArgument mainArg = (Connector.StringArgument)map.get("main");
    mainArg.setValue(config.getClassToLaunch());
    Connector.StringArgument vmexecArg = (Connector.StringArgument)map.get("vmexec");
    vmexecArg.setValue(program);

    JikesRVMDebug.d.bug(" >>> Launching defaultArguments(after) =" + map);
    

    Process p = null;
    try {

        if (monitor.isCanceled()) {
          System.err.println(" >>> monitor=" + monitor + " isCanceled()");
          return;
        }

        try {
          p = Runtime.getRuntime().exec(cmdLine, null, workingDir);
        } catch (IOException e) {
          if (p != null) p.destroy();
          showErrorDialog(JikesRVMLauncherMessages.getString("JikesRVMLauncher.error.startVM"), e);
          return;
        }

        JikesRVMDebug.d.bug(" >>> Launching p=" + p);

        IProcess process = DebugPlugin.getDefault().newProcess(launch, p, renderProcessLabel(cmdLine));
        process.setAttribute(JavaRuntime.ATTR_CMDLINE, renderCommandLine(cmdLine));

        VirtualMachine vm = connector.launch(map);

        JikesRVMDebug.d.bug("*** vm=" + vm + " ***");

        JDIDebugModel.newDebugTarget(launch, 
                                     vm, 
                                     renderDebugTarget(config.getClassToLaunch(), port), 
                                     process, true, false);
    } catch (VMStartException e) {
      showErrorDialog(JikesRVMLauncherMessages.getString("JikesRVMLauncher.error.startVM"), e);
    } catch (IOException e) {
      showErrorDialog(JikesRVMLauncherMessages.getString("JikesRVMDebugger.error.noConnect1"), e);
    } catch (IllegalConnectorArgumentsException e) {
      showErrorDialog(JikesRVMLauncherMessages.getString("JikesRVMDebugger.error.noConnect2"), e);
    }
    if (p != null) {
      JikesRVMDebug.d.bug("*** destroying process=" + p + " ***");
      p.destroy();
    }
  }

  private void attach(VMRunnerConfiguration config, 
                      ILaunch launch, 
                      IProgressMonitor monitor,
                      String[] cmdLine,
                      int port,
                      String program) 
    throws CoreException 
  {

    File workingDir = workingDir(config);

    Process p= null;
    Process p2= null;
    try {
      p  = Runtime.getRuntime().exec(cmdLine, null, workingDir);
      //p2 = Runtime.getRuntime().exec(proxyCmd, null, workingDir);
      
    } catch (IOException e) {
      if (p != null) p.destroy();
      //if (p2 != null) p2.destroy();
      showErrorDialog(JikesRVMLauncherMessages.getString("JikesRVMLauncher.error.startVM"), e);
      return;
    }
    IProcess process1 = DebugPlugin.getDefault().newProcess(launch, p, renderProcessLabel(cmdLine));
    //IProcess process2 = DebugPlugin.getDefault().newProcess(p2, renderProcessLabel(new String[] { "j9Proxy" })); //$NON-NLS-1$
    process1.setAttribute(JavaRuntime.ATTR_CMDLINE, renderCommandLine(cmdLine));
    //process2.setAttribute(JavaRuntime.ATTR_CMDLINE, proxyCmd);
    
    AttachingConnector connector = getAttachingConnector();
    if (connector == null) {
      p.destroy();
      //p2.destroy();
      showErrorDialog(JikesRVMLauncherMessages.getString("JikesRVMLauncher.error.noConnector"));
      return;
    } 
    Map map = connector.defaultArguments();
    // XXX: Revisit: allows us to put a quote (") around the classpath
    Connector.IntegerArgument portArg = (Connector.IntegerArgument) map.get("port"); //$NON-NLS-1$
    portArg.setValue(port);

    boolean retry = false;
    do {
      try {
        VirtualMachine vm;
        try {
          vm = connector.attach(map);
        } catch (IOException exc) {
          vm = connector.attach(map);
        }
        setTimeout(vm);
        IDebugTarget debugTarget = 
          JDIDebugModel.newDebugTarget(launch, 
                                       vm, 
                                       renderDebugTarget(config.getClassToLaunch(), port), 
                                       process1, true, false);
        return;
        //return new VMRunnerResult(debugTarget, new IProcess[] { process1, process2 });
      } catch (IOException e) {
        retry = askRetry(JikesRVMLauncherMessages.getString("JikesRVMLauncher.error.noConnect1"));
      } catch (IllegalConnectorArgumentsException e) {
        retry = false;
        showErrorDialog(JikesRVMLauncherMessages.getString("JikesRVMDebugger.error.noConnect2"), e);
      }
    } while (retry);
    p.destroy();
    //p2.destroy();
  }
  
  private void listen(VMRunnerConfiguration config, 
                      ILaunch launch, 
                      IProgressMonitor monitor,
                      String[] cmdLine,
                      int port,
                      String program) 
    throws CoreException 
  {
    
    File workingDir = workingDir(config);

    ListeningConnector connector = getListeningConnector();

    if (connector == null) {
      showErrorDialog(JikesRVMLauncherMessages.getString("JikesRVMLauncher.error.noConnector"));
      return;
    }
    
    // Take care of the default arguments
    Map map = connector.defaultArguments();
    Connector.IntegerArgument portArg = (Connector.IntegerArgument)map.get("port");
    portArg.setValue(port);
    Connector.IntegerArgument timeoutArg = (Connector.IntegerArgument)map.get("timeout");

    if (timeoutArg != null) {
      int timeout = JavaRuntime.getPreferences().getInt(JavaRuntime.PREF_CONNECT_TIMEOUT);
      timeoutArg.setValue(timeout);
    }

    Process p = null;

    try {
      try {

        if (monitor.isCanceled()) {
          System.err.println(" >>> monitor=" + monitor + " isCanceled()");
          return;
        }
      
        connector.startListening(map);
        p = exec(cmdLine, workingDir);                          
        if (p == null) return;
      
        if (monitor.isCanceled()) {
          System.err.println(" >>> monitor=" + monitor + " isCanceled()");
          p.destroy();
          return;
        }                               
                                
        IProcess process = DebugPlugin.getDefault().newProcess(launch, p, renderProcessLabel(cmdLine));
        process.setAttribute(JavaRuntime.ATTR_CMDLINE, renderCommandLine(cmdLine));
                                
        boolean retry = false;
        do  {
          try {
            VirtualMachine vm = connector.accept(map);

            JikesRVMDebug.d.bug("*** vm=" + vm + " ***");

            setPluginTimeout();

            JDIDebugModel.newDebugTarget(launch, 
                                         vm, 
                                         renderDebugTarget(config.getClassToLaunch(), port), 
                                         process, true, false);
            setPluginTimeout();
            return;
          } catch (InterruptedIOException e) {
            JikesRVMDebug.d.handle(e);
            String errorMessage = process.getStreamsProxy().getErrorStreamMonitor().getContents();
            if (errorMessage.length() == 0) {
              errorMessage = process.getStreamsProxy().getOutputStreamMonitor().getContents();
            }
            if (errorMessage.length() != 0) {
              showErrorDialog(errorMessage, e);
            } else {
              // timeout, consult status handler if there is one
              IStatus status = new Status(IStatus.ERROR, 
                                          JikesRVMLaunchingPlugin.getPluginId(),
                                          IJavaLaunchConfigurationConstants.ERR_VM_CONNECT_TIMEOUT, 
                                          "", e);
              IStatusHandler handler = DebugPlugin.getDefault().getStatusHandler(status);                                                       
              retry = false;
              if (handler == null) {
                // if there is no handler, throw the exception
                throw new CoreException(status);
              } else {
                Object result = handler.handleStatus(status, this);
                if (result instanceof Boolean) {
                  retry = ((Boolean)result).booleanValue();
                }
              } 
            }
          }
        } while (retry);
      } finally {
        JikesRVMDebug.d.bug("*** stopListening connector=" + connector + " ***");
        connector.stopListening(map);
      }
    } catch (IOException e) {
      JikesRVMDebug.d.handle(e);
      showErrorDialog(JikesRVMLauncherMessages.getString("JikesRVMDebugger.error.noConnect1"), e);
    } catch (IllegalConnectorArgumentsException e) {
      JikesRVMDebug.d.handle(e);
      showErrorDialog(JikesRVMLauncherMessages.getString("JikesRVMDebugger.error.noConnect2"), e);
    }
    if (p != null) {
      JikesRVMDebug.d.bug("*** destorying process=" + p + " ***");
      p.destroy();
    }
  }

  protected final void setPluginTimeout() {
    setPluginTimeout(Integer.MAX_VALUE);
  }

  protected final void setPluginTimeout(int value) {
    JDIDebugModel.getPreferences().setValue(JDIDebugModel.PREF_REQUEST_TIMEOUT,
                                            Integer.MAX_VALUE);
    JDIDebugPlugin.getDefault().getPluginPreferences().setDefault(JDIDebugModel.PREF_REQUEST_TIMEOUT,
                                                                  Integer.MAX_VALUE);
  }

  protected LaunchingConnector getLaunchingConnector() {
    List connectors = Bootstrap.virtualMachineManager().launchingConnectors();
    for (int i= 0; i < connectors.size(); i++) {
      LaunchingConnector c = (LaunchingConnector)connectors.get(i);
      if ("com.sun.jdi.CommandLineLaunch".equals(c.name()))
        return c;
    }
    return null;
  }
  
  protected ListeningConnector getListeningConnector() {
    List connectors = Bootstrap.virtualMachineManager().listeningConnectors();
    for (int i= 0; i < connectors.size(); i++) {
      ListeningConnector c = (ListeningConnector)connectors.get(i);
      if ("com.sun.jdi.SocketListen".equals(c.name()))
        return c;
    }
    return null;
  }
  
  protected AttachingConnector getAttachingConnector() {
    List connectors= Bootstrap.virtualMachineManager().attachingConnectors();
    for (int i= 0; i < connectors.size(); i++) {
      AttachingConnector c= (AttachingConnector)connectors.get(i);
      if ("com.sun.jdi.SocketAttach".equals(c.name()))
        return c;
    }
    return null;
  }
  
  private final static class SocketUtil {
    private static int findUnusedLocalPort(String host, int searchFrom, int searchTo) {
      for (int i= 0; i < 10; i++) {
        int port = getRandomPort(searchFrom, searchTo);
        try {
          new Socket(host, port);
        } catch (ConnectException e) {
            return port;
        } catch (IOException e) {
        }
      }
      return -1;
    }
    private static final Random fgRandom = new Random(System.currentTimeMillis());
    private static int getRandomPort(int low, int high) {
      return (int)(fgRandom.nextFloat()*(high-low))+low;
    }
  }

  protected boolean askRetry(String message) {
    return askRetry(JikesRVMLauncherMessages.getString("JikesRVMLauncher.dialog.title"), 
                    message);
  }

  protected boolean askRetry(final String title, final String message) {
    final boolean[] result= new boolean[1];
    Display.getDefault().syncExec(new Runnable() {
        public void run() {
          result[0]= (MessageDialog.openQuestion(JikesRVMLaunchingPlugin.getActiveWorkbenchShell(), 
                                                 title, 
                                                 message));
        }
      });
    return result[0];
  }

  protected void setTimeout(VirtualMachine vm) {                
    if (vm instanceof org.eclipse.jdi.VirtualMachine) {
      int timeout = 30000; //fVMInstance.getDebuggerTimeout();
      org.eclipse.jdi.VirtualMachine vm2= (org.eclipse.jdi.VirtualMachine)vm;
      vm2.setRequestTimeout(timeout);
    }
  }

  protected int askConnectMode() {
    
    Object[] input = new Object[] {
      "Launching Connector",
      "Listening Connector",
      "Attaching Connector",
    };
//      ListSelectionDialog dialog = new ListSelectionDialog(JikesRVMLaunchingPlugin.getActiveWorkbenchShell(),
//                                                       input,
//                                                       new WorkbenchContentProvider(),
//                                                       new WorkbenchLabelProvider(),
//                                                       "Choose a selector");
//      dialog.open();
//      Object result = dialog.getResult();
//      System.err.println("result="+result);
    //return ATTACH_MODE;
    return LISTEN_MODE;
    //return LAUNCH_MODE;
  }

  protected boolean validateVMArgument(String arg, List result) {

    //XXX Hijack the -Xrunjdwp argument to add -jdplisten if we're
    //XXX we're in launching mode.  An example argument is
    //XXX
    //XXX   -Xrunjdwp:transport=dt_socket,address=localhost:52929,server=n,suspend=y
    //XXX
    if (arg.startsWith("-Xrunjdwp:")) {
      for (StringTokenizer t = new StringTokenizer(arg.substring(10), ",", false); t.hasMoreTokens();) {
        String str = t.nextToken().trim();
        int ieq = str.indexOf("=");
        final String key;
        final String val;
        if (ieq == -1) {
          key = str;
          val = "";
        } else {
          key = str.substring(0,ieq);
          val = str.substring(ieq+1);
        }
        if (key.equals("address")) {
          result.add("-jdplisten" + val);
          return false;
        }
      }
    }
    
    // We have no other restrictions
    return true; 
  }
}
