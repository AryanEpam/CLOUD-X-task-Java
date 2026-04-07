FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
COPY host.json .
RUN mvn clean package -DskipTests

FROM mcr.microsoft.com/azure-functions/java:4-java21

ENV AzureWebJobsScriptRoot=/home/site/wwwroot
ENV AzureFunctionsJobHost__Logging__Console__IsEnabled=true

# Copy host.json
COPY --from=build /app/host.json /home/site/wwwroot/host.json

# Copy function app JAR and dependencies
COPY --from=build /app/target/orderitemsreserver-1.0.0.jar /home/site/wwwroot/orderitemsreserver-1.0.0.jar
COPY --from=build /app/target/lib/ /home/site/wwwroot/lib/

# Copy function.json descriptor
COPY --from=build /app/src/main/function/orderItemsReserver/function.json /home/site/wwwroot/orderItemsReserver/function.json

EXPOSE 80
