#! /bin/bash
# Select all, copy to temporary shell script in temporary directory, then run it.

mvn --version
if [ $? -ne 0 ] ; then
	echo "Please install maven."
	exit -1
fi
echo ""
if [ ! -f ~/Documents/particle-tokens.txt ] ; then
	echo "Please create ~/Documents/particle-tokens.txt containing one or more lines with <token><tab><accountName>"
	exit -2
fi
repo=monitor-particle-with-webserver
if [ -d ~/Documents/Github/${repo} ] ; then
	echo "Please rename ~/Documents/Github/${repo}"
	exit -3
fi

if [[ ! ${HOSTNAME} ]] ; then
	echo "No environment variable for HOSTNAME"
	exit -4
fi
set -x
# Edit the following line from chris-keith-gmail-com to your (modified) particle account name.
particleAccountName=chris-keith-gmail-com
mkdir -p ~/Documents/tmp/${HOSTNAME}/${particleAccountName}				; if [ $? -ne 0 ] ; then exit -6 ; fi
mkdir -p ~/Documents/Github												; if [ $? -ne 0 ] ; then exit -6 ; fi
cd ~/Documents/Github													; if [ $? -ne 0 ] ; then exit -6 ; fi
rm -rf JParticle/														; if [ $? -ne 0 ] ; then exit -6 ; fi
git clone https://github.com/Walter-Stroebel/JParticle.git				; if [ $? -ne 0 ] ; then exit -6 ; fi

git clone https://github.com/chrisxkeith/${repo}.git	                ; if [ $? -ne 0 ] ; then exit -6 ; fi
cp -R JParticle/src/* ${repo}/src/					                    ; if [ $? -ne 0 ] ; then exit -6 ; fi
rm -rf JParticle/									                    ; if [ $? -ne 0 ] ; then exit -6 ; fi
cd ~/Documents/Github/${repo}	  					                    ; if [ $? -ne 0 ] ; then exit -6 ; fi
mkdir -p src/main/resources/com/ckkeith/monitor/	  					; if [ $? -ne 0 ] ; then exit -6 ; fi

cp src/main/resources/com/ckkeith/monitor/runparams.json ~/Documents/tmp/${HOSTNAME}/${particleAccountName}/
echo "Edit the runparams.json copy as necessary."
echo "Open src/main/java/com/example/restservice/RestServiceApplication.java"
echo "Run from within VSC."
echo "Verify using http://localhost:8080/sensordata"
