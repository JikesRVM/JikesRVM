/**
 ** GCSpyStream
 **
 ** (C) Copyright Richard Jones, 2003
 ** Computing Laboratory, University of Kent at Canterbury
 ** All rights reserved.
 **/

package org.mmtk.vm.gcspy;

import com.ibm.JikesRVM.VM_SysCall;

import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Uninterruptible;

/**
 * Stream
 *
 * Set up a GCspy Stream, by forwarding calls to gcspy C library
 *VM_SysCall
 * @author <a href="www.ukc.ac.uk/people/staff/rej">Richard Jones</a>
 * @version $Revision$
 * @date $Date$
 */

public class Stream 
  implements  VM_Uninterruptible {
  public final static String Id = "$Id$";
    
  private VM_Address stream_;	// address of c stream, gcspy_gc_stream_t *stream
  private int min;
  private int max;
  
  /**
   * Construct a new GCspy stream
   * @param driver	   The Space driver that owns this Stream
   * @param id	    	   The stream ID
   * @param dataType	   The stream's data type, one of BYTE_TYPE, SHORT_TYPE or INT_TYPE
   * @param name	   The name of the stream (e.g. "Used space")
   * @param minValue	   The minimum value for any item in this stream. 
   *                       Values less than this will be represented as "minValue-"
   * @param maxValue	   The maximum value for any item in this stream. 
   *                       Values greater than this will be represented as "maxValue+"
   * @param zeroValue	   The zero value for this stream
   * @param defaultValue   The default value for this stream
   * @param stringPre      A string to prefix values (e.g. "used: ")
   * @param stringPost	   A string to suffix values (e.g. " bytes.")
   * @param presentation   How a stream value is to be presented.    
   * @param paintStyle 	   How the value is to be painted.   
   * @param maxStreamIndex The maximum index for the stream. TODO Is this used?
   * @param colour 	   The default colour for tiles of this stream
   */
  public Stream (ServerSpace driver,
          int id,	
	  int dataType,
	  String name,
	  int minValue,		
	  int maxValue,
	  int zeroValue,
	  int defaultValue,
	  String stringPre,
	  String stringPost,
 	  int presentation,
	  int paintStyle,
	  int maxStreamIndex,
	  Color colour) {

    stream_ = driver.addStream(id);
    min = minValue;
    max = maxValue;

    VM_Address tmpName = Util.getBytes(name);
    VM_Address tmpPre =  Util.getBytes(stringPre);
    VM_Address tmpPost = Util.getBytes(stringPost);
    
    VM_SysCall.gcspyStreamInit(stream_, id, dataType, tmpName,
		               minValue, maxValue, zeroValue, defaultValue,
		               tmpPre, tmpPost, presentation, paintStyle,
		               maxStreamIndex, colour.getRed(), colour.getGreen(), colour.getBlue());
  }

  /**
   * Return the minimum legal value for this stream
   *
   * @return the minimum value
   */
  public int getMinValue() { return min; }

  /**
   * Return the maximum legal value for this stream
   *
   * @return the maximum value
   */
  public int getMaxValue() { return max; }
}

