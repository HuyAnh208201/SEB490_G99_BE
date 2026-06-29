#Stage 1 Dùng để BUILD source (có Maven + JDK 17)
FROM maven:3.9-eclipse-temurin-17 AS build
#=> Chọn image có Maven và JDK 17 để compile project. Đặt tên stage là build

WORKDIR /app
#=> Chuyển vào thư mục làm việc /app (sẽ chứa source code).
COPY pom.xml .
#=> Copy file pom.xml vào thư mục làm việc
RUN mvn dependency:go-offline -B
#=>Maven tải toàn bộ dependencies về sẵn
COPY src ./src
#Copy toàn bộ source code Java (folder src) vào container.
RUN mvn clean package -DskipTests
#Build project → tạo file .jar trong thư mục target. Bỏ qua test để build nhanh hơn.

#Stage 2 — Dùng để RUN app (chỉ cần JRE (Java Runtime Environment) 17)
FROM eclipse-temurin:17-jre-alpine
#Đặt thư mục làm việc trong container là /app
WORKDIR /app

COPY --from=build /app/target/*.jar app.jar
#Copy file JAR đã build ở stage 1 → sang đây, đặt tên là app.jar
EXPOSE 1328
ENTRYPOINT ["java", "-jar", "app.jar"]




# Docker file => docker image => docker container
