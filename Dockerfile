# ---- Stage 1: Build WAR ----
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app

# Giới hạn RAM cho Maven để tránh bị kill (OOM) trên Railway
ENV MAVEN_OPTS="-Xmx384m -XX:+UseSerialGC"

COPY . .

# Build riêng module web, tải dependency ổn định hơn
RUN mvn -B -ntp -f nhom4_website_ca_phe/pom.xml clean package -DskipTests

# ---- Stage 2: Chạy trên Tomcat ----
FROM tomcat:10.1-jdk21
RUN rm -rf /usr/local/tomcat/webapps/*
COPY --from=build /app/nhom4_website_ca_phe/target/*.war /usr/local/tomcat/webapps/ROOT.war
EXPOSE 8080
CMD ["catalina.sh", "run"]