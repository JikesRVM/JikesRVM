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
import java.util.List;
import java.util.Vector;
import org.eclipse.core.runtime.*;
import org.eclipse.jdt.internal.ui.dialogs.*;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.*;
import org.eclipse.swt.*;
import org.eclipse.swt.events.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

import org.eclipse.jface.preference.IPreferenceStore;

/**
 * @author Jeffrey Palm
 */
public class JikesRVMChangeConfigurationDialog extends StatusDialog {

  public final static void changeConfiguration(Shell parent, JikesRVMInstall install, boolean useDefaults) {
    new JikesRVMChangeConfigurationDialog(parent, install, useDefaults).open();
  }

  public final static void changeConfiguration(JikesRVMInstall install, boolean useDefaults) {
    new JikesRVMChangeConfigurationDialog(install, useDefaults).open();
  }

  private final JikesRVMInstall install;
  protected final JikesRVMInstall install() {
    return install;
  }

  private final StringButtonDialogField rvmBuildField;
  private final StringButtonDialogField rvmRootField;
  private       Button useDefaultsButton;
  private       Button overrideDefaultsButton;

  //
  // Buttons and other stuff for the debugger types
  //
  private final List   debuggerTypesButtons = new Vector();
  private       Button commandLineDebuggerTypeButton;
  private       Button guiDebuggerTypeButton;

  private IStatus[] stati = new IStatus[2];

  private JikesRVMChangeConfigurationDialog(Shell parent, JikesRVMInstall install, boolean useDefaults) {
    super(parent);

    this.install = install;

    setTitle(JikesRVMLauncherMessages.getString("configureDialog.title"));

    JikesRVMDebug.d.bug("ctor install="+install+" parent="+parent);
    
    // RVM_ROOT field
    rvmRootField = new StringButtonDialogField(new IStringButtonAdapter() {
        public void changeControlPressed(DialogField field) {
          browseForRvmRoot();
        }
      });
    rvmRootField.setLabelText(JikesRVMLauncherMessages.getString("configureDialog.rvmRoot"));
    rvmRootField.setButtonLabel(JikesRVMLauncherMessages.getString("configureDialog.browse"));
    rvmRootField.setDialogFieldListener(new IDialogFieldListener() {
        public void dialogFieldChanged(DialogField field) {
          stati[0] = validateRvmRootLocation();
          updateStatusLine();
        }
      });

    // RVM_BUILD field
    rvmBuildField = new StringButtonDialogField(new IStringButtonAdapter() {
        public void changeControlPressed(DialogField field) {
          browseForRvmBuild();
        }
      });
    rvmBuildField.setLabelText(JikesRVMLauncherMessages.getString("configureDialog.rvmBuild"));
    rvmBuildField.setButtonLabel(JikesRVMLauncherMessages.getString("configureDialog.browse"));
    rvmBuildField.setDialogFieldListener(new IDialogFieldListener() {
        public void dialogFieldChanged(DialogField field) {
          stati[1] = validateRvmBuildLocation();
          updateStatusLine();
        }
      });

    if (useDefaults) {
      rvmRootField.setText(install.getRvmRoot() != null ? 
                           install.getRvmRoot() : 
                           nonNull(install.getDefaultRvmRoot()));
      rvmBuildField.setText(install.getRvmBuild() != null ? 
                            install.getRvmBuild() : 
                            nonNull(install.getDefaultRvmBuild()));
    } else {
      rvmRootField.setText(nonNull(install.getRvmRoot()));
      rvmBuildField.setText(nonNull(install.getRvmBuild()));
    }
  }

