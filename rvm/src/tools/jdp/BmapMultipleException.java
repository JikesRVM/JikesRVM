/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
/**
 * Exception for BootMap:  thrown when there are multiple matches in the BootMap 
 * for a name
 * @ see BootMap.findAddress
 * @author Ton Ngo
 */
class BmapMultipleException extends Exception
{
  public BmapMultipleException(String msg)  {
    super(msg);
  }
}
