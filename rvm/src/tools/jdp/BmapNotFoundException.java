/*
 * (C) Copyright IBM Corp. 2001
 */
/**
 * Exception for BootMap:  thrown when there is no match in the BootMap for a name
 * @author Ton Ngo
 */
class BmapNotFoundException extends Exception
{
  public BmapNotFoundException(String msg)  {
    super(msg);
  }
}

