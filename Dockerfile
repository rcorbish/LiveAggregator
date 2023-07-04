FROM openjdk:19

WORKDIR /home/la

ADD run.sh  				run.sh
ADD src/main/resources/*	/home/la/resources/
ADD build/libs/*			/home/la/build/libs/
ADD libs/*					/home/la/libs/

RUN chmod 0500 run.sh 

ENV CP resources

VOLUME [ "/home/la/data" ]

ENTRYPOINT [ "sh", "/home/la/run.sh" ]  
