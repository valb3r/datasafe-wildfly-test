mkdir ROOT_BUCKET

docker build -t wildfly-datasafe . && docker run -it --rm -p 8080:8080 -v ${PWD}/ROOT_BUCKET:/home/ROOT_BUCKET wildfly-datasafe

http://localhost:8080/datasafe-wildfly-test_war/index.jsp
