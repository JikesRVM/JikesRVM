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

# The following is a list of patterns to match files
# that are expempted from project-wide standards on
# header infromation.  This list should be kept as
# short as possible, and all exceptions need to be
# approved by the steering committee.
#
/\/rvm\/src-generated\/opt-burs\/jburg\// { next }

/doc\/userguide\// { next }
/MMTk\/doc\// { next }

/testing\/tests\/javalex\/qb1.lex.ref/ {next}
/testing\/tests\/SPECjbb2000\/SPECjbb./ {next}
/testing\/tests\/SPECjbb2005\/SPECjbb./ {next}
/testing\/tests\/pseudojbb\/pseudojbb/ {next}
/testing\/tests\/pseudojbb\/props/ {next}

/\.classpath/ { next }
/\.project/ { next }

/COPYRIGHT.txt/ { next }
/LICENSE.txt/ { next }
/NEWS.txt/ { next }
/README.txt/ { next }

# print everything else
{ print; }

