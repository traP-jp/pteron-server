FROM eclipse-temurin:24-jdk AS builder

WORKDIR /app

# Gradle Wrapperと設定ファイルを先にコピー
COPY gradle gradle
COPY gradlew gradlew
COPY gradlew.bat gradlew.bat
COPY build.gradle.kts build.gradle.kts
COPY settings.gradle.kts settings.gradle.kts
COPY gradle.properties gradle.properties

# 依存関係のダウンロード
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

# ソースコードをコピーしてビルド
COPY src src

# Shadow JARをビルド
RUN ./gradlew shadowJar --no-daemon

FROM eclipse-temurin:24-jre

# Labels
LABEL org.opencontainers.image.source="https://github.com/traP-jp/pteron-server"
LABEL org.opencontainers.image.description="Pteron Server"

# Create non-root user
RUN groupadd -r pteron && useradd -r -g pteron pteron

WORKDIR /app

# Copy Pteron JAR from builder stage
COPY --from=builder /app/build/libs/*-all.jar /app/pteron.jar

# Change ownership
RUN chown -R pteron:pteron /app

# Switch to non-root user
USER pteron

# Pteron server port
EXPOSE 8080

# Default command
CMD ["java", "-jar", "/app/pteron.jar"]
