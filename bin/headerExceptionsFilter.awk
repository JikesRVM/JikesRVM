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

## Auxiliary AWK program to help out /bin/findDeviantFiles.  You should
## never run this directly; to discourage people from doing so, this is not an
## executable file.

#
# The following is a list of patterns to match files
# that are expempted from project-wide standards on
# header infromation.  This list should be kept as
# short as possible, and all exceptions need to be
# approved by the steering committee.
#
/\/rvm\/src-generated\/opt-burs\/jburg\// { next }

# Avoid reporting a gazillion bogus violations when
# this script is run from night-sanity-run
/results\// { next }
/dist\// { next }
/generated\// { next }
/target\// { next }
/doc\/api\// { next }

/doc\/userguide\// { next }
/MMTk\/doc\// { next }

/testing\/tests\/javalex\/qb1.lex.ref/ {next}
/testing\/tests\/SPECjbb2000\/SPECjbb./ {next}
/testing\/tests\/SPECjbb2005\/SPECjbb./ {next}
/testing\/tests\/pseudojbb\/pseudojbb/ {next}
/testing\/tests\/pseudojbb\/props/ {next}
/testing\/tests\/mauve\/mauve-jikesrvm/ {next}

# actually has the right headers, but utf8 characters break the checkers
/testing\/tests\/utf8\/src\/utf8test.java/ {next}

/tools\/bootImageRunner\/org_jikesrvm_VM_0005fProcess.h/ { next }
/tools\/bootImageWriter\/rvm.security/ { next }

/LICENSE/ { next }
/NEWS/ { next }

/.properties$/ { next }
/.properties\.sample$/ { next }
/README$/ { next }

#
# print everything else
#
{ print; }

