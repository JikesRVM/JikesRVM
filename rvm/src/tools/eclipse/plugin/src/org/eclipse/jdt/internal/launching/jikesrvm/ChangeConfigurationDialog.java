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

import java.io.*;
import org.eclipse.core.runtime.*;
import org.eclipse.jdt.internal.ui.dialogs.*;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.*;
import org.eclipse.jdt.internal.ui.wizards.swt.*;
import org.eclipse.swt.*;
import org.eclipse.swt.widgets.*;

/**
 * @author Jeffrey Palm
 */
public class ChangeConfigurationDialog extends StatusDialog {

  public final static void changeConfiguration(Shell parent, JikesRVMInstall install, boolean useDefaults) {
    new ChangeConfigurationDialog(parent, install, useDefaults).open();
  }

  public final static void changeConfiguration(JikesRVMInstall install, boolean useDefaults) {
    new ChangeConfigurationDialog(install, useDefaults).open();
  }

  private final JikesRVMInstall install;

  private final StringButtonDialogField rvmBuildField;
  private final StringButtonDialogField rvmRootField;
  private /*final*/ Button useDefaultsButton;
  private /*final*/ Button overrideDefaultsButton;

  private IStatus[] stati = new IStatus[2];

  private ChangeConfigurationDialog(Shell parent, JikesRVMInstall install, boolean useDefaults) {
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
    MGridLayout layout= new MGridLayout();
    layout.numColumns= 3;
    layout.minimumWidth = convertWidthInCharsToPixels(80);
    parent.setLayout(layout);

    MGridData gd;

    gd = new MGridData();
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
    gd = new MGridData();
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
    gd = new MGridData();
    gd.horizontalSpan = 3;
    gd.horizontalAlignment= SWT.END;
    overrideDefaultsButton.setLayoutData(gd);
    overrideDefaults(install.overridingEnv());

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

  private ChangeConfigurationDialog(JikesRVMInstall install, boolean useDefaults) {
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
      if (overridingEnv) {
	if (stati[0] == null || stati[0].getCode() == IStatus.OK) {
	  install.setRvmRoot(new File(rvmRootField.getText()).getAbsolutePath());
	}
	if (stati[1] == null || stati[1].getCode() == IStatus.OK) {
	  install.setRvmBuild(new File(rvmBuildField.getText()).getAbsolutePath());
	}
      }
      install.setOverridingEnv(overridingEnv);
      super.okPressed();
    }
  }

  public void create() {
    super.create();
    rvmRootField.setFocus();
  }
}
