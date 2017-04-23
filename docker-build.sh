#!/bin/sh


DPID=$( docker -H tcp://mercury.rac.local:2375 ps | grep live-aggregator | cut -c1-16 )

if [ ! -z ${DPID} ] 
then
	docker -H tcp://mercury.rac.local:2375 kill ${DPID}
fi

docker -H tcp://mercury.rac.local:2375 build -t rcorbish/live-aggregator .

