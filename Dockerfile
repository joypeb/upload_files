# 빌드
FROM gradle:8.8-jdk AS build

WORKDIR /app

COPY . .

RUN gradle build -x test --parallel

# 실행
FROM amazoncorretto:21-alpine-jdk

WORKDIR /app

COPY --from=build /app/build/libs/*.jar app.jar

RUN apk update && apk add --no-cache \
    curl \
    tzdata \
    && rm -rf /var/cache/apk/*

EXPOSE 8088

ENTRYPOINT ["java", "-jar", "/app/app.jar"]