/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/*
 * Code to convert floating point number to ascii.
 * @author unattributed
 */

public class VM_FloatingPoint {

  /**
   * Covert a double to a string.
   */
  public static String toString(double value) {  

    String str = null;

    VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
    byte [] buffer = new byte[256];
    int rc = VM.sysCallAD(bootRecord.sysSprintfIP,
                          VM_Magic.objectAsAddress(buffer).toInt(),		
                          value);
    str =  new String(buffer, 0, rc);

    if (str == null) return null;

    int indexPlus = str.indexOf('+');
    int indexDot = str.indexOf('.');
    int indexE = str.indexOf('E');

    if (-1 == indexDot)
      // insert a ".0" before the exponent, or at the end
      if (-1 == indexE)
        return str.concat(".0");
      else
        if (-1 == indexPlus)
          return str.substring(0, indexE).concat(".0".concat(str.substring(indexE)));
        else
          return str.substring(0, indexE).concat(".0E".concat(str.substring(indexPlus+1)));
    else
      if (-1 == indexPlus)
        return str;
      else
        // strip the plus before the exponent
        return str.substring(0, indexPlus).concat(str.substring(indexPlus+1));
  }

}
