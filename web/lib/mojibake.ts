// gen2 Cloud Function 트리거가 한글 등 비ASCII 정책 ID를 UTF-8 바이트→Latin-1로 오독해
// 만든 모지바케(firebase-functions#1459)를 원래 문자열로 되돌린다.
// Android 앱 MojibakeDecoder.kt 포팅. round-trip 가드로 정상 입력은 그대로 반환.

export function decodeMojibake(s: string): string {
  if (!s) return s;
  try {
    // s 의 각 char를 Latin-1 바이트로 보고 UTF-8 로 재해석.
    const bytes = Uint8Array.from(Array.from(s, (ch) => ch.charCodeAt(0) & 0xff));
    const restored = new TextDecoder("utf-8", { fatal: false }).decode(bytes);
    // 역검증: 복원 결과를 다시 UTF-8 바이트→Latin-1 문자열로 만들었을 때 원본과 같아야 진짜 모지바케.
    const reencoded = Array.from(new TextEncoder().encode(restored))
      .map((b) => String.fromCharCode(b))
      .join("");
    return reencoded === s ? restored : s;
  } catch {
    return s;
  }
}
