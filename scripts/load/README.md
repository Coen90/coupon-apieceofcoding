# 부하 테스트

## 사전 준비

- `brew install k6 jq`
- `./gradlew --stop && ./gradlew jibDockerBuild && docker compose up -d` jib 도커 빌드 후 서비스가 8080 응답

### 브랜치 전환 시

브랜치마다 서비스 코드가 다르므로, 전환 후에는 이미지를 새로 생성하여 도커 컨테이너를 재실행한다.

```bash
git checkout <branch>
./gradlew --stop && ./gradlew jibDockerBuild && docker compose up -d
```

## part-2

```bash
./scripts/load/part-2/run.sh
```

`run.sh` 는 `reset → create_coupon → k6 → verify` 를 한 번에 실행한다.

### 동기 DB 테스트 결과

![img.png](img.png)

- vUser 5000
- 쿠폰 초과발급
- p95 3.68초

### 비관적락 테스트 결과

![img_1.png](img_1.png)

- vUser 5000
- 쿠폰 초과발급 없음
- p95 3.65초
