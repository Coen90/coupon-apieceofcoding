# coupon-service

## 실행

```bash
   # 이미지 빌드 (Docker daemon 필요)
docker compose up -d       # MySQL + 앱 기동
```

종료:

```bash
docker compose down -v     # 컨테이너 + 볼륨 제거
```

## API 호출

[scripts/api.sh](scripts/api.sh) (`jq` 필요)

## 라이선스

Copyright 2026 apieceofcoding

이 저장소의 자체 작성 예제 코드는 [Apache License 2.0](LICENSE)으로 제공됩니다.
외부 라이브러리와 Docker 이미지는 각 프로젝트의 라이선스를 따르며,
Gradle과 Docker를 통해 사용자의 로컬 환경에 직접 설치됩니다.
