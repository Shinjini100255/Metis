FROM maven:3.9.11-eclipse-temurin-21-alpine AS build
WORKDIR /work
COPY backend/spring-boot/pom.xml backend/spring-boot/pom.xml
COPY database/migrations database/migrations
RUN mvn -B -ntp -f backend/spring-boot/pom.xml dependency:go-offline
COPY backend/spring-boot/src backend/spring-boot/src
RUN mvn -B -ntp -f backend/spring-boot/pom.xml package -DskipTests
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S metis && adduser -S -G metis metis
WORKDIR /app
COPY --from=build /work/backend/spring-boot/target/metis-backend-0.1.0.jar app.jar
USER metis
EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]
