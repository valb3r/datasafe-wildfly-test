FROM frekele/wildfly:11.0.0.Final-jdk8
COPY target/datasafe-wildfly-test-1.0-SNAPSHOT.war /opt/wildfly/standalone/deployments/
