/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
import com.ibm.JikesRVM.*;
/**
 * a wrapper around class information
 * for network debugger
 * @author Manu Sridharan
 */

public class JDP_Field implements java.io.Serializable
{
  /**
   * name of field
   */
  String name;

  /**
   * type of field
   */
  String type;

  /**
   * true if field is not a primitive
   * or an array
   */
  boolean classType;

  /**
   * true if field is of array type
   */
  boolean arrayType;

  /**
   * length of array
   * only valid if array type
   */
  int arrayLength;
  
  /**
   * address of field
   */
  int address;

  /**
   * value of field, valid only
   * for primitive or array types
   */
  String value;

  /**
   * for serialization purposes
   */
  private static final long serialVersionUID = 2742213187266658048L;

  public String toString()
  {
    String ret;
    if (classType && address != 0)
    {
      // expandable
      ret = type + " " + name;
      if (arrayType)
      {
        // add array dimensions
	if (arrayLength == 0)
	{
	  ret = ret + "[empty]";
	}
	else
	{
	  ret = ret + "[0:" + (arrayLength-1) + "]";
	}
      }
    }
    else
    {
      ret = type + " " + name + " = " + value;
    }
    ret += " @" + intAsHexString(address);
    return ret;
  }

  /**
   * convert an int to a hex string, taken from VM class
   * @param number number to be converted
   * @return String hex representation of number
   */
  private static String intAsHexString(int number)
  {
    char[] buf   = new char[8];
    int    index = 8;
    while (--index > -1)
    {
      int digit = number & 0x0000000f;
      buf[index] = digit <= 9 ? (char)('0' + digit) : (char)('a' + digit - 10);
      number >>= 4;
    }
    return new String(buf);
  }


}
   

 
