/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
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
   * whether this frame is valid
   */
  boolean valid;

  /**
   * message if frame is invalid
   */
  String invalidMessage;

  private static final long serialVersionUID =  3944939733538353418L;
  public String toString()
  {
    
    return valid ? ("Frame " + number + ": " + methodName) : invalidMessage;
  }
}
