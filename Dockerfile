# ---- Stage 1: Build WAR ----
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app

# Giới hạn RAM cho Maven để tránh bị kill (OOM) trên Railway
ENV MAVEN_OPTS="-Xmx384m -XX:+UseSerialGC"

COPY . .

# Build riêng module web, tải dependency ổn định hơn
RUN mvn -B -ntp -f nhom4_website_ca_phe/pom.xml clean package -DskipTests

# ---- Stage 2: Chạy trên Tomcat ----
FROM tomcat:10.1-jdk17
RUN rm -rf /usr/local/tomcat/webapps/*
COPY --from=build /app/nhom4_website_ca_phe/target/*.war /usr/local/tomcat/webapps/ROOT.war
EXPOSE 8080
CMD ["catalina.sh", "run"]