  protected Control createDialogArea(Composite ancestor) {
    Composite parent = new Composite(ancestor, SWT.NULL);
    GridLayout layout= new GridLayout();
    layout.numColumns= 3;
    //TODOlayout.minimumWidth = convertWidthInCharsToPixels(80);
    parent.setLayout(layout);

    GridData gd;

    gd = new GridData();
    gd.horizontalSpan= 3;
    gd.horizontalAlignment= gd.FILL;

    rvmRootField.doFillIntoGrid(parent, 3);
    rvmBuildField.doFillIntoGrid(parent, 3);

    // Defaults button
    useDefaultsButton = new Button(parent, SWT.PUSH);
    useDefaultsButton.setText(JikesRVMLauncherMessages.getString("configureDialog.useDefaults"));
    useDefaultsButton.addListener(SWT.Selection, new Listener() {
        public void handleEvent(Event evt) {
          useDefaults();
        }
      });
    gd = new GridData();
    gd.horizontalSpan = 3;
    gd.horizontalAlignment = SWT.END;
    useDefaultsButton.setLayoutData(gd);

    // Override defaults button
    overrideDefaultsButton = new Button(parent, SWT.CHECK);
    overrideDefaultsButton.setText(JikesRVMLauncherMessages.getString("configureDialog.overrideDefaults"));
    overrideDefaultsButton.addListener(SWT.Selection, new Listener() {
        public void handleEvent(Event evt) {
          overrideDefaults(overrideDefaultsButton.getSelection());
        }});
    gd = new GridData();
    gd.horizontalSpan = 3;
    gd.horizontalAlignment= SWT.END;
    overrideDefaultsButton.setLayoutData(gd);
    overrideDefaults(install().overridingEnv());

    // Debugger types
    gd = new GridData(GridData.HORIZONTAL_ALIGN_FILL);
    gd.horizontalSpan= 2;
    Label debuggerTypesLabel = new Label(parent, SWT.WRAP);
    debuggerTypesLabel.setText(JikesRVMLauncherMessages.getString("JikesRVMPreferencePage.debuggerTypes"));
    debuggerTypesLabel.setLayoutData(gd);
    int indent = convertWidthInCharsToPixels(1);
    SelectionListener debuggerTypesSelectionListener = new SelectionAdapter() {
        public void widgetSelected(SelectionEvent e) {
          if (e.widget == commandLineDebuggerTypeButton) {

          } else if (e.widget == guiDebuggerTypeButton) {
            
          }
        }
      };
    commandLineDebuggerTypeButton = 
      addRadioButton(parent,
                     JikesRVMLauncherMessages.getString("JikesRVMPreferencePage.commandLineDebugger"),
                     install.getDebugMode() == JikesRVMInstall.DebugMode.COMMAND_LINE,
                     indent,
                     debuggerTypesButtons);
    commandLineDebuggerTypeButton.addSelectionListener(debuggerTypesSelectionListener);
    guiDebuggerTypeButton = 
      addRadioButton(parent,
                     JikesRVMLauncherMessages.getString("JikesRVMPreferencePage.guiDebugger"),
                     install.getDebugMode() == JikesRVMInstall.DebugMode.GUI,
                     indent,
                     debuggerTypesButtons);
    guiDebuggerTypeButton.addSelectionListener(debuggerTypesSelectionListener);

    return parent;
  }

  private void overrideDefaults(boolean overrideDefaults) {
    install.setOverridingEnv(overrideDefaults);
    rvmRootField.setEnabled(overrideDefaults);
    rvmBuildField.setEnabled(overrideDefaults);
    useDefaultsButton.setEnabled(overrideDefaults);
    stati[0] = validateRvmRootLocation();
    stati[1] = validateRvmBuildLocation();
    updateStatusLine();
  }

  private JikesRVMChangeConfigurationDialog(JikesRVMInstall install, boolean useDefaults) {
    this(JikesRVMLaunchingPlugin.getDefault().getActiveWorkbenchShell(), install, useDefaults);
  }

  private void browseForRvmRoot() {
    DirectoryDialog dialog = new DirectoryDialog(getShell());
    dialog.setFilterPath(rvmRootField.getText());
    dialog.setMessage(JikesRVMLauncherMessages.getString("configureDialog.pickRvmRoot"));
    dialog.setText(JikesRVMLauncherMessages.getString("configureDialog.pickRvmRoot"));
    String newPath = dialog.open();
    if (newPath != null) rvmRootField.setText(newPath);
  }

  private void browseForRvmBuild() {
    DirectoryDialog dialog = new DirectoryDialog(getShell());
    dialog.setFilterPath(rvmBuildField.getText());
    dialog.setMessage(JikesRVMLauncherMessages.getString("configureDialog.pickRvmBuild"));
    dialog.setText(JikesRVMLauncherMessages.getString("configureDialog.pickRvmBuild"));
    String newPath = dialog.open();
    if (newPath != null) rvmBuildField.setText(newPath);
  }

