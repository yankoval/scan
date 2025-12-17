#!/usr/bin/env sh

#
# Copyright 2015 the original author or authors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options to this script.
DEFAULT_JVM_OPTS=""

APP_NAME="Gradle"
APP_BASE_NAME=`basename "$0"`

# Use the maximum available, or set MAX_FD != -1 to use that value.
MAX_FD="maximum"

warn () {
    echo "$*"
}

die () {
    echo
    echo "ERROR: $*"
    echo
    exit 1
}

# OS specific support (must be 'true' or 'false').
cygwin=false
msys=false
darwin=false
nonstop=false
case "`uname`" in
  CYGWIN* )
    cygwin=true
    ;;
  Darwin* )
    darwin=true
    ;;
  MINGW* )
    msys=true
    ;;
  NONSTOP* )
    nonstop=true
    ;;
esac

# Attempt to set APP_HOME
# Resolve links: $0 may be a link
PRG="$0"
# Need this for relative symlinks.
while [ -h "$PRG" ] ; do
    ls=`ls -ld "$PRG"`
    link=`expr "$ls" : '.*-> \(.*\)$'`
    if expr "$link" : '/.*' > /dev/null; then
        PRG="$link"
    else
        PRG=`dirname "$PRG"`"/$link"
    fi
done
SAVED="`pwd`"
cd "`dirname \"$PRG\"`/" >/dev/null
APP_HOME="`pwd -P`"
cd "$SAVED" >/dev/null

# For Cygwin, ensure paths are in UNIX format before anything is touched.
if ${cygwin} ; then
    [ -n "$APP_HOME" ] &&
        APP_HOME=`cygpath --unix "$APP_HOME"`
    [ -n "$JAVA_HOME" ] &&
        JAVA_HOME=`cygpath --unix "$JAVA_HOME"`
    [ -n "$CLASSPATH" ] &&
        CLASSPATH=`cygpath --path --unix "$CLASSPATH"`
fi

# Attempt to set JAVA_HOME if it's not already set.
if [ -z "$JAVA_HOME" ] ; then
    if ${darwin} ; then
        [ -x '/usr/libexec/java_home' ] && JAVA_HOME=`/usr/libexec/java_home`
    fi
fi
if [ -z "$JAVA_HOME" ] ; then
    # In Cygwin, the Windows JAVA_HOME is translated to a Cygwin path.
    if ${cygwin} && [ -n "$ कॉमन फाइल्स" ] && [ -x "/cygdrive/c/Program Files/Java/jdk1.8.0_221/bin/java.exe" ] ; then
        JAVA_HOME="/cygdrive/c/Program Files/Java/jdk1.8.0_221"
    else
        # For other operating systems, there is no good way to find the JAVA_HOME.
        # We will just assume that 'java' is on the PATH.
        :
    fi
fi
if [ -n "$JAVA_HOME" ] ; then
    if [ ! -d "$JAVA_HOME" ] ; then
        die "JAVA_HOME is not valid: $JAVA_HOME"
    fi
    if [ ! -x "$JAVA_HOME/bin/java" ] ; then
        die "JAVA_HOME is not valid: $JAVA_HOME"
    fi
fi

# Add the '-d64' option if available.
if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ] && `"$JAVA_HOME/bin/java" -d64 -version > /dev/null 2>&1` ; then
    DEFAULT_JVM_OPTS="$DEFAULT_JVM_OPTS -d64"
fi

# Set the GRADLE_HOME.
GRADLE_HOME="$APP_HOME/gradle"
if [ ! -f "$GRADLE_HOME/lib/gradle-launcher.jar" ] ; then
    # We are probably running from the distribution directory.
    GRADLE_HOME="$APP_HOME"
fi

# Set the CLASSPATH.
if [ -n "$CLASSPATH" ] ; then
    CLASSPATH="$CLASSPATH:$GRADLE_HOME/lib/*"
else
    CLASSPATH="$GRADLE_HOME/lib/*"
fi

# Set the JVM options.
if [ -n "$JAVA_OPTS" ] ; then
    GRADLE_OPTS="$GRADLE_OPTS $JAVA_OPTS"
fi
if [ -n "$DEFAULT_JVM_OPTS" ] ; then
    GRADLE_OPTS="$GRADLE_OPTS $DEFAULT_JVM_OPTS"
fi

# Execute the application.
exec "$JAVA_HOME/bin/java" ${GRADLE_OPTS} -cp "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
