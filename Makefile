.PHONY: help build clean test run-api run-realtime docker-build docker-build-api docker-build-realtime docker-run-api docker-run-realtime setup-db install

# 기본 타겟
help:
	@echo "QBIT-BE 프로젝트 빌드 및 실행 스크립트"
	@echo ""
	@echo "사용 가능한 명령어:"
	@echo "  make install            - 프로젝트 의존성 설치 및 초기 설정"
	@echo "  make build              - 전체 프로젝트 빌드"
	@echo "  make clean              - 빌드 산출물 정리"
	@echo "  make test               - 테스트 실행"
	@echo "  make run-api            - API 애플리케이션 실행 (포트 8080)"
	@echo "  make run-realtime      - Realtime 애플리케이션 실행 (포트 8081)"
	@echo "  make docker-build       - Docker 이미지 빌드 (전체)"
	@echo "  make docker-build-api   - API Docker 이미지 빌드"
	@echo "  make docker-build-realtime - Realtime Docker 이미지 빌드"
	@echo "  make docker-run-api     - API Docker 컨테이너 실행"
	@echo "  make docker-run-realtime - Realtime Docker 컨테이너 실행"
	@echo "  make setup-db           - 데이터베이스 초기 설정 (PostgreSQL)"
	@echo ""

# 프로젝트 의존성 설치 및 초기 설정
install:
	@echo "=== 프로젝트 의존성 설치 중 ==="
	chmod +x gradlew
	./gradlew wrapper --gradle-version=8.5
	./gradlew clean build -x test
	@echo "=== 설치 완료 ==="

# 전체 프로젝트 빌드
build:
	@echo "=== 프로젝트 빌드 중 ==="
	./gradlew clean build
	@echo "=== 빌드 완료 ==="

# 빌드 산출물 정리
clean:
	@echo "=== 빌드 산출물 정리 중 ==="
	./gradlew clean
	@echo "=== 정리 완료 ==="

# 테스트 실행
test:
	@echo "=== 테스트 실행 중 ==="
	./gradlew test
	@echo "=== 테스트 완료 ==="

# API 애플리케이션 실행
run-api:
	@echo "=== API 애플리케이션 실행 중 (포트 8080) ==="
	./gradlew :qbit-api-app:bootRun

# Realtime 애플리케이션 실행
run-realtime:
	@echo "=== Realtime 애플리케이션 실행 중 (포트 8081) ==="
	./gradlew :qbit-realtime-app:bootRun

# Docker 이미지 빌드 (API)
docker-build-api:
	@echo "=== API Docker 이미지 빌드 중 ==="
	docker build -t qbit-api:latest -f Dockerfile .

# Docker 이미지 빌드 (Realtime)
docker-build-realtime:
	@echo "=== Realtime Docker 이미지 빌드 중 ==="
	docker build -t qbit-realtime:latest -f Dockerfile.realtime .

# Docker 이미지 빌드 (전체)
docker-build: docker-build-api docker-build-realtime
	@echo "=== Docker 이미지 빌드 완료 ==="

# Docker 컨테이너 실행 (API)
docker-run-api:
	@echo "=== API Docker 컨테이너 실행 중 ==="
	docker run -p 8080:8080 --env-file .env qbit-api:latest

# Docker 컨테이너 실행 (Realtime)
docker-run-realtime:
	@echo "=== Realtime Docker 컨테이너 실행 중 ==="
	docker run -p 8081:8081 --env-file .env qbit-realtime:latest

# 데이터베이스 초기 설정
setup-db:
	@echo "=== 데이터베이스 초기 설정 ==="
	@echo "PostgreSQL 데이터베이스 'qbit_dev' 생성 중..."
	@echo "CREATE DATABASE qbit_dev;" | psql -U postgres || echo "데이터베이스가 이미 존재하거나 생성에 실패했습니다."
	@echo "=== 데이터베이스 설정 완료 ==="
	@echo "주의: 환경 변수 설정 파일(.env)을 확인하세요."

