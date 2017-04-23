FROM openjdk:8

WORKDIR /home/la

ADD run.sh  run.sh
ADD src/main/resources  /home/la/resources
ADD target/classes  	/home/la/classes
ADD target/dependency 	/home/la/libs

RUN chmod 0500 run.sh 

ENV CP classes:resources

VOLUME [ "/home/la/data" ]

ENTRYPOINT [ "sh", "/home/la/run.sh" ]  
