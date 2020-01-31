FROM jboss/wildfly
COPY target/datasafe-wildfly-test-1.0-SNAPSHOT/ /opt/jboss/wildfly/standalone/deployments/
