package org.jikesrvm.tools.ant;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.taskdefs.MacroDef;
import org.apache.tools.ant.taskdefs.MacroInstance;
import org.apache.tools.ant.taskdefs.condition.Condition;
import org.apache.tools.ant.taskdefs.condition.ConditionBase;

/**
 * The if task makes it easier to performs some tasks conditionally.
 * It contains a nested condition and associated sequential task.
 *
 * @author Peter Donald
 */
public class IfTask
    extends ConditionBase {
  private MacroDef macroDef;

  public MacroDef.NestedSequential createSequential() {
    macroDef = new MacroDef();
    macroDef.setProject(getProject());
    return macroDef.createSequential();
  }

  public void execute() {
    validate();

    final Condition condition = (Condition) getConditions().nextElement();
    if (condition.eval()) {
      final MacroInstance i = new MacroInstance();
      i.setProject(getProject());
      i.setMacroDef(macroDef);
      i.execute();
    }
  }

  private void validate() {
    if (1 != countConditions()) throw new BuildException("Must specify exactly one condition.");
    if (null == macroDef) throw new BuildException("Must specify a sequential task to execute.");
  }
}
