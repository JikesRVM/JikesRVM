/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
/*
 * @author Manu Sridharan
 */
import java.util.Vector;

public class JDP_Locals implements java.io.Serializable
{

  /**
   * name to display
   */
  String displayName;

  /**
   * name of current method
   */
  String methodName;
  
  /**
   * local variables
   */
  Vector vars = new Vector(); // JDP_Field
  
  private static final long serialVersionUID =  -7468743512016338671L;

  public String toString()
  {
    return displayName;
  }
}
