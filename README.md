# AeoLens

AI 답변 엔진 최적화(AEO)와 웹 접근성(A11y)을 함께 진단하는 React + Spring WebFlux BFF 프로젝트입니다.

## 저장소 구조

```text
AEOLens/
├─ frontend/            # React + Vite 클라이언트
├─ backend/             # Spring WebFlux BFF
├─ docs/                # 아키텍처 및 포트폴리오 문서
├─ docker-compose.yml   # 전체 로컬 실행
└─ README.md
```

상세 요청 흐름과 설계 의도는 [`docs/architecture.md`](docs/architecture.md)에 정리되어 있습니다.

## 현재 구현 상태

| 영역 | 상태 | 내용 |
| --- | --- | --- |
| Frontend 기반 | 완료 | React 19, TypeScript, Vite, Tailwind CSS v4, Zustand |
| 대시보드 UI | 완료 | 반응형 다크 테마, 점수 링/게이지, 이슈 필터, JSON 내보내기, 코드 복사 |
| 로딩 UX | 완료 | 단계별 진행률과 스켈레톤 표시 |
| BFF 기반 | 완료 | Java 17, Spring Boot, WebFlux, Bean Validation, Actuator, Maven Wrapper |
| 정적 크롤링 | 완료 | URL 검증, 공인 호스트 제한, WebClient 수집, Jsoup 파싱 |
| 규칙 기반 진단 | 1차 완료 | JSON-LD 문법, 시맨틱 요소, 제목 계층, CSR 가능성, alt/label 검사 |
| 프론트/API 연동 | 완료 | `POST /api/v1/audits`, Vite `/api` 프록시, Problem Details 오류 표시 |
| 동적 렌더링 | 기반만 구성 | Playwright를 `dynamic-analysis` Maven 프로필에 분리. 브라우저 분석기는 다음 단계 |
| Gemini 문맥 평가 | 설정 지점 구성 | 서버 전용 `GEMINI_API_KEY`와 응답 계약 구성. 실제 호출/프롬프트는 Phase 4 |
| 대비/터치 영역 | 미구현 | 렌더링 후 computed style/bounding box 및 Lighthouse 연동 필요 |
| AI 코드 자동 생성 | 미구현 | 현재 UI의 코드는 예시 스니펫이며 Gemini 연동 후 결과 기반으로 교체 필요 |
| 로그인/히스토리 | 미구현 | REST 확장 구조만 고려되어 있으며 DB는 아직 없음 |

즉, Phase 1과 Phase 2의 정적 진단 코어 및 Phase 3의 UI/API 연결까지 실행 가능한 상태입니다. Playwright/Lighthouse, Gemini, 영속화는 후속 페이즈입니다.

## 요구 환경

- Node.js 22+
- Java 17+
- 별도 Maven 설치 불필요 (`backend/mvnw`, `backend/mvnw.cmd` 포함)

## 실행

터미널 1 — 백엔드:

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

터미널 2 — 프론트엔드:

```powershell
cd frontend
npm install
npm run dev
```

브라우저에서 `http://localhost:6006`을 열면 Vite가 `/api` 요청을 `http://localhost:8080`으로 전달합니다.

## 검증

```powershell
cd frontend
npm run lint
npm run build

cd ..\backend
.\mvnw.cmd test
```

Docker가 설치되어 있다면 루트에서 전체 애플리케이션을 함께 실행할 수 있습니다.

```powershell
docker compose up --build
```

헬스 체크는 `GET http://localhost:8080/actuator/health`, 진단 요청은 아래 형식입니다.

```http
POST /api/v1/audits
Content-Type: application/json

{"url":"https://example.com"}
```

## 선택 기능

Playwright Java 의존성 확인:

```powershell
cd backend
.\mvnw.cmd -Pdynamic-analysis test
```

Gemini 키는 반드시 프론트의 `VITE_*` 환경변수가 아니라 백엔드 프로세스 환경변수로 설정합니다.

```powershell
$env:GEMINI_API_KEY = "your-server-side-key"
.\mvnw.cmd spring-boot:run
```

현재 키 설정 여부만 응답에 반영하며 외부 Gemini 호출은 아직 수행하지 않습니다.

## 주요 파일

- `frontend/src/App.tsx` — 앱 셸과 페이지 조합
- `frontend/src/features/audit` — URL 입력, 분석 로딩, 결과 화면과 감사 상태
- `frontend/src/components/layout` — 사이드바와 상단바를 포함한 공통 레이아웃
- `frontend/vite.config.ts` — React/Tailwind 플러그인과 개발 프록시
- `backend/pom.xml` — WebFlux, Validation, Actuator, Jsoup, Playwright 프로필
- `backend/src/main/java/com/aeolens/api/audit` — URL 검증, 크롤링, 정적 분석, API 계약
