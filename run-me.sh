#!/bin/bash

echo "You need to have Docker and Maven on local machine, check that fact if something fails"
mvn clean package
docker build . -t ds-wildfly:1.0.0
docker run -p 8080:8080 ds-wildfly:1.0.0