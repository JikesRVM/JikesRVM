/*
 * (C) Copyright IBM Corp 2001,2002
 */
//$Id$
import com.ibm.JikesRVM.*;

/**
 * This interface is implemented by any class which
 * renders debugger output for the user
 * 
 *
 * @author Manu Sridharan 6/29/00
 */

public interface JDPCommandInterface
{

  public void readCommand(OsProcess proc);

  public String cmd();

  public String[] args();
  
  /**
   * write an object to the output
   * @param output     the output Object
   */
  public void writeOutput(Object output);

  /**
   * Write a command to this interface.  Note this
   * may be an empty method when it makes sense that
   * commands cannot be written to the interface.
   *
   * @param input the input command
   */
  public void writeCommand(String command);

}

