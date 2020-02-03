FROM jboss/wildfly

COPY target/datasafe-wildfly-test-1.0-SNAPSHOT.war /opt/jboss/wildfly/standalone/deployments/
