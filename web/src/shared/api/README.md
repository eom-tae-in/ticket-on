# API 타입

`schema.d.ts`는 서버의 OpenAPI 명세에서 **자동 생성**됩니다. 손으로 고치지 않습니다.

```bash
# 서버(app-api)를 띄운 상태에서
npm run api:types
```

서버 Request/Response가 바뀌면 다시 생성하고, 화면 빌드에서 어긋난 곳을 고칩니다.
