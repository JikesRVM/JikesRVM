/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
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

}

