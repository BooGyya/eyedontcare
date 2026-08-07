import { currentParticipantKey } from '../api/identity'

export const SOLO_PLAY_ENTRY_KEY = 'eye-dont-care.solo-play-entry.v1'
export const SOLO_PLAY_ENTRY_TTL_MS = 30_000

interface SoloPlayEntry {
  gameId: string
  participantKey: string
  issuedAt: number
}

function getSessionStorage(): Storage | null {
  try {
    return globalThis.sessionStorage ?? null
  } catch {
    return null
  }
}

function removeStoredEntry(storage: Storage): boolean {
  try {
    storage.removeItem(SOLO_PLAY_ENTRY_KEY)
    return true
  } catch {
    return false
  }
}

export function issueSoloPlayEntry(gameId: string): boolean {
  const storage = getSessionStorage()
  if (!gameId || !storage) return false

  let participantKey: string | null
  try {
    participantKey = currentParticipantKey()
  } catch {
    return false
  }
  if (!participantKey) return false

  const entry: SoloPlayEntry = {
    gameId,
    participantKey,
    issuedAt: Date.now(),
  }

  try {
    storage.setItem(SOLO_PLAY_ENTRY_KEY, JSON.stringify(entry))
    return true
  } catch {
    return false
  }
}

export function consumeSoloPlayEntry(gameId: string): boolean {
  const storage = getSessionStorage()
  if (!storage) return false

  let raw: string | null
  try {
    raw = storage.getItem(SOLO_PLAY_ENTRY_KEY)
  } catch {
    return false
  }
  if (!raw) return false

  try {
    const entry = JSON.parse(raw) as Partial<SoloPlayEntry>
    const participantKey = currentParticipantKey()
    const age = Date.now() - Number(entry.issuedAt)
    const isValid =
      entry.gameId === gameId &&
      typeof entry.participantKey === 'string' &&
      entry.participantKey === participantKey &&
      Number.isFinite(entry.issuedAt) &&
      age >= 0 &&
      age < SOLO_PLAY_ENTRY_TTL_MS

    const removed = removeStoredEntry(storage)
    return removed && isValid
  } catch {
    removeStoredEntry(storage)
    return false
  }
}

export function clearSoloPlayEntry(): void {
  const storage = getSessionStorage()
  if (storage) removeStoredEntry(storage)
}
