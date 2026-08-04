/**
 * MediaPipe Face Landmarker 로더.
 *
 * `@mediapipe/tasks-vision`을 npm 의존성으로 두지 않고, `ai_game` 프로토타입과 동일하게 jsDelivr
 * CDN에서 동적으로 불러온다. 그래서 이 파일은 실제 타입 선언 없이 최소한의 구조적 타입만 정의한다.
 *
 * ⚠️ 배포 전 확인할 것: 이 방식은 브라우저가 인터넷에서 CDN 리소스(모듈 + WASM)를 받아올 수 있어야
 * 하고, WASM 스레드를 쓰기 위해 응답 헤더에 `Cross-Origin-Opener-Policy: same-origin` /
 * `Cross-Origin-Embedder-Policy: require-corp`가 필요하다. 지금 프론트엔드 배포용
 * `nginx.conf`에는 이 헤더가 없으므로, 이 모듈을 실제로 붙이기 전에 nginx(또는 프록시)에 헤더를
 * 추가해야 한다. 폐쇄망/사내망처럼 외부 CDN 접근이 막힌 환경이라면 이 로더 대신 모델/런타임을
 * 자체 호스팅하도록 `MODULE_URL`/`WASM_ROOT`/`MODEL_URL`을 바꿔야 한다.
 */
import { MEDIAPIPE_VERSION, MODEL_URL } from './config'
import type { Landmark } from './eye-engine'

export interface FaceLandmarkerResult {
  faceLandmarks: Landmark[][]
}

export interface FaceLandmarker {
  detectForVideo(
    video: globalThis.HTMLVideoElement,
    timestampMs: number,
  ): FaceLandmarkerResult
  close(): void
}

interface FaceLandmarkerStatic {
  createFromOptions(
    vision: unknown,
    options: {
      baseOptions: { modelAssetPath: string; delegate: 'GPU' | 'CPU' }
      runningMode: 'VIDEO'
      numFaces: number
      minFaceDetectionConfidence: number
      minFacePresenceConfidence: number
      minTrackingConfidence: number
      outputFaceBlendshapes: boolean
      outputFacialTransformationMatrixes: boolean
    },
  ): Promise<FaceLandmarker>
}

interface FilesetResolverStatic {
  forVisionTasks(wasmRoot: string): Promise<unknown>
}

interface TasksVisionModule {
  FaceLandmarker: FaceLandmarkerStatic
  FilesetResolver: FilesetResolverStatic
}

const faceLandmarkerPromises = new Map<string, Promise<FaceLandmarker>>()

export async function loadFaceLandmarker(
  options: { numFaces?: number } = {},
): Promise<FaceLandmarker> {
  const numFaces = options.numFaces ?? 1
  const cacheKey = String(numFaces)
  const cached = faceLandmarkerPromises.get(cacheKey)
  if (cached) {
    return cached
  }
  const created = createFaceLandmarker({ numFaces })
  faceLandmarkerPromises.set(cacheKey, created)
  return created
}

async function createFaceLandmarker(options: {
  numFaces: number
}): Promise<FaceLandmarker> {
  const moduleUrl = `https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@${MEDIAPIPE_VERSION}/vision_bundle.mjs`
  const wasmRoot = `https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@${MEDIAPIPE_VERSION}/wasm`
  // 빌드 시점에 vite가 존재하지 않는 로컬 경로로 해석하려 하지 않도록 변수를 거쳐 동적 import한다.
  const { FaceLandmarker, FilesetResolver } = (await import(
    /* @vite-ignore */ moduleUrl
  )) as TasksVisionModule
  const vision = await FilesetResolver.forVisionTasks(wasmRoot)
  try {
    return await createWithDelegate(FaceLandmarker, vision, 'GPU', options)
  } catch {
    return createWithDelegate(FaceLandmarker, vision, 'CPU', options)
  }
}

function createWithDelegate(
  FaceLandmarker: FaceLandmarkerStatic,
  vision: unknown,
  delegate: 'GPU' | 'CPU',
  options: { numFaces: number },
): Promise<FaceLandmarker> {
  return FaceLandmarker.createFromOptions(vision, {
    baseOptions: {
      modelAssetPath: MODEL_URL,
      delegate,
    },
    runningMode: 'VIDEO',
    numFaces: options.numFaces,
    minFaceDetectionConfidence: 0.55,
    minFacePresenceConfidence: 0.55,
    minTrackingConfidence: 0.55,
    outputFaceBlendshapes: false,
    outputFacialTransformationMatrixes: false,
  })
}
