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

##############################################################################
##
##  Gradle start up script for UN*X
##
##############################################################################

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

APP_NAME="Gradle"
APP_BASE_NAME=`basename "$0"`

# Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options to this script.
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'

# Use the maximum available, or set MAX_FD != -1 to use that value.
MAX_FD="maximum"

warn () {
    echo "$*"
}

die () {
    echo
    echo "$*"
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

# For Cygwin, ensure paths are in UNIX format before anything is touched.
if ${cygwin} ; then
    [ -n "$JAVA_HOME" ] && JAVA_HOME=`cygpath --unix "$JAVA_HOME"`
fi

# Attempt to find java
if [ -z "$JAVA_HOME" ] ; then
    # try to use JDK7 if available
    if [ -d /usr/lib/jvm/java-7-oracle/bin ] ; then
        JAVA_HOME=/usr/lib/jvm/java-7-oracle
    elif [ -d /usr/lib/jvm/java-7-openjdk/bin ] ; then
        JAVA_HOME=/usr/lib/jvm/java-7-openjdk
    elif [ -d /usr/lib/jvm/java-7-openjdk-amd64/bin ] ; then
        JAVA_HOME=/usr/lib/jvm/java-7-openjdk-amd64
    else
        # fall back to the JAVA_HOME from the environment
        JAVA_HOME=`type -p java`
        JAVA_HOME=`dirname \`dirname \`readlink -f $JAVA_HOME\ \`\ \``
    fi
fi
if [ -z "$JAVA_HOME" ] ; then
    die "ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.

Please set the JAVA_HOME variable in your environment to match the
location of your Java installation."
fi

# Set JAVA_EXE
JAVA_EXE="$JAVA_HOME/bin/java"

#
# For non-cygwin, we need to re-resolve the JAVA_HOME variable to deal with symlinks
#
if [ "${cygwin}" = "false" ] ; then
    if [ -h "$JAVA_EXE" ] ; then
        while [ -h "$JAVA_EXE" ] ; do
            ls=`ls -ld "$JAVA_EXE"`
            link=`expr "$ls" : '.*-> \(.*\)$'`
            if expr "$link" : '/.*' > /dev/null; then
                JAVA_EXE="$link"
            else
                JAVA_EXE=`dirname "$JAVA_EXE"`"/$link"
            fi
        done
        JAVA_HOME=`dirname \`dirname $JAVA_EXE\ \``
        JAVA_EXE="$JAVA_HOME/bin/java"
    fi
fi

if [ ! -x "$JAVA_EXE" ] ; then
  die "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME

Please set the JAVA_HOME variable in your environment to match the
location of your Java installation."
fi

# Increase the maximum number of open files
if [ "${cygwin}" = "false" ] && [ "${darwin}" = "false" ] && [ "${nonstop}" = "false" ] ; then
    MAX_FD_LIMIT=`ulimit -H -n`
    if [ $? -eq 0 ] ; then
        if [ "$MAX_FD" = "maximum" -o "$MAX_FD" = "max" ] ; then
            # use the system max
            MAX_FD="$MAX_FD_LIMIT"
        fi
        ulimit -n $MAX_FD
        if [ $? -ne 0 ] ; then
            warn "Could not set maximum file descriptor limit: $MAX_FD"
        fi
    else
        warn "Could not query maximum file descriptor limit: $MAX_FD_LIMIT"
    fi
fi

# For Darwin, add options to specify how the application appears in the dock
if ${darwin}; then
    GRADLE_OPTS="$GRADLE_OPTS \"-Xdock:name=$APP_NAME\" \"-Xdock:icon=$APP_HOME/media/gradle.icns\""
fi

# For Cygwin, switch paths to Windows format before running java
if ${cygwin} ; then
    APP_HOME=`cygpath --path --windows "$APP_HOME"`
    JAVA_HOME=`cygpath --path --windows "$JAVA_HOME"`
    CLASSPATH=`cygpath --path --windows "$CLASSPATH"`
    CYGWIN_OPTS=`cygpath --path --windows "$CYGWIN_OPTS"`
fi

# Split up the JVM options only if the variable is not quoted
DEFAULT_JVM_OPTS=`echo $DEFAULT_JVM_OPTS | tr "\'" "\""`
JAVA_OPTS=`echo $JAVA_OPTS | tr "\'" "\""`
GRADLE_OPTS=`echo $GRADLE_OPTS | tr "\'" "\""`

# Collect all arguments for the java command, following the shell quoting and substitution rules
eval set -- "$DEFAULT_JVM_OPTS" "$JAVA_OPTS" "$GRADLE_OPTS" "-Dorg.gradle.appname=$APP_BASE_NAME" -classpath "\"$APP_HOME/gradle/wrapper/gradle-wrapper.jar\"" org.gradle.wrapper.GradleWrapperMain "$@"

# Pass all arguments to the java command
exec "$JAVA_EXE" "$@"
