import org.eclipse.debug.ui.ILaunchConfigurationTab;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.ui.ILaunchConfigurationDialog;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.SWT;

public class Jikes_RVM_Launch_Config_JRE_Tab
  implements ILaunchConfigurationTab
{
  public boolean canSave() {
    return true;
  }
  public boolean isValid(ILaunchConfiguration c) {
    return true;
  }
  private Control control;
  private Label l;		// same as control; convenience method
  
  public void createControl(Composite parent) {
    if (control != null)
      return;
    l = new Label(parent, SWT.HORIZONTAL | SWT.CENTER | SWT.WRAP);
    l.setText("A sample message for our Launch Config JRE Tab");
    
    control = l;
  }

  public void dispose() {
    l.dispose();
    l = null;
    control = null;
  }
  public void performApply(ILaunchConfigurationWorkingCopy config) {}
  public void setDefaults(ILaunchConfigurationWorkingCopy config) {}
  public void setLaunchConfigurationDialog(ILaunchConfigurationDialog config) {}
  public void initializeFrom(org.eclipse.debug.core.ILaunchConfiguration conf) {}
  public String getErrorMessage() { return null;}
  public String getMessage() { return null; };
  public void launched(ILaunch launch) {}
  public String getName() {
    return "Jikes RVM JRE Config";
  }
  public Image getImage() {
    return null;
  }
  public Control getControl() {
    return control;
  }
  
}
