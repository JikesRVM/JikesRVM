/*
 * (C) Copyright IBM Corp 2001,2002
 */
//$Id$
import com.ibm.JikesRVM.*;

/*
 * @author Manu Sridharan
 */
public class JDP_Frame implements java.io.Serializable
{
  /**
   * frame number
   */
  int number;

  /**
   * method name
   */
  String methodName;

  /**
   * Get class name corresponding to method.
   */
  public String getClassName() {
    return methodName.substring(0, methodName.lastIndexOf('.'));
  }

  /**
   * compiled method ID
   */
  int compiledMethodID;

  int fp;
  
  /**
   * instruction offset when this was
   * constructed
   */
  int ipOffset;

  /**
   * Actual address of instruction.
   */
  transient int ip;
  
  /**
   * whether this frame is valid
   */
  boolean valid;

  /**
   * message if frame is invalid
   */
  String invalidMessage;

  // This field is filled in by Debugger.fillInSourceLocationInformation().
  transient int lineNumber;

  private static final long serialVersionUID =  3944939733538353418L;
  public String toString()
  {
    
    return valid ? ("Frame " + number + ": " + methodName) : invalidMessage;
  }
}
