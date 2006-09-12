/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Collection of useful utilities.
 *
 * @author Peter F. Sweeney
 * @date 3/5/2003
 */

import java.io.*;
import java.lang.Math.*;

public class Utilities 
{

  public static final int debug = 0;

  // end of file indicator
  static public int    EOF_int    = -1;
  static public String EOF_String = "-1";
  // io exception
  static public int    EIO_int    = -2;
  static public String EIO_String = "-2";

  /**
   * Add commas to a long.
   */
  static public String format_long(long counter) {
    /*PFS debug*/ System.out.println("format_long("+counter+")");
    String value = new Long(counter).toString();
    int length = value.length();
    if (length <= 3) 
      return value;

    int segments  = length/3;
    int remainder = length - (segments*3);
    String constructed = "";
    if (remainder > 0) {
      constructed += value.substring(0,remainder)+",";
    }
    int start = remainder;
    for (int i=0; i<segments; i++) {
      constructed += value.substring(start, start+3);
      start += 3;
      if (i<(segments-1)) {
        constructed += ",";
      }
    }
    return constructed;
  }

  /**
   * Given a double format it with only 3 decimal places
   * @param value double to be formatted
   */
  static public double twoDigitDouble(double value)
  {
    return (Math.round(value*100))/100.0;
  }
  /**
   * Given a double format it with only 3 decimal places
   * @param value double to be formatted
   */
  static public double threeDigitDouble(double value)
  {
    return (Math.round(value*1000))/1000.0;
  }

  /*
   * Open trace file
   *
   * @param trace_filename name of trace file to be opened
   */
  static public DataInputStream openDataInputStream(String trace_filename)
  {
    if(debug>=2)System.out.println("openDataInputStream("+trace_filename+")");
    DataInputStream input_file = null;
    // open the trace file
    try {
      input_file = new DataInputStream(new FileInputStream(trace_filename));
    } catch (FileNotFoundException e) {
      System.out.println("***Utilities.openDataInputStream("+trace_filename+"): FileNotFound exception!***");
      System.exit(1);
    } catch (SecurityException e) {
      System.out.println("***Utilities.openDataInputStream("+trace_filename+"): Security exception!***");
      System.exit(1);
    }
    return input_file;
  }

