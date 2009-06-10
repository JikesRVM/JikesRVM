#
#  This file is part of the Jikes RVM project (http://jikesrvm.org).
#
#  This file is licensed to You under the Eclipse Public License (EPL);
#  You may not use this file except in compliance with the License. You
#  may obtain a copy of the License at
#
#      http://www.opensource.org/licenses/eclipse-1.0.php
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

/MMTk\/doc\// { next }

# Ignore patches
/build\/test-runs\/local.\.properties/ {next}

# Ignore patches
/build\/components\/patches/ {next}

# Ignore Code under an alternative license
/libraryInterface\/GNUClasspath\/LGPL/ {next}
/libraryInterface\/GNUClasspath\/PD/ {next}

# Test results
/testing\/tests\/javalex\/qb1.lex.ref/ {next}
/testing\/tests\/perf-jbb2000\/SPECjbb./ {next}
/testing\/tests\/perf-jbb2005\/SPECjbb./ {next}
/testing\/tests\/perf-dacapo\/SPECjbb./ {next}
/testing\/tests\/SPECjbb2000\/SPECjbb./ {next}
/testing\/tests\/SPECjbb2005\/SPECjbb./ {next}
/testing\/tests\/pseudojbb\/pseudojbb/ {next}
/testing\/tests\/pseudojbb\/props/ {next}
/\.expected/ { next }
/testing\/tests\/basic\/src\/test\/org\/jikesrvm\/basic\/core\/serialization\/SerializationData\.dat/ {next}

# Users custom settings
/\.ant.properties/ { next }

# Eclipses project files
/\.classpath/ { next }
/\.project/ { next }

# IDEAs project files
/\.iml/ { next }

/COPYRIGHT\.txt/ { next }
/LICENSE\.txt/ { next }
/NEWS\.txt/ { next }
/README\.txt/ { next }

# Non-text media
/\.gif/ { next }
/\.ico/ { next }

# print everything else
{ print; }

