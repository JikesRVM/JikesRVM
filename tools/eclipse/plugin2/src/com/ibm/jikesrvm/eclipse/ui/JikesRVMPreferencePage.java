/*
 * ===========================================================================
 * Eclipse/Jikes
 *
 * Copyright © IBM Corp., 2002, 2003.
 *
 * $Source$
 * $Revision$
 * $Date$
 * $Author$
 * $Id$
 */
package com.ibm.jikesrvm.eclipse.ui;

import com.ibm.jikesrvm.eclipse.jdt.launching.*;

import java.util.Collection;
import java.util.Iterator;

import org.eclipse.jface.preference.ColorFieldEditor;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.List;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

/*
 * @author Jeffrey Palm
 */
public class JikesRVMPreferencePage 
  extends PreferencePage 
  implements IWorkbenchPreferencePage {

  /** THe list of all the installed RVMs */
  private List rvmList;

  /**
   * @see PreferencePage#createContents(Composite)
   */
  protected Control createContents(Composite parent) {
    JikesRVMDebug.d.todo("createContents parent="+parent);

    Composite table = new Composite(parent, SWT.NULL);

    //Create a data that takes up the extra space in the dialog .
    GridData data = new GridData(GridData.FILL_HORIZONTAL);
    data.grabExcessHorizontalSpace = true;
    table.setLayoutData(data);
    
    GridLayout layout = new GridLayout();
    table.setLayout(layout);
    new Label(table, SWT.NONE);

    rvmList = new List(table, SWT.BORDER | SWT.SINGLE);
    rvmList.setItems(vmStrings());
    rvmList.addMouseListener(new MouseAdapter() {
        public void mouseDoubleClick(MouseEvent e) {
          editSelectedRVM();
        }
      });
    
    // Create a data that takes up the extra space in the dialog and spans both columns.
    data = new GridData(GridData.FILL_BOTH);
    rvmList.setLayoutData(data);

    Composite buttonComposite = new Composite(table,SWT.NULL);
    
    GridLayout buttonLayout = new GridLayout();
    buttonLayout.numColumns = 2;
    buttonComposite.setLayout(buttonLayout);
    
    // Create a data that takes up the extra space in the dialog and spans both columns.
    data = new GridData(GridData.FILL_BOTH | GridData.VERTICAL_ALIGN_BEGINNING);
    buttonComposite.setLayoutData(data);

    Button editButton = new Button(buttonComposite, SWT.PUSH | SWT.CENTER);
    editButton.setText(JikesRVMLauncherMessages.getString("JikesRVMPreferencePage.editButton"));
    editButton.addSelectionListener(new SelectionAdapter() {
        public void widgetSelected(SelectionEvent event) {
          editSelectedRVM();
        }
      });

    // A label with the build info
    if (JikesRVMDebug.d.debug()) {
      Label buildInfoLabel = new Label(table, SWT.NONE);
      buildInfoLabel.setText("Last built: " + BuildInfo.TIME);
      data = new GridData(GridData.FILL_BOTH | GridData.VERTICAL_ALIGN_BEGINNING);
      buildInfoLabel.setLayoutData(data);
    }

    // An option to change the debugger
    if (JikesRVMDebug.d.debug()) {
      Label debuggerLabel = new Label(table, SWT.NONE);
      debuggerLabel.setText("Debugger program:");
      data = new GridData();
      debuggerLabel.setLayoutData(data);
      final Text debuggerText = new Text(table, SWT.SINGLE);
      debuggerText.setText(JikesRVMLaunchingPlugin.getDefault().debuggerProgram());
      data = new GridData();
      debuggerText.setLayoutData(data);
      Button debuggerSetButton = new Button(table, SWT.PUSH);
      debuggerSetButton.setText("Set Debugger Command");
      debuggerSetButton.addSelectionListener(new SelectionAdapter() {
          public void widgetSelected(SelectionEvent event) {
            JikesRVMLaunchingPlugin.getDefault().setDebuggerProgram(debuggerText.getText());
          }
        });
    }
    
    return table;
  }
  
  /** 
   * Pops up a change configuration dialog for the RVM
   * selected in {@link rvmList}.  Any errors are sent to the error
   * message of this page.
   */
  private void editSelectedRVM() {
    JikesRVMDebug.d.todo("editSelectedRVM");
    String[] names = rvmList.getSelection();
    if (names == null) {
      setErrorMessage(JikesRVMLauncherMessages.getString("JikesRVMPreferencePage.error.nullSelection"));
      return;
    }
    if (names.length != 1) {
      setErrorMessage(JikesRVMLauncherMessages.getString("JikesRVMPreferencePage.error.multipleSelection"));
      return;
    }
    String name = names[0];
    JikesRVMInstall rvm = JikesRVMLaunchingPlugin.getDefault().getInstall(name);
    if (name == null) {
      setErrorMessage(JikesRVMLauncherMessages.getString("JikesRVMPreferencePage.error.nullInstall"));
      return;
    }
    JikesRVMChangeConfigurationDialog.changeConfiguration(rvm, true);
  }

  /**
   * Returns an array of the known RVMs
   * @return an array of the known RVMs
   */
  private String[] vmStrings() {
    Object[] rvms = JikesRVMLaunchingPlugin.getDefault().getRVMs().toArray();
    String[] result = new String[rvms.length];
    for (int i = 0; i < rvms.length; i++) {
      result[i] = ((JikesRVMInstall)rvms[i]).toString();
    }
    return result;
  }

  /**
   * @see IWorkbenchPreferencePage#init(IWorkbench)
   */
  public void init(IWorkbench workbench) {
    setPreferenceStore(JikesRVMLaunchingPlugin.getDefault().getPreferenceStore());
  }
}