  /**
   * Read an integer from data input stream
   * @param stream   data input stream
   */
  static public int getIntFromDataInputStream(DataInputStream stream)
  {
    byte b[] = new byte[4];
    int value = 0;
    try {
      stream.readFully(b);
    } catch (EOFException e) {
      if (debug>=3) 
        System.out.println("***Utilities.readInt() EOF exception!***");
      return EOF_int;
    }catch (IOException e) {
      System.out.println("***Utilities.readInt() IO exception!***");
      new Exception().printStackTrace();
      System.exit(1);
    }
    if(debug>=5) {System.out.print("Utilities.readInt("+b[0]+" "+b[1]+" "+b[2]+" "+b[3]+") ");}
    if (TraceHeader.isLittleEndian()) {
      value = 
        (((int)(b[3] << 24)) & 0xFF000000) + 
        (((int)(b[2] << 16)) & 0x00FF0000) + 
        (((int)(b[1] <<  8)) & 0x0000FF00) + 
        (((int)(b[0]      )) & 0x000000FF);
      if(debug>=5){System.out.print(value+" little-endian\n");}
    } else {
      value = 
        (((int)(b[0] << 24)) & 0xFF000000) + 
        (((int)(b[1] << 16)) & 0x00FF0000) + 
        (((int)(b[2] <<  8)) & 0x0000FF00) + 
        (((int)(b[3]      )) & 0x000000FF);
      if(debug>=5) {System.out.print(value+" big-endian (default)");}
    }
    if(debug>=5) {System.out.print("\n");}
    return value;
  }
  /**
   * Read long from data input stream.
   *
   * @param dis data input stream
   */
  static public long getLongFromDataInputStream(DataInputStream dis)
  {
    long value = 0;
    byte b[] = new byte[8];
    try {
      dis.readFully(b);
    } catch (EOFException e) {
      if (debug>=3) 
        System.out.println("***Utilities.getLongFromDataInputStream() EOF exception!***");
      return EOF_int;
    }catch (IOException e) {
      System.out.println("***Utilities.getLongFromDataInputStream() IO exception!***");
      new Exception().printStackTrace();
      System.exit(1);
    }

    if(debug>=4) {System.out.print("Utilities.getLongFromDataInputStream("+
                                   b[0]+" "+b[1]+" "+b[2]+" "+b[3]+" "+b[4]+" "+b[5]+" "+b[6]+" "+b[7]+") ");}
    if (TraceHeader.isLittleEndian()) {
      value = (long)
        (((long)(b[7]) << 56)&0xFF00000000000000L) + 
        (((long)(b[6]) << 48)&0x00FF000000000000L) +
        (((long)(b[5]) << 40)&0x0000FF0000000000L) + 
        (((long)(b[4]) << 32)&0x000000FF00000000L) + 
        (((long)(b[3]) << 24)&0x00000000FF000000L) + 
        (((long)(b[2]) << 16)&0x0000000000FF0000L) + 
        (((long)(b[1]) <<  8)&0x000000000000FF00L) + 
        (((long)(b[0])      )&0x00000000000000FFL);
      if(debug>=4) {System.out.print(Long.toString(value)+" little-endian\n");}
    } else {
      value = (long)
        (((long)(b[0]) << 56)&0xFF00000000000000L) + 
        (((long)(b[1]) << 48)&0x00FF000000000000L) +
        (((long)(b[2]) << 40)&0x0000FF0000000000L) + 
        (((long)(b[3]) << 32)&0x000000FF00000000L) + 
        (((long)(b[4]) << 24)&0x00000000FF000000L) + 
        (((long)(b[5]) << 16)&0x0000000000FF0000L) + 
        (((long)(b[6]) <<  8)&0x000000000000FF00L) + 
        (((long)(b[7])      )&0x00000000000000FFL);
      if(debug>=4) {System.out.print(value+" big-endian (default)\n");}
    }
    
    return value;
  }
  /*
   * Read a string from DataInputStream.
   * Expected format of string: 
   *   int length
   *   byte[length]
   * @param stream   open DataInputStream
   */
  static public String getStringFromDataInputStream(DataInputStream input_file) 
  {
    int length = 0;
    byte[] b_array = null;
    try {
      length = getIntFromDataInputStream(input_file);
      b_array = new byte[length];
      for (int i=0; i<length; i++) {
        b_array[i] = input_file.readByte();
      }
    } catch (EOFException e) {
      if(debug>=3)
        System.out.println("***Utilities.getStringFromDataInputStream() EOF exception!***");
      return EOF_String;
    }catch (IOException e) {
      System.out.println("***Utilities.getStringFromDataInputStream() IO exception!***");
      new Exception().printStackTrace();
      System.exit(1);
    }
    
    String string = new String(b_array);
    if(debug>=5)System.out.println("Utilities.getString() returns "+string+" with length "+length);
    return string;
  }
  
  /*
   * Add an element to a int array.
   *
   * @param array array of ints
   * @param value value to be added
   * @param index where to add value in array
   * @return array must return array if it is grown!
   */
  static public int[] addIntArrayElement(int[] array, int value, int index) 
  {
    if(debug>=5)System.out.println("addIntArrayElement: index "+index+" has value "+value+
                                   " length "+array.length);
    if (index >= array.length) {
      array = growIntArray(array, Math.max(array.length<<1,index+1));
    }
    array[index] = value;

    return array;
  }
  /**
   * Expand an array of ints.
   *
   * @param array array of doubles
   * @param newLength that array is to be expanded to
   */ 
  static private int[] growIntArray(int[] array, int newLength) 
  {
    if(debug>=5)System.out.println("growIntArray("+newLength+")");
    if (array.length >= newLength) {
      System.err.println("***Utilities.growIntArray() called with array.length "+array.length+
                         " >= length "+newLength+"!***");
      new Exception().printStackTrace();
      System.exit(1);
    }
    int[] newarray = new int[newLength];
    for (int i = 0, n = array.length; i < n; ++i)
      newarray[i] = array[i];
    return newarray;
  }
}
