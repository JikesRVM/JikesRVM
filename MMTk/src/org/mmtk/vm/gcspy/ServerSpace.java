/*
 * (C) Copyright Richard Jones, 2003
 * Computing Laboratory, University of Kent at Canterbury
 * All rights reserved.
 */
package org.mmtk.vm.gcspy;

import org.mmtk.utility.gcspy.AbstractTile;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * VM-neutral stub file for the GCspy Space abstraction.
 *
 * Here, it largely to forward calls to the gcspy C library.
 *
 * $Id$
 *
 * @author <a href="http://www.ukc.ac.uk/people/staff/rej">Richard Jones</a>
 * @version $Revision$
 * @date $Date$
 */
public class ServerSpace implements  Uninterruptible {
  public ServerSpace(int id, 
	      String serverName, 
	      String driverName,
	      String title,
	      String blockInfo,
	      int tileNum,
	      String unused, 
	      boolean mainSpace    ) {}
  public void setTilename(int i, Address start, Address end) {}
  Address addStream(int id) { return Address.zero(); }
  Address getDriverAddress() { return Address.zero(); }
  public void resize(int size) {}
  public void startComm() {}
  public void stream(int id, int len) {}
  public void streamByteValue(byte value) {}
  public void streamShortValue(short value) {}
  public void streamIntValue(int value) {}
  public void streamEnd () {}
  public void summary (int id, int len) {}
  public void summaryValue (int val) {}
  public void summaryEnd () {}
  public void controlEnd (int tileNum, AbstractTile[] tiles) {}
  public void spaceInfo (Address info) {}
  public void endComm() {}
}