  protected void updateStatusLine() {
    int currentSeverity= IStatus.OK;
    String currentMessage= "";
    for (int i= 0; i < stati.length; i++) {
      if (stati[i] != null) {
        boolean isBiggerSeverity = 
          stati[i].getSeverity() > currentSeverity;
        boolean isBetterErrorMessage = 
          stati[i].getSeverity() == currentSeverity &&
          (currentMessage == null || "".equals(currentMessage));
        if (isBiggerSeverity) {
          updateStatus(stati[i]);
          currentSeverity = stati[i].getSeverity();
        }
      }
    }
    if (currentSeverity == IStatus.OK) {
      updateStatus(new Status(IStatus.OK, install.getId(), 0, "", null));
    }
  }

  protected String nonNull(String str) {
    return str != null ? str : "";
  }

  private void useDefaults() {
    rvmRootField.setText(install.getDefaultRvmRoot());
    rvmBuildField.setText(install.getDefaultRvmBuild());
    updateStatus(new Status(IStatus.INFO, install.getId(), 0, 
                            JikesRVMLauncherMessages.getString("configureDialog.usingDefaults"), 
                            null));
  }

  private IStatus validateRvmRootLocation() {
    String rvmRootName = rvmRootField.getText();
    if (rvmRootName == null || "".equals(rvmRootName)) {
      return new Status(IStatus.ERROR, install.getId(), 0, "", null);
    }
    File f = new File(rvmRootName);
    if (!f.exists()) {
      return new Status(IStatus.ERROR, install.getId(), 0,
                        JikesRVMLauncherMessages.getString("configureDialog.nofileRvmRoot"), null);
    }
    if (!f.isDirectory()) {
      return new Status(IStatus.ERROR, install.getId(), 0,
                        JikesRVMLauncherMessages.getString("configureDialog.notADirRvmRoot"), null);
    }
    return null;
  }

  private IStatus validateRvmBuildLocation() {
    String rvmBuildName = rvmBuildField.getText();
    if (rvmBuildName == null || "".equals(rvmBuildName)) {
      return new Status(IStatus.ERROR, install.getId(), 0, "", null);
    }
    File f = new File(rvmBuildName);
    if (!f.exists()) {
      return new Status(IStatus.ERROR, install.getId(), 0,
                        JikesRVMLauncherMessages.getString("configureDialog.nofileRvmBuild"), null);
    }
    if (!f.isDirectory()) {
      return new Status(IStatus.ERROR, install.getId(), 0,
                        JikesRVMLauncherMessages.getString("configureDialog.notADirRvmBuild"), null);
    }
    return null;
  }

  protected void okPressed() {
    final boolean overridingEnv = false; //TODO
    synchronized (this) {

      // Check env variables
      if (overridingEnv) {
        if (stati[0] == null || stati[0].getCode() == IStatus.OK) {
          install.setRvmRoot(new File(rvmRootField.getText()).getAbsolutePath());
        }
        if (stati[1] == null || stati[1].getCode() == IStatus.OK) {
          install.setRvmBuild(new File(rvmBuildField.getText()).getAbsolutePath());
        }
      }
      install.setOverridingEnv(overridingEnv);

      // Check debugger type
      if (commandLineDebuggerTypeButton.getSelection()) {
        install.setDebugMode(JikesRVMInstall.DebugMode.COMMAND_LINE);
      } else {
        install.setDebugMode(JikesRVMInstall.DebugMode.GUI);
      }

      super.okPressed();
    }
  }

  public void create() {
    super.create();
    rvmRootField.setFocus();
  }

  /**
   * @see org.eclipse.jdt.internal.ui.preferences.NewJavaProjectPreferencePage.addRadioButton
   */
  private Button addRadioButton(Composite parent, 
                                String label, 
                                boolean selection,
                                int indent,
                                List radioButtons) { 
    GridData gd = new GridData(GridData.HORIZONTAL_ALIGN_FILL);
    gd.horizontalSpan= 2;
    gd.horizontalIndent= indent;
    
    Button button = new Button(parent, SWT.RADIO);
    button.setText(label);
    button.setLayoutData(gd);

    button.setSelection(selection);
    
    radioButtons.add(button);
    return button;
  }
}
