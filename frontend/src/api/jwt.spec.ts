import { describe, expect, it } from 'vitest'
import { decodeUserId } from './jwt'

function base64url(value: unknown): string {
  return globalThis
    .btoa(JSON.stringify(value))
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '')
}

describe('decodeUserId', () => {
  it('reads the numeric sub claim from a JWT', () => {
    const token = `header.${base64url({ sub: 42, tokenType: 'ACCESS' })}.sig`
    expect(decodeUserId(token)).toBe(42)
  })

  it('reads a string sub claim as a number', () => {
    const token = `header.${base64url({ sub: '7' })}.sig`
    expect(decodeUserId(token)).toBe(7)
  })

  it('returns null for a malformed token', () => {
    expect(decodeUserId('not-a-jwt')).toBeNull()
    expect(decodeUserId('header.%%%.sig')).toBeNull()
  })

  it('returns null when sub is missing or non-numeric', () => {
    expect(decodeUserId(`h.${base64url({ tokenType: 'ACCESS' })}.s`)).toBeNull()
    expect(decodeUserId(`h.${base64url({ sub: 'abc' })}.s`)).toBeNull()
  })
})
