# ---------- build (Aliyun Maven mirror for ECS in China) ----------
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build
COPY settings-docker.xml /root/.m2/settings.xml
COPY pom.xml .
COPY src ./src
RUN mvn -DskipTests package -B

# ---------- runtime ----------
FROM eclipse-temurin:17-jre-jammy
RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg python3 python3-pip ca-certificates \
    && pip3 install --no-cache-dir -i https://pypi.tuna.tsinghua.edu.cn/simple yt-dlp \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=builder /build/target/dovidioai-backend-*.jar /app/app.jar
RUN mkdir -p /app/storage /app/config

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
