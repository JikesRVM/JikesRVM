#
#  This file is part of the Jikes RVM project (http://jikesrvm.org).
#
#  This file is licensed to You under the Common Public License (CPL);
#  You may not use this file except in compliance with the License. You
#  may obtain a copy of the License at
#
#      http://www.opensource.org/licenses/cpl1.0.php
#
#  See the COPYRIGHT.txt file distributed with this work for information
#  regarding copyright ownership.
#

#
# GCSpy system messages
#
/^GCspy.startGCspyServer/ { next }
/^GCspy server on port [0-9]*/ { next }
/^GCspy safepoint for event [0-9]*/ { next }

#
# GC system messages
#
/^GC Warning:/ { next }
/^GC Message:/ { next }
/^\[GC [0-9]*/ { next }
/^\[Forced GC\]*/ { next }
/^\[End [0-9]*.[0-9]* ms\]*/ { next }
#
# adaptive system messages
#
/AOS: In non-adaptive mode; controller thread exiting./ { next }

#
# print everything else
#
/.*/